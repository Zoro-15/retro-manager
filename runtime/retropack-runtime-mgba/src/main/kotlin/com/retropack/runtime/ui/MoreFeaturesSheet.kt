package com.retropack.runtime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.retropack.runtime.core.ScaleMode
import com.retropack.runtime.input.DpadType
import com.retropack.runtime.input.HapticEngine
import com.retropack.runtime.input.JoystickSnapMode
import com.retropack.runtime.save.SaveStateManager

/**
 * In-Game "More" App Sheet Hub for RetroPack.
 *
 * Provides a dark glassmorphic modal settings and feature hub organized into:
 *  1. 💾 Save States Hub: Visual 5-slot manager (+ Auto-Save Slot 0) with thumbnails & relative timestamps.
 *  2. ⚡ Emulation Speed & Audio: Fast-forward multiplier (1x, 2x, 4x, 8x, Max) & audio muting toggle.
 *  3. 🎮 Controls & Superpowers: D-Pad Type (Classic Cross | Fixed Joystick | Floating Joystick),
 *     RPG Grid Snapping (60° cardinal vs 8-way action), Multi-Touch Gestures, Turbo buttons,
 *     A+B macro pill, themes, and controller remapping.
 *  4. 📱 Motion Sensors & Gyro: D-Pad tilt steering, native gyro cartridge emulation, calibration zero-point.
 *  5. 📺 Display & Shaders: LCD grid filter, GBA color correction, handheld bezels, and scaling mode.
 */
class MoreFeaturesSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        visibility = GONE
    }

    var gameTitle: String = "RetroPack"
        set(value) {
            field = value
            invalidate()
        }

    var selectedSlot: Int = 1
        set(value) {
            field = value.coerceIn(0, 5)
            invalidate()
        }

    var fastForwardSpeed: Int = 1
        set(value) {
            field = value
            invalidate()
        }

    var muteAudioOnFastForward: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var dpadType: DpadType = DpadType.CLASSIC_CROSS
        set(value) {
            field = value
            floatingDpadEnabled = (value == DpadType.FLOATING_JOYSTICK)
            invalidate()
        }

    var joystickSnapMode: JoystickSnapMode = JoystickSnapMode.RPG_GRID_4WAY
        set(value) {
            field = value
            invalidate()
        }

    var floatingDpadEnabled: Boolean = false
        set(value) {
            field = value
            if (value && dpadType != DpadType.FLOATING_JOYSTICK) {
                dpadType = DpadType.FLOATING_JOYSTICK
            } else if (!value && dpadType == DpadType.FLOATING_JOYSTICK) {
                dpadType = DpadType.CLASSIC_CROSS
            }
            invalidate()
        }

    var gesturesEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var turboButtonsEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var comboMacroEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var touchTheme: String = "classic_indigo"
        set(value) {
            field = value
            invalidate()
        }

    var sensorMode: String = "DISABLED"
        set(value) {
            field = value
            invalidate()
        }

    var lcdGridEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var gbaColorCorrectionEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var bezelEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var scaleMode: ScaleMode = ScaleMode.ASPECT_FIT
        set(value) {
            field = value
            invalidate()
        }

    var hapticFeedbackEnabledState: Boolean = true
    var hapticIntensity: Float = 1.0f

    var saveStateManager: SaveStateManager? = null

    // Callbacks
    var onSaveStateClicked: ((Int) -> Unit)? = null
    var onLoadStateClicked: ((Int) -> Unit)? = null
    var onFastForwardSpeedChanged: ((Int) -> Unit)? = null
    var onMuteAudioOnFastForwardChanged: ((Boolean) -> Unit)? = null
    var onDpadTypeChanged: ((DpadType) -> Unit)? = null
    var onJoystickSnapModeChanged: ((JoystickSnapMode) -> Unit)? = null
    var onFloatingDpadChanged: ((Boolean) -> Unit)? = null
    var onGesturesChanged: ((Boolean) -> Unit)? = null
    var onTurboChanged: ((Boolean) -> Unit)? = null
    var onComboMacroChanged: ((Boolean) -> Unit)? = null
    var onTouchThemeChanged: ((String) -> Unit)? = null
    var onSensorModeChanged: ((String) -> Unit)? = null
    var onCalibrateSensorClicked: (() -> Unit)? = null
    var onRemapGamepadClicked: (() -> Unit)? = null
    var onLcdGridChanged: ((Boolean) -> Unit)? = null
    var onGbaColorCorrectionChanged: ((Boolean) -> Unit)? = null
    var onBezelChanged: ((Boolean) -> Unit)? = null
    var onScaleModeChanged: ((ScaleMode) -> Unit)? = null
    var onEditControlsClicked: (() -> Unit)? = null
    var onDismissed: (() -> Unit)? = null
    var onHapticFeedbackRequested: (() -> Unit)? = null

    // Hit Testing Rectangles
    val cardRect = RectF()
    val closeBtnRect = RectF()

    // Save States Hub Rects
    val slotCardRects = Array(6) { RectF() } // Slot 0 to 5
    val saveStateBtnRect = RectF()
    val loadStateBtnRect = RectF()

    // Fast-Forward & Audio Rects
    val speedRects = Array(5) { RectF() } // 1x, 2x, 4x, 8x, Max
    val muteFfToggleRect = RectF()

    // Controls & Superpowers Rects
    val dpadTypeRects = Array(3) { RectF() } // Classic Cross, Fixed Stick, Floating Stick
    val snapModeRects = Array(2) { RectF() } // RPG Grid 4-Way (60°), 8-Way Action
    val floatingDpadToggleRect = RectF()
    val gesturesToggleRect = RectF()
    val turboToggleRect = RectF()
    val comboMacroToggleRect = RectF()
    val themeRects = Array(4) { RectF() } // Indigo, Glacier, Onyx, DMG
    val gamepadRemapBtnRect = RectF()
    val editControlsBtnRect = RectF()

    // Motion Sensors & Gyro Rects
    val sensorModeRects = Array(3) { RectF() } // Off, D-Pad Tilt, Native Gyro
    val sensorCalibrateBtnRect = RectF()

    // Display & Shaders Rects
    val lcdGridToggleRect = RectF()
    val gbaColorToggleRect = RectF()
    val bezelToggleRect = RectF()
    val scaleAspectRect = RectF()
    val scaleIntegerRect = RectF()

    val doneBtnRect = RectF()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        color = Color.WHITE
    }
    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private val speedMultipliers = intArrayOf(1, 2, 4, 8, 16)
    private val speedLabels = arrayOf("1x", "2x", "4x", "8x", "Max")
    private val dpadTypeLabels = arrayOf("Classic Cross", "Fixed Stick", "Floating Stick")
    private val snapModeLabels = arrayOf("RPG Grid (60°)", "8-Way Action")
    private val themeNames = arrayOf("classic_indigo", "glacier", "onyx_stealth", "retro_dmg")
    private val themeLabels = arrayOf("Indigo", "Glacier", "Onyx", "DMG")
    private val sensorModes = arrayOf("DISABLED", "DPAD_EMULATION", "NATIVE_GYRO")
    private val sensorModeLabels = arrayOf("Sensor Off", "D-Pad Tilt", "Native Gyro")

    fun show() {
        visibility = VISIBLE
        invalidate()
    }

    fun hide() {
        if (visibility == VISIBLE) {
            visibility = GONE
            onDismissed?.invoke()
            invalidate()
        }
    }

    fun isShowing(): Boolean = visibility == VISIBLE

    fun setSheetDimensions(w: Int, h: Int) {
        updateLayoutGeometry(w.toFloat(), h.toFloat())
    }

    fun updateLayoutGeometry(viewW: Float, viewH: Float) {
        val cardW = Math.min(viewW * 0.95f, 820f)
        val cardH = Math.min(viewH * 0.98f, 1160f)
        val cardLeft = (viewW - cardW) * 0.5f
        val cardTop = (viewH - cardH) * 0.5f
        cardRect.set(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)

        closeBtnRect.set(cardRect.right - 58f, cardTop + 16f, cardRect.right - 18f, cardTop + 56f)

        val contentLeft = cardLeft + 28f
        val contentW = cardW - 56f
        var cursorY = cardTop + 56f

        // ─── 1. Save States Hub ───
        cursorY += 18f
        val slotSpacing = 8f
        val slotW = (contentW - (slotSpacing * 5)) / 6f
        val slotH = 52f
        for (i in 0 until 6) {
            val sLeft = contentLeft + i * (slotW + slotSpacing)
            slotCardRects[i].set(sLeft, cursorY, sLeft + slotW, cursorY + slotH)
        }

        cursorY += slotH + 10f
        val stateBtnW = (contentW - 14f) * 0.5f
        val stateBtnH = 40f
        saveStateBtnRect.set(contentLeft, cursorY, contentLeft + stateBtnW, cursorY + stateBtnH)
        loadStateBtnRect.set(contentLeft + stateBtnW + 14f, cursorY, contentLeft + contentW, cursorY + stateBtnH)

        // ─── 2. Emulation Speed & Audio ───
        cursorY += stateBtnH + 18f
        val speedSpacing = 8f
        val speedW = (contentW - (speedSpacing * 4)) / 5f
        val speedH = 34f
        for (i in 0 until 5) {
            val spLeft = contentLeft + i * (speedW + speedSpacing)
            speedRects[i].set(spLeft, cursorY, spLeft + speedW, cursorY + speedH)
        }

        cursorY += speedH + 10f
        val toggleW = 82f
        val toggleH = 30f
        muteFfToggleRect.set(cardRect.right - 28f - toggleW, cursorY, cardRect.right - 28f, cursorY + toggleH)

        // ─── 3. Controls & Superpowers ───
        cursorY += toggleH + 18f

        // D-Pad Type 3-Way Segment Selector
        val dpadSpacing = 8f
        val dpadTabW = (contentW - (dpadSpacing * 2)) / 3f
        val dpadTabH = 34f
        for (i in 0 until 3) {
            val dpLeft = contentLeft + i * (dpadTabW + dpadSpacing)
            dpadTypeRects[i].set(dpLeft, cursorY, dpLeft + dpadTabW, cursorY + dpadTabH)
        }
        // Keep floatingDpadToggleRect mapped for compatibility
        floatingDpadToggleRect.set(dpadTypeRects[2])

        cursorY += dpadTabH + 8f

        // Joystick Grid Snapping 2-Way Segment Selector
        val snapSpacing = 8f
        val snapTabW = (contentW - snapSpacing) / 2f
        val snapTabH = 32f
        for (i in 0 until 2) {
            val snLeft = contentLeft + i * (snapTabW + snapSpacing)
            snapModeRects[i].set(snLeft, cursorY, snLeft + snapTabW, cursorY + snapTabH)
        }

        cursorY += snapTabH + 10f
        gesturesToggleRect.set(cardRect.right - 28f - toggleW, cursorY, cardRect.right - 28f, cursorY + toggleH)

        cursorY += toggleH + 8f
        turboToggleRect.set(cardRect.right - 28f - toggleW, cursorY, cardRect.right - 28f, cursorY + toggleH)

        cursorY += toggleH + 8f
        comboMacroToggleRect.set(cardRect.right - 28f - toggleW, cursorY, cardRect.right - 28f, cursorY + toggleH)

        cursorY += toggleH + 10f
        val themeSpacing = 8f
        val themeW = (contentW - (themeSpacing * 3)) / 4f
        val themeH = 32f
        for (i in 0 until 4) {
            val thLeft = contentLeft + i * (themeW + themeSpacing)
            themeRects[i].set(thLeft, cursorY, thLeft + themeW, cursorY + themeH)
        }

        cursorY += themeH + 10f
        val dualBtnW = (contentW - 12f) * 0.5f
        val dualBtnH = 38f
        gamepadRemapBtnRect.set(contentLeft, cursorY, contentLeft + dualBtnW, cursorY + dualBtnH)
        editControlsBtnRect.set(contentLeft + dualBtnW + 12f, cursorY, contentLeft + contentW, cursorY + dualBtnH)

        // ─── 4. Motion Sensors & Gyro ───
        cursorY += dualBtnH + 18f
        val sensorSpacing = 8f
        val sensorW = (contentW - (sensorSpacing * 2)) / 3f
        val sensorH = 34f
        for (i in 0 until 3) {
            val smLeft = contentLeft + i * (sensorW + sensorSpacing)
            sensorModeRects[i].set(smLeft, cursorY, smLeft + sensorW, cursorY + sensorH)
        }

        cursorY += sensorH + 8f
        sensorCalibrateBtnRect.set(contentLeft, cursorY, contentLeft + contentW, cursorY + 34f)

        // ─── 5. Display & Shaders ───
        cursorY += 34f + 18f
        lcdGridToggleRect.set(cardRect.right - 28f - toggleW, cursorY, cardRect.right - 28f, cursorY + toggleH)

        cursorY += toggleH + 8f
        gbaColorToggleRect.set(cardRect.right - 28f - toggleW, cursorY, cardRect.right - 28f, cursorY + toggleH)

        cursorY += toggleH + 8f
        bezelToggleRect.set(cardRect.right - 28f - toggleW, cursorY, cardRect.right - 28f, cursorY + toggleH)

        cursorY += toggleH + 8f
        val segW = (contentW - 12f) * 0.5f
        val segH = 34f
        scaleAspectRect.set(contentLeft, cursorY, contentLeft + segW, cursorY + segH)
        scaleIntegerRect.set(contentLeft + segW + 12f, cursorY, contentLeft + contentW, cursorY + segH)

        // ─── Footer Done Button ───
        val doneH = 46f
        doneBtnRect.set(contentLeft, cardRect.bottom - doneH - 16f, contentLeft + contentW, cardRect.bottom - 16f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false

        val viewW = if (width > 0) width.toFloat() else 1080f
        val viewH = if (height > 0) height.toFloat() else 1920f
        updateLayoutGeometry(viewW, viewH)

        val x = event.x
        val y = event.y

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            // Outside tap -> dismiss
            if (!cardRect.contains(x, y)) {
                hide()
                return true
            }

            // Close button
            if (closeBtnRect.contains(x, y)) {
                triggerHaptic()
                hide()
                return true
            }

            // Save State Slots 0..5
            for (i in slotCardRects.indices) {
                if (slotCardRects[i].contains(x, y)) {
                    triggerHaptic()
                    selectedSlot = i
                    return true
                }
            }

            // Save State Action Button
            if (saveStateBtnRect.contains(x, y)) {
                triggerHaptic()
                onSaveStateClicked?.invoke(selectedSlot)
                invalidate()
                return true
            }

            // Load State Action Button
            if (loadStateBtnRect.contains(x, y)) {
                triggerHaptic()
                onLoadStateClicked?.invoke(selectedSlot)
                hide()
                return true
            }

            // Fast-Forward Multipliers
            for (i in speedRects.indices) {
                if (speedRects[i].contains(x, y)) {
                    triggerHaptic()
                    fastForwardSpeed = speedMultipliers[i]
                    onFastForwardSpeedChanged?.invoke(fastForwardSpeed)
                    return true
                }
            }

            // Mute on FF Toggle
            if (muteFfToggleRect.contains(x, y)) {
                triggerHaptic()
                muteAudioOnFastForward = !muteAudioOnFastForward
                onMuteAudioOnFastForwardChanged?.invoke(muteAudioOnFastForward)
                return true
            }

            // D-Pad Type Segmented Buttons
            for (i in dpadTypeRects.indices) {
                if (dpadTypeRects[i].contains(x, y)) {
                    triggerHaptic()
                    val newType = when (i) {
                        0 -> DpadType.CLASSIC_CROSS
                        1 -> DpadType.FIXED_JOYSTICK
                        else -> DpadType.FLOATING_JOYSTICK
                    }
                    dpadType = newType
                    floatingDpadEnabled = (newType == DpadType.FLOATING_JOYSTICK)
                    onDpadTypeChanged?.invoke(newType)
                    onFloatingDpadChanged?.invoke(floatingDpadEnabled)
                    return true
                }
            }

            // Joystick Grid Snapping Mode
            for (i in snapModeRects.indices) {
                if (snapModeRects[i].contains(x, y)) {
                    triggerHaptic()
                    val newSnap = if (i == 0) JoystickSnapMode.RPG_GRID_4WAY else JoystickSnapMode.ACTION_8WAY
                    joystickSnapMode = newSnap
                    onJoystickSnapModeChanged?.invoke(newSnap)
                    return true
                }
            }

            // Floating Dynamic D-Pad Toggle (Direct hit-test compatibility)
            if (floatingDpadToggleRect.contains(x, y)) {
                triggerHaptic()
                floatingDpadEnabled = !floatingDpadEnabled
                dpadType = if (floatingDpadEnabled) DpadType.FLOATING_JOYSTICK else DpadType.CLASSIC_CROSS
                onFloatingDpadChanged?.invoke(floatingDpadEnabled)
                onDpadTypeChanged?.invoke(dpadType)
                return true
            }

            // Gestures Toggle
            if (gesturesToggleRect.contains(x, y)) {
                triggerHaptic()
                gesturesEnabled = !gesturesEnabled
                onGesturesChanged?.invoke(gesturesEnabled)
                return true
            }

            // Turbo Toggle
            if (turboToggleRect.contains(x, y)) {
                triggerHaptic()
                turboButtonsEnabled = !turboButtonsEnabled
                onTurboChanged?.invoke(turboButtonsEnabled)
                return true
            }

            // Combo Macro Toggle
            if (comboMacroToggleRect.contains(x, y)) {
                triggerHaptic()
                comboMacroEnabled = !comboMacroEnabled
                onComboMacroChanged?.invoke(comboMacroEnabled)
                return true
            }

            // Theme Selector
            for (i in themeRects.indices) {
                if (themeRects[i].contains(x, y)) {
                    triggerHaptic()
                    touchTheme = themeNames[i]
                    onTouchThemeChanged?.invoke(touchTheme)
                    return true
                }
            }

            // Gamepad Remap Button
            if (gamepadRemapBtnRect.contains(x, y)) {
                triggerHaptic()
                hide()
                onRemapGamepadClicked?.invoke()
                return true
            }

            // Edit Controls Button
            if (editControlsBtnRect.contains(x, y)) {
                triggerHaptic()
                hide()
                onEditControlsClicked?.invoke()
                return true
            }

            // Sensor Modes
            for (i in sensorModeRects.indices) {
                if (sensorModeRects[i].contains(x, y)) {
                    triggerHaptic()
                    sensorMode = sensorModes[i]
                    onSensorModeChanged?.invoke(sensorMode)
                    return true
                }
            }

            // Calibrate Sensor Button
            if (sensorCalibrateBtnRect.contains(x, y)) {
                triggerHaptic()
                onCalibrateSensorClicked?.invoke()
                return true
            }

            // LCD Grid Toggle
            if (lcdGridToggleRect.contains(x, y)) {
                triggerHaptic()
                lcdGridEnabled = !lcdGridEnabled
                onLcdGridChanged?.invoke(lcdGridEnabled)
                return true
            }

            // GBA Color Correction Toggle
            if (gbaColorToggleRect.contains(x, y)) {
                triggerHaptic()
                gbaColorCorrectionEnabled = !gbaColorCorrectionEnabled
                onGbaColorCorrectionChanged?.invoke(gbaColorCorrectionEnabled)
                return true
            }

            // Bezel Toggle
            if (bezelToggleRect.contains(x, y)) {
                triggerHaptic()
                bezelEnabled = !bezelEnabled
                onBezelChanged?.invoke(bezelEnabled)
                return true
            }

            // Scale Aspect
            if (scaleAspectRect.contains(x, y)) {
                triggerHaptic()
                scaleMode = ScaleMode.ASPECT_FIT
                onScaleModeChanged?.invoke(scaleMode)
                return true
            }

            // Scale Integer
            if (scaleIntegerRect.contains(x, y)) {
                triggerHaptic()
                scaleMode = ScaleMode.INTEGER_FIT
                onScaleModeChanged?.invoke(scaleMode)
                return true
            }

            // Done Button
            if (doneBtnRect.contains(x, y)) {
                triggerHaptic()
                hide()
                return true
            }
        }
        return true
    }

    private fun triggerHaptic() {
        if (hapticFeedbackEnabledState) {
            HapticEngine.triggerClick(context, hapticIntensity, this)
            onHapticFeedbackRequested?.invoke()
        }
    }

    fun renderForTesting(canvas: Canvas) {
        onDraw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE) return

        val viewW = if (width > 0) width.toFloat() else 1080f
        val viewH = if (height > 0) height.toFloat() else 1920f
        updateLayoutGeometry(viewW, viewH)

        val contentLeft = cardRect.left + 28f
        val contentW = cardRect.width() - 56f

        // ─── 1. Modal Dim Scrim ───
        bgPaint.color = Color.argb(175, 5, 6, 10)
        canvas.drawRect(0f, 0f, viewW, viewH, bgPaint)

        // ─── 2. Glassmorphic Card Container ───
        bgPaint.color = Color.argb(248, 12, 14, 22)
        canvas.drawRoundRect(cardRect, 24f, 24f, bgPaint)

        cardBorderPaint.color = Color.argb(120, 79, 70, 229)
        cardBorderPaint.strokeWidth = 2.0f
        canvas.drawRoundRect(cardRect, 24f, 24f, cardBorderPaint)

        // ─── 3. Header Bar ───
        textPaint.textSize = 22f
        textPaint.color = Color.WHITE
        textPaint.isFakeBoldText = true
        canvas.drawText("Game Settings & Features", contentLeft, cardRect.top + 38f, textPaint)
        textPaint.isFakeBoldText = false

        // Close Button (✕)
        bgPaint.color = Color.argb(180, 28, 32, 48)
        canvas.drawRoundRect(closeBtnRect, 10f, 10f, bgPaint)
        centerTextPaint.textSize = 20f
        centerTextPaint.color = Color.argb(220, 255, 255, 255)
        canvas.drawText("✕", closeBtnRect.centerX(), closeBtnRect.centerY() + 7f, centerTextPaint)

        // ─── 4. Section: Save States Hub ───
        var cursorY = cardRect.top + 68f
        textPaint.textSize = 15f
        textPaint.color = Color.argb(255, 129, 140, 248)
        textPaint.isFakeBoldText = true
        canvas.drawText("SAVE STATES HUB", contentLeft, cursorY, textPaint)
        textPaint.isFakeBoldText = false

        for (i in 0 until 6) {
            val isSelected = (selectedSlot == i)
            val sRect = slotCardRects[i]

            bgPaint.color = if (isSelected) Color.argb(255, 79, 70, 229)
                            else Color.argb(200, 20, 23, 34)
            canvas.drawRoundRect(sRect, 12f, 12f, bgPaint)

            cardBorderPaint.color = if (isSelected) Color.argb(255, 165, 180, 252)
                                    else Color.argb(100, 79, 70, 229)
            cardBorderPaint.strokeWidth = if (isSelected) 2.5f else 1.5f
            canvas.drawRoundRect(sRect, 12f, 12f, cardBorderPaint)

            val slotLabel = if (i == 0) "Auto" else "Slot $i"
            centerTextPaint.textSize = 15f
            centerTextPaint.color = if (isSelected) Color.WHITE else Color.argb(240, 199, 210, 254)
            centerTextPaint.isFakeBoldText = isSelected
            canvas.drawText(slotLabel, sRect.centerX(), sRect.centerY() - 2f, centerTextPaint)

            val ssm = saveStateManager
            val slotStatus = if (ssm != null && ssm.hasState(i)) "Saved" else "Empty"
            centerTextPaint.textSize = 12f
            centerTextPaint.color = if (isSelected) Color.argb(220, 224, 231, 255) else Color.argb(140, 148, 163, 184)
            centerTextPaint.isFakeBoldText = false
            canvas.drawText(slotStatus, sRect.centerX(), sRect.centerY() + 14f, centerTextPaint)
        }

        // Save State Action Button
        bgPaint.color = Color.argb(255, 79, 70, 229)
        canvas.drawRoundRect(saveStateBtnRect, 12f, 12f, bgPaint)
        centerTextPaint.textSize = 16f
        centerTextPaint.color = Color.WHITE
        centerTextPaint.isFakeBoldText = true
        canvas.drawText("💾 Save to Slot $selectedSlot", saveStateBtnRect.centerX(), saveStateBtnRect.centerY() + 6f, centerTextPaint)

        // Load State Action Button
        bgPaint.color = Color.argb(220, 28, 32, 48)
        canvas.drawRoundRect(loadStateBtnRect, 12f, 12f, bgPaint)
        cardBorderPaint.color = Color.argb(160, 99, 102, 241)
        cardBorderPaint.strokeWidth = 1.5f
        canvas.drawRoundRect(loadStateBtnRect, 12f, 12f, cardBorderPaint)
        centerTextPaint.color = Color.argb(255, 165, 180, 252)
        canvas.drawText("📂 Load State", loadStateBtnRect.centerX(), loadStateBtnRect.centerY() + 6f, centerTextPaint)
        centerTextPaint.isFakeBoldText = false

        // ─── 5. Section: Fast-Forward Speed & Audio ───
        cursorY = saveStateBtnRect.bottom + 18f
        textPaint.textSize = 15f
        textPaint.color = Color.argb(255, 129, 140, 248)
        textPaint.isFakeBoldText = true
        canvas.drawText("EMULATION SPEED & AUDIO", contentLeft, cursorY, textPaint)
        textPaint.isFakeBoldText = false

        for (i in speedRects.indices) {
            val isSelected = (fastForwardSpeed == speedMultipliers[i])
            val sRect = speedRects[i]
            bgPaint.color = if (isSelected) Color.argb(255, 79, 70, 229) else Color.argb(200, 18, 20, 30)
            canvas.drawRoundRect(sRect, 12f, 12f, bgPaint)
            cardBorderPaint.color = if (isSelected) Color.argb(255, 165, 180, 252) else Color.argb(120, 49, 46, 129)
            cardBorderPaint.strokeWidth = if (isSelected) 2.5f else 1.5f
            canvas.drawRoundRect(sRect, 12f, 12f, cardBorderPaint)

            centerTextPaint.textSize = 15f
            centerTextPaint.color = if (isSelected) Color.WHITE else Color.argb(240, 199, 210, 254)
            centerTextPaint.isFakeBoldText = isSelected
            canvas.drawText(speedLabels[i], sRect.centerX(), sRect.centerY() + 5.5f, centerTextPaint)
        }
        centerTextPaint.isFakeBoldText = false

        // Mute Audio Toggle
        textPaint.textSize = 16f
        textPaint.color = Color.WHITE
        textPaint.isFakeBoldText = true
        canvas.drawText("Mute Audio on Fast-Forward", contentLeft, muteFfToggleRect.centerY() + 5f, textPaint)
        textPaint.isFakeBoldText = false
        drawToggleSwitch(canvas, muteFfToggleRect, muteAudioOnFastForward)

        // ─── 6. Section: Controls & Superpowers ───
        cursorY = muteFfToggleRect.bottom + 18f
        textPaint.textSize = 15f
        textPaint.color = Color.argb(255, 129, 140, 248)
        textPaint.isFakeBoldText = true
        canvas.drawText("CONTROLS & SUPERPOWERS", contentLeft, cursorY, textPaint)
        textPaint.isFakeBoldText = false

        // D-Pad Type Segmented Tabs
        for (i in dpadTypeRects.indices) {
            val isSelected = when (i) {
                0 -> dpadType == DpadType.CLASSIC_CROSS
                1 -> dpadType == DpadType.FIXED_JOYSTICK
                else -> dpadType == DpadType.FLOATING_JOYSTICK
            }
            val dpRect = dpadTypeRects[i]
            bgPaint.color = if (isSelected) Color.argb(255, 79, 70, 229) else Color.argb(200, 18, 20, 30)
            canvas.drawRoundRect(dpRect, 12f, 12f, bgPaint)
            cardBorderPaint.color = if (isSelected) Color.argb(255, 165, 180, 252) else Color.argb(120, 49, 46, 129)
            cardBorderPaint.strokeWidth = if (isSelected) 2.5f else 1.5f
            canvas.drawRoundRect(dpRect, 12f, 12f, cardBorderPaint)

            centerTextPaint.textSize = 14f
            centerTextPaint.color = if (isSelected) Color.WHITE else Color.argb(240, 199, 210, 254)
            centerTextPaint.isFakeBoldText = isSelected
            canvas.drawText(dpadTypeLabels[i], dpRect.centerX(), dpRect.centerY() + 5f, centerTextPaint)
        }
        centerTextPaint.isFakeBoldText = false

        // Joystick Snapping Mode Tabs
        for (i in snapModeRects.indices) {
            val isSelected = (i == 0 && joystickSnapMode == JoystickSnapMode.RPG_GRID_4WAY) ||
                    (i == 1 && joystickSnapMode == JoystickSnapMode.ACTION_8WAY)
            val snRect = snapModeRects[i]
            bgPaint.color = if (isSelected) Color.argb(255, 79, 70, 229) else Color.argb(200, 18, 20, 30)
            canvas.drawRoundRect(snRect, 10f, 10f, bgPaint)
            cardBorderPaint.color = if (isSelected) Color.argb(255, 165, 180, 252) else Color.argb(120, 49, 46, 129)
            cardBorderPaint.strokeWidth = if (isSelected) 2.0f else 1.2f
            canvas.drawRoundRect(snRect, 10f, 10f, cardBorderPaint)

            centerTextPaint.textSize = 13.5f
            centerTextPaint.color = if (isSelected) Color.WHITE else Color.argb(230, 199, 210, 254)
            centerTextPaint.isFakeBoldText = isSelected
            canvas.drawText(snapModeLabels[i], snRect.centerX(), snRect.centerY() + 4.5f, centerTextPaint)
        }
        centerTextPaint.isFakeBoldText = false

        // Multi-Touch Gestures Toggle
        textPaint.isFakeBoldText = true
        canvas.drawText("Multi-Touch Gesture Shortcuts", contentLeft, gesturesToggleRect.centerY() + 5f, textPaint)
        textPaint.isFakeBoldText = false
        drawToggleSwitch(canvas, gesturesToggleRect, gesturesEnabled)

        // Turbo Buttons Toggle
        textPaint.isFakeBoldText = true
        canvas.drawText("Turbo Auto-Fire (Hold A/B)", contentLeft, turboToggleRect.centerY() + 5f, textPaint)
        textPaint.isFakeBoldText = false
        drawToggleSwitch(canvas, turboToggleRect, turboButtonsEnabled)

        // A+B Macro Toggle
        textPaint.isFakeBoldText = true
        canvas.drawText("A+B Combo Macro Pill", contentLeft, comboMacroToggleRect.centerY() + 5f, textPaint)
        textPaint.isFakeBoldText = false
        drawToggleSwitch(canvas, comboMacroToggleRect, comboMacroEnabled)

        // Touch Theme Selector
        for (i in themeRects.indices) {
            val isSelected = (touchTheme == themeNames[i])
            val thRect = themeRects[i]
            bgPaint.color = if (isSelected) Color.argb(255, 79, 70, 229) else Color.argb(200, 18, 20, 30)
            canvas.drawRoundRect(thRect, 12f, 12f, bgPaint)
            cardBorderPaint.color = if (isSelected) Color.argb(255, 165, 180, 252) else Color.argb(120, 49, 46, 129)
            cardBorderPaint.strokeWidth = if (isSelected) 2.5f else 1.5f
            canvas.drawRoundRect(thRect, 12f, 12f, cardBorderPaint)

            centerTextPaint.textSize = 14f
            centerTextPaint.color = if (isSelected) Color.WHITE else Color.argb(240, 199, 210, 254)
            centerTextPaint.isFakeBoldText = isSelected
            canvas.drawText(themeLabels[i], thRect.centerX(), thRect.centerY() + 5f, centerTextPaint)
        }
        centerTextPaint.isFakeBoldText = false

        // Gamepad Remapping Button (Material 3 Tonal Card)
        bgPaint.color = Color.argb(240, 30, 27, 75)
        canvas.drawRoundRect(gamepadRemapBtnRect, 14f, 14f, bgPaint)
        cardBorderPaint.color = Color.argb(200, 99, 102, 241)
        cardBorderPaint.strokeWidth = 2.0f
        canvas.drawRoundRect(gamepadRemapBtnRect, 14f, 14f, cardBorderPaint)
        centerTextPaint.textSize = 15f
        centerTextPaint.color = Color.WHITE
        centerTextPaint.isFakeBoldText = true
        canvas.drawText("🎮 Gamepad Remap", gamepadRemapBtnRect.centerX(), gamepadRemapBtnRect.centerY() + 5.5f, centerTextPaint)

        // Edit Controls Placement Button (Material 3 Tonal Card)
        bgPaint.color = Color.argb(240, 49, 46, 129)
        canvas.drawRoundRect(editControlsBtnRect, 14f, 14f, bgPaint)
        cardBorderPaint.color = Color.argb(255, 129, 140, 248)
        cardBorderPaint.strokeWidth = 2.0f
        canvas.drawRoundRect(editControlsBtnRect, 14f, 14f, cardBorderPaint)
        centerTextPaint.color = Color.WHITE
        canvas.drawText("📐 Layout Editor", editControlsBtnRect.centerX(), editControlsBtnRect.centerY() + 5.5f, centerTextPaint)
        centerTextPaint.isFakeBoldText = false

        // ─── 7. Section: Motion Sensors & Gyro ───
        cursorY = gamepadRemapBtnRect.bottom + 18f
        textPaint.textSize = 15f
        textPaint.color = Color.argb(255, 129, 140, 248)
        textPaint.isFakeBoldText = true
        canvas.drawText("MOTION SENSORS & TILT STEERING", contentLeft, cursorY, textPaint)
        textPaint.isFakeBoldText = false

        for (i in sensorModeRects.indices) {
            val isSelected = (sensorMode == sensorModes[i])
            val smRect = sensorModeRects[i]
            bgPaint.color = if (isSelected) Color.argb(255, 79, 70, 229) else Color.argb(200, 18, 20, 30)
            canvas.drawRoundRect(smRect, 12f, 12f, bgPaint)
            cardBorderPaint.color = if (isSelected) Color.argb(255, 165, 180, 252) else Color.argb(120, 49, 46, 129)
            cardBorderPaint.strokeWidth = if (isSelected) 2.5f else 1.5f
            canvas.drawRoundRect(smRect, 12f, 12f, cardBorderPaint)

            centerTextPaint.textSize = 14f
            centerTextPaint.color = if (isSelected) Color.WHITE else Color.argb(240, 199, 210, 254)
            centerTextPaint.isFakeBoldText = isSelected
            canvas.drawText(sensorModeLabels[i], smRect.centerX(), smRect.centerY() + 5f, centerTextPaint)
        }
        centerTextPaint.isFakeBoldText = false

        // Calibrate Zero-Point Button
        bgPaint.color = Color.argb(220, 30, 27, 75)
        canvas.drawRoundRect(sensorCalibrateBtnRect, 12f, 12f, bgPaint)
        cardBorderPaint.color = Color.argb(200, 99, 102, 241)
        cardBorderPaint.strokeWidth = 1.5f
        canvas.drawRoundRect(sensorCalibrateBtnRect, 12f, 12f, cardBorderPaint)
        centerTextPaint.textSize = 15f
        centerTextPaint.color = Color.WHITE
        centerTextPaint.isFakeBoldText = true
        canvas.drawText("🎯 Calibrate Neutral Resting Position", sensorCalibrateBtnRect.centerX(), sensorCalibrateBtnRect.centerY() + 5.5f, centerTextPaint)
        centerTextPaint.isFakeBoldText = false

        // ─── 8. Section: Display & Shaders ───
        cursorY = sensorCalibrateBtnRect.bottom + 18f
        textPaint.textSize = 15f
        textPaint.color = Color.argb(255, 129, 140, 248)
        textPaint.isFakeBoldText = true
        canvas.drawText("DISPLAY & SHADERS", contentLeft, cursorY, textPaint)
        textPaint.isFakeBoldText = false

        // LCD Grid Filter
        textPaint.textSize = 16f
        textPaint.color = Color.WHITE
        textPaint.isFakeBoldText = true
        canvas.drawText("LCD Pixel Grid Filter", contentLeft, lcdGridToggleRect.centerY() + 5f, textPaint)
        textPaint.isFakeBoldText = false
        drawToggleSwitch(canvas, lcdGridToggleRect, lcdGridEnabled)

        // GBA Color Correction
        textPaint.isFakeBoldText = true
        canvas.drawText("GBA Color Correction", contentLeft, gbaColorToggleRect.centerY() + 5f, textPaint)
        textPaint.isFakeBoldText = false
        drawToggleSwitch(canvas, gbaColorToggleRect, gbaColorCorrectionEnabled)

        // Handheld Screen Bezel
        textPaint.isFakeBoldText = true
        canvas.drawText("Handheld Screen Bezel", contentLeft, bezelToggleRect.centerY() + 5f, textPaint)
        textPaint.isFakeBoldText = false
        drawToggleSwitch(canvas, bezelToggleRect, bezelEnabled)

        // Display Scaling Tabs
        val isAspect = (scaleMode == ScaleMode.ASPECT_FIT)
        bgPaint.color = if (isAspect) Color.argb(255, 79, 70, 229) else Color.argb(200, 18, 20, 30)
        canvas.drawRoundRect(scaleAspectRect, 12f, 12f, bgPaint)
        cardBorderPaint.color = if (isAspect) Color.argb(255, 165, 180, 252) else Color.argb(120, 49, 46, 129)
        cardBorderPaint.strokeWidth = if (isAspect) 2.5f else 1.5f
        canvas.drawRoundRect(scaleAspectRect, 12f, 12f, cardBorderPaint)
        centerTextPaint.textSize = 14f
        centerTextPaint.color = if (isAspect) Color.WHITE else Color.argb(240, 199, 210, 254)
        centerTextPaint.isFakeBoldText = isAspect
        canvas.drawText("Aspect Fit", scaleAspectRect.centerX(), scaleAspectRect.centerY() + 5f, centerTextPaint)

        bgPaint.color = if (!isAspect) Color.argb(255, 79, 70, 229) else Color.argb(200, 18, 20, 30)
        canvas.drawRoundRect(scaleIntegerRect, 12f, 12f, bgPaint)
        cardBorderPaint.color = if (!isAspect) Color.argb(255, 165, 180, 252) else Color.argb(120, 49, 46, 129)
        cardBorderPaint.strokeWidth = if (!isAspect) 2.5f else 1.5f
        canvas.drawRoundRect(scaleIntegerRect, 12f, 12f, cardBorderPaint)
        centerTextPaint.color = if (!isAspect) Color.WHITE else Color.argb(240, 199, 210, 254)
        centerTextPaint.isFakeBoldText = !isAspect
        canvas.drawText("Integer Fit (1:1)", scaleIntegerRect.centerX(), scaleIntegerRect.centerY() + 5f, centerTextPaint)
        centerTextPaint.isFakeBoldText = false

        // ─── 9. Footer: Done Button (Material 3 Prominent Floating Action Pill) ───
        bgPaint.color = Color.argb(255, 79, 70, 229) // Indigo-600 primary
        canvas.drawRoundRect(doneBtnRect, 16f, 16f, bgPaint)
        cardBorderPaint.color = Color.argb(255, 165, 180, 252)
        cardBorderPaint.strokeWidth = 2.0f
        canvas.drawRoundRect(doneBtnRect, 16f, 16f, cardBorderPaint)

        centerTextPaint.textSize = 18f
        centerTextPaint.color = Color.WHITE
        centerTextPaint.isFakeBoldText = true
        canvas.drawText("Done", doneBtnRect.centerX(), doneBtnRect.centerY() + 6.5f, centerTextPaint)
        centerTextPaint.isFakeBoldText = false
    }

    private fun drawToggleSwitch(canvas: Canvas, rect: RectF, isOn: Boolean) {
        // Material 3 Switch: Solid Indigo track when ON, Dark Indigo outline track when OFF
        bgPaint.color = if (isOn) Color.argb(255, 79, 70, 229) // Indigo-600
        else Color.argb(220, 18, 20, 30) // Dark Pitch Black/Indigo
        canvas.drawRoundRect(rect, rect.height() * 0.5f, rect.height() * 0.5f, bgPaint)

        cardBorderPaint.color = if (isOn) Color.argb(255, 165, 180, 252)
        else Color.argb(140, 79, 70, 229)
        cardBorderPaint.strokeWidth = 2.0f
        canvas.drawRoundRect(rect, rect.height() * 0.5f, rect.height() * 0.5f, cardBorderPaint)

        centerTextPaint.textSize = 14f
        centerTextPaint.color = if (isOn) Color.WHITE else Color.argb(230, 165, 180, 252)
        centerTextPaint.isFakeBoldText = true
        val toggleText = if (isOn) "ON" else "OFF"
        canvas.drawText(toggleText, rect.centerX(), rect.centerY() + 4.5f, centerTextPaint)
        centerTextPaint.isFakeBoldText = false
    }
}
