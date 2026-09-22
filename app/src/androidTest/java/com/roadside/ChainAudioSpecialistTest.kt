package com.roadside

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadside.sensing.AudioClassificationResult
import com.roadside.sensing.ChainAudioSpecialist
import com.roadside.sensing.YamNetAudioClassifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device checks for the specialist chain head, through the PRODUCTION audio classifier
 * (WavDecoder -> YAMNet + EvidenceTranslator -> specialist), not a desktop mirror.
 *
 * Labelled clips may be pushed to
 *   /sdcard/Android/data/com.roadside/files/specialist_test/
 * named `chain__*.wav` or `neg__*.wav`. Every `neg__` clip must NOT produce chain evidence;
 * `chain__` clips are scored and reported. Output is tagged [TAG].
 */
@RunWith(AndroidJUnit4::class)
class ChainAudioSpecialistTest {

    companion object {
        const val TAG = "RoadSideSpecialist"
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(msg: String) = Log.i(TAG, msg)

    @Test
    fun specialistOnLabelledDeviceClips() {
        val installed = context.assets.list("")?.contains(ChainAudioSpecialist.HEAD_ASSET) == true
        log("")
        log("========== SPECIALIST CHAIN HEAD ==========")
        log("head asset installed: $installed")

        val dir = File(context.getExternalFilesDir(null), "specialist_test")
        val clips = dir.listFiles { f -> f.extension.equals("wav", true) }?.sortedBy { it.name }.orEmpty()
        log("labelled clips: ${clips.size} in ${dir.absolutePath}")

        val classifier = YamNetAudioClassifier(context)
        var negFlagged = 0; var negs = 0; var chainHit = 0; var chains = 0
        val scoreTimes = mutableListOf<Long>()
        for (f in clips) {
            val t0 = System.nanoTime()
            val r = runBlocking { classifier.classifyAudio(f, "") }
            val ms = (System.nanoTime() - t0) / 1_000_000L
            scoreTimes.add(ms)
            val chain = r.label == AudioClassificationResult.POSSIBLE_CHAIN_NOISE
            when {
                f.name.startsWith("neg__") -> { negs++; if (chain) negFlagged++ }
                f.name.startsWith("chain__") -> { chains++; if (chain) chainHit++ }
            }
            log("%-60s label=%-20s specialist=%s  %d ms".format(
                f.name.take(60), r.label, r.specialistScore?.let { "%.3f".format(it) } ?: "n/a", ms))
        }
        classifier.close()
        log("negatives flagged as chain: $negFlagged/$negs")
        log("chain clips detected:       $chainHit/$chains")
        if (scoreTimes.isNotEmpty()) log("full classify per clip: median ${scoreTimes.sorted()[scoreTimes.size / 2]} ms")
        log("===========================================")

        assertEquals("Negatives must never produce chain evidence", 0, negFlagged)
        if (!installed) {
            assertEquals("Without the head, nothing may be reported as specialist chain evidence", 0, chainHit)
        }
    }

    /**
     * Proves the on-device scoring (Kotlin extractor + head + mean aggregation) reproduces the
     * desktop training script, using an UNSHIPPED candidate head bundled only in the test APK.
     * The clips are its training data, so the scores say nothing about accuracy — only parity.
     */
    @Test
    fun deviceScoringMatchesDesktopTrainingScript() {
        val testCtx = InstrumentationRegistry.getInstrumentation().context
        val sp = ChainAudioSpecialist.loadOrNull(context, "candidate_chain_audio_head.json", headContext = testCtx)
        assertTrue("Candidate head must load from the test APK", sp != null)
        val expected = org.json.JSONObject(
            testCtx.assets.open("specialist_parity/expected_scores.json").bufferedReader().use { it.readText() })

        log("")
        log("========== SPECIALIST PARITY (candidate head, NOT shipped) ==========")
        var worst = 0.0
        val times = mutableListOf<Long>()
        for (name in expected.keys()) {
            val f = File(context.cacheDir, "parity_$name")
            testCtx.assets.open("specialist_parity/$name").use { i -> f.outputStream().use { i.copyTo(it) } }
            val s = sp!!.score(com.roadside.audio.WavDecoder.decode(f))
            f.delete()
            val want = expected.getDouble(name)
            val diff = kotlin.math.abs(s.clipScore - want)
            worst = maxOf(worst, diff)
            times.add(s.inferenceMs)
            log("%-46s device=%.4f desktop=%.4f |diff|=%.4f frames=%d embed=%d ms".format(
                name.take(46), s.clipScore, want, diff, s.frames, s.inferenceMs))
        }
        sp!!.close()
        log("max |device - desktop| = ${"%.5f".format(worst)}")
        log("=====================================================================")
        assertTrue("Device scoring diverges from the training script by $worst", worst < 0.02)
    }

    /**
     * A shipped head must state whether it passed the held-out gate. The head in assets today
     * did not (`gate_passed: false, prototype_only: true`): it was fitted on all available
     * data, including the chain sessions it is demoed on, and its threshold was set after the
     * guardrail was scored. That is defensible for a fixed demo and indefensible as a silent
     * claim, so the declaration is enforced here rather than left to a reviewer to notice.
     */
    @Test
    fun shippedHeadDeclaresItsValidationStatus() {
        if (context.assets.list("")?.contains(ChainAudioSpecialist.HEAD_ASSET) != true) {
            log("no head shipped; nothing to declare")
            return
        }
        val head = com.roadside.sensing.ChainConditionHead.load(context, ChainAudioSpecialist.HEAD_ASSET)
        val gate = head.metadata["gate_passed"]
        val prototypeOnly = head.metadata["prototype_only"]
        log("")
        log("========== SHIPPED HEAD PROVENANCE ==========")
        log("metadata: ${head.metadata}")
        log("gate_passed=$gate prototype_only=$prototypeOnly threshold=${head.metadata["clip_threshold"]}")
        log("=============================================")

        assertTrue(
            "A shipped head must declare clip_threshold",
            head.metadata["clip_threshold"]?.toFloatOrNull() != null
        )
        assertTrue(
            "A shipped head must declare gate_passed=true or prototype_only=true, " +
                "so its chain evidence is never mistaken for validated (metadata: ${head.metadata})",
            gate == "true" || prototypeOnly == "true"
        )
    }

    @Test
    fun missingHeadDisablesSpecialistCleanly() {
        val sp = ChainAudioSpecialist.loadOrNull(context, headAsset = "no_such_head.json")
        log("missing head -> specialist ${if (sp == null) "disabled (null)" else "LOADED (bug)"}")
        assertTrue("A missing head must disable the specialist", sp == null)
    }
}
