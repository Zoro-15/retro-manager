package com.retropack.runtime.fbneo

import com.retropack.runtime.core.KeyMaskBuilder
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FbNeoNativeCoreTest {

    @Test
    fun `test FbNeoNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(FbNeoNativeCore)
        assertFalse(FbNeoNativeCore.isLoaded())
        assertNotNull(FbNeoNativeCore.loadError)
    }

    @Test
    fun `test FbNeoNativeCore handles load failure gracefully without crashing on host JVM`() {
        assertNull(FbNeoNativeCore.loadedLibraryName)
        assertTrue(
            FbNeoNativeCore.loadError?.contains("fbneo", ignoreCase = true) == true ||
            FbNeoNativeCore.loadError?.contains("link", ignoreCase = true) == true ||
            FbNeoNativeCore.loadError?.contains("library", ignoreCase = true) == true
        )
    }

    @Test
    fun `test KeyMaskBuilder constructs valid Arcade and Neo Geo control masks`() {
        val mask = KeyMaskBuilder()
            .press(RetroKey.A) // Neo Geo A
            .press(RetroKey.B) // Neo Geo B
            .press(RetroKey.X) // Neo Geo C
            .press(RetroKey.Y) // Neo Geo D
            .press(RetroKey.START) // Start 1
            .press(RetroKey.SELECT) // Coin 1
            .build()

        assertTrue((mask and RetroKey.KEY_A) != 0)
        assertTrue((mask and RetroKey.KEY_B) != 0)
        assertTrue((mask and RetroKey.KEY_X) != 0)
        assertTrue((mask and RetroKey.KEY_Y) != 0)
        assertTrue((mask and RetroKey.KEY_START) != 0)
        assertTrue((mask and RetroKey.KEY_SELECT) != 0)
        assertEquals(0, mask and RetroKey.KEY_UP)
    }

    @Test
    fun `test FbNeoNativeCore state functions fail gracefully when library is not loaded`() {
        assertThrows(UnsatisfiedLinkError::class.java) {
            FbNeoNativeCore.nativeInit("/tmp/fbneo")
        }
        assertThrows(UnsatisfiedLinkError::class.java) {
            FbNeoNativeCore.nativeGetSramSize()
        }
    }
}
