package com.retropack.runtime.genesis

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GenesisNativeCoreTest {

    @Test
    fun `test GenesisNativeCore satisfies NativeCoreBridge interface contract`() {
        assertNotNull(GenesisNativeCore)
        assertFalse(GenesisNativeCore.isLoaded())
        assertNotNull(GenesisNativeCore.loadError)
    }
}
