package com.example.gesturecontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground service: front camera -> MediaPipe hand + face models (bundled in assets, offline)
 * -> gesture classifier -> accessibility actions.
 */
class GestureService : LifecycleService() {

    companion object {
        private const val TAG = "GestureService"
        private const val CHANNEL_ID = "gesture_control"
        private const val FACE_GRACE_MS = 1500L
        private const val HAND_MODEL = "hand_landmarker.task"
        private const val FACE_MODEL = "blaze_face_short_range.tflite"
    }

    private lateinit var cameraExecutor: ExecutorService
    private var hand: HandLandmarker? = null
    private var face: FaceDetector? = null
    private var cameraBound = false

    @Volatile
    private var lastFaceSeenAt = 0L

    private val classifier = GestureClassifier { gesture -> handleGesture(gesture) }

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!cameraBound) {
            try {
                createModels()
                bindCamera()
                cameraBound = true
                GestureState.running.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start. Are the model files in app/src/main/assets?", e)
                GestureState.lastGesture.value = "Error: ${e.message}"
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        GestureState.running.value = false
        GestureState.faceVisible.value = false
        ProcessCameraProvider.getInstance(this).get().unbindAll()
        hand?.close()
        face?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun createModels() {
        hand = HandLandmarker.createFromOptions(
            this,
            HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(HAND_MODEL).build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.6f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    val landmarks = result.landmarks().firstOrNull()
                    if (landmarks == null || landmarks.size < 21) {
                        classifier.onNoHand()
                    } else {
                        classifier.onHand(landmarks, SystemClock.uptimeMillis())
                    }
                }
                .setErrorListener { Log.e(TAG, "Hand error", it) }
                .build()
        )

        face = FaceDetector.createFromOptions(
            this,
            FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(FACE_MODEL).build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMinDetectionConfidence(0.6f)
                .setResultListener { result, _ ->
                    val seen = result.detections().any {
                        (it.categories().firstOrNull()?.score() ?: 0f) >= 0.6f
                    }
                    if (seen) lastFaceSeenAt = SystemClock.uptimeMillis()
                    GestureState.faceVisible.value = seen
                }
                .setErrorListener { Log.e(TAG, "Face error", it) }
                .build()
        )
    }

    private fun bindCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(480, 360))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(cameraExecutor) { proxy -> analyze(proxy) }

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val raw = proxy.toBitmap()
            // Upright + mirrored, so "hand moves to my right" means x increases.
            val matrix = Matrix().apply {
                postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f)
            }
            val bitmap = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            val image = BitmapImageBuilder(bitmap).build()
            val ts = SystemClock.uptimeMillis()
            hand?.detectAsync(image, ts)
            face?.detectAsync(image, ts)
        } catch (e: Exception) {
            Log.e(TAG, "Frame error", e)
        } finally {
            proxy.close()
        }
    }

    private fun handleGesture(gesture: Gesture) {
        val now = SystemClock.uptimeMillis()
        if (GestureState.faceLock && now - lastFaceSeenAt > FACE_GRACE_MS) {
            GestureState.lastGesture.value = "$gesture ignored (no face)"
            return
        }
        GestureState.lastGesture.value = gesture.name
        GestureAccessibilityService.instance?.perform(gesture)
            ?: run { GestureState.lastGesture.value = "${gesture.name}: enable Accessibility service" }
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Gesture detection", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture Control is running")
            .setContentText("Front camera is on. Processing stays on this device.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(1, notification)
        }
    }
}
