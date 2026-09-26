package com.retropack.runtime.input

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputCoordinatorTest {

    @Test
    fun `combines touch and gamepad key masks`() {
        var dispatchedMask = -1
        val coordinator = InputCoordinator(
            onKeyMaskDispatched = { mask -> dispatchedMask = mask }
        )

        // Touch presses A
        coordinator.updateTouchMask(RetroKey.KEY_A)
        assertEquals(RetroKey.KEY_A, dispatchedMask)
        assertEquals(RetroKey.KEY_A, coordinator.compositeKeyMask)

        // Gamepad presses UP
        coordinator.updateGamepadMask(RetroKey.KEY_UP)
        assertEquals(RetroKey.KEY_A or RetroKey.KEY_UP, dispatchedMask)
        assertEquals(RetroKey.KEY_A or RetroKey.KEY_UP, coordinator.compositeKeyMask)

        // Touch releases A
        coordinator.updateTouchMask(RetroKey.NO_KEYS_MASK)
        assertEquals(RetroKey.KEY_UP, dispatchedMask)
        assertEquals(RetroKey.KEY_UP, coordinator.compositeKeyMask)
    }

    @Test
    fun `auto hides touch overlay when gamepad is detected`() {
        val overlay = TouchOverlayView(Context())
        assertTrue(overlay.isControlsVisible)

        val gamepadMapper = GamepadMapper()
        val coordinator = InputCoordinator(
            touchOverlay = overlay,
            gamepadMapper = gamepadMapper
        )
        coordinator.autoHideTouchOnGamepad = true

        // User presses a button on a physical gamepad
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)
        gamepadMapper.handleKeyEvent(event)

        // Virtual controls should now be auto-hidden
        assertFalse(overlay.isControlsVisible)
    }

    @Test
    fun `reset clears all inputs`() {
        var dispatchedMask = -1
        val coordinator = InputCoordinator(
            onKeyMaskDispatched = { mask -> dispatchedMask = mask }
        )

        coordinator.updateTouchMask(RetroKey.KEY_B)
        coordinator.updateGamepadMask(RetroKey.KEY_START)
        assertEquals(RetroKey.KEY_B or RetroKey.KEY_START, dispatchedMask)

        coordinator.reset()
        assertEquals(RetroKey.NO_KEYS_MASK, dispatchedMask)
        assertEquals(RetroKey.NO_KEYS_MASK, coordinator.compositeKeyMask)
    }
}
