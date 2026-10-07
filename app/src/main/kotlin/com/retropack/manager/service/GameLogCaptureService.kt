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
 * Diagnostic log entry parsed from logcat or native engine persistent logs.
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
 * Structured crash insight extracted from native crash diagnostics.
 */
data class CrashDiagnostic(
    val signal: String,
    val description: String,
    val faultAddress: String? = null,
    val programCounter: String? = null,
    val linkRegister: String? = null,
    val faultingModule: String? = null,
    val rawBacktrace: List<String> = emptyList()
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
    val hasErrors: Boolean,
    val crashDiagnostic: CrashDiagnostic? = null
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
        "RetroEngine-Crash",
        "RetroEngine",
        "RetroCore",
        "NativeActivity",
        "AndroidRuntime",
        "DEBUG",
        "libc"
    )

    private val EXCLUDED_PATTERNS = listOf(
        "Compiler allocated",
        "ImeTracker",
        "onCancelled at PHASE",
        "com.retropack.manager.ui"
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
        val appSlug = game.packageName.substringAfterLast('.')

        // 1. Read persistent on-disk engine logs first (most accurate for pure C++ NativeActivity)
        val onDiskFiles = locateOnDiskLogs(context, game.packageName, game.gameTitle)
        for (file in onDiskFiles) {
            try {
                if (file.exists() && file.length() > 0) {
                    val fileLines = file.readLines(Charsets.UTF_8)
                    collectedLines.add("\n==================================================")
                    collectedLines.add("--- [On-Disk Diagnostic Log: ${file.name}] ---")
                    collectedLines.add("==================================================")
                    for (fLine in fileLines) {
                        collectedLines.add(fLine)
                        parseDiskLogLine(fLine)?.let { parsedEntries.add(it) }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("GameLogCaptureService", "Could not read file ${file.absolutePath}: ${e.message}")
            }
        }

        // 2. Read on-device system logcat buffer
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", maxLines.toString()))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var capturedLogcatCount = 0

            val logcatLines = mutableListOf<String>()
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (isRelevantLogLine(currentLine, game.packageName, appSlug)) {
                    logcatLines.add(currentLine)
                    parseLogcatLine(currentLine)?.let { parsedEntries.add(it) }
                    capturedLogcatCount++
                }
            }
            reader.close()
            process.waitFor()

            if (logcatLines.isNotEmpty()) {
                if (collectedLines.isNotEmpty()) {
                    collectedLines.add("\n==================================================")
                    collectedLines.add("--- [System Logcat Stream ($capturedLogcatCount events)] ---")
                    collectedLines.add("==================================================")
                }
                collectedLines.addAll(logcatLines)
            }
        } catch (e: Exception) {
            AppLogger.w("GameLogCaptureService", "Could not read logcat buffer: ${e.message}")
        }

        val rawText = if (collectedLines.isNotEmpty()) {
            collectedLines.joinToString("\n")
        } else {
            "[No runtime logs detected for ${game.packageName}]\n\n" +
            "How to capture live diagnostics:\n" +
            "1. Tap 'Launch & Record' below to start the game.\n" +
            "2. RetroPack Manager will monitor startup and record native diagnostics directly."
        }

        // Extract crash diagnostics
        val crashDiag = extractCrashDiagnostic(rawText, parsedEntries)

        val hasCrash = crashDiag != null ||
                parsedEntries.any { it.isCrash } ||
                rawText.contains("FATAL CRASH", ignoreCase = true) ||
                rawText.contains("SIGSEGV", ignoreCase = true) ||
                rawText.contains("SIGABRT", ignoreCase = true) ||
                rawText.contains("dlopen failed", ignoreCase = true)

        val hasErrors = hasCrash || parsedEntries.any { it.isError } || rawText.contains("ERROR", ignoreCase = true)

        val summary = when {
            crashDiag != null -> "CRASH DETECTED: ${crashDiag.signal} - ${crashDiag.description}"
            hasCrash -> "CRASH DETECTED: Fatal signal or library loading failure."
            hasErrors -> "WARNINGS/ERRORS: Native engine reported initialization errors."
            parsedEntries.any { it.message.contains("Booting", ignoreCase = true) || it.message.contains("Frame timing", ignoreCase = true) } ->
                "BOOT OK: Pure C++ NativeActivity running successfully."
            else -> "IDLE: No recent activity recorded."
        }

        GameLogResult(
            game = game,
            timestamp = System.currentTimeMillis(),
            entries = parsedEntries,
            rawText = rawText,
            summary = summary,
            hasCrash = hasCrash,
            hasErrors = hasErrors,
            crashDiagnostic = crashDiag
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

    private fun isRelevantLogLine(line: String, packageName: String, appSlug: String): Boolean {
        // Exclude internal UI / Compose compiler clutter
        for (excluded in EXCLUDED_PATTERNS) {
            if (line.contains(excluded, ignoreCase = true)) return false
        }

        if (line.contains(packageName, ignoreCase = true)) return true
        if (appSlug.length > 3 && line.contains(appSlug, ignoreCase = true)) return true

        for (tag in RELEVANT_TAGS) {
            if (line.contains(tag, ignoreCase = true)) return true
        }

        // Catch low-level dynamic linker and SIGSEGV events
        if (line.contains("dlopen", ignoreCase = true) ||
            line.contains("SIGSEGV", ignoreCase = true) ||
            line.contains("SIGABRT", ignoreCase = true) ||
            line.contains("FATAL", ignoreCase = true)
        ) {
            return true
        }

        return false
    }

    private fun parseLogcatLine(line: String): GameLogEntry? {
        val isCrash = line.contains("FATAL", ignoreCase = true) ||
                line.contains("SIGSEGV", ignoreCase = true) ||
                line.contains("SIGABRT", ignoreCase = true) ||
                line.contains("backtrace:", ignoreCase = true) ||
                line.contains("dlopen failed", ignoreCase = true)
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

    private fun extractCrashDiagnostic(rawText: String, entries: List<GameLogEntry>): CrashDiagnostic? {
        if (!rawText.contains("SIG", ignoreCase = true) &&
            !rawText.contains("FATAL", ignoreCase = true) &&
            !rawText.contains("dlopen failed", ignoreCase = true)
        ) {
            return null
        }

        var signal = "FATAL_ERROR"
        var description = "Native runtime crash or initialization failure."
        var faultAddr: String? = null
        var pc: String? = null
        var lr: String? = null
        var module: String? = null
        val backtrace = mutableListOf<String>()

        val lines = rawText.lines()
        for (line in lines) {
            if (line.contains("FATAL CRASH DETECTED:", ignoreCase = true)) {
                signal = line.substringAfter("FATAL CRASH DETECTED:").trim()
            } else if (line.contains("Description   :", ignoreCase = true)) {
                description = line.substringAfter("Description   :").trim()
            } else if (line.contains("Fault Address :", ignoreCase = true)) {
                faultAddr = line.substringAfter("Fault Address :").trim()
            } else if (line.contains("Program Count :", ignoreCase = true)) {
                pc = line.substringAfter("Program Count :").trim()
                if (pc.contains("->")) {
                    module = pc.substringAfter("->").trim()
                }
            } else if (line.contains("Link Register :", ignoreCase = true)) {
                lr = line.substringAfter("Link Register :").trim()
            } else if (line.contains("dlopen failed:", ignoreCase = true)) {
                signal = "DLOPEN_FAILURE"
                description = line.substringAfter("dlopen failed:").trim()
            } else if (line.contains("#00 pc") || line.contains("#01 pc") || line.contains("backtrace:")) {
                backtrace.add(line.trim())
            }
        }

        return CrashDiagnostic(
            signal = signal,
            description = description,
            faultAddress = faultAddr,
            programCounter = pc,
            linkRegister = lr,
            faultingModule = module,
            rawBacktrace = backtrace
        )
    }

    private fun locateOnDiskLogs(context: Context, packageName: String, gameTitle: String): List<File> {
        val candidateFiles = mutableListOf<File>()
        val appSlug = packageName.substringAfterLast('.')
        val safeTitle = gameTitle.replace(Regex("[^a-zA-Z0-9_]"), "_")

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val retroPackLogsDir = File(downloadDir, "RetroPack/Logs")
        val generalLogsDir = File(downloadDir, "logs")

        // 1. /sdcard/Download/RetroPack/Logs/
        candidateFiles.add(File(retroPackLogsDir, "$appSlug.log"))
        candidateFiles.add(File(retroPackLogsDir, "${appSlug}_crash.log"))
        candidateFiles.add(File(retroPackLogsDir, "$packageName.log"))
        candidateFiles.add(File(retroPackLogsDir, "${safeTitle}.log"))

        // Find any timestamped exports
        retroPackLogsDir.listFiles()?.filter {
            it.name.startsWith(safeTitle, ignoreCase = true) ||
            it.name.startsWith(appSlug, ignoreCase = true) ||
            it.name.startsWith(packageName, ignoreCase = true)
        }?.let { candidateFiles.addAll(it) }

        // 2. /sdcard/Download/logs/
        candidateFiles.add(File(generalLogsDir, "$appSlug.log"))
        candidateFiles.add(File(generalLogsDir, "$appSlug/crash.log"))
        candidateFiles.add(File(generalLogsDir, "$appSlug/$appSlug.log"))
        candidateFiles.add(File(generalLogsDir, "$packageName/launch.log"))
        candidateFiles.add(File(generalLogsDir, "$packageName/crash.log"))
        candidateFiles.add(File(generalLogsDir, "$packageName/$packageName.log"))
        candidateFiles.add(File(generalLogsDir, "game_launch.log"))

        // 3. App-specific external storage
        try {
            val extDataDir = File(context.getExternalFilesDir(null)?.parentFile?.parentFile, "$packageName/files")
            candidateFiles.add(File(extDataDir, "engine.log"))
            candidateFiles.add(File(extDataDir, "crash.log"))
            candidateFiles.add(File(extDataDir, "$appSlug.log"))
            candidateFiles.add(File(extDataDir, "launch.log"))
        } catch (_: Exception) {}

        return candidateFiles.filter { it.exists() && it.length() > 0 }.distinctBy { it.absolutePath }
    }
}
