package com.retropack.runtime.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RetroAudioPlayerTest {

    private class FakeAudioSink : AudioSink {
        override val sampleRate: Int = 44_100
        override val channelCount: Int = 2

        var played = false
        var paused = false
        var flushed = false
        var released = false
        var currentVolume = 1.0f
        var totalSamplesWritten = 0
        val writtenData = mutableListOf<Short>()

        override fun write(samples: ShortArray, offset: Int, count: Int): Int {
            totalSamplesWritten += count
            for (i in offset until offset + count) {
                writtenData.add(samples[i])
            }
            return count
        }

        override fun play() { played = true; paused = false }
        override fun pause() { paused = true; played = false }
        override fun flush() { flushed = true; writtenData.clear() }
        override fun release() { released = true }
        override fun setVolume(volume: Float) { currentVolume = volume }
        override fun getUnderrunCount(): Int = 0
    }

    @Test
    fun `initializes with default volume and unmuted state`() {
        val fakeSink = FakeAudioSink()
        val player = RetroAudioPlayer(sink = fakeSink)

        assertEquals(1.0f, player.volume)
        assertFalse(player.isMuted)
        assertFalse(player.isPlaying)
        assertEquals(1.0f, fakeSink.currentVolume)
    }

    @Test
    fun `clamps volume between 0 and 1`() {
        val fakeSink = FakeAudioSink()
        val player = RetroAudioPlayer(sink = fakeSink)

        player.volume = 1.5f
        assertEquals(1.0f, player.volume)
        assertEquals(1.0f, fakeSink.currentVolume)

        player.volume = -0.2f
        assertEquals(0.0f, player.volume)
        assertEquals(0.0f, fakeSink.currentVolume)

        player.volume = 0.75f
        assertEquals(0.75f, player.volume)
        assertEquals(0.75f, fakeSink.currentVolume)
    }

    @Test
    fun `mute gating silences sink and unmuting restores volume`() {
        val fakeSink = FakeAudioSink()
        val player = RetroAudioPlayer(sink = fakeSink)
        player.volume = 0.8f
        assertEquals(0.8f, fakeSink.currentVolume)

        player.isMuted = true
        assertTrue(player.isMuted)
        assertEquals(0.0f, fakeSink.currentVolume)

        player.isMuted = false
        assertFalse(player.isMuted)
        assertEquals(0.8f, fakeSink.currentVolume)
    }

    @Test
    fun `lifecycle controls play pause stop and release`() {
        val fakeSink = FakeAudioSink()
        val player = RetroAudioPlayer(sink = fakeSink)

        player.start()
        assertTrue(player.isPlaying)
        assertTrue(fakeSink.played)

        player.pause()
        assertFalse(player.isPlaying)
        assertTrue(fakeSink.paused)

        player.resume()
        assertTrue(player.isPlaying)
        assertTrue(fakeSink.played)

        player.stop()
        assertFalse(player.isPlaying)
        assertTrue(fakeSink.flushed)

        player.release()
        assertTrue(fakeSink.released)
    }

    @Test
    fun `pumpAudio ignores execution when not playing`() {
        val fakeSink = FakeAudioSink()
        val player = RetroAudioPlayer(
            sink = fakeSink,
            sampleProvider = { buf, max ->
                buf[0] = 100
                1
            }
        )

        val written = player.pumpAudio()
        assertEquals(0, written)
        assertEquals(0, fakeSink.totalSamplesWritten)
    }

    @Test
    fun `pumpAudio pulls samples and dispatches to sink when playing`() {
        val fakeSink = FakeAudioSink()
        val testSamples = shortArrayOf(10, -20, 30, -40)
        val player = RetroAudioPlayer(
            sink = fakeSink,
            sampleProvider = { buf, max ->
                for (i in testSamples.indices) {
                    buf[i] = testSamples[i]
                }
                testSamples.size
            }
        )

        player.start()
        val written = player.pumpAudio()

        assertEquals(testSamples.size, written)
        assertEquals(testSamples.size, fakeSink.totalSamplesWritten)
        assertEquals(10.toShort(), fakeSink.writtenData[0])
        assertEquals((-40).toShort(), fakeSink.writtenData[3])
    }

    @Test
    fun `pumpAudio zeroes samples when muted`() {
        val fakeSink = FakeAudioSink()
        val testSamples = shortArrayOf(100, 200, 300, 400)
        val player = RetroAudioPlayer(
            sink = fakeSink,
            sampleProvider = { buf, max ->
                for (i in testSamples.indices) {
                    buf[i] = testSamples[i]
                }
                testSamples.size
            }
        )

        player.start()
        player.isMuted = true

        val written = player.pumpAudio()
        assertEquals(4, written)
        for (i in 0 until 4) {
            assertEquals(0.toShort(), fakeSink.writtenData[i], "Muted audio must write zero samples")
        }
    }
}
