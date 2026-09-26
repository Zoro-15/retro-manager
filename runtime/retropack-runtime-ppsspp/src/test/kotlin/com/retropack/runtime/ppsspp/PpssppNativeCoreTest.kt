package com.retropack.runtime.ppsspp

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class PpssppNativeCoreTest {
    @Test
    fun testCoreStructure() {
        assertNotNull(PpssppNativeCore)
    }
}
