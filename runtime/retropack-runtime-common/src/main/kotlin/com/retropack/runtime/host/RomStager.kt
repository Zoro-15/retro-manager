package com.retropack.runtime.host

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Manages atomic, crash-consistent first-boot staging of game ROMs from APK assets to POSIX disk.
 *
 * Implements specifications from:
 * - masterplan.md Section 5.5: "Atomic First-Boot ROM Staging: Native mGBA requires standard POSIX paths."
 * - architechture.md Constitutional Law 7 & Section 7 (Invariant 5, lines 289-295).
 */
object RomStager {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB chunk size for flash storage throughput
    const val MAX_ROM_SIZE_BYTES: Long = 64L * 1024L * 1024L

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    /**
     * Checks if the ROM is already staged at [destination] and optionally validates its SHA-256 checksum.
     */
    fun isRomStaged(destination: File, expectedSha256: String? = null): Boolean {
        if (!destination.exists() || destination.length() == 0L) {
            return false
        }
        if (expectedSha256.isNullOrBlank()) {
            return true
        }
        val actualSha256 = calculateFileSha256(destination)
        return actualSha256.equals(expectedSha256, ignoreCase = true)
    }

    /**
     * Atomically extracts [sourceStream] to [destination].
     *
     * Invariants enforced:
     * 1. Streams to temporary file (`destination.name + ".tmp"`).
     * 2. Computes running SHA-256 digest during stream copy.
     * 3. Syncs file descriptor (`fd.sync()`) to guarantee data is committed to flash memory.
     * 4. Verifies SHA-256 match if [expectedSha256] is provided.
     * 5. Atomically renames temporary file to [destination].
     *
     * @return true on successful staging, false if verification failed or an I/O error occurred.
     */
    fun stageRom(
        sourceStream: InputStream,
        destination: File,
        expectedSha256: String? = null
    ): Boolean {
        destination.parentFile?.mkdirs()
        // Null-safe sibling (CWD-relative destinations) + resume: a stale .tmp
        // from a killed prior stage must not linger or be mistaken for data.
        val parentDir = destination.parentFile ?: destination.absoluteFile.parentFile
        val tempFile = if (parentDir != null) File(parentDir, "${destination.name}.tmp")
                       else File("${destination.name}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytes = 0L

            FileOutputStream(tempFile).use { fos ->
                var bytesRead: Int
                while (sourceStream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    if (totalBytes > MAX_ROM_SIZE_BYTES) {
                        tempFile.delete()
                        return false
                    }
                    fos.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                }
                fos.flush()
                fos.fd.sync() // POSIX fsync guarantee
            }

            val actualSha256 = bytesToHex(digest.digest())
            if (!expectedSha256.isNullOrBlank() && !actualSha256.equals(expectedSha256, ignoreCase = true)) {
                tempFile.delete()
                return false
            }

            // Atomic rename OVER the destination (no pre-delete: a crash
            // between delete and rename used to lose both files). Falls back
            // to copy+fsync+delete across filesystems.
            val renamed = tempFile.renameTo(destination)
            if (!renamed) {
                try {
                    tempFile.copyTo(destination, overwrite = true)
                    FileOutputStream(destination, true).use { it.fd.sync() }
                    tempFile.delete()
                } catch (_: IOException) {
                    tempFile.delete()
                    return false
                }
            }
            return true
        } catch (_: IOException) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            return false
        }
    }

    private fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        file.inputStream().use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return bytesToHex(digest.digest())
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            chars[i * 2] = HEX_CHARS[v ushr 4]
            chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(chars)
    }
}
