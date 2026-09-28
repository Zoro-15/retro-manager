package com.retropack.runtime.logging

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * High-reliability, crash-consistent diagnostic logger for the RetroPack standalone runtime.
 *
 * Implements accessible public logging and single-session persistence:
 * - On game launch, initializes a dedicated log file in the public Download directory:
 *   `/sdcard/Download/logs/<app_slug>/<app_slug>.log`
 *   (e.g., `/sdcard/Download/logs/tobu_tobu_girl/tobu_tobu_girl.log`).
 * - Records all startup, ROM staging, JNI bridge calls, frame pacing, and errors during the
 *   critical 2-minute launch window with immediate disk flush.
 * - Flushes and preserves all logs even if the user closes the game before 2 minutes.
 * - Also maintains an internal fallback in app-specific storage.
 * - Automatically hooks into uncaught exceptions to guarantee crash logs are written before process death.
 */
object RuntimeLogger {

    const val LOG_FILENAME = "retropack_runtime.log"
    const val LAUNCH_RECORDING_WINDOW_MS = 120_000L // 2 minutes (120s)
    private const val MAX_MEMORY_LINES = 500

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val memoryLogBuffer = ConcurrentLinkedQueue<String>()

    @Volatile
    private var activeInternalLogFile: File? = null

    @Volatile
    private var activePublicLogFile: File? = null

    @Volatile
    private var internalWriter: BufferedWriter? = null

    @Volatile
    private var publicWriter: BufferedWriter? = null

    @Volatile
    private var defaultUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    @Volatile
    private var isInitialized = false

    @Volatile
    private var launchTimestamp: Long = 0L

    val logFile: File?
        get() = activePublicLogFile ?: activeInternalLogFile

    val publicLogFile: File?
        get() = activePublicLogFile

    val internalLogFile: File?
        get() = activeInternalLogFile

    /**
     * Initializes continuous file logging for the runtime host.
     */
    fun start(context: Context) = init(context)

    /**
     * Resolves the public Download logs folder: `/sdcard/Download/logs/<appSlug>`.
     */
    fun resolvePublicDownloadLogsDir(appSlug: String): File {
        val downloadDir: File = runCatching<File> {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }.getOrNull() ?: File(System.getProperty("user.home", "/sdcard"), "Download")

        val target = File(downloadDir, "logs/$appSlug")
        runCatching { target.mkdirs() }
        return target
    }

    private fun deriveAppSlug(context: Context?): String {
        if (context == null) return "game"
        val pkg = runCatching { context.packageName }.getOrNull() ?: return "game"
        return when {
            pkg.contains("manager") -> "manager"
            pkg.contains("retropack") -> pkg.substringAfterLast(".").ifEmpty { "game" }
            else -> pkg.substringAfterLast(".").ifEmpty { "game" }
        }
    }

    @Synchronized
    fun init(context: Context? = null, logDir: File? = null, publicLogDir: File? = null, slug: String? = null) {
        if (isInitialized) return

        launchTimestamp = System.currentTimeMillis()
        val appSlug = slug ?: deriveAppSlug(context)

        try {
            // 1. Internal / App-specific logging destination
            val targetDir = logDir ?: context?.let { it.getExternalFilesDir(null) ?: it.filesDir } ?: File(System.getProperty("java.io.tmpdir"), "retropack_logs")
            targetDir.mkdirs()
            val internalFile = File(targetDir, LOG_FILENAME)
            activeInternalLogFile = internalFile
            internalWriter = BufferedWriter(FileWriter(internalFile, false), 8192)

            // 2. Public Download logging destination: /sdcard/Download/logs/<appSlug>/<appSlug>.log
            val targetPublicDir = publicLogDir ?: resolvePublicDownloadLogsDir(appSlug)
            runCatching { targetPublicDir.mkdirs() }
            val publicFile = File(targetPublicDir, "$appSlug.log")
            activePublicLogFile = publicFile
            publicWriter = runCatching { BufferedWriter(FileWriter(publicFile, false), 8192) }.getOrNull()

            isInitialized = true

            // Install crash trap
            defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                e("CrashHandler", "FATAL UNCAUGHT EXCEPTION on thread '${thread.name}'", throwable)
                flush()
                defaultUncaughtHandler?.uncaughtException(thread, throwable)
            }

            // Write diagnostic header
            i("System", "==================================================")
            i("System", "RETROPACK STANDALONE RUNTIME DIAGNOSTIC LOG ($appSlug)")
            i("System", "Started At: ${dateFormat.format(Date(launchTimestamp))}")
            i("System", "Package: ${context?.packageName ?: "com.retropack.runtime"}")
            try {
                i("System", "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android SDK ${Build.VERSION.SDK_INT})")
                i("System", "Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            } catch (_: Throwable) {
                // JVM stub environment
            }
            i("System", "Internal FilesDir: ${context?.filesDir?.absolutePath ?: targetDir.absolutePath}")
            i("System", "Public Log Path: ${publicFile.absolutePath}")
            i("System", "Internal Log Path: ${internalFile.absolutePath}")
            i("System", "Launch Auto-Record: 2 Minutes Active Window")
            i("System", "==================================================")

            // Start background logcat service if running on Android
            if (context != null) {
                try {
                    RuntimeLoggingService.start(context)
                } catch (_: Throwable) {
                    // Best effort in test environments
                }
            }
        } catch (t: Throwable) {
            System.err.println("RuntimeLogger failed to initialize: ${t.message}")
        }
    }

    fun d(tag: String, message: String) = log("DEBUG", tag, message, null)
    fun i(tag: String, message: String) = log("INFO", tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log("WARN", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log("ERROR", tag, message, throwable)

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val formatted = "[$timestamp] [$level/$tag] $message"

        println(formatted)

        // Keep rolling memory buffer
        memoryLogBuffer.add(formatted)
        while (memoryLogBuffer.size > MAX_MEMORY_LINES) {
            memoryLogBuffer.poll()
        }

        val now = System.currentTimeMillis()
        val isWithin2MinWindow = (now - launchTimestamp) <= LAUNCH_RECORDING_WINDOW_MS
        val shouldForceFlush = isWithin2MinWindow || level == "ERROR" || level == "WARN"

        synchronized(this) {
            try {
                internalWriter?.let { w ->
                    w.write(formatted)
                    w.newLine()
                    if (throwable != null) {
                        val sw = StringWriter()
                        throwable.printStackTrace(PrintWriter(sw))
                        val traceStr = sw.toString()
                        w.write(traceStr)
                        if (!traceStr.endsWith("\n")) w.newLine()
                    }
                    if (shouldForceFlush) w.flush()
                }
            } catch (_: Throwable) {}

            try {
                publicWriter?.let { pw ->
                    pw.write(formatted)
                    pw.newLine()
                    if (throwable != null) {
                        val sw = StringWriter()
                        throwable.printStackTrace(PrintWriter(sw))
                        val traceStr = sw.toString()
                        pw.write(traceStr)
                        if (!traceStr.endsWith("\n")) pw.newLine()
                    }
                    if (shouldForceFlush) pw.flush()
                }
            } catch (_: Throwable) {}
        }
    }

    fun getRecentLogs(): List<String> {
        return memoryLogBuffer.toList()
    }

    @Synchronized
    fun flush() {
        try {
            internalWriter?.flush()
        } catch (_: Throwable) {}

        try {
            publicWriter?.flush()
        } catch (_: Throwable) {}
    }

    @Synchronized
    fun stop(context: Context? = null) {
        if (!isInitialized) return
        try {
            i("System", "==================================================")
            i("System", "RETROPACK RUNTIME SHUTTING DOWN NORMALLY")
            i("System", "==================================================")
            flush()
            internalWriter?.close()
            publicWriter?.close()
            internalWriter = null
            publicWriter = null

            if (context != null) {
                try {
                    RuntimeLoggingService.stop(context)
                } catch (_: Throwable) {}
            }

            if (defaultUncaughtHandler != null) {
                Thread.setDefaultUncaughtExceptionHandler(defaultUncaughtHandler)
                defaultUncaughtHandler = null
            }
        } catch (_: Throwable) {
        } finally {
            isInitialized = false
        }
    }
}
