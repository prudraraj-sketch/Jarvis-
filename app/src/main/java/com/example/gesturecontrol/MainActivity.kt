package com.example.gesturecontrol

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var consentStatus: TextView

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startDetection()
            else {
                status.text = "Camera permission denied. Gesture detection is stopped."
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val root = findViewById<android.view.View>(R.id.root)
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                baseLeft + bars.left,
                baseTop + bars.top,
                baseRight + bars.right,
                baseBottom + bars.bottom
            )
            insets
        }

        status = findViewById(R.id.status)
        consentStatus = findViewById(R.id.consentStatus)

        findViewById<Button>(R.id.accessibilityBtn).setOnClickListener {
            if (!requireConsent()) return@setOnClickListener
            openAccessibilitySettings()
        }
        findViewById<Button>(R.id.startBtn).setOnClickListener { requestAndStart() }
        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            stopService(Intent(this, GestureService::class.java))
            GestureState.running.value = false
        }
        findViewById<Button>(R.id.privacyBtn).setOnClickListener {
            showAccessibilityDisclosure()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(GestureState.running, GestureState.lastGesture) { running, last ->
                    "Detection: " + (if (running) "running" else "stopped") + "\n" +
                        "Accessibility service: " +
                        (if (isAccessibilityServiceEnabled()) "enabled" else "not enabled") + "\n" +
                        "Last gesture: " + last
                }.collect {
                    status.text = it
                    refreshConsentStatus()
                }
            }
        }

        if (!GestureConsent.hasAccessibilityConsent(this)) {
            showAccessibilityDisclosure()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            status.text =
                "Detection: " + (if (GestureState.running.value) "running" else "stopped") + "\n" +
                    "Accessibility service: " +
                    (if (isAccessibilityServiceEnabled()) "enabled" else "not enabled") + "\n" +
                    "Last gesture: " + GestureState.lastGesture.value
            refreshConsentStatus()
        }
    }

    private fun refreshConsentStatus() {
        if (!::consentStatus.isInitialized) return
        consentStatus.text = if (GestureConsent.hasAccessibilityConsent(this)) {
            "Accessibility consent: granted. You can withdraw it here at any time."
        } else {
            "Accessibility consent: not granted. Gesture actions are disabled."
        }
    }

    private fun requireConsent(): Boolean {
        if (GestureConsent.hasAccessibilityConsent(this)) return true
        showAccessibilityDisclosure()
        return false
    }

    private fun showAccessibilityDisclosure() {
        val accepted = GestureConsent.hasAccessibilityConsent(this)
        val message = """
            GestureControl is a general-purpose gesture-control app. It uses Android Accessibility access only to perform the actions listed below after you make a recognised hand gesture.

            • Send on-screen swipe and scroll gestures.
            • Use the Android Home action when you hold an open palm.
            • Use the Android Back action when you hold a fist.

            The Accessibility service does not read screen text, window contents, notifications or keystrokes. It does not take screenshots or record the screen. It does not make decisions or perform actions without a recognised hand gesture from you.

            The front camera is used to identify hand landmarks. Camera frames are processed on this device, are not saved, and are not sent to a server. The app does not request internet access for gesture recognition.

            You can stop camera detection from the app or its persistent notification. You can disable the Accessibility service at any time in Android Settings.
        """.trimIndent()

        val builder = AlertDialog.Builder(this)
            .setTitle("How GestureControl uses Accessibility")
            .setMessage(message)
            .setPositiveButton(if (accepted) "Keep consent active" else "Agree and continue") { _, _ ->
                GestureConsent.grantAccessibilityConsent(this)
                refreshConsentStatus()
            }
            .setNegativeButton(if (accepted) "Cancel" else "Not now", null)

        if (accepted) {
            builder.setNeutralButton("Withdraw consent") { _, _ ->
                GestureConsent.revokeAccessibilityConsent(this)
                stopService(Intent(this, GestureService::class.java))
                GestureState.running.value = false
                GestureState.lastGesture.value = "consent withdrawn"
                refreshConsentStatus()
                Toast.makeText(
                    this,
                    "Consent withdrawn. Disable Gesture Control in Android Accessibility settings too.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        builder.create().show()
    }

    private fun requestAndStart() {
        if (!requireConsent()) return

        if (!isAccessibilityServiceEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Enable gesture actions")
                .setMessage(
                    "To send swipes and use Home and Back, enable Gesture Control in Android Accessibility settings. Android controls this system-level permission."
                )
                .setPositiveButton("Open settings") { _, _ -> openAccessibilitySettings() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startDetection()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, GestureAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        return enabled.split(':').any { value ->
            ComponentName.unflattenFromString(value.trim()) == expected
        }
    }

    private fun startDetection() {
        if (!GestureConsent.hasAccessibilityConsent(this) || !isAccessibilityServiceEnabled()) {
            status.text = "Grant consent and enable Accessibility before starting."
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, GestureService::class.java))
    }
}
