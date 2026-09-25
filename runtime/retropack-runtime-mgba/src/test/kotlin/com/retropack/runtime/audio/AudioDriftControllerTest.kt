package com.retropack.runtime.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioDriftControllerTest {

    @Test
    fun `calculates samples per frame correctly for 44100 Hz stereo at 60 fps`() {
        val controller = AudioDriftController(sampleRate = 44_100, channelCount = 2, fpsTarget = 60.0f)

        // 44100 / 60 = 735 stereo frames = 1470 samples
        assertEquals(1470, controller.samplesPerFrame)
        assertEquals(2940, controller.targetWatermarkSamples) // 2 frames
        assertEquals(7350, controller.maxWatermarkSamples)    // 5 frames
    }

    @Test
    fun `evaluates normal buffer occupancy as pass through`() {
        val controller = AudioDriftController()
        val decision = controller.evaluate(controller.targetWatermarkSamples)

        assertEquals(AudioDriftController.DriftAction.PASS_THROUGH, decision.action)
        assertEquals(controller.targetWatermarkSamples, decision.adjustedSampleCount)
        assertEquals(0, decision.dropCount)
    }

    @Test
    fun `detects buffer overflow and drops excess samples to bound latency`() {
        val controller = AudioDriftController()
        val overflowSamples = controller.maxWatermarkSamples + 2000

        val decision = controller.evaluate(overflowSamples)

        assertEquals(AudioDriftController.DriftAction.DROP_EXCESS, decision.action)
        assertEquals(controller.targetWatermarkSamples, decision.adjustedSampleCount)
        val expectedDrop = overflowSamples - controller.targetWatermarkSamples
        assertEquals(expectedDrop, decision.dropCount)

        val stats = controller.getStats()
        assertEquals(expectedDrop.toLong(), stats.totalDropped)
        assertEquals(overflowSamples.toLong(), stats.totalProcessed)
    }

    @Test
    fun `records underrun on zero available samples`() {
        val controller = AudioDriftController()
        val decision = controller.evaluate(0)

        assertEquals(AudioDriftController.DriftAction.PASS_THROUGH, decision.action)
        assertEquals(0, decision.adjustedSampleCount)

        val stats = controller.getStats()
        assertEquals(1, stats.underruns)

        controller.recordUnderrun()
        assertEquals(2, controller.getStats().underruns)
    }

    @Test
    fun `reset clears all statistics`() {
        val controller = AudioDriftController()
        controller.evaluate(controller.maxWatermarkSamples + 1000)
        controller.recordUnderrun()

        controller.reset()
        val stats = controller.getStats()
        assertEquals(0L, stats.totalProcessed)
        assertEquals(0L, stats.totalDropped)
        assertEquals(0, stats.underruns)
    }
}
