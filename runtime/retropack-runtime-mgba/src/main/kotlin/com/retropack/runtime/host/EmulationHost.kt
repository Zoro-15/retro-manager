package com.retropack.runtime.host

import com.retropack.runtime.audio.RetroAudioPlayer
import com.retropack.runtime.core.EmulationEngine
import com.retropack.runtime.core.EmulationState
import com.retropack.runtime.core.ScaleMode
import com.retropack.runtime.input.GamepadMapper
import com.retropack.runtime.input.InputCoordinator
import com.retropack.runtime.input.TouchOverlayView
import com.retropack.runtime.save.SaveManager
import com.retropack.runtime.video.RetroGlRenderer
import java.io.File

/**
 * Top-level runtime bedrock coordinator for RetroPack (`retropack-runtime-mgba.aar`).
 *
 * Implements specifications from:
 * - masterplan.md Section 5.4 & Section 5.5: "Native Execution Bedrock: retropack-runtime-mgba.aar".
 * - architechture.md Constitutional Invariant 5: Zero-loss save durability across lifecycle events.
 * - roadmap.md Part 2.6: Runtime Host Bedrock Integration & Phase 2 Completion.
 */
class EmulationHost(
    val engine: EmulationEngine,
    var saveManager: SaveManager? = null,
    val audioPlayer: RetroAudioPlayer? = null,
    val renderer: RetroGlRenderer? = null,
    touchOverlay: TouchOverlayView? = null,
    gamepadMapper: GamepadMapper = GamepadMapper()
) : AutoCloseable {

    val inputCoordinator: InputCoordinator = InputCoordinator(
        touchOverlay = touchOverlay,
        gamepadMapper = gamepadMapper,
        onKeyMaskDispatched = { mask -> engine.setKeyMask(mask) }
    )

    val emulationLoop: EmulationLoop = EmulationLoop(
        engine = engine,
        audioPlayer = audioPlayer,
        saveManagerSupplier = { saveManager },
        onFrameComplete = {
            // Video buffer is updated in-engine; renderer draws on next surface tick
        }
    )

    val state: EmulationState
        get() = engine.state

    /**
     * Initializes the native core, loads the game ROM, and restores existing battery SRAM.
     */
    fun loadGame(
        romFile: File,
        internalStorageDir: File,
        saveFile: File? = null
    ): Boolean {
        require(romFile.exists()) { "ROM file does not exist: ${romFile.absolutePath}" }
        internalStorageDir.mkdirs()

        if (!engine.initialize(internalStorageDir.absolutePath)) {
            return false
        }

        if (!engine.loadRom(romFile.absolutePath)) {
            return false
        }

        if (saveFile != null) {
            val sm = SaveManager(saveFile = saveFile)
            if (saveFile.exists() && saveFile.length() > 0L) {
                sm.restoreToSram()
            }
            this.saveManager = sm
        }

        return true
    }

    /**
     * Starts active emulation and audio output.
     */
    fun start() {
        audioPlayer?.start()
        emulationLoop.start()
    }

    /**
     * Suspends emulation on activity pause or backgrounding.
     *
     * Invariant: Guarantees synchronous cartridge SRAM flush to flash memory.
     */
    fun pause() {
        emulationLoop.pause()
        audioPlayer?.pause()
        saveManager?.flushNow()
    }

    /**
     * Resumes emulation after being paused.
     */
    fun resume() {
        audioPlayer?.start()
        emulationLoop.resume()
    }

    /**
     * Halts emulation loop, flushes battery SRAM, and stops audio.
     */
    fun stop() {
        emulationLoop.stop()
        saveManager?.flushNow()
        audioPlayer?.stop()
        engine.unloadRom()
    }

    /**
     * Steps a single frame forward synchronously.
     */
    fun stepFrame(): Boolean {
        return emulationLoop.stepSingleFrame()
    }

    /**
     * Changes display scaling mode.
     */
    fun setScaleMode(mode: ScaleMode) {
        renderer?.updateScaleMode(mode)
    }

    /**
     * Captures a savestate snapshot to disk at [targetPath].
     */
    fun saveState(slot: Int, targetPath: String): Boolean {
        return engine.saveState(slot, targetPath)
    }

    /**
     * Restores a savestate snapshot from disk at [sourcePath].
     */
    fun loadState(slot: Int, sourcePath: String): Boolean {
        return engine.loadState(slot, sourcePath)
    }

    /**
     * Toggles visibility of on-screen virtual touch controls.
     */
    fun setVirtualControlsVisible(visible: Boolean) {
        inputCoordinator.touchOverlay?.isControlsVisible = visible
    }

    override fun close() {
        stop()
        audioPlayer?.release()
        renderer?.release()
        engine.close()
    }
}
