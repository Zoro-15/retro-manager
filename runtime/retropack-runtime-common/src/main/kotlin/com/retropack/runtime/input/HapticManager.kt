package com.retropack.runtime.input

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import com.retropack.domain.model.HapticFeedbackMode
import com.retropack.runtime.audio.AudioHapticEngine
import com.retropack.runtime.logging.RuntimeLogger

/**
 * Modern Android Haptic and Gamepad Rumble Manager for RetroPack.
 *
 * Implements Feature 2 specifications:
 * - Native motor vibration for rumble-enabled consoles (PS1 DualShock, N64 Rumble Pak, GBA Drill Dozer).
 * - Physical DualSense, Xbox, and Android gamepad motor routing via InputDevice vibrators.
 * - Target Android 12+ (API 31+) VibratorManager & VibrationEffect.Composition primitives
 *   (PRIMITIVE_THUD, PRIMITIVE_CLICK, PRIMITIVE_HEAVY_CLICK, PRIMITIVE_LOW_TICK).
 * - Backward-compatible fallback for Android 8.0-11 via VibrationEffect.createOneShot().
 * - Real-time Audio-Reactive transient integration via [AudioHapticEngine].
 */
class HapticManager(
    private val context: Context
) {

    var hapticMode: HapticFeedbackMode = HapticFeedbackMode.AUDIO_REACTIVE
    var rumbleStrength: Float = 1.0f // 0.0f to 1.0f

    val audioHapticEngine: AudioHapticEngine = AudioHapticEngine().apply {
        onHapticTransient = { intensity ->
            if (hapticMode == HapticFeedbackMode.AUDIO_REACTIVE) {
                triggerAudioTransient(intensity)
            }
        }
    }

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Dispatches native emulator core rumble (e.g. PS1 DualShock motor 0/1 or N64 Rumble Pak).
     *
     * @param motorIndex 0 for large low-frequency motor, 1 for small high-frequency motor.
     * @param strengthPercent Vibration intensity (0 to 100).
     * @param durationMs Duration in milliseconds (default 50ms per frame tick).
     */
    fun triggerNativeRumble(motorIndex: Int, strengthPercent: Int, durationMs: Int = 50) {
        if (hapticMode == HapticFeedbackMode.OFF || strengthPercent <= 0) return

        val effectiveIntensity = (strengthPercent / 100.0f * rumbleStrength).coerceIn(0.0f, 1.0f)
        if (effectiveIntensity <= 0.01f) return

        // 1. Dispatch to connected external physical gamepads (DualSense / Xbox)
        dispatchGamepadRumble(effectiveIntensity, durationMs)

        // 2. Dispatch to device phone chassis vibrator
        dispatchChassisVibration(effectiveIntensity, durationMs, motorIndex == 0)
    }

    /**
     * Dispatches a punchy audio-reactive sub-bass transient haptic pulse.
     */
    fun triggerAudioTransient(intensity: Float) {
        if (hapticMode != HapticFeedbackMode.AUDIO_REACTIVE) return
        val effectiveIntensity = (intensity * rumbleStrength).coerceIn(0.0f, 1.0f)
        if (effectiveIntensity <= 0.05f) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val v = vibrator ?: return
                if (v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                    val comp = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, effectiveIntensity)
                        .compose()
                    v.vibrate(comp)
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = (255 * effectiveIntensity).toInt().coerceIn(1, 255)
                val effect = VibrationEffect.createOneShot(20L, amplitude)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20L)
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Triggers a crisp tactile button press click.
     */
    fun triggerButtonPress(intensity: Float = 1.0f) {
        val effective = (intensity * rumbleStrength).coerceIn(0.1f, 1.0f)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val v = vibrator ?: return
                if (v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    val comp = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, effective)
                        .compose()
                    v.vibrate(comp)
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = (255 * effective).toInt().coerceIn(1, 255)
                vibrator?.vibrate(VibrationEffect.createOneShot(12L, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(12L)
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Dispatches vibration to external Bluetooth/USB gamepad hardware motors.
     */
    private fun dispatchGamepadRumble(intensity: Float, durationMs: Int) {
        try {
            val deviceIds = InputDevice.getDeviceIds()
            for (id in deviceIds) {
                val device = InputDevice.getDevice(id) ?: continue
                if (device.sources and InputDevice.SOURCE_GAMEPAD != 0 ||
                    device.sources and InputDevice.SOURCE_JOYSTICK != 0) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vm = device.vibratorManager
                        val v = vm.defaultVibrator
                        if (v.hasVibrator()) {
                            val amplitude = (255 * intensity).toInt().coerceIn(1, 255)
                            v.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), amplitude))
                        }
                    } else {
                        val v = device.vibrator
                        if (v.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val amplitude = (255 * intensity).toInt().coerceIn(1, 255)
                                v.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), amplitude))
                            } else {
                                @Suppress("DEPRECATION")
                                v.vibrate(durationMs.toLong())
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun dispatchChassisVibration(intensity: Float, durationMs: Int, isHeavyMotor: Boolean) {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val primitive = if (isHeavyMotor) {
                    VibrationEffect.Composition.PRIMITIVE_HEAVY_CLICK
                } else {
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
                }
                if (v.areAllPrimitivesSupported(primitive)) {
                    val comp = VibrationEffect.startComposition()
                        .addPrimitive(primitive, intensity)
                        .compose()
                    v.vibrate(comp)
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = (255 * intensity).toInt().coerceIn(1, 255)
                v.vibrate(VibrationEffect.createOneShot(durationMs.toLong().coerceIn(10L, 200L), amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs.toLong().coerceIn(10L, 200L))
            }
        } catch (_: Throwable) {
        }
    }
}
