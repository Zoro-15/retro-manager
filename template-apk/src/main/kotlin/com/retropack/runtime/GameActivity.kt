package com.retropack.runtime

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.retropack.domain.model.HapticFeedbackMode
import com.retropack.runtime.audio.RetroAudioPlayer
import com.retropack.runtime.core.EmulationEngine
import com.retropack.runtime.core.NativeCore
import com.retropack.runtime.core.NativeEmulationEngine
import com.retropack.runtime.host.EmulationHost
import com.retropack.runtime.host.RomStager
import com.retropack.runtime.host.RuntimeConfig
import com.retropack.runtime.input.ControlsPreferences
import com.retropack.runtime.input.DpadType
import com.retropack.runtime.input.GamepadMapper
import com.retropack.runtime.input.HapticManager
import com.retropack.runtime.input.JoystickSnapMode
import com.retropack.runtime.input.SensorController
import com.retropack.runtime.input.TouchOverlayView
import com.retropack.runtime.input.TouchTheme
import com.retropack.runtime.logging.RuntimeLogger
import com.retropack.runtime.save.AutoResumeManager
import com.retropack.runtime.save.SaveManager
import com.retropack.runtime.save.SaveStateManager
import com.retropack.runtime.ui.BezelOverlayView
import com.retropack.runtime.ui.DiscSwitcherOverlay
import com.retropack.runtime.ui.GamepadRemapOverlay
import com.retropack.runtime.ui.InGameSettingsOverlay
import com.retropack.runtime.ui.MoreFeaturesSheet
import com.retropack.runtime.ui.QuickMenuOverlay
import com.retropack.runtime.factory.NativeCoreFactory
import com.retropack.runtime.video.DisplaySyncManager
import com.retropack.runtime.video.RetroGlRenderer
import com.retropack.runtime.video.RetroSurfaceView
import java.io.File
import kotlin.math.abs

/**
 * Standalone Android Application bootstrap and lifecycle coordinator for RetroPack.
 *
 * Implements specifications from:
 * - masterplan.md Section 5.1: "Architecture & Bootstrap (GameActivity.kt)"
 * - architechture.md Constitutional Invariant 1: Fully-qualified activity identifier
 * - architechture.md Constitutional Invariant 5: Zero-loss save durability across lifecycle events
 * - Feature 1: Multi-Disc Game Management & Virtual Disc Switcher
 * - Feature 2: In-Game Pause HUD & Savestate Quick Slots UI
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

    var bezelOverlay: BezelOverlayView? = null
        protected set

    var touchOverlay: TouchOverlayView? = null
        protected set

    var quickMenu: QuickMenuOverlay? = null
        protected set

    var settingsOverlay: InGameSettingsOverlay? = null
        protected set

    var saveManager: SaveManager? = null
        protected set

    var saveStateManager: SaveStateManager? = null
        protected set

    var moreFeaturesSheet: MoreFeaturesSheet? = null
        protected set

    var discSwitcherOverlay: DiscSwitcherOverlay? = null
        protected set

    var sensorController: SensorController? = null
        protected set

    var gamepadRemapOverlay: GamepadRemapOverlay? = null
        protected set

    var hapticManager: HapticManager? = null
        protected set

    var isGameLoaded: Boolean = false
        protected set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuntimeLogger.start(this)
        RuntimeLogger.i("Bootstrap", "GameActivity.onCreate starting...")

        // 1. Configure Fullscreen Immersive Window
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        configureWindow()

        // 2. Load Injected Runtime Configuration (assets/retropack.json)
        config = loadRuntimeConfig()

        // Load persisted preferences or fallback to config defaults
        val effectiveScaleMode = ControlsPreferences.loadScaleMode(this, config.runtime.videoScaleMode)
        val effectiveOpacity = ControlsPreferences.loadOpacity(this, config.controls.touchOpacity)
        val effectiveHaptics = ControlsPreferences.loadHaptics(this, config.controls.haptics)
        val effectiveHapticIntensity = ControlsPreferences.loadHapticIntensity(this, 1.0f)
        val isN64 = config.game.platform.equals("n64", ignoreCase = true)
        val effectiveFloatingDpad = ControlsPreferences.loadFloatingDpadEnabled(this, false)
        val defaultDpadType = if (isN64) DpadType.FIXED_JOYSTICK else if (effectiveFloatingDpad) DpadType.FLOATING_JOYSTICK else DpadType.CLASSIC_CROSS
        val defaultSnapMode = if (isN64) JoystickSnapMode.ANALOG_FREE else JoystickSnapMode.RPG_GRID_4WAY
        val effectiveDpadType = ControlsPreferences.loadDpadType(this, defaultDpadType)
        val effectiveSnapMode = ControlsPreferences.loadJoystickSnapMode(this, defaultSnapMode)
        val effectiveDeadzone = ControlsPreferences.loadJoystickDeadzone(this, 12.0f)
        val effectiveSensitivity = ControlsPreferences.loadJoystickSensitivity(this, 1.0f)
        val effectiveGestures = ControlsPreferences.loadGesturesEnabled(this, true)
        val effectiveTouchTheme = ControlsPreferences.loadTouchTheme(this, "classic_indigo")
        val effectiveSensorModeStr = ControlsPreferences.loadSensorMode(this, "DISABLED")
        val effectiveSensorSensitivity = ControlsPreferences.loadSensorSensitivity(this, 1.0f)
        val effectiveFastForwardSpeed = ControlsPreferences.loadFastForwardSpeed(this, 1)
        val effectiveMuteAudio = ControlsPreferences.loadMuteAudioOnFastForward(this, true)
        val effectiveTurbo = ControlsPreferences.loadTurboEnabled(this, false)
        val effectiveComboMacro = ControlsPreferences.loadComboMacroEnabled(this, false)
        val effectiveLcdGrid = ControlsPreferences.loadLcdGridEnabled(this, false)
        val effectiveGbaColor = ControlsPreferences.loadGbaColorCorrectionEnabled(this, false)
        val effectiveBezel = ControlsPreferences.loadBezelEnabled(this, true)
        val effectiveShaderMode = ControlsPreferences.loadShaderMode(this, config.runtime.videoShaderMode)
        val effectiveBezelMode = ControlsPreferences.loadBezelMode(this, config.runtime.videoBezelMode)
        val effectiveAutoResume = ControlsPreferences.loadAutoResumeEnabled(this, true)
        val effectiveHapticMode = ControlsPreferences.loadHapticFeedbackMode(this, HapticFeedbackMode.AUDIO_REACTIVE)
        val effectiveRumbleStrength = ControlsPreferences.loadRumbleStrength(this, 1.0f)
        val effectiveVrr = ControlsPreferences.loadDisplayVrrEnabled(this, true)
        val effectiveBfi = ControlsPreferences.loadBfiEnabled(this, false)
        val effectiveWsola = ControlsPreferences.loadWsolaEnabled(this, true)

        // Determine multi-disc support
        val isMultiDisc = config.game.discs.size > 1 || config.game.m3uPath != null
        val discLabels = if (config.game.discs.isNotEmpty()) {
            config.game.discs.map { it.label }
        } else listOf("Disc 1")

        // 3. Stage ROM atomically to storage/game.rom with SHA-256 verification
        val romFile = File(storageDirectory(), ROM_FILENAME)
        stageRomIfNeeded(romFile, config.game.romSha256)

        // Initialize Save State Manager
        val ssm = createSaveStateManager(storageDirectory())
        this.saveStateManager = ssm

        // 4. Initialize Motion Sensor Controller
        val sc = createSensorController().apply {
            mode = try {
                SensorController.SensorMode.valueOf(effectiveSensorModeStr)
            } catch (_: Throwable) {
                SensorController.SensorMode.DISABLED
            }
            sensitivity = effectiveSensorSensitivity
        }
        this.sensorController = sc

        // 5. Assemble View Hierarchy
        val root = FrameLayout(this)

        val sv = createSurfaceView()
        sv.scaleMode = effectiveScaleMode
        sv.shaderMode = effectiveShaderMode
        sv.bezelMode = effectiveBezelMode
        sv.bfiEnabled = effectiveBfi
        this.surfaceView = sv
        root.addView(sv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Configure Dynamic Adaptive Display Sync (VRR / 90Hz / 120Hz / 144Hz)
        DisplaySyncManager.configureDisplaySync(
            activity = this,
            surfaceView = sv,
            vrrEnabled = effectiveVrr,
            bfiEnabled = effectiveBfi
        )

        // Handheld Screen Bezel & Borders
        val bezel = createBezelOverlay().apply {
            bezelEnabled = effectiveBezel
            bezelMode = effectiveBezelMode
            platform = config.game.platform
            scaleMode = effectiveScaleMode
        }
        this.bezelOverlay = bezel
        root.addView(bezel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val gamepadMapper = createGamepadMapper().apply {
            loadProfile(this@GameActivity)
        }

        var to: TouchOverlayView? = null
        if (config.controls.touchEnabled) {
            to = createTouchOverlay()
            to.platform = config.game.platform
            to.opacity = effectiveOpacity
            to.hapticFeedbackEnabledState = effectiveHaptics
            to.hapticIntensity = effectiveHapticIntensity
            to.dpadType = effectiveDpadType
            to.joystickSnapMode = effectiveSnapMode
            to.joystickDeadzone = effectiveDeadzone
            to.joystickSensitivity = effectiveSensitivity
            to.gesturesEnabled = effectiveGestures
            to.theme = TouchTheme.fromId(effectiveTouchTheme)
            to.turboEnabled = effectiveTurbo
            to.comboMacroEnabled = effectiveComboMacro
            to.applySavedCustomLayout()

            // Wire Multi-Touch Gestures
            to.onQuickSaveRequested = {
                host?.let { h ->
                    val vBuf = h.engine.getVideoBuffer()
                    ssm.saveState(
                        slot = 1,
                        engine = h.engine,
                        videoBuffer = vBuf,
                        gameTitle = config.game.title
                    )
                    moreFeaturesSheet?.invalidate()
                    RuntimeLogger.i("Gesture", "Quick Save State triggered (Slot 1)")
                }
            }
            to.onQuickLoadRequested = {
                host?.let { h ->
                    ssm.loadState(1, h.engine)
                    RuntimeLogger.i("Gesture", "Quick Load State triggered (Slot 1)")
                }
            }
            to.onToggleFastForwardRequested = {
                val currentSpeed = quickMenu?.fastForwardSpeed ?: 1
                val newSpeed = if (currentSpeed == 1) {
                    val saved = ControlsPreferences.loadFastForwardSpeed(this@GameActivity, 2)
                    if (saved > 1) saved else 2
                } else 1
                host?.setFastForwardMultiplier(newSpeed)
                quickMenu?.fastForwardSpeed = newSpeed
                moreFeaturesSheet?.fastForwardSpeed = newSpeed
                ControlsPreferences.saveFastForwardSpeed(this@GameActivity, newSpeed)
                RuntimeLogger.i("Gesture", "Fast-Forward toggled to ${newSpeed}x")
            }
            to.onToggleQuickMenuRequested = {
                quickMenu?.let { q -> q.isExpanded = !q.isExpanded }
            }
            to.onStylusTouch = { x, y, isTouching ->
                if (config.game.platform.lowercase() == "nds" || config.runtime.core.contains("melon")) {
                    com.retropack.runtime.core.UniversalLibretroCore.nativeSetTouch(x, y, isTouching)
                }
            }

            this.touchOverlay = to
            root.addView(to, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        // In-Game Settings Modal Sheet
        val settings = createSettingsOverlay().apply {
            scaleMode = effectiveScaleMode
            touchOpacity = effectiveOpacity
            hapticsEnabled = effectiveHaptics
            gameTitle = config.game.title
        }
        this.settingsOverlay = settings
        root.addView(settings, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // In-Game "More" App Page & Pause HUD
        val moreSheet = createMoreFeaturesSheet().apply {
            gameTitle = config.game.title
            scaleMode = effectiveScaleMode
            fastForwardSpeed = effectiveFastForwardSpeed
            muteAudioOnFastForward = effectiveMuteAudio
            dpadType = effectiveDpadType
            joystickSnapMode = effectiveSnapMode
            floatingDpadEnabled = effectiveFloatingDpad
            gesturesEnabled = effectiveGestures
            turboButtonsEnabled = effectiveTurbo
            comboMacroEnabled = effectiveComboMacro
            touchTheme = effectiveTouchTheme
            sensorMode = effectiveSensorModeStr
            lcdGridEnabled = effectiveLcdGrid
            gbaColorCorrectionEnabled = effectiveGbaColor
            bezelEnabled = effectiveBezel
            autoResumeEnabled = effectiveAutoResume
            hapticFeedbackMode = effectiveHapticMode
            rumbleStrength = effectiveRumbleStrength
            vrrEnabled = effectiveVrr
            bfiEnabled = effectiveBfi
            wsolaEnabled = effectiveWsola
            this.isMultiDisc = isMultiDisc
            hapticFeedbackEnabledState = effectiveHaptics
            hapticIntensity = effectiveHapticIntensity
            saveStateManager = ssm
        }
        this.moreFeaturesSheet = moreSheet
        root.addView(moreSheet, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Virtual Disc Switcher Modal Overlay
        val discSwitcher = createDiscSwitcherOverlay().apply {
            gameTitle = config.game.title
            this.discLabels = discLabels
            hapticFeedbackEnabledState = effectiveHaptics
        }
        this.discSwitcherOverlay = discSwitcher
        root.addView(discSwitcher, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Gamepad Remapping Modal Dialog
        val remapOverlay = createGamepadRemapOverlay().apply {
            this.gamepadMapper = gamepadMapper
            this.hapticFeedbackEnabledState = effectiveHaptics
            this.hapticIntensity = effectiveHapticIntensity
        }
        this.gamepadRemapOverlay = remapOverlay
        root.addView(remapOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Floating In-Game Quick Menu FAB
        val qm = createQuickMenu().apply {
            isControlsActive = to?.isControlsVisible ?: true
            fastForwardSpeed = effectiveFastForwardSpeed
            this.isMultiDisc = isMultiDisc
            hapticFeedbackEnabledState = effectiveHaptics
        }
        this.quickMenu = qm
        root.addView(qm, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Wire Quick Menu Interactions
        qm.onToggleControls = {
            to?.let {
                it.isControlsVisible = !it.isControlsVisible
                qm.isControlsActive = it.isControlsVisible
            }
        }
        qm.onFastForwardSpeedChanged = { speed ->
            host?.setFastForwardMultiplier(speed)
            ControlsPreferences.saveFastForwardSpeed(this, speed)
            moreSheet.fastForwardSpeed = speed
        }
        qm.onOpenSettings = {
            host?.pause()
            settings.show()
        }
        qm.onOpenMore = {
            host?.pause()
            moreSheet.show()
        }
        qm.onSwitchDisc = {
            host?.pause()
            discSwitcher.show()
        }

        // Wire Disc Switcher Callbacks
        discSwitcher.onDiscSelected = { discIndex, discLabel ->
            RuntimeLogger.i("DiscSwitcher", "User requested switch to $discLabel (index $discIndex)")
            host?.let { h ->
                h.engine.ejectDisc()
                val discEntry = config.game.discs.getOrNull(discIndex)
                val targetPath = if (discEntry != null) {
                    val discFile = File(storageDirectory(), discEntry.path)
                    if (!discFile.exists() && discEntry.sha256.isNotBlank()) {
                        stageRomIfNeeded(discFile, discEntry.sha256)
                    }
                    discFile.absolutePath
                } else {
                    romFile.absolutePath
                }
                h.engine.insertDisc(discIndex, targetPath)
                RuntimeLogger.i("DiscSwitcher", "Mounted $targetPath for disc index $discIndex")
            }
        }
        discSwitcher.onDismissed = {
            if (isGameLoaded) {
                host?.resume()
            }
        }

        // Wire Settings Sheet Callbacks
        settings.onEditControlsClicked = {
            settings.hide()
            to?.isEditMode = true
        }
        settings.onScaleModeChanged = { mode ->
            sv.scaleMode = mode
            bezel.scaleMode = mode
            moreSheet.scaleMode = mode
            ControlsPreferences.saveScaleMode(this, mode)
        }
        settings.onOpacityChanged = { opacity ->
            to?.opacity = opacity
            ControlsPreferences.saveOpacity(this, opacity)
        }
        settings.onHapticsChanged = { haptics ->
            to?.hapticFeedbackEnabledState = haptics
            qm.hapticFeedbackEnabledState = haptics
            settings.hapticFeedbackEnabledState = haptics
            moreSheet.hapticFeedbackEnabledState = haptics
            discSwitcher.hapticFeedbackEnabledState = haptics
            remapOverlay.hapticFeedbackEnabledState = haptics
            ControlsPreferences.saveHaptics(this, haptics)
        }
        settings.onResetDefaultsClicked = {
            ControlsPreferences.resetAll(this)
            sv.scaleMode = config.runtime.videoScaleMode
            bezel.scaleMode = config.runtime.videoScaleMode
            settings.scaleMode = config.runtime.videoScaleMode
            moreSheet.scaleMode = config.runtime.videoScaleMode
            moreSheet.dpadType = DpadType.CLASSIC_CROSS
            moreSheet.joystickSnapMode = JoystickSnapMode.RPG_GRID_4WAY
            to?.let {
                it.opacity = config.controls.touchOpacity
                it.hapticFeedbackEnabledState = config.controls.haptics
                it.dpadType = if (isN64) DpadType.FIXED_JOYSTICK else DpadType.CLASSIC_CROSS
                it.joystickSnapMode = if (isN64) JoystickSnapMode.ANALOG_FREE else JoystickSnapMode.RPG_GRID_4WAY
                it.gesturesEnabled = true
                it.theme = TouchTheme.CLASSIC_INDIGO
                it.turboEnabled = false
                it.comboMacroEnabled = false
                it.resetToDefaultLayout()
            }
            sc.mode = SensorController.SensorMode.DISABLED
            sc.stop()
            gamepadMapper.resetBindingsToDefault(this)
            settings.touchOpacity = config.controls.touchOpacity
            settings.hapticsEnabled = config.controls.haptics
            qm.isControlsActive = to?.isControlsVisible ?: true
            qm.fastForwardSpeed = 1
            host?.setFastForwardMultiplier(1)
        }
        settings.onDismissed = {
            if (isGameLoaded && moreSheet.visibility != View.VISIBLE && discSwitcher.visibility != View.VISIBLE) {
                host?.resume()
            }
        }

        // Wire More Features Sheet Callbacks
        moreSheet.onSaveStateClicked = { slot ->
            host?.let { h ->
                val vBuf = h.engine.getVideoBuffer()
                ssm.saveState(
                    slot = slot,
                    engine = h.engine,
                    videoBuffer = vBuf,
                    gameTitle = config.game.title
                )
                moreSheet.invalidate()
            }
        }
        moreSheet.onLoadStateClicked = { slot ->
            host?.let { h ->
                ssm.loadState(slot, h.engine)
            }
        }
        moreSheet.onFastForwardSpeedChanged = { speed ->
            host?.setFastForwardMultiplier(speed)
            ControlsPreferences.saveFastForwardSpeed(this, speed)
            qm.fastForwardSpeed = speed
        }
        moreSheet.onMuteAudioOnFastForwardChanged = { mute ->
            host?.setMuteAudioOnFastForward(mute)
            ControlsPreferences.saveMuteAudioOnFastForward(this, mute)
        }
        moreSheet.onDpadTypeChanged = { type ->
            to?.dpadType = type
            ControlsPreferences.saveDpadType(this, type)
        }
        moreSheet.onJoystickSnapModeChanged = { snapMode ->
            to?.joystickSnapMode = snapMode
            ControlsPreferences.saveJoystickSnapMode(this, snapMode)
        }
        moreSheet.onFloatingDpadChanged = { enabled ->
            val type = if (enabled) DpadType.FLOATING_JOYSTICK else DpadType.CLASSIC_CROSS
            to?.dpadType = type
            moreSheet.dpadType = type
            ControlsPreferences.saveDpadType(this, type)
        }
        moreSheet.onGesturesChanged = { enabled ->
            to?.gesturesEnabled = enabled
            ControlsPreferences.saveGesturesEnabled(this, enabled)
        }
        moreSheet.onTurboChanged = { turbo ->
            to?.turboEnabled = turbo
            ControlsPreferences.saveTurboEnabled(this, turbo)
        }
        moreSheet.onComboMacroChanged = { combo ->
            to?.comboMacroEnabled = combo
            ControlsPreferences.saveComboMacroEnabled(this, combo)
        }
        moreSheet.onTouchThemeChanged = { themeId ->
            to?.theme = TouchTheme.fromId(themeId)
            ControlsPreferences.saveTouchTheme(this, themeId)
        }
        moreSheet.onSensorModeChanged = { modeStr ->
            val targetMode = try {
                SensorController.SensorMode.valueOf(modeStr)
            } catch (_: Throwable) {
                SensorController.SensorMode.DISABLED
            }
            sc.mode = targetMode
            if (targetMode != SensorController.SensorMode.DISABLED) {
                sc.start(this)
            } else {
                sc.stop()
            }
            ControlsPreferences.saveSensorMode(this, modeStr)
        }
        moreSheet.onCalibrateSensorClicked = {
            sc.calibrateZeroPoint()
            RuntimeLogger.i("Sensor", "Zero-point calibrated")
        }
        moreSheet.onRemapGamepadClicked = {
            remapOverlay.show()
        }
        moreSheet.onLcdGridChanged = { enabled ->
            ControlsPreferences.saveLcdGridEnabled(this, enabled)
        }
        moreSheet.onGbaColorCorrectionChanged = { enabled ->
            ControlsPreferences.saveGbaColorCorrectionEnabled(this, enabled)
        }
        moreSheet.onBezelChanged = { enabled ->
            bezel.bezelEnabled = enabled
            ControlsPreferences.saveBezelEnabled(this, enabled)
        }
        moreSheet.onShaderModeChanged = { mode ->
            sv.shaderMode = mode
            ControlsPreferences.saveShaderMode(this, mode)
        }
        moreSheet.onBezelModeChanged = { mode ->
            sv.bezelMode = mode
            bezel.bezelMode = mode
            ControlsPreferences.saveBezelMode(this, mode)
        }
        moreSheet.onScaleModeChanged = { mode ->
            sv.scaleMode = mode
            bezel.scaleMode = mode
            settings.scaleMode = mode
            ControlsPreferences.saveScaleMode(this, mode)
        }
        moreSheet.onAutoResumeChanged = { enabled ->
            ControlsPreferences.saveAutoResumeEnabled(this, enabled)
            RuntimeLogger.i("HUD", "Auto-Resume toggled: $enabled")
        }
        moreSheet.onHapticModeChanged = { mode ->
            hapticManager?.hapticMode = mode
            ControlsPreferences.saveHapticFeedbackMode(this, mode)
            RuntimeLogger.i("HUD", "Haptic Mode set to: ${mode.displayName}")
        }
        moreSheet.onRumbleStrengthChanged = { strength ->
            hapticManager?.rumbleStrength = strength
            ControlsPreferences.saveRumbleStrength(this, strength)
        }
        moreSheet.onVrrChanged = { vrr ->
            ControlsPreferences.saveDisplayVrrEnabled(this, vrr)
            DisplaySyncManager.configureDisplaySync(
                activity = this,
                surfaceView = sv,
                vrrEnabled = vrr,
                bfiEnabled = moreSheet.bfiEnabled
            )
        }
        moreSheet.onBfiChanged = { bfi ->
            sv.bfiEnabled = bfi
            ControlsPreferences.saveBfiEnabled(this, bfi)
            DisplaySyncManager.configureDisplaySync(
                activity = this,
                surfaceView = sv,
                vrrEnabled = moreSheet.vrrEnabled,
                bfiEnabled = bfi
            )
        }
        moreSheet.onWsolaChanged = { wsola ->
            host?.setWsolaEnabled(wsola)
            ControlsPreferences.saveWsolaEnabled(this, wsola)
            RuntimeLogger.i("HUD", "WSOLA Audio Time-Stretching toggled: $wsola")
        }
        moreSheet.onEditControlsClicked = {
            moreSheet.hide()
            to?.isEditMode = true
        }
        moreSheet.onSwitchDiscClicked = {
            discSwitcher.show()
        }
        moreSheet.onResetGameClicked = {
            RuntimeLogger.i("Action", "Reset Game triggered from Pause HUD")
            host?.let { h ->
                h.engine.reset()
            }
        }
        moreSheet.onExitToLauncherClicked = {
            RuntimeLogger.i("Action", "Exit to Launcher triggered from Pause HUD")
            finish()
        }
        moreSheet.onDismissed = {
            if (isGameLoaded && settings.visibility != View.VISIBLE && discSwitcher.visibility != View.VISIBLE) {
                host?.resume()
            }
        }

        to?.onEditModeChanged = { inEditMode ->
            qm.visibility = if (inEditMode) View.GONE else View.VISIBLE
        }
        to?.onLayoutSaved = {
            qm.visibility = View.VISIBLE
        }

        setContentView(root)

        // 6. Initialize Core Subsystems and Wire into EmulationHost
        val saveFile = File(storageDirectory(), SAVE_FILENAME)
        val sm = createSaveManager(saveFile)
        this.saveManager = sm

        val engine = createEngine()
        val audioPlayer = createAudioPlayer(config)

        // Initialize Modern Haptic & Gamepad Vibration Manager
        val hm = HapticManager(this).apply {
            hapticMode = effectiveHapticMode
            rumbleStrength = effectiveRumbleStrength
        }
        this.hapticManager = hm

        // Route native core motor vibration to HapticManager
        engine.onRumbleListener = { motorIndex, strength, durationMs ->
            hm.triggerNativeRumble(motorIndex, strength, durationMs)
        }

        // Route audio samples to real-time AudioHapticEngine
        audioPlayer.onAudioSamplesProcessed = { samples, count ->
            hm.audioHapticEngine.processSamples(samples, count)
        }

        gamepadMapper.onGamepadDetected = {
            to?.isControlsVisible = false
            qm.isControlsActive = false
        }

        val emulationHost = EmulationHost(
            engine = engine,
            saveManager = sm,
            audioPlayer = audioPlayer,
            renderer = sv.renderer,
            touchOverlay = touchOverlay,
            gamepadMapper = gamepadMapper,
            sensorController = sc
        ).apply {
            setFastForwardMultiplier(effectiveFastForwardSpeed)
            setMuteAudioOnFastForward(effectiveMuteAudio)
            setWsolaEnabled(effectiveWsola)
            onFrameRenderRequested = {
                sv.requestRenderFrame()
            }
        }
        this.host = emulationHost

        // 7. Bootstrap Emulation if ROM is staged and verified
        if (romFile.exists() && romFile.length() > 0L) {
            RuntimeLogger.i("Bootstrap", "Loading ROM into EmulationHost: ${romFile.absolutePath} (${romFile.length()} bytes)")
            isGameLoaded = emulationHost.loadGame(
                romFile = romFile,
                internalStorageDir = storageDirectory(),
                saveFile = saveFile
            )
            RuntimeLogger.i("Bootstrap", "EmulationHost.loadGame result: $isGameLoaded (engine state: ${engine.state})")
            if (isGameLoaded) {
                // Instant Resume: Attempt frame 0 restoration from auto-resume snapshot
                if (effectiveAutoResume) {
                    val romSha = config.game.romSha256.ifEmpty { romFile.name }
                    if (AutoResumeManager.hasValidAutoResume(storageDirectory(), romSha)) {
                        val restored = AutoResumeManager.restoreAutoResume(
                            storageDir = storageDirectory(),
                            engine = emulationHost.engine,
                            romSha256 = romSha
                        )
                        if (restored) {
                            RuntimeLogger.i("Bootstrap", "Instant Resume: Restored exact frame 0 from background snapshot")
                        }
                    }
                }
                emulationHost.start()
                RuntimeLogger.i("Bootstrap", "EmulationHost active loop started successfully")
            } else {
                val errorDetails = buildString {
                    val activeCore = (engine as? NativeEmulationEngine)?.core ?: NativeCore
                    appendLine("Failed to initialize or parse ROM in ${config.runtime.core} (${config.game.platform}) core.")
                    appendLine("• Engine State: ${engine.state}")
                    appendLine("• NativeCore loaded: ${activeCore.isLoaded()}")
                }
                RuntimeLogger.e("Bootstrap", errorDetails)
                showDiagnosticAlert(root, errorDetails)
            }
        } else {
            val errorMsg = "ROM file not found or empty at '${romFile.absolutePath}'. Staging from APK assets failed."
            RuntimeLogger.e("Bootstrap", errorMsg)
            showDiagnosticAlert(root, errorMsg)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        surfaceView?.resume()
        if (sensorController?.mode != SensorController.SensorMode.DISABLED) {
            sensorController?.start(this)
        }
        RuntimeLogger.i("Lifecycle", "onResume: isGameLoaded=$isGameLoaded")
        host?.let {
            if (isGameLoaded && moreFeaturesSheet?.isShowing() != true && settingsOverlay?.isShowing() != true) {
                it.resume()
            }
        }
    }

    override fun onPause() {
        RuntimeLogger.i("Lifecycle", "onPause: pausing emulation, capturing auto-snapshot and flushing SRAM")
        sensorController?.stop()
        host?.pause()
        // Feature 1: Instant Resume background snapshot capture
        if (isGameLoaded && host != null) {
            val autoResumeEnabled = ControlsPreferences.loadAutoResumeEnabled(this, true)
            if (autoResumeEnabled) {
                val romSha = config.game.romSha256.ifEmpty { ROM_FILENAME }
                AutoResumeManager.saveAutoSnapshot(
                    storageDir = storageDirectory(),
                    engine = host!!.engine,
                    romSha256 = romSha,
                    gameTitle = config.game.title
                )
            }
        }
        // Invariant 5: Guarantees synchronous POSIX fsync cartridge SRAM flush to flash memory
        saveManager?.flushNow()
        surfaceView?.pause()
        super.onPause()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND && isGameLoaded && host != null) {
            val autoResumeEnabled = ControlsPreferences.loadAutoResumeEnabled(this, true)
            if (autoResumeEnabled) {
                val romSha = config.game.romSha256.ifEmpty { ROM_FILENAME }
                AutoResumeManager.saveAutoSnapshot(
                    storageDir = storageDirectory(),
                    engine = host!!.engine,
                    romSha256 = romSha,
                    gameTitle = config.game.title
                )
            }
        }
    }

    override fun onStop() {
        RuntimeLogger.i("Lifecycle", "onStop: flushing SRAM")
        // Invariant 5: Guarantees synchronous POSIX fsync cartridge SRAM flush on activity backgrounding
        saveManager?.flushNow()
        super.onStop()
    }

    override fun onDestroy() {
        RuntimeLogger.i("Lifecycle", "onDestroy: closing host and stopping logger")
        sensorController?.stop()
        saveManager?.flushNow()
        host?.close()
        host = null
        RuntimeLogger.stop(this)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (gamepadRemapOverlay?.isShowing() == true) {
            if (gamepadRemapOverlay?.handleKeyEvent(event) == true) {
                return true
            }
        }
        if (host?.inputCoordinator?.gamepadMapper?.handleKeyEvent(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepadRemapOverlay?.isShowing() == true) {
            if (gamepadRemapOverlay?.handleMotionEvent(event) == true) {
                return true
            }
        }
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
                RuntimeLogger.i("Config", "Loaded runtime config: $jsonText")
                RuntimeConfig.fromJson(jsonText)
            }
        } catch (e: Exception) {
            RuntimeLogger.w("Config", "assets.open('${RuntimeConfig.ASSET_PATH}') failed: ${e.message}, using DEFAULT", e)
            RuntimeConfig.DEFAULT
        }
    }

    /**
     * Stages ROM from APK assets to [targetFile] if not already present or checksum differs.
     */
    protected open fun stageRomIfNeeded(targetFile: File, expectedSha256: String): Boolean {
        if (RomStager.isRomStaged(targetFile, expectedSha256)) {
            RuntimeLogger.i("RomStager", "ROM already staged and valid at ${targetFile.absolutePath}")
            return true
        }

        return try {
            assets.open(ROM_FILENAME).use { stream ->
                val ok = RomStager.stageRom(stream, targetFile, expectedSha256)
                RuntimeLogger.i("RomStager", "RomStager.stageRom returned: $ok")
                ok
            }
        } catch (e: Exception) {
            RuntimeLogger.e("RomStager", "Failed staging ROM asset '$ROM_FILENAME' to '${targetFile.absolutePath}'", e)
            false
        }
    }

    private fun showDiagnosticAlert(root: FrameLayout, details: String) {
        runOnUiThread {
            try {
                val container = LinearLayout(this).apply {
                    setOrientation(LinearLayout.VERTICAL)
                    setBackgroundColor(0xF00A0C10.toInt())
                    setPadding(48, 80, 48, 48)
                }

                val title = TextView(this).apply {
                    text = "⚠️ RetroPack Runtime Alert"
                    setTextSize(20f)
                    setTextColor(0xFFFF5252.toInt())
                }
                container.addView(title)

                val body = TextView(this).apply {
                    text = details
                    setTextSize(14f)
                    setTextColor(0xFFE0E0E0.toInt())
                }
                container.addView(body)

                val logPathInfo = TextView(this).apply {
                    val path = RuntimeLogger.logFile?.absolutePath ?: "Unavailable"
                    text = "\nDiagnostic log saved to:\n$path\n"
                    setTextSize(12f)
                    setTextColor(0xFF80D8FF.toInt())
                }
                container.addView(logPathInfo)

                val shareBtn = Button(this).apply {
                    text = "Share Diagnostic Log"
                    setOnClickListener {
                        try {
                            val file = RuntimeLogger.logFile
                            val text = if (file != null && file.exists()) file.readText() else details
                            val sendIntent = android.content.Intent().apply {
                                action = "android.intent.action.SEND"
                                type = "text/plain"
                                putExtra("android.intent.extra.TEXT", text)
                            }
                            startActivity(android.content.Intent.createChooser(sendIntent, "Share RetroPack Log"))
                        } catch (_: Throwable) {}
                    }
                }
                container.addView(shareBtn)

                root.addView(container, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            } catch (_: Throwable) {}
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        touchOverlay?.let { to ->
            val dm = resources.displayMetrics
            to.updateLayout(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
        }
        bezelOverlay?.invalidate()
    }

    // Factory methods for dependency injection and test isolation
    protected open fun createEngine(): EmulationEngine {
        val core = NativeCoreFactory.createCore(config)
        return NativeEmulationEngine(core = core)
    }

    protected open fun createSurfaceView(): RetroSurfaceView =
        RetroSurfaceView(
            context = this,
            renderer = RetroGlRenderer(
                initialScaleMode = config.runtime.videoScaleMode,
                frameBufferSupplier = { host?.engine?.getVideoBuffer() ?: NativeCore.nativeGetVideoBuffer() }
            )
        )
    protected open fun createBezelOverlay(): BezelOverlayView = BezelOverlayView(this)
    protected open fun createTouchOverlay(): TouchOverlayView = TouchOverlayView(this)
    protected open fun createAudioPlayer(cfg: RuntimeConfig): RetroAudioPlayer =
        RetroAudioPlayer(driftController = com.retropack.runtime.audio.AudioDriftController(cfg.runtime.audioSampleRate, 2))
    protected open fun createSaveManager(saveFile: File): SaveManager = SaveManager(saveFile = saveFile)
    protected open fun createSaveStateManager(storageDir: File): SaveStateManager = SaveStateManager(storageDir = storageDir)
    protected open fun createGamepadMapper(): GamepadMapper = GamepadMapper()
    protected open fun createQuickMenu(): QuickMenuOverlay = QuickMenuOverlay(this)
    protected open fun createSettingsOverlay(): InGameSettingsOverlay = InGameSettingsOverlay(this)
    protected open fun createMoreFeaturesSheet(): MoreFeaturesSheet = MoreFeaturesSheet(this)
    protected open fun createDiscSwitcherOverlay(): DiscSwitcherOverlay = DiscSwitcherOverlay(this)
    protected open fun createSensorController(): SensorController = SensorController()
    protected open fun createGamepadRemapOverlay(): GamepadRemapOverlay = GamepadRemapOverlay(this)
}
