package com.openclaw.assistant.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.openclaw.assistant.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraFrameManager @Inject constructor() {

    private var analysisJob: Job? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val lastCapture = AtomicLong(0L)
    private val isCaptureEnabled = AtomicBoolean(true)

    /**
     * @param context    Application context (NOT cast from LifecycleOwner – bug fix over original)
     * @param lifecycle  LifecycleOwner for CameraX binding (OverlayService implements this)
     */
    fun startCapture(
        context: Context,
        lifecycle: LifecycleOwner,
        scope: CoroutineScope,
        wsClient: SecureWebSocketClient
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                val now = System.currentTimeMillis()
                if (isCaptureEnabled.get() && now - lastCapture.get() >= Constants.CAMERA_INTERVAL_MS) {
                    lastCapture.set(now)
                    try {
                        val bitmap = imageProxy.toBitmap()
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 55, baos)
                        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        wsClient.sendVisionFrame(b64)
                        bitmap.recycle()
                    } catch (e: Exception) {
                        Log.e(TAG, "Frame encode error: ${e.message}")
                    }
                }
                imageProxy.close()
            }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycle,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )
                Log.i(TAG, "Camera capture started")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed: ${e.message}")
            }

            analysisJob = scope.launch(Dispatchers.IO) {
                while (isActive) delay(10_000)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setCaptureEnabled(enabled: Boolean) { isCaptureEnabled.set(enabled) }

    fun stop() {
        analysisJob?.cancel()
        cameraProvider?.unbindAll()
        Log.i(TAG, "Camera capture stopped")
    }

    companion object {
        private const val TAG = "CameraFrameManager"
    }
}
