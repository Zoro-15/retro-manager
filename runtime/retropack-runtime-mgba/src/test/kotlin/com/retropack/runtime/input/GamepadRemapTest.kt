package com.retropack.runtime.input

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import com.retropack.runtime.core.RetroKey
import com.retropack.runtime.ui.GamepadRemapOverlay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GamepadRemapTest {

    private lateinit var context: Context
    private lateinit var mapper: GamepadMapper
    private lateinit var overlay: GamepadRemapOverlay

    @BeforeEach
    fun setUp() {
        context = Context()
        Context.resetSharedPreferences()
        mapper = GamepadMapper()
        overlay = GamepadRemapOverlay(context).apply {
            this.gamepadMapper = mapper
        }
    }

    @Test
    fun `auto-detection sets descriptor and loads profile`() {
        val device = InputDevice(2, InputDevice.SOURCE_GAMEPAD, "PlayStation DualSense", "ps_dualsense_uuid_123")
        mapper.detectAndApplyDevice(context, device)

        assertEquals("PlayStation DualSense", mapper.activeDeviceName)
        assertEquals("ps_dualsense_uuid_123", mapper.activeDeviceDescriptor)
    }

    @Test
    fun `interactive wizard steps through all 10 buttons sequentially`() {
        overlay.show()
        overlay.startWizard()

        assertTrue(overlay.isWizardMode)
        assertEquals(RetroKey.UP, overlay.activeListeningKey)
        assertEquals(0, overlay.wizardStepIndex)

        // Step 1: Bind UP to KEYCODE_DPAD_UP
        overlay.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(RetroKey.DOWN, overlay.activeListeningKey)
        assertEquals(1, overlay.wizardStepIndex)
        assertEquals(RetroKey.UP, mapper.keyBindings[KeyEvent.KEYCODE_DPAD_UP])

        // Step 2: Bind DOWN
        overlay.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(RetroKey.LEFT, overlay.activeListeningKey)
        assertEquals(2, overlay.wizardStepIndex)

        // Step 3: Bind LEFT
        overlay.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(RetroKey.RIGHT, overlay.activeListeningKey)

        // Step 4: Bind RIGHT
        overlay.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(RetroKey.A, overlay.activeListeningKey)

        // Step 5: Bind A
        overlay.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(RetroKey.B, overlay.activeListeningKey)

        // Step 6: Bind B
        overlay.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(RetroKey.L, overlay.activeListeningKey)

        // Step 7: Bind L
        overlay.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(RetroKey.R, overlay.activeListeningKey)

        // Step 8: Bind R
        overlay.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(RetroKey.START, overlay.activeListeningKey)

        // Step 9: Bind START
        overlay.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(RetroKey.SELECT, overlay.activeListeningKey)

        // Step 10: Bind SELECT -> finishes wizard and saves profile!
        overlay.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_SELECT))
        assertFalse(overlay.isWizardMode)
        assertEquals(null, overlay.activeListeningKey)

        // Check that bindings were persisted to SharedPreferences
        val saved = ControlsPreferences.loadGamepadMapping(context, mapper.activeDeviceDescriptor)
        assertNotNull(saved)
        assertEquals(RetroKey.A, saved?.get(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test
    fun `single button remap updates mapping for specific key`() {
        overlay.show()
        overlay.startListeningFor(RetroKey.A)
        assertEquals(RetroKey.A, overlay.activeListeningKey)

        // Map custom KEYCODE_C to Button A
        overlay.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C))
        assertEquals(null, overlay.activeListeningKey)
        assertEquals(RetroKey.A, mapper.keyBindings[KeyEvent.KEYCODE_C])
    }

    @Test
    fun `reset defaults restores canonical mapping`() {
        mapper.setKeyBinding(KeyEvent.KEYCODE_BUTTON_A, RetroKey.START)
        assertEquals(RetroKey.START, mapper.keyBindings[KeyEvent.KEYCODE_BUTTON_A])

        mapper.resetBindingsToDefault(context)
        assertEquals(RetroKey.A, mapper.keyBindings[KeyEvent.KEYCODE_BUTTON_A])
    }
}
