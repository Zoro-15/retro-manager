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

    @Test
    fun `test FceummNativeCore handles load failure gracefully without crashing on host JVM`() {
        assertNull(FceummNativeCore.loadedLibraryName)
        assertTrue(
            FceummNativeCore.loadError?.contains("fceumm", ignoreCase = true) == true ||
            FceummNativeCore.loadError?.contains("link", ignoreCase = true) == true ||
            FceummNativeCore.loadError?.contains("library", ignoreCase = true) == true
        )
    }
}

