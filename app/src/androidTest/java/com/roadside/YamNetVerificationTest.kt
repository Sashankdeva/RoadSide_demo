package com.roadside

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadside.sensing.AudioClassificationResult
import com.roadside.sensing.EvidenceTranslator
import com.roadside.sensing.ValidationWavReader
import com.roadside.sensing.YamNetBatchValidator
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

/**
 * Device-side verification entry point — no UI interaction required.
 *
 * Run headlessly:
 *   adb shell am instrument -w -e class com.roadside.YamNetVerificationTest \
 *     com.roadside.test/androidx.test.runner.AndroidJUnitRunner
 *
 * All output is tagged [TAG] in logcat.
 */
@RunWith(AndroidJUnit4::class)
class YamNetVerificationTest {

    companion object {
        const val TAG = "RoadSideVerify"
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(msg: String) = msg.lines().forEach { Log.i(TAG, it) }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Model load + real inference, with latency, on whatever audio exists.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun yamnetInferenceReport() {
        val testDir = YamNetBatchValidator.testDir(context)
        val realWavs = testDir.listFiles { f -> f.isFile && f.extension.equals("wav", true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

        // Cold model init
        val initStart = System.nanoTime()
        val interpreter = YamNetInterpreter(context)
        val initMs = (System.nanoTime() - initStart) / 1_000_000L

        val inputShape = interpreter.inputShape().toList()
        val outputShapes = interpreter.outputShapes().map { it.toList() }

        // Choose audio: real recordings if present, otherwise a synthetic probe signal.
        val usingSynthetic = realWavs.isEmpty()
        val wav: File
        if (usingSynthetic) {
            log("NO REAL MOTORCYCLE RECORDINGS AVAILABLE")
            log("Falling back to a SYNTHETIC probe signal — this exercises the pipeline only.")
            log("Its predictions describe a synthetic tone, NOT a motorcycle. Do not read them as vehicle results.")
            wav = File(testDir, "synthetic_probe.wav").also { writeSyntheticWav(it) }
        } else {
            wav = realWavs.first()
        }

        val decoded = ValidationWavReader.decode(wav)
        assertTrue("Decoded audio must not be empty", decoded.samples.isNotEmpty())

        val hop = YamNetInterpreter.WINDOW_SAMPLES / 2
        val meanScores = FloatArray(YamNetInterpreter.NUM_CLASSES)
        var windows = 0
        var firstMs = 0L
        var warmTotalMs = 0L
        var start = 0

        while (start < decoded.samples.size) {
            val window = FloatArray(YamNetInterpreter.WINDOW_SAMPLES)
            val copyLen = minOf(YamNetInterpreter.WINDOW_SAMPLES, decoded.samples.size - start)
            System.arraycopy(decoded.samples, start, window, 0, copyLen)

            val t0 = System.nanoTime()
            val r = interpreter.runWindow(window)
            val ms = (System.nanoTime() - t0) / 1_000_000L

            if (windows == 0) firstMs = ms else warmTotalMs += ms
            for (i in meanScores.indices) meanScores[i] += r.scores[i]
            windows++
            start += hop
        }
        for (i in meanScores.indices) meanScores[i] /= windows
        interpreter.close()

        val warmAvg = if (windows > 1) warmTotalMs / (windows - 1) else 0L
        val classMap = loadClassMap()
        val top10 = meanScores.mapIndexed { i, s -> i to s }
            .sortedByDescending { it.second }
            .take(10)
        val evidence = EvidenceTranslator.translate(meanScores)
        val breakdown = EvidenceTranslator.breakdown(meanScores)

        // Verify the model actually produced a real distribution rather than zeros —
        // the app launching proves nothing about whether inference ran.
        val scoreSum = meanScores.sum()
        val maxScore = meanScores.max()
        val inferenceRan = scoreSum > 0f && maxScore > 0f

        log("")
        log("========== YAMNET DIRECT INFERENCE ==========")
        log("MODEL:            ${YamNetInterpreter.MODEL_FILENAME}")
        log("INPUT:            shape=$inputShape  outputs=$outputShapes")
        log("AUDIO:            ${wav.name} (${decoded.sourceSampleRate} Hz, ${decoded.sourceChannels} ch, " +
            "${decoded.sourceBitsPerSample}-bit, ${"%.2f".format(decoded.durationSeconds)} s)" +
            if (usingSynthetic) "  [SYNTHETIC PROBE]" else "  [REAL RECORDING]")
        log("WINDOWS:          $windows")
        log("INIT:             $initMs ms")
        log("FIRST INFERENCE:  $firstMs ms")
        log("WARM INFERENCE:   $warmAvg ms/window")
        log("")
        log("TOP 10 YAMNET:")
        top10.forEachIndexed { i, (idx, s) ->
            log("  ${i + 1}. ${(classMap[idx] ?: "class_$idx").padEnd(34)} — ${"%.4f".format(s)}")
        }
        log("")
        log("ROADSIDE EVIDENCE:")
        log("  category: ${evidence.label}")
        log("  evidence: ${evidence.topContributor} (uncalibrated ${"%.4f".format(evidence.evidenceScore)})")
        log("  reason:   ${evidence.reason}")
        breakdown.lines().forEach { log("  $it") }
        log("")
        log("SCORE SUM:        ${"%.4f".format(scoreSum)}   MAX: ${"%.4f".format(maxScore)}")
        log("INFERENCE RAN:    ${if (inferenceRan) "YES" else "NO — all-zero output"}")
        log("RESULT:           ${if (inferenceRan) "PASS" else "FAIL"}")
        log("=============================================")

        assertTrue("Model produced an all-zero score vector — inference did not run", inferenceRan)
        assertEquals("Unexpected output class count", YamNetInterpreter.NUM_CLASSES, meanScores.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Windowing math — the corrected 5 s = 11 windows figure.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun windowingMathMatchesDocumentation() {
        val hop = YamNetInterpreter.WINDOW_SAMPLES / 2
        assertEquals(15600, YamNetInterpreter.WINDOW_SAMPLES)
        assertEquals(7800, hop)

        fun windowsFor(seconds: Double): Int {
            val n = (seconds * 16000).toInt()
            var c = 0
            var s = 0
            while (s < n) { c++; s += hop }
            return c
        }

        log("")
        log("========== WINDOWING MATH ==========")
        listOf(0.5 to 2, 1.0 to 3, 2.0 to 5, 3.0 to 7, 5.0 to 11, 10.0 to 21, 30.0 to 62).forEach { (sec, expect) ->
            val actual = windowsFor(sec)
            log("  ${sec}s -> $actual windows (expected $expect)")
            assertEquals("Window count wrong for ${sec}s", expect, actual)
        }
        log("  5 s = 11 windows, NOT 50")
        log("====================================")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. EvidenceTranslator behaviour, with indices resolved from the shipped CSV.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun evidenceTranslatorMappingIsCorrect() {
        val classMap = loadClassMap()
        val byName = classMap.entries.associate { (k, v) -> v to k }

        // Every label the translator relies on must exist in the shipped CSV.
        val required = listOf(
            "Vehicle", "Motor vehicle (road)", "Car", "Motorcycle", "Traffic noise, roadway noise",
            "Engine", "Light engine (high frequency)", "Medium engine (mid frequency)",
            "Heavy engine (low frequency)", "Engine knocking", "Engine starting", "Idling",
            "Accelerating, revving, vroom", "Skidding", "Tire squeal", "Train wheels squealing",
            "Squeak", "Squeal", "Rattle", "Mechanisms", "Gears", "Clatter", "Clickety-clack",
            "Tick", "Tick-tock", "Scrape", "Whir", "Clicking", "Buzz", "Buzzer", "Hum",
            "Mains hum", "Static", "Silence", "Noise", "Environmental noise", "White noise",
            "Pink noise", "Wind", "Wind noise (microphone)", "Speech", "Music"
        )
        log("")
        log("========== CLASS MAP VERIFICATION ==========")
        log("  classes in CSV: ${classMap.size} (expected ${YamNetInterpreter.NUM_CLASSES})")
        assertEquals(YamNetInterpreter.NUM_CLASSES, classMap.size)
        val missing = required.filterNot { byName.containsKey(it) }
        log("  required labels present: ${required.size - missing.size}/${required.size}")
        if (missing.isNotEmpty()) log("  MISSING: $missing")
        assertTrue("Labels missing from class map: $missing", missing.isEmpty())

        // Every index must be inside the model's real output range.
        val maxIndex = classMap.keys.max()
        log("  max index: $maxIndex (must be ${YamNetInterpreter.NUM_CLASSES - 1})")
        assertEquals(YamNetInterpreter.NUM_CLASSES - 1, maxIndex)

        fun scores(vararg pairs: Pair<String, Float>): FloatArray {
            val v = FloatArray(YamNetInterpreter.NUM_CLASSES)
            for ((label, s) in pairs) {
                val idx = byName[label] ?: error("Label not in class map: $label")
                v[idx] = s
            }
            return v
        }

        data class Case(val name: String, val scores: FloatArray, val expected: String)

        val cases = listOf(
            Case("normal engine (idling)",
                scores("Engine" to 0.62f, "Idling" to 0.55f, "Motorcycle" to 0.48f),
                AudioClassificationResult.ENGINE_NOISE),
            Case("normal motorcycle riding",
                scores("Motorcycle" to 0.70f, "Accelerating, revving, vroom" to 0.51f,
                       "Traffic noise, roadway noise" to 0.30f),
                AudioClassificationResult.ENGINE_NOISE),
            Case("REGRESSION: light engine + motorcycle, no rattle",
                scores("Light engine (high frequency)" to 0.58f, "Motorcycle" to 0.50f),
                AudioClassificationResult.ENGINE_NOISE),
            Case("REGRESSION: rattle present but engine louder",
                scores("Rattle" to 0.28f, "Engine" to 0.55f, "Motorcycle" to 0.40f),
                AudioClassificationResult.ENGINE_NOISE),
            Case("rattle dominant over quiet engine",
                scores("Rattle" to 0.44f, "Engine" to 0.18f, "Motorcycle" to 0.35f),
                AudioClassificationResult.POSSIBLE_CHAIN_NOISE),
            Case("brake squeal",
                scores("Squeal" to 0.52f, "Motorcycle" to 0.30f),
                AudioClassificationResult.BRAKE_SQUEAL),
            Case("ambient wind / road noise",
                scores("Wind" to 0.41f, "Environmental noise" to 0.33f),
                AudioClassificationResult.AMBIENT_ONLY),
            Case("people talking nearby",
                scores("Speech" to 0.66f),
                AudioClassificationResult.AMBIENT_ONLY),
            Case("silence",
                scores("Silence" to 0.22f),
                AudioClassificationResult.AMBIENT_ONLY),
            Case("nothing at all",
                FloatArray(YamNetInterpreter.NUM_CLASSES),
                AudioClassificationResult.UNKNOWN)
        )

        log("")
        log("========== EVIDENCE TRANSLATOR CASES ==========")
        val failures = mutableListOf<String>()
        for (c in cases) {
            val r = EvidenceTranslator.translate(c.scores)
            val ok = r.label == c.expected
            if (!ok) failures.add("${c.name}: expected ${c.expected}, got ${r.label}")
            log("  ${if (ok) "PASS" else "FAIL"}  ${c.name.padEnd(46)} -> ${r.label}")
        }
        log("===============================================")
        assertTrue("Translator mapping failures: $failures", failures.isEmpty())

        // The specific regression this rewrite exists to prevent.
        val normalBike = scores(
            "Engine" to 0.66f, "Idling" to 0.58f, "Light engine (high frequency)" to 0.52f,
            "Motorcycle" to 0.61f, "Vehicle" to 0.40f
        )
        val result = EvidenceTranslator.translate(normalBike)
        log("REGRESSION GUARD: normal motorcycle -> ${result.label}")
        assertTrue(
            "A normal running motorcycle must not be read as chain evidence (got ${result.label})",
            result.label != AudioClassificationResult.POSSIBLE_CHAIN_NOISE
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Batch validation over any real recordings present on the device.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun batchValidateRealRecordings() {
        val dir = YamNetBatchValidator.testDir(context)
        val wavs = dir.listFiles { f ->
            f.isFile && f.extension.equals("wav", true) && f.name != "synthetic_probe.wav"
        } ?: emptyArray()

        if (wavs.isEmpty()) {
            log("")
            log("NO REAL MOTORCYCLE RECORDINGS AVAILABLE")
            log("Push clips to: ${dir.absolutePath}")
            log("Then re-run this test. Skipping batch validation.")
            return
        }

        val report = YamNetBatchValidator.runBatch(context)
        log("")
        log(report.text)
        assertTrue("Batch produced no file reports", report.fileReports.isNotEmpty())
    }


    // ─────────────────────────────────────────────────────────────────────────
    // 5. Production audio path: AudioRecorder's WAV format -> WavDecoder ->
    //    YamNetAudioClassifier. Covers everything downstream of the microphone.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun productionAudioPathEndToEnd() {
        // A WAV written in exactly AudioRecorder's format (16 kHz mono 16-bit, 44-byte header).
        val wav = File(context.cacheDir, "prod_path_probe.wav").also { writeSyntheticWav(it) }

        // Production decoder (not the harness reader).
        val samples = com.roadside.audio.WavDecoder.decode(wav)
        log("")
        log("========== PRODUCTION AUDIO PATH ==========")
        log("WAV bytes:        ${wav.length()}")
        log("WavDecoder:       ${samples.size} samples (${"%.2f".format(samples.size / 16000f)} s)")
        assertTrue("Production WavDecoder returned no samples", samples.isNotEmpty())
        assertEquals("Sample count should be (bytes-44)/2", ((wav.length() - 44) / 2).toInt(), samples.size)

        val peak = samples.maxOf { kotlin.math.abs(it) }
        log("Peak amplitude:   ${"%.4f".format(peak)}")
        assertTrue("Decoded audio is silent — header/offset handling is wrong", peak > 0.01f)

        // Production classifier end to end.
        val classifier = com.roadside.sensing.YamNetAudioClassifier(context)
        val t0 = System.nanoTime()
        val result = kotlinx.coroutines.runBlocking { classifier.classifyAudio(wav, "") }
        val ms = (System.nanoTime() - t0) / 1_000_000L
        classifier.close()

        log("YamNetAudioClassifier: ${ms} ms for the full clip")
        log("  category: ${result.label}")
        log("  evidence: ${result.topContributor} (uncalibrated ${"%.4f".format(result.evidenceScore)})")
        log("  reason:   ${result.reason}")
        log("==========================================")

        assertTrue("Production classifier returned an empty label", result.label.isNotEmpty())
        wav.delete()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Microphone capture — requires RECORD_AUDIO, which ColorOS will not let
    //    adb grant. Reports status instead of failing the run.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun microphoneCaptureStatus() {
        val granted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        log("")
        log("========== MICROPHONE CAPTURE ==========")
        log("RECORD_AUDIO granted: $granted")
        if (!granted) {
            log("SKIPPED — permission not granted and adb cannot grant it on this build.")
            log("Grant it once on the device, then re-run this test.")
            log("========================================")
            return
        }

        val recorder = com.roadside.audio.AudioRecorder(context)
        val started = recorder.startRecording()
        log("startRecording: ${if (started.isSuccess) "OK" else "FAILED: ${started.exceptionOrNull()}"}")
        assertTrue("Recorder failed to start", started.isSuccess)
        Thread.sleep(3000)
        val stopped = recorder.stopRecording()
        assertTrue("Recorder failed to stop: ${stopped.exceptionOrNull()}", stopped.isSuccess)

        val file = stopped.getOrThrow()
        val samples = com.roadside.audio.WavDecoder.decode(file)
        log("Recorded: ${file.name} ${file.length()} bytes -> ${samples.size} samples")
        assertTrue("Recorded WAV decoded to nothing", samples.isNotEmpty())

        val classifier = com.roadside.sensing.YamNetAudioClassifier(context)
        val r = kotlinx.coroutines.runBlocking { classifier.classifyAudio(file, "") }
        classifier.close()
        log("Live mic evidence: ${r.label} (${r.topContributor})")
        log("========================================")
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Writes a deterministic synthetic probe: swept tone plus broadband noise.
     * Purely to exercise the pipeline when no real recordings are on the device.
     */
    private fun writeSyntheticWav(out: File) {
        val sr = 16000
        val seconds = 5
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
            fos.write(h.array())
            fos.write(data)
        }
    }

    private fun loadClassMap(): Map<Int, String> =
        context.assets.open("yamnet_class_map.csv").bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                // display_name may be quoted and contain commas
                val first = line.indexOf(',')
                if (first < 0) return@mapNotNull null
                val second = line.indexOf(',', first + 1)
                if (second < 0) return@mapNotNull null
                val idx = line.substring(0, first).trim().toIntOrNull() ?: return@mapNotNull null
                val name = line.substring(second + 1).trim().trim('"')
                idx to name
            }.toMap()
        }
}
