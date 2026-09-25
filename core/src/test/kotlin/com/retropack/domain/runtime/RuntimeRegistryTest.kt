package com.retropack.domain.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class RuntimeRegistryTest {

    @BeforeEach
    fun setUp() {
        RuntimeRegistry.resetToDefaults()
    }

    @Test
    fun `discovers mgba-unified runtime for gb gbc and gba platforms`() {
        val gbRuntime = RuntimeRegistry.findRuntimeForPlatform("gb")
        val gbcRuntime = RuntimeRegistry.findRuntimeForPlatform("gbc")
        val gbaRuntime = RuntimeRegistry.findRuntimeForPlatform("gba")

        assertNotNull(gbRuntime)
        assertNotNull(gbcRuntime)
        assertNotNull(gbaRuntime)

        assertEquals(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, gbRuntime!!.id)
        assertEquals(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, gbcRuntime!!.id)
        assertEquals(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, gbaRuntime!!.id)
    }

    @Test
    fun `isTrustedTemplate validates compiled bytecode fingerprint`() {
        val trustedHash = "52498f863e0efb3c05fd9fcf4ebaade5c0a03db4f725e5ac264150fad132f566"
        assertTrue(RuntimeRegistry.isTrustedTemplate(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, trustedHash))
        assertTrue(RuntimeRegistry.isTrustedTemplate(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, "sha256:$trustedHash"))

        val maliciousHash = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        assertFalse(RuntimeRegistry.isTrustedTemplate(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, maliciousHash))
    }

    @Test
    fun `verifyProtectedEntries validates classes dex and native library against bytecode trust anchors`() {
        val validEntries = mapOf(
            "classes.dex" to "798a89a984f3e83964c19e681336ce6dacf4d948bd4253dfb895c55b82ef243d",
            "lib/arm64-v8a/libmgba.so" to "79eca5e1ea4df26b67ea6c23839173de1ba465baf0713c3198c1d6f61a2d1bf1"
        )
        assertTrue(RuntimeRegistry.verifyProtectedEntries(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, validEntries))

        val tamperedDex = mapOf(
            "classes.dex" to "badbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadb",
            "lib/arm64-v8a/libmgba.so" to "79eca5e1ea4df26b67ea6c23839173de1ba465baf0713c3198c1d6f61a2d1bf1"
        )
        assertFalse(RuntimeRegistry.verifyProtectedEntries(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, tamperedDex))
    }

    @Test
    fun `loads runtime bundle from directory and registers template`() {
        val runtimeDir = File("runtimes/mgba-unified").takeIf { it.exists() }
            ?: File("../runtimes/mgba-unified")
        // Pending the real Phase 3 bundle (descriptor + template.apk):
        // abort, don't fail, when fixtures are absent.
        assumeTrue(
            File(runtimeDir, RuntimeDescriptor.DESCRIPTOR_FILENAME).exists() &&
                File(runtimeDir, RuntimeTemplate.TEMPLATE_APK_FILENAME).exists(),
            "runtimes/mgba-unified must contain runtime.json + template.apk"
        )
        val template = RuntimeRegistry.loadFromDirectory(runtimeDir)

        assertEquals(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, template.descriptor.id)
        assertTrue(template.templateApk.exists())
        assertEquals(template, RuntimeRegistry.getTemplate(RuntimeRegistry.RUNTIME_MGBA_UNIFIED))
    }
}
