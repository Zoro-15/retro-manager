package com.retropack.runtime.audio

/**
 * Monitors circular buffer watermarks and applies dynamic drift compensation to prevent
 * audio pops, crackles, and creeping latency during emulation.
 *
 * Issue #43 adds a proportional-integral (PI) occupancy regulator: instead of only
 * dropping excess backlog after it accumulates, the controller continuously computes a
 * micro playback-rate adjustment (±3%) that feeds the native AAudio direct-pull
 * callback. The callback consumes rate-scaled input from the SPSC ring buffer, so the
 * buffer occupancy converges on the target watermark without audible artifacts — the
 * same rate-control strategy RetroArch uses for clock-drift correction.
 */
class AudioDriftController(
    val sampleRate: Int = 44_100,
    val channelCount: Int = 2,
    fpsTarget: Float = 60.0f
) {
    /**
     * Number of 16-bit stereo samples generated in one emulation frame.
     */
    val samplesPerFrame: Int = ((sampleRate / fpsTarget) * channelCount).toInt()

    /**
     * Target buffer depth: 2 frames of audio (~33.3 ms).
     */
    val targetWatermarkSamples: Int = samplesPerFrame * 2

    /**
     * Maximum permissible buffer lag before dropping excess samples: 5 frames (~83.3 ms).
     */
    val maxWatermarkSamples: Int = samplesPerFrame * 5

    private var totalSamplesProcessed: Long = 0
    private var totalSamplesDropped: Long = 0
    private var underrunCount: Int = 0

    // --- PI regulator state (issue #43) -------------------------------------
    private var piIntegral: Float = 0f
    private var lastRateAdjustment: Float = 1.0f

    enum class DriftAction {
        PASS_THROUGH,
        DROP_EXCESS
    }

    data class DriftDecision(
        val action: DriftAction,
        val adjustedSampleCount: Int,
        val dropCount: Int
    )

    data class Stats(
        val totalProcessed: Long,
        val totalDropped: Long,
        val underruns: Int
    )

    /**
     * Evaluates buffer occupancy [availableSamples] against watermark thresholds
     * and bounds playback latency if the buffer overflows.
     */
    fun evaluate(availableSamples: Int): DriftDecision {
        totalSamplesProcessed += availableSamples

        return when {
            availableSamples > maxWatermarkSamples -> {
                val excess = availableSamples - targetWatermarkSamples
                totalSamplesDropped += excess
                DriftDecision(
                    action = DriftAction.DROP_EXCESS,
                    adjustedSampleCount = targetWatermarkSamples,
                    dropCount = excess
                )
            }
            availableSamples == 0 -> {
                underrunCount++
                DriftDecision(
                    action = DriftAction.PASS_THROUGH,
                    adjustedSampleCount = 0,
                    dropCount = 0
                )
            }
            else -> {
                DriftDecision(
                    action = DriftAction.PASS_THROUGH,
                    adjustedSampleCount = availableSamples,
                    dropCount = 0
                )
            }
        }
    }

    /**
     * Proportional-integral occupancy regulation (issue #43).
     *
     * Maps the current buffer occupancy onto a micro playback-rate multiplier in
     * [RATE_MIN, RATE_MAX]:
     *  - occupancy above the target watermark → rate > 1.0 (drain faster, shrink latency)
     *  - occupancy below the target watermark → rate < 1.0 (drain slower, bridge the core)
     *
     * The proportional term reacts to the instantaneous error; the integral term
     * (clamped to ± [PI_INTEGRAL_LIMIT_FRAMES] × target watermark samples) eliminates the
     * steady-state offset between the core clock (~59.7275 Hz NTSC) and the display/DAC
     * clock (60.00 Hz or VRR). The evaluation interval is one emulation frame.
     *
     * @param availableSamples Current ring buffer occupancy in samples.
     * @return Playback rate multiplier for the native AAudio callback.
     */
    fun computeRateAdjustment(availableSamples: Int): Float {
        val error = (availableSamples - targetWatermarkSamples).toFloat()

        // Normalize the error against the target watermark so the proportional
        // gain is sample-rate independent (GB 32.1k vs PS1 44.1k vs N64 48k).
        val normError = if (targetWatermarkSamples > 0) error / targetWatermarkSamples else 0f
        val proportional = PI_PROPORTIONAL_GAIN * normError

        val integralLimit = PI_INTEGRAL_LIMIT_FRAMES * targetWatermarkSamples
        piIntegral = (piIntegral + error).coerceIn(-integralLimit, integralLimit)
        val normIntegral = if (targetWatermarkSamples > 0) piIntegral / targetWatermarkSamples else 0f
        val integral = PI_INTEGRAL_GAIN * normIntegral

        val rate = 1.0f + proportional + integral
        lastRateAdjustment = rate.coerceIn(RATE_MIN, RATE_MAX)
        return lastRateAdjustment
    }

    /**
     * Last rate adjustment computed by [computeRateAdjustment] (diagnostics).
     */
    fun getLastRateAdjustment(): Float = lastRateAdjustment

    fun recordUnderrun() {
        underrunCount++
    }

    fun reset() {
        totalSamplesProcessed = 0
        totalSamplesDropped = 0
        underrunCount = 0
        piIntegral = 0f
        lastRateAdjustment = 1.0f
    }

    fun getStats(): Stats = Stats(
        totalProcessed = totalSamplesProcessed,
        totalDropped = totalSamplesDropped,
        underruns = underrunCount
    )

    companion object {
        /** Hard clamp on the micro time-stretch: ±3% stays perceptually transparent. */
        const val RATE_MIN: Float = 0.97f
        const val RATE_MAX: Float = 1.03f

        /**
         * Proportional gain: 10% watermark error yields a 0.8% rate nudge,
         * reaching the ±3% clamp near a ~37% error (severe drift).
         */
        const val PI_PROPORTIONAL_GAIN: Float = 0.08f

        /**
         * Integral gain per frame: a persistent one-frame error accumulates
         * 0.15% rate per second of drift, eliminating steady-state offsets
         * without audible wow.
         */
        const val PI_INTEGRAL_GAIN: Float = 0.0015f

        /** Integral windup clamp: saturates at 3 × target watermark (~66 ms) of
         *  accumulated error, which is ample headroom for the ~0.45% sustained
         *  NTSC→DAC clock mismatch while bounding wow-and-flutter. */
        const val PI_INTEGRAL_LIMIT_FRAMES: Float = 3f
    }
}
