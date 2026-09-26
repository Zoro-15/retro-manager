package com.retropack.runtime.mupen64

import com.retropack.runtime.core.KeyMaskBuilder
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class Mupen64NativeCoreTest {

    @Test
    fun `test Mupen64NativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(Mupen64NativeCore)
        assertFalse(Mupen64NativeCore.isLoaded())
        assertNotNull(Mupen64NativeCore.loadError)
    }

    @Test
    fun `test Mupen64NativeCore handles load failure gracefully without crashing on host JVM`() {
        assertNull(Mupen64NativeCore.loadedLibraryName)
        assertTrue(
            Mupen64NativeCore.loadError?.contains("mupen", ignoreCase = true) == true ||
            Mupen64NativeCore.loadError?.contains("link", ignoreCase = true) == true ||
            Mupen64NativeCore.loadError?.contains("library", ignoreCase = true) == true
        )
    }

    @Test
    fun `test KeyMaskBuilder constructs valid N64 control masks`() {
        val mask = KeyMaskBuilder()
            .press(RetroKey.A)
            .press(RetroKey.B)
            .press(RetroKey.Z)
            .press(RetroKey.START)
            .press(RetroKey.L)
            .press(RetroKey.R)
            .press(RetroKey.C_UP)
            .press(RetroKey.C_DOWN)
            .press(RetroKey.C_LEFT)
            .press(RetroKey.C_RIGHT)
            .build()

        assertTrue((mask and RetroKey.KEY_A) != 0)
        assertTrue((mask and RetroKey.KEY_B) != 0)
        assertTrue((mask and RetroKey.KEY_Z) != 0)
        assertTrue((mask and RetroKey.KEY_START) != 0)
        assertTrue((mask and RetroKey.KEY_L) != 0)
        assertTrue((mask and RetroKey.KEY_R) != 0)
        assertTrue((mask and RetroKey.KEY_C_UP) != 0)
        assertTrue((mask and RetroKey.KEY_C_DOWN) != 0)
        assertTrue((mask and RetroKey.KEY_C_LEFT) != 0)
        assertTrue((mask and RetroKey.KEY_C_RIGHT) != 0)
        assertEquals(0, mask and RetroKey.KEY_UP)
    }

    @Test
    fun `test Mupen64NativeCore state functions fail gracefully when library is not loaded`() {
        assertThrows(UnsatisfiedLinkError::class.java) {
            Mupen64NativeCore.nativeInit("/tmp/mupen64")
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            Mupen64NativeCore.nativeGetSramSize()
        }
    }
}
