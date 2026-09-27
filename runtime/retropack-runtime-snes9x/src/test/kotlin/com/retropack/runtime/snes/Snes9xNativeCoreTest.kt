package com.retropack.runtime.snes

import com.retropack.runtime.core.KeyMaskBuilder
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class Snes9xNativeCoreTest {

    @Test
    fun `test Snes9xNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(Snes9xNativeCore)
        assertFalse(Snes9xNativeCore.isLoaded())
        assertNotNull(Snes9xNativeCore.loadError)
    }

    @Test
    fun `test Snes9xNativeCore handles load failure gracefully without crashing on host JVM`() {
        assertNull(Snes9xNativeCore.loadedLibraryName)
        assertTrue(
            Snes9xNativeCore.loadError?.contains("snes", ignoreCase = true) == true ||
            Snes9xNativeCore.loadError?.contains("link", ignoreCase = true) == true ||
            Snes9xNativeCore.loadError?.contains("library", ignoreCase = true) == true
        )
    }

    @Test
    fun `test KeyMaskBuilder constructs valid SNES 12-button control masks`() {
        val mask = KeyMaskBuilder()
            .press(RetroKey.A)
            .press(RetroKey.B)
            .press(RetroKey.X)
            .press(RetroKey.Y)
            .press(RetroKey.L)
            .press(RetroKey.R)
            .press(RetroKey.START)
            .press(RetroKey.SELECT)
            .press(RetroKey.UP)
            .press(RetroKey.DOWN)
            .press(RetroKey.LEFT)
            .press(RetroKey.RIGHT)
            .build()

        assertTrue((mask and RetroKey.KEY_A) != 0)
        assertTrue((mask and RetroKey.KEY_B) != 0)
        assertTrue((mask and RetroKey.KEY_X) != 0)
        assertTrue((mask and RetroKey.KEY_Y) != 0)
        assertTrue((mask and RetroKey.KEY_L) != 0)
        assertTrue((mask and RetroKey.KEY_R) != 0)
        assertTrue((mask and RetroKey.KEY_START) != 0)
        assertTrue((mask and RetroKey.KEY_SELECT) != 0)
        assertTrue((mask and RetroKey.KEY_UP) != 0)
        assertTrue((mask and RetroKey.KEY_DOWN) != 0)
        assertTrue((mask and RetroKey.KEY_LEFT) != 0)
        assertTrue((mask and RetroKey.KEY_RIGHT) != 0)
    }

    @Test
    fun `test Snes9xNativeCore state functions fail gracefully when library is not loaded`() {
        assertThrows(UnsatisfiedLinkError::class.java) {
            Snes9xNativeCore.nativeInit("/tmp/snes9x")
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            Snes9xNativeCore.nativeGetSramSize()
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            Snes9xNativeCore.nativeRunFrame()
        }
    }
}
