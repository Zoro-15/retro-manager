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

    private const val BUFFER_SIZE = 8192

    /**
     * Checks if the ROM is already staged at [destination] and optionally validates its SHA-256 checksum.
     */
    fun isRomStaged(destination: File, expectedSha256: String? = null): Boolean {
        if (!destination.exists() || destination.length() == 0L) {
            return false
        }
        if (expectedSha256 == null) {
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
        val tempFile = File(destination.parentFile, "${destination.name}.tmp")

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)

            FileOutputStream(tempFile).use { fos ->
                var bytesRead: Int
                while (sourceStream.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                }
                fos.flush()
                fos.fd.sync() // POSIX fsync guarantee
            }

            val actualSha256 = bytesToHex(digest.digest())
            if (expectedSha256 != null && !actualSha256.equals(expectedSha256, ignoreCase = true)) {
                tempFile.delete()
                return false
            }

            // Atomic filesystem rename
            if (destination.exists()) {
                destination.delete()
            }
            val renamed = tempFile.renameTo(destination)
            if (!renamed) {
                tempFile.delete()
                return false
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
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }
}
