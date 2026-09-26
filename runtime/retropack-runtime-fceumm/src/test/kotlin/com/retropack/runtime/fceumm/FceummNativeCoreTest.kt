package com.retropack.runtime.fceumm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FceummNativeCoreTest {

    @Test
    fun `test FceummNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(FceummNativeCore)
        assertFalse(FceummNativeCore.isLoaded())
        assertNotNull(FceummNativeCore.loadError)
    }
}
