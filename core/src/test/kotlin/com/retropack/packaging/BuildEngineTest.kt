package com.retropack.packaging

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.retropack.domain.model.*
import com.retropack.domain.rom.GbaTestRomFactory
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
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BuildEngineTest {

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
        block.setApplicationLabel("Original Template")

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
            zos.write("classes-dex-bytecode-data-payload".toByteArray())
            zos.closeEntry()

            // lib/arm64-v8a/libmgba.so
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libmgba.so"))
            zos.write("libmgba-so-binary-shared-library-data".toByteArray())
            zos.closeEntry()
        }
        return file
    }

    private val validPngHeader = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02
    )

    @Test
    fun `test complete 15-step transformation pipeline with GBA rom`() {
        val templateFile = createMockTemplateApk(File(tempDir, "template.apk"))
        val outputDir = File(tempDir, "output")
        val romBytes = GbaTestRomFactory.create(
            title = "POKEMON EMER",
            gameCode = "BPEE",
            makerCode = "01",
            version = 0
        )
        val checksums = StreamChecksum.calculate(ByteArrayInputStream(romBytes))

        val request = BuildRequest(
            identity = GameIdentity(
                gameId = "emerald-01",
                gameTitle = "Pokemon Emerald Version",
                packageName = "com.retropack.game.pokemon_emerald",
                versionCode = 1,
                versionName = "1.0.0"
            ),
            content = ContentPayload(
                sourceRom = "pokemon_emerald.gba",
                platform = "gba"
            ),
            runtime = RuntimeConfigPayload(
                templateId = "mgba-unified",
                audio = AudioSettings(sampleRate = 44100, bufferSize = 2048),
                video = VideoSettings(scaleMode = "integer_fit")
            ),
            controls = ControlsPayload(
                touch = TouchControlsSettings(enabled = true, opacity = 0.7f, haptics = true)
            ),
            storage = StoragePayload(saveType = "battery_sram", periodicFlushIntervalSec = 60)
        )

        val signingIdentity = HybridKeystore.generateIdentity("game_signer", KeyType.RSA_2048)

        val stageCallbackLogs = mutableListOf<BuildStageRecord>()

        val result = BuildEngine.build(
            request = request,
            romBytes = romBytes,
            signingIdentity = signingIdentity,
            outputDir = outputDir,
            iconForegroundBytes = validPngHeader,
            templateOverride = templateFile,
            stageListener = { stageCallbackLogs.add(it) }
        )

        // 1. Assert result success
        assertTrue(result.success, "Build failed with error: ${result.errorMessage}")
        assertNotNull(result.artifactFile)
        val artifact = result.artifactFile!!
        assertTrue(artifact.exists())
        assertTrue(artifact.length() > 0)
        assertEquals(1, result.versionCode)
        assertTrue(result.packageName.startsWith("com.retropack.game."))
        assertTrue(result.certificateSha256Fingerprint.isNotEmpty())
        assertEquals(15, result.stageProvenance.size)
        assertEquals(15, stageCallbackLogs.size)

        // 2. Assert all 15 stages passed
        for (i in 1..15) {
            val stage = result.stageProvenance[i - 1]
            assertEquals(i, stage.stageNumber)
            assertTrue(stage.passed, "Stage $i failed: ${stage.description}")
        }

        // 3. Verify signature using ApkVerificationService
        val verification = ApkVerificationService.verifyApk(artifact)
        assertTrue(verification.isVerified, "Errors: ${verification.errors}")
        assertTrue(verification.isV1SchemeVerified)
        assertTrue(verification.isV2SchemeVerified)
        assertTrue(verification.isV3SchemeVerified)
        assertEquals(1, verification.signerCertificates.size)
        assertEquals(signingIdentity.certificate, verification.signerCertificates[0])

        // 4. Assert 16 KB Page Alignment compliance
        val alignmentReport = AlignmentVerifier.verify(artifact)
        assertTrue(alignmentReport.isCompliant, "Violations: ${alignmentReport.violations}")
        assertEquals(1, alignmentReport.nativeLibraries.size)
        assertTrue(alignmentReport.nativeLibraries[0].isAligned16Kb)

        // 5. Inspect contents of final APK container
        ZipFile(artifact).use { zip ->
            // Injected ROM
            val romEntry = zip.getEntry("assets/game.rom")
            assertNotNull(romEntry)
            val extractedRom = zip.getInputStream(romEntry).use { it.readBytes() }
            assertArrayEquals(romBytes, extractedRom)

            // Injected retropack.json
            val jsonEntry = zip.getEntry("assets/retropack.json")
            assertNotNull(jsonEntry)
            val jsonContent = String(zip.getInputStream(jsonEntry).use { it.readBytes() })
            assertTrue(jsonContent.contains("\"title\": \"Pokemon Emerald Version\""))
            assertTrue(jsonContent.contains("\"platform\": \"gba\""))
            assertTrue(jsonContent.contains("\"rom_sha256\": \"${checksums.checksums.sha256}\""))

            // Injected icon
            val iconEntry = zip.getEntry("res/drawable-nodpi/ic_launcher_foreground.png")
            assertNotNull(iconEntry)

            // Mutated AndroidManifest.xml
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
            assertNotNull(manifestEntry)
            val manifestBytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
            val manifestBlock = AndroidManifestBlock()
            ByteArrayInputStream(manifestBytes).use { manifestBlock.readBytes(it) }
            assertEquals(result.packageName, manifestBlock.packageName)
            assertEquals("Pokemon Emerald Version", manifestBlock.applicationLabelString)

            // Protected classes.dex preserved
            val dexEntry = zip.getEntry("classes.dex")
            assertNotNull(dexEntry)
        }
    }

    @Test
    fun `test precomputed checksums path matches default build outputs`() {
        val templateFile = createMockTemplateApk(File(tempDir, "template-precomputed.apk"))
        val romBytes = GbaTestRomFactory.create(title = "MARIO KART", gameCode = "AMKE", makerCode = "01")
        val checksums = StreamChecksum.calculate(ByteArrayInputStream(romBytes)).checksums

        fun freshRequest() = BuildRequest(
            identity = GameIdentity(
                gameId = "kart-01",
                gameTitle = "Mario Kart",
                packageName = "com.retropack.game.placeholder",
                versionCode = 3,
                versionName = "3.0.0"
            ),
            content = ContentPayload(sourceRom = "kart.gba", platform = "gba"),
            runtime = RuntimeConfigPayload(templateId = "mgba-unified")
        )
        val signingIdentity = HybridKeystore.generateIdentity("game_precomputed", KeyType.RSA_2048)

        val baseline = BuildEngine.build(
            request = freshRequest(),
            romBytes = romBytes,
            signingIdentity = signingIdentity,
            outputDir = File(tempDir, "output-precomputed-baseline"),
            templateOverride = templateFile
        )
        val optimized = BuildEngine.build(
            request = freshRequest(),
            romBytes = romBytes,
            signingIdentity = signingIdentity,
            outputDir = File(tempDir, "output-precomputed-fast"),
            templateOverride = templateFile,
            precomputedChecksums = checksums
        )

        assertTrue(baseline.success, "Baseline failed: ${baseline.errorMessage}")
        assertTrue(optimized.success, "Optimized failed: ${optimized.errorMessage}")
        assertEquals(baseline.packageName, optimized.packageName)
        assertEquals(baseline.versionCode, optimized.versionCode)
        assertTrue(optimized.stageProvenance.all { it.passed })

        ZipFile(baseline.artifactFile!!).use { baseZip ->
            ZipFile(optimized.artifactFile!!).use { fastZip ->
                val baseRom = baseZip.getInputStream(baseZip.getEntry("assets/game.rom")).use { it.readBytes() }
                val fastRom = fastZip.getInputStream(fastZip.getEntry("assets/game.rom")).use { it.readBytes() }
                assertArrayEquals(baseRom, fastRom)
                val baseJson = baseZip.getInputStream(baseZip.getEntry("assets/retropack.json")).use { it.readBytes() }
                val fastJson = fastZip.getInputStream(fastZip.getEntry("assets/retropack.json")).use { it.readBytes() }
                // Same inputs except wall-clock provenance timestamp.
                assertEquals(
                    String(baseJson).replace(Regex("\"build_timestamp_utc\": \"[^\"]*\""), ""),
                    String(fastJson).replace(Regex("\"build_timestamp_utc\": \"[^\"]*\""), "")
                )
            }
        }
    }

    @Test
    fun `test template hash skip decision honors identity match only`() {
        val templateFile = createMockTemplateApk(File(tempDir, "template-skip.apk"))
        val matching = BuildEngine.VerifiedTemplate(
            file = templateFile,
            sha256 = "abc123",
            lastModified = templateFile.lastModified(),
            length = templateFile.length()
        )
        assertTrue(BuildEngine.shouldSkipTemplateHash(matching, templateFile))

        assertFalse(
            BuildEngine.shouldSkipTemplateHash(null, templateFile),
            "Absent verification must hash"
        )
        assertFalse(
            BuildEngine.shouldSkipTemplateHash(
                matching.copy(lastModified = matching.lastModified + 1), templateFile
            ),
            "Stale mtime must re-hash"
        )
        assertFalse(
            BuildEngine.shouldSkipTemplateHash(
                matching.copy(length = matching.length + 1), templateFile
            ),
            "Size drift must re-hash"
        )
        assertFalse(
            BuildEngine.shouldSkipTemplateHash(
                matching.copy(sha256 = ""), templateFile
            ),
            "Empty digest must re-hash"
        )
        assertFalse(
            BuildEngine.shouldSkipTemplateHash(
                matching.copy(file = File(tempDir, "other.apk")), templateFile
            ),
            "Path mismatch must re-hash"
        )
    }

    @Test
    fun `test build fails cleanly on empty ROM`() {
        val templateFile = createMockTemplateApk(File(tempDir, "template2.apk"))
        val outputDir = File(tempDir, "output2")
        val signingIdentity = HybridKeystore.generateIdentity("game_signer2", KeyType.EC_P256)

        val request = BuildRequest(
            identity = GameIdentity(
                gameId = "test",
                gameTitle = "Test Game",
                packageName = "com.retropack.game.test"
            ),
            content = ContentPayload(
                sourceRom = "test.gba",
                platform = "gba"
            )
        )

        val result = BuildEngine.build(
            request = request,
            romBytes = ByteArray(0),
            signingIdentity = signingIdentity,
            outputDir = outputDir,
            templateOverride = templateFile
        )

        assertFalse(result.success)
        assertNull(result.artifactFile)
        assertTrue(result.errorMessage?.contains("empty") == true)
        assertTrue(result.stageProvenance.isNotEmpty())
        assertFalse(result.stageProvenance[0].passed)
    }

    @Test
    fun `test build fails on missing template`() {
        val outputDir = File(tempDir, "output3")
        val signingIdentity = HybridKeystore.generateIdentity("game_signer3", KeyType.RSA_2048)
        val romBytes = GbaTestRomFactory.create()

        val request = BuildRequest(
            identity = GameIdentity(
                gameId = "test",
                gameTitle = "Test Game",
                packageName = "com.retropack.game.test"
            ),
            content = ContentPayload(
                sourceRom = "test.gba",
                platform = "gba"
            ),
            runtime = RuntimeConfigPayload(templateId = "non_existent_template")
        )

        val result = BuildEngine.build(
            request = request,
            romBytes = romBytes,
            signingIdentity = signingIdentity,
            outputDir = outputDir
        )

        assertFalse(result.success)
        assertNull(result.artifactFile)
        assertTrue(result.errorMessage?.contains("non_existent_template") == true)
    }
}
