package com.roadside.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class AudioRecorder(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val NUM_CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        const val BYTE_RATE = SAMPLE_RATE * NUM_CHANNELS * (BITS_PER_SAMPLE / 8) // 32000 bytes/sec
        const val BLOCK_ALIGN = NUM_CHANNELS * (BITS_PER_SAMPLE / 8) // 2 bytes
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var currentOutputFile: File? = null
    private var rawPcmFile: File? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    /**
     * Starts a new recording.
     *
     * Refuses if a recording is already in progress. Previously a second call replaced
     * [audioRecord] and [recordingThread] without stopping the first AudioRecord, which then
     * leaked: it was never stopped or released, kept the microphone, left an orphaned .pcm
     * file, and every later recording in the process came back as pure digital silence.
     * The UI could trigger this by leaving the capture screen mid-recording and pressing
     * Start again.
     */
    @Synchronized
    @SuppressLint("MissingPermission")
    fun startRecording(): Result<File> {
        if (isRecording || audioRecord != null) {
            return Result.failure(IllegalStateException("A recording is already in progress"))
        }
        return try {
            val outputDir = File(context.cacheDir, "audio_diagnostics")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            // Orphaned .pcm files (left by the old double-start bug, or a crash mid-recording)
            // are never valid recordings; old WAVs are capped. Nothing is recording here.
            com.roadside.util.CacheFiles.prune(outputDir, "wav", alwaysDelete = setOf("pcm"))
            val timestamp = System.currentTimeMillis()
            val wavFile = File(outputDir, "diagnostic_$timestamp.wav")
            val pcmFile = File(outputDir, "diagnostic_$timestamp.pcm")
            currentOutputFile = wavFile
            rawPcmFile = pcmFile

            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ).coerceAtLeast(4096)

            var recorder: AudioRecord? = null

            // Prioritize UNPROCESSED to bypass voice/speech preprocessing filters
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    recorder = AudioRecord(
                        MediaRecorder.AudioSource.UNPROCESSED,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        minBufferSize * 2
                    )
                    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                        recorder.release()
                        recorder = null
                    }
                } catch (e: Exception) {
                    Log.w("AudioRecorder", "AudioSource.UNPROCESSED failed: ${e.message}")
                    recorder = null
                }
            }

            // Fallback to standard MIC if UNPROCESSED is unavailable on device/HAL
            if (recorder == null) {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    minBufferSize * 2
                )
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return Result.failure(IllegalStateException("AudioRecord failed to initialize (state ${recorder.state})"))
            }

            audioRecord = recorder
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                // e.g. the microphone is held by another app
                release()
                pcmFile.delete()
                return Result.failure(IllegalStateException("Microphone could not start recording (it may be in use by another app)"))
            }
            isRecording = true

            recordingThread = thread(start = true, name = "VehicleAudioRecordThread") {
                val buffer = ByteArray(minBufferSize)
                var fos: FileOutputStream? = null
                try {
                    fos = FileOutputStream(pcmFile)
                    while (isRecording) {
                        val bytesRead = recorder.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                    fos.flush()
                } catch (e: Exception) {
                    Log.e("AudioRecorder", "Error streaming PCM audio", e)
                } finally {
                    try {
                        fos?.close()
                    } catch (_: Exception) {}
                }
            }

            Result.success(wavFile)
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start audio recording", e)
            release()
            Result.failure(e)
        }
    }

    @Synchronized
    fun stopRecording(): Result<File> {
        return try {
            if (!isRecording) {
                return Result.failure(IllegalStateException("Not recording"))
            }
            haltAndRelease()

            val pcm = rawPcmFile
            val wav = currentOutputFile
            if (pcm != null && pcm.exists() && pcm.length() > 0 && wav != null) {
                convertPcmToWav(pcm, wav)
                pcm.delete() // clean up intermediate raw PCM file
                Result.success(wav)
            } else {
                Result.failure(IllegalStateException("Recorded PCM data was empty or missing"))
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop recording", e)
            release()
            Result.failure(e)
        }
    }

    private fun convertPcmToWav(pcmFile: File, wavFile: File) {
        val pcmSize = pcmFile.length()
        val totalDataLen = pcmSize
        val totalAudioLen = totalDataLen + 36

        FileOutputStream(wavFile).use { out ->
            // 44-byte RIFF/WAVE header
            val header = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                // ChunkID "RIFF"
                put('R'.code.toByte())
                put('I'.code.toByte())
                put('F'.code.toByte())
                put('F'.code.toByte())
                // ChunkSize: 36 + SubChunk2Size
                putInt(totalAudioLen.toInt())
                // Format "WAVE"
                put('W'.code.toByte())
                put('A'.code.toByte())
                put('V'.code.toByte())
                put('E'.code.toByte())
                // Subchunk1ID "fmt "
                put('f'.code.toByte())
                put('m'.code.toByte())
                put('t'.code.toByte())
                put(' '.code.toByte())
                // Subchunk1Size: 16 for PCM
                putInt(16)
                // AudioFormat: 1 (PCM)
                putShort(1.toShort())
                // NumChannels: 1 (Mono)
                putShort(NUM_CHANNELS.toShort())
                // SampleRate: 16000
                putInt(SAMPLE_RATE)
                // ByteRate: 32000
                putInt(BYTE_RATE)
                // BlockAlign: 2
                putShort(BLOCK_ALIGN.toShort())
                // BitsPerSample: 16
                putShort(BITS_PER_SAMPLE.toShort())
                // Subchunk2ID "data"
                put('d'.code.toByte())
                put('a'.code.toByte())
                put('t'.code.toByte())
                put('a'.code.toByte())
                // Subchunk2Size
                putInt(totalDataLen.toInt())
            }.array()

            out.write(header)
            pcmFile.inputStream().use { input ->
                input.copyTo(out)
            }
            out.flush()
        }
    }

    /**
     * Stops and discards the current recording, if any, deleting its files. Used when the
     * capture screen goes away or the app is backgrounded mid-recording: Android silences
     * background microphone capture, so continuing would only record zeros.
     */
    @Synchronized
    fun cancelRecording() {
        if (!isRecording && audioRecord == null) return
        haltAndRelease()
        rawPcmFile?.delete()
        currentOutputFile?.delete()
        rawPcmFile = null
        currentOutputFile = null
        Log.i("AudioRecorder", "Recording cancelled and discarded")
    }

    /** Ends the capture loop, stops the AudioRecord, waits for the writer, releases. */
    private fun haltAndRelease() {
        isRecording = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w("AudioRecorder", "Error stopping AudioRecord", e)
        }
        try {
            recordingThread?.join(1000)
        } catch (_: Exception) {}
        recordingThread = null
        release()
    }

    @Synchronized
    fun release() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w("AudioRecorder", "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
            isRecording = false
        }
    }
}
