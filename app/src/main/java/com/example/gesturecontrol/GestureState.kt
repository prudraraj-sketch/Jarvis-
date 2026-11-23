package com.example.gesturecontrol

import kotlinx.coroutines.flow.MutableStateFlow

/** In-process UI state shared by the activity and camera service. */
object GestureState {
    val running = MutableStateFlow(false)
    val lastGesture = MutableStateFlow("none yet")
}
