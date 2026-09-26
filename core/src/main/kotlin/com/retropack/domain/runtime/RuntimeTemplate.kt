package com.retropack.domain.runtime

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Concrete encapsulation of a precompiled generic standalone runtime template bundle.
 *
 * Implements specifications from:
 * - architechture.md Section 3: "The Runtime Abstraction: RuntimeTemplate"
 * - architechture.md Section 5: Allowlist Policy & Protected Entries
 */
data class RuntimeTemplate(
    val descriptor: RuntimeDescriptor,
    val templateApk: File
) {
    companion object {
        const val TEMPLATE_APK_FILENAME = "template.apk"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB chunk size

        fun computeSha256(file: File): String {
            require(file.exists() && file.isFile) { "File does not exist: ${file.absolutePath}" }
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Computes the physical SHA-256 digest of `template.apk`.
     */
    fun computeSha256(): String = computeSha256(templateApk)

    /**
     * Asserts that `template.apk` matches the trusted SHA-256 hash.
     */
    fun verifyTemplateIntegrity(expectedSha256: String): Boolean {
        val actualSha256 = computeSha256()
        val normalizedExpected = expectedSha256.removePrefix("sha256:").trim()
        return actualSha256.equals(normalizedExpected, ignoreCase = true)
    }

    /**
     * Cryptographically verifies that all entries declared in `protected_entries` match
     * their expected digests in [actualEntries] (entry name -> SHA-256).
     */
    fun verifyProtectedEntries(actualEntries: Map<String, String>): Boolean {
        for ((entryName, expectedRawHash) in descriptor.protectedEntries) {
            val actualHash = actualEntries[entryName] ?: return false
            val normalizedExpected = expectedRawHash.removePrefix("sha256:").trim()
            val normalizedActual = actualHash.removePrefix("sha256:").trim()
            if (!normalizedActual.equals(normalizedExpected, ignoreCase = true)) {
                return false
            }
        }
        return true
    }
}
