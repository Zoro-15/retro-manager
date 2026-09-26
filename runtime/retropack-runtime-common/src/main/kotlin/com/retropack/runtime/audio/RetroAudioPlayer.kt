package com.retropack.runtime.audio

import com.retropack.runtime.core.NativeCore
import java.util.Arrays

/**
 * High-level coordinator for low-latency 16-bit stereo emulation audio output at 44,100 Hz.
 *
 * Implements specifications from:
 * - masterplan.md Section 5.4: "Audio: Low-latency AudioTrack 16-bit stereo at 44,100 Hz;
 *   circular ring buffer with drift compensation to eliminate pops and crackles."
 * - roadmap.md Part 2.4.
 */
class RetroAudioPlayer(
    private val sink: AudioSink = AudioTrackSink(SAMPLE_RATE, CHANNEL_COUNT),
    private val sampleProvider: (ShortArray, Int) -> Int = { buf, max -> NativeCore.nativeGetAudioSamples(buf, max) },
    val driftController: AudioDriftController = AudioDriftController(SAMPLE_RATE, CHANNEL_COUNT)
) {
    companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNEL_COUNT = 2
        private const val PUMP_BUFFER_SIZE = 4_096
    }

    private val scratchBuffer = ShortArray(PUMP_BUFFER_SIZE)

    @Volatile
    var volume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
            applyVolume()
        }

    @Volatile
    var isMuted: Boolean = false
        set(value) {
            field = value
            applyVolume()
        }

    @Volatile
    var isPlaying: Boolean = false
        private set

    init {
        applyVolume()
    }

    fun start() {
        if (!isPlaying) {
            sink.play()
            isPlaying = true
        }
    }

    fun pause() {
        if (isPlaying) {
            sink.pause()
            isPlaying = false
        }
    }

    fun resume() {
        if (!isPlaying) {
            sink.play()
            isPlaying = true
        }
    }

    fun stop() {
        sink.pause()
        sink.flush()
        driftController.reset()
        isPlaying = false
    }

    fun release() {
        stop()
        sink.release()
    }

    /**
     * Pulls pending audio samples from the native ring buffer, evaluates drift compensation,
     * and streams them into the audio sink.
     *
     * Should be called periodically (e.g. after each native frame or on an audio pump thread).
     *
     * @return Number of samples successfully dispatched to [AudioSink].
     */
    fun pumpAudio(): Int {
        if (!isPlaying) return 0

        val pulled = sampleProvider(scratchBuffer, scratchBuffer.size)
        if (pulled <= 0) {
            driftController.evaluate(0)
            return 0
        }

        if (isMuted) {
            Arrays.fill(scratchBuffer, 0, pulled, 0.toShort())
        }

        // Evaluate against true ringbuffer occupancy, not the drained batch:
        // the batch size (~1470) can never trip the watermark thresholds, so
        // latency grew unbounded while the controller reported healthy.
        // Falls back to the drained count when native is unavailable (tests).
        val occupancy = try {
            NativeCore.nativeGetAudioAvailable()
        } catch (_: UnsatisfiedLinkError) {
            pulled
        }
        val decision = driftController.evaluate(if (occupancy >= 0) occupancy else pulled)
        val samplesToWrite = when (decision.action) {
            AudioDriftController.DriftAction.PASS_THROUGH -> pulled
            AudioDriftController.DriftAction.DROP_EXCESS -> decision.adjustedSampleCount.coerceAtMost(pulled)
        }

        val written = sink.write(scratchBuffer, 0, samplesToWrite)
        if (written < samplesToWrite) {
            // Dead/full sink (e.g. AudioTrack init failed and writes return 0):
            // record it instead of silently discarding drained samples.
            driftController.recordUnderrun()
        }
        return written
    }

    private fun applyVolume() {
        val effectiveVolume = if (isMuted) 0.0f else volume
        sink.setVolume(effectiveVolume)
    }
}
