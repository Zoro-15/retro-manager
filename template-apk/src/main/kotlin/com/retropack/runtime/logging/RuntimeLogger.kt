package com.retropack.runtime.logging

import android.content.Context
import android.os.Build
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
 * Captures lifecycle milestones, ROM staging status, JNI bridge calls, OpenGL frame pacing,
 * and uncaught exceptions directly to a persistent, shareable log file on disk.
 */
object RuntimeLogger {

    const val LOG_FILENAME = "retropack_runtime.log"
    private const val MAX_MEMORY_LINES = 500

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val memoryLogBuffer = ConcurrentLinkedQueue<String>()

    @Volatile
    private var activeLogFile: File? = null

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var defaultUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    @Volatile
    private var isInitialized = false

    val logFile: File?
        get() = activeLogFile

    /**
     * Initializes continuous file logging for the runtime host.
     */
    @Synchronized
    fun init(context: Context, logDir: File? = null) {
        if (isInitialized) return

        try {
            val targetDir = logDir ?: (context.getExternalFilesDir(null) ?: context.filesDir)
            targetDir.mkdirs()
            val file = File(targetDir, LOG_FILENAME)
            activeLogFile = file

            writer = BufferedWriter(FileWriter(file, false), 8192)
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
            i("System", "RETROPACK STANDALONE RUNTIME DIAGNOSTIC LOG")
            i("System", "Started At: ${dateFormat.format(Date())}")
            i("System", "Package: ${context.packageName}")
            try {
                i("System", "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android SDK ${Build.VERSION.SDK_INT})")
                i("System", "Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            } catch (_: Throwable) {
                // JVM stub environment
            }
            i("System", "Internal FilesDir: ${context.filesDir.absolutePath}")
            i("System", "Target Log Path: ${file.absolutePath}")
            i("System", "==================================================")

            // Start background logcat service if running on Android
            try {
                RuntimeLoggingService.start(context)
            } catch (_: Throwable) {
                // Best effort in test environments
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

        synchronized(this) {
            try {
                writer?.let { w ->
                    w.write(formatted)
                    w.newLine()
                    if (throwable != null) {
                        val sw = StringWriter()
                        throwable.printStackTrace(PrintWriter(sw))
                        val traceStr = sw.toString()
                        w.write(traceStr)
                        if (!traceStr.endsWith("\n")) w.newLine()
                    }
                    w.flush()
                }
            } catch (_: Throwable) {
                // Fall back to stderr
            }
        }
    }

    fun getRecentLogs(): List<String> {
        return memoryLogBuffer.toList()
    }

    @Synchronized
    fun flush() {
        try {
            writer?.flush()
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
            writer?.close()
            writer = null

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
