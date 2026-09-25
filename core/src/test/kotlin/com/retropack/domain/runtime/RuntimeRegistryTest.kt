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
        // Hardcoded (NOT read from RuntimeRegistry) so an accidental anchor
        // rotation is caught here; RuntimeBundleIntegrityTest separately pins
        // the anchor to the committed template.apk bytes.
        val trustedHash = "391b8bc1cb323f4a4d07784af3778cebe1ecd366f702fb0605bb63a1b6d9d8e0"
        assertTrue(RuntimeRegistry.isTrustedTemplate(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, trustedHash))
        assertTrue(RuntimeRegistry.isTrustedTemplate(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, "sha256:$trustedHash"))

        val maliciousHash = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        assertFalse(RuntimeRegistry.isTrustedTemplate(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, maliciousHash))
    }

    @Test
    fun `verifyProtectedEntries validates classes dex and native library against bytecode trust anchors`() {
        val validEntries = mapOf(
            "classes.dex" to "b2533f8585723081e9d2bda0038eb8b0d550a7dbc9c4a52a0a66a2bf3901010f",
            "lib/arm64-v8a/libretropack-runtime.so" to "2253df2006ed765a84492382325b09e2ee2dfad72e943ab9d50fa3a31f09754b"
        )
        assertTrue(RuntimeRegistry.verifyProtectedEntries(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, validEntries))

        val tamperedDex = mapOf(
            "classes.dex" to "badbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadb",
            "lib/arm64-v8a/libretropack-runtime.so" to "2253df2006ed765a84492382325b09e2ee2dfad72e943ab9d50fa3a31f09754b"
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
