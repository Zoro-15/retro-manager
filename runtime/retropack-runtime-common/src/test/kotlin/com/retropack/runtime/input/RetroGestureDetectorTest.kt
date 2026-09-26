package com.retropack.runtime.input

import android.view.MotionEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RetroGestureDetectorTest {

    private lateinit var detector: RetroGestureDetector
    private var quickSaveCount = 0
    private var quickLoadCount = 0
    private var toggleFastForwardCount = 0
    private var toggleQuickMenuCount = 0

    @BeforeEach
    fun setUp() {
        detector = RetroGestureDetector().apply {
            onTwoFingerSwipeLeft = { quickSaveCount++ }
            onTwoFingerSwipeRight = { quickLoadCount++ }
            onTwoFingerDoubleTap = { toggleFastForwardCount++ }
            onThreeFingerTap = { toggleQuickMenuCount++ }
        }
        quickSaveCount = 0
        quickLoadCount = 0
        toggleFastForwardCount = 0
        toggleQuickMenuCount = 0
    }

    @Test
    fun `two finger swipe left triggers quick save shortcut`() {
        // Pointer Down (2 fingers)
        val downEvent = MotionEvent.obtain(
            0L, 0L, MotionEvent.ACTION_POINTER_DOWN,
            arrayOf(Pair(300f, 400f), Pair(350f, 420f))
        )
        detector.onTouchEvent(downEvent)

        // Move Left by 100px (greater than 70px threshold)
        val moveEvent = MotionEvent.obtain(
            0L, 50L, MotionEvent.ACTION_MOVE,
            arrayOf(Pair(180f, 405f), Pair(230f, 415f))
        )
        val consumed = detector.onTouchEvent(moveEvent)

        assertTrue(consumed)
        assertEquals(1, quickSaveCount)
        assertEquals(0, quickLoadCount)
    }

    @Test
    fun `two finger swipe right triggers quick load shortcut`() {
        val downEvent = MotionEvent.obtain(
            0L, 0L, MotionEvent.ACTION_POINTER_DOWN,
            arrayOf(Pair(200f, 400f), Pair(250f, 420f))
        )
        detector.onTouchEvent(downEvent)

        // Move Right by 100px
        val moveEvent = MotionEvent.obtain(
            0L, 50L, MotionEvent.ACTION_MOVE,
            arrayOf(Pair(320f, 405f), Pair(370f, 415f))
        )
        val consumed = detector.onTouchEvent(moveEvent)

        assertTrue(consumed)
        assertEquals(0, quickSaveCount)
        assertEquals(1, quickLoadCount)
    }

    @Test
    fun `three finger tap toggles quick menu`() {
        val downEvent = MotionEvent.obtain(
            0L, 0L, MotionEvent.ACTION_POINTER_DOWN,
            arrayOf(Pair(200f, 400f), Pair(250f, 400f), Pair(300f, 400f))
        )
        detector.onTouchEvent(downEvent)

        val upEvent = MotionEvent.obtain(
            0L, 100L, MotionEvent.ACTION_POINTER_UP,
            arrayOf(Pair(202f, 401f), Pair(251f, 401f), Pair(301f, 401f))
        )
        detector.onTouchEvent(upEvent)

        assertEquals(1, toggleQuickMenuCount)
    }
}
