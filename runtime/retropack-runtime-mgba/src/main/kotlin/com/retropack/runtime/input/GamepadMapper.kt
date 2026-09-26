package com.retropack.runtime.input

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.retropack.runtime.core.RetroKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Translates physical Bluetooth/USB HID gamepad and keyboard inputs into GBA [RetroKey] bitmasks.
 * Supports dynamic controller auto-detection and per-device custom remapping profiles.
 *
 * Implements specifications from:
 * - masterplan.md Section 5.4: "physical Bluetooth/USB gamepad HID handler. Auto-hides virtual
 *   controls when physical gamepad buttons are pressed."
 * - PPSSPP (ControlMappingScreen.cpp) & Lemuroid (GamepadManager.kt).
 */
class GamepadMapper(
    initialBindings: Map<Int, RetroKey> = defaultKeyBindings()
) {
    companion object {
        const val DEFAULT_AXIS_DEADZONE = 0.25f
        const val DEFAULT_TRIGGER_THRESHOLD = 0.5f

        /**
         * Default key bindings mapping standard Android Gamepad & Keyboard keys
         * to canonical GBA hardware buttons.
         */
        fun defaultKeyBindings(): Map<Int, RetroKey> {
            val map = mutableMapOf<Int, RetroKey>()
            // Gamepad Action Buttons
            map[KeyEvent.KEYCODE_BUTTON_A] = RetroKey.A
            map[KeyEvent.KEYCODE_BUTTON_B] = RetroKey.B
            map[KeyEvent.KEYCODE_BUTTON_X] = RetroKey.A
            map[KeyEvent.KEYCODE_BUTTON_Y] = RetroKey.B

            // Gamepad Shoulder Buttons
            map[KeyEvent.KEYCODE_BUTTON_L1] = RetroKey.L
            map[KeyEvent.KEYCODE_BUTTON_R1] = RetroKey.R
            map[KeyEvent.KEYCODE_BUTTON_L2] = RetroKey.L
            map[KeyEvent.KEYCODE_BUTTON_R2] = RetroKey.R

            // Gamepad System Buttons
            map[KeyEvent.KEYCODE_BUTTON_START] = RetroKey.START
            map[KeyEvent.KEYCODE_BUTTON_SELECT] = RetroKey.SELECT

            // Gamepad / Keyboard D-Pad Buttons
            map[KeyEvent.KEYCODE_DPAD_UP] = RetroKey.UP
            map[KeyEvent.KEYCODE_DPAD_DOWN] = RetroKey.DOWN
            map[KeyEvent.KEYCODE_DPAD_LEFT] = RetroKey.LEFT
            map[KeyEvent.KEYCODE_DPAD_RIGHT] = RetroKey.RIGHT

            // Hardware Keyboard Fallback Controls
            map[KeyEvent.KEYCODE_X] = RetroKey.A
            map[KeyEvent.KEYCODE_Z] = RetroKey.B
            map[KeyEvent.KEYCODE_ENTER] = RetroKey.START
            map[KeyEvent.KEYCODE_SPACE] = RetroKey.SELECT

            return map
        }

        /**
         * Returns a human-friendly label for standard Android key codes.
         */
        fun getKeyLabel(keyCode: Int): String {
            return when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> "BUTTON A"
                KeyEvent.KEYCODE_BUTTON_B -> "BUTTON B"
                KeyEvent.KEYCODE_BUTTON_X -> "BUTTON X"
                KeyEvent.KEYCODE_BUTTON_Y -> "BUTTON Y"
                KeyEvent.KEYCODE_BUTTON_L1 -> "L1 TRIGGER"
                KeyEvent.KEYCODE_BUTTON_R1 -> "R1 TRIGGER"
                KeyEvent.KEYCODE_BUTTON_L2 -> "L2 TRIGGER"
                KeyEvent.KEYCODE_BUTTON_R2 -> "R2 TRIGGER"
                KeyEvent.KEYCODE_BUTTON_START -> "START"
                KeyEvent.KEYCODE_BUTTON_SELECT -> "SELECT"
                KeyEvent.KEYCODE_DPAD_UP -> "DPAD UP"
                KeyEvent.KEYCODE_DPAD_DOWN -> "DPAD DOWN"
                KeyEvent.KEYCODE_DPAD_LEFT -> "DPAD LEFT"
                KeyEvent.KEYCODE_DPAD_RIGHT -> "DPAD RIGHT"
                KeyEvent.KEYCODE_X -> "KEY X"
                KeyEvent.KEYCODE_Z -> "KEY Z"
                KeyEvent.KEYCODE_ENTER -> "ENTER"
                KeyEvent.KEYCODE_SPACE -> "SPACE"
                else -> "KEY ($keyCode)"
            }
        }
    }

    val keyBindings: MutableMap<Int, RetroKey> = ConcurrentHashMap(initialBindings)

    var activeDeviceName: String = "Default Gamepad"
    var activeDeviceDescriptor: String = "default_gamepad"

    var axisDeadzone: Float = DEFAULT_AXIS_DEADZONE
        set(value) {
            field = value.coerceIn(0.05f, 0.95f)
        }

    var triggerThreshold: Float = DEFAULT_TRIGGER_THRESHOLD
        set(value) {
            field = value.coerceIn(0.05f, 0.95f)
        }

    /** Callback invoked whenever the physical gamepad keymask changes. */
    var onKeyMaskChanged: ((Int) -> Unit)? = null

    /**
     * Callback triggered when physical controller input is first detected,
     * used by runtime UI to auto-hide virtual touch controls.
     */
    var onGamepadDetected: (() -> Unit)? = null

    /** Callback triggered when a new controller device profile is activated. */
    var onDeviceProfileChanged: ((name: String, descriptor: String) -> Unit)? = null

    @Volatile
    private var buttonMask: Int = RetroKey.NO_KEYS_MASK

    @Volatile
    private var axisMask: Int = RetroKey.NO_KEYS_MASK

    // Single guard for read-modify-write composites
    private val maskLock = Any()

    val currentKeyMask: Int
        get() = synchronized(maskLock) {
            (buttonMask or axisMask) and RetroKey.ALL_KEYS_MASK
        }

    /**
     * Automatically identifies and applies the controller profile corresponding to [device].
     */
    fun detectAndApplyDevice(context: Context, device: InputDevice) {
        val descriptor = device.descriptor ?: "gamepad_${device.id}"
        val name = device.name ?: "Game Controller"
        activeDeviceDescriptor = descriptor
        activeDeviceName = name
        loadProfile(context, descriptor, name)
        onDeviceProfileChanged?.invoke(name, descriptor)
    }

    /**
     * Loads custom bindings from SharedPreferences for [descriptor], or falls back to defaults.
     */
    fun loadProfile(context: Context, descriptor: String = activeDeviceDescriptor, name: String = activeDeviceName) {
        activeDeviceDescriptor = descriptor
        activeDeviceName = name
        val saved = ControlsPreferences.loadGamepadMapping(context, descriptor)
        keyBindings.clear()
        if (saved != null && saved.isNotEmpty()) {
            keyBindings.putAll(saved)
        } else {
            keyBindings.putAll(defaultKeyBindings())
        }
    }

    /**
     * Persists the current key bindings profile for [descriptor] to SharedPreferences.
     */
    fun saveProfile(context: Context, descriptor: String = activeDeviceDescriptor) {
        ControlsPreferences.saveGamepadMapping(context, descriptor, keyBindings)
    }

    /**
     * Binds an Android [keyCode] to a GBA [retroKey].
     */
    fun setKeyBinding(keyCode: Int, retroKey: RetroKey) {
        // Remove any existing binding that might map to this retroKey if desired,
        // or allow multiple keys to map to the same retroKey
        keyBindings[keyCode] = retroKey
    }

    /**
     * Removes binding for [keyCode].
     */
    fun removeKeyBinding(keyCode: Int) {
        keyBindings.remove(keyCode)
    }

    /**
     * Resets bindings back to factory defaults.
     */
    fun resetBindingsToDefault(context: Context? = null) {
        keyBindings.clear()
        keyBindings.putAll(defaultKeyBindings())
        if (context != null) {
            ControlsPreferences.clearGamepadMapping(context, activeDeviceDescriptor)
        }
    }

    /**
     * Returns the keycode currently assigned to [retroKey], or null if unassigned.
     */
    fun getKeyCodeFor(retroKey: RetroKey): Int? {
        return keyBindings.entries.firstOrNull { it.value == retroKey }?.key
    }

    /**
     * Processes Android [KeyEvent] input (e.g. from Bluetooth/USB controller or keyboard).
     *
     * @return true if the event was consumed and mapped to a GBA button, false otherwise.
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        val retroKey = keyBindings[event.keyCode] ?: return false

        // Notify that a physical gamepad button was pressed
        if (event.action == KeyEvent.ACTION_DOWN) {
            onGamepadDetected?.invoke()
        }

        synchronized(maskLock) {
            val oldMask = (buttonMask or axisMask) and RetroKey.ALL_KEYS_MASK
            val newButtonMask = when (event.action) {
                KeyEvent.ACTION_DOWN -> buttonMask or retroKey.mask
                KeyEvent.ACTION_UP -> buttonMask and retroKey.mask.inv()
                else -> buttonMask
            }

            if (newButtonMask != buttonMask) {
                buttonMask = newButtonMask
                val newComposite = (buttonMask or axisMask) and RetroKey.ALL_KEYS_MASK
                if (newComposite != oldMask) {
                    onKeyMaskChanged?.invoke(newComposite)
                }
            }
        }

        return true
    }

    /**
     * Processes Android [MotionEvent] input for analog sticks, HAT switches, and triggers.
     *
     * @return true if the motion event corresponded to game controller axes and changed state.
     */
    fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        var newAxisMask = RetroKey.NO_KEYS_MASK

        // 1. D-Pad Hat Switch (Digital HAT axes)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (hatX < -0.5f) newAxisMask = newAxisMask or RetroKey.KEY_LEFT
        if (hatX > 0.5f) newAxisMask = newAxisMask or RetroKey.KEY_RIGHT
        if (hatY < -0.5f) newAxisMask = newAxisMask or RetroKey.KEY_UP
        if (hatY > 0.5f) newAxisMask = newAxisMask or RetroKey.KEY_DOWN

        // 2. Left Analog Stick
        val stickX = event.getAxisValue(MotionEvent.AXIS_X)
        val stickY = event.getAxisValue(MotionEvent.AXIS_Y)
        if (stickX < -axisDeadzone) newAxisMask = newAxisMask or RetroKey.KEY_LEFT
        if (stickX > axisDeadzone) newAxisMask = newAxisMask or RetroKey.KEY_RIGHT
        if (stickY < -axisDeadzone) newAxisMask = newAxisMask or RetroKey.KEY_UP
        if (stickY > axisDeadzone) newAxisMask = newAxisMask or RetroKey.KEY_DOWN

        // 3. Analog Triggers (L2 / R2 or Brake / Gas)
        val lTrigger = Math.max(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        val rTrigger = Math.max(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )
        if (lTrigger > triggerThreshold) newAxisMask = newAxisMask or RetroKey.KEY_L
        if (rTrigger > triggerThreshold) newAxisMask = newAxisMask or RetroKey.KEY_R

        // If any axis is active, notify controller detected
        if (newAxisMask != RetroKey.NO_KEYS_MASK) {
            onGamepadDetected?.invoke()
        }

        synchronized(maskLock) {
            val oldMask = (buttonMask or axisMask) and RetroKey.ALL_KEYS_MASK
            if (newAxisMask != axisMask) {
                axisMask = newAxisMask
                val newComposite = (buttonMask or axisMask) and RetroKey.ALL_KEYS_MASK
                if (newComposite != oldMask) {
                    onKeyMaskChanged?.invoke(newComposite)
                }
                return true
            }
        }

        return newAxisMask != RetroKey.NO_KEYS_MASK
    }

    /**
     * Resets all pressed button and axis states to neutral.
     */
    fun reset() {
        synchronized(maskLock) {
            val oldMask = (buttonMask or axisMask) and RetroKey.ALL_KEYS_MASK
            buttonMask = RetroKey.NO_KEYS_MASK
            axisMask = RetroKey.NO_KEYS_MASK
            if (oldMask != RetroKey.NO_KEYS_MASK) {
                onKeyMaskChanged?.invoke(RetroKey.NO_KEYS_MASK)
            }
        }
    }
}
