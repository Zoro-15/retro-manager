package com.retropack.runtime.logging

import android.app.Service
import android.content.Context
import android.os.IBinder
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Autonomous background service capturing system and emulator logcat output continuously
 * while the standalone game application is running.
 */
class RuntimeLoggingService : Service() {

    companion object {
        private const val THREAD_NAME = "RetroPack-LogcatWorker"

        @Volatile
        private var isServiceRunning = false

        fun start(context: Context) {
            try {
                val intent = try {
                    val intentClass = Class.forName("android.content.Intent")
                    val constructor = intentClass.getConstructor(Context::class.java, Class::class.java)
                    constructor.newInstance(context, RuntimeLoggingService::class.java)
                } catch (_: Throwable) {
                    null
                }

                if (intent != null) {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                RuntimeLogger.w("LogService", "Could not start RuntimeLoggingService: ${t.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = try {
                    val intentClass = Class.forName("android.content.Intent")
                    val constructor = intentClass.getConstructor(Context::class.java, Class::class.java)
                    constructor.newInstance(context, RuntimeLoggingService::class.java)
                } catch (_: Throwable) {
                    null
                }

                if (intent != null) {
                    context.stopService(intent)
                }
            } catch (_: Throwable) {}
        }
    }

    private var logcatProcess: Process? = null
    private var workerThread: Thread? = null

    @Volatile
    private var shouldRun = false

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        shouldRun = true
        startLogcatStream()
    }

    override fun onStartCommand(intent: Any?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    private fun startLogcatStream() {
        workerThread = Thread({
            try {
                RuntimeLogger.i("LogService", "Logcat background collector stream started")
                val cmd = arrayOf(
                    "logcat",
                    "-v",
                    "time",
                    "-s",
                    "RetroPack:V",
                    "RetroRuntime:V",
                    "RetroPack-mGBA:V",
                    "mGBA:V",
                    "AndroidRuntime:E"
                )
                val process = Runtime.getRuntime().exec(cmd)
                logcatProcess = process

                BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (shouldRun && reader.readLine().also { line = it } != null) {
                        line?.let { l ->
                            RuntimeLogger.d("Logcat", l)
                        }
                    }
                }
            } catch (e: Exception) {
                RuntimeLogger.d("LogService", "Logcat collector terminated: ${e.message}")
            }
        }, THREAD_NAME).apply {
            isDaemon = true
            start()
        }
    }

    override fun onDestroy() {
        shouldRun = false
        isServiceRunning = false
        try {
            logcatProcess?.destroy()
            logcatProcess = null
        } catch (_: Throwable) {}

        try {
            workerThread?.interrupt()
            workerThread = null
        } catch (_: Throwable) {}

        RuntimeLogger.i("LogService", "Logcat background collector service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Any?): IBinder? = null
}
