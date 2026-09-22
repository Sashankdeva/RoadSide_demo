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

        /** Class names the trained head uses. See `roadside_vision/tools/train_head.py`. */
        const val CLASS_CHAIN_VISIBLE = "chain_visible"
        const val CLASS_NOT_CHAIN = "not_chain"
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

        // Thresholds are chosen on the validation split by the training script and shipped in
        // the head's metadata, so the UNKNOWN policy stays tied to the head it was validated
        // with. The constants are only a fallback for heads without that metadata.
        val minP = hd.metadata["min_probability"]?.toFloatOrNull() ?: MIN_PROBABILITY
        val minMargin = hd.metadata["min_margin"]?.toFloatOrNull() ?: MIN_MARGIN
        val confident = pred.topProbability >= minP && pred.margin >= minMargin

        val (result, reason) = when {
            !confident -> unknown(
                "The photo is not clear enough to judge the chain"
            ) to ("top=${pred.topClass} p=${"%.3f".format(pred.topProbability)} " +
                "margin=${"%.3f".format(pred.margin)} below thresholds " +
                "(p>=$minP, margin>=$minMargin)")

            pred.topClass == CLASS_NOT_CHAIN -> unknown(
                "No drive chain is clearly visible in this photo"
            ) to "classified as $CLASS_NOT_CHAIN p=${"%.3f".format(pred.topProbability)}"

            pred.topClass == CLASS_CHAIN_VISIBLE -> VisionClassificationResult(
                label = VisionClassificationResult.CHAIN_VISIBLE,
                confidence = pred.topProbability,
                labelDescription = "Drive chain detected in the photo. Its condition cannot be assessed from this image."
            ) to "classified as $CLASS_CHAIN_VISIBLE p=${"%.3f".format(pred.topProbability)}"

            else -> unknown("Unrecognised visual category") to
                "head returned unexpected class '${pred.topClass}'"
        }

        return Detail(result, pred.topClass, probs, pred.margin, preMs, featMs, headMs, reason)
    }

    private fun unknown(description: String) = VisionClassificationResult(
        label = VisionClassificationResult.UNKNOWN,
        confidence = 0f,
        labelDescription = description
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
