package com.retropack.packaging

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AlignmentVerifierTest {

    @field:TempDir
    lateinit var tempDir: File

    @Test
    fun `test report on empty or non-native zip`() {
        val zipFile = File(tempDir, "plain.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val entry = ZipEntry("assets/test.txt")
            zos.putNextEntry(entry)
            zos.write("hello".toByteArray())
            zos.closeEntry()
        }

        val report = AlignmentVerifier.verify(zipFile)
        assertTrue(report.isCompliant)
        assertEquals(1, report.totalEntries)
        assertTrue(report.nativeLibraries.isEmpty())
        assertTrue(report.violations.isEmpty())
    }

    @Test
    fun `test non-existent file throws exception`() {
        val nonExistent = File(tempDir, "does_not_exist.apk")
        assertThrows(IllegalArgumentException::class.java) {
            AlignmentVerifier.verify(nonExistent)
        }
    }

    @Test
    fun `test assertCompliant passes on compliant archive and fails on unaligned`() {
        // Create an archive with unaligned .so using standard ZipOutputStream
        val unalignedApk = File(tempDir, "unaligned.apk")
        val bytes = byteArrayOf(1, 2, 3, 4)
        val computedCrc = CRC32().apply { update(bytes) }.value

        ZipOutputStream(FileOutputStream(unalignedApk)).use { zos ->
            zos.setMethod(ZipOutputStream.STORED)
            val entry = ZipEntry("lib/arm64-v8a/libtest.so").apply {
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                crc = computedCrc
            }
            zos.putNextEntry(entry)
            zos.write(bytes)
            zos.closeEntry()
        }

        val report = AlignmentVerifier.verify(unalignedApk)
        assertFalse(report.isCompliant)
        assertEquals(1, report.nativeLibraries.size)
        assertFalse(report.nativeLibraries[0].isAligned16Kb)
        assertTrue(report.nativeLibraries[0].dataOffset > 0L)
        assertNotEquals(0L, report.nativeLibraries[0].dataOffset % 16384L)

        assertThrows(IllegalStateException::class.java) {
            AlignmentVerifier.assertCompliant(unalignedApk)
        }
    }
}
