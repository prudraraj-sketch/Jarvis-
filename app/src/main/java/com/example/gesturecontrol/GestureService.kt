package com.example.gesturecontrol

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Camera foreground service. Frames are analysed locally for hand landmarks only.
 * No frames or landmarks are saved or transmitted.
 */
class GestureService : LifecycleService() {

    companion object {
        private const val TAG = "GestureService"
        private const val CHANNEL_ID = "gesture_control"
        private const val HAND_MODEL = "hand_landmarker.task"
        private const val ACTION_STOP = "com.example.gesturecontrol.action.STOP"
    }

    private lateinit var cameraExecutor: ExecutorService
    private var hand: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    @Volatile private var cameraBinding = false
    @Volatile private var isStopping = false

    private val classifier = GestureClassifier { gesture -> handleGesture(gesture) }

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!GestureConsent.hasAccessibilityConsent(this)) {
            GestureState.lastGesture.value = "Start blocked: consent not granted"
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            GestureState.lastGesture.value = "Start blocked: camera permission missing"
            stopSelf()
            return START_NOT_STICKY
        }

        if (hand == null && !cameraBinding) {
            try {
                createHandModel()
                bindCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to initialise hand detection", e)
                GestureState.lastGesture.value = "Startup error: " + (e.message ?: "unknown error")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isStopping = true
        GestureState.running.value = false
        cameraProvider?.unbindAll()
        hand?.close()
        hand = null
        cameraExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun createHandModel() {
        hand = HandLandmarker.createFromOptions(
            this,
            HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(HAND_MODEL).build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.6f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    if (isStopping) return@setResultListener
                    val landmarks = result.landmarks().firstOrNull()
                    if (landmarks == null || landmarks.size < 21) {
                        classifier.onNoHand()
                    } else {
                        classifier.onHand(landmarks, SystemClock.uptimeMillis())
                    }
                }
                .setErrorListener { Log.e(TAG, "Hand landmark error", it) }
                .build()
        )
    }

    private fun bindCamera() {
        cameraBinding = true
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (isStopping || !GestureConsent.hasAccessibilityConsent(this)) {
                stopSelf()
                return@addListener
            }

            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 360))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { proxy -> analyze(proxy) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                GestureState.running.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Unable to bind the front camera", e)
                GestureState.lastGesture.value = "Camera error: " + (e.message ?: "unavailable")
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            if (isStopping) return
            val raw: Bitmap = proxy.toBitmap()
            val transform = Matrix().apply {
                postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f)
            }
            val uprightMirrored = Bitmap.createBitmap(
                raw, 0, 0, raw.width, raw.height, transform, true
            )
            val image = BitmapImageBuilder(uprightMirrored).build()
            hand?.detectAsync(image, SystemClock.uptimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis failed", e)
        } finally {
            proxy.close()
        }
    }

    private fun handleGesture(gesture: Gesture) {
        if (isStopping || !GestureConsent.hasAccessibilityConsent(this)) return
        GestureState.lastGesture.value = gesture.name
        val accessibility = GestureAccessibilityService.instance
        if (accessibility == null) {
            GestureState.lastGesture.value = gesture.name + ": enable Accessibility service"
        } else {
            accessibility.perform(gesture)
        }
    }

    private fun startInForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Gesture detection",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val stopIntent = Intent(this, GestureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            100,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture Control is running")
            .setContentText("Front camera is analysing hand gestures on this device.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(1, notification)
        }
    }
}
