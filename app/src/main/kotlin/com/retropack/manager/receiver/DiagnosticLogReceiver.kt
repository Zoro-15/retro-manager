package com.retropack.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import com.retropack.manager.util.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BroadcastReceiver receiving live runtime diagnostics and crash reports
 * from standalone RetroPack pure C++ NativeActivity game APKs.
 */
class DiagnosticLogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DIAGNOSTIC_LOG) return

        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: "com.retropack.game"
        val slug = intent.getStringExtra(EXTRA_SLUG) ?: pkg.substringAfterLast('.')
        val tag = intent.getStringExtra(EXTRA_TAG) ?: "RetroEngine"
        val level = intent.getStringExtra(EXTRA_LEVEL) ?: "INFO"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val isCrash = intent.getBooleanExtra(EXTRA_IS_CRASH, false)

        if (message.isBlank()) return

        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val formattedLine = "[$timeStamp] [$level] [$tag] $message\n"

        // 1. Write to manager internal private storage: /data/user/0/com.retropack.manager/files/logs/<slug>.log
        try {
            val internalLogsDir = File(context.filesDir, "logs").apply { mkdirs() }
            val internalLogFile = File(internalLogsDir, "$slug.log")
            internalLogFile.appendText(formattedLine, Charsets.UTF_8)

            if (isCrash) {
                val crashFile = File(internalLogsDir, "${slug}_crash.log")
                crashFile.appendText(formattedLine, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            AppLogger.w("DiagnosticLogReceiver", "Failed to write internal log: ${e.message}")
        }

        // 2. Write to public Downloads storage: /sdcard/Download/RetroPack/Logs/<slug>.log
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val retroPackLogsDir = File(downloadDir, "RetroPack/Logs").apply { mkdirs() }
            val publicLogFile = File(retroPackLogsDir, "$slug.log")
            publicLogFile.appendText(formattedLine, Charsets.UTF_8)

            if (isCrash) {
                val crashFile = File(retroPackLogsDir, "${slug}_crash.log")
                crashFile.appendText(formattedLine, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            // Public storage may be constrained on scoped storage
        }
    }

    companion object {
        const val ACTION_DIAGNOSTIC_LOG = "com.retropack.DIAGNOSTIC_LOG"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_SLUG = "slug"
        const val EXTRA_TAG = "tag"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_IS_CRASH = "isCrash"
    }
}
