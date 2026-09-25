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

    /**
     * Inspects incoming byte array. If it is a ZIP archive containing a retro ROM
     * (.gba, .gbc, .gb), extracts the inner ROM bytes, fileName, and size.
     */
    fun extractRomIfZip(rawBytes: ByteArray, rawFileName: String): Triple<ByteArray, String, Long> {
        if (!rawFileName.endsWith(".zip", ignoreCase = true) &&
            !(rawBytes.size >= 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte())
        ) {
            return Triple(rawBytes, rawFileName, rawBytes.size.toLong())
        }

        try {
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(rawBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                        val lower = name.lowercase(java.util.Locale.US)
                        if (lower.endsWith(".gba") || lower.endsWith(".gbc") || lower.endsWith(".gb")) {
                            val extracted = zis.readBytes()
                            return Triple(extracted, name, extracted.size.toLong())
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
            // Fall back to original bytes if not a valid zip
        }
        return Triple(rawBytes, rawFileName, rawBytes.size.toLong())
    }
}
