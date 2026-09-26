package com.retropack.runtime.ppsspp

import com.retropack.runtime.core.KeyMaskBuilder
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PpssppNativeCoreTest {

    @Test
    fun `test PpssppNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(PpssppNativeCore)
        assertFalse(PpssppNativeCore.isLoaded())
        assertNotNull(PpssppNativeCore.loadError)
    }

    @Test
    fun `test PpssppNativeCore handles load failure gracefully without crashing on host JVM`() {
        assertNull(PpssppNativeCore.loadedLibraryName)
        assertTrue(
            PpssppNativeCore.loadError?.contains("ppsspp", ignoreCase = true) == true ||
            PpssppNativeCore.loadError?.contains("link", ignoreCase = true) == true ||
            PpssppNativeCore.loadError?.contains("library", ignoreCase = true) == true
        )
    }

    @Test
    fun `test KeyMaskBuilder constructs valid PSP control masks`() {
        val mask = KeyMaskBuilder()
            .press(RetroKey.A) // Circle
            .press(RetroKey.B) // Cross
            .press(RetroKey.X) // Triangle
            .press(RetroKey.Y) // Square
            .press(RetroKey.L) // L trigger
            .press(RetroKey.R) // R trigger
            .press(RetroKey.START)
            .press(RetroKey.SELECT)
            .build()

        assertTrue((mask and RetroKey.KEY_A) != 0)
        assertTrue((mask and RetroKey.KEY_B) != 0)
        assertTrue((mask and RetroKey.KEY_X) != 0)
        assertTrue((mask and RetroKey.KEY_Y) != 0)
        assertTrue((mask and RetroKey.KEY_L) != 0)
        assertTrue((mask and RetroKey.KEY_R) != 0)
        assertTrue((mask and RetroKey.KEY_START) != 0)
        assertTrue((mask and RetroKey.KEY_SELECT) != 0)
        assertEquals(0, mask and RetroKey.KEY_UP)
    }

    @Test
    fun `test PpssppNativeCore state functions fail gracefully when library is not loaded`() {
        assertThrows(UnsatisfiedLinkError::class.java) {
            PpssppNativeCore.nativeInit("/tmp/ppsspp")
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            PpssppNativeCore.nativeGetSramSize()
        }
    }
}
