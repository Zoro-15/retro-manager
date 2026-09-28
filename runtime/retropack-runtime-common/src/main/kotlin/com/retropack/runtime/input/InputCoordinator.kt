package com.retropack.runtime.input

import com.retropack.runtime.core.RetroKey
import java.util.concurrent.atomic.AtomicInteger

/**
 * Coordinates and unifies virtual touch overlay, physical gamepad, and motion sensor inputs
 * into a single consolidated [RetroKey] bitmask.
 *
 * Implements specifications from:
 * - masterplan.md Section 5.4: "Auto-hides virtual controls when physical gamepad buttons are pressed."
 * - Motion sensor tilt steering integration.
 * - roadmap.md Part 2.5 & Part 3.
 *
 * Issue #44: the three source masks are stored in plain [AtomicInteger] cells
 * instead of guarded by a shared monitor lock, so the hot input path performs
 * lock-free volatile writes and dispatches the composite bitmask to the native
 * layer as a raw primitive `Int` — no synchronized blocks, no boxing, and no
 * intermediate wrapper objects on the 60+ Hz input dispatch path. A transient
 * dispatch may observe a mask updated a few cycles earlier; the concurrent
 * updater's own dispatch always follows and carries the final composite, so
 * the emulator-side state converges within one frame.
 */
class InputCoordinator(
    val touchOverlay: TouchOverlayView? = null,
    val gamepadMapper: GamepadMapper = GamepadMapper(),
    val sensorController: SensorController? = null,
    private val onKeyMaskDispatched: (Int) -> Unit = {},
    private val onAnalogAxisDispatched: (Float, Float) -> Unit = { _, _ -> }
) {
    var autoHideTouchOnGamepad: Boolean = true

    private val touchMaskAtomic = AtomicInteger(RetroKey.NO_KEYS_MASK)
    private val gamepadMaskAtomic = AtomicInteger(RetroKey.NO_KEYS_MASK)
    private val sensorMaskAtomic = AtomicInteger(RetroKey.NO_KEYS_MASK)

    val compositeKeyMask: Int
        get() = (touchMaskAtomic.get() or gamepadMaskAtomic.get() or sensorMaskAtomic.get()) and RetroKey.ALL_KEYS_MASK

    init {
        // Wire touch overlay callbacks
        touchOverlay?.onKeyMaskChanged = { mask ->
            updateTouchMask(mask)
        }

        touchOverlay?.onAnalogAxisChanged = { ax, ay ->
            onAnalogAxisDispatched(ax, ay)
        }

        // Wire gamepad mapper callbacks
        gamepadMapper.onKeyMaskChanged = { mask ->
            updateGamepadMask(mask)
        }

        gamepadMapper.onAnalogAxisChanged = { ax, ay ->
            onAnalogAxisDispatched(ax, ay)
        }

        gamepadMapper.onGamepadDetected = {
            if (autoHideTouchOnGamepad) {
                touchOverlay?.isControlsVisible = false
            }
        }

        // Wire motion sensor callbacks
        sensorController?.onKeyMaskChanged = { mask ->
            updateSensorMask(mask)
        }
    }

    /**
     * Updates the touch key mask (lock-free) and dispatches the composite bitmask.
     */
    fun updateTouchMask(mask: Int) {
        touchMaskAtomic.set(mask and RetroKey.ALL_KEYS_MASK)
        dispatchCompositeMask()
    }

    /**
     * Updates the gamepad key mask (lock-free) and dispatches the composite bitmask.
     */
    fun updateGamepadMask(mask: Int) {
        gamepadMaskAtomic.set(mask and RetroKey.ALL_KEYS_MASK)
        dispatchCompositeMask()
    }

    /**
     * Updates the motion sensor key mask (lock-free) and dispatches the composite bitmask.
     */
    fun updateSensorMask(mask: Int) {
        sensorMaskAtomic.set(mask and RetroKey.ALL_KEYS_MASK)
        dispatchCompositeMask()
    }

    /**
     * Updates analog axis deflection directly (-1.0f..1.0f).
     */
    fun updateAnalogAxis(axisX: Float, axisY: Float) {
        onAnalogAxisDispatched(axisX, axisY)
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
        touchMaskAtomic.set(RetroKey.NO_KEYS_MASK)
        gamepadMaskAtomic.set(RetroKey.NO_KEYS_MASK)
        sensorMaskAtomic.set(RetroKey.NO_KEYS_MASK)
        dispatchCompositeMask()
        onAnalogAxisDispatched(0f, 0f)
    }

    private fun dispatchCompositeMask() {
        onKeyMaskDispatched(compositeKeyMask)
    }
}
