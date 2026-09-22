package com.roadside.sensing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Turns a captured JPEG into the tensor MobileNetV3 expects.
 *
 * Steps, in order:
 *   1. decode (downsampled during decode so a 12 MP capture never fully enters memory)
 *   2. apply EXIF rotation — CameraX records orientation in EXIF rather than rotating pixels,
 *      so skipping this feeds the model sideways images
 *   3. centre-crop to square — preserves aspect ratio instead of squashing the chain
 *   4. scale to 224x224
 *   5. normalise to [0, 1] float32, NHWC
 *
 * The [0, 1] range is the TF-Hub convention for the `mobilenet_v3/feature_vector` family
 * that [MobileNetFeatureExtractor] loads. Feeding [-1, 1] or raw 0..255 produces features
 * that are silently wrong rather than an error, so this must stay in step with the model.
 */
object ImagePreprocessor {

    private const val TAG = "ImagePreprocessor"

    const val INPUT_SIZE = 224
    const val CHANNELS = 3

    class DecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * @return float32 array shaped [1][224][224][3], values in [0, 1].
     */
    fun toModelInput(imageFile: File): Array<Array<Array<FloatArray>>> {
        val bitmap = decodeOriented(imageFile)
        try {
            val square = centreCropSquare(bitmap)
            val scaled = Bitmap.createScaledBitmap(square, INPUT_SIZE, INPUT_SIZE, true)
            if (square !== bitmap) square.recycle()
            try {
                return toNormalisedTensor(scaled)
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Decodes [file], downsampling large images, and applies its EXIF rotation. */
    fun decodeOriented(file: File): Bitmap {
        if (!file.exists() || file.length() == 0L) {
            throw DecodeException("Image missing or empty: ${file.name}")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw DecodeException("Not a decodable image: ${file.name}")
        }

        // Decode no smaller than the model input so we never upscale from a thumbnail.
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= INPUT_SIZE &&
            bounds.outHeight / (sample * 2) >= INPUT_SIZE
        ) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: throw DecodeException("Decoder returned null for ${file.name}")

        val rotation = readExifRotation(file)
        if (rotation == 0f) return raw

        val rotated = try {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                Matrix().apply { postRotate(rotation) }, true)
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply EXIF rotation to ${file.name}", e)
            return raw
        }
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    private fun readExifRotation(file: File): Float = try {
        when (ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        Log.w(TAG, "No readable EXIF for ${file.name}: ${e.message}")
        0f
    }

    private fun centreCropSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        if (src.width == src.height) return src
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        return Bitmap.createBitmap(src, x, y, side, side)
    }

    private fun toNormalisedTensor(bmp: Bitmap): Array<Array<Array<FloatArray>>> {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bmp.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val out = Array(1) { Array(INPUT_SIZE) { Array(INPUT_SIZE) { FloatArray(CHANNELS) } } }
        var i = 0
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val p = pixels[i++]
                val px = out[0][y][x]
                px[0] = ((p shr 16) and 0xFF) / 255f
                px[1] = ((p shr 8) and 0xFF) / 255f
                px[2] = (p and 0xFF) / 255f
            }
        }
        return out
    }
}
