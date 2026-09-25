package com.retropack.runtime.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EmulationStateTest {

    @Test
    fun `verify state query flags`() {
        // UNINITIALIZED
        assertFalse(EmulationState.UNINITIALIZED.isEmulating)
        assertFalse(EmulationState.UNINITIALIZED.isReady)
        assertTrue(EmulationState.UNINITIALIZED.isHalted)

        // INITIALIZED
        assertFalse(EmulationState.INITIALIZED.isEmulating)
        assertTrue(EmulationState.INITIALIZED.isReady)
        assertFalse(EmulationState.INITIALIZED.isHalted)

        // RUNNING
        assertTrue(EmulationState.RUNNING.isEmulating)
        assertTrue(EmulationState.RUNNING.isReady)
        assertFalse(EmulationState.RUNNING.isHalted)

        // PAUSED
        assertTrue(EmulationState.PAUSED.isEmulating)
        assertTrue(EmulationState.PAUSED.isReady)
        assertFalse(EmulationState.PAUSED.isHalted)

        // STOPPED
        assertFalse(EmulationState.STOPPED.isEmulating)
        assertFalse(EmulationState.STOPPED.isReady)
        assertTrue(EmulationState.STOPPED.isHalted)

        // ERROR
        assertFalse(EmulationState.ERROR.isEmulating)
        assertFalse(EmulationState.ERROR.isReady)
        assertTrue(EmulationState.ERROR.isHalted)
    }

    @Test
    fun `verify valid lifecycle transitions`() {
        // Self transitions are always valid
        for (state in EmulationState.entries) {
            assertTrue(state.canTransitionTo(state))
            assertDoesNotThrow { state.checkTransition(state) }
        }

        // UNINITIALIZED transitions
        assertTrue(EmulationState.UNINITIALIZED.canTransitionTo(EmulationState.INITIALIZED))
        assertTrue(EmulationState.UNINITIALIZED.canTransitionTo(EmulationState.ERROR))

        // INITIALIZED transitions
        assertTrue(EmulationState.INITIALIZED.canTransitionTo(EmulationState.RUNNING))
        assertTrue(EmulationState.INITIALIZED.canTransitionTo(EmulationState.STOPPED))
        assertTrue(EmulationState.INITIALIZED.canTransitionTo(EmulationState.ERROR))

        // RUNNING transitions
        assertTrue(EmulationState.RUNNING.canTransitionTo(EmulationState.PAUSED))
        assertTrue(EmulationState.RUNNING.canTransitionTo(EmulationState.STOPPED))
        assertTrue(EmulationState.RUNNING.canTransitionTo(EmulationState.ERROR))

        // PAUSED transitions
        assertTrue(EmulationState.PAUSED.canTransitionTo(EmulationState.RUNNING))
        assertTrue(EmulationState.PAUSED.canTransitionTo(EmulationState.STOPPED))
        assertTrue(EmulationState.PAUSED.canTransitionTo(EmulationState.ERROR))

        // STOPPED transitions
        assertTrue(EmulationState.STOPPED.canTransitionTo(EmulationState.INITIALIZED))
        assertTrue(EmulationState.STOPPED.canTransitionTo(EmulationState.UNINITIALIZED))
        assertTrue(EmulationState.STOPPED.canTransitionTo(EmulationState.ERROR))

        // ERROR transitions
        assertTrue(EmulationState.ERROR.canTransitionTo(EmulationState.STOPPED))
        assertTrue(EmulationState.ERROR.canTransitionTo(EmulationState.UNINITIALIZED))
    }

    @Test
    fun `verify invalid transitions throw IllegalStateException`() {
        assertFalse(EmulationState.UNINITIALIZED.canTransitionTo(EmulationState.RUNNING))
        assertFalse(EmulationState.UNINITIALIZED.canTransitionTo(EmulationState.PAUSED))
        assertFalse(EmulationState.UNINITIALIZED.canTransitionTo(EmulationState.STOPPED))

        assertFalse(EmulationState.INITIALIZED.canTransitionTo(EmulationState.PAUSED))
        assertFalse(EmulationState.INITIALIZED.canTransitionTo(EmulationState.UNINITIALIZED))

        assertFalse(EmulationState.RUNNING.canTransitionTo(EmulationState.UNINITIALIZED))
        assertFalse(EmulationState.RUNNING.canTransitionTo(EmulationState.INITIALIZED))

        assertFalse(EmulationState.PAUSED.canTransitionTo(EmulationState.UNINITIALIZED))
        assertFalse(EmulationState.PAUSED.canTransitionTo(EmulationState.INITIALIZED))

        assertFalse(EmulationState.STOPPED.canTransitionTo(EmulationState.RUNNING))
        assertFalse(EmulationState.STOPPED.canTransitionTo(EmulationState.PAUSED))

        assertFalse(EmulationState.ERROR.canTransitionTo(EmulationState.RUNNING))
        assertFalse(EmulationState.ERROR.canTransitionTo(EmulationState.PAUSED))
        assertFalse(EmulationState.ERROR.canTransitionTo(EmulationState.INITIALIZED))

        assertThrows<IllegalStateException> {
            EmulationState.UNINITIALIZED.checkTransition(EmulationState.RUNNING)
        }
        assertThrows<IllegalStateException> {
            EmulationState.STOPPED.checkTransition(EmulationState.PAUSED)
        }
    }
}
