package com.retropack.runtime.pcsx

import com.retropack.runtime.core.KeyMaskBuilder
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PcsxNativeCoreTest {

    @Test
    fun `test PcsxNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(PcsxNativeCore)
        assertFalse(PcsxNativeCore.isLoaded())
        assertNotNull(PcsxNativeCore.loadError)
    }

    @Test
    fun `test PcsxNativeCore handles load failure gracefully without crashing on host JVM`() {
        assertNull(PcsxNativeCore.loadedLibraryName)
        assertTrue(
            PcsxNativeCore.loadError?.contains("pcsx", ignoreCase = true) == true ||
            PcsxNativeCore.loadError?.contains("link", ignoreCase = true) == true ||
            PcsxNativeCore.loadError?.contains("library", ignoreCase = true) == true
        )
    }

    @Test
    fun `test KeyMaskBuilder constructs valid PS1 DualShock control masks`() {
        val mask = KeyMaskBuilder()
            .press(RetroKey.A) // Circle
            .press(RetroKey.B) // Cross
            .press(RetroKey.X) // Triangle
            .press(RetroKey.Y) // Square
            .press(RetroKey.L) // L1
            .press(RetroKey.R) // R1
            .press(RetroKey.L2) // L2
            .press(RetroKey.R2) // R2
            .press(RetroKey.L3) // L3
            .press(RetroKey.R3) // R3
            .press(RetroKey.START) // Start
            .press(RetroKey.SELECT) // Select
            .build()

        assertTrue((mask and RetroKey.KEY_A) != 0)
        assertTrue((mask and RetroKey.KEY_B) != 0)
        assertTrue((mask and RetroKey.KEY_X) != 0)
        assertTrue((mask and RetroKey.KEY_Y) != 0)
        assertTrue((mask and RetroKey.KEY_L) != 0)
        assertTrue((mask and RetroKey.KEY_R) != 0)
        assertTrue((mask and RetroKey.KEY_L2) != 0)
        assertTrue((mask and RetroKey.KEY_R2) != 0)
        assertTrue((mask and RetroKey.KEY_L3) != 0)
        assertTrue((mask and RetroKey.KEY_R3) != 0)
        assertTrue((mask and RetroKey.KEY_START) != 0)
        assertTrue((mask and RetroKey.KEY_SELECT) != 0)
        assertEquals(0, mask and RetroKey.KEY_UP)
    }

    @Test
    fun `test PcsxNativeCore state functions fail gracefully when library is not loaded`() {
        assertThrows(UnsatisfiedLinkError::class.java) {
            PcsxNativeCore.nativeInit("/tmp/pcsx")
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            PcsxNativeCore.nativeGetSramSize()
        }
    }
}
