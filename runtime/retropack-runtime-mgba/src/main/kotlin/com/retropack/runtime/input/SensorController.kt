package com.retropack.runtime.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.retropack.runtime.core.RetroKey

/**
 * Hardware Motion Sensor Controller for RetroPack.
 *
 * Captures device accelerometer and gyroscope data via Android [SensorManager], providing:
 *  1. [SensorMode.DPAD_EMULATION]: Tilt steering that synthesizes D-Pad Left/Right inputs for racing games (e.g. F-Zero, Mario Kart).
 *  2. [SensorMode.NATIVE_GYRO]: High-precision angle deltas for native tilt-cartridge games (WarioWare Twisted, Kirby Tilt 'n' Tumble).
 *  3. Dynamic zero-point calibration and deadzone/sensitivity tuning.
 */
class SensorController : SensorEventListener {

    enum class SensorMode {
        DISABLED,
        DPAD_EMULATION,
        NATIVE_GYRO
    }

    var mode: SensorMode = SensorMode.DISABLED
        set(value) {
            field = value
            if (value == SensorMode.DISABLED) {
                resetMask()
            }
        }

    var sensitivity: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.2f, 5.0f)
        }

    var deadzone: Float = 1.8f
        set(value) {
            field = value.coerceIn(0.2f, 6.0f)
        }

    var invertX: Boolean = false
    var invertY: Boolean = false

    // Calibration zero-point offsets
    var baselineX: Float = 0f
    var baselineY: Float = 0f
    var baselineZ: Float = 9.8f

    var isListening: Boolean = false
        private set

    /** Callback dispatched when D-Pad emulation synthesizes a keymask change. */
    var onKeyMaskChanged: ((Int) -> Unit)? = null

    /** Callback dispatched when native gyro angles update (normalized -1.0 to 1.0). */
    var onNativeTiltChanged: ((tiltX: Float, tiltY: Float) -> Unit)? = null

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    @Volatile
    private var currentMask: Int = RetroKey.NO_KEYS_MASK

    /**
     * Starts listening to device motion sensors.
     */
    fun start(context: Context, customSensorManager: SensorManager? = null) {
        if (isListening) return

        val sm = customSensorManager ?: (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
        if (sm != null) {
            this.sensorManager = sm
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            this.accelerometer = accel
            if (accel != null) {
                sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
                isListening = true
            }
        }
    }

    /**
     * Stops listening to sensors to conserve battery and eliminate lifecycle leaks.
     */
    fun stop() {
        if (!isListening) return
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Throwable) {}
        isListening = false
        resetMask()
    }

    /**
     * Calibrates current physical orientation as the neutral resting zero-point.
     */
    fun calibrateZeroPoint(currentX: Float? = null, currentY: Float? = null, currentZ: Float? = null) {
        if (currentX != null && currentY != null && currentZ != null) {
            baselineX = currentX
            baselineY = currentY
            baselineZ = currentZ
        }
    }

    /**
     * Resets calibration baseline to default flat portrait orientation.
     */
    fun resetCalibration() {
        baselineX = 0f
        baselineY = 0f
        baselineZ = 9.8f
    }

    /**
     * Processes raw acceleration readings and translates them according to active [mode].
     */
    fun processAcceleration(rawX: Float, rawY: Float, rawZ: Float) {
        if (mode == SensorMode.DISABLED) {
            resetMask()
            return
        }

        // Apply baseline calibration delta
        val deltaX = (rawX - baselineX) * (if (invertX) -1f else 1f) * sensitivity
        val deltaY = (rawY - baselineY) * (if (invertY) -1f else 1f) * sensitivity

        when (mode) {
            SensorMode.DPAD_EMULATION -> {
                var newMask = RetroKey.NO_KEYS_MASK

                // Horizontal steering (tilt left / right)
                // In landscape mode, gravity acts primarily along Y axis. In portrait, along X axis.
                // We handle both axes by checking dominant deflection:
                val steerVal = if (Math.abs(deltaX) > Math.abs(deltaY)) deltaX else -deltaY

                if (steerVal < -deadzone) {
                    newMask = newMask or RetroKey.KEY_RIGHT
                } else if (steerVal > deadzone) {
                    newMask = newMask or RetroKey.KEY_LEFT
                }

                if (newMask != currentMask) {
                    currentMask = newMask
                    onKeyMaskChanged?.invoke(currentMask)
                }
            }
            SensorMode.NATIVE_GYRO -> {
                // Feed normalized tilt coordinates (-1.0 to 1.0)
                val normX = (deltaX / 9.8f).coerceIn(-1.0f, 1.0f)
                val normY = (deltaY / 9.8f).coerceIn(-1.0f, 1.0f)
                onNativeTiltChanged?.invoke(normX, normY)
            }
            SensorMode.DISABLED -> {
                resetMask()
            }
        }
    }

    private fun resetMask() {
        if (currentMask != RetroKey.NO_KEYS_MASK) {
            currentMask = RetroKey.NO_KEYS_MASK
            onKeyMaskChanged?.invoke(RetroKey.NO_KEYS_MASK)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.isNotEmpty()) {
            val x = event.values[0]
            val y = if (event.values.size > 1) event.values[1] else 0f
            val z = if (event.values.size > 2) event.values[2] else 9.8f
            processAcceleration(x, y, z)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
