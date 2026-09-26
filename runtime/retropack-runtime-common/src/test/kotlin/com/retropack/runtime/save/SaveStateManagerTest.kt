package com.retropack.runtime.save

import com.retropack.runtime.core.EmulationEngine
import com.retropack.runtime.core.EmulationState
import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.IntBuffer

class SaveStateManagerTest {

    @TempDir
    lateinit var tempDir: File

    private class FakeSaveStateEngine : EmulationEngine {
        var lastSavedSlot: Int = -1
        var lastSavedPath: String = ""
        var lastLoadedSlot: Int = -1
        var lastLoadedPath: String = ""

        override val state: EmulationState get() = EmulationState.RUNNING
        override fun initialize(internalStoragePath: String): Boolean = true
        override fun loadRom(romPath: String): Boolean = true
        override fun unloadRom() {}
        override fun stepFrame(): Boolean = true
        override fun setKeyMask(mask: Int) {}
        override fun setKeys(vararg keys: RetroKey) {}
        override fun getVideoBuffer(): IntBuffer = IntBuffer.allocate(240 * 160)
        override fun getAudioSamples(outSamples: ShortArray, maxSamples: Int): Int = 0
        override fun getSramSize(): Int = 0
        override fun readSram(outBuffer: ByteArray): Boolean = false
        override fun writeSram(inBuffer: ByteArray): Boolean = false

        override fun saveState(slot: Int, targetPath: String): Boolean {
            lastSavedSlot = slot
            lastSavedPath = targetPath
            File(targetPath).writeBytes(ByteArray(1024) { 0x33 })
            return true
        }

        override fun loadState(slot: Int, sourcePath: String): Boolean {
            lastLoadedSlot = slot
            lastLoadedPath = sourcePath
            return File(sourcePath).exists()
        }

        override fun pause() {}
        override fun resume() {}
        override fun close() {}
    }

    private lateinit var manager: SaveStateManager
    private lateinit var engine: FakeSaveStateEngine

    @BeforeEach
    fun setUp() {
        manager = SaveStateManager(storageDir = tempDir)
        engine = FakeSaveStateEngine()
    }

    @Test
    fun `saveState writes state snapshot, thumbnail png, and metadata json`() {
        val videoBuffer = IntBuffer.allocate(240 * 160)
        for (i in 0 until 240 * 160) {
            videoBuffer.put(i, 0xFF00E5FF.toInt()) // Cyan pixel
        }

        val success = manager.saveState(
            slot = 1,
            engine = engine,
            videoBuffer = videoBuffer,
            nativeWidth = 240,
            nativeHeight = 160,
            gameTitle = "Test Game"
        )

        assertTrue(success)
        assertEquals(1, engine.lastSavedSlot)

        val stateFile = manager.getStateFile(1)
        val thumbFile = manager.getThumbnailFile(1)
        val metaFile = manager.getMetaFile(1)

        assertTrue(stateFile.exists(), "State binary file must exist")
        assertEquals(1024, stateFile.length())

        assertTrue(thumbFile.exists(), "Screenshot thumbnail PNG file must exist")
        assertTrue(thumbFile.length() > 0)

        // Verify PNG magic header
        val pngBytes = thumbFile.readBytes()
        assertEquals(0x89.toByte(), pngBytes[0])
        assertEquals('P'.code.toByte(), pngBytes[1])
        assertEquals('N'.code.toByte(), pngBytes[2])
        assertEquals('G'.code.toByte(), pngBytes[3])

        assertTrue(metaFile.exists(), "Metadata JSON must exist")
        val meta = SaveSlotMetadata.fromJson(metaFile.readText())
        assertNotNull(meta)
        assertEquals(1, meta!!.slot)
        assertEquals("Test Game", meta.gameTitle)
    }

    @Test
    fun `loadState restores emulator state from disk`() {
        // Save state first
        manager.saveState(slot = 2, engine = engine, gameTitle = "Slot 2 Game")

        val loaded = manager.loadState(slot = 2, engine = engine)
        assertTrue(loaded)
        assertEquals(2, engine.lastLoadedSlot)
        assertEquals(manager.getStateFile(2).absolutePath, engine.lastLoadedPath)
    }

    @Test
    fun `getSlotInfo describes empty and saved slots accurately`() {
        val emptyInfo = manager.getSlotInfo(3)
        assertFalse(emptyInfo.exists)
        assertEquals("Empty", emptyInfo.relativeTime)

        val fixedTime = System.currentTimeMillis() - 120_000L // 2 mins ago
        manager.saveState(slot = 3, engine = engine)
        manager.getMetaFile(3).writeText(SaveSlotMetadata(3, fixedTime, "Test").toJson())

        val savedInfo = manager.getSlotInfo(3)
        assertTrue(savedInfo.exists)
        assertEquals("2 mins ago", savedInfo.relativeTime)
    }

    @Test
    fun `formatRelativeTime computes human-readable timestamps`() {
        val now = 1000000000000L

        assertEquals("Just now", SaveStateManager.formatRelativeTime(now - 10_000L, now))
        assertEquals("1 min ago", SaveStateManager.formatRelativeTime(now - 70_000L, now))
        assertEquals("5 mins ago", SaveStateManager.formatRelativeTime(now - 300_000L, now))
        assertEquals("1 hour ago", SaveStateManager.formatRelativeTime(now - 3700_000L, now))
        assertEquals("3 hours ago", SaveStateManager.formatRelativeTime(now - 3 * 3600_000L, now))
        assertEquals("Yesterday", SaveStateManager.formatRelativeTime(now - 28 * 3600_000L, now))
        assertEquals("5 days ago", SaveStateManager.formatRelativeTime(now - 5 * 86400_000L, now))
        assertEquals("Empty", SaveStateManager.formatRelativeTime(0L, now))
    }

    @Test
    fun `downsampleFrame scales 240x160 buffer to 120x80 pixels`() {
        val srcW = 240
        val srcH = 160
        val buffer = IntBuffer.allocate(srcW * srcH)
        for (i in 0 until srcW * srcH) {
            buffer.put(i, 0xFF123456.toInt())
        }

        val downsampled = SaveStateManager.downsampleFrame(buffer, srcW, srcH, 120, 80)
        assertEquals(120 * 80, downsampled.size)
        assertEquals(0xFF123456.toInt(), downsampled[0])
    }

    @Test
    fun `deleteSlot removes state, thumbnail, and metadata`() {
        manager.saveState(slot = 4, engine = engine)
        assertTrue(manager.getStateFile(4).exists())

        val deleted = manager.deleteSlot(4)
        assertTrue(deleted)
        assertFalse(manager.getStateFile(4).exists())
        assertFalse(manager.getThumbnailFile(4).exists())
        assertFalse(manager.getMetaFile(4).exists())
    }
}
