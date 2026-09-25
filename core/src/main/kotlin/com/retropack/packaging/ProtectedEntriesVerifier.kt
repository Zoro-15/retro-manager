package com.retropack.packaging

import com.retropack.domain.runtime.RuntimeDescriptor
import com.retropack.packaging.HexUtils.toHexString
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Enforces cryptographic integrity on immutable protected entries (Step 9).
 *
 * All bytecode (`classes*.dex`) and native shared libraries (`lib/`) must match
 * the trusted hashes declared in the runtime descriptor before signing can proceed.
 */
object ProtectedEntriesVerifier {

    /**
     * Verifies that the entries in [apkFile] match the expected [descriptor] protected entries.
     * Throws [SecurityException] if any entry is missing or altered.
     */
    fun verifyApk(apkFile: File, descriptor: RuntimeDescriptor) {
        require(apkFile.exists()) { "APK file does not exist: ${apkFile.absolutePath}" }

        ZipFile(apkFile).use { zip ->
            for ((entryPath, expectedHash) in descriptor.protectedEntries) {
                val entry = zip.getEntry(entryPath)
                    ?: throw SecurityException("Protected entry missing from APK archive: $entryPath")

                val actualHash = zip.getInputStream(entry).use { computeSha256(it) }
                if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                    throw SecurityException(
                        "Protected entry integrity violation for '$entryPath'! " +
                            "Expected: $expectedHash, Actual: $actualHash"
                    )
                }
            }
        }
    }

    /**
     * Verifies in-memory entry byte map against expected [protectedEntries].
     */
    fun verifyEntries(entryBytes: Map<String, ByteArray>, protectedEntries: Map<String, String>) {
        for ((entryPath, expectedHash) in protectedEntries) {
            val bytes = entryBytes[entryPath]
                ?: throw SecurityException("Protected entry missing from staged entries: $entryPath")

            val actualHash = computeSha256(bytes)
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                throw SecurityException(
                    "Protected entry integrity violation for '$entryPath'! " +
                        "Expected: $expectedHash, Actual: $actualHash"
                )
            }
        }
    }

    private fun computeSha256(inputStream: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            md.update(buffer, 0, read)
        }
        return md.digest().toHexString()
    }

    private fun computeSha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).toHexString()
    }
}
