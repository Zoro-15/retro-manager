package com.retropack.runtime.input

import android.view.KeyEvent
import android.view.MotionEvent
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GamepadMapperTest {

    @Test
    fun `maps standard gamepad buttons to GBA keys`() {
        val mapper = GamepadMapper()
        var reportedMask = -1
        var detected = false
        mapper.onKeyMaskChanged = { mask -> reportedMask = mask }
        mapper.onGamepadDetected = { detected = true }

        // Press A
        val handledA = mapper.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(handledA)
        assertTrue(detected)
        assertEquals(RetroKey.KEY_A, reportedMask)
        assertEquals(RetroKey.KEY_A, mapper.currentKeyMask)

        // Press B concurrently
        mapper.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(RetroKey.KEY_A or RetroKey.KEY_B, reportedMask)

        // Release A
        mapper.handleKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(RetroKey.KEY_B, reportedMask)

        // Release B
        mapper.handleKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(RetroKey.NO_KEYS_MASK, reportedMask)
    }

    @Test
    fun `maps shoulder buttons and start select`() {
        val mapper = GamepadMapper()
        var reportedMask = -1
        mapper.onKeyMaskChanged = { mask -> reportedMask = mask }

        mapper.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(RetroKey.KEY_L, reportedMask)

        mapper.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(RetroKey.KEY_L or RetroKey.KEY_R, reportedMask)

        mapper.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(RetroKey.KEY_L or RetroKey.KEY_R or RetroKey.KEY_START, reportedMask)

        mapper.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_SELECT))
        assertEquals(RetroKey.KEY_L or RetroKey.KEY_R or RetroKey.KEY_START or RetroKey.KEY_SELECT, reportedMask)
    }

    @Test
    fun `maps keyboard fallback keys`() {
        val mapper = GamepadMapper()
        var reportedMask = -1
        mapper.onKeyMaskChanged = { mask -> reportedMask = mask }

        mapper.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X))
        assertEquals(RetroKey.KEY_A, reportedMask)

        mapper.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z))
        assertEquals(RetroKey.KEY_A or RetroKey.KEY_B, reportedMask)

        mapper.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        assertEquals(RetroKey.KEY_A or RetroKey.KEY_B or RetroKey.KEY_START, reportedMask)
    }

    @Test
    fun `maps analog stick axes with deadzone`() {
        val mapper = GamepadMapper()
        mapper.axisDeadzone = 0.25f
        var reportedMask = -1
        var detected = false
        mapper.onKeyMaskChanged = { mask -> reportedMask = mask }
        mapper.onGamepadDetected = { detected = true }

        // Within deadzone: stickX = 0.15f, stickY = -0.1f
        val eventDeadzone = MotionEvent.createJoystick(mapOf(
            MotionEvent.AXIS_X to 0.15f,
            MotionEvent.AXIS_Y to -0.10f
        ))
        mapper.handleGenericMotionEvent(eventDeadzone)
        assertEquals(RetroKey.NO_KEYS_MASK, mapper.currentKeyMask)

        // Stick right and up: stickX = 0.8f, stickY = -0.7f
        val eventDiagonal = MotionEvent.createJoystick(mapOf(
            MotionEvent.AXIS_X to 0.80f,
            MotionEvent.AXIS_Y to -0.70f
        ))
        val handled = mapper.handleGenericMotionEvent(eventDiagonal)
        assertTrue(handled)
        assertTrue(detected)
        assertEquals(RetroKey.KEY_RIGHT or RetroKey.KEY_UP, reportedMask)
    }

    @Test
    fun `maps hat switch and analog triggers`() {
        val mapper = GamepadMapper()
        mapper.triggerThreshold = 0.5f
        var reportedMask = -1
        mapper.onKeyMaskChanged = { mask -> reportedMask = mask }

        // Hat switch LEFT (-1.0) and analog trigger L (0.9)
        val eventHat = MotionEvent.createJoystick(mapOf(
            MotionEvent.AXIS_HAT_X to -1.0f,
            MotionEvent.AXIS_LTRIGGER to 0.9f
        ))
        mapper.handleGenericMotionEvent(eventHat)
        assertEquals(RetroKey.KEY_LEFT or RetroKey.KEY_L, reportedMask)
    }

    @Test
    fun `reset clears all button and axis states`() {
        val mapper = GamepadMapper()
        var reportedMask = -1
        mapper.onKeyMaskChanged = { mask -> reportedMask = mask }

        mapper.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(RetroKey.KEY_A, mapper.currentKeyMask)

        mapper.reset()
        assertEquals(RetroKey.NO_KEYS_MASK, mapper.currentKeyMask)
        assertEquals(RetroKey.NO_KEYS_MASK, reportedMask)
    }
}
