package com.retropack.domain.rom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32

class StreamChecksumTest {

    @Test
    fun `test empty byte array checksums`() {
        val result = StreamChecksum.calculate(ByteArray(0))
        assertEquals(0L, result.totalBytes)
        assertEquals("00000000", result.checksums.crc32)
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", result.checksums.md5)
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", result.checksums.sha1)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result.checksums.sha256)
    }

    @Test
    fun `test standard ASCII test vector`() {
        val testData = "The quick brown fox jumps over the lazy dog".toByteArray(Charsets.UTF_8)
        val result = StreamChecksum.calculate(testData)

        assertEquals(testData.size.toLong(), result.totalBytes)
        assertEquals("414fa339", result.checksums.crc32)
        assertEquals("9e107d9d372bb6826bd81d3542a419d6", result.checksums.md5)
        assertEquals("2fd4e1c67a2d28fced849ee1bb76e7391b93eb12", result.checksums.sha1)
        assertEquals("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592", result.checksums.sha256)
    }

    @Test
    fun `test multi-chunk stream exceeding 64 KB buffer boundary`() {
        // 160 KB payload spans three 64 KB buffer cycles (64KB + 64KB + 32KB)
        val size = 160 * 1024
        val data = ByteArray(size) { (it % 251).toByte() }

        val result = StreamChecksum.calculate(data)
        assertEquals(size.toLong(), result.totalBytes)

        // Independently verify against standard JVM libraries
        val expectedCrc = CRC32().apply { update(data) }.value
        val expectedMd5 = MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }
        val expectedSha1 = MessageDigest.getInstance("SHA-1").digest(data).joinToString("") { "%02x".format(it) }
        val expectedSha256 = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

        assertEquals("%08x".format(expectedCrc), result.checksums.crc32)
        assertEquals(expectedMd5, result.checksums.md5)
        assertEquals(expectedSha1, result.checksums.sha1)
        assertEquals(expectedSha256, result.checksums.sha256)
    }

    @Test
    fun `test file checksum calculation`(@TempDir tempDir: File) {
        val file = File(tempDir, "test_rom.bin")
        val data = ByteArray(128 * 1024) { (it and 0xFF).toByte() }
        file.writeBytes(data)

        val fileResult = StreamChecksum.calculate(file)
        val byteResult = StreamChecksum.calculate(data)

        assertEquals(byteResult.totalBytes, fileResult.totalBytes)
        assertEquals(byteResult.checksums, fileResult.checksums)
    }
}
