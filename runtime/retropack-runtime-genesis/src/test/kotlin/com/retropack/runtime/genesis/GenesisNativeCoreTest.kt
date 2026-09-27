package com.retropack.runtime.genesis

import com.retropack.runtime.core.KeyMaskBuilder
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GenesisNativeCoreTest {

    @Test
    fun `test GenesisNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(GenesisNativeCore)
        assertFalse(GenesisNativeCore.isLoaded())
        assertNotNull(GenesisNativeCore.loadError)
    }

    @Test
    fun `test GenesisNativeCore handles load failure gracefully without crashing on host JVM`() {
        assertNull(GenesisNativeCore.loadedLibraryName)
        assertTrue(
            GenesisNativeCore.loadError?.contains("genesis", ignoreCase = true) == true ||
            GenesisNativeCore.loadError?.contains("link", ignoreCase = true) == true ||
            GenesisNativeCore.loadError?.contains("library", ignoreCase = true) == true
        )
    }

    @Test
    fun `test KeyMaskBuilder constructs valid Sega Genesis 6-button arcade masks`() {
        val mask = KeyMaskBuilder()
            .press(RetroKey.A)
            .press(RetroKey.B)
            .press(RetroKey.C)
            .press(RetroKey.X)
            .press(RetroKey.Y)
            .press(RetroKey.Z)
            .press(RetroKey.START)
            .press(RetroKey.MODE)
            .press(RetroKey.UP)
            .press(RetroKey.DOWN)
            .press(RetroKey.LEFT)
            .press(RetroKey.RIGHT)
            .build()

        assertTrue((mask and RetroKey.KEY_A) != 0)
        assertTrue((mask and RetroKey.KEY_B) != 0)
        assertTrue((mask and RetroKey.KEY_C) != 0)
        assertTrue((mask and RetroKey.KEY_X) != 0)
        assertTrue((mask and RetroKey.KEY_Y) != 0)
        assertTrue((mask and RetroKey.KEY_Z) != 0)
        assertTrue((mask and RetroKey.KEY_START) != 0)
        assertTrue((mask and RetroKey.KEY_MODE) != 0)
        assertTrue((mask and RetroKey.KEY_UP) != 0)
        assertTrue((mask and RetroKey.KEY_DOWN) != 0)
        assertTrue((mask and RetroKey.KEY_LEFT) != 0)
        assertTrue((mask and RetroKey.KEY_RIGHT) != 0)
    }

    @Test
    fun `test GenesisNativeCore state functions fail gracefully when library is not loaded`() {
        assertThrows(UnsatisfiedLinkError::class.java) {
            GenesisNativeCore.nativeInit("/tmp/genesis")
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            GenesisNativeCore.nativeGetSramSize()
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            GenesisNativeCore.nativeRunFrame()
        }
    }
}
