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

    private val maskLock = Any()

    @Volatile
    private var touchMask: Int = RetroKey.NO_KEYS_MASK

    @Volatile
    private var gamepadMask: Int = RetroKey.NO_KEYS_MASK

    val compositeKeyMask: Int
        get() = synchronized(maskLock) {
            (touchMask or gamepadMask) and RetroKey.ALL_KEYS_MASK
        }

    init {
        // Wire touch overlay callbacks
        touchOverlay?.onKeyMaskChanged = { mask ->
            synchronized(maskLock) {
                touchMask = mask
                onKeyMaskDispatched((touchMask or gamepadMask) and RetroKey.ALL_KEYS_MASK)
            }
        }

        // Wire gamepad mapper callbacks
        gamepadMapper.onKeyMaskChanged = { mask ->
            synchronized(maskLock) {
                gamepadMask = mask
                onKeyMaskDispatched((touchMask or gamepadMask) and RetroKey.ALL_KEYS_MASK)
            }
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
        synchronized(maskLock) {
            touchMask = mask
            dispatchCompositeMask()
        }
    }

    /**
     * Updates the gamepad key mask directly.
     */
    fun updateGamepadMask(mask: Int) {
        synchronized(maskLock) {
            gamepadMask = mask
            dispatchCompositeMask()
        }
    }

    /**
     * Resets all touch and gamepad key states with a single dispatch (the old
     * path fired once via gamepadMapper.reset() plus once directly).
     */
    fun reset() {
        val mapperCallback = gamepadMapper.onKeyMaskChanged
        gamepadMapper.onKeyMaskChanged = null
        try {
            gamepadMapper.reset()
        } finally {
            gamepadMapper.onKeyMaskChanged = mapperCallback
        }
        synchronized(maskLock) {
            touchMask = RetroKey.NO_KEYS_MASK
            gamepadMask = RetroKey.NO_KEYS_MASK
            dispatchCompositeMask()
        }
    }

    private fun dispatchCompositeMask() {
        onKeyMaskDispatched(compositeKeyMask)
    }
}
