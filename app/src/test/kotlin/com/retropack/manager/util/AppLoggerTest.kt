package com.retropack.manager.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AppLoggerTest {

    @Test
    fun `AppLogger writes logs and reads latest session log text`(@TempDir tempDir: File) {
        val publicLogDir = File(tempDir, "public_logs/manager")
        AppLogger.init(
            baseDir = tempDir,
            packageName = "com.retropack.manager",
            publicLogDir = publicLogDir,
            appSlug = "manager"
        )

        AppLogger.i("TestTag", "Testing info message")
        AppLogger.d("TestTag", "Testing debug message")
        AppLogger.w("TestTag", "Testing warning message")
        AppLogger.e("TestTag", "Testing error message", RuntimeException("Test Exception"))

        AppLogger.flush()

        val logFile = AppLogger.getLatestLogFile()
        assertNotNull(logFile)
        assertTrue(logFile!!.exists())

        val publicFile = AppLogger.getPublicLogFile()
        assertNotNull(publicFile)
        assertTrue(publicFile!!.exists())
        assertEquals("manager.log", publicFile!!.name)

        val logContent = AppLogger.readLatestLogText()
        assertTrue(logContent.contains("RETROPACK - SESSION DIAGNOSTIC LOG"))
        assertTrue(logContent.contains("Testing info message"))
        assertTrue(logContent.contains("Testing error message"))
        assertTrue(logContent.contains("Test Exception"))

        // Re-initializing simulates a new app launch: must purge old log and recreate fresh log
        AppLogger.clearLatestLog(baseDir = tempDir, publicLogDir = publicLogDir)
        val refreshedText = AppLogger.readLatestLogText()
        assertTrue(refreshedText.contains("RETROPACK - SESSION DIAGNOSTIC LOG"))
        assertFalse(refreshedText.contains("Test Exception"))
    }

    @Test
    fun `AppLogger resolves public download logs directory structure`() {
        val dir = AppLogger.resolvePublicDownloadLogsDir("manager")
        assertTrue(dir.path.replace("\\", "/").contains("logs/manager"))
    }
}
