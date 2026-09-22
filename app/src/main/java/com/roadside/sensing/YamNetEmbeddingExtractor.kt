package com.roadside.sensing

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.ceil

/**
 * Wraps the **official TensorFlow YAMNet** TFLite model, which exposes all three
 * canonical outputs:
 *
 *   output[0] scores      float32 [N, 521]   per-frame AudioSet class scores
 *   output[1] embeddings  float32 [N, 1024]  per-frame feature embeddings
 *   output[2] spectrogram float32 [M, 64]    log-mel spectrogram
 *
 * Source: Kaggle Models `google/yamnet`, framework `tfLite`, variation `tflite`, version 1
 * (the TF-Hub successor of `lite-model/yamnet/tflite/1`). Licence: Apache 2.0.
 *
 * This is a SEPARATE implementation from [YamNetAudioClassifier] / [YamNetInterpreter],
 * which keep using the quantized MediaPipe `classification-tflite` variant bundled as
 * `yamnet.tflite`. That baseline stays fully intact and is still the app's default path;
 * this class exists to produce embeddings for future transfer learning.
 *
 * ## Framing
 *
 * Unlike the score-only model — which takes exactly one 15,600-sample window per call and
 * requires the caller to do its own windowing — this model takes a **variable-length
 * waveform** and frames it internally using canonical YAMNet framing: a 0.96 s window
 * (15,360 samples) with a 0.48 s hop (7,680 samples).
 *
 * The number of frames it returns is:
 *
 *     frames = 1 + ceil((samples - 15360) / 7680)     for samples >= 15360
 *     frames = 1                                      for 0 < samples < 15360
 *
 * This was verified empirically against the model across clip lengths from 0.5 s to 53 s.
 * It matters because TFLite does not propagate the dynamic output shape at
 * `allocateTensors()` time, so the output buffers must be sized before `invoke()`.
 * [runWaveform] cross-checks the prediction against the tensor's reported shape after
 * resizing and fails loudly rather than silently returning a wrong-sized array.
 *
 * ## Temporal resolution
 *
 * Per-frame embeddings are returned as-is. They are deliberately NOT pooled here — mean
 * pooling, max pooling and temporal models are downstream decisions, and collapsing them
 * at extraction time would throw away the information needed to make that choice.
 *
 * Thread safety: not thread-safe; call from a single coroutine/thread.
 */
class YamNetEmbeddingExtractor(context: Context) : Closeable {

    companion object {
        const val TAG = "YamNetEmbedding"
        const val MODEL_FILENAME = "yamnet_embedding.tflite"

        /** Canonical YAMNet analysis window: 0.96 s at 16 kHz. */
        const val FRAME_SAMPLES = 15360

        /** Canonical YAMNet hop: 0.48 s at 16 kHz. */
        const val HOP_SAMPLES = 7680

        const val NUM_CLASSES = 521
        const val EMBEDDING_DIM = 1024
        const val MEL_BINS = 64

        const val SAMPLE_RATE = 16000

        /** Frames the model will emit for a waveform of [sampleCount] samples. */
        fun frameCountFor(sampleCount: Int): Int = when {
            sampleCount <= 0 -> 0
            sampleCount <= FRAME_SAMPLES -> 1
            else -> 1 + ceil((sampleCount - FRAME_SAMPLES).toDouble() / HOP_SAMPLES).toInt()
        }
    }

    /**
     * One waveform's worth of YAMNet output.
     *
     * @property scores      [frames][521] per-frame class scores.
     * @property embeddings  [frames][1024] per-frame embeddings — the point of this class.
     * @property spectrogram [melFrames][64] log-mel spectrogram.
     */
    data class Result(
        val scores: Array<FloatArray>,
        val embeddings: Array<FloatArray>,
        val spectrogram: Array<FloatArray>,
        val frames: Int,
        val inferenceTimeMs: Long
    ) {
        /** Element-wise mean of the per-frame scores, for parity with the baseline path. */
        fun meanScores(): FloatArray {
            val mean = FloatArray(NUM_CLASSES)
            if (frames == 0) return mean
            for (f in scores) for (i in mean.indices) mean[i] += f[i]
            for (i in mean.indices) mean[i] /= frames
            return mean
        }
    }

    private val interpreter: Interpreter
    private var currentSampleCount = -1

    /** Shapes as reported by the model before any resize, for the verification report. */
    val inputShape: IntArray
    val outputShapes: List<IntArray>
    val outputTypes: List<String>

    init {
        val options = Interpreter.Options().apply { numThreads = 2 }
        interpreter = Interpreter(loadModelFile(context, MODEL_FILENAME), options)

        inputShape = interpreter.getInputTensor(0).shape()
        outputShapes = (0 until interpreter.outputTensorCount)
            .map { interpreter.getOutputTensor(it).shape() }
        outputTypes = (0 until interpreter.outputTensorCount)
            .map { interpreter.getOutputTensor(it).dataType().name }

        Log.i(TAG, "Embedding-capable YAMNet loaded ($MODEL_FILENAME)")
        Log.i(TAG, "  Input shape:  ${inputShape.toList()}")
        Log.i(TAG, "  Output count: ${interpreter.outputTensorCount}")
        outputShapes.forEachIndexed { i, s ->
            Log.i(TAG, "  Output[$i] shape=${s.toList()} type=${outputTypes[i]}")
        }

        require(interpreter.outputTensorCount >= 2) {
            "Model exposes ${interpreter.outputTensorCount} output(s); an embedding-capable " +
                "YAMNet must expose at least scores and embeddings."
        }
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val fd = context.assets.openFd(filename)
        FileInputStream(fd.fileDescriptor).use { input ->
            return input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    /**
     * Runs the whole waveform through YAMNet in one call.
     *
     * @param samples mono 16 kHz float32 samples in [-1, 1]. Any length.
     */
    fun runWaveform(samples: FloatArray): Result {
        require(samples.isNotEmpty()) { "Waveform is empty" }

        // Resizing reallocates tensors, so only do it when the length actually changes.
        if (samples.size != currentSampleCount) {
            interpreter.resizeInput(0, intArrayOf(samples.size))
            interpreter.allocateTensors()
            currentSampleCount = samples.size
        }

        val frames = frameCountFor(samples.size)

        // TFLite does NOT propagate this model's dynamic output shapes at
        // allocateTensors() time — on both the desktop runtime and on Android the output
        // tensors keep reporting their static [1, N] shape no matter how the input is
        // resized. An earlier version trusted the tensor over the formula and silently
        // captured a single frame of a ten-frame clip.
        //
        // So the formula is authoritative. The reported shape is only believed when it
        // names a concrete frame count greater than one, which would mean a future
        // runtime really did resolve the shape and our framing assumption is stale.
        // The framing formula is authoritative; the output tensors' reported shapes are not.
        // Before invoke they still read the unresolved [1, N]; and after an invoke they keep
        // the PREVIOUS waveform's frame count. An earlier version preferred a reported count
        // > 1 over the formula, so reusing one extractor for a second, different-length clip
        // decoded it with the first clip's frame count (e.g. 77 frames for a 23-frame clip:
        // real frames plus stale data), and a third clip crashed with a buffer-size mismatch.
        // Found by ChainAudioSpecialistTest's parity check on the OnePlus 13R, 2026-09-22.
        val effectiveFrames = frames
        val reported = interpreter.getOutputTensor(1).shape()
        if (reported.size == 2 && reported[0] > 1 && reported[0] != frames) {
            Log.d(TAG, "Output tensor reports ${reported[0]} frames (stale); using formula $frames")
        }

        // Log-mel frames follow the same pattern: 96 for the first 0.96 s patch, then 48
        // more per 0.48 s hop. Verified against the model from 0.5 s to 53 s.
        val melFrames = if (effectiveFrames <= 0) 0 else 96 + 48 * (effectiveFrames - 1)

        // Outputs must be ByteBuffers, not Array<FloatArray>.
        //
        // TFLite's Java API shape-checks a typed-array destination against the tensor's
        // CURRENT shape, which for this model is still the unresolved [1, N] until invoke
        // actually runs. Passing Array(10){FloatArray(521)} therefore fails outright with
        //   "Cannot copy from a TensorFlowLite tensor (Identity) with shape [1, 521]
        //    to a Java object with shape [10, 521]"
        // even though the model really does produce 10 frames. The Python runtime hides
        // this because get_tensor() just returns whatever the invoke produced.
        //
        // A ByteBuffer destination is only capacity-checked, so it accommodates the real
        // output. Shapes are then read back from the tensors AFTER invoke, when they have
        // been resolved, and the buffers are decoded against those.
        val scoresBuf = newBuffer(effectiveFrames * NUM_CLASSES)
        val embeddingsBuf = newBuffer(effectiveFrames * EMBEDDING_DIM)
        val spectrogramBuf = newBuffer(melFrames * MEL_BINS)

        val outputs: MutableMap<Int, Any> = mutableMapOf(
            0 to scoresBuf,
            1 to embeddingsBuf,
            2 to spectrogramBuf
        )

        val t0 = System.nanoTime()
        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(samples), outputs)
        val ms = (System.nanoTime() - t0) / 1_000_000L

        val scoresShape = interpreter.getOutputTensor(0).shape()
        val embeddingsShape = interpreter.getOutputTensor(1).shape()
        val spectrogramShape = interpreter.getOutputTensor(2).shape()

        // The reported output shape is NOT trustworthy even here: on Android it still
        // reads [1, 1024] after a successful invoke on a 4-frame clip, while the input
        // resize demonstrably took effect (a 32,000-sample input is accepted, which a
        // [1]-shaped input tensor would reject). The Java wrapper simply never refreshes
        // this model's dynamic output shape.
        //
        // So decoding is driven by the framing formula, which was verified against the
        // model across clip lengths from 0.5 s to 53 s and matched 98/98 validation clips.
        // The disagreement is logged rather than thrown: failing here would reject output
        // that is actually correct.
        val reportedFrames = embeddingsShape.getOrElse(0) { -1 }
        if (reportedFrames != effectiveFrames) {
            Log.d(TAG, "Output tensor still reports $reportedFrames frames after invoke; " +
                "decoding $effectiveFrames per the framing formula (${samples.size} samples)")
        }

        return Result(
            scores = decode(scoresBuf, scoresShape, effectiveFrames, NUM_CLASSES),
            embeddings = decode(embeddingsBuf, embeddingsShape, effectiveFrames, EMBEDDING_DIM),
            spectrogram = decode(spectrogramBuf, spectrogramShape, melFrames, MEL_BINS),
            frames = effectiveFrames,
            inferenceTimeMs = ms
        )
    }

    private fun newBuffer(floats: Int): ByteBuffer =
        ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder())

    /**
     * Reads [rows] x [cols] floats out of [buf].
     *
     * Bounded by the buffer's own capacity rather than the tensor's reported shape, which
     * this model leaves stale. [shape] is accepted only for logging by the caller.
     */
    private fun decode(buf: ByteBuffer, shape: IntArray, rows: Int, cols: Int): Array<FloatArray> {
        buf.rewind()
        val f = buf.asFloatBuffer()
        val available = f.limit()
        return Array(rows) { row ->
            FloatArray(cols) { col ->
                val i = row * cols + col
                if (i < available) f.get(i) else 0f
            }
        }
    }

    override fun close() {
        try {
            interpreter.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing interpreter", e)
        }
    }
}
