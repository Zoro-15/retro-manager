package com.retropack.runtime.mupen64

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class Mupen64NativeCoreTest {

    @Test
    fun `test Mupen64NativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(Mupen64NativeCore)
        assertFalse(Mupen64NativeCore.isLoaded())
        assertNotNull(Mupen64NativeCore.loadError)
    }
}
