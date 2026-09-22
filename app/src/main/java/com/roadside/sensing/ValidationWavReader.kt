package com.roadside.sensing

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Chunk-walking WAV reader used ONLY by the validation harness.
 *
 * [com.roadside.audio.WavDecoder] assumes the exact format our own [com.roadside.audio.AudioRecorder]
 * writes (16 kHz, mono, 16-bit, 44-byte header). Recordings supplied from outside the app —
 * phone voice recorders, action cams, downloaded clips — are routinely 44.1/48 kHz, stereo,
 * 24/32-bit, or carry extra chunks (LIST/INFO) before `data`. Feeding those through the
 * fixed-offset decoder produces silent garbage rather than an error, which would invalidate
 * every validation result.
 *
 * This reader parses the real chunk table, downmixes to mono and resamples to 16 kHz so that
 * arbitrary test recordings reach YAMNet in the format it expects.
 *
 * The production path is untouched — nothing here is wired into the app flow.
 */
object ValidationWavReader {

    private const val TAG = "ValidationWavReader"
    const val TARGET_SAMPLE_RATE = 16000

    data class Decoded(
        val samples: FloatArray,
        val sourceSampleRate: Int,
        val sourceChannels: Int,
        val sourceBitsPerSample: Int,
        val sourceFormat: Int,
        /** Duration in seconds of the returned 16 kHz mono signal. */
        val durationSeconds: Float,
        val resampled: Boolean,
        val downmixed: Boolean
    )

    class WavFormatException(message: String) : Exception(message)

    /**
     * Decodes [file] to mono float32 samples at [TARGET_SAMPLE_RATE], normalised to [-1, 1].
     *
     * @throws WavFormatException if the file is not a WAV we can interpret.
     */
    fun decode(file: File): Decoded {
        val bytes = file.readBytes()
        if (bytes.size < 12) throw WavFormatException("File too small to be a WAV (${bytes.size} bytes)")

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (tag(bytes, 0) != "RIFF") throw WavFormatException("Missing RIFF magic (found '${tag(bytes, 0)}')")
        if (tag(bytes, 8) != "WAVE") throw WavFormatException("Not a WAVE file (found '${tag(bytes, 8)}')")

        var audioFormat = -1
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataLength = 0

        // Walk the chunk table rather than assuming a 44-byte header.
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val chunkId = tag(bytes, pos)
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize < 0) throw WavFormatException("Corrupt chunk size in '$chunkId'")
            val body = pos + 8

            when (chunkId) {
                "fmt " -> {
                    if (body + 16 > bytes.size) throw WavFormatException("Truncated fmt chunk")
                    audioFormat = buf.getShort(body).toInt() and 0xFFFF
                    channels = buf.getShort(body + 2).toInt() and 0xFFFF
                    sampleRate = buf.getInt(body + 4)
                    bitsPerSample = buf.getShort(body + 14).toInt() and 0xFFFF
                    // WAVE_FORMAT_EXTENSIBLE carries the real format in the extension GUID.
                    if (audioFormat == 0xFFFE && body + 26 <= bytes.size) {
                        audioFormat = buf.getShort(body + 24).toInt() and 0xFFFF
                    }
                }
                "data" -> {
                    dataOffset = body
                    // Some writers leave a placeholder size; clamp to what is actually present.
                    dataLength = minOf(chunkSize, bytes.size - body)
                }
            }
            // Chunks are word-aligned: odd sizes carry a pad byte.
            pos = body + chunkSize + (chunkSize and 1)
        }

        if (dataOffset < 0) throw WavFormatException("No 'data' chunk found")
        if (channels <= 0 || sampleRate <= 0) throw WavFormatException("No usable 'fmt ' chunk found")
        if (dataLength <= 0) throw WavFormatException("'data' chunk is empty")

        // 1 = PCM integer, 3 = IEEE float
        val interleaved = when {
            audioFormat == 1 && bitsPerSample == 16 -> readPcm16(bytes, dataOffset, dataLength)
            audioFormat == 1 && bitsPerSample == 8  -> readPcm8(bytes, dataOffset, dataLength)
            audioFormat == 1 && bitsPerSample == 24 -> readPcm24(bytes, dataOffset, dataLength)
            audioFormat == 1 && bitsPerSample == 32 -> readPcm32(bytes, dataOffset, dataLength)
            audioFormat == 3 && bitsPerSample == 32 -> readFloat32(bytes, dataOffset, dataLength)
            else -> throw WavFormatException(
                "Unsupported WAV encoding: format=$audioFormat bits=$bitsPerSample " +
                    "(supported: PCM 8/16/24/32-bit, IEEE float 32-bit). Re-export as 16-bit PCM."
            )
        }

        val mono = if (channels == 1) interleaved else downmix(interleaved, channels)
        val resampled = if (sampleRate == TARGET_SAMPLE_RATE) mono else resampleLinear(mono, sampleRate, TARGET_SAMPLE_RATE)

        Log.i(
            TAG,
            "${file.name}: fmt=$audioFormat ${sampleRate}Hz ${channels}ch ${bitsPerSample}bit -> " +
                "${resampled.size} samples @ ${TARGET_SAMPLE_RATE}Hz"
        )

        return Decoded(
            samples = resampled,
            sourceSampleRate = sampleRate,
            sourceChannels = channels,
            sourceBitsPerSample = bitsPerSample,
            sourceFormat = audioFormat,
            durationSeconds = resampled.size / TARGET_SAMPLE_RATE.toFloat(),
            resampled = sampleRate != TARGET_SAMPLE_RATE,
            downmixed = channels > 1
        )
    }

    private fun tag(bytes: ByteArray, offset: Int): String {
        if (offset + 4 > bytes.size) return ""
        return String(bytes, offset, 4, Charsets.US_ASCII)
    }

    private fun readPcm16(bytes: ByteArray, offset: Int, length: Int): FloatArray {
        val n = length / 2
        val buf = ByteBuffer.wrap(bytes, offset, n * 2).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(n) { buf.short / 32768.0f }
    }

    private fun readPcm8(bytes: ByteArray, offset: Int, length: Int): FloatArray {
        // 8-bit WAV is unsigned, centred on 128.
        return FloatArray(length) { i -> ((bytes[offset + i].toInt() and 0xFF) - 128) / 128.0f }
    }

    private fun readPcm24(bytes: ByteArray, offset: Int, length: Int): FloatArray {
        val n = length / 3
        return FloatArray(n) { i ->
            val p = offset + i * 3
            val lo = bytes[p].toInt() and 0xFF
            val mid = bytes[p + 1].toInt() and 0xFF
            val hi = bytes[p + 2].toInt() // signed: carries the sign bit
            ((hi shl 16) or (mid shl 8) or lo) / 8388608.0f
        }
    }

    private fun readPcm32(bytes: ByteArray, offset: Int, length: Int): FloatArray {
        val n = length / 4
        val buf = ByteBuffer.wrap(bytes, offset, n * 4).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(n) { buf.int / 2147483648.0f }
    }

    private fun readFloat32(bytes: ByteArray, offset: Int, length: Int): FloatArray {
        val n = length / 4
        val buf = ByteBuffer.wrap(bytes, offset, n * 4).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(n) { buf.float }
    }

    /** Averages all channels into one. */
    private fun downmix(interleaved: FloatArray, channels: Int): FloatArray {
        val frames = interleaved.size / channels
        val out = FloatArray(frames)
        for (f in 0 until frames) {
            var acc = 0f
            val base = f * channels
            for (c in 0 until channels) acc += interleaved[base + c]
            out[f] = acc / channels
        }
        return out
    }

    /**
     * Linear-interpolation resampler.
     *
     * Adequate for 44.1/48 kHz -> 16 kHz validation clips: it has no anti-alias filter, so
     * content above 8 kHz folds back. Brake squeal energy sits near/above that edge, so treat
     * high-frequency results from resampled clips as indicative and prefer recording the
     * validation set at 16 kHz directly.
     */
    private fun resampleLinear(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (input.isEmpty()) return input
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLen = Math.floor(input.size / ratio).toInt()
        if (outLen <= 0) return FloatArray(0)
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val src = i * ratio
            val i0 = src.toInt()
            val i1 = minOf(i0 + 1, input.size - 1)
            val frac = (src - i0).toFloat()
            out[i] = input[i0] * (1f - frac) + input[i1] * frac
        }
        return out
    }
}
