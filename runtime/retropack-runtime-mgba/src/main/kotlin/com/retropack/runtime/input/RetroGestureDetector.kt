package com.retropack.runtime.input

import android.view.MotionEvent

/**
 * High-Precision Multi-Touch Gesture Shortcut Interpreter for RetroPack.
 *
 * Implements specifications from:
 *  - PPSSPP (GestureManager.cpp) and Pizza Boy GBA multi-touch gesture engine.
 *  - 2-Finger Swipe Left: Instant Quick Save State.
 *  - 2-Finger Swipe Right: Instant Quick Load State.
 *  - 2-Finger Double-Tap: Toggle Fast-Forward on/off.
 *  - 3-Finger Tap: Toggle Quick Menu visibility.
 */
class RetroGestureDetector(
    var gesturesEnabled: Boolean = true
) {
    var swipeThresholdPx: Float = 70f
    var maxVerticalSwipeDeviationPx: Float = 60f
    var doubleTapTimeoutMs: Long = 350L
    var tapMaxDurationMs: Long = 280L
    var tapMaxDistancePx: Float = 35f

    // Callbacks
    var onTwoFingerSwipeLeft: (() -> Unit)? = null
    var onTwoFingerSwipeRight: (() -> Unit)? = null
    var onTwoFingerDoubleTap: (() -> Unit)? = null
    var onThreeFingerTap: (() -> Unit)? = null

    // Tracking state
    private var twoFingerStartTime: Long = 0L
    private var twoFingerStartX0: Float = 0f
    private var twoFingerStartY0: Float = 0f
    private var twoFingerStartX1: Float = 0f
    private var twoFingerStartY1: Float = 0f
    private var isTwoFingerGestureActive: Boolean = false
    private var isTwoFingerSwipeFired: Boolean = false

    private var lastTwoFingerTapTimestamp: Long = 0L

    private var threeFingerStartTime: Long = 0L
    private var isThreeFingerGestureActive: Boolean = false
    private var threeFingerMaxDist: Float = 0f
    private var threeFingerStartX: Float = 0f
    private var threeFingerStartY: Float = 0f

    /**
     * Interprets incoming [MotionEvent] and triggers corresponding multi-touch gesture shortcuts.
     *
     * @return true if a multi-touch gesture was recognized and consumed.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        if (!gesturesEnabled) return false

        val action = event.actionMasked
        val pointerCount = event.pointerCount
        val now = System.currentTimeMillis()

        when (action) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerCount == 2) {
                    twoFingerStartTime = now
                    twoFingerStartX0 = event.getX(0)
                    twoFingerStartY0 = event.getY(0)
                    twoFingerStartX1 = event.getX(1)
                    twoFingerStartY1 = event.getY(1)
                    isTwoFingerGestureActive = true
                    isTwoFingerSwipeFired = false
                } else if (pointerCount == 3) {
                    threeFingerStartTime = now
                    threeFingerStartX = event.getX(0)
                    threeFingerStartY = event.getY(0)
                    threeFingerMaxDist = 0f
                    isThreeFingerGestureActive = true
                    isTwoFingerGestureActive = false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTwoFingerGestureActive && pointerCount >= 2 && !isTwoFingerSwipeFired) {
                    val x0 = event.getX(0)
                    val y0 = event.getY(0)
                    val x1 = event.getX(1)
                    val y1 = event.getY(1)

                    val dx0 = x0 - twoFingerStartX0
                    val dy0 = Math.abs(y0 - twoFingerStartY0)
                    val dx1 = x1 - twoFingerStartX1
                    val dy1 = Math.abs(y1 - twoFingerStartY1)

                    // 2-Finger Swipe Left
                    if (dx0 < -swipeThresholdPx && dx1 < -swipeThresholdPx &&
                        dy0 < maxVerticalSwipeDeviationPx && dy1 < maxVerticalSwipeDeviationPx) {
                        isTwoFingerSwipeFired = true
                        onTwoFingerSwipeLeft?.invoke()
                        return true
                    }

                    // 2-Finger Swipe Right
                    if (dx0 > swipeThresholdPx && dx1 > swipeThresholdPx &&
                        dy0 < maxVerticalSwipeDeviationPx && dy1 < maxVerticalSwipeDeviationPx) {
                        isTwoFingerSwipeFired = true
                        onTwoFingerSwipeRight?.invoke()
                        return true
                    }
                }

                if (isThreeFingerGestureActive && pointerCount >= 3) {
                    val dist = Math.hypot(
                        (event.getX(0) - threeFingerStartX).toDouble(),
                        (event.getY(0) - threeFingerStartY).toDouble()
                    ).toFloat()
                    if (dist > threeFingerMaxDist) {
                        threeFingerMaxDist = dist
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val duration = now - twoFingerStartTime
                if (isTwoFingerGestureActive && !isTwoFingerSwipeFired && duration <= tapMaxDurationMs) {
                    // Check tap distance for both fingers
                    val x0 = event.getX(0)
                    val y0 = event.getY(0)
                    val x1 = if (pointerCount > 1) event.getX(1) else x0
                    val y1 = if (pointerCount > 1) event.getY(1) else y0

                    val d0 = Math.hypot((x0 - twoFingerStartX0).toDouble(), (y0 - twoFingerStartY0).toDouble())
                    val d1 = Math.hypot((x1 - twoFingerStartX1).toDouble(), (y1 - twoFingerStartY1).toDouble())

                    if (d0 < tapMaxDistancePx && d1 < tapMaxDistancePx) {
                        if (now - lastTwoFingerTapTimestamp <= doubleTapTimeoutMs) {
                            // 2-Finger Double-Tap detected!
                            lastTwoFingerTapTimestamp = 0L
                            onTwoFingerDoubleTap?.invoke()
                            isTwoFingerGestureActive = false
                            return true
                        } else {
                            lastTwoFingerTapTimestamp = now
                        }
                    }
                }

                // 3-Finger Tap Detection
                if (isThreeFingerGestureActive) {
                    val threeDuration = now - threeFingerStartTime
                    if (threeDuration <= tapMaxDurationMs && threeFingerMaxDist < tapMaxDistancePx) {
                        isThreeFingerGestureActive = false
                        onThreeFingerTap?.invoke()
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTwoFingerGestureActive = false
                isTwoFingerSwipeFired = false
                isThreeFingerGestureActive = false
            }
        }

        return isTwoFingerSwipeFired
    }
}
