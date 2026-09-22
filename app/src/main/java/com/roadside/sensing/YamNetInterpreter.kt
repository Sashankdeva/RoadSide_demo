package com.roadside.sensing

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Wraps the YAMNet TFLite model for per-window audio classification.
 *
 * This targets the MediaPipe YAMNet model which exposes a single output:
 *   Output[0]: float32[1, 521]  — AudioSet class scores
 *
 * Input:  float32[WINDOW_SAMPLES]  (15600 samples = 0.975 s at 16 kHz)
 *
 * Thread safety: not thread-safe; call from a single coroutine/thread.
 */
class YamNetInterpreter(context: Context) : Closeable {

    companion object {
        const val TAG = "YamNetInterpreter"
        const val MODEL_FILENAME = "yamnet.tflite"

        /** Number of PCM samples per YAMNet window (0.975 s * 16000 Hz). */
        const val WINDOW_SAMPLES = 15600

        /** Number of AudioSet sound-event classes. */
        const val NUM_CLASSES = 521

        /** Embedding dimension (not available in MediaPipe variant). */
        const val EMBEDDING_DIM = 1024
    }

    data class WindowResult(
        /** Raw class probability scores [0, 1] for each of the 521 AudioSet classes. */
        val scores: FloatArray,
        /** 1024-dim feature embedding (zeros for MediaPipe single-output model). */
        val embeddings: FloatArray,
        /** Wall-clock inference time for this window in milliseconds. */
        val inferenceTimeMs: Long
    )

    private val interpreter: Interpreter
    private val numOutputs: Int

    init {
        val modelBuffer = loadModelFile(context, MODEL_FILENAME)
        val options = Interpreter.Options().apply {
            numThreads = 2
        }
        interpreter = Interpreter(modelBuffer, options)
        numOutputs = interpreter.outputTensorCount

        Log.i(TAG, "YAMNet interpreter initialised.")
        Log.i(TAG, "  Input shape:    ${interpreter.getInputTensor(0).shape().toList()}")
        Log.i(TAG, "  Num outputs:    $numOutputs")
        for (i in 0 until numOutputs) {
            Log.i(TAG, "  Output[$i] shape: ${interpreter.getOutputTensor(i).shape().toList()}")
        }
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    /**
     * Runs one YAMNet window inference.
     *
     * @param samples Float32 array of [WINDOW_SAMPLES] samples, normalised to [-1, 1].
     *                If fewer samples are provided, the remainder is zero-padded.
     * @return [WindowResult] containing scores, embeddings (zeros if unavailable), and inference time.
     */
    fun runWindow(samples: FloatArray): WindowResult {
        // Build input array — zero-pad if shorter than WINDOW_SAMPLES
        val inputArray = FloatArray(WINDOW_SAMPLES)
        val copyCount = minOf(samples.size, WINDOW_SAMPLES)
        System.arraycopy(samples, 0, inputArray, 0, copyCount)
        // Remainder remains 0f by default in newly allocated FloatArray

        // Output scores array matching tensor shape [1, 521]
        val outputScores = Array(1) { FloatArray(NUM_CLASSES) }

        val t0 = System.currentTimeMillis()
        try {
            if (numOutputs == 1) {
                // MediaPipe single-output variant: scores only
                interpreter.run(inputArray, outputScores)
            } else {
                // Multi-output variant (TF Hub full model)
                val embeddingsOutput = Array(1) { FloatArray(EMBEDDING_DIM) }
                val outputsMap: MutableMap<Int, Any> = mutableMapOf(
                    0 to outputScores,
                    1 to embeddingsOutput
                )
                interpreter.runForMultipleInputsOutputs(arrayOf(inputArray), outputsMap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            return WindowResult(FloatArray(NUM_CLASSES), FloatArray(EMBEDDING_DIM), 0L)
        }
        val inferenceMs = System.currentTimeMillis() - t0

        return WindowResult(outputScores[0], FloatArray(EMBEDDING_DIM), inferenceMs)
    }

    /** Input tensor shape information for display in debug screen. */
    fun inputShape(): IntArray = interpreter.getInputTensor(0).shape()

    /** Output tensor shapes for display in debug screen. */
    fun outputShapes(): List<IntArray> = (0 until numOutputs)
        .map { interpreter.getOutputTensor(it).shape() }

    override fun close() {
        try {
            interpreter.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing interpreter", e)
        }
    }
}
