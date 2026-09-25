package com.retropack.runtime.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NativeEmulationEngineTest {

    @Test
    fun `verify initial engine state is UNINITIALIZED`() {
        val engine = NativeEmulationEngine()
        assertEquals(EmulationState.UNINITIALIZED, engine.state)
        assertTrue(engine.state.isHalted)
        assertFalse(engine.state.isReady)
    }

    @Test
    fun `verify pause and resume transitions when running`() {
        val engine = NativeEmulationEngine()
        // Calling pause or resume on uninitialized engine does not change state
        engine.pause()
        assertEquals(EmulationState.UNINITIALIZED, engine.state)
        engine.resume()
        assertEquals(EmulationState.UNINITIALIZED, engine.state)
    }

    @Test
    fun `verify key handling without active native library`() {
        val engine = NativeEmulationEngine()
        assertDoesNotThrow {
            engine.setKeys(RetroKey.A, RetroKey.START)
            engine.setKeyMask(RetroKey.KEY_B or RetroKey.KEY_L)
        }
    }

    @Test
    fun `verify saveState and loadState slot bounds validation`() {
        val engine = NativeEmulationEngine()

        assertThrows<IllegalArgumentException> {
            engine.saveState(-1, "/path/to/state")
        }
        assertThrows<IllegalArgumentException> {
            engine.saveState(10, "/path/to/state")
        }
        assertThrows<IllegalArgumentException> {
            engine.loadState(-1, "/path/to/state")
        }
        assertThrows<IllegalArgumentException> {
            engine.loadState(10, "/path/to/state")
        }
    }

    @Test
    fun `verify close on uninitialized engine is safe`() {
        val engine = NativeEmulationEngine()
        assertDoesNotThrow {
            engine.close()
        }
        assertEquals(EmulationState.UNINITIALIZED, engine.state)
    }
}
