package com.roadside.sensing

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Wraps the official MobileNetV3-Large feature extractor.
 *
 *   input   float32[1, 224, 224, 3]  values in [0, 1]
 *   output  float32[1, 1280]         pooled feature vector
 *
 * Source: Kaggle Models `google/mobilenet-v3`, framework `tfLite`, variation
 * `large-100-224-feature-vector`, version 1. Licence: Apache 2.0.
 *
 * This is a frozen, pretrained feature extractor — nothing here is trained on device.
 * The small task-specific head that consumes these 1280-d vectors lives in
 * [ChainConditionHead], with weights shipped as an asset.
 *
 * The same split is used on the audio side ([YamNetEmbeddingExtractor]): a general
 * pretrained backbone, plus a tiny head for the RoadSide-specific decision.
 *
 * Thread safety: not thread-safe; call from a single coroutine/thread.
 */
class MobileNetFeatureExtractor(
    context: Context,
    modelFilename: String = MODEL_FILENAME
) : Closeable {

    companion object {
        const val TAG = "MobileNetFeature"
        const val MODEL_FILENAME = "mobilenet_v3_feature.tflite"
        const val FEATURE_DIM = 1280
    }

    private val interpreter: Interpreter

    val inputShape: IntArray
    val outputShape: IntArray

    init {
        interpreter = Interpreter(
            loadModelFile(context, modelFilename),
            Interpreter.Options().apply { numThreads = 2 }
        )
        inputShape = interpreter.getInputTensor(0).shape()
        outputShape = interpreter.getOutputTensor(0).shape()

        Log.i(TAG, "MobileNetV3 feature extractor loaded ($modelFilename)")
        Log.i(TAG, "  input:  ${inputShape.toList()}")
        Log.i(TAG, "  output: ${outputShape.toList()}")

        val dim = outputShape.lastOrNull() ?: -1
        require(dim == FEATURE_DIM) {
            "Expected a $FEATURE_DIM-d feature vector, model reports $dim " +
                "(${outputShape.toList()}). Wrong MobileNet variation?"
        }
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val fd = context.assets.openFd(filename)
        FileInputStream(fd.fileDescriptor).use { input ->
            return input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    /**
     * @param input float32[1][224][224][3] from [ImagePreprocessor.toModelInput]
     * @return the 1280-d feature vector
     */
    fun extract(input: Array<Array<Array<FloatArray>>>): FloatArray {
        val out = Array(1) { FloatArray(FEATURE_DIM) }
        interpreter.run(input, out)
        return out[0]
    }

    override fun close() {
        try {
            interpreter.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing interpreter", e)
        }
    }
}
