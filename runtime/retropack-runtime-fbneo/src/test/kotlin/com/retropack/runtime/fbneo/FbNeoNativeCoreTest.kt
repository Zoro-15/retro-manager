package com.retropack.runtime.fbneo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FbNeoNativeCoreTest {

    @Test
    fun `test FbNeoNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(FbNeoNativeCore)
        assertFalse(FbNeoNativeCore.isLoaded())
        assertNotNull(FbNeoNativeCore.loadError)
    }
}
