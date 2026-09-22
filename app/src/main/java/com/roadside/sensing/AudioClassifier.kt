package com.roadside.sensing

import android.content.Context
import android.util.Log
import com.roadside.audio.WavDecoder
import java.io.File

interface AudioClassifier {
    suspend fun classifyAudio(audioFile: File, userContextHint: String = ""): AudioClassificationResult
}

/**
 * @property label            RoadSide evidence category (see companion constants).
 * @property evidenceScore    **Uncalibrated** strength of the supporting acoustic observation
 *                            in [0, 1]. This is NOT a probability that the category is correct
 *                            and NOT a confidence: YAMNet emits independent per-class sigmoids,
 *                            so a high value means "some member class scored high", nothing more.
 *                            Do not present it to users as a percentage certainty.
 * @property labelDescription Human-readable description of the sound observed.
 * @property reason           Which group scores drove this decision, for debugging.
 * @property topContributor   Strongest single AudioSet observation across all groups.
 */
data class AudioClassificationResult(
    val label: String,
    val evidenceScore: Float,
    val labelDescription: String,
    val reason: String = "",
    val topContributor: String = "",
    /** Specialist chain-head clip score, or null if the specialist is not installed. */
    val specialistScore: Float? = null
) {
    companion object {
        const val POSSIBLE_CHAIN_NOISE  = "possible_chain_noise"
        const val BRAKE_SQUEAL          = "brake_squeal"
        const val CLICKING_ELECTRICAL   = "clicking_electrical"
        const val ENGINE_NOISE          = "engine_noise"
        const val MECHANICAL_NOISE      = "mechanical_noise"
        const val AMBIENT_ONLY          = "ambient_only"
        const val UNKNOWN               = "UNKNOWN"
    }
}

// ── MockAudioClassifier ── kept intact for testing / fallback ─────────────────

class MockAudioClassifier : AudioClassifier {
    override suspend fun classifyAudio(audioFile: File, userContextHint: String): AudioClassificationResult {
        val hint = userContextHint.lowercase()
        return when {
            hint.contains("brake") || hint.contains("squeal") -> {
                AudioClassificationResult(
                    label = AudioClassificationResult.BRAKE_SQUEAL,
                    evidenceScore = 0.88f,
                    labelDescription = "High frequency friction sound consistent with brake component"
                )
            }
            hint.contains("battery") || hint.contains("click") || hint.contains("crank") || hint.contains("start") -> {
                AudioClassificationResult(
                    label = AudioClassificationResult.CLICKING_ELECTRICAL,
                    evidenceScore = 0.84f,
                    labelDescription = "Rapid solenoid or relay clicking sound"
                )
            }
            hint.contains("unknown") || hint.contains("nothing") -> {
                AudioClassificationResult(
                    label = AudioClassificationResult.UNKNOWN,
                    evidenceScore = 0.30f,
                    labelDescription = "Inconclusive audio pattern"
                )
            }
            else -> {
                // Primary prototype motorcycle demo default: chain noise
                AudioClassificationResult(
                    label = AudioClassificationResult.POSSIBLE_CHAIN_NOISE,
                    evidenceScore = 0.91f,
                    labelDescription = "Cyclic mechanical rattling consistent with drive chain or sprocket"
                )
            }
        }
    }
}

// ── YamNetAudioClassifier ─────────────────────────────────────────────────────

/**
 * Real on-device audio classifier backed by YAMNet TFLite.
 *
 * Pipeline:
 *   WAV file → [WavDecoder] (PCM float32) → 15600-sample windows (50% hop)
 *   → [YamNetInterpreter] (per window) → temporal mean scores
 *   → [EvidenceTranslator] → [AudioClassificationResult]
 *
 * The [YamNetInterpreter] is loaded lazily on first [classifyAudio] call,
 * which runs off the main thread. This avoids blocking Activity.onCreate.
 */
class YamNetAudioClassifier(private val context: Context) : AudioClassifier, AutoCloseable {

    companion object {
        private const val TAG = "YamNetAudioClassifier"
        /** 50% overlap between consecutive windows. */
        private const val HOP_SAMPLES = YamNetInterpreter.WINDOW_SAMPLES / 2
        /**
         * Fraction of exact-zero samples above which the mic is treated as having delivered
         * nothing. Measured on the OnePlus 13R: genuine quiet-room recordings have 0.2–3 %
         * exact zeros; recordings made while a leaked AudioRecord held the mic were 100 %.
         */
        const val SILENT_MIC_ZERO_FRACTION = 0.95f
    }

    // Lazy to avoid loading the 4 MB model on the main thread during Activity.onCreate.
    // RoadSideAgent calls classifyAudio on Dispatchers.Default, so the load happens there.
    private val interpreter: YamNetInterpreter by lazy { YamNetInterpreter(context) }

    // Optional specialist chain head on YAMNet embeddings. Null (and the audio path exactly
    // as before) unless chain_audio_head.json is shipped, which only happens when its
    // training run passes the held-out gate.
    private val specialist: ChainAudioSpecialist? by lazy { ChainAudioSpecialist.loadOrNull(context) }

    override suspend fun classifyAudio(audioFile: File, userContextHint: String): AudioClassificationResult {
        Log.i(TAG, "Classifying: ${audioFile.name} (${audioFile.length()} bytes)")

        // 1. Decode WAV → float32 samples
        val allSamples = WavDecoder.decode(audioFile)
        if (allSamples.isEmpty()) {
            Log.w(TAG, "Empty audio after decode; returning UNKNOWN")
            return AudioClassificationResult(
                label = AudioClassificationResult.UNKNOWN,
                evidenceScore = 0f,
                labelDescription = "No audio data could be read from the recording"
            )
        }

        // A real microphone always has some noise floor. A recording that is (almost) all
        // exact zeros means the mic delivered nothing — held by another recorder, muted, or
        // silenced in the background. YAMNet would call that "Silence" and the app would
        // report "background sound only", which is wrong: nothing was heard at all.
        val zeroFraction = allSamples.count { it == 0f }.toFloat() / allSamples.size
        if (zeroFraction >= SILENT_MIC_ZERO_FRACTION) {
            Log.w(TAG, "Recording is ${"%.1f".format(zeroFraction * 100)}% exact zeros; microphone delivered no signal")
            return AudioClassificationResult(
                label = AudioClassificationResult.UNKNOWN,
                evidenceScore = 0f,
                labelDescription = "The microphone recorded no sound. Close other apps that may be using it and record again",
                reason = "digital silence: ${"%.1f".format(zeroFraction * 100)}% of samples are exactly zero"
            )
        }

        // 2. Split into windows with 50% hop
        val windows = buildWindows(allSamples)
        Log.d(TAG, "${windows.size} windows from ${allSamples.size} samples")

        // 3. Run YAMNet per window, accumulate scores
        val meanScores = FloatArray(YamNetInterpreter.NUM_CLASSES)
        var totalInferenceMs = 0L

        for (window in windows) {
            val result = interpreter.runWindow(window)
            totalInferenceMs += result.inferenceTimeMs
            for (i in meanScores.indices) {
                meanScores[i] += result.scores[i]
            }
        }

        // 4. Average scores
        if (windows.isNotEmpty()) {
            for (i in meanScores.indices) meanScores[i] /= windows.size
        }

        Log.i(TAG, "Inference done: ${windows.size} windows, ${totalInferenceMs}ms total, " +
                "${if (windows.isNotEmpty()) totalInferenceMs / windows.size else 0}ms avg/window")

        // 5. Translate to RoadSide evidence
        val baseline = EvidenceTranslator.translate(meanScores)
        return withSpecialist(baseline, allSamples)
    }

    /**
     * Adds the specialist's chain evidence to the baseline result.
     *
     * The baseline is computed exactly as before and its thresholds are untouched. The
     * specialist can only ADD chain evidence; it never removes any. It does not override
     * brake or electrical evidence, which it was not trained to judge.
     */
    private fun withSpecialist(
        baseline: AudioClassificationResult,
        samples: FloatArray
    ): AudioClassificationResult {
        val sp = specialist ?: return baseline
        val s = try {
            sp.score(samples)
        } catch (e: Exception) {
            Log.e(TAG, "Specialist scoring failed; using baseline evidence only", e)
            return baseline
        }
        Log.i(TAG, "Specialist chain score ${"%.3f".format(s.clipScore)} over ${s.frames} frames " +
            "(threshold ${s.threshold}, ${s.inferenceMs} ms); baseline=${baseline.label}")

        val keepBaseline = baseline.label == AudioClassificationResult.BRAKE_SQUEAL ||
            baseline.label == AudioClassificationResult.CLICKING_ELECTRICAL ||
            baseline.label == AudioClassificationResult.POSSIBLE_CHAIN_NOISE
        return if (s.isChain && !keepBaseline) {
            AudioClassificationResult(
                label = AudioClassificationResult.POSSIBLE_CHAIN_NOISE,
                evidenceScore = s.clipScore.coerceIn(0f, 1f),
                labelDescription = "Sound resembling drive-chain noise detected by the trained chain-sound model",
                reason = "specialist chain head score ${"%.3f".format(s.clipScore)} >= ${s.threshold} " +
                    "over ${s.frames} frames; baseline was ${baseline.label} (${baseline.reason})",
                topContributor = baseline.topContributor,
                specialistScore = s.clipScore
            )
        } else {
            baseline.copy(
                reason = baseline.reason + "; specialist chain score ${"%.3f".format(s.clipScore)} < ${s.threshold}"
                    .takeIf { !s.isChain }.orEmpty(),
                specialistScore = s.clipScore
            )
        }
    }

    /**
     * Chops [samples] into fixed-length windows of [YamNetInterpreter.WINDOW_SAMPLES]
     * with a hop of [HOP_SAMPLES]. The last window is zero-padded if short.
     */
    private fun buildWindows(samples: FloatArray): List<FloatArray> {
        val windows = mutableListOf<FloatArray>()
        var start = 0
        while (start < samples.size) {
            val window = FloatArray(YamNetInterpreter.WINDOW_SAMPLES)
            val copyLen = minOf(YamNetInterpreter.WINDOW_SAMPLES, samples.size - start)
            System.arraycopy(samples, start, window, 0, copyLen)
            // Remaining positions stay 0 (zero-padded)
            windows.add(window)
            start += HOP_SAMPLES
        }
        return windows
    }

    override fun close() {
        interpreter.close()
        specialist?.close()
    }
}

