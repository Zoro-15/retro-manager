package com.retropack.manager.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.retropack.manager.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log entry parsed from logcat or native engine persistent logs.
 */
data class GameLogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val isError: Boolean = false,
    val isCrash: Boolean = false,
    val isEngine: Boolean = false
)

/**
 * Result bundle for an installed game's captured diagnostic logs.
 */
data class GameLogResult(
    val game: InstalledGame,
    val timestamp: Long,
    val entries: List<GameLogEntry>,
    val rawText: String,
    val summary: String,
    val hasCrash: Boolean,
    val hasErrors: Boolean
)

/**
 * Diagnostic Service capturing, filtering, and exporting logcat & native runtime
 * logs for installed RetroPack standalone game APKs.
 */
object GameLogCaptureService {

    private val RELEVANT_TAGS = listOf(
        "RetroEngine-Main",
        "RetroEngine-Bridge",
        "RetroEngine-Audio",
        "RetroEngine-Gles",
        "RetroEngine-Pad",
        "RetroEngine-State",
        "RetroEngine-Osd",
        "RetroCore",
        "RetroPack",
        "NativeActivity",
        "AndroidRuntime",
        "DEBUG",
        "libc"
    )

    /**
     * Captures recent logcat buffers and on-disk persistent engine logs for [game].
     */
    suspend fun captureLogs(
        context: Context,
        game: InstalledGame,
        maxLines: Int = 1000
    ): GameLogResult = withContext(Dispatchers.IO) {
        val collectedLines = mutableListOf<String>()
        val parsedEntries = mutableListOf<GameLogEntry>()

        // 1. Read on-device system logcat buffer
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", maxLines.toString()))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (isRelevantLogLine(currentLine, game.packageName)) {
                    collectedLines.add(currentLine)
                    parseLogcatLine(currentLine)?.let { parsedEntries.add(it) }
                }
            }
            reader.close()
            process.waitFor()
        } catch (e: Exception) {
            AppLogger.w("GameLogCaptureService", "Could not read logcat buffer: ${e.message}")
        }

        // 2. Read persistent on-disk engine logs if present
        val onDiskFiles = locateOnDiskLogs(context, game.packageName)
        for (file in onDiskFiles) {
            try {
                if (file.exists() && file.length() > 0) {
                    val fileLines = file.readLines(Charsets.UTF_8)
                    collectedLines.add("\n--- [From Disk: ${file.name}] ---")
                    for (fLine in fileLines) {
                        collectedLines.add(fLine)
                        parseDiskLogLine(fLine)?.let { parsedEntries.add(it) }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("GameLogCaptureService", "Could not read file ${file.absolutePath}: ${e.message}")
            }
        }

        val rawText = if (collectedLines.isNotEmpty()) {
            collectedLines.joinToString("\n")
        } else {
            "[No logs captured yet for ${game.packageName}]\n" +
            "Tip: Tap 'Launch & Record' to start the game and immediately stream startup diagnostics."
        }

        val hasCrash = parsedEntries.any { it.isCrash } ||
                rawText.contains("FATAL EXCEPTION", ignoreCase = true) ||
                rawText.contains("FATAL CRASH SIGNAL", ignoreCase = true) ||
                rawText.contains("SIGSEGV", ignoreCase = true) ||
                rawText.contains("SIGABRT", ignoreCase = true)

        val hasErrors = hasCrash || parsedEntries.any { it.isError } || rawText.contains("ERROR", ignoreCase = true)

        val summary = when {
            hasCrash -> "CRASH DETECTED: Fatal signal or uncaught exception during execution."
            hasErrors -> "WARNINGS/ERRORS: Native engine reported initialization errors."
            parsedEntries.any { it.message.contains("Starting", ignoreCase = true) } -> "BOOT OK: Pure C++ NativeActivity initialized."
            else -> "IDLE: No recent activity recorded in logcat."
        }

        GameLogResult(
            game = game,
            timestamp = System.currentTimeMillis(),
            entries = parsedEntries,
            rawText = rawText,
            summary = summary,
            hasCrash = hasCrash,
            hasErrors = hasErrors
        )
    }

    /**
     * Launches [game] and records the subsequent logcat output over a monitoring window.
     */
    suspend fun launchAndRecord(
        context: Context,
        game: InstalledGame,
        recordingDurationMs: Long = 2500L
    ): GameLogResult = withContext(Dispatchers.IO) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(game.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }

        delay(recordingDurationMs)
        captureLogs(context, game)
    }

    /**
     * Exports the raw log text to a file in Downloads/RetroPack/Logs/.
     */
    fun exportLogToFile(context: Context, game: InstalledGame, rawText: String): File {
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logsDir = File(baseDir, "RetroPack/Logs").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeTitle = game.gameTitle.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val targetFile = File(logsDir, "${safeTitle}_$timeStamp.log")
        targetFile.writeText(rawText, Charsets.UTF_8)
        return targetFile
    }

    /**
     * Creates an Android Intent to share the exported log file.
     */
    fun createShareLogIntent(context: Context, game: InstalledGame, logFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RetroPack Diagnostic Log - ${game.gameTitle}")
            putExtra(Intent.EXTRA_TEXT, "Diagnostic log for ${game.gameTitle} (${game.packageName})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun isRelevantLogLine(line: String, packageName: String): Boolean {
        if (line.contains(packageName, ignoreCase = true)) return true
        for (tag in RELEVANT_TAGS) {
            if (line.contains(tag, ignoreCase = true)) return true
        }
        return false
    }

    private fun parseLogcatLine(line: String): GameLogEntry? {
        val isCrash = line.contains("FATAL", ignoreCase = true) ||
                line.contains("SIGSEGV", ignoreCase = true) ||
                line.contains("SIGABRT", ignoreCase = true) ||
                line.contains("backtrace:", ignoreCase = true)
        val isError = isCrash || line.contains(" E ") || line.contains("ERROR", ignoreCase = true)
        val isEngine = line.contains("RetroEngine") || line.contains("RetroCore")

        val tag = RELEVANT_TAGS.firstOrNull { line.contains(it, ignoreCase = true) } ?: "System"
        val level = if (isCrash) "CRASH" else if (isError) "ERROR" else "INFO"
        val timestamp = line.take(18).trim()

        return GameLogEntry(
            timestamp = timestamp,
            level = level,
            tag = tag,
            message = line,
            isError = isError,
            isCrash = isCrash,
            isEngine = isEngine
        )
    }

    private fun parseDiskLogLine(line: String): GameLogEntry? {
        val isCrash = line.contains("CRASH", ignoreCase = true) || line.contains("SIG", ignoreCase = true)
        val isError = isCrash || line.contains("[ERROR]")
        val isEngine = true

        return GameLogEntry(
            timestamp = "",
            level = if (isCrash) "CRASH" else if (isError) "ERROR" else "INFO",
            tag = "RetroEngine",
            message = line,
            isError = isError,
            isCrash = isCrash,
            isEngine = isEngine
        )
    }

    private fun locateOnDiskLogs(context: Context, packageName: String): List<File> {
        val candidateFiles = mutableListOf<File>()

        // 1. /sdcard/Download/logs/
        val downloadLogs = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "logs")
        candidateFiles.add(File(downloadLogs, "game_launch.log"))
        candidateFiles.add(File(downloadLogs, "$packageName/launch.log"))
        candidateFiles.add(File(downloadLogs, "$packageName/crash.log"))
        candidateFiles.add(File(downloadLogs, "$packageName/$packageName.log"))

        // 2. App-specific external storage
        val extDataDir = File(context.getExternalFilesDir(null)?.parentFile?.parentFile, "$packageName/files")
        candidateFiles.add(File(extDataDir, "engine.log"))
        candidateFiles.add(File(extDataDir, "crash.log"))

        return candidateFiles.filter { it.exists() && it.length() > 0 }
    }
}
