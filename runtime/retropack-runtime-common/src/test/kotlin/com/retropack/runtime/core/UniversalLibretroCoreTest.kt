package com.retropack.runtime.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UniversalLibretroCoreTest {

    @Test
    fun `verify UniversalLibretroCore implements NativeCoreBridge contract`() {
        val bridge: NativeCoreBridge = UniversalLibretroCore
        assertNotNull(bridge)
    }

    @Test
    fun `verify UniversalLibretroCore safe fallback in JVM environment`() {
        // When running in host JVM test without libretropack-host.so,
        // isLoaded returns false without throwing UnsatisfiedLinkError on class init.
        val loaded = UniversalLibretroCore.isLoaded()
        if (!loaded) {
            assertNotNull(UniversalLibretroCore.hostLoadError)
            assertNull(UniversalLibretroCore.loadedHostLibraryName)
            assertFalse(UniversalLibretroCore.loadCore("libretro_mgba.so"))
        }
    }

    @Test
    fun `verify NativeEmulationEngine compatibility with UniversalLibretroCore`() {
        val engine = NativeEmulationEngine(core = UniversalLibretroCore)
        assertEquals(EmulationState.UNINITIALIZED, engine.state)
        assertTrue(engine.state.isHalted)
        assertFalse(engine.state.isReady)

        // Initializing without native library gracefully transitions to ERROR or returns false
        if (!UniversalLibretroCore.isLoaded()) {
            val initialized = engine.initialize("/tmp/storage")
            assertFalse(initialized)
            assertEquals(EmulationState.ERROR, engine.state)
        }
    }

    @Test
    fun `verify unload and reset lifecycle safety`() {
        assertDoesNotThrow {
            UniversalLibretroCore.unloadCore()
            UniversalLibretroCore.reset()
        }
    }
}
