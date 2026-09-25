package com.retropack.packaging

import com.android.zipflinger.BytesSource
import com.android.zipflinger.ZipArchive
import java.io.File
import java.nio.file.Files
import java.util.zip.Deflater
import java.util.zip.ZipFile

/**
 * Reassembles APK ZIP archives using Android's zipflinger toolchain (Step 10).
 *
 * Enforces Constitutional Invariant 3:
 * Computes exact physical alignment padding after all variable-length entries
 * (mutated AXML, injected ROM/configs, icons) are sized and sequenced.
 * All uncompressed native libraries (`.so`) are written with `alignment = 16384` (16 KB page-size).
 */
object ZipArchiveTransformer {

    const val ALIGNMENT_16KB = 16384L
    const val ALIGNMENT_4B = 4L

    /**
     * Transforms [templateApk] into [outputApk] by injecting [injectedEntries],
     * stripping stale signature residue, and aligning native libraries to 16 KB boundaries.
     */
    fun transform(
        templateApk: File,
        outputApk: File,
        injectedEntries: Map<String, ByteArray>
    ) {
        require(templateApk.exists()) { "Template APK does not exist: ${templateApk.absolutePath}" }

        // Ensure output parent directories exist and target file is clean
        outputApk.parentFile?.mkdirs()
        if (outputApk.exists()) {
            outputApk.delete()
        }

        // Open zipflinger ZipArchive targeting the output file
        ZipArchive(outputApk.toPath()).use { archive ->
            // 1. Process and transfer existing template entries
            ZipFile(templateApk).use { sourceZip ->
                val entries = sourceZip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    // Step 5: Strip stale signature residue (META-INF/*.SF, *.RSA, *.DSA, *.EC, MANIFEST.MF)
                    if (ScratchAllocator.isSignatureResidue(name)) {
                        continue
                    }

                    // Skip entries being overridden by injected replacements
                    if (injectedEntries.containsKey(name)) {
                        continue
                    }

                    // Read source entry bytes
                    val bytes = sourceZip.getInputStream(entry).use { it.readBytes() }
                    val source = createSourceForEntry(name, bytes)
                    archive.add(source)
                }
            }

            // 2. Inject mutated and added entries (Steps 6, 7, 8)
            for ((name, bytes) in injectedEntries) {
                val source = createSourceForEntry(name, bytes)
                archive.add(source)
            }
        }
    }

    private fun createSourceForEntry(name: String, bytes: ByteArray): BytesSource {
        val lowerName = name.lowercase()

        return when {
            // Uncompressed 16 KB Page Aligned Native Libraries (Android 15 / Invariant 3)
            lowerName.endsWith(".so") -> {
                val source = BytesSource(bytes, name, Deflater.NO_COMPRESSION)
                source.align(ALIGNMENT_16KB)
                source
            }

            // Uncompressed 4-Byte Aligned DEX bytecode and Resource Table
            lowerName.endsWith(".dex") || lowerName == "resources.arsc" -> {
                val source = BytesSource(bytes, name, Deflater.NO_COMPRESSION)
                source.align(ALIGNMENT_4B)
                source
            }

            // Uncompressed image drawables (PNG layers)
            lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".jpg") -> {
                BytesSource(bytes, name, Deflater.NO_COMPRESSION)
            }

            // Compressed XML, JSON, ROM and other resources
            else -> {
                BytesSource(bytes, name, Deflater.DEFAULT_COMPRESSION)
            }
        }
    }
}
