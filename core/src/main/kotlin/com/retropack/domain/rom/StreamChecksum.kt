package com.retropack.domain.rom

import com.retropack.domain.model.ChecksumRecords
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Result of a streaming checksum calculation containing individual hash records
 * and total byte count.
 */
data class StreamChecksumResult(
    val checksums: ChecksumRecords,
    val totalBytes: Long
)

/**
 * High-performance, zero-heap chunked streaming checksum calculator.
 * Reads InputStreams in 64 KB buffers, calculating CRC32, MD5, SHA-1, and SHA-256
 * concurrently in a single pass without heap allocation spikes.
 */
object StreamChecksum {
    const val BUFFER_SIZE: Int = 64 * 1024 // 64 KB chunk size

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    /**
     * Reads the provided [stream] in 64 KB chunks, feeding CRC32, MD5, SHA-1,
     * and SHA-256 concurrently.
     */
    fun calculate(stream: InputStream): StreamChecksumResult {
        val crc = CRC32()
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")

        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytes = 0L
        var bytesRead: Int

        while (stream.read(buffer).also { bytesRead = it } != -1) {
            if (bytesRead > 0) {
                totalBytes += bytesRead
                crc.update(buffer, 0, bytesRead)
                md5.update(buffer, 0, bytesRead)
                sha1.update(buffer, 0, bytesRead)
                sha256.update(buffer, 0, bytesRead)
            }
        }

        val records = ChecksumRecords(
            crc32 = "%08x".format(crc.value and 0xFFFFFFFFL),
            md5 = md5.digest().toHexString(),
            sha1 = sha1.digest().toHexString(),
            sha256 = sha256.digest().toHexString()
        )

        return StreamChecksumResult(
            checksums = records,
            totalBytes = totalBytes
        )
    }

    /**
     * Calculates checksums for the given byte array.
     */
    fun calculate(bytes: ByteArray): StreamChecksumResult {
        return ByteArrayInputStream(bytes).use { calculate(it) }
    }

    /**
     * Calculates checksums for the given file.
     */
    fun calculate(file: File): StreamChecksumResult {
        return FileInputStream(file).use { calculate(it) }
    }

    /**
     * Fast hex formatting without allocating intermediary strings or builders.
     */
    private fun ByteArray.toHexString(): String {
        val chars = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            chars[i * 2] = HEX_CHARS[v ushr 4]
            chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(chars)
    }
}
