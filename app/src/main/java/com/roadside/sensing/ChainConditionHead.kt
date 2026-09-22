package com.roadside.sensing

import android.content.Context
import android.util.Log
import org.json.JSONObject
import kotlin.math.exp

/**
 * The RoadSide-specific part of the vision path: a multinomial logistic-regression
 * head over the 1280-d [MobileNetFeatureExtractor] features.
 *
 * Weights are trained offline on labelled Commons images (see
 * `roadside_vision/tools/train_head.py`) and shipped as `chain_head.json`. The head is a
 * few thousand floats, so there is no reason to make it a second TFLite model — a dot
 * product in Kotlin is smaller, inspectable, and removes a conversion step that would
 * need TensorFlow on the build machine.
 *
 * Inference is:
 *
 *     z = (x - mean) / scale        standardisation, as fitted
 *     logits = W z + b
 *     p = softmax(logits)
 *
 * The caller decides what counts as sufficient evidence; this class only reports
 * probabilities. That keeps the "evidence, never a verdict" split used on the audio side.
 */
class ChainConditionHead private constructor(
    val classes: List<String>,
    val featureDim: Int,
    private val weights: Array<FloatArray>,
    private val bias: FloatArray,
    private val mean: FloatArray?,
    private val scale: FloatArray?,
    val metadata: Map<String, String>
) {

    companion object {
        const val TAG = "ChainConditionHead"
        const val ASSET_FILENAME = "chain_head.json"

        /** Reads and validates the head from assets. */
        fun load(context: Context, filename: String = ASSET_FILENAME): ChainConditionHead {
            val text = context.assets.open(filename).bufferedReader().use { it.readText() }
            val root = JSONObject(text)

            val classes = root.getJSONArray("classes").let { a ->
                (0 until a.length()).map { a.getString(it) }
            }
            val featureDim = root.getInt("feature_dim")

            val wArr = root.getJSONArray("weights")
            require(wArr.length() == classes.size) {
                "Head has ${wArr.length()} weight rows for ${classes.size} classes"
            }
            val weights = Array(classes.size) { c ->
                val row = wArr.getJSONArray(c)
                require(row.length() == featureDim) {
                    "Weight row $c has ${row.length()} values, expected $featureDim"
                }
                FloatArray(featureDim) { i -> row.getDouble(i).toFloat() }
            }

            val bArr = root.getJSONArray("bias")
            require(bArr.length() == classes.size) { "Bias length != class count" }
            val bias = FloatArray(classes.size) { bArr.getDouble(it).toFloat() }

            fun optVector(key: String): FloatArray? {
                if (!root.has(key) || root.isNull(key)) return null
                val a = root.getJSONArray(key)
                require(a.length() == featureDim) { "$key length != feature dim" }
                return FloatArray(featureDim) { a.getDouble(it).toFloat() }
            }

            val meta = mutableMapOf<String, String>()
            if (root.has("metadata")) {
                val m = root.getJSONObject("metadata")
                m.keys().forEach { k -> meta[k] = m.get(k).toString() }
            }

            Log.i(TAG, "Chain head loaded: classes=$classes featureDim=$featureDim")
            meta.forEach { (k, v) -> Log.i(TAG, "  $k = $v") }

            return ChainConditionHead(
                classes, featureDim, weights, bias,
                optVector("scaler_mean"), optVector("scaler_scale"), meta
            )
        }
    }

    data class Prediction(
        /** Probability per class, aligned with [classes]. */
        val probabilities: FloatArray,
        val topIndex: Int,
        val topClass: String,
        val topProbability: Float,
        /** Gap between the best and second-best class — low means the head is unsure. */
        val margin: Float,
        /** Independent calibrated sigmoid probability per class attribute. */
        val sigmoids: Map<String, Float> = emptyMap()
    ) {
        fun describe(classes: List<String>): String =
            classes.indices.sortedByDescending { probabilities[it] }
                .joinToString(", ") { "${classes[it]}=${"%.3f".format(probabilities[it])}" }
    }

    fun predict(features: FloatArray): Prediction {
        require(features.size == featureDim) {
            "Feature vector is ${features.size}-d, head expects $featureDim"
        }

        val z = if (mean != null && scale != null) {
            FloatArray(featureDim) { i ->
                val s = scale[i]
                if (s == 0f) 0f else (features[i] - mean[i]) / s
            }
        } else {
            features
        }

        val logits = FloatArray(classes.size) { c ->
            var acc = bias[c]
            val w = weights[c]
            for (i in 0 until featureDim) acc += w[i] * z[i]
            acc
        }

        // Calibrated sigmoid probabilities per class/attribute
        val sigmoids = mutableMapOf<String, Float>()
        for (c in classes.indices) {
            val s = 1.0 / (1.0 + exp(-logits[c].toDouble()))
            sigmoids[classes[c]] = s.toFloat()
        }

        // Presence softmax: between chain_present and not_chain
        val presIdx = classes.indexOf("chain_present").takeIf { it >= 0 } ?: classes.indexOf("chain_visible")
        val notIdx = classes.indexOf("not_chain")
        
        val probs = FloatArray(classes.size)
        if (presIdx >= 0 && notIdx >= 0) {
            val maxL = maxOf(logits[presIdx], logits[notIdx])
            val ePres = exp((logits[presIdx] - maxL).toDouble())
            val eNot = exp((logits[notIdx] - maxL).toDouble())
            val sumP = (ePres + eNot).toFloat()
            probs[presIdx] = (ePres / sumP).toFloat()
            probs[notIdx] = (eNot / sumP).toFloat()
            
            // Assign remaining classes their sigmoid probabilities
            for (c in classes.indices) {
                if (c != presIdx && c != notIdx) {
                    probs[c] = sigmoids[classes[c]] ?: 0f
                }
            }
        } else {
            // General Softmax, shifted for numerical stability
            val maxLogit = logits.max()
            var sum = 0.0
            for (c in logits.indices) {
                val e = exp((logits[c] - maxLogit).toDouble())
                probs[c] = e.toFloat()
                sum += e
            }
            for (c in probs.indices) probs[c] = (probs[c] / sum).toFloat()
        }

        val order = classes.indices.sortedByDescending { probs[it] }
        val top = order[0]
        val second = if (order.size > 1) probs[order[1]] else 0f

        return Prediction(probs, top, classes[top], probs[top], probs[top] - second, sigmoids)
    }
}
