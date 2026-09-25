package com.retropack.runtime.audio

/**
 * Monitors circular buffer watermarks and applies dynamic drift compensation to prevent
 * audio pops, crackles, and creeping latency during emulation.
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

    fun recordUnderrun() {
        underrunCount++
    }

    fun reset() {
        totalSamplesProcessed = 0
        totalSamplesDropped = 0
        underrunCount = 0
    }

    fun getStats(): Stats = Stats(
        totalProcessed = totalSamplesProcessed,
        totalDropped = totalSamplesDropped,
        underruns = underrunCount
    )
}
