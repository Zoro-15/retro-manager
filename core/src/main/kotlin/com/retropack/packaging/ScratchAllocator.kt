package com.retropack.packaging

import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

/**
 * Manages atomic scratch files and signature residue identification (Steps 4 & 5).
 */
object ScratchAllocator {

    private val SIGNATURE_EXTENSIONS = setOf("sf", "rsa", "dsa", "ec", "mf")

    /**
     * Allocates a hidden scratch temporary file in the same parent directory as [targetFile].
     *
     * By allocating in the exact same directory, an atomic filesystem `renameTo` is guaranteed
     * not to throw `EXDEV` (cross-device link error).
     */
    fun allocateScratchFile(targetFile: File): File {
        val parentDir = targetFile.parentFile ?: File(".")
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        val uuid = UUID.randomUUID().toString().replace("-", "")
        return File(parentDir, ".${uuid}.tmp.apk")
    }

    /**
     * Determines whether a ZIP entry path is stale signature metadata that must be stripped.
     */
    fun isSignatureResidue(entryName: String): Boolean {
        val normalized = entryName.replace('\\', '/')
        if (!normalized.startsWith("META-INF/", ignoreCase = true)) {
            return false
        }
        val fileName = normalized.substringAfterLast('/').lowercase(Locale.ROOT)
        val ext = fileName.substringAfterLast('.', "")
        
        // Exact MANIFEST.MF
        if (fileName == "manifest.mf") return true
        
        // Android/Java signature block files: *.SF, *.RSA, *.DSA, *.EC
        if (ext in SIGNATURE_EXTENSIONS) return true
        
        // Custom signature files: META-INF/SIG-* or *.sig
        if (fileName.startsWith("sig-") || ext == "sig") return true

        return false
    }

    /**
     * Safely executes an atomic rename from [source] to [target], falling back to a durable
     * byte copy with fsync if rename fails.
     */
    fun atomicFinalize(source: File, target: File) {
        if (!source.exists()) {
            throw IOException("Source scratch file does not exist: ${source.absolutePath}")
        }
        if (target.exists()) {
            target.delete()
        }
        if (source.renameTo(target)) {
            return
        }

        // Fallback: Copy bytes with fsync, then delete source
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
        source.delete()
    }
}
