package com.retropack.runtime.input

import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SensorControllerTest {

    private lateinit var controller: SensorController
    private var lastKeyMask: Int = RetroKey.NO_KEYS_MASK
    private var lastTiltX: Float = 0f
    private var lastTiltY: Float = 0f

    @BeforeEach
    fun setUp() {
        controller = SensorController().apply {
            onKeyMaskChanged = { mask -> lastKeyMask = mask }
            onNativeTiltChanged = { x, y ->
                lastTiltX = x
                lastTiltY = y
            }
        }
        lastKeyMask = RetroKey.NO_KEYS_MASK
        lastTiltX = 0f
        lastTiltY = 0f
    }

    @Test
    fun `dpad emulation mode synthesizes tilt steering left and right`() {
        controller.mode = SensorController.SensorMode.DPAD_EMULATION
        controller.deadzone = 2.0f
        controller.sensitivity = 1.0f

        // Neutral / Flat position
        controller.processAcceleration(0f, 0f, 9.8f)
        assertEquals(RetroKey.NO_KEYS_MASK, lastKeyMask)

        // Tilt Left (> deadzone)
        controller.processAcceleration(4.0f, 0f, 8.0f)
        assertEquals(RetroKey.KEY_LEFT, lastKeyMask)

        // Return to neutral
        controller.processAcceleration(0f, 0f, 9.8f)
        assertEquals(RetroKey.NO_KEYS_MASK, lastKeyMask)

        // Tilt Right (< -deadzone)
        controller.processAcceleration(-4.0f, 0f, 8.0f)
        assertEquals(RetroKey.KEY_RIGHT, lastKeyMask)
    }

    @Test
    fun `native gyro mode produces normalized tilt coordinates`() {
        controller.mode = SensorController.SensorMode.NATIVE_GYRO
        controller.sensitivity = 1.0f

        // 4.9 m/s² should normalize to ~0.5
        controller.processAcceleration(4.9f, -4.9f, 9.8f)
        assertEquals(0.5f, lastTiltX, 0.05f)
        assertEquals(-0.5f, lastTiltY, 0.05f)

        // Clamping to -1.0 .. 1.0
        controller.processAcceleration(20.0f, -20.0f, 9.8f)
        assertEquals(1.0f, lastTiltX, 0.001f)
        assertEquals(-1.0f, lastTiltY, 0.001f)
    }

    @Test
    fun `zero-point calibration offsets neutral resting position`() {
        controller.mode = SensorController.SensorMode.DPAD_EMULATION
        controller.deadzone = 2.0f

        // Suppose user holds phone tilted at 3.0 m/s² by default
        controller.calibrateZeroPoint(3.0f, 0f, 9.0f)

        // Holding at resting position 3.0 m/s² yields delta 0 -> neutral!
        controller.processAcceleration(3.0f, 0f, 9.0f)
        assertEquals(RetroKey.NO_KEYS_MASK, lastKeyMask)

        // Tilting further left to 6.0 m/s² -> delta 3.0 > deadzone 2.0 -> LEFT!
        controller.processAcceleration(6.0f, 0f, 8.0f)
        assertEquals(RetroKey.KEY_LEFT, lastKeyMask)

        // Reset calibration restores original flat baseline
        controller.resetCalibration()
        assertEquals(0f, controller.baselineX)
        assertEquals(0f, controller.baselineY)
        assertEquals(9.8f, controller.baselineZ)
    }

    @Test
    fun `disabling sensor clears active keymask`() {
        controller.mode = SensorController.SensorMode.DPAD_EMULATION
        controller.processAcceleration(4.0f, 0f, 8.0f)
        assertEquals(RetroKey.KEY_LEFT, lastKeyMask)

        controller.mode = SensorController.SensorMode.DISABLED
        assertEquals(RetroKey.NO_KEYS_MASK, lastKeyMask)
    }
}
