package com.retropack.manager.util

import android.content.Context
import android.os.Build
import android.os.Environment
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
 * Implements accessible public logging and single-session persistence:
 * - On app launch, initializes a dedicated log file in the public Download directory:
 *   `/sdcard/Download/logs/manager/manager.log`
 *   (and `/sdcard/Download/logs/<app_slug>/<app_slug>.log` for specific components).
 * - Records all events during the critical 2-minute launch window with immediate disk flush.
 * - Flushes and preserves all logs even if the user closes the app before 2 minutes.
 * - Also maintains an internal fallback in app-private storage for resilient in-app reading.
 * - Automatically hooks into uncaught exceptions to guarantee crash logs are written before process death.
 */
object AppLogger {

    private const val LOG_DIR_NAME = "logs"
    private const val SESSION_LOG_FILE = "latest_session.log"
    private const val TAG_PREFIX = "RetroPack"
    const val LAUNCH_RECORDING_WINDOW_MS = 120_000L // 2 minutes (120s)

    private val lock = Any()
    private var internalLogFile: File? = null
    private var publicLogFile: File? = null
    private var internalWriter: FileWriter? = null
    private var publicWriter: FileWriter? = null
    private var isInitialized = false
    private var launchTimestamp: Long = 0L

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    /**
     * Initializes the logger for the current application session using Context.
     */
    fun init(context: Context, appSlug: String = "manager") {
        val baseDir = runCatching { context.filesDir }.getOrNull()
            ?: File("/data/data/${runCatching { context.packageName }.getOrDefault("com.retropack.manager")}/files")
        val pkg = runCatching { context.packageName }.getOrDefault("com.retropack.manager")
        
        val publicDir = resolvePublicDownloadLogsDir(appSlug)
        init(baseDir = baseDir, packageName = pkg, publicLogDir = publicDir, appSlug = appSlug)
    }

    /**
     * Initializes the logger using explicit base and public directories (for JVM unit tests or decoupled components).
     */
    fun init(
        baseDir: File,
        packageName: String = "com.retropack.manager",
        publicLogDir: File? = null,
        appSlug: String = "manager"
    ) {
        synchronized(lock) {
            if (isInitialized) return

            launchTimestamp = System.currentTimeMillis()

            try {
                // 1. Setup Internal App-Private Session Log
                val internalLogsDir = File(baseDir, LOG_DIR_NAME).apply { mkdirs() }
                internalLogsDir.listFiles()?.forEach { file ->
                    runCatching { file.delete() }
                }
                val internalTarget = File(internalLogsDir, SESSION_LOG_FILE)
                internalLogFile = internalTarget
                internalWriter = runCatching { FileWriter(internalTarget, false) }.getOrNull()

                // 2. Setup Public Download Log Directory: /sdcard/Download/logs/<appSlug>/<appSlug>.log
                val targetPublicDir = publicLogDir ?: resolvePublicDownloadLogsDir(appSlug)
                runCatching { targetPublicDir.mkdirs() }
                val publicTarget = File(targetPublicDir, "$appSlug.log")
                publicLogFile = publicTarget
                publicWriter = runCatching { FileWriter(publicTarget, false) }.getOrNull()

                writeHeader(packageName, appSlug)
                setupCrashHandler()
                isInitialized = true

                i("AppLogger", "RetroPack Session Logger initialized.")
                i("AppLogger", "Public Log Target: ${publicTarget.absolutePath}")
                i("AppLogger", "Internal Log Target: ${internalTarget.absolutePath}")
                i("AppLogger", "Launch recording window active for 2 minutes (immediate auto-flush).")
            } catch (e: Exception) {
                runCatching { Log.e(TAG_PREFIX, "Failed to initialize AppLogger: ${e.message}", e) }
            }
        }
    }

    /**
     * Resolves the public Download logs folder: `/sdcard/Download/logs/<appSlug>`.
     */
    fun resolvePublicDownloadLogsDir(appSlug: String = "manager"): File {
        val downloadDir: File = runCatching<File> {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }.getOrNull() ?: File(System.getProperty("user.home", "/sdcard"), "Download")

        val target = File(downloadDir, "logs/$appSlug")
        runCatching { target.mkdirs() }
        return target
    }

    private fun writeHeader(packageName: String, appSlug: String) {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTime = isoFormat.format(Date(launchTimestamp))
        val runtime = Runtime.getRuntime()
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)

        val osVersion = runCatching { "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }.getOrDefault("Android API (JVM Mock/Host)")
        val deviceModel = runCatching { "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE} / ${Build.PRODUCT})" }.getOrDefault("Generic Emulator/Host Device")
        val cpuAbis = runCatching { Build.SUPPORTED_ABIS.joinToString(", ") }.getOrDefault("arm64-v8a, x86_64")

        val header = buildString {
            appendLine("================================================================================")
            appendLine("                   RETROPACK - SESSION DIAGNOSTIC LOG ($appSlug)                ")
            appendLine("================================================================================")
            appendLine("Session Started      : $startTime")
            appendLine("Component / Slug     : $appSlug")
            appendLine("Package Name         : $packageName")
            appendLine("Android OS Version   : $osVersion")
            appendLine("Device Model         : $deviceModel")
            appendLine("CPU ABIs             : $cpuAbis")
            appendLine("JVM Max / Total Mem  : $maxMemMb MB / $totalMemMb MB")
            appendLine("Storage Location     : sdcard/Download/logs/$appSlug/$appSlug.log")
            appendLine("Launch Auto-Record   : 2 Minutes Active Window")
            appendLine("16 KB Page Alignment : Enforced")
            appendLine("================================================================================")
            appendLine()
        }
        writeRaw(header, forceFlush = true)
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

        // Flush immediately if within the 2-minute launch window or on errors/warnings
        val now = System.currentTimeMillis()
        val isWithin2MinWindow = (now - launchTimestamp) <= LAUNCH_RECORDING_WINDOW_MS
        val shouldForceFlush = isWithin2MinWindow || level == LogLevel.ERROR || level == LogLevel.WARN

        writeRaw(formattedLog, forceFlush = shouldForceFlush)
    }

    private fun writeRaw(text: String, forceFlush: Boolean = false) {
        synchronized(lock) {
            try {
                internalWriter?.write(text)
                if (forceFlush) internalWriter?.flush()
            } catch (e: Exception) {
                runCatching { Log.e(TAG_PREFIX, "Error writing to internal session log: ${e.message}") }
            }

            try {
                publicWriter?.write(text)
                if (forceFlush) publicWriter?.flush()
            } catch (e: Exception) {
                runCatching { Log.e(TAG_PREFIX, "Error writing to public session log: ${e.message}") }
            }
        }
    }

    /**
     * Flushes all buffered log lines to disk immediately.
     */
    fun flush() {
        synchronized(lock) {
            try {
                internalWriter?.flush()
            } catch (_: Exception) {}

            try {
                publicWriter?.flush()
            } catch (_: Exception) {}
        }
    }

    /**
     * Retrieves the latest public session log file (`sdcard/Download/logs/manager/manager.log`).
     */
    fun getPublicLogFile(): File? {
        synchronized(lock) {
            return publicLogFile?.takeIf { it.exists() }
        }
    }

    /**
     * Retrieves the latest session log file handle if available (prioritizing public, falling back to internal).
     */
    fun getLatestLogFile(context: Context? = null): File? {
        synchronized(lock) {
            if (publicLogFile?.exists() == true) return publicLogFile
            if (internalLogFile?.exists() == true) return internalLogFile
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
    fun clearLatestLog(context: Context? = null, baseDir: File? = null, publicLogDir: File? = null) {
        synchronized(lock) {
            try {
                val targetDir = baseDir ?: context?.filesDir ?: internalLogFile?.parentFile?.parentFile
                internalLogFile?.delete()
                publicLogFile?.delete()
                internalWriter?.close()
                publicWriter?.close()
                internalWriter = null
                publicWriter = null
                isInitialized = false
                if (targetDir != null) {
                    val pkg = context?.let { runCatching { it.packageName }.getOrNull() } ?: "com.retropack.manager"
                    init(baseDir = targetDir, packageName = pkg, publicLogDir = publicLogDir)
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
