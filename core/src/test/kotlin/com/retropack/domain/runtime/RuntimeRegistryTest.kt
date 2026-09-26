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
    fun `discovers canonical runtimes for all 10 retro console systems`() {
        // GBA / GB / GBC
        assertEquals(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("gba")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("gbc")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("gb")?.id)

        // SNES / Super Famicom
        assertEquals(RuntimeRegistry.RUNTIME_SNES9X_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("snes")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_SNES9X_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("sfc")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_SNES9X_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("smc")?.id)

        // Genesis / Mega Drive / SMS / GG
        assertEquals(RuntimeRegistry.RUNTIME_GENESIS_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("genesis")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_GENESIS_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("md")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_GENESIS_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("smd")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_GENESIS_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("sms")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_GENESIS_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("gg")?.id)

        // NES / Famicom
        assertEquals(RuntimeRegistry.RUNTIME_FCEUMM_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("nes")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_FCEUMM_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("fds")?.id)

        // PC Engine / TG-16
        assertEquals(RuntimeRegistry.RUNTIME_PCE_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("pce")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_PCE_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("tg16")?.id)

        // Arcade / Neo Geo / CPS
        assertEquals(RuntimeRegistry.RUNTIME_FBNEO_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("arcade")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_FBNEO_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("neogeo")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_FBNEO_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("cps1")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_FBNEO_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("cps2")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_FBNEO_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("cps3")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_FBNEO_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("fbneo")?.id)

        // PS1
        assertEquals(RuntimeRegistry.RUNTIME_PCSX_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("psx")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_PCSX_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("ps1")?.id)

        // N64
        assertEquals(RuntimeRegistry.RUNTIME_MUPEN64_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("n64")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_MUPEN64_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("z64")?.id)

        // PSP
        assertEquals(RuntimeRegistry.RUNTIME_PPSSPP_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("psp")?.id)

        // NDS
        assertEquals(RuntimeRegistry.RUNTIME_MELONDS_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("nds")?.id)
        assertEquals(RuntimeRegistry.RUNTIME_MELONDS_UNIFIED, RuntimeRegistry.findRuntimeForPlatform("dsi")?.id)
    }

    @Test
    fun `registers and retrieves all 10 canonical descriptors by ID`() {
        assertNotNull(RuntimeRegistry.getDescriptor(RuntimeRegistry.RUNTIME_MGBA_UNIFIED))
        assertNotNull(RuntimeRegistry.getDescriptor(RuntimeRegistry.RUNTIME_SNES9X_UNIFIED))
        assertNotNull(RuntimeRegistry.getDescriptor(RuntimeRegistry.RUNTIME_GENESIS_UNIFIED))
        assertNotNull(RuntimeRegistry.getDescriptor(RuntimeRegistry.RUNTIME_FCEUMM_UNIFIED))
        assertNotNull(RuntimeRegistry.getDescriptor(RuntimeRegistry.RUNTIME_PCE_UNIFIED))
        assertNotNull(RuntimeRegistry.getDescriptor(RuntimeRegistry.RUNTIME_FBNEO_UNIFIED))
        assertNotNull(RuntimeRegistry.getDescriptor(RuntimeRegistry.RUNTIME_PCSX_UNIFIED))
        assertNotNull(RuntimeRegistry.getDescriptor(RuntimeRegistry.RUNTIME_MUPEN64_UNIFIED))
        assertNotNull(RuntimeRegistry.getDescriptor(RuntimeRegistry.RUNTIME_PPSSPP_UNIFIED))
        assertNotNull(RuntimeRegistry.getDescriptor(RuntimeRegistry.RUNTIME_MELONDS_UNIFIED))
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
