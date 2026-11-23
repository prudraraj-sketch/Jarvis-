package com.example.gesturecontrol

import android.content.Context

/** Stores the user's explicit consent for this app's general-purpose Accessibility actions. */
object GestureConsent {
    private const val PREFS_NAME = "gesture_control_privacy"
    private const val KEY_ACCESSIBILITY_CONSENT = "accessibility_consent_v1"

    fun hasAccessibilityConsent(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCESSIBILITY_CONSENT, false)

    fun grantAccessibilityConsent(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCESSIBILITY_CONSENT, true)
            .apply()
    }

    fun revokeAccessibilityConsent(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCESSIBILITY_CONSENT, false)
            .apply()
    }
}
