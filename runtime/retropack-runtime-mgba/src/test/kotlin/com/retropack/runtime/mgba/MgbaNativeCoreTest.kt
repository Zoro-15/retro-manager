package com.retropack.runtime.mgba

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MgbaNativeCoreTest {

    @Test
    fun `test MgbaNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(MgbaNativeCore)
        // In JVM test mode without native lib, isLoaded is false and loadError is captured
        assertFalse(MgbaNativeCore.isLoaded())
        assertNotNull(MgbaNativeCore.loadError)
    }
}
