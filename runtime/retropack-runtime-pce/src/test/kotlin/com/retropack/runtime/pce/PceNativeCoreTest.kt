package com.retropack.runtime.pce

import com.retropack.runtime.core.KeyMaskBuilder
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PceNativeCoreTest {

    @Test
    fun `test PceNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(PceNativeCore)
        assertFalse(PceNativeCore.isLoaded())
        assertNotNull(PceNativeCore.loadError)
    }

    @Test
    fun `test PceNativeCore handles load failure gracefully without crashing on host JVM`() {
        assertNull(PceNativeCore.loadedLibraryName)
        assertTrue(
            PceNativeCore.loadError?.contains("pce", ignoreCase = true) == true ||
            PceNativeCore.loadError?.contains("link", ignoreCase = true) == true ||
            PceNativeCore.loadError?.contains("library", ignoreCase = true) == true
        )
    }

    @Test
    fun `test KeyMaskBuilder constructs valid PCE control masks`() {
        val mask = KeyMaskBuilder()
            .press(RetroKey.A) // Button I
            .press(RetroKey.B) // Button II
            .press(RetroKey.START) // RUN
            .press(RetroKey.SELECT) // SELECT
            .press(RetroKey.X) // Turbo I / Button III
            .press(RetroKey.Y) // Turbo II / Button IV
            .build()

        assertTrue((mask and RetroKey.KEY_A) != 0)
        assertTrue((mask and RetroKey.KEY_B) != 0)
        assertTrue((mask and RetroKey.KEY_START) != 0)
        assertTrue((mask and RetroKey.KEY_SELECT) != 0)
        assertTrue((mask and RetroKey.KEY_X) != 0)
        assertTrue((mask and RetroKey.KEY_Y) != 0)
        assertEquals(0, mask and RetroKey.KEY_UP)
    }

    @Test
    fun `test PceNativeCore state functions fail gracefully when library is not loaded`() {
        assertThrows(UnsatisfiedLinkError::class.java) {
            PceNativeCore.nativeInit("/tmp/pce")
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            PceNativeCore.nativeGetSramSize()
        }
    }
}
