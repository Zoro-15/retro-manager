package com.retropack.manager.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AppLoggerTest {

    @Test
    fun `AppLogger writes logs and reads latest session log text`(@TempDir tempDir: File) {
        AppLogger.init(tempDir, "com.retropack.manager")

        AppLogger.i("TestTag", "Testing info message")
        AppLogger.d("TestTag", "Testing debug message")
        AppLogger.w("TestTag", "Testing warning message")
        AppLogger.e("TestTag", "Testing error message", RuntimeException("Test Exception"))

        AppLogger.flush()

        val logFile = AppLogger.getLatestLogFile()
        assertNotNull(logFile)
        assertTrue(logFile!!.exists())

        val logContent = AppLogger.readLatestLogText()
        assertTrue(logContent.contains("RETROPACK MANAGER - SESSION DIAGNOSTIC LOG"))
        assertTrue(logContent.contains("Testing info message"))
        assertTrue(logContent.contains("Testing error message"))
        assertTrue(logContent.contains("Test Exception"))

        // Re-initializing simulates a new app launch: must purge old log and recreate fresh log
        AppLogger.clearLatestLog(baseDir = tempDir)
        val refreshedText = AppLogger.readLatestLogText()
        assertTrue(refreshedText.contains("RETROPACK MANAGER - SESSION DIAGNOSTIC LOG"))
        assertFalse(refreshedText.contains("Test Exception"))
    }
}

