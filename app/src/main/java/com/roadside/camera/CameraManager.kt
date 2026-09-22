package com.roadside.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

class CameraManager {

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Incremented by every [startCamera] and [unbind]. The provider future resolves
     * asynchronously, so a bind requested by a screen that has already gone away must not
     * happen; the listener only binds if its generation is still current.
     */
    private var generation = 0

    /** True while use cases are bound (camera open). Exposed for tests and diagnostics. */
    var isBound: Boolean = false
        private set

    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val myGeneration = ++generation
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            if (myGeneration != generation) {
                Log.i("CameraManager", "Camera start superseded before the provider was ready; not binding")
                return@addListener
            }
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture
                )
                isBound = true

                onReady()
            } catch (e: Exception) {
                Log.e("CameraManager", "Use case binding failed", e)
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun capturePhoto(
        context: Context,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val capture = imageCapture ?: run {
            onError(IllegalStateException("Camera capture is not initialized"))
            return
        }

        val outputDir = File(context.cacheDir, "camera_captures")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        com.roadside.util.CacheFiles.prune(outputDir, "jpg")
        val photoFile = File(outputDir, "capture_${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSuccess(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraManager", "Photo capture failed: ${exception.message}", exception)
                    onError(exception)
                }
            }
        )
    }

    /**
     * Closes the camera. Must be called when the camera preview leaves the screen: use cases
     * are bound to the Activity's lifecycle, so without this the camera stayed open (privacy
     * indicator on, battery drain, camera unavailable to other apps) until the Activity was
     * destroyed.
     */
    fun unbind() {
        generation++
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w("CameraManager", "Error unbinding camera", e)
        } finally {
            imageCapture = null
            if (isBound) Log.i("CameraManager", "Camera unbound")
            isBound = false
        }
    }
}
