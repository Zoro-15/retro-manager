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
     * Initializes the logger for the current application session.
     * Overwrites previous session logs to guarantee only the latest session is retained in app data.
     */
    fun init(context: Context) {
        synchronized(lock) {
            if (isInitialized) return

            try {
                val baseDir = context.filesDir ?: File("/data/data/${context.packageName}/files")
                val logsDir = File(baseDir, LOG_DIR_NAME).apply { mkdirs() }

                // Clean up any historical log files to strictly retain only the latest session
                logsDir.listFiles()?.forEach { file ->
                    runCatching { file.delete() }
                }

                logFile = File(logsDir, SESSION_LOG_FILE)
                fileWriter = FileWriter(logFile, false) // Fresh overwrite

                writeHeader(context)
                setupCrashHandler()
                isInitialized = true

                i("AppLogger", "RetroPack Session Logger initialized. Storing latest session at: ${logFile?.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG_PREFIX, "Failed to initialize AppLogger: ${e.message}", e)
            }
        }
    }

    private fun writeHeader(context: Context) {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTime = isoFormat.format(Date())
        val runtime = Runtime.getRuntime()
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)

        val header = buildString {
            appendLine("================================================================================")
            appendLine("                      RETROPACK MANAGER - SESSION DIAGNOSTIC LOG                ")
            appendLine("================================================================================")
            appendLine("Session Started      : $startTime")
            appendLine("Package Name         : ${context.packageName}")
            appendLine("Android OS Version   : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device Model         : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE} / ${Build.PRODUCT})")
            appendLine("CPU ABIs             : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
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

        // Android logcat output
        when (level) {
            LogLevel.DEBUG -> Log.d(fullTag, message, throwable)
            LogLevel.INFO -> Log.i(fullTag, message, throwable)
            LogLevel.WARN -> Log.w(fullTag, message, throwable)
            LogLevel.ERROR -> Log.e(fullTag, message, throwable)
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
                Log.e(TAG_PREFIX, "Error writing to session log: ${e.message}")
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
    fun getLatestLogFile(context: Context): File? {
        synchronized(lock) {
            val baseDir = context.filesDir ?: return null
            val file = File(File(baseDir, LOG_DIR_NAME), SESSION_LOG_FILE)
            return if (file.exists()) file else null
        }
    }

    /**
     * Reads the entire contents of the latest session log.
     */
    fun readLatestLogText(context: Context): String {
        return getLatestLogFile(context)?.readText(Charsets.UTF_8)
            ?: "No session log recorded yet."
    }

    /**
     * Clears the session log file.
     */
    fun clearLatestLog(context: Context) {
        synchronized(lock) {
            try {
                getLatestLogFile(context)?.delete()
                fileWriter?.close()
                fileWriter = null
                isInitialized = false
                init(context)
            } catch (e: Exception) {
                Log.e(TAG_PREFIX, "Failed to clear session log: ${e.message}")
            }
        }
    }

    private enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
}
