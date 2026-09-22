package com.roadside

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadside.audio.AudioRecorder
import com.roadside.audio.WavDecoder
import com.roadside.sensing.AudioClassificationResult
import com.roadside.sensing.YamNetAudioClassifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Regression tests for the AudioRecord leak reproduced on the OnePlus 13R on 2026-09-22:
 * a second startRecording() replaced the first AudioRecord without stopping it, the leaked
 * recorder kept the microphone, and every later recording was 100 % digital zeros.
 *
 * Uses the real microphone, so RECORD_AUDIO must be granted on the device. Output is tagged
 * [TAG] in logcat.
 */
@RunWith(AndroidJUnit4::class)
class AudioRecorderLifecycleTest {

    companion object {
        const val TAG = "RoadSideRecorder"
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(msg: String) = Log.i(TAG, msg)

    private fun zeroFraction(samples: FloatArray) =
        if (samples.isEmpty()) 1f else samples.count { it == 0f }.toFloat() / samples.size

    private fun pcmFiles() = File(context.cacheDir, "audio_diagnostics")
        .listFiles { f -> f.extension == "pcm" }?.toSet() ?: emptySet()

    @Test
    fun secondStartIsRefusedAndMicStaysUsable() {
        assumeTrue("RECORD_AUDIO not granted",
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED)
        val pcmBefore = pcmFiles()
        val rec = AudioRecorder(context)

        log("========== RECORDER LIFECYCLE ==========")
        val first = rec.startRecording()
        assertTrue("First start must succeed: ${first.exceptionOrNull()}", first.isSuccess)
        Thread.sleep(500)

        // The exact action that used to leak the first AudioRecord.
        val second = rec.startRecording()
        log("second start while recording -> ${if (second.isSuccess) "ACCEPTED (bug)" else "refused: ${second.exceptionOrNull()?.message}"}")
        assertFalse("A second start while recording must be refused", second.isSuccess)

        Thread.sleep(1500)
        val stopped = rec.stopRecording()
        assertTrue("Stop must succeed: ${stopped.exceptionOrNull()}", stopped.isSuccess)
        val a = WavDecoder.decode(stopped.getOrThrow())
        log("recording 1: ${a.size} samples, exact zeros ${"%.1f".format(zeroFraction(a) * 100)}%")

        // If anything had leaked, the mic would now be held and this would be all zeros.
        assertTrue(rec.startRecording().isSuccess)
        Thread.sleep(2000)
        val b = WavDecoder.decode(rec.stopRecording().getOrThrow())
        log("recording 2 (after refused double start): ${b.size} samples, exact zeros ${"%.1f".format(zeroFraction(b) * 100)}%")

        val orphaned = pcmFiles() - pcmBefore
        log("orphaned .pcm files created: ${orphaned.size}")
        log("========================================")

        assertTrue("Recording 1 is digital silence", zeroFraction(a) < 0.5f)
        assertTrue("Recording 2 is digital silence — the microphone was leaked", zeroFraction(b) < 0.5f)
        assertEquals("No orphaned .pcm may be left behind", 0, orphaned.size)
    }

    @Test
    fun cancelDiscardsAndReleasesMic() {
        assumeTrue("RECORD_AUDIO not granted",
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED)
        val rec = AudioRecorder(context)
        val pcmPre = pcmFiles()
        val started = rec.startRecording()
        assertTrue(started.isSuccess)
        val wavPath = started.getOrThrow()
        Thread.sleep(800)
        val thisPcm = pcmFiles() - pcmPre
        assertEquals("Recording should be streaming to exactly one .pcm", 1, thisPcm.size)

        // What the capture screen now does when it is left, or the app is backgrounded.
        rec.cancelRecording()
        assertFalse("Recorder must not be recording after cancel", rec.isRecording)
        assertFalse("Cancelled WAV must not exist", wavPath.exists())
        val leftover = thisPcm.filter { it.exists() }
        assertTrue("Cancelled .pcm must be deleted: $leftover", leftover.isEmpty())

        assertTrue("Mic must be usable after cancel", rec.startRecording().isSuccess)
        Thread.sleep(1500)
        val s = WavDecoder.decode(rec.stopRecording().getOrThrow())
        log("after cancel: new recording ${s.size} samples, exact zeros ${"%.1f".format(zeroFraction(s) * 100)}%")
        assertTrue("Recording after cancel is digital silence", zeroFraction(s) < 0.5f)
    }

    @Test
    fun digitallySilentRecordingIsReportedNotCalledBackground() {
        // Same format AudioRecorder writes, all samples exactly zero — what the leaked
        // recorder produced.
        val wav = File(context.cacheDir, "silent_probe.wav")
        val n = 16000 * 3
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + n * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(16000); putInt(32000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(n * 2)
        }
        FileOutputStream(wav).use { it.write(header.array()); it.write(ByteArray(n * 2)) }

        val classifier = YamNetAudioClassifier(context)
        val r = runBlocking { classifier.classifyAudio(wav, "") }
        classifier.close()
        wav.delete()
        log("all-zero recording -> ${r.label}: \"${r.labelDescription}\" (${r.reason})")
        assertEquals(AudioClassificationResult.UNKNOWN, r.label)
        assertTrue(r.labelDescription.contains("microphone", ignoreCase = true))
    }
}
