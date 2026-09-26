package com.retropack.runtime.pce

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PceNativeCoreTest {

    @Test
    fun `test PceNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(PceNativeCore)
        assertFalse(PceNativeCore.isLoaded())
        assertNotNull(PceNativeCore.loadError)
    }
}
