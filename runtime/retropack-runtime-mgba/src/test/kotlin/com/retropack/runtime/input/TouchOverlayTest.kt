package com.retropack.runtime.input

import android.content.Context
import android.view.MotionEvent
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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

    @Test
    fun `isEditMode clears active key masks and suppresses input emissions`() {
        val overlay = TouchOverlayView(Context())
        var reportedMask = -1
        overlay.onKeyMaskChanged = { mask -> reportedMask = mask }

        val btnA = overlay.layout.controls.first { it.id == TouchLayout.ID_A }
        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_DOWN, btnA.cx, btnA.cy))
        assertEquals(RetroKey.KEY_A, overlay.getKeyMask())

        // Activate edit mode
        overlay.isEditMode = true
        assertEquals(RetroKey.NO_KEYS_MASK, overlay.getKeyMask())
        assertEquals(RetroKey.NO_KEYS_MASK, reportedMask)

        // Touching button while in edit mode does NOT produce keymask
        reportedMask = -1
        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_DOWN, btnA.cx, btnA.cy))
        assertEquals(-1, reportedMask)
        assertEquals(RetroKey.NO_KEYS_MASK, overlay.getKeyMask())
    }

    @Test
    fun `edit mode drag updates cluster coordinates in real time`() {
        val overlay = TouchOverlayView(Context())
        overlay.isEditMode = true

        val dpadCluster = overlay.layout.getCluster(TouchLayout.CLUSTER_DPAD)!!
        val initialX = dpadCluster.anchorX
        val initialY = dpadCluster.anchorY

        // Touch down on D-pad
        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_DOWN, initialX, initialY))
        assertEquals(TouchLayout.CLUSTER_DPAD, overlay.selectedClusterId)

        // Drag 100px right, 50px down
        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_MOVE, initialX + 100f, initialY + 50f))

        val movedCluster = overlay.layout.getCluster(TouchLayout.CLUSTER_DPAD)!!
        assertEquals(initialX + 100f, movedCluster.anchorX, 0.001f)
        assertEquals(initialY + 50f, movedCluster.anchorY, 0.001f)

        // Touch up releases drag (keeps cluster selected for scale toolbar)
        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, initialX + 100f, initialY + 50f))
        assertEquals(TouchLayout.CLUSTER_DPAD, overlay.selectedClusterId)
    }

    @Test
    fun `edit mode save button invokes callback and persists layout`() {
        val overlay = TouchOverlayView(Context())
        overlay.isEditMode = true

        var savedLayout: TouchLayout? = null
        overlay.onLayoutSaved = { savedLayout = it }

        // Trigger save directly
        overlay.saveCurrentLayout()

        assertFalse(overlay.isEditMode)
        assertNotNull(savedLayout)
    }

    @Test
    fun `edit mode reset button restores default coordinates`() {
        val overlay = TouchOverlayView(Context())
        val defaultDpadX = overlay.layout.controls.first { it.id == TouchLayout.ID_DPAD }.cx

        // Move D-pad
        overlay.setCustomLayout(overlay.layout.withClusterPosition(TouchLayout.CLUSTER_DPAD, 500f, 600f))
        assertEquals(500f, overlay.layout.controls.first { it.id == TouchLayout.ID_DPAD }.cx)

        // Reset
        var resetTriggered = false
        overlay.onLayoutReset = { resetTriggered = true }
        overlay.resetToDefaultLayout()

        assertTrue(resetTriggered)
        assertEquals(defaultDpadX, overlay.layout.controls.first { it.id == TouchLayout.ID_DPAD }.cx)
    }

    @Test
    fun `inactivity auto-dimming fades opacity down to 10 percent after 4000ms`() {
        val overlay = TouchOverlayView(Context())
        overlay.opacity = 0.80f
        overlay.inactivityTimeoutMs = 4000L
        overlay.dimAlphaFactor = 0.10f

        val t0 = 100000L
        overlay.notifyTouchActivity(t0)

        // Immediately after touch: full opacity (0.80)
        assertEquals(0.80f, overlay.getEffectiveOpacity(t0), 0.001f)
        assertEquals(0.80f, overlay.getEffectiveOpacity(t0 + 2000L), 0.001f)

        // After 4000ms: dimmed to 10% (0.80 * 0.10 = 0.08)
        assertEquals(0.08f, overlay.getEffectiveOpacity(t0 + 4000L), 0.001f)
        assertEquals(0.08f, overlay.getEffectiveOpacity(t0 + 10000L), 0.001f)

        // New touch restores full opacity
        overlay.notifyTouchActivity(t0 + 10000L)
        assertEquals(0.80f, overlay.getEffectiveOpacity(t0 + 10000L), 0.001f)
    }

    @Test
    fun `setClusterScale clamps within 0_5x to 2_0x and scales cluster hitboxes`() {
        val overlay = TouchOverlayView(Context())
        val defaultDpadRadius = overlay.layout.controls.first { it.id == TouchLayout.ID_DPAD }.radius

        // Scale up to 1.5x
        overlay.setClusterScale(TouchLayout.CLUSTER_DPAD, 1.5f)
        assertEquals(1.5f, overlay.clusterScales[TouchLayout.CLUSTER_DPAD])
        val scaledDpadRadius = overlay.layout.controls.first { it.id == TouchLayout.ID_DPAD }.radius
        assertEquals(defaultDpadRadius * 1.5f, scaledDpadRadius, 0.01f)

        // Scale beyond 2.0x is clamped to 2.0x
        overlay.setClusterScale(TouchLayout.CLUSTER_DPAD, 3.5f)
        assertEquals(2.0f, overlay.clusterScales[TouchLayout.CLUSTER_DPAD])

        // Scale below 0.5x is clamped to 0.5x
        overlay.setClusterScale(TouchLayout.CLUSTER_DPAD, 0.1f)
        assertEquals(0.5f, overlay.clusterScales[TouchLayout.CLUSTER_DPAD])
    }

    @Test
    fun `floating dynamic dpad mode centers on touch down in left screen half and calculates directional deflection`() {
        val overlay = TouchOverlayView(Context())
        overlay.floatingDpadEnabled = true
        overlay.updateLayout(1080f, 1920f)

        var reportedMask = -1
        overlay.onKeyMaskChanged = { mask -> reportedMask = mask }

        // Touch down at (200, 1400) - left half of screen away from shoulder triggers
        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, 200f, 1400f)
        overlay.onTouchEvent(downEvent)

        assertTrue(overlay.floatingDpadActive)
        assertEquals(200f, overlay.floatingDpadX, 0.01f)
        assertEquals(1400f, overlay.floatingDpadY, 0.01f)

        // Drag right to (280, 1400) -> D-Pad Right
        val moveRightEvent = MotionEvent.createTouch(MotionEvent.ACTION_MOVE, 280f, 1400f)
        overlay.onTouchEvent(moveRightEvent)
        assertEquals(RetroKey.KEY_RIGHT, reportedMask)

        // Drag up to (200, 1320) -> D-Pad Up
        val moveUpEvent = MotionEvent.createTouch(MotionEvent.ACTION_MOVE, 200f, 1320f)
        overlay.onTouchEvent(moveUpEvent)
        assertEquals(RetroKey.KEY_UP, reportedMask)

        // Touch Up clears floating d-pad
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, 200f, 1320f)
        overlay.onTouchEvent(upEvent)
        assertFalse(overlay.floatingDpadActive)
        assertEquals(RetroKey.NO_KEYS_MASK, reportedMask)
    }

    @Test
    fun `touch down and touch up trigger dual-state haptic feedback`() {
        val overlay = TouchOverlayView(Context())
        val hapticEvents = mutableListOf<String>()
        overlay.onHapticFeedbackRequested = { hapticEvents.add("press") }
        overlay.onHapticReleaseRequested = { hapticEvents.add("release") }

        val btnA = overlay.layout.controls.first { it.id == TouchLayout.ID_A }
        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_DOWN, btnA.cx, btnA.cy))
        assertEquals(listOf("press"), hapticEvents)

        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, btnA.cx, btnA.cy))
        assertEquals(listOf("press", "release"), hapticEvents)
    }
}

