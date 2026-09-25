package com.retropack.manager

import com.retropack.manager.util.UriUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UriUtilsTest {

    @Test
    fun testFormatFileSize() {
        assertEquals("512 B", UriUtils.formatFileSize(512L))
        assertEquals("1.0 KB", UriUtils.formatFileSize(1024L))
        assertEquals("512.0 KB", UriUtils.formatFileSize(512 * 1024L))
        assertEquals("1.00 MB", UriUtils.formatFileSize(1024 * 1024L))
        assertEquals("16.00 MB", UriUtils.formatFileSize(16 * 1024 * 1024L))
        assertEquals("32.50 MB", UriUtils.formatFileSize((32.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun testExtractRomIfZipWithGba() {
        val fakeGbaBytes = ByteArray(1024) { 0x42 }
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("Pokemon - FireRed Version (USA).gba"))
            zos.write(fakeGbaBytes)
            zos.closeEntry()
        }
        val zipBytes = baos.toByteArray()
        val (extracted, fileName, size) = UriUtils.extractRomIfZip(zipBytes, "Pokemon - FireRed Version (USA).zip")
        assertEquals("Pokemon - FireRed Version (USA).gba", fileName)
        assertEquals(1024L, size)
        org.junit.jupiter.api.Assertions.assertArrayEquals(fakeGbaBytes, extracted)
    }

    @Test
    fun testExtractRomIfZipWithRawGba() {
        val fakeGbaBytes = ByteArray(512) { 0x11 }
        val (extracted, fileName, size) = UriUtils.extractRomIfZip(fakeGbaBytes, "game.gba")
        assertEquals("game.gba", fileName)
        assertEquals(512L, size)
        org.junit.jupiter.api.Assertions.assertArrayEquals(fakeGbaBytes, extracted)
    }
}
