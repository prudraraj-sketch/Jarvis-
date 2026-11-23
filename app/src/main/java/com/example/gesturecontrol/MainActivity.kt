package com.example.gesturecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.CAMERA] == true) startDetection()
            else status.text = "Camera permission is required."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        val faceLock = findViewById<Switch>(R.id.faceLock)
        faceLock.isChecked = GestureState.faceLock
        faceLock.setOnCheckedChangeListener { _, checked -> GestureState.faceLock = checked }

        findViewById<Button>(R.id.accessibilityBtn).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.startBtn).setOnClickListener { requestAndStart() }
        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            stopService(Intent(this, GestureService::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    GestureState.running,
                    GestureState.faceVisible,
                    GestureState.lastGesture
                ) { running, face, last ->
                    val a11y = if (GestureAccessibilityService.instance != null) "on" else "OFF"
                    "Detection: ${if (running) "running" else "stopped"}\n" +
                        "Face visible: ${if (face) "yes" else "no"}\n" +
                        "Accessibility service: $a11y\n" +
                        "Last gesture: $last"
                }.collect { status.text = it }
            }
        }
    }

    private fun requestAndStart() {
        val needed = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startDetection() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startDetection() {
        ContextCompat.startForegroundService(this, Intent(this, GestureService::class.java))
    }
}
