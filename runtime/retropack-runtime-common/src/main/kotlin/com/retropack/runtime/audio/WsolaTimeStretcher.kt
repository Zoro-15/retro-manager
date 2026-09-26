package com.retropack.runtime.audio

import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Real-Time WSOLA (Waveform Similarity Overlap-Add) Time-Stretching Audio Engine.
 *
 * Implements Feature 4 specifications:
 * - Maintains pitch while playing audio at accelerated fast-forward speeds (1.25x to 4.0x).
 * - Eliminates high-pitched chipmunk audio distortion and buffer pops during turbo/fast-forward.
 * - Analyzes cross-correlation across overlapping 20ms audio grains (~882 samples at 44.1 kHz).
 * - Smoothly cross-fades overlapping grains with Hann windowing to prevent clicks and phase cancellation.
 * - Zero allocation in the steady-state audio processing loop.
 */
class WsolaTimeStretcher(
    val sampleRate: Int = 44_100,
    val channels: Int = 2
) {

    companion object {
        private const val GRAIN_DURATION_MS = 20
        private const val SEARCH_WINDOW_MS = 4
    }

    // Number of stereo sample frames per grain (20ms)
    private val grainFrames: Int = (sampleRate * GRAIN_DURATION_MS) / 1000
    private val grainSamples: Int = grainFrames * channels

    // Overlap size (50% of grain)
    private val overlapFrames: Int = grainFrames / 2
    private val overlapSamples: Int = overlapFrames * channels

    // Synthesis hop size
    private val synthHopFrames: Int = overlapFrames
    private val synthHopSamples: Int = synthHopFrames * channels

    // Max search delta for cross-correlation
    private val maxSearchDeltaFrames: Int = (sampleRate * SEARCH_WINDOW_MS) / 1000
    private val maxSearchDeltaSamples: Int = maxSearchDeltaFrames * channels

    // Precomputed Hann cross-fade window
    private val hannWindow: FloatArray = FloatArray(overlapFrames) { i ->
        0.5f * (1.0f - cos(Math.PI.toFloat() * i / overlapFrames))
    }

    // Internal circular ring buffers
    private val inputRingBuffer = ShortArray(grainSamples * 8)
    private var inputWritePos = 0
    private var inputReadPos = 0
    private var inputAvailableSamples = 0

    // Overlap accumulation buffer
    private val prevOverlapBuffer = ShortArray(overlapSamples)

    // Intermediate output buffer
    private val outputBuffer = ShortArray(grainSamples * 4)
    private var outputWritePos = 0
    private var outputReadPos = 0
    private var outputAvailableSamples = 0

    @Volatile
    var speedRatio: Float = 1.0f
        set(value) {
            field = value.coerceIn(1.0f, 4.0f)
        }

    @Volatile
    var isEnabled: Boolean = true

    /**
     * Feeds incoming 16-bit PCM audio samples from the emulator into the WSOLA stretcher.
     *
     * @param inSamples Raw interleaved stereo samples.
     * @param offset Start offset in [inSamples].
     * @param count Number of samples to feed.
     */
    fun process(inSamples: ShortArray, offset: Int, count: Int): Int {
        if (count <= 0) return 0
        if (!isEnabled || Math.abs(speedRatio - 1.0f) < 0.05f) {
            // Passthrough directly to output buffer
            appendOutput(inSamples, offset, count)
            return count
        }

        // Push into input ring buffer
        var i = 0
        while (i < count) {
            val space = inputRingBuffer.size - inputAvailableSamples
            if (space <= 0) break
            val toCopy = Math.min(count - i, space)
            for (j in 0 until toCopy) {
                inputRingBuffer[inputWritePos] = inSamples[offset + i + j]
                inputWritePos = (inputWritePos + 1) % inputRingBuffer.size
            }
            inputAvailableSamples += toCopy
            i += toCopy
        }

        // Process grain chunks while enough input is available
        val analysisHopFrames = (synthHopFrames * speedRatio).roundToInt()
        val analysisHopSamples = analysisHopFrames * channels
        val requiredInputSamples = grainSamples + maxSearchDeltaSamples * 2

        while (inputAvailableSamples >= requiredInputSamples) {
            synthesizeGrain(analysisHopFrames, analysisHopSamples)
        }

        return count
    }

    /**
     * Drains processed pitch-preserved time-compressed samples.
     *
     * @param outBuffer Destination buffer.
     * @param offset Start offset in [outBuffer].
     * @param maxSamples Maximum samples to read.
     * @return Number of samples read.
     */
    fun drain(outBuffer: ShortArray, offset: Int, maxSamples: Int): Int {
        val available = outputAvailableSamples
        if (available <= 0 || maxSamples <= 0) return 0
        val toDrain = Math.min(available, maxSamples)

        for (i in 0 until toDrain) {
            outBuffer[offset + i] = outputBuffer[outputReadPos]
            outputReadPos = (outputReadPos + 1) % outputBuffer.size
        }
        outputAvailableSamples -= toDrain
        return toDrain
    }

    /**
     * Core WSOLA Grain Cross-Correlation Alignment and Hann Overlap-Add step.
     */
    private fun synthesizeGrain(analysisHopFrames: Int, analysisHopSamples: Int) {
        // Find best template offset k in [-maxSearchDeltaFrames, +maxSearchDeltaFrames]
        // using cross-correlation with previous overlap buffer
        var bestDeltaFrames = 0
        var maxCorrelation = Long.MIN_VALUE

        val stepFrames = 2 // Skip frames to optimize correlation search
        var delta = -maxSearchDeltaFrames
        while (delta <= maxSearchDeltaFrames) {
            var correlation = 0L
            for (f in 0 until overlapFrames step stepFrames) {
                val inputFramePos = (inputReadPos + (delta * channels) + (f * channels) + inputRingBuffer.size) % inputRingBuffer.size
                val prevFramePos = f * channels

                val sampleInL = inputRingBuffer[inputFramePos].toLong()
                val samplePrevL = prevOverlapBuffer[prevFramePos].toLong()
                val sampleInR = inputRingBuffer[(inputFramePos + 1) % inputRingBuffer.size].toLong()
                val samplePrevR = prevOverlapBuffer[prevFramePos + 1].toLong()

                correlation += (sampleInL * samplePrevL) + (sampleInR * samplePrevR)
            }

            if (correlation > maxCorrelation) {
                maxCorrelation = correlation
                bestDeltaFrames = delta
            }
            delta += stepFrames
        }

        val grainStartPos = (inputReadPos + (bestDeltaFrames * channels) + inputRingBuffer.size) % inputRingBuffer.size

        // 1. Cross-fade overlap region (first half of grain with prevOverlapBuffer)
        for (f in 0 until overlapFrames) {
            val inPos = (grainStartPos + (f * channels)) % inputRingBuffer.size
            val prevPos = f * channels
            val win = hannWindow[f]
            val invWin = 1.0f - win

            val mixedL = (prevOverlapBuffer[prevPos] * invWin + inputRingBuffer[inPos] * win).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            val mixedR = (prevOverlapBuffer[prevPos + 1] * invWin + inputRingBuffer[(inPos + 1) % inputRingBuffer.size] * win).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

            writeOutputSample(mixedL)
            writeOutputSample(mixedR)
        }

        // 2. Direct copy non-overlap region
        val nonOverlapFrames = grainFrames - overlapFrames * 2
        if (nonOverlapFrames > 0) {
            for (f in 0 until nonOverlapFrames) {
                val inPos = (grainStartPos + (overlapFrames * channels) + (f * channels)) % inputRingBuffer.size
                writeOutputSample(inputRingBuffer[inPos])
                writeOutputSample(inputRingBuffer[(inPos + 1) % inputRingBuffer.size])
            }
        }

        // 3. Store tail of grain into prevOverlapBuffer for next iteration
        val tailStartPos = (grainStartPos + grainSamples - overlapSamples + inputRingBuffer.size) % inputRingBuffer.size
        for (s in 0 until overlapSamples) {
            prevOverlapBuffer[s] = inputRingBuffer[(tailStartPos + s) % inputRingBuffer.size]
        }

        // Advance input read pointer by analysis hop
        inputReadPos = (inputReadPos + analysisHopSamples) % inputRingBuffer.size
        inputAvailableSamples -= analysisHopSamples
    }

    private fun writeOutputSample(sample: Short) {
        if (outputAvailableSamples >= outputBuffer.size) {
            // Buffer full, drop oldest
            outputReadPos = (outputReadPos + 1) % outputBuffer.size
            outputAvailableSamples--
        }
        outputBuffer[outputWritePos] = sample
        outputWritePos = (outputWritePos + 1) % outputBuffer.size
        outputAvailableSamples++
    }

    private fun appendOutput(inSamples: ShortArray, offset: Int, count: Int) {
        for (i in 0 until count) {
            writeOutputSample(inSamples[offset + i])
        }
    }

    fun reset() {
        inputWritePos = 0
        inputReadPos = 0
        inputAvailableSamples = 0
        outputWritePos = 0
        outputReadPos = 0
        outputAvailableSamples = 0
        prevOverlapBuffer.fill(0)
    }
}
