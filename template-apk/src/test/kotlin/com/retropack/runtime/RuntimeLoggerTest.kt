package com.retropack.runtime

import com.retropack.runtime.logging.RuntimeLogger
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RuntimeLoggerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun testRuntimeLoggerLifecycleAndFileCreation() {
        RuntimeLogger.init(logDir = tempDir)

        val logFile = RuntimeLogger.logFile
        assertNotNull(logFile, "Log file should be initialized")
        assertTrue(logFile!!.exists(), "Log file should exist on disk")

        RuntimeLogger.i("TestTag", "Testing informational message")
        RuntimeLogger.e("ErrorTag", "Testing error message with exception", IllegalStateException("Test error"))

        val recent = RuntimeLogger.getRecentLogs()
        assertTrue(recent.any { it.contains("Testing informational message") })
        assertTrue(recent.any { it.contains("Testing error message with exception") })

        RuntimeLogger.stop()

        val content = logFile.readText()
        assertTrue(content.contains("RETROPACK STANDALONE RUNTIME DIAGNOSTIC LOG"))
        assertTrue(content.contains("Testing informational message"))
        assertTrue(content.contains("IllegalStateException: Test error"))
        assertTrue(content.contains("RETROPACK RUNTIME SHUTTING DOWN NORMALLY"))
    }
}
