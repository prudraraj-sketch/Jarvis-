package com.example.gesturecontrol

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.hypot

enum class Gesture {
    SWIPE_LEFT, SWIPE_RIGHT, SWIPE_UP, SWIPE_DOWN,
    SCROLL_UP, SCROLL_DOWN,
    OPEN_HOLD, FIST_HOLD
}

enum class Posture { OPEN_PALM, FIST, PEACE, OTHER }

/**
 * Turns a stream of hand landmarks (selfie-mirrored, upright, normalised 0..1)
 * into discrete gestures. Directions are the direction the HAND moves as the user sees it.
 */
class GestureClassifier(private val onGesture: (Gesture) -> Unit) {

    private data class Sample(val t: Long, val x: Float, val y: Float)

    private val history = ArrayDeque<Sample>()
    private var lastPosture = Posture.OTHER

    // Hold detection (open palm / fist kept still)
    private var anchorX = 0f
    private var anchorY = 0f
    private var stillSince = 0L
    private var holdFired = false

    // Cooldowns
    private var lastSwipeAt = 0L
    private var lastScrollAt = 0L
    private var scrollAnchorY = 0f

    // Tunables
    private val swipeWindowMs = 400L
    private val swipeDistance = 0.28f
    private val swipeCooldownMs = 1000L
    private val scrollStep = 0.07f
    private val scrollCooldownMs = 250L
    private val holdMs = 1000L
    private val stillRadius = 0.06f

    fun onNoHand() {
        history.clear()
        lastPosture = Posture.OTHER
        holdFired = false
    }

    fun onHand(lm: List<NormalizedLandmark>, now: Long): Posture {
        val posture = postureOf(lm)
        val palm = lm[9] // middle finger base: stable palm centre
        val x = palm.x()
        val y = palm.y()

        if (posture != lastPosture) {
            history.clear()
            anchorX = x; anchorY = y
            stillSince = now
            holdFired = false
            scrollAnchorY = y
            lastPosture = posture
        }

        when (posture) {
            Posture.OPEN_PALM -> {
                trackSwipe(now, x, y)
                trackHold(now, x, y, Gesture.OPEN_HOLD)
            }
            Posture.FIST -> trackHold(now, x, y, Gesture.FIST_HOLD)
            Posture.PEACE -> trackScroll(now, y)
            Posture.OTHER -> Unit
        }
        return posture
    }

    private fun trackSwipe(now: Long, x: Float, y: Float) {
        history.addLast(Sample(now, x, y))
        while (history.isNotEmpty() && now - history.first().t > swipeWindowMs) history.removeFirst()
        if (history.size < 4 || now - lastSwipeAt < swipeCooldownMs) return

        val dx = x - history.first().x
        val dy = y - history.first().y
        val g = when {
            abs(dx) > swipeDistance && abs(dx) > 2 * abs(dy) ->
                if (dx > 0) Gesture.SWIPE_RIGHT else Gesture.SWIPE_LEFT
            abs(dy) > swipeDistance && abs(dy) > 2 * abs(dx) ->
                if (dy > 0) Gesture.SWIPE_DOWN else Gesture.SWIPE_UP
            else -> null
        }
        if (g != null) {
            lastSwipeAt = now
            history.clear()
            holdFired = true // a moving hand is not a hold
            onGesture(g)
        }
    }

    private fun trackHold(now: Long, x: Float, y: Float, gesture: Gesture) {
        if (hypot(x - anchorX, y - anchorY) > stillRadius) {
            anchorX = x; anchorY = y
            stillSince = now
            return
        }
        if (!holdFired && now - stillSince >= holdMs) {
            holdFired = true
            onGesture(gesture)
        }
    }

    private fun trackScroll(now: Long, y: Float) {
        val dy = y - scrollAnchorY
        if (abs(dy) > scrollStep && now - lastScrollAt > scrollCooldownMs) {
            lastScrollAt = now
            scrollAnchorY = y
            onGesture(if (dy > 0) Gesture.SCROLL_DOWN else Gesture.SCROLL_UP)
        }
    }

    // --- posture from landmarks (rotation independent: compares distances to the wrist) ---

    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark) =
        hypot(a.x() - b.x(), a.y() - b.y())

    private fun extended(lm: List<NormalizedLandmark>, tip: Int, pip: Int) =
        dist(lm[tip], lm[0]) > dist(lm[pip], lm[0]) * 1.1f

    private fun postureOf(lm: List<NormalizedLandmark>): Posture {
        val index = extended(lm, 8, 6)
        val middle = extended(lm, 12, 10)
        val ring = extended(lm, 16, 14)
        val pinky = extended(lm, 20, 18)
        val count = listOf(index, middle, ring, pinky).count { it }
        return when {
            count == 4 -> Posture.OPEN_PALM
            count == 0 -> Posture.FIST
            index && middle && !ring && !pinky -> Posture.PEACE
            else -> Posture.OTHER
        }
    }
}
