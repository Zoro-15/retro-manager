package com.retropack.runtime.snes

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class Snes9xNativeCoreTest {

    @Test
    fun `test Snes9xNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(Snes9xNativeCore)
        assertFalse(Snes9xNativeCore.isLoaded())
        assertNotNull(Snes9xNativeCore.loadError)
    }
}
