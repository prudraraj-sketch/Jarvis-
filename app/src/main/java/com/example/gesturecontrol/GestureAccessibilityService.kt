package com.example.gesturecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/** Performs the actual system actions. The user must enable it in Accessibility settings. */
class GestureAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: GestureAccessibilityService? = null
    }

    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Perform [gesture] as a system action. Safe to call from any thread. */
    fun perform(gesture: Gesture) = main.post {
        when (gesture) {
            Gesture.SWIPE_LEFT -> swipe(-1f, 0f, 250)
            Gesture.SWIPE_RIGHT -> swipe(1f, 0f, 250)
            Gesture.SWIPE_UP -> swipe(0f, -1f, 250)
            Gesture.SWIPE_DOWN -> swipe(0f, 1f, 250)
            Gesture.SCROLL_UP -> swipe(0f, -0.6f, 350)
            Gesture.SCROLL_DOWN -> swipe(0f, 0.6f, 350)
            Gesture.OPEN_HOLD -> performGlobalAction(GLOBAL_ACTION_HOME)
            Gesture.FIST_HOLD -> closeCurrentApp()
        }
    }

    /** Finger swipe through the screen centre. dx/dy are -1..1 (fraction of 35% of the screen). */
    private fun swipe(dx: Float, dy: Float, durationMs: Long) {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val cy = dm.heightPixels / 2f
        val vx = dx * 0.35f * dm.widthPixels
        val vy = dy * 0.35f * dm.heightPixels
        val path = Path().apply {
            moveTo(cx - vx, cy - vy)
            lineTo(cx + vx, cy + vy)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    /** Android has no "kill app" API for normal apps: open Recents, then flick the top card away. */
    private fun closeCurrentApp() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        main.postDelayed({ swipe(0f, -1.3f, 150) }, 800)
    }
}
