package com.retropack.runtime.input

import com.retropack.runtime.core.RetroKey

/**
 * Coordinates and unifies virtual touch overlay, physical gamepad, and motion sensor inputs
 * into a single consolidated [RetroKey] bitmask.
 *
 * Implements specifications from:
 * - masterplan.md Section 5.4: "Auto-hides virtual controls when physical gamepad buttons are pressed."
 * - Motion sensor tilt steering integration.
 * - roadmap.md Part 2.5 & Part 3.
 */
class InputCoordinator(
    val touchOverlay: TouchOverlayView? = null,
    val gamepadMapper: GamepadMapper = GamepadMapper(),
    val sensorController: SensorController? = null,
    private val onKeyMaskDispatched: (Int) -> Unit = {}
) {
    var autoHideTouchOnGamepad: Boolean = true

    private val maskLock = Any()

    @Volatile
    private var touchMask: Int = RetroKey.NO_KEYS_MASK

    @Volatile
    private var gamepadMask: Int = RetroKey.NO_KEYS_MASK

    @Volatile
    private var sensorMask: Int = RetroKey.NO_KEYS_MASK

    val compositeKeyMask: Int
        get() = synchronized(maskLock) {
            (touchMask or gamepadMask or sensorMask) and RetroKey.ALL_KEYS_MASK
        }

    init {
        // Wire touch overlay callbacks
        touchOverlay?.onKeyMaskChanged = { mask ->
            synchronized(maskLock) {
                touchMask = mask
                dispatchCompositeMask()
            }
        }

        // Wire gamepad mapper callbacks
        gamepadMapper.onKeyMaskChanged = { mask ->
            synchronized(maskLock) {
                gamepadMask = mask
                dispatchCompositeMask()
            }
        }

        gamepadMapper.onGamepadDetected = {
            if (autoHideTouchOnGamepad) {
                touchOverlay?.isControlsVisible = false
            }
        }

        // Wire motion sensor callbacks
        sensorController?.onKeyMaskChanged = { mask ->
            synchronized(maskLock) {
                sensorMask = mask
                dispatchCompositeMask()
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
     * Updates the motion sensor key mask directly.
     */
    fun updateSensorMask(mask: Int) {
        synchronized(maskLock) {
            sensorMask = mask
            dispatchCompositeMask()
        }
    }

    /**
     * Resets all touch, gamepad, and sensor key states with a single consolidated dispatch.
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
            sensorMask = RetroKey.NO_KEYS_MASK
            dispatchCompositeMask()
        }
    }

    private fun dispatchCompositeMask() {
        onKeyMaskDispatched(compositeKeyMask)
    }
}
