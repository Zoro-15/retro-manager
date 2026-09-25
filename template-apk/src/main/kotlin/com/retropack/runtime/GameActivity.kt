package com.retropack.runtime

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import com.retropack.runtime.audio.RetroAudioPlayer
import com.retropack.runtime.core.EmulationEngine
import com.retropack.runtime.core.NativeEmulationEngine
import com.retropack.runtime.host.EmulationHost
import com.retropack.runtime.host.RomStager
import com.retropack.runtime.host.RuntimeConfig
import com.retropack.runtime.input.GamepadMapper
import com.retropack.runtime.input.TouchOverlayView
import com.retropack.runtime.save.SaveManager
import com.retropack.runtime.video.RetroSurfaceView
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.abs

/**
 * Standalone Android Application bootstrap and lifecycle coordinator for RetroPack.
 *
 * Implements specifications from:
 * - masterplan.md Section 5.1: "Architecture & Bootstrap (GameActivity.kt)"
 * - architechture.md Constitutional Invariant 1: Fully-qualified activity identifier
 * - architechture.md Constitutional Invariant 5: Zero-loss save durability across lifecycle events
 * - roadmap.md Phase 3.
 */
open class GameActivity : Activity() {

    companion object {
        const val ROM_FILENAME = "game.rom"
        const val SAVE_FILENAME = "game.sav"
        private const val TARGET_REFRESH_RATE = 60.0f
    }

    var config: RuntimeConfig = RuntimeConfig.DEFAULT
        protected set

    var host: EmulationHost? = null
        protected set

    var surfaceView: RetroSurfaceView? = null
        protected set

    var touchOverlay: TouchOverlayView? = null
        protected set

    var saveManager: SaveManager? = null
        protected set

    var isGameLoaded: Boolean = false
        protected set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Configure Fullscreen Immersive Window
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        configureWindow()

        // 2. Load Injected Runtime Configuration (assets/retropack.json)
        config = loadRuntimeConfig()

        // 3. Stage ROM atomically to storage/game.rom with SHA-256 verification
        val romFile = File(storageDirectory(), ROM_FILENAME)
        stageRomIfNeeded(romFile, config.game.romSha256)

        // 4. Assemble View Hierarchy
        val root = FrameLayout(this)

        val sv = createSurfaceView()
        sv.scaleMode = config.runtime.videoScaleMode
        this.surfaceView = sv
        root.addView(sv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        if (config.controls.touchEnabled) {
            val to = createTouchOverlay()
            to.opacity = config.controls.touchOpacity
            to.hapticFeedbackEnabledState = config.controls.haptics
            this.touchOverlay = to
            root.addView(to, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        setContentView(root)

        // 5. Initialize Core Subsystems and Wire into EmulationHost
        val saveFile = File(storageDirectory(), SAVE_FILENAME)
        val sm = createSaveManager(saveFile)
        this.saveManager = sm

        val engine = createEngine()
        val audioPlayer = createAudioPlayer(config)
        val gamepadMapper = createGamepadMapper()

        val emulationHost = EmulationHost(
            engine = engine,
            saveManager = sm,
            audioPlayer = audioPlayer,
            renderer = sv.renderer,
            touchOverlay = touchOverlay,
            gamepadMapper = gamepadMapper
        )
        this.host = emulationHost

        // 6. Bootstrap Emulation if ROM is staged and verified
        if (romFile.exists() && romFile.length() > 0L) {
            isGameLoaded = emulationHost.loadGame(
                romFile = romFile,
                internalStorageDir = storageDirectory(),
                saveFile = saveFile
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        surfaceView?.resume()
        host?.let {
            if (isGameLoaded) {
                it.resume()
            }
        }
    }

    override fun onPause() {
        host?.pause()
        // Invariant 5: Guarantees synchronous POSIX fsync cartridge SRAM flush to flash memory
        saveManager?.flushNow()
        surfaceView?.pause()
        super.onPause()
    }

    override fun onStop() {
        // Invariant 5: Guarantees synchronous POSIX fsync cartridge SRAM flush on activity backgrounding
        saveManager?.flushNow()
        super.onStop()
    }

    override fun onDestroy() {
        saveManager?.flushNow()
        host?.close()
        host = null
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (host?.inputCoordinator?.gamepadMapper?.handleKeyEvent(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (host?.inputCoordinator?.gamepadMapper?.handleGenericMotionEvent(event) == true) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * Storage root for the staged ROM and cartridge save file.
     *
     * Test seam: production resolves [filesDir]; tests override this to inject
     * a temp directory. This works in BOTH build modes — the previous test
     * approach called the JVM-stub-only Context.setFilesDir(), which does not
     * exist on the real Android framework jar and broke
     * :template-apk:compileDebugUnitTestKotlin in CI (unresolved reference).
     */
    protected open fun storageDirectory(): File = filesDir

    /**
     * Reads `assets/retropack.json` or falls back to canonical default settings.
     */
    protected open fun loadRuntimeConfig(): RuntimeConfig {
        return try {
            assets.open(RuntimeConfig.ASSET_PATH).use { stream ->
                val jsonText = stream.bufferedReader(Charsets.UTF_8).readText()
                RuntimeConfig.fromJson(jsonText)
            }
        } catch (_: IOException) {
            RuntimeConfig.DEFAULT
        }
    }

    /**
     * Stages ROM from APK assets to [targetFile] if not already present or checksum differs.
     */
    protected open fun stageRomIfNeeded(targetFile: File, expectedSha256: String): Boolean {
        if (RomStager.isRomStaged(targetFile, expectedSha256)) {
            return true
        }

        return try {
            assets.open(ROM_FILENAME).use { stream ->
                RomStager.stageRom(stream, targetFile, expectedSha256)
            }
        } catch (_: IOException) {
            false
        }
    }

    private fun configureWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Lock refresh rate to 60 Hz where supported by display modes
        try {
            val display = windowManager.defaultDisplay
            val mode60 = display.supportedModes.find { mode ->
                abs(mode.refreshRate - TARGET_REFRESH_RATE) < 1.0f
            }
            if (mode60 != null) {
                window.attributes.preferredDisplayModeId = mode60.modeId
            }
        } catch (_: Throwable) {
            // Best effort display mode selection
        }

        // Notch / Display Cutout accommodation for edge-to-edge presentation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    // Factory methods for dependency injection and test isolation
    protected open fun createEngine(): EmulationEngine = NativeEmulationEngine()
    protected open fun createSurfaceView(): RetroSurfaceView = RetroSurfaceView(this)
    protected open fun createTouchOverlay(): TouchOverlayView = TouchOverlayView(this)
    protected open fun createAudioPlayer(cfg: RuntimeConfig): RetroAudioPlayer =
        RetroAudioPlayer(driftController = com.retropack.runtime.audio.AudioDriftController(cfg.runtime.audioSampleRate, 2))
    protected open fun createSaveManager(saveFile: File): SaveManager = SaveManager(saveFile = saveFile)
    protected open fun createGamepadMapper(): GamepadMapper = GamepadMapper()
}
