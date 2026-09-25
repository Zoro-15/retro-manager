package com.retropack.runtime.input

import android.content.Context
import android.view.MotionEvent
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TouchOverlayTest {

    @Test
    fun `single touch down on button A triggers key mask changed and haptic feedback`() {
        val overlay = TouchOverlayView(Context())
        var reportedMask = -1
        var hapticTriggered = false

        overlay.onKeyMaskChanged = { mask -> reportedMask = mask }
        overlay.onHapticFeedbackRequested = { hapticTriggered = true }

        val btnA = overlay.layout.controls.first { it.id == TouchLayout.ID_A }
        val event = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, btnA.cx, btnA.cy)

        val handled = overlay.onTouchEvent(event)
        assertTrue(handled)
        assertEquals(RetroKey.KEY_A, reportedMask)
        assertEquals(RetroKey.KEY_A, overlay.getKeyMask())
        assertTrue(hapticTriggered)
    }

    @Test
    fun `touch up clears active key mask`() {
        val overlay = TouchOverlayView(Context())
        var reportedMask = -1
        overlay.onKeyMaskChanged = { mask -> reportedMask = mask }

        val btnA = overlay.layout.controls.first { it.id == TouchLayout.ID_A }
        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_DOWN, btnA.cx, btnA.cy))
        assertEquals(RetroKey.KEY_A, reportedMask)

        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, btnA.cx, btnA.cy))
        assertEquals(RetroKey.NO_KEYS_MASK, reportedMask)
        assertEquals(RetroKey.NO_KEYS_MASK, overlay.getKeyMask())
    }

    @Test
    fun `multi touch pointers track concurrent presses`() {
        val overlay = TouchOverlayView(Context())
        var reportedMask = -1
        overlay.onKeyMaskChanged = { mask -> reportedMask = mask }

        val btnA = overlay.layout.controls.first { it.id == TouchLayout.ID_A }
        val btnB = overlay.layout.controls.first { it.id == TouchLayout.ID_B }

        val pointers = mutableListOf(
            MotionEvent.PointerCoords(0, btnA.cx, btnA.cy),
            MotionEvent.PointerCoords(1, btnB.cx, btnB.cy)
        )

        // Pointer 0 down on A
        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_DOWN, btnA.cx, btnA.cy))
        assertEquals(RetroKey.KEY_A, reportedMask)

        // Pointer 1 down on B
        val pointerDownEvent = MotionEvent.createMultiTouch(MotionEvent.ACTION_POINTER_DOWN, 1, pointers)
        overlay.onTouchEvent(pointerDownEvent)
        assertEquals(RetroKey.KEY_A or RetroKey.KEY_B, reportedMask)

        // Pointer 1 up
        val pointerUpEvent = MotionEvent.createMultiTouch(MotionEvent.ACTION_POINTER_UP, 1, pointers)
        overlay.onTouchEvent(pointerUpEvent)
        assertEquals(RetroKey.KEY_A, reportedMask)
    }

    @Test
    fun `controls visibility toggle clears active pointers and hides view`() {
        val overlay = TouchOverlayView(Context())
        var reportedMask = -1
        overlay.onKeyMaskChanged = { mask -> reportedMask = mask }

        val btnA = overlay.layout.controls.first { it.id == TouchLayout.ID_A }
        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_DOWN, btnA.cx, btnA.cy))
        assertEquals(RetroKey.KEY_A, overlay.getKeyMask())

        // Hide controls (e.g. when physical controller is used)
        overlay.isControlsVisible = false
        assertEquals(RetroKey.NO_KEYS_MASK, overlay.getKeyMask())
        assertEquals(RetroKey.NO_KEYS_MASK, reportedMask)
        assertFalse(overlay.isControlsVisible)
    }

    @Test
    fun `reveals controls on touch when hidden and revealOnTouchWhenHidden is enabled`() {
        val overlay = TouchOverlayView(Context())
        overlay.isControlsVisible = false
        overlay.revealOnTouchWhenHidden = true

        val touchEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, 100f, 100f)
        val handled = overlay.onTouchEvent(touchEvent)

        assertTrue(handled)
        assertTrue(overlay.isControlsVisible)
    }
}
