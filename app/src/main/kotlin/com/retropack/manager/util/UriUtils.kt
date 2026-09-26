package com.retropack.manager.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream

object UriUtils {

    fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "unknown_file"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // Fall back to defaults
        }

        return Pair(name, size)
    }

    fun writeTextToUri(context: Context, uri: Uri, content: String): Result<Unit> {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: throw IllegalStateException("Could not open output stream for URI: $uri")
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    val SUPPORTED_ROM_EXTENSIONS = com.retropack.domain.rom.ArchiveExtractor.SUPPORTED_ROM_EXTENSIONS

    /**
     * Inspects incoming byte array. If it is a ZIP or RAR archive containing a retro ROM,
     * extracts the inner ROM bytes, fileName, and size.
     */
    fun extractRomIfArchive(rawBytes: ByteArray, rawFileName: String): Triple<ByteArray, String, Long> {
        val result = com.retropack.domain.rom.ArchiveExtractor.extractCandidateRom(rawBytes, rawFileName)
        return Triple(result.bytes, result.candidateFileName, result.bytes.size.toLong())
    }

    /**
     * Backward-compatible alias for extractRomIfArchive.
     */
    fun extractRomIfZip(rawBytes: ByteArray, rawFileName: String): Triple<ByteArray, String, Long> {
        return extractRomIfArchive(rawBytes, rawFileName)
    }
}
