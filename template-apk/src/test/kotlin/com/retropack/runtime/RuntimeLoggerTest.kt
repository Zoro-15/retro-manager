package com.retropack.runtime

import com.retropack.runtime.logging.RuntimeLogger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RuntimeLoggerTest {

    @TempDir
    lateinit var tempDir: File

    @AfterEach
    fun tearDown() {
        RuntimeLogger.stop()
    }

    @Test
    fun testRuntimeLoggerLifecycleAndFileCreation() {
        val publicLogDir = File(tempDir, "public_logs/tobu_tobu_girl")
        RuntimeLogger.init(logDir = tempDir, publicLogDir = publicLogDir, slug = "tobu_tobu_girl")

        val logFile = RuntimeLogger.logFile
        assertNotNull(logFile, "Log file should be initialized")
        assertTrue(logFile!!.exists(), "Log file should exist on disk")

        val publicFile = RuntimeLogger.publicLogFile
        assertNotNull(publicFile, "Public log file should be initialized")
        assertTrue(publicFile!!.exists(), "Public log file should exist on disk")
        assertEquals("tobu_tobu_girl.log", publicFile!!.name)

        RuntimeLogger.i("TestTag", "Testing informational message")
        RuntimeLogger.e("ErrorTag", "Testing error message with exception", IllegalStateException("Test error"))

        val recent = RuntimeLogger.getRecentLogs()
        assertTrue(recent.any { it.contains("Testing informational message") })
        assertTrue(recent.any { it.contains("Testing error message with exception") })

        RuntimeLogger.stop()

        val content = publicFile.readText()
        assertTrue(content.contains("RETROPACK STANDALONE RUNTIME DIAGNOSTIC LOG"))
        assertTrue(content.contains("tobu_tobu_girl"))
        assertTrue(content.contains("Testing informational message"))
        assertTrue(content.contains("IllegalStateException: Test error"))
        assertTrue(content.contains("RETROPACK RUNTIME SHUTTING DOWN NORMALLY"))
    }

    @Test
    fun testPublicDownloadDirectoryResolution() {
        val dir = RuntimeLogger.resolvePublicDownloadLogsDir("pcsx_game")
        assertTrue(dir.path.replace("\\", "/").contains("logs/pcsx_game"))
    }
}
