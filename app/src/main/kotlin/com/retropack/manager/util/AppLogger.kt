package com.retropack.manager.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Diagnostic Session Logger for RetroPack Manager.
 *
 * Implements strict single-session persistence:
 * - On app launch, purges any previous logs and initializes a fresh `latest_session.log` in app data.
 * - Stores all operational, transformation, diagnostic, and crash logs for the current/last session.
 * - Automatically hooks into uncaught exceptions to guarantee crash logs are flushed to disk before process death.
 */
object AppLogger {

    private const val LOG_DIR_NAME = "logs"
    private const val SESSION_LOG_FILE = "latest_session.log"
    private const val TAG_PREFIX = "RetroPack"

    private val lock = Any()
    private var logFile: File? = null
    private var fileWriter: FileWriter? = null
    private var isInitialized = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    /**
     * Initializes the logger for the current application session using Context.
     * Overwrites previous session logs to guarantee only the latest session is retained in app data.
     */
    fun init(context: Context) {
        val baseDir = runCatching { context.filesDir }.getOrNull()
            ?: File("/data/data/${runCatching { context.packageName }.getOrDefault("com.retropack.manager")}/files")
        val pkg = runCatching { context.packageName }.getOrDefault("com.retropack.manager")
        init(baseDir, pkg)
    }

    /**
     * Initializes the logger using base files directory (for JVM unit tests or decoupled components).
     */
    fun init(baseDir: File, packageName: String = "com.retropack.manager") {
        synchronized(lock) {
            if (isInitialized) return

            try {
                val logsDir = File(baseDir, LOG_DIR_NAME).apply { mkdirs() }

                // Clean up any historical log files to strictly retain only the latest session
                logsDir.listFiles()?.forEach { file ->
                    runCatching { file.delete() }
                }

                val targetFile = File(logsDir, SESSION_LOG_FILE)
                logFile = targetFile
                fileWriter = FileWriter(targetFile, false) // Fresh overwrite

                writeHeader(packageName)
                setupCrashHandler()
                isInitialized = true

                i("AppLogger", "RetroPack Session Logger initialized. Storing latest session at: ${targetFile.absolutePath}")
            } catch (e: Exception) {
                runCatching { Log.e(TAG_PREFIX, "Failed to initialize AppLogger: ${e.message}", e) }
            }
        }
    }

    private fun writeHeader(packageName: String) {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTime = isoFormat.format(Date())
        val runtime = Runtime.getRuntime()
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)

        val osVersion = runCatching { "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }.getOrDefault("Android API (JVM Mock/Host)")
        val deviceModel = runCatching { "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE} / ${Build.PRODUCT})" }.getOrDefault("Generic Emulator/Host Device")
        val cpuAbis = runCatching { Build.SUPPORTED_ABIS.joinToString(", ") }.getOrDefault("arm64-v8a, x86_64")

        val header = buildString {
            appendLine("================================================================================")
            appendLine("                      RETROPACK MANAGER - SESSION DIAGNOSTIC LOG                ")
            appendLine("================================================================================")
            appendLine("Session Started      : $startTime")
            appendLine("Package Name         : $packageName")
            appendLine("Android OS Version   : $osVersion")
            appendLine("Device Model         : $deviceModel")
            appendLine("CPU ABIs             : $cpuAbis")
            appendLine("JVM Max / Total Mem  : $maxMemMb MB / $totalMemMb MB")
            appendLine("16 KB Page Alignment : Enforced")
            appendLine("================================================================================")
            appendLine()
        }
        writeRaw(header)
    }

    private fun setupCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                e("CRASH_HANDLER", "FATAL UNCAUGHT EXCEPTION on thread ${thread.name} (${thread.id}):", throwable)
                flush()
            } catch (_: Throwable) {
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message, null)
    }

    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val timestamp = dateFormat.format(Date())
        val fullTag = "$TAG_PREFIX/$tag"

        // Android logcat output (safe on host/JVM without stub crashes)
        runCatching {
            when (level) {
                LogLevel.DEBUG -> Log.d(fullTag, message, throwable)
                LogLevel.INFO -> Log.i(fullTag, message, throwable)
                LogLevel.WARN -> Log.w(fullTag, message, throwable)
                LogLevel.ERROR -> Log.e(fullTag, message, throwable)
            }
        }

        // File persistence output
        val formattedLog = buildString {
            append("[$timestamp] [${level.name}] [$tag] $message")
            if (throwable != null) {
                appendLine()
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                append(sw.toString().trimEnd())
            }
            appendLine()
        }

        writeRaw(formattedLog)
    }

    private fun writeRaw(text: String) {
        synchronized(lock) {
            try {
                fileWriter?.write(text)
                fileWriter?.flush()
            } catch (e: Exception) {
                runCatching { Log.e(TAG_PREFIX, "Error writing to session log: ${e.message}") }
            }
        }
    }

    fun flush() {
        synchronized(lock) {
            try {
                fileWriter?.flush()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Retrieves the latest session log file handle if available.
     */
    fun getLatestLogFile(context: Context? = null): File? {
        synchronized(lock) {
            if (logFile?.exists() == true) return logFile
            val baseDir = context?.filesDir ?: return null
            val file = File(File(baseDir, LOG_DIR_NAME), SESSION_LOG_FILE)
            return if (file.exists()) file else null
        }
    }

    /**
     * Reads the entire contents of the latest session log.
     */
    fun readLatestLogText(context: Context? = null): String {
        return getLatestLogFile(context)?.readText(Charsets.UTF_8)
            ?: "No session log recorded yet."
    }

    /**
     * Clears the session log file.
     */
    fun clearLatestLog(context: Context? = null, baseDir: File? = null) {
        synchronized(lock) {
            try {
                val targetDir = baseDir ?: context?.filesDir ?: logFile?.parentFile?.parentFile
                getLatestLogFile(context)?.delete()
                fileWriter?.close()
                fileWriter = null
                isInitialized = false
                if (targetDir != null) {
                    val pkg = context?.let { runCatching { it.packageName }.getOrNull() } ?: "com.retropack.manager"
                    init(targetDir, pkg)
                }
            } catch (e: Exception) {
                runCatching { Log.e(TAG_PREFIX, "Failed to clear session log: ${e.message}") }
            }
        }
    }

    private enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
}
