package com.roadside.sensing

import android.content.Context
import android.util.Log
import java.io.Closeable

/**
 * RoadSide's specialist chain-noise detector: a small trained head on YAMNet's 1024-d
 * embeddings, the same "frozen backbone + tiny head" split as the vision path.
 *
 *   waveform -> [YamNetEmbeddingExtractor] (per 0.96 s frame, 1024-d)
 *            -> [ChainConditionHead] loaded from `chain_audio_head.json`
 *            -> per-frame p(chain_noise) -> clip score = mean over frames
 *
 * Why this exists: YAMNet's 521 AudioSet classes have no chain class. Real chain recordings
 * made on the OnePlus 13R scored Breathing/Snoring (~0.4–0.9) and every rattle-family class
 * below 0.05, so [EvidenceTranslator]'s thresholds could never be reached, and lowering them
 * would flag 30–43 of the 98 guardrail clips. The embeddings carry more than the class
 * scores, so a head trained on labelled chain audio can separate what the classes cannot.
 *
 * With no `chain_audio_head.json` in assets, [loadOrNull] returns null and the audio path
 * behaves exactly as before.
 *
 * PROVENANCE OF THE CURRENTLY SHIPPED HEAD — read before claiming anything about it.
 * `train_audio_head.py` exports a head only when its pre-registered gate passes. That gate
 * never passed (see MODELS.md), and the head in assets today was exported outside it:
 * it was fitted on ALL available data, including the two user chain sessions it is demoed
 * on, and its 0.65 clip threshold was chosen after seeing the guardrail scores. Measured
 * with those exact weights: 0/98 guardrail clips and 0/28 user phone negatives flagged
 * (highest negative 0.597), 3/3 user chain recordings detected (0.88–0.95) — but those three
 * are its own training data, so that number is not an accuracy estimate. The only honest
 * held-out estimates available are out-of-fold precision 0.765 / recall 0.632.
 *
 * So: this head is a demo-scoped component, not a validated chain detector. Anything it
 * reports is still framed as "possible", and [EvidenceTranslator]'s own thresholds are
 * untouched. Heads must declare `gate_passed` or `prototype_only` in their metadata;
 * `ChainAudioSpecialistTest` fails if a head is shipped without saying which it is.
 *
 * Not thread-safe; [YamNetAudioClassifier] calls it under the agent's audio lock.
 */
class ChainAudioSpecialist private constructor(
    private val extractor: YamNetEmbeddingExtractor,
    private val head: ChainConditionHead,
    /** Clip score at or above which chain evidence is reported. Set by the training run. */
    val clipThreshold: Float
) : Closeable {

    data class Score(
        /** Mean per-frame p(chain_noise). Uncalibrated evidence strength, not a probability of a fault. */
        val clipScore: Float,
        val frames: Int,
        val threshold: Float,
        val inferenceMs: Long
    ) {
        val isChain: Boolean get() = clipScore >= threshold
    }

    companion object {
        private const val TAG = "ChainAudioSpecialist"
        const val HEAD_ASSET = "chain_audio_head.json"
        const val CHAIN_CLASS = "chain_noise"

        /**
         * Loads the specialist, or returns null if the head is absent or invalid.
         *
         * @param headContext where to read the head from. The app always uses its own assets;
         *        instrumented tests pass the test APK's context to load an unshipped candidate.
         */
        fun loadOrNull(
            context: Context,
            headAsset: String = HEAD_ASSET,
            headContext: Context = context
        ): ChainAudioSpecialist? {
            val present = try {
                headContext.assets.list("")?.contains(headAsset) == true
            } catch (e: Exception) {
                false
            }
            if (!present) {
                Log.i(TAG, "No $headAsset in assets; specialist chain detection disabled")
                return null
            }
            return try {
                val head = ChainConditionHead.load(headContext, headAsset)
                require(CHAIN_CLASS in head.classes) { "Head has no '$CHAIN_CLASS' class: ${head.classes}" }
                require(head.featureDim == YamNetEmbeddingExtractor.EMBEDDING_DIM) {
                    "Head expects ${head.featureDim}-d features, YAMNet embeddings are ${YamNetEmbeddingExtractor.EMBEDDING_DIM}-d"
                }
                val threshold = head.metadata["clip_threshold"]?.toFloatOrNull()
                    ?: error("Head metadata has no clip_threshold")
                // A head that did not pass the gate is usable for the fixed demo but must not
                // be mistaken for a validated detector, so it says so on every load.
                if (head.metadata["gate_passed"] != "true") {
                    Log.w(TAG, "Head did NOT pass the held-out gate (metadata: ${head.metadata}); " +
                        "treat its chain evidence as demo-scoped, not validated")
                }
                ChainAudioSpecialist(YamNetEmbeddingExtractor(context), head, threshold)
                    .also { Log.i(TAG, "Specialist chain head loaded, clip threshold $threshold") }
            } catch (e: Exception) {
                // Fail closed: a broken head produces no chain evidence at all.
                Log.e(TAG, "Specialist head failed to load; chain detection disabled", e)
                null
            }
        }
    }

    private val chainIndex = head.classes.indexOf(CHAIN_CLASS)

    fun score(samples: FloatArray): Score {
        val r = extractor.runWaveform(samples)
        var sum = 0.0
        for (f in r.embeddings) sum += head.predict(f).probabilities[chainIndex]
        val clip = if (r.frames > 0) (sum / r.frames).toFloat() else 0f
        return Score(clip, r.frames, clipThreshold, r.inferenceTimeMs)
    }

    override fun close() = extractor.close()
}
