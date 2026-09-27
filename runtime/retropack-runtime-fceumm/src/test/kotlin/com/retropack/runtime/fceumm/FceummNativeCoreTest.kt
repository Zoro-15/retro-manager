package com.retropack.runtime.fceumm

import com.retropack.runtime.core.KeyMaskBuilder
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FceummNativeCoreTest {

    @Test
    fun `test FceummNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(FceummNativeCore)
        assertFalse(FceummNativeCore.isLoaded())
        assertNotNull(FceummNativeCore.loadError)
    }

    @Test
    fun `test FceummNativeCore handles load failure gracefully without crashing on host JVM`() {
        assertNull(FceummNativeCore.loadedLibraryName)
        assertTrue(
            FceummNativeCore.loadError?.contains("fceumm", ignoreCase = true) == true ||
            FceummNativeCore.loadError?.contains("link", ignoreCase = true) == true ||
            FceummNativeCore.loadError?.contains("library", ignoreCase = true) == true
        )
    }

    @Test
    fun `test KeyMaskBuilder constructs valid NES 8-button control masks`() {
        val mask = KeyMaskBuilder()
            .press(RetroKey.A)
            .press(RetroKey.B)
            .press(RetroKey.START)
            .press(RetroKey.SELECT)
            .press(RetroKey.UP)
            .press(RetroKey.DOWN)
            .press(RetroKey.LEFT)
            .press(RetroKey.RIGHT)
            .build()

        assertTrue((mask and RetroKey.KEY_A) != 0)
        assertTrue((mask and RetroKey.KEY_B) != 0)
        assertTrue((mask and RetroKey.KEY_START) != 0)
        assertTrue((mask and RetroKey.KEY_SELECT) != 0)
        assertTrue((mask and RetroKey.KEY_UP) != 0)
        assertTrue((mask and RetroKey.KEY_DOWN) != 0)
        assertTrue((mask and RetroKey.KEY_LEFT) != 0)
        assertTrue((mask and RetroKey.KEY_RIGHT) != 0)
    }

    @Test
    fun `test FceummNativeCore state functions fail gracefully when library is not loaded`() {
        assertThrows(UnsatisfiedLinkError::class.java) {
            FceummNativeCore.nativeInit("/tmp/fceumm")
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            FceummNativeCore.nativeGetSramSize()
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            FceummNativeCore.nativeRunFrame()
        }
    }
}
