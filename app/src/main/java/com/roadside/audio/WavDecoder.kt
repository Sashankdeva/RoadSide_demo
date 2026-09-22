package com.roadside.audio

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes a RIFF WAV file written by [AudioRecorder] (16 kHz, mono, PCM 16-bit LE).
 *
 * Outputs a FloatArray of normalised samples in the range [-1.0, 1.0],
 * ready to be fed into YAMNet windows.
 */
object WavDecoder {

    private const val TAG = "WavDecoder"
    private const val WAV_HEADER_BYTES = 44
    private const val PCM_MAX = 32768.0f

    /**
     * Reads [wavFile], skips the 44-byte RIFF header, and converts the PCM
     * payload to float32 samples normalised to [-1, 1].
     *
     * @return FloatArray of samples, or an empty FloatArray on error.
     */
    fun decode(wavFile: File): FloatArray {
        if (!wavFile.exists() || wavFile.length() <= WAV_HEADER_BYTES) {
            Log.w(TAG, "WAV file missing or too small: ${wavFile.name} (${wavFile.length()} bytes)")
            return FloatArray(0)
        }

        return try {
            val rawBytes = wavFile.readBytes()
            // Validate RIFF magic
            if (rawBytes.size < 4 || rawBytes[0] != 'R'.code.toByte() || rawBytes[1] != 'I'.code.toByte()) {
                Log.w(TAG, "Not a valid RIFF file: ${wavFile.name}")
                return FloatArray(0)
            }

            val pcmBytes = rawBytes.size - WAV_HEADER_BYTES
            if (pcmBytes <= 0) {
                Log.w(TAG, "WAV has no PCM data: ${wavFile.name}")
                return FloatArray(0)
            }

            val numSamples = pcmBytes / 2 // 16-bit = 2 bytes per sample
            val buf = ByteBuffer.wrap(rawBytes, WAV_HEADER_BYTES, pcmBytes)
                .order(ByteOrder.LITTLE_ENDIAN)

            val samples = FloatArray(numSamples)
            for (i in 0 until numSamples) {
                samples[i] = buf.short / PCM_MAX
            }

            Log.d(TAG, "Decoded ${wavFile.name}: $numSamples samples (${rawBytes.size} bytes)")
            samples
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding WAV file: ${wavFile.name}", e)
            FloatArray(0)
        }
    }
}
