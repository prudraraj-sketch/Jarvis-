package com.example.gesturecontrol

import kotlinx.coroutines.flow.MutableStateFlow

/** Shared state between the camera service and the UI. */
object GestureState {
    val running = MutableStateFlow(false)
    val faceVisible = MutableStateFlow(false)
    val lastGesture = MutableStateFlow("none yet")

    /** When true, gestures are ignored unless a face is currently detected. */
    @Volatile
    var faceLock: Boolean = true
}
