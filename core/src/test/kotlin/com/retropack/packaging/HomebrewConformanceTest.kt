package com.retropack.packaging

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.retropack.domain.model.*
import com.retropack.domain.rom.GbRomParser
import com.retropack.domain.rom.GbTestRomFactory
import com.retropack.domain.rom.GbaRomParser
import com.retropack.domain.rom.GbaTestRomFactory
import com.retropack.domain.rom.RomParser
import com.retropack.domain.rom.StreamChecksum
import com.retropack.security.HybridKeystore
import com.retropack.security.KeyType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Phase 6: Homebrew Conformance & Hardware Invariant Test Suite.
 * Validates canonical homebrew fixtures for Game Boy (.gb), Game Boy Color (.gbc),
 * and Game Boy Advance (.gba) through the complete 15-step transformation pipeline.
 */
class HomebrewConformanceTest {

    @field:TempDir
    lateinit var tempDir: File

    private fun createSampleAxml(
        packageName: String = "com.retropack.template",
        activityName: String = "com.retropack.runtime.GameActivity",
        extractNativeLibs: Boolean = false
    ): ByteArray {
        val block = AndroidManifestBlock()
        block.packageName = packageName
        block.versionCode = 1
        block.versionName = "1.0"
        block.setApplicationLabel("RetroPack Template")

        val app = block.getOrCreateApplicationElement()
        val extractAttr = app.getOrCreateAndroidAttribute("extractNativeLibs", 0x010104ea)
        extractAttr.setValueAsBoolean(extractNativeLibs)

        val activity = app.createChildElement("activity")
        val nameAttr = activity.getOrCreateAndroidAttribute("name", 0x01010003)
        nameAttr.valueAsString = activityName

        block.refresh()
        val out = ByteArrayOutputStream()
        block.writeBytes(out)
        return out.toByteArray()
    }

    private fun createMockTemplateApk(file: File): File {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            // AndroidManifest.xml
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(createSampleAxml())
            zos.closeEntry()

            // classes.dex
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write("classes-dex-conformance-payload".toByteArray())
            zos.closeEntry()

            // lib/arm64-v8a/libmgba.so
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libmgba.so"))
            zos.write("libmgba-so-binary-arm64-conformance".toByteArray())
            zos.closeEntry()
        }
        return file
    }

    @Test
    fun `conformance test 1 - Game Boy Tobu Tobu Girl Deluxe`() {
        val romBytes = GbTestRomFactory.create(
            title = "TOBUTOBU GIRL",
            isCgb = false,
            cartridgeType = 0x03, // MBC1+RAM+BATTERY
            romSizeCode = 0x01,   // 64 KB
            ramSizeCode = 0x02    // 8 KB
        )

        val identity = RomParser.parse(romBytes)
        assertEquals("gb", identity.platform)
        assertEquals("TOBUTOBU GIRL", identity.gameTitle)
        assertTrue(identity.hasBattery)
        assertTrue(identity.headerChecksumValid)
        assertTrue(identity.logoOrFixedValid)

        val templateFile = createMockTemplateApk(File(tempDir, "template_gb.apk"))
        val outputDir = File(tempDir, "out_gb")
        val signingIdentity = HybridKeystore.generateIdentity("tobu_signer", KeyType.RSA_2048)

        val request = BuildRequest(
            identity = GameIdentity(
                gameId = "tobu-01",
                gameTitle = "Tobu Tobu Girl Deluxe",
                packageName = identity.derivePackageName(),
                versionCode = 1,
                versionName = "1.0.0"
            ),
            content = identity.toContentPayload("tobu_tobu_girl.gb"),
            runtime = RuntimeConfigPayload(templateId = "mgba-unified"),
            controls = ControlsPayload(),
            storage = StoragePayload(saveType = "battery_sram")
        )

        val result = BuildEngine.build(
            request = request,
            romBytes = romBytes,
            signingIdentity = signingIdentity,
            outputDir = outputDir,
            templateOverride = templateFile
        )

        assertTrue(result.success)
        assertNotNull(result.artifactFile)
        assertTrue(result.artifactFile!!.exists())
        assertEquals(15, result.stageProvenance.size)
        assertTrue(result.stageProvenance.all { it.passed })
        assertTrue(result.packageName.startsWith("com.retropack.game.tobutobugirldelu_") || result.packageName.startsWith("com.retropack.game.tobutobugirl"))
    }

    @Test
    fun `conformance test 2 - Game Boy Color Dangan GB`() {
        val romBytes = GbTestRomFactory.create(
            title = "DANGAN GB",
            isCgb = true,
            cgbFlag = 0xC0,        // CGB only
            cartridgeType = 0x1B,  // MBC5+RAM+BATTERY
            romSizeCode = 0x02,    // 128 KB
            ramSizeCode = 0x02     // 8 KB
        )

        val identity = RomParser.parse(romBytes)
        assertEquals("gbc", identity.platform)
        assertEquals("DANGAN GB", identity.gameTitle)
        assertTrue(identity.hasBattery)
        assertTrue(identity.headerChecksumValid)

        val templateFile = createMockTemplateApk(File(tempDir, "template_gbc.apk"))
        val outputDir = File(tempDir, "out_gbc")
        val signingIdentity = HybridKeystore.generateIdentity("dangan_signer", KeyType.EC_P256)

        val request = BuildRequest(
            identity = GameIdentity(
                gameId = "dangan-01",
                gameTitle = "Dangan GB",
                packageName = identity.derivePackageName(),
                versionCode = 1,
                versionName = "1.0.0"
            ),
            content = identity.toContentPayload("dangan.gbc"),
            runtime = RuntimeConfigPayload(templateId = "mgba-unified"),
            controls = ControlsPayload(),
            storage = StoragePayload(saveType = "battery_sram")
        )

        val result = BuildEngine.build(
            request = request,
            romBytes = romBytes,
            signingIdentity = signingIdentity,
            outputDir = outputDir,
            templateOverride = templateFile
        )

        assertTrue(result.success)
        assertNotNull(result.artifactFile)
        assertTrue(result.artifactFile!!.exists())
        assertEquals(15, result.stageProvenance.size)
        assertTrue(result.stageProvenance.all { it.passed })
        assertTrue(result.packageName.startsWith("com.retropack.game.dangangb_"))
    }

    @Test
    fun `conformance test 3 - Game Boy Advance Anguna Warriors of the Demis`() {
        val romBytes = GbaTestRomFactory.create(
            title = "ANGUNA DEMI",
            gameCode = "AGNA",
            makerCode = "01",
            version = 0
        )

        val identity = RomParser.parse(romBytes)
        assertEquals("gba", identity.platform)
        assertEquals("ANGUNA DEMI", identity.gameTitle)
        assertEquals("AGNA", identity.gameCode)
        assertTrue(identity.headerChecksumValid)
        assertTrue(identity.logoOrFixedValid)

        val templateFile = createMockTemplateApk(File(tempDir, "template_gba.apk"))
        val outputDir = File(tempDir, "out_gba")
        val signingIdentity = HybridKeystore.generateIdentity("anguna_signer", KeyType.RSA_2048)

        val request = BuildRequest(
            identity = GameIdentity(
                gameId = "anguna-01",
                gameTitle = "Anguna: Warriors of the Demis",
                packageName = identity.derivePackageName(),
                versionCode = 1,
                versionName = "1.0.0"
            ),
            content = identity.toContentPayload("anguna.gba"),
            runtime = RuntimeConfigPayload(templateId = "mgba-unified"),
            controls = ControlsPayload(),
            storage = StoragePayload(saveType = "flash_auto")
        )

        val result = BuildEngine.build(
            request = request,
            romBytes = romBytes,
            signingIdentity = signingIdentity,
            outputDir = outputDir,
            templateOverride = templateFile
        )

        assertTrue(result.success)
        assertNotNull(result.artifactFile)
        assertTrue(result.artifactFile!!.exists())
        assertEquals(15, result.stageProvenance.size)
        assertTrue(result.stageProvenance.all { it.passed })
        assertTrue(result.packageName.startsWith("com.retropack.game.angunawarriorsof_") || result.packageName.startsWith("com.retropack.game.anguna"))
    }
}
