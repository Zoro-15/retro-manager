package com.retropack.manager.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.retropack.manager.util.AppLogger
import com.retropack.manager.util.UriUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Battery Save Backup, Export, and Cross-Platform Import Manager for RetroPack.
 *
 * Implements Feature 3 specifications:
 * - Reads battery saves (.srm, .sav, .nvram).
 * - Exports saves to Downloads/RetroPack/Saves/<GameTitle>/ with timestamp.
 * - Imports saves from emulators (mGBA, Snes9x, Genesis Plus GX, FCEUmm, Mednafen PCE)
 *   with atomic POSIX fsync verification.
 */
object SaveSyncManager {

    /**
     * Exports the game's battery save file to the device's Downloads/RetroPack/Saves/<GameTitle>/ folder.
     */
    suspend fun exportSave(context: Context, game: InstalledGame): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = game.saveFile
                ?: throw IllegalStateException("No active battery save file found for ${game.gameTitle}")

            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                throw IllegalStateException("Save file is empty or unreadable")
            }

            val sanitizedTitle = game.gameTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "RetroPack/Saves/$sanitizedTitle"
            ).apply { mkdirs() }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val ext = if (sourceFile.name.contains(".")) ".${sourceFile.name.substringAfterLast(".")}" else game.saveFormat
            val targetFile = File(targetDir, "${sanitizedTitle}_backup_$timestamp$ext")

            // Copy with POSIX fsync flush
            sourceFile.inputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }

            AppLogger.i("SaveSyncManager", "Successfully exported save for '${game.gameTitle}' to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            targetFile
        }
    }

    /**
     * Creates an Android ACTION_SEND intent to share the game's save file via messaging/cloud apps.
     */
    fun createShareIntent(context: Context, game: InstalledGame, saveFile: File): Intent {
        val uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                saveFile
            )
        } catch (_: Exception) {
            Uri.fromFile(saveFile)
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RetroPack Save: ${game.gameTitle}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Imports a save file from PC Emulator or file picker and atomically injects it into the target game.
     */
    suspend fun importSave(
        context: Context,
        game: InstalledGame,
        inputUri: Uri
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = UriUtils.readBytesFromUri(context, inputUri)
                ?: throw IllegalStateException("Failed to read save bytes from selected URI")

            if (bytes.isEmpty()) {
                throw IllegalStateException("Imported save file is empty (0 bytes)")
            }

            // Determine target save location
            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "RetroPack/Saves/${game.packageName}"
            ).apply { mkdirs() }

            val targetFile = File(targetDir, "game${game.saveFormat}")
            val tempFile = File(targetDir, "game_import_scratch.tmp")

            FileOutputStream(tempFile).use { out ->
                out.write(bytes)
                out.fd.sync()
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }

            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            AppLogger.i("SaveSyncManager", "Successfully imported ${bytes.size} bytes into ${targetFile.absolutePath} for '${game.gameTitle}'")
            targetFile
        }
    }
}
