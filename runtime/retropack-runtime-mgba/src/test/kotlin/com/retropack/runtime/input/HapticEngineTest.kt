package com.retropack.runtime.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HapticEngineTest {

    private val dispatchedActions = mutableListOf<Pair<HapticEngine.HapticType, Float>>()

    @BeforeEach
    fun setUp() {
        dispatchedActions.clear()
        HapticEngine.onHapticAction = { type, intensity ->
            dispatchedActions.add(Pair(type, intensity))
        }
    }

    @Test
    fun `triggerPress dispatches press haptic with clamped intensity`() {
        HapticEngine.triggerPress(null, 0.8f)
        assertEquals(1, dispatchedActions.size)
        assertEquals(HapticEngine.HapticType.PRESS, dispatchedActions[0].first)
        assertEquals(0.8f, dispatchedActions[0].second, 0.001f)

        // Test intensity clamping
        HapticEngine.triggerPress(null, 1.5f)
        assertEquals(HapticEngine.HapticType.PRESS, dispatchedActions[1].first)
        assertEquals(1.0f, dispatchedActions[1].second, 0.001f)

        HapticEngine.triggerPress(null, 0.01f)
        assertEquals(HapticEngine.HapticType.PRESS, dispatchedActions[2].first)
        assertEquals(0.10f, dispatchedActions[2].second, 0.001f)
    }

    @Test
    fun `triggerRelease dispatches subtle release tick`() {
        HapticEngine.triggerRelease(null, 0.5f)
        assertEquals(1, dispatchedActions.size)
        assertEquals(HapticEngine.HapticType.RELEASE, dispatchedActions[0].first)
        assertEquals(0.5f, dispatchedActions[0].second, 0.001f)
    }

    @Test
    fun `triggerTick and triggerHeavy dispatch corresponding waveforms`() {
        HapticEngine.triggerTick(null, 0.7f)
        HapticEngine.triggerHeavy(null, 1.0f)

        assertEquals(2, dispatchedActions.size)
        assertEquals(HapticEngine.HapticType.TICK, dispatchedActions[0].first)
        assertEquals(HapticEngine.HapticType.HEAVY, dispatchedActions[1].first)
    }
}
