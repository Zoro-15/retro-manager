package com.retropack.runtime.input

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Dual-State Mechanical Haptic Vibration Engine for RetroPack.
 *
 * Implements specifications from:
 * - DuckStation and PPSSPP-inspired dual-state tactile waveforms.
 * - Touch Down (Button Press): Sharp, crisp haptic click (15ms high-amplitude impulse).
 * - Touch Up (Button Release): Subtle micro-tick (8ms low-amplitude release).
 * - Adjustable intensity scaling (10% to 100%).
 */
object HapticEngine {

    enum class HapticType {
        PRESS,
        RELEASE,
        TICK,
        CLICK,
        HEAVY
    }

    /** Test hook callback invoked when any haptic action is dispatched. */
    var onHapticAction: ((type: HapticType, intensity: Float) -> Unit)? = null

    /**
     * Triggers a sharp, tactile click upon initial button touchdown.
     *
     * @param context Context used to resolve system Vibrator service.
     * @param intensity Global haptic strength multiplier (0.10f to 1.0f).
     * @param view Optional View to fallback to standard system performHapticFeedback.
     */
    fun triggerPress(context: Context?, intensity: Float = 1.0f, view: View? = null) {
        val clampedIntensity = intensity.coerceIn(0.10f, 1.0f)
        onHapticAction?.invoke(HapticType.PRESS, clampedIntensity)

        if (context == null) {
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            return
        }

        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ Predefined click effect
                    val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    vibrator.vibrate(effect)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android 8+ Amplitude-scaled waveform impulse
                    val amplitude = (255 * clampedIntensity).toInt().coerceIn(1, 255)
                    val effect = VibrationEffect.createOneShot(15L, amplitude)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(15L)
                }
            } else {
                view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        } catch (_: Throwable) {
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /**
     * Triggers a subtle, short micro-tick when a virtual button is released.
     *
     * @param context Context used to resolve system Vibrator service.
     * @param intensity Global haptic strength multiplier (0.10f to 1.0f).
     * @param view Optional View fallback.
     */
    fun triggerRelease(context: Context?, intensity: Float = 1.0f, view: View? = null) {
        val clampedIntensity = intensity.coerceIn(0.10f, 1.0f)
        onHapticAction?.invoke(HapticType.RELEASE, clampedIntensity)

        if (context == null) return

        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    vibrator.vibrate(effect)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = (110 * clampedIntensity).toInt().coerceIn(1, 255)
                    val effect = VibrationEffect.createOneShot(8L, amplitude)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(8L)
                }
            }
        } catch (_: Throwable) {
            // Graceful silent fallback
        }
    }

    /**
     * Triggers a crisp UI interaction tick (e.g. slider step or tab switch).
     */
    fun triggerTick(context: Context?, intensity: Float = 1.0f, view: View? = null) {
        val clampedIntensity = intensity.coerceIn(0.10f, 1.0f)
        onHapticAction?.invoke(HapticType.TICK, clampedIntensity)

        if (context == null) {
            view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            return
        }

        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    vibrator.vibrate(effect)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = (90 * clampedIntensity).toInt().coerceIn(1, 255)
                    val effect = VibrationEffect.createOneShot(6L, amplitude)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(6L)
                }
            } else {
                view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        } catch (_: Throwable) {
            view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /**
     * Triggers a heavy vibration click (e.g. Save State completion, gesture action).
     */
    fun triggerHeavy(context: Context?, intensity: Float = 1.0f, view: View? = null) {
        val clampedIntensity = intensity.coerceIn(0.10f, 1.0f)
        onHapticAction?.invoke(HapticType.HEAVY, clampedIntensity)

        if (context == null) {
            view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return
        }

        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    vibrator.vibrate(effect)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = (255 * clampedIntensity).toInt().coerceIn(1, 255)
                    val effect = VibrationEffect.createOneShot(30L, amplitude)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30L)
                }
            } else {
                view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } catch (_: Throwable) {
            view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}
