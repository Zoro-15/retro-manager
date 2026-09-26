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
    gamepadMapper: GamepadMapper = GamepadMapper(),
    sensorController: com.retropack.runtime.input.SensorController? = null
) : AutoCloseable {

    val inputCoordinator: InputCoordinator = InputCoordinator(
        touchOverlay = touchOverlay,
        gamepadMapper = gamepadMapper,
        sensorController = sensorController,
        onKeyMaskDispatched = { mask -> engine.setKeyMask(mask) }
    )

    var onFrameRenderRequested: (() -> Unit)? = null

    val emulationLoop: EmulationLoop = EmulationLoop(
        engine = engine,
        audioPlayer = audioPlayer,
        saveManagerSupplier = { saveManager },
        onFrameComplete = {
            onFrameRenderRequested?.invoke()
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
        if (!internalStorageDir.exists() && !internalStorageDir.mkdirs()) {
            return false
        }

        if (!engine.initialize(internalStorageDir.absolutePath)) {
            return false
        }

        if (!engine.loadRom(romFile.absolutePath)) {
            return false
        }

        // Sync GL dimensions from the core: GB/GBC frames are 160x144, not
        // the renderer's 240x160 default (issue #13). Best effort — failures
        // keep the default rather than failing the load.
        try {
            val dims = com.retropack.runtime.core.NativeCore.nativeGetVideoSize()
            if (dims != null && dims.size == 2 && dims[0] > 0 && dims[1] > 0) {
                renderer?.setNativeDimensions(dims[0], dims[1])
            }
        } catch (_: UnsatisfiedLinkError) {
        }

        if (saveFile != null) {
            val sm = SaveManager(saveFile = saveFile)
            // Always restore through the manager: it falls back to .bak when
            // the primary is missing (issue #14), instead of orphaning it.
            sm.restoreToSram()
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
     * Updates fast-forward emulation speed multiplier.
     */
    fun setFastForwardMultiplier(multiplier: Int) {
        emulationLoop.speedMultiplier = multiplier
    }

    fun getFastForwardMultiplier(): Int = emulationLoop.speedMultiplier

    /**
     * Configures whether audio output is muted during fast-forward.
     */
    fun setMuteAudioOnFastForward(mute: Boolean) {
        emulationLoop.muteAudioOnFastForward = mute
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
