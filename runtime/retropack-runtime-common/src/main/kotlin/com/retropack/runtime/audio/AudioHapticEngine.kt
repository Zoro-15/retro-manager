package com.retropack.runtime.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real-Time Audio-Reactive Frequency Analyzer and Transient Detector for RetroPack.
 *
 * Implements Feature 2 specifications:
 * - Runs a lightweight 1st-order IIR Low-Pass Filter (fc < 120 Hz) on incoming 16-bit PCM stereo audio streams.
 * - Detects sudden high-amplitude sub-bass transients (explosions, heavy hits, jump impacts, drum kicks)
 *   with dynamic gain thresholding.
 * - Converts detected impacts into subtle, punchy haptic micro-pulses for systems without native rumble
 *   (NES, SNES, Genesis, GB, PCE).
 * - Zero-allocation DSP processing in the audio pump thread.
 */
class AudioHapticEngine(
    val sampleRate: Int = 44_100,
    var sensitivityThreshold: Float = 2.2f,
    var minCooldownMs: Long = 65L
) {

    // LPF Filter coefficient: alpha = 2*pi*(fc/fs) / (1 + 2*pi*(fc/fs)) with fc=120Hz
    private val alpha: Float = run {
        val fc = 120.0f
        val dt = 1.0f / sampleRate.toFloat()
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * fc)
        dt / (rc + dt)
    }

    private var filterStateL: Float = 0.0f
    private var filterStateR: Float = 0.0f
    private var movingAvgEnergy: Float = 400.0f
    private var lastTriggerTimeMs: Long = 0L

    /**
     * Listener invoked when an audio transient sub-bass impact is detected.
     * Param: intensity normalized from 0.0f to 1.0f.
     */
    var onHapticTransient: ((intensity: Float) -> Unit)? = null

    /**
     * Processes a block of interleaved 16-bit signed stereo PCM audio samples.
     *
     * @param samples Interleaved stereo samples [L, R, L, R...].
     * @param count Total number of samples (must be even for stereo).
     */
    fun processSamples(samples: ShortArray, count: Int) {
        if (count <= 0) return

        var bassEnergyAccumulator = 0.0f
        val framePairs = count / 2
        if (framePairs == 0) return

        // Downsample analysis step (process every 2nd frame pair to minimize CPU overhead)
        val step = 2
        var processedFrames = 0

        var i = 0
        while (i < count - 1) {
            val sampleL = samples[i].toFloat()
            val sampleR = samples[i + 1].toFloat()

            // 1st-order IIR Low Pass Filter (fc ~ 120Hz)
            filterStateL += alpha * (sampleL - filterStateL)
            filterStateR += alpha * (sampleR - filterStateR)

            val bassSample = (filterStateL + filterStateR) * 0.5f
            bassEnergyAccumulator += bassSample * bassSample
            processedFrames++

            i += step * 2
        }

        if (processedFrames == 0) return

        val currentBassRms = sqrt(bassEnergyAccumulator / processedFrames.toFloat())

        // Moving baseline energy follower (slow attack, slow decay)
        movingAvgEnergy = movingAvgEnergy * 0.92f + currentBassRms * 0.08f

        val now = System.currentTimeMillis()
        if (now - lastTriggerTimeMs >= minCooldownMs) {
            // Check for transient burst above moving baseline
            val baseline = movingAvgEnergy.coerceAtLeast(350.0f)
            val ratio = currentBassRms / baseline

            if (ratio >= sensitivityThreshold && currentBassRms > 800.0f) {
                lastTriggerTimeMs = now
                val normalizedIntensity = ((currentBassRms - 800.0f) / 12000.0f).coerceIn(0.25f, 1.0f)
                onHapticTransient?.invoke(normalizedIntensity)
            }
        }
    }

    /**
     * Resets filter state and energy accumulator.
     */
    fun reset() {
        filterStateL = 0.0f
        filterStateR = 0.0f
        movingAvgEnergy = 400.0f
        lastTriggerTimeMs = 0L
    }
}
