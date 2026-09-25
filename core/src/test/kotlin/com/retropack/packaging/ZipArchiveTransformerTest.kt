package com.retropack.packaging

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ZipArchiveTransformerTest {

    @field:TempDir
    lateinit var tempDir: File

    @Test
    fun `test transform aligns so files to 16KB and strips signature residue`() {
        val templateApk = File(tempDir, "template.apk")
        val outputApk = File(tempDir, "output.apk")

        // Build a mock template.apk with classes.dex, lib/arm64-v8a/libmgba.so, AndroidManifest.xml, and stale META-INF signatures
        ZipOutputStream(FileOutputStream(templateApk)).use { zos ->
            // 1. Stale signature files
            zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zos.write("Manifest-Version: 1.0\n".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/CERT.SF"))
            zos.write("Signature-Version: 1.0\n".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            zos.write(byteArrayOf(0x30, 0x82.toByte(), 0x01))
            zos.closeEntry()

            // 2. AndroidManifest.xml
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write("Original Manifest".toByteArray())
            zos.closeEntry()

            // 3. classes.dex
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write("dex-bytecode-data".toByteArray())
            zos.closeEntry()

            // 4. lib/arm64-v8a/libmgba.so
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libmgba.so"))
            zos.write("elf-shared-library-binary-payload".toByteArray())
            zos.closeEntry()
        }

        val injectedEntries = mapOf(
            "AndroidManifest.xml" to "Mutated Manifest".toByteArray(),
            "assets/game.rom" to byteArrayOf(0x00, 0x11, 0x22, 0x33),
            "assets/retropack.json" to "{\"schema_version\": 1}".toByteArray(),
            "res/drawable-nodpi/ic_launcher_foreground.png" to byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00
            )
        )

        // Execute transform
        ZipArchiveTransformer.transform(templateApk, outputApk, injectedEntries)

        assertTrue(outputApk.exists())
        assertTrue(outputApk.length() > 0)

        // Verify entries in output APK
        ZipFile(outputApk).use { zip ->
            // Stale signatures must be stripped
            assertNull(zip.getEntry("META-INF/MANIFEST.MF"))
            assertNull(zip.getEntry("META-INF/CERT.SF"))
            assertNull(zip.getEntry("META-INF/CERT.RSA"))

            // Injected and mutated entries must exist with correct content
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
            assertNotNull(manifestEntry)
            val manifestContent = zip.getInputStream(manifestEntry).use { it.readBytes() }
            assertEquals("Mutated Manifest", String(manifestContent))

            val romEntry = zip.getEntry("assets/game.rom")
            assertNotNull(romEntry)
            val romContent = zip.getInputStream(romEntry).use { it.readBytes() }
            assertArrayEquals(byteArrayOf(0x00, 0x11, 0x22, 0x33), romContent)

            val jsonEntry = zip.getEntry("assets/retropack.json")
            assertNotNull(jsonEntry)

            val iconEntry = zip.getEntry("res/drawable-nodpi/ic_launcher_foreground.png")
            assertNotNull(iconEntry)

            // Preserved entries must exist
            val dexEntry = zip.getEntry("classes.dex")
            assertNotNull(dexEntry)

            val soEntry = zip.getEntry("lib/arm64-v8a/libmgba.so")
            assertNotNull(soEntry)
            assertEquals(ZipEntry.STORED, soEntry.method)
        }

        // Programmatic 16 KB alignment assertion
        val alignmentReport = AlignmentVerifier.verify(outputApk)
        assertTrue(alignmentReport.isCompliant, "Violations: ${alignmentReport.violations}")
        assertEquals(1, alignmentReport.nativeLibraries.size)
        assertTrue(alignmentReport.nativeLibraries[0].isAligned16Kb)
        assertEquals(0L, alignmentReport.nativeLibraries[0].dataOffset % 16384L)
    }
}
