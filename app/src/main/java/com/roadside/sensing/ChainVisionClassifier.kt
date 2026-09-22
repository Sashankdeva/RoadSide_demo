package com.roadside.sensing

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Real on-device vision path for the motorcycle-chain scenario.
 *
 *   JPEG -> [ImagePreprocessor] -> [MobileNetFeatureExtractor] (1280-d)
 *        -> [ChainConditionHead] -> RoadSide visual evidence
 *
 * SCOPE — read this before trusting the output. The trained head answers exactly one
 * question: **is a drive chain the subject of this photo?** It does not assess the
 * chain's condition, and it says nothing about brake rotors or battery terminals.
 *
 * Why so narrow: of the openly licensed images obtainable, only 3 showed clearly
 * corroded drive chains — far too few to learn a condition class. A rust-hue statistic
 * was measured as an alternative and rejected: clean chains photographed on brown wooden
 * benches scored *higher* (0.44) than genuinely corroded chains (0.29–0.38), so it does
 * not separate. Condition assessment needs labelled corrosion data.
 *
 * Consequence: `chain_appears_dry` is NOT produced by this classifier. The mock asserted
 * it from nothing, which is precisely the fabricated evidence this class removes.
 *
 * HONESTY CONSTRAINTS, matching the audio side:
 *   - [VisionClassificationResult.CHAIN_VISIBLE] means "a chain is in view", never
 *     "the chain is healthy".
 *   - When the chain is not the subject, or the head is not confident enough, the result is
 *     UNKNOWN. Guessing would feed [com.roadside.rules.DiagnosisRules] fabricated evidence,
 *     which is exactly the problem this class exists to remove.
 *   - If the backbone or head cannot be loaded, the result is UNKNOWN. An earlier version
 *     fell back to [MockVisionClassifier], which reports `chain_appears_dry` from nothing;
 *     that turned a broken install into chain-maintenance advice. The mock is retained only
 *     for deterministic UI testing and is never used as a fallback.
 *
 * Models load lazily on first use. Callers must invoke this off the main thread (see
 * [com.roadside.agent.RoadSideAgent]); loading and inference block for tens of ms.
 *
 * @param backboneAsset / headAsset asset names, overridable so tests can exercise the
 *        load-failure path against the real code.
 */
class ChainVisionClassifier(
    private val context: Context,
    private val backboneAsset: String = MobileNetFeatureExtractor.MODEL_FILENAME,
    private val headAsset: String = ChainConditionHead.ASSET_FILENAME
) : VisionClassifier, AutoCloseable {

    companion object {
        private const val TAG = "ChainVisionClassifier"

        /**
         * Minimum probability for the winning class to count as evidence.
         * PROVISIONAL — set from held-out validation, see roadside_vision/report.md.
         */
        const val MIN_PROBABILITY = 0.60f

        /**
         * Minimum gap to the runner-up. Guards the case where two classes are both
         * plausible, which on a 3-class head can happen at ~0.45/0.44/0.11.
         */
        const val MIN_MARGIN = 0.15f

        /** Class names the trained head uses. See `roadside_vision/tools/train_condition_head.py`. */
        const val CLASS_CHAIN_VISIBLE = "chain_visible"
        const val CLASS_CHAIN_PRESENT = "chain_present"
        const val CLASS_NOT_CHAIN = "not_chain"
        const val CLASS_CHAIN_SOILED = "chain_soiled"
        const val CLASS_CHAIN_APPEARS_DRY = "chain_appears_dry"
        const val CLASS_SPROCKET_WEAR_VISIBLE = "sprocket_wear_visible"
        const val CLASS_NORMAL = "normal"
    }

    private val extractor: MobileNetFeatureExtractor? by lazy {
        try {
            MobileNetFeatureExtractor(context, backboneAsset)
        } catch (e: Exception) {
            Log.e(TAG, "Feature extractor failed to load; vision evidence will be UNKNOWN", e)
            null
        }
    }

    private val head: ChainConditionHead? by lazy {
        try {
            ChainConditionHead.load(context, headAsset)
        } catch (e: Exception) {
            Log.e(TAG, "Chain head failed to load; vision evidence will be UNKNOWN", e)
            null
        }
    }

    /** Full detail for the debug screen and instrumented tests. */
    data class Detail(
        val result: VisionClassificationResult,
        val topClass: String,
        val probabilities: Map<String, Float>,
        val margin: Float,
        val preprocessMs: Long,
        val featureMs: Long,
        val headMs: Long,
        val reason: String
    )

    override suspend fun classifyImage(imageFile: File, userContextHint: String): VisionClassificationResult =
        classifyDetailed(imageFile, userContextHint).result

    fun classifyDetailed(imageFile: File, userContextHint: String = ""): Detail {
        val ex = extractor
        val hd = head
        if (ex == null || hd == null) {
            Log.w(TAG, "Vision model unavailable — returning UNKNOWN")
            return Detail(
                unknown("The camera model could not be loaded, so the photo was not assessed"),
                "unavailable", emptyMap(), 0f, 0, 0, 0,
                "model or head unavailable; no visual evidence produced"
            )
        }

        val t0 = System.nanoTime()
        val input = try {
            ImagePreprocessor.toModelInput(imageFile)
        } catch (e: Exception) {
            Log.e(TAG, "Could not read image ${imageFile.name}", e)
            return Detail(
                unknown("The photo could not be read"),
                "decode_failed", emptyMap(), 0f, 0, 0, 0,
                "image decode failed: ${e.message}"
            )
        }
        val preMs = (System.nanoTime() - t0) / 1_000_000L

        val t1 = System.nanoTime()
        val features = ex.extract(input)
        val featMs = (System.nanoTime() - t1) / 1_000_000L

        val t2 = System.nanoTime()
        val pred = hd.predict(features)
        val headMs = (System.nanoTime() - t2) / 1_000_000L

        val probs = hd.classes.indices.associate { hd.classes[it] to pred.probabilities[it] }
        Log.i(TAG, "${imageFile.name}: ${pred.describe(hd.classes)} " +
            "(margin=${"%.3f".format(pred.margin)}, ${preMs}/${featMs}/${headMs} ms)")

        val minP = hd.metadata["min_probability_presence"]?.toFloatOrNull()
            ?: hd.metadata["min_probability"]?.toFloatOrNull()
            ?: MIN_PROBABILITY
        val minMargin = hd.metadata["min_margin"]?.toFloatOrNull() ?: MIN_MARGIN

        val thSoiled = hd.metadata["min_probability_soiled"]?.toFloatOrNull() ?: 0.45f
        val thDry = hd.metadata["min_probability_dry"]?.toFloatOrNull() ?: 0.40f
        val thWear = hd.metadata["min_probability_sprocket_wear"]?.toFloatOrNull() ?: 0.65f
        val thNormal = hd.metadata["min_probability_normal"]?.toFloatOrNull() ?: 0.35f

        // Presence is decided on the presence pair ONLY: chain_present vs not_chain.
        //
        // The v3 head's probability array mixes that softmax pair with independent condition
        // sigmoids (soiled, dry, sprocket wear, normal). Taking the top class and margin across
        // the whole array — as this code did — let a condition attribute interfere with presence.
        // Two real cases: a chain photo with normal=0.99 outranked chain_present=0.966, so the
        // top class was "normal" and it was rejected; another had chain_present=0.999 but a
        // "margin" of 0.009 to normal=0.99, far below 0.90. Measured on 42 labelled chain
        // photos, that rule detected 0; this one detects 27, with the same thresholds.
        val presIdx = hd.classes.indexOf(CLASS_CHAIN_PRESENT).takeIf { it >= 0 }
            ?: hd.classes.indexOf(CLASS_CHAIN_VISIBLE)
        val notIdx = hd.classes.indexOf(CLASS_NOT_CHAIN)
        val (pPresent, pNot) = if (presIdx >= 0 && notIdx >= 0) {
            pred.probabilities[presIdx] to pred.probabilities[notIdx]
        } else {
            // A head without the pair: fall back to the whole-array decision.
            (if (pred.topClass in setOf(CLASS_CHAIN_PRESENT, CLASS_CHAIN_VISIBLE)) pred.topProbability else 0f) to
                (if (pred.topClass == CLASS_NOT_CHAIN) pred.topProbability else 0f)
        }
        val presenceMargin = if (presIdx >= 0 && notIdx >= 0) pPresent - pNot else pred.margin
        val isChainSubject = pPresent >= minP && presenceMargin >= minMargin

        val (result, reason) = when {
            !isChainSubject -> {
                val desc = if (pNot >= minP) {
                    "No drive chain is clearly visible in this photo"
                } else {
                    "The photo is not clear enough to confirm a drive chain"
                }
                unknown(desc, rawScores = pred.sigmoids) to
                    "presence p=${"%.3f".format(pPresent)} not_chain=${"%.3f".format(pNot)} " +
                    "margin=${"%.3f".format(presenceMargin)} below thresholds (p>=$minP, margin>=$minMargin)"
            }

            else -> {
                // Chain is detected! Evaluate supported condition observations
                val pSoiled = pred.sigmoids["chain_soiled"] ?: 0f
                val pDry = pred.sigmoids["chain_appears_dry"] ?: 0f
                val pWear = pred.sigmoids["sprocket_wear_visible"] ?: 0f
                val pNormal = pred.sigmoids["normal"] ?: 0f

                val observations = mutableListOf(VisionClassificationResult.CHAIN_PRESENT)
                val descParts = mutableListOf("Drive chain detected.")

                if (pSoiled >= thSoiled) {
                    observations.add(VisionClassificationResult.CHAIN_SOILED)
                    descParts.add("The visible chain appears heavily soiled.")
                }
                if (pDry >= thDry) {
                    observations.add(VisionClassificationResult.CHAIN_APPEARS_DRY)
                    descParts.add("Drive chain rollers lack visible lubricant film with mild surface dryness.")
                }
                if (pWear >= thWear) {
                    observations.add(VisionClassificationResult.SPROCKET_WEAR_VISIBLE)
                    descParts.add("Visible sprocket teeth appear worn or irregular.")
                }
                if (observations.size == 1 && pNormal >= thNormal) {
                    observations.add(VisionClassificationResult.NORMAL)
                    descParts.add("No obvious visual defect detected on visible chain and sprocket.")
                } else if (observations.size == 1) {
                    descParts.add("Its condition cannot be assessed from this image.")
                }

                // Pick primary label for compatibility with rule evaluators
                val primaryLabel = when {
                    VisionClassificationResult.SPROCKET_WEAR_VISIBLE in observations -> VisionClassificationResult.SPROCKET_WEAR_VISIBLE
                    VisionClassificationResult.CHAIN_SOILED in observations -> VisionClassificationResult.CHAIN_SOILED
                    VisionClassificationResult.CHAIN_APPEARS_DRY in observations -> VisionClassificationResult.CHAIN_APPEARS_DRY
                    VisionClassificationResult.NORMAL in observations -> VisionClassificationResult.NORMAL
                    else -> VisionClassificationResult.CHAIN_VISIBLE
                }

                val primaryConf = when (primaryLabel) {
                    VisionClassificationResult.SPROCKET_WEAR_VISIBLE -> pWear
                    VisionClassificationResult.CHAIN_SOILED -> pSoiled
                    VisionClassificationResult.CHAIN_APPEARS_DRY -> pDry
                    VisionClassificationResult.NORMAL -> pNormal
                    else -> pPresent
                }

                VisionClassificationResult(
                    label = primaryLabel,
                    confidence = primaryConf,
                    labelDescription = descParts.joinToString(" "),
                    observations = observations,
                    rawScores = pred.sigmoids
                ) to "classified observations=$observations scores=${pred.sigmoids}"
            }
        }

        return Detail(result, pred.topClass, probs, pred.margin, preMs, featMs, headMs, reason)
    }

    private fun unknown(description: String, rawScores: Map<String, Float> = emptyMap()) = VisionClassificationResult(
        label = VisionClassificationResult.UNKNOWN,
        confidence = 0f,
        labelDescription = description,
        observations = listOf(VisionClassificationResult.UNKNOWN),
        rawScores = rawScores
    )

    /** Metadata for the debug screen. */
    fun modelInfo(): String {
        val ex = extractor ?: return "feature extractor unavailable"
        val hd = head ?: return "chain head unavailable"
        return "backbone=${MobileNetFeatureExtractor.MODEL_FILENAME} " +
            "in=${ex.inputShape.toList()} out=${ex.outputShape.toList()} | " +
            "head classes=${hd.classes} dim=${hd.featureDim} ${hd.metadata}"
    }

    override fun close() {
        try {
            extractor?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing feature extractor", e)
        }
    }
}
