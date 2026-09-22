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
 * The head is only shipped if its training run passed the held-out gate
 * (`roadside_chain_audio/tools/train_audio_head.py`). With no `chain_audio_head.json` in
 * assets, [loadOrNull] returns null and the audio path behaves exactly as before.
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
