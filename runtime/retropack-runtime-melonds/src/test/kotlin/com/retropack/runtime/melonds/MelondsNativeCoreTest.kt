package com.retropack.runtime.melonds

import com.retropack.runtime.core.KeyMaskBuilder
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MelondsNativeCoreTest {

    @Test
    fun `test MelondsNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(MelondsNativeCore)
        assertFalse(MelondsNativeCore.isLoaded())
        assertNotNull(MelondsNativeCore.loadError)
    }

    @Test
    fun `test MelondsNativeCore handles load failure gracefully without crashing on host JVM`() {
        assertNull(MelondsNativeCore.loadedLibraryName)
        assertTrue(
            MelondsNativeCore.loadError?.contains("melonds", ignoreCase = true) == true ||
            MelondsNativeCore.loadError?.contains("link", ignoreCase = true) == true ||
            MelondsNativeCore.loadError?.contains("library", ignoreCase = true) == true
        )
    }

    @Test
    fun `test KeyMaskBuilder constructs valid Nintendo DS control masks`() {
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
        assertTrue((mask and RetroKey.KEY_RIGHT) != 0)
        assertEquals(0, mask and RetroKey.KEY_DOWN)
        assertEquals(0, mask and RetroKey.KEY_LEFT)
    }

    @Test
    fun `test MelondsNativeCore state functions fail gracefully when library is not loaded`() {
        assertThrows(UnsatisfiedLinkError::class.java) {
            MelondsNativeCore.nativeInit("/tmp/melonds")
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            MelondsNativeCore.nativeGetSramSize()
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            MelondsNativeCore.nativeRunFrame()
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            MelondsNativeCore.nativeSetTouch(128, 96, true)
        }
    }
}
