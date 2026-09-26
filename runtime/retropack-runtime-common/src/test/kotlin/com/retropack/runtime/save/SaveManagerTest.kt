package com.retropack.runtime.save

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SaveManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var saveFile: File
    private lateinit var mockSramSource: MockSramStorageSource
    private lateinit var saveManager: SaveManager

    class MockSramStorageSource(initialData: ByteArray = ByteArray(0)) : SramStorageSource {
        var sramData: ByteArray = initialData.clone()
        var readCount: Int = 0
        var writeCount: Int = 0

        override fun getSramSize(): Int = sramData.size

        override fun readSram(outBuffer: ByteArray): Boolean {
            readCount++
            if (outBuffer.size < sramData.size) return false
            System.arraycopy(sramData, 0, outBuffer, 0, sramData.size)
            return true
        }

        override fun writeSram(inBuffer: ByteArray): Boolean {
            writeCount++
            sramData = inBuffer.clone()
            return true
        }
    }

    @BeforeEach
    fun setup() {
        saveFile = tempDir.resolve("game.sav").toFile()
        mockSramSource = MockSramStorageSource(ByteArray(32) { (it + 1).toByte() })
        saveManager = SaveManager(saveFile = saveFile, sramSource = mockSramSource)
    }

    @Test
    fun `verify flush returns false when sram size is zero`() {
        val emptySource = MockSramStorageSource(ByteArray(0))
        val manager = SaveManager(saveFile = saveFile, sramSource = emptySource)

        assertFalse(manager.flush(force = true))
        assertFalse(saveFile.exists())
    }

    @Test
    fun `verify atomic posix fsync flush produces valid save file`() {
        saveManager.markDirty()
        val flushed = saveManager.flush()

        assertTrue(flushed)
        assertTrue(saveFile.exists())
        assertEquals(32, saveFile.length())
        assertArrayEquals(mockSramSource.sramData, saveFile.readBytes())
        assertFalse(saveManager.tmpSaveFile.exists(), "Tmp file must not remain after atomic rename")
        assertFalse(saveManager.isDirty, "SaveManager must not be dirty after successful flush")
    }

    @Test
    fun `verify dirty gating prevents redundant writes`() {
        saveManager.markDirty()
        assertTrue(saveManager.flush())

        // Second flush without modification must be skipped
        assertFalse(saveManager.flush(), "Unmodified SRAM must be skipped by dirty-gate")

        // Force flush bypasses dirty gate
        assertTrue(saveManager.flush(force = true), "Force flush must execute even if not dirty")
    }

    @Test
    fun `verify backup file rotation on successive flushes`() {
        saveManager.markDirty()
        assertTrue(saveManager.flush())
        assertFalse(saveManager.bakSaveFile.exists())

        // Mutate SRAM
        mockSramSource.sramData[0] = 99
        saveManager.markDirty()
        assertTrue(saveManager.flush())

        assertTrue(saveManager.bakSaveFile.exists(), "Previous save must be rotated to .bak")
        assertEquals(32, saveManager.bakSaveFile.length())
        assertNotEquals(saveFile.readBytes()[0], saveManager.bakSaveFile.readBytes()[0])
    }

    @Test
    fun `verify restore loads data into SRAM and cleans stale tmp`() {
        val testData = ByteArray(32) { (it * 2).toByte() }
        saveFile.writeBytes(testData)

        // Simulate stale tmp file left by sudden crash
        saveManager.tmpSaveFile.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(saveManager.tmpSaveFile.exists())

        val restored = saveManager.restore()
        assertTrue(restored)
        assertArrayEquals(testData, mockSramSource.sramData)
        assertFalse(saveManager.tmpSaveFile.exists(), "Stale tmp must be pruned during restore")
    }

    @Test
    fun `verify restore falls back to backup file when primary save is missing`() {
        val backupData = ByteArray(32) { 77 }
        saveManager.bakSaveFile.writeBytes(backupData)
        assertFalse(saveFile.exists())

        val restored = saveManager.restore()
        assertTrue(restored)
        assertArrayEquals(backupData, mockSramSource.sramData)
    }

    @Test
    fun `verify checkAndMarkDirty detects buffer modification`() {
        saveManager.markDirty()
        saveManager.flush()
        assertFalse(saveManager.isDirty)

        // No change
        assertFalse(saveManager.checkAndMarkDirty())

        // Mutate SRAM
        mockSramSource.sramData[5] = 0x5A
        assertTrue(saveManager.checkAndMarkDirty())
        assertTrue(saveManager.isDirty)
    }
}
