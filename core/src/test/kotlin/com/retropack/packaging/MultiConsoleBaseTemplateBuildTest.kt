package com.retropack.packaging

import com.retropack.domain.model.*
import com.retropack.domain.rom.GbaTestRomFactory
import com.retropack.domain.runtime.RuntimeRegistry
import com.retropack.security.HybridKeystore
import com.retropack.security.KeyType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * End-to-end multi-console build test verifying that the 5 precompiled
 * pure C++ NativeActivity base templates transform cleanly through the 15-step pipeline.
 */
class MultiConsoleBaseTemplateBuildTest {

    @field:TempDir
    lateinit var tempDir: File

    private val runtimesDir: File
        get() = sequenceOf(
            File("runtimes"),
            File("../runtimes")
        ).firstOrNull { it.isDirectory }
            ?: throw AssertionError("runtimes/ directory missing")

    @BeforeEach
    fun setUp() {
        RuntimeRegistry.resetToDefaults()
        // Load all 5 runtime templates
        listOf(
            "mgba-unified",
            "snes9x-unified",
            "genesis-unified",
            "fceumm-unified",
            "pce-unified"
        ).forEach { id ->
            val dir = File(runtimesDir, id)
            if (dir.isDirectory && File(dir, "template.apk").exists()) {
                RuntimeRegistry.loadFromDirectory(dir)
            }
        }
    }

    @Test
    fun `test build standalone GBA game using mgba-unified base template`() {
        val outputDir = File(tempDir, "output_gba")
        val signingIdentity = HybridKeystore.generateIdentity("gba_signer", KeyType.RSA_2048)
        val romBytes = GbaTestRomFactory.create()

        val request = BuildRequest(
            identity = GameIdentity(
                gameId = "emerald_01",
                gameTitle = "Pokemon Emerald",
                packageName = "com.retropack.game.emerald"
            ),
            content = ContentPayload(
                sourceRom = "emerald.gba",
                platform = "gba"
            ),
            runtime = RuntimeConfigPayload(
                templateId = RuntimeRegistry.RUNTIME_MGBA_UNIFIED,
                core = "mgba"
            )
        )

        val result = BuildEngine.build(
            request = request,
            romBytes = romBytes,
            signingIdentity = signingIdentity,
            outputDir = outputDir
        )

        assertTrue(result.success, "GBA build must succeed: ${result.errorMessage}")
        assertNotNull(result.artifactFile)
        assertTrue(result.artifactFile!!.exists())
        assertEquals(15, result.stageProvenance.size)
        assertTrue(result.stageProvenance.all { it.passed })
    }

    @Test
    fun `test build standalone NES game using fceumm-unified base template`() {
        val outputDir = File(tempDir, "output_nes")
        val signingIdentity = HybridKeystore.generateIdentity("nes_signer", KeyType.EC_P256)
        // Minimal NES header: 'NES\x1a' + 16 bytes header + 16KB PRG
        val nesBytes = ByteArray(16 + 16384).apply {
            this[0] = 'N'.code.toByte()
            this[1] = 'E'.code.toByte()
            this[2] = 'S'.code.toByte()
            this[3] = 0x1A
            this[4] = 1 // 1x 16KB PRG
            this[5] = 1 // 1x 8KB CHR
        }

        val request = BuildRequest(
            identity = GameIdentity(
                gameId = "mario_nes",
                gameTitle = "Super Mario Bros",
                packageName = "com.retropack.game.mario_nes"
            ),
            content = ContentPayload(
                sourceRom = "mario.nes",
                platform = "nes"
            ),
            runtime = RuntimeConfigPayload(
                templateId = RuntimeRegistry.RUNTIME_FCEUMM_UNIFIED,
                core = "fceumm"
            )
        )

        val result = BuildEngine.build(
            request = request,
            romBytes = nesBytes,
            signingIdentity = signingIdentity,
            outputDir = outputDir
        )

        assertTrue(result.success, "NES build must succeed: ${result.errorMessage}")
        assertNotNull(result.artifactFile)
        assertTrue(result.artifactFile!!.exists())
        assertEquals(15, result.stageProvenance.size)
        assertTrue(result.stageProvenance.all { it.passed })
    }

    @Test
    fun `test build standalone SNES game using snes9x-unified base template`() {
        val outputDir = File(tempDir, "output_snes")
        val signingIdentity = HybridKeystore.generateIdentity("snes_signer", KeyType.RSA_2048)
        val snesBytes = ByteArray(32768) // Minimal 32KB buffer

        val request = BuildRequest(
            identity = GameIdentity(
                gameId = "smw_snes",
                gameTitle = "Super Mario World",
                packageName = "com.retropack.game.smw_snes"
            ),
            content = ContentPayload(
                sourceRom = "smw.sfc",
                platform = "snes"
            ),
            runtime = RuntimeConfigPayload(
                templateId = RuntimeRegistry.RUNTIME_SNES9X_UNIFIED,
                core = "snes9x"
            )
        )

        val result = BuildEngine.build(
            request = request,
            romBytes = snesBytes,
            signingIdentity = signingIdentity,
            outputDir = outputDir
        )

        assertTrue(result.success, "SNES build must succeed: ${result.errorMessage}")
        assertNotNull(result.artifactFile)
        assertTrue(result.artifactFile!!.exists())
        assertEquals(15, result.stageProvenance.size)
        assertTrue(result.stageProvenance.all { it.passed })
    }

    @Test
    fun `test build standalone Genesis game using genesis-unified base template`() {
        val outputDir = File(tempDir, "output_gen")
        val signingIdentity = HybridKeystore.generateIdentity("gen_signer", KeyType.RSA_2048)
        val genBytes = ByteArray(32768).apply {
            // SEGA GENESIS string at 0x100
            val sega = "SEGA GENESIS".toByteArray(Charsets.US_ASCII)
            System.arraycopy(sega, 0, this, 0x100, sega.size)
        }

        val request = BuildRequest(
            identity = GameIdentity(
                gameId = "sonic_gen",
                gameTitle = "Sonic The Hedgehog",
                packageName = "com.retropack.game.sonic_gen"
            ),
            content = ContentPayload(
                sourceRom = "sonic.md",
                platform = "genesis"
            ),
            runtime = RuntimeConfigPayload(
                templateId = RuntimeRegistry.RUNTIME_GENESIS_UNIFIED,
                core = "genesis_plus_gx"
            )
        )

        val result = BuildEngine.build(
            request = request,
            romBytes = genBytes,
            signingIdentity = signingIdentity,
            outputDir = outputDir
        )

        assertTrue(result.success, "Genesis build must succeed: ${result.errorMessage}")
        assertNotNull(result.artifactFile)
        assertTrue(result.artifactFile!!.exists())
        assertEquals(15, result.stageProvenance.size)
        assertTrue(result.stageProvenance.all { it.passed })
    }

    @Test
    fun `test build standalone PC Engine game using pce-unified base template`() {
        val outputDir = File(tempDir, "output_pce")
        val signingIdentity = HybridKeystore.generateIdentity("pce_signer", KeyType.EC_P256)
        val pceBytes = ByteArray(32768)

        val request = BuildRequest(
            identity = GameIdentity(
                gameId = "bonk_pce",
                gameTitle = "Bonk Adventure",
                packageName = "com.retropack.game.bonk_pce"
            ),
            content = ContentPayload(
                sourceRom = "bonk.pce",
                platform = "pce"
            ),
            runtime = RuntimeConfigPayload(
                templateId = RuntimeRegistry.RUNTIME_PCE_UNIFIED,
                core = "mednafen_pce_fast"
            )
        )

        val result = BuildEngine.build(
            request = request,
            romBytes = pceBytes,
            signingIdentity = signingIdentity,
            outputDir = outputDir
        )

        assertTrue(result.success, "PC Engine build must succeed: ${result.errorMessage}")
        assertNotNull(result.artifactFile)
        assertTrue(result.artifactFile!!.exists())
        assertEquals(15, result.stageProvenance.size)
        assertTrue(result.stageProvenance.all { it.passed })
    }
}
