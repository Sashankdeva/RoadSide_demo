package com.roadside

import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadside.sensing.ValidationWavReader
import com.roadside.sensing.YamNetBatchValidator
import com.roadside.sensing.YamNetEmbeddingExtractor
import com.roadside.sensing.YamNetInterpreter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Device-side verification for the embedding-capable YAMNet.
 *
 *   adb shell am instrument -w -e class com.roadside.YamNetEmbeddingTest \
 *     com.roadside.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Does not touch the score-only baseline path, which is covered by
 * [YamNetVerificationTest] and must keep passing alongside this.
 */
@RunWith(AndroidJUnit4::class)
class YamNetEmbeddingTest {

    companion object {
        const val TAG = "RoadSideEmbed"
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(msg: String) = msg.lines().forEach { Log.i(TAG, it) }

    private fun usedKb(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / 1024 + Debug.getNativeHeapAllocatedSize() / 1024
    }

    /** Test audio: a real clip if one was pushed, otherwise a deterministic synthetic probe. */
    private fun testWaveform(seconds: Int = 5): Pair<FloatArray, String> {
        val dir = YamNetBatchValidator.testDir(context)
        val real = dir.listFiles { f -> f.isFile && f.extension.equals("wav", true) }
            ?.sortedBy { it.name.lowercase() }?.firstOrNull()
        if (real != null) {
            val d = ValidationWavReader.decode(real)
            if (d.samples.isNotEmpty()) return d.samples to (real.name + " [REAL]")
        }
        val f = File(context.cacheDir, "embed_probe.wav").also { writeSyntheticWav(it, seconds) }
        return ValidationWavReader.decode(f).samples to (f.name + " [SYNTHETIC PROBE]")
    }

    @Test
    fun embeddingModelExposes1024DimTensor() {
        val memBefore = usedKb()
        val initStart = System.nanoTime()
        val ex = YamNetEmbeddingExtractor(context)
        val initMs = (System.nanoTime() - initStart) / 1_000_000L
        val memAfterInit = usedKb()

        val (wave, label) = testWaveform()

        val first = ex.runWaveform(wave)
        val warm = (1..3).map { ex.runWaveform(wave).inferenceTimeMs }
        val memAfterRun = usedKb()

        log("")
        log("========== EMBEDDING MODEL ==========")
        log("MODEL:            " + YamNetEmbeddingExtractor.MODEL_FILENAME)
        log("INPUT:            shape=" + ex.inputShape.toList() + " type=float32")
        log("OUTPUT COUNT:     " + ex.outputShapes.size)
        ex.outputShapes.forEachIndexed { i, s ->
            log("")
            log("output[" + i + "]:")
            log("  shape=" + s.toList())
            log("  type=" + ex.outputTypes[i])
        }
        log("")
        log("AUDIO:            " + label + " (" + "%.2f".format(wave.size / 16000f) + " s, " + wave.size + " samples)")
        log("FRAMES:           " + first.frames + "  (formula predicts " +
            YamNetEmbeddingExtractor.frameCountFor(wave.size) + ")")
        log("scores      runtime shape = [" + first.scores.size + ", " + first.scores[0].size + "]")
        log("embeddings  runtime shape = [" + first.embeddings.size + ", " + first.embeddings[0].size + "]")
        log("spectrogram runtime shape = [" + first.spectrogram.size + ", " + first.spectrogram[0].size + "]")
        log("")
        log("embedding dimension = " + first.embeddings[0].size)
        log("")
        log("INIT:             " + initMs + " ms")
        log("FIRST INFERENCE:  " + first.inferenceTimeMs + " ms (whole clip)")
        log("WARM INFERENCE:   " + warm.joinToString("/") + " ms (whole clip)")
        log("MEMORY:           " + memBefore + " -> " + memAfterInit + " KB after init -> " +
            memAfterRun + " KB after runs")
        log("  model load delta: " + (memAfterInit - memBefore) + " KB")

        val flat = first.embeddings.flatMap { it.asIterable() }
        val mean = flat.average()
        val sd = sqrt(flat.sumOf { (it - mean) * (it - mean) } / flat.size)
        log("")
        log("EMBEDDING STATS:  mean=" + "%.4f".format(mean) + " std=" + "%.4f".format(sd) +
            " min=" + "%.4f".format(flat.min()) + " max=" + "%.4f".format(flat.max()))
        val allZero = flat.all { it == 0f }
        log("NON-ZERO:         " + (!allZero))
        log("=====================================")

        assertEquals("Embedding dimension must be 1024",
            YamNetEmbeddingExtractor.EMBEDDING_DIM, first.embeddings[0].size)
        assertEquals("Score width must be 521",
            YamNetEmbeddingExtractor.NUM_CLASSES, first.scores[0].size)
        assertTrue("Model must expose at least scores + embeddings", ex.outputShapes.size >= 2)
        assertTrue("Embeddings are all zero — extraction did not run", !allZero)
        assertEquals("Frame counts disagree between scores and embeddings",
            first.scores.size, first.embeddings.size)
        ex.close()
    }

    /**
     * Both models implement YAMNet, so their scores should broadly agree on the same audio.
     * They are NOT bit-identical: the baseline is a quantized Relu6 retrain taking one fixed
     * 15,600-sample window, while this one is float and frames internally at 15,360/7,680.
     * The check is therefore on ranked agreement, not numeric equality.
     */
    @Test
    fun scoreAgreementWithBaselineModel() {
        val (wave, label) = testWaveform()

        val ex = YamNetEmbeddingExtractor(context)
        val embResult = ex.runWaveform(wave)
        val embMean = embResult.meanScores()
        ex.close()

        val base = YamNetInterpreter(context)
        val baseMean = FloatArray(YamNetInterpreter.NUM_CLASSES)
        var n = 0
        var start = 0
        val hop = YamNetInterpreter.WINDOW_SAMPLES / 2
        while (start < wave.size) {
            val w = FloatArray(YamNetInterpreter.WINDOW_SAMPLES)
            val len = minOf(YamNetInterpreter.WINDOW_SAMPLES, wave.size - start)
            System.arraycopy(wave, start, w, 0, len)
            val r = base.runWindow(w)
            for (i in baseMean.indices) baseMean[i] += r.scores[i]
            n++
            start += hop
        }
        for (i in baseMean.indices) baseMean[i] /= n
        base.close()

        val classMap = loadClassMap()

        fun top(v: FloatArray, k: Int) = v.mapIndexed { i, s -> i to s }
            .sortedByDescending { it.second }.take(k)

        val tb = top(baseMean, 5)
        val te = top(embMean, 5)
        val overlap = tb.map { it.first }.toSet().intersect(te.map { it.first }.toSet()).size

        log("")
        log("========== SCORE COMPARISON ==========")
        log("AUDIO: " + label)
        log("baseline  yamnet.tflite           windows=" + n + " (15600 window / 7800 hop, quantized)")
        log("embedding yamnet_embedding.tflite frames=" + embResult.frames + " (15360 / 7680, float)")
        log("")
        log("rank  baseline                           score      embedding-model                    score")
        for (i in 0 until 5) {
            log("%-5d %-34s %-10s %-34s %s".format(
                i + 1,
                classMap[tb[i].first] ?: "?", "%.4f".format(tb[i].second),
                classMap[te[i].first] ?: "?", "%.4f".format(te[i].second)))
        }
        log("")
        log("top-5 index overlap: " + overlap + "/5")
        log("top-1 agreement:     " + (tb[0].first == te[0].first))
        log("======================================")

        assertTrue("Top-5 sets disagree entirely — the two models are not both YAMNet", overlap >= 1)
    }

    /** Per-frame embeddings must be preserved, not collapsed at extraction time. */
    @Test
    fun perFrameEmbeddingsArePreserved() {
        val ex = YamNetEmbeddingExtractor(context)
        val (wave, _) = testWaveform()
        val r = ex.runWaveform(wave)

        log("")
        log("========== TEMPORAL RESOLUTION ==========")
        log("samples=" + wave.size + " (" + "%.2f".format(wave.size / 16000f) + " s) -> frames=" + r.frames)
        log("one 1024-d embedding per 0.48 s hop / 0.96 s window")
        for (i in 0 until minOf(3, r.frames)) {
            val f = r.embeddings[i]
            log("  frame[" + i + "] mean=" + "%.4f".format(f.average()) +
                " min=" + "%.4f".format(f.min()) + " max=" + "%.4f".format(f.max()))
        }
        log("=========================================")

        val expected = YamNetEmbeddingExtractor.frameCountFor(wave.size)
        assertEquals("Frame count does not match canonical YAMNet framing", expected, r.frames)
        assertTrue("Expected more than one frame for a multi-second clip", r.frames > 1)

        if (r.frames >= 2) {
            val differs = r.embeddings[0].indices.any { r.embeddings[0][it] != r.embeddings[1][it] }
            log("frame[0] differs from frame[1]: " + differs)
            assertTrue("Consecutive embeddings are identical — temporal detail was lost", differs)
        }
        ex.close()
    }

    /** The score-only baseline must still work unchanged. */
    @Test
    fun baselineScoreModelStillIntact() {
        val interp = YamNetInterpreter(context)
        val (wave, _) = testWaveform()
        val w = FloatArray(YamNetInterpreter.WINDOW_SAMPLES)
        System.arraycopy(wave, 0, w, 0, minOf(w.size, wave.size))
        val r = interp.runWindow(w)
        val sum = r.scores.sum()
        log("")
        log("========== BASELINE INTACT ==========")
        log("model=" + YamNetInterpreter.MODEL_FILENAME + " input=" + interp.inputShape().toList())
        log("outputs=" + interp.outputShapes().map { it.toList() })
        log("score sum=" + "%.4f".format(sum) + " max=" + "%.4f".format(r.scores.max()))
        log("=====================================")
        assertEquals(YamNetInterpreter.NUM_CLASSES, r.scores.size)
        assertTrue("Baseline model returned all zeros", sum > 0f)
        interp.close()
    }

    private fun loadClassMap(): Map<Int, String> =
        context.assets.open("yamnet_class_map.csv").bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val a = line.indexOf(',')
                val b = if (a >= 0) line.indexOf(',', a + 1) else -1
                if (b < 0) return@mapNotNull null
                val idx = line.substring(0, a).trim().toIntOrNull() ?: return@mapNotNull null
                idx to line.substring(b + 1).trim().trim('"')
            }.toMap()
        }

    private fun writeSyntheticWav(out: File, seconds: Int) {
        val sr = 16000
        val n = sr * seconds
        val pcm = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        var seed = 12345L
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val freq = 220.0 + 180.0 * (i.toDouble() / n)
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val noise = ((seed shr 33).toInt() % 1000) / 1000.0 * 0.15
            val v = (0.45 * sin(2.0 * PI * freq * t) + noise).coerceIn(-1.0, 1.0)
            pcm.putShort((v * 32767).toInt().toShort())
        }
        val data = pcm.array()
        FileOutputStream(out).use { fos ->
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt(36 + data.size)
            h.put("WAVE".toByteArray()); h.put("fmt ".toByteArray())
            h.putInt(16); h.putShort(1); h.putShort(1)
            h.putInt(sr); h.putInt(sr * 2); h.putShort(2); h.putShort(16)
            h.put("data".toByteArray()); h.putInt(data.size)
            fos.write(h.array()); fos.write(data)
        }
    }
}
