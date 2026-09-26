package com.retropack.runtime.pcsx

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PcsxNativeCoreTest {

    @Test
    fun `test PcsxNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(PcsxNativeCore)
        assertFalse(PcsxNativeCore.isLoaded())
        assertNotNull(PcsxNativeCore.loadError)
    }
}
