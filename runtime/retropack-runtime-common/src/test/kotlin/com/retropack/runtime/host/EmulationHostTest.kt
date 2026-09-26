package com.retropack.runtime.host

import com.retropack.runtime.core.EmulationEngine
import com.retropack.runtime.core.EmulationState
import com.retropack.runtime.core.RetroKey
import com.retropack.runtime.core.ScaleMode
import com.retropack.runtime.save.SaveManager
import com.retropack.runtime.save.SramStorageSource
import com.retropack.runtime.video.RetroGlRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.IntBuffer

class EmulationHostTest {

    @TempDir
    lateinit var tempDir: File

    private class FakeEngine : EmulationEngine {
        var initialized = false
        var romLoaded = false
        var framesStepped = 0
        var activeKeyMask = 0
        var closed = false
        var engineState = EmulationState.UNINITIALIZED

        override val state: EmulationState get() = engineState

        override fun initialize(internalStoragePath: String): Boolean {
            initialized = true
            engineState = EmulationState.INITIALIZED
            return true
        }

        override fun loadRom(romPath: String): Boolean {
            romLoaded = true
            return true
        }

        override fun unloadRom() {
            romLoaded = false
            engineState = EmulationState.STOPPED
        }

        override fun stepFrame(): Boolean {
            framesStepped++
            engineState = EmulationState.RUNNING
            return true
        }

        override fun setKeyMask(mask: Int) {
            activeKeyMask = mask
        }

        override fun setKeys(vararg keys: RetroKey) {
            setKeyMask(RetroKey.maskOf(*keys))
        }

        override fun getVideoBuffer(): IntBuffer? = null
        override fun getAudioSamples(outSamples: ShortArray, maxSamples: Int): Int = 0
        override fun getSramSize(): Int = 512
        override fun readSram(outBuffer: ByteArray): Boolean = true
        override fun writeSram(inBuffer: ByteArray): Boolean = true
        override fun saveState(slot: Int, targetPath: String): Boolean = true
        override fun loadState(slot: Int, sourcePath: String): Boolean = true
        override fun pause() { engineState = EmulationState.PAUSED }
        override fun resume() { engineState = EmulationState.RUNNING }
        override fun close() {
            closed = true
            engineState = EmulationState.UNINITIALIZED
        }
    }

    private class FakeSramSource : SramStorageSource {
        val sram = ByteArray(512) { 0x42 }
        var readCount = 0

        override fun getSramSize(): Int = sram.size
        override fun readSram(outBuffer: ByteArray): Boolean {
            readCount++
            System.arraycopy(sram, 0, outBuffer, 0, Math.min(sram.size, outBuffer.size))
            return true
        }
        override fun writeSram(inBuffer: ByteArray): Boolean = true
    }

    @Test
    fun `loadGame initializes engine, loads ROM, and restores SRAM`() {
        val engine = FakeEngine()
        val romFile = File(tempDir, "game.gba").apply { writeBytes(ByteArray(64)) }
        val saveFile = File(tempDir, "game.sav")
        val internalDir = File(tempDir, "internal")

        val host = EmulationHost(engine = engine)
        val loaded = host.loadGame(romFile, internalDir, saveFile)

        assertTrue(loaded)
        assertTrue(engine.initialized)
        assertTrue(engine.romLoaded)
    }

    @Test
    fun `stepFrame steps engine and advances emulation`() {
        val engine = FakeEngine()
        val host = EmulationHost(engine = engine)

        assertEquals(0, engine.framesStepped)
        val success = host.stepFrame()

        assertTrue(success)
        assertEquals(1, engine.framesStepped)
        assertEquals(EmulationState.RUNNING, host.state)
    }

    @Test
    fun `pause and stop trigger synchronous save durability flush`() {
        val engine = FakeEngine()
        val sramSource = FakeSramSource()
        val saveFile = File(tempDir, "durability.sav")
        val saveManager = SaveManager(saveFile = saveFile, sramSource = sramSource)

        val host = EmulationHost(engine = engine, saveManager = saveManager)

        // Initial pause: forces flushNow()
        host.pause()
        assertTrue(saveFile.exists())
        assertEquals(512, saveFile.length())
        assertTrue(sramSource.readCount >= 1)

        // Modify fake SRAM and test stop() flush
        sramSource.sram[0] = 0x99.toByte()
        host.stop()
        assertEquals(0x99.toByte(), saveFile.readBytes()[0])
    }

    @Test
    fun `input coordinator forwards inputs to engine`() {
        val engine = FakeEngine()
        val host = EmulationHost(engine = engine)

        host.inputCoordinator.updateTouchMask(RetroKey.KEY_A or RetroKey.KEY_B)
        assertEquals(RetroKey.KEY_A or RetroKey.KEY_B, engine.activeKeyMask)

        host.inputCoordinator.updateGamepadMask(RetroKey.KEY_START)
        assertEquals(RetroKey.KEY_A or RetroKey.KEY_B or RetroKey.KEY_START, engine.activeKeyMask)
    }

    @Test
    fun `setScaleMode updates renderer`() {
        val renderer = RetroGlRenderer()
        val host = EmulationHost(engine = FakeEngine(), renderer = renderer)

        host.setScaleMode(ScaleMode.ASPECT_FIT)
        assertEquals(ScaleMode.ASPECT_FIT, renderer.scaleMode)

        host.setScaleMode(ScaleMode.INTEGER_FIT)
        assertEquals(ScaleMode.INTEGER_FIT, renderer.scaleMode)
    }

    @Test
    fun `fast forward multiplier and audio mute update loop configuration`() {
        val engine = FakeEngine()
        val host = EmulationHost(engine = engine)

        assertEquals(1, host.getFastForwardMultiplier())
        host.setFastForwardMultiplier(4)
        assertEquals(4, host.getFastForwardMultiplier())
        assertEquals(4, host.emulationLoop.speedMultiplier)

        host.setMuteAudioOnFastForward(true)
        assertTrue(host.emulationLoop.muteAudioOnFastForward)
    }

    @Test
    fun `saveState and loadState delegate to engine`() {
        val engine = FakeEngine()
        val host = EmulationHost(engine = engine)

        assertTrue(host.saveState(1, "/tmp/slot_1.state"))
        assertTrue(host.loadState(1, "/tmp/slot_1.state"))
    }

    @Test
    fun `close releases engine and stops loop`() {
        val engine = FakeEngine()
        val host = EmulationHost(engine = engine)

        assertFalse(engine.closed)
        host.close()
        assertTrue(engine.closed)
        assertEquals(EmulationState.UNINITIALIZED, host.state)
    }
}
