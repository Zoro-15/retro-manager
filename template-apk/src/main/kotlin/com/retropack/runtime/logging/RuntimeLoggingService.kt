package com.retropack.runtime.logging

import android.app.Service
import android.content.Context
import android.content.Intent
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

        fun start(context: Context) {
            try {
                val intent = Intent(context, RuntimeLoggingService::class.java)
                context.startService(intent)
            } catch (t: Throwable) {
                RuntimeLogger.w("LogService", "Could not start RuntimeLoggingService: ${t.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, RuntimeLoggingService::class.java)
                context.stopService(intent)
            } catch (_: Throwable) {}
        }
    }

    private var logcatProcess: Process? = null
    private var workerThread: Thread? = null

    @Volatile
    private var shouldRun = false

    override fun onCreate() {
        super.onCreate()
        shouldRun = true
        startLogcatStream()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    private fun isProcessAlive(p: Process): Boolean {
        return try {
            p.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }

    private fun startLogcatStream() {
        workerThread = Thread({
            try {
                val myPid = android.os.Process.myPid()
                RuntimeLogger.i("LogService", "Logcat background collector stream starting for PID $myPid")

                var process: Process? = null
                try {
                    val pidCmd = arrayOf(
                        "logcat",
                        "--pid=$myPid",
                        "-v",
                        "time"
                    )
                    process = Runtime.getRuntime().exec(pidCmd)
                } catch (_: Throwable) {
                    process = null
                }

                if (process == null || !isProcessAlive(process)) {
                    val fallbackCmd = arrayOf(
                        "logcat",
                        "-v",
                        "time",
                        "-s",
                        "RetroPack-LibretroHost:V",
                        "RetroPack:V",
                        "RetroRuntime:V",
                        "RetroPack-EmulationHost:V",
                        "RetroPack-NativeCore:V",
                        "RetroPack-mGBA:V",
                        "mGBA:V",
                        "Mupen64Plus:V",
                        "Mupen64Plus-Next:V",
                        "PCSX:V",
                        "PCSX-ReARMed:V",
                        "Snes9x:V",
                        "GenesisPlusGX:V",
                        "FBNeo:V",
                        "melonDS:V",
                        "PPSSPP:V",
                        "DEBUG:E",
                        "DEBUG:F",
                        "linker:E",
                        "linker:W",
                        "AndroidRuntime:E",
                        "Adreno:E",
                        "Mali:E",
                        "OpenGLRenderer:E"
                    )
                    process = Runtime.getRuntime().exec(fallbackCmd)
                }
                logcatProcess = process

                BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String? = null
                    while (shouldRun && reader.readLine().also { line = it } != null) {
                        line?.let { l ->
                            if (!l.contains("[RetroPack-") && !l.contains("RETROPACK STANDALONE")) {
                                RuntimeLogger.d("Logcat", l)
                            }
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

    override fun onBind(intent: Intent?): IBinder? = null
}
