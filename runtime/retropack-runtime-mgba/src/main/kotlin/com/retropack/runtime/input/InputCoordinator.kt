package com.retropack.runtime.input

import com.retropack.runtime.core.RetroKey

/**
 * Coordinates and unifies virtual touch overlay and physical gamepad inputs
 * into a single consolidated [RetroKey] bitmask.
 *
 * Implements specifications from:
 * - masterplan.md Section 5.4: "Auto-hides virtual controls when physical gamepad buttons are pressed."
 * - roadmap.md Part 2.5.
 */
class InputCoordinator(
    val touchOverlay: TouchOverlayView? = null,
    val gamepadMapper: GamepadMapper = GamepadMapper(),
    private val onKeyMaskDispatched: (Int) -> Unit = {}
) {
    var autoHideTouchOnGamepad: Boolean = true

    @Volatile
    private var touchMask: Int = RetroKey.NO_KEYS_MASK

    @Volatile
    private var gamepadMask: Int = RetroKey.NO_KEYS_MASK

    val compositeKeyMask: Int
        get() = (touchMask or gamepadMask) and RetroKey.ALL_KEYS_MASK

    init {
        // Wire touch overlay callbacks
        touchOverlay?.onKeyMaskChanged = { mask ->
            touchMask = mask
            dispatchCompositeMask()
        }

        // Wire gamepad mapper callbacks
        gamepadMapper.onKeyMaskChanged = { mask ->
            gamepadMask = mask
            dispatchCompositeMask()
        }

        gamepadMapper.onGamepadDetected = {
            if (autoHideTouchOnGamepad) {
                touchOverlay?.isControlsVisible = false
            }
        }
    }

    /**
     * Updates the touch key mask directly.
     */
    fun updateTouchMask(mask: Int) {
        touchMask = mask
        dispatchCompositeMask()
    }

    /**
     * Updates the gamepad key mask directly.
     */
    fun updateGamepadMask(mask: Int) {
        gamepadMask = mask
        dispatchCompositeMask()
    }

    /**
     * Resets all touch and gamepad key states.
     */
    fun reset() {
        touchMask = RetroKey.NO_KEYS_MASK
        gamepadMask = RetroKey.NO_KEYS_MASK
        gamepadMapper.reset()
        dispatchCompositeMask()
    }

    private fun dispatchCompositeMask() {
        onKeyMaskDispatched(compositeKeyMask)
    }
}
