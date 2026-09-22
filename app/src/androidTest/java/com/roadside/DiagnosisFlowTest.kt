package com.roadside

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadside.agent.RoadSideAgent
import com.roadside.rules.DiagnosisRules
import com.roadside.sensing.AudioClassificationResult
import com.roadside.sensing.AudioClassifier
import com.roadside.sensing.ChainVisionClassifier
import com.roadside.sensing.VisionClassificationResult
import com.roadside.sensing.VisionClassifier
import com.roadside.sensing.YamNetAudioClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * Guards for the diagnosis flow as the app actually wires it:
 *
 *   - the verdict comes from sensor evidence, never from words the rider typed
 *   - user-facing text never contains internal labels
 *   - a vision model that fails to load yields UNKNOWN, not fabricated chain evidence
 *   - audio and vision inference run off the main thread
 *
 * Output is tagged [TAG] in logcat.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosisFlowTest {

    companion object {
        const val TAG = "RoadSideFlow"

        /** Audio labels that are NOT positive evidence for any fault rule. */
        val NON_FAULT_AUDIO = listOf(
            AudioClassificationResult.ENGINE_NOISE,
            AudioClassificationResult.MECHANICAL_NOISE,
            AudioClassificationResult.AMBIENT_ONLY,
            AudioClassificationResult.UNKNOWN,
            null
        )

        /** Vision labels the real classifier can produce, none of which is a fault. */
        val NON_FAULT_VISION = listOf(
            VisionClassificationResult.CHAIN_VISIBLE,
            VisionClassificationResult.CHAIN_PRESENT,
            VisionClassificationResult.NORMAL,
            VisionClassificationResult.UNKNOWN,
            null
        )

        /** Problem descriptions that used to select a fault on their own. */
        val KEYWORD_TEXTS = listOf(
            "my chain makes a noise",
            "brakes squeal when stopping",
            "battery seems dead",
            "it started rattling yesterday",
            "CHAIN BRAKE BATTERY START"
        )

        val ALL_AUDIO_LABELS = listOf(
            AudioClassificationResult.POSSIBLE_CHAIN_NOISE,
            AudioClassificationResult.BRAKE_SQUEAL,
            AudioClassificationResult.CLICKING_ELECTRICAL,
            AudioClassificationResult.ENGINE_NOISE,
            AudioClassificationResult.MECHANICAL_NOISE,
            AudioClassificationResult.AMBIENT_ONLY,
            AudioClassificationResult.UNKNOWN,
            null
        )

        val ALL_VISION_LABELS = listOf(
            VisionClassificationResult.CHAIN_VISIBLE,
            VisionClassificationResult.CHAIN_PRESENT,
            VisionClassificationResult.CHAIN_APPEARS_DRY,
            VisionClassificationResult.CHAIN_SOILED,
            VisionClassificationResult.SPROCKET_WEAR_VISIBLE,
            VisionClassificationResult.NORMAL,
            VisionClassificationResult.BRAKE_ROTOR_WORN,
            VisionClassificationResult.BATTERY_TERMINAL_CORRODED,
            VisionClassificationResult.UNKNOWN,
            null
        )
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(msg: String) = msg.lines().forEach { Log.i(TAG, it) }

    private fun diagnose(text: String, audio: String?, vision: String?) =
        DiagnosisRules.evaluate("Motorcycle", text, audio, vision)

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Typed keywords must not override sensor evidence.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun typedKeywordsDoNotOverrideSensorEvidence() {
        log("")
        log("========== TYPED KEYWORDS vs SENSOR EVIDENCE ==========")
        val failures = mutableListOf<String>()
        var checked = 0

        // No positive evidence: every keyword text must still land on unknown.
        for (text in KEYWORD_TEXTS) for (audio in NON_FAULT_AUDIO) for (vision in NON_FAULT_VISION) {
            val d = diagnose(text, audio, vision)
            checked++
            if (d.issueKey != "unknown") {
                failures.add("text='$text' audio=$audio vision=$vision -> ${d.issueKey}")
            }
        }
        log("non-fault evidence x keyword text: $checked combinations, ${failures.size} overridden")

        // Positive evidence decides, whatever the text says.
        val positive = listOf(
            AudioClassificationResult.POSSIBLE_CHAIN_NOISE to "chain_maintenance",
            AudioClassificationResult.BRAKE_SQUEAL to "brake_sound",
            AudioClassificationResult.CLICKING_ELECTRICAL to "battery_electrical"
        )
        for ((audio, expected) in positive) for (text in KEYWORD_TEXTS + "") {
            val d = diagnose(text, audio, null)
            checked++
            if (d.issueKey != expected) failures.add("text='$text' audio=$audio -> ${d.issueKey}, expected $expected")
        }
        positive.forEach { (audio, expected) -> log("audio=$audio (any text) -> $expected") }

        // The exact case reproduced on the OnePlus 13R before this fix.
        val repro = diagnose("my chain makes a noise", AudioClassificationResult.AMBIENT_ONLY, null)
        log("REPRO: text='my chain makes a noise' audio=ambient_only -> ${repro.issueKey}")
        log("checked=$checked failures=${failures.size}")
        failures.forEach { log("  FAIL $it") }
        log("=======================================================")

        assertEquals("unknown", repro.issueKey)
        assertTrue("Typed text changed the verdict: $failures", failures.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. No internal label reaches user-facing diagnosis text.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun userFacingTextHasNoInternalLabels() {
        val internal = (ALL_AUDIO_LABELS + ALL_VISION_LABELS).filterNotNull().toSet() +
            setOf("chain_maintenance", "brake_sound", "battery_electrical", "chain_loose")
        // Anything that looks like a snake_case identifier is an internal label leaking.
        val snake = Regex("\\b[a-z]+_[a-z_]+\\b")

        log("")
        log("========== USER-FACING TEXT ==========")
        val failures = mutableListOf<String>()
        for (audio in ALL_AUDIO_LABELS) for (vision in ALL_VISION_LABELS) {
            val d = diagnose("", audio, vision)
            val shown = listOf(d.title, d.summary, d.audioEvidenceSummary, d.visionEvidenceSummary,
                d.safetyAdvisory) + d.requiredTools + d.guideSteps
            for (s in shown) {
                val leaked = internal.filter { s.contains(it) } + snake.findAll(s).map { it.value }
                if (leaked.isNotEmpty()) failures.add("audio=$audio vision=$vision: $leaked in \"$s\"")
            }
        }
        // Unrecognised labels must not be echoed either.
        val odd = diagnose("", "some_future_label", "another_future_label")
        listOf(odd.audioEvidenceSummary, odd.visionEvidenceSummary).forEach { s ->
            if (snake.containsMatchIn(s)) failures.add("unrecognised label echoed: \"$s\"")
        }

        val ambient = diagnose("", AudioClassificationResult.AMBIENT_ONLY, null)
        log("ambient_only audio -> \"${ambient.audioEvidenceSummary}\"")
        log("ambient_only summary -> \"${ambient.summary}\"")
        log("combinations=${ALL_AUDIO_LABELS.size * ALL_VISION_LABELS.size} leaks=${failures.size}")
        failures.forEach { log("  FAIL $it") }
        log("======================================")

        assertFalse("UNKNOWN summary must not read like a fault",
            ambient.summary.contains("benefit from attention", ignoreCase = true))
        assertTrue("Internal labels reached user-facing text: $failures", failures.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Vision load failure -> UNKNOWN, never fabricated chain evidence.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun visionModelFailureYieldsUnknown() {
        val img = syntheticImage()
        log("")
        log("========== VISION LOAD FAILURE ==========")

        val cases = listOf(
            "missing backbone" to ChainVisionClassifier(context, backboneAsset = "missing_backbone.tflite"),
            "missing head" to ChainVisionClassifier(context, headAsset = "missing_head.json")
        )
        for ((name, classifier) in cases) {
            // The hint is the kind of text that made the old mock fallback say "chain appears dry".
            val d = classifier.classifyDetailed(img, "chain noise")
            val diagnosis = diagnose("chain noise", null, d.result.label)
            log("$name -> label=${d.result.label} top=${d.topClass} diagnosis=${diagnosis.issueKey}")
            log("  reason: ${d.reason}")
            assertEquals("$name must yield UNKNOWN", VisionClassificationResult.UNKNOWN, d.result.label)
            assertEquals("$name must not produce a fault", "unknown", diagnosis.issueKey)
            classifier.close()
        }
        log("=========================================")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Inference runs off the main thread, with the real models.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun sensorInferenceRunsOffMainThread() {
        val wav = File(context.cacheDir, "flow_probe.wav").also { writeSyntheticWav(it) }
        val img = syntheticImage()

        val realAudio = YamNetAudioClassifier(context)
        val realVision = ChainVisionClassifier(context)
        val audio = ThreadRecordingAudio(realAudio)
        val vision = ThreadRecordingVision(realVision)
        val agent = RoadSideAgent(context, audioClassifier = audio, visionClassifier = vision)

        // Call exactly as MainActivity does: from a coroutine on the main thread.
        var callerOnMain = false
        runBlocking(Dispatchers.Main) {
            callerOnMain = Looper.myLooper() == Looper.getMainLooper()
            agent.processAudioCapture(wav)
            agent.processVisionCapture(img)
        }
        val state = agent.context.value

        log("")
        log("========== INFERENCE THREADING ==========")
        log("caller on main thread:  $callerOnMain")
        log("audio ran on:           ${audio.threadName} (main=${audio.onMain}) ${audio.ms} ms")
        log("vision ran on:          ${vision.threadName} (main=${vision.onMain}) ${vision.ms} ms")
        log("audio evidence:         ${state.audioEvidence} / \"${state.audioEvidenceLabel}\"")
        log("vision evidence:        ${state.visionEvidence} / \"${state.visionEvidenceLabel}\"")
        log("diagnosis:              ${state.diagnosisCandidate?.issueKey}")
        log("pending flags cleared:  ${!state.audioAnalysisPending && !state.visionAnalysisPending}")
        log("=========================================")

        realAudio.close()
        realVision.close()
        wav.delete()

        assertTrue("Test must call from the main thread to be meaningful", callerOnMain)
        assertEquals("Audio inference must not run on the main thread", false, audio.onMain)
        assertEquals("Vision inference must not run on the main thread", false, vision.onMain)
        assertNotNull("Audio evidence must reach the agent state", state.audioEvidence)
        assertNotNull("Vision evidence must reach the agent state", state.visionEvidence)
        assertNotNull(state.diagnosisCandidate)
        assertFalse(state.audioAnalysisPending)
        assertFalse(state.visionAnalysisPending)

        // An UNKNOWN visual result must show the classifier's specific reason, in plain words.
        val visionShown = state.diagnosisCandidate!!.visionEvidenceSummary
        log("vision summary shown:   \"$visionShown\"")
        if (state.visionEvidence == VisionClassificationResult.UNKNOWN) {
            assertEquals(state.visionEvidenceLabel!!.trimEnd('.') + ".", visionShown)
        }
        assertFalse("Internal label in vision summary: $visionShown",
            Regex("\\b[a-z]+_[a-z_]+\\b").containsMatchIn(visionShown))

        // Finishing a guide must clear the session so the next check starts clean.
        agent.updateVehicleType("Scooter")
        agent.resetSession()
        val reset = agent.context.value
        log("after resetSession:     audio=${reset.audioEvidence} vision=${reset.visionEvidence} " +
            "diagnosis=${reset.diagnosisCandidate?.issueKey} vehicle=${reset.vehicleType}")
        assertEquals(null, reset.audioEvidence)
        assertEquals(null, reset.visionEvidence)
        assertEquals(null, reset.diagnosisCandidate)
        assertEquals("Vehicle choice survives a reset", "Scooter", reset.vehicleType)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private class ThreadRecordingAudio(private val delegate: AudioClassifier) : AudioClassifier {
        var onMain: Boolean? = null
        var threadName = ""
        var ms = 0L
        override suspend fun classifyAudio(audioFile: File, userContextHint: String): AudioClassificationResult {
            onMain = Looper.myLooper() == Looper.getMainLooper()
            threadName = Thread.currentThread().name
            val t0 = System.nanoTime()
            return delegate.classifyAudio(audioFile, userContextHint)
                .also { ms = (System.nanoTime() - t0) / 1_000_000L }
        }
    }

    private class ThreadRecordingVision(private val delegate: VisionClassifier) : VisionClassifier {
        var onMain: Boolean? = null
        var threadName = ""
        var ms = 0L
        override suspend fun classifyImage(imageFile: File, userContextHint: String): VisionClassificationResult {
            onMain = Looper.myLooper() == Looper.getMainLooper()
            threadName = Thread.currentThread().name
            val t0 = System.nanoTime()
            return delegate.classifyImage(imageFile, userContextHint)
                .also { ms = (System.nanoTime() - t0) / 1_000_000L }
        }
    }

    private fun syntheticImage(): File {
        val f = File(context.cacheDir, "flow_probe.jpg")
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(android.graphics.Color.rgb(60, 60, 64))
        val p = Paint().apply { color = android.graphics.Color.rgb(180, 180, 190) }
        for (i in 0 until 12) c.drawCircle(60f + i * 45f, 240f, 16f, p)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        return f
    }

    /** 3 s, 16 kHz mono 16-bit, 44-byte header — the format AudioRecorder writes. */
    private fun writeSyntheticWav(out: File) {
        val sr = 16000
        val n = sr * 3
        val pcm = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            val s = 0.3 * sin(2 * PI * 220.0 * i / sr) + 0.1 * sin(2 * PI * 1800.0 * i / sr)
            pcm.putShort((s * Short.MAX_VALUE).toInt().toShort())
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + n * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(sr); putInt(sr * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(n * 2)
        }
        FileOutputStream(out).use { it.write(header.array()); it.write(pcm.array()) }
    }
}
