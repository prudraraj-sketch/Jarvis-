package com.example.gesturecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Performs only explicit, locally recognised user gesture actions.
 * This service does not retrieve window content or inspect screen text.
 */
class GestureAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: GestureAccessibilityService? = null
    }

    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // No accessibility events are requested or used.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun perform(gesture: Gesture) {
        main.post {
            if (!GestureConsent.hasAccessibilityConsent(this)) return@post

            when (gesture) {
                Gesture.SWIPE_LEFT -> swipe(-1f, 0f, 260)
                Gesture.SWIPE_RIGHT -> swipe(1f, 0f, 260)
                Gesture.SWIPE_UP -> swipe(0f, -1f, 260)
                Gesture.SWIPE_DOWN -> swipe(0f, 1f, 260)
                Gesture.SCROLL_UP -> swipe(0f, -0.6f, 320)
                Gesture.SCROLL_DOWN -> swipe(0f, 0.6f, 320)
                Gesture.OPEN_HOLD -> performGlobalAction(GLOBAL_ACTION_HOME)
                Gesture.FIST_HOLD -> performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    /** Dispatch a screen-centred swipe. Values are normalised direction multipliers. */
    private fun swipe(dx: Float, dy: Float, durationMs: Long) {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val cy = dm.heightPixels / 2f
        val vx = dx * 0.34f * dm.widthPixels
        val vy = dy * 0.34f * dm.heightPixels
        val path = Path().apply {
            moveTo(cx - vx, cy - vy)
            lineTo(cx + vx, cy + vy)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
