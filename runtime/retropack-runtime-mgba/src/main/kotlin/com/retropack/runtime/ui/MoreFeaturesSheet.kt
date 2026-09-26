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
import com.retropack.runtime.input.HapticEngine
import com.retropack.runtime.save.SaveStateManager

/**
 * In-Game "More" App Sheet Hub for RetroPack.
 *
 * Provides a dark glassmorphic modal settings and feature hub organized into:
 *  1. 💾 Save States Hub: Visual 5-slot manager (+ Auto-Save Slot 0) with thumbnails & relative timestamps.
 *  2. ⚡ Emulation Speed & Audio: Fast-forward multiplier (1x, 2x, 4x, 8x, Max) & audio muting toggle.
 *  3. 🎮 Controls & Superpowers: Floating D-Pad, Multi-Touch Gestures, Turbo buttons, A+B macro pill, themes, and controller remapping.
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

    var floatingDpadEnabled: Boolean = false
        set(value) {
            field = value
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

    var sensorSensitivity: Float = 1.0f
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

    fun updateLayoutGeometry(viewW: Float, viewH: Float) {
        val cardW = Math.min(viewW * 0.94f, 600f)
        val cardH = Math.min(viewH * 0.96f, 920f)
        val cardLeft = (viewW - cardW) * 0.5f
        val cardTop = (viewH - cardH) * 0.5f
        cardRect.set(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)

        closeBtnRect.set(cardRect.right - 44f, cardTop + 14f, cardRect.right - 14f, cardTop + 44f)

        val contentLeft = cardLeft + 20f
        val contentW = cardW - 40f
        var cursorY = cardTop + 50f

        // ─── 1. Save States Hub ───
        cursorY += 16f
        val slotSpacing = 6f
        val slotW = (contentW - (slotSpacing * 5)) / 6f
        val slotH = 42f
        for (i in 0 until 6) {
            val sLeft = contentLeft + i * (slotW + slotSpacing)
            slotCardRects[i].set(sLeft, cursorY, sLeft + slotW, cursorY + slotH)
        }

        cursorY += slotH + 8f
        val stateBtnW = (contentW - 12f) * 0.5f
        val stateBtnH = 32f
        saveStateBtnRect.set(contentLeft, cursorY, contentLeft + stateBtnW, cursorY + stateBtnH)
        loadStateBtnRect.set(contentLeft + stateBtnW + 12f, cursorY, contentLeft + contentW, cursorY + stateBtnH)

        // ─── 2. Emulation Speed & Audio ───
        cursorY += stateBtnH + 20f
        val speedSpacing = 6f
        val speedW = (contentW - (speedSpacing * 4)) / 5f
        val speedH = 26f
        for (i in 0 until 5) {
            val spLeft = contentLeft + i * (speedW + speedSpacing)
            speedRects[i].set(spLeft, cursorY, spLeft + speedW, cursorY + speedH)
        }

        cursorY += speedH + 8f
        val toggleW = 64f
        val toggleH = 24f
        muteFfToggleRect.set(cardRect.right - 20f - toggleW, cursorY, cardRect.right - 20f, cursorY + toggleH)

        // ─── 3. Controls & Superpowers ───
        cursorY += toggleH + 18f
        floatingDpadToggleRect.set(cardRect.right - 20f - toggleW, cursorY, cardRect.right - 20f, cursorY + toggleH)

        cursorY += toggleH + 6f
        gesturesToggleRect.set(cardRect.right - 20f - toggleW, cursorY, cardRect.right - 20f, cursorY + toggleH)

        cursorY += toggleH + 6f
        turboToggleRect.set(cardRect.right - 20f - toggleW, cursorY, cardRect.right - 20f, cursorY + toggleH)

        cursorY += toggleH + 6f
        comboMacroToggleRect.set(cardRect.right - 20f - toggleW, cursorY, cardRect.right - 20f, cursorY + toggleH)

        cursorY += toggleH + 8f
        val themeSpacing = 6f
        val themeW = (contentW - (themeSpacing * 3)) / 4f
        val themeH = 24f
        for (i in 0 until 4) {
            val thLeft = contentLeft + i * (themeW + themeSpacing)
            themeRects[i].set(thLeft, cursorY, thLeft + themeW, cursorY + themeH)
        }

        cursorY += themeH + 8f
        val dualBtnW = (contentW - 8f) * 0.5f
        val dualBtnH = 30f
        gamepadRemapBtnRect.set(contentLeft, cursorY, contentLeft + dualBtnW, cursorY + dualBtnH)
        editControlsBtnRect.set(contentLeft + dualBtnW + 8f, cursorY, contentLeft + contentW, cursorY + dualBtnH)

        // ─── 4. Motion Sensors & Gyro ───
        cursorY += dualBtnH + 18f
        val sensorSpacing = 6f
        val sensorW = (contentW - (sensorSpacing * 2)) / 3f
        val sensorH = 26f
        for (i in 0 until 3) {
            val smLeft = contentLeft + i * (sensorW + sensorSpacing)
            sensorModeRects[i].set(smLeft, cursorY, smLeft + sensorW, cursorY + sensorH)
        }

        cursorY += sensorH + 6f
        sensorCalibrateBtnRect.set(contentLeft, cursorY, contentLeft + contentW, cursorY + 28f)

        // ─── 5. Display & Shaders ───
        cursorY += 28f + 18f
        lcdGridToggleRect.set(cardRect.right - 20f - toggleW, cursorY, cardRect.right - 20f, cursorY + toggleH)

        cursorY += toggleH + 6f
        gbaColorToggleRect.set(cardRect.right - 20f - toggleW, cursorY, cardRect.right - 20f, cursorY + toggleH)

        cursorY += toggleH + 6f
        bezelToggleRect.set(cardRect.right - 20f - toggleW, cursorY, cardRect.right - 20f, cursorY + toggleH)

        cursorY += toggleH + 6f
        val segW = (contentW - 8f) * 0.5f
        val segH = 26f
        scaleAspectRect.set(contentLeft, cursorY, contentLeft + segW, cursorY + segH)
        scaleIntegerRect.set(contentLeft + segW + 8f, cursorY, contentLeft + contentW, cursorY + segH)

        // ─── Footer Done Button ───
        val doneH = 34f
        doneBtnRect.set(contentLeft, cardRect.bottom - doneH - 12f, contentLeft + contentW, cardRect.bottom - 12f)
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

            // Floating Dynamic D-Pad Toggle
            if (floatingDpadToggleRect.contains(x, y)) {
                triggerHaptic()
                floatingDpadEnabled = !floatingDpadEnabled
                onFloatingDpadChanged?.invoke(floatingDpadEnabled)
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

            // Sensor Calibration Button
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

            // GBA Color Toggle
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

            // Scaling Modes
            if (scaleAspectRect.contains(x, y)) {
                triggerHaptic()
                scaleMode = ScaleMode.ASPECT_FIT
                onScaleModeChanged?.invoke(scaleMode)
                return true
            }
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
            HapticEngine.triggerPress(context, hapticIntensity, this)
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

        // 1. Scrim Backdrop
        bgPaint.color = Color.argb(195, 6, 9, 14)
        canvas.drawRect(0f, 0f, viewW, viewH, bgPaint)

        // 2. Card Glass Background
        bgPaint.color = Color.argb(248, 16, 20, 30)
        canvas.drawRoundRect(cardRect, 20f, 20f, bgPaint)
        cardBorderPaint.color = Color.argb(180, 50, 70, 105)
        canvas.drawRoundRect(cardRect, 20f, 20f, cardBorderPaint)

        val cardLeft = cardRect.left
        val contentLeft = cardLeft + 20f

        // 3. Header
        var cursorY = cardRect.top + 28f
        textPaint.textSize = 17f
        textPaint.color = Color.WHITE
        canvas.drawText("🎮 RetroPack Hub", contentLeft, cursorY, textPaint)

        cursorY += 14f
        textPaint.textSize = 11f
        textPaint.color = Color.argb(200, 140, 160, 190)
        canvas.drawText(gameTitle, contentLeft, cursorY, textPaint)

        // Close button (X)
        centerTextPaint.textSize = 17f
        centerTextPaint.color = Color.argb(220, 170, 185, 210)
        canvas.drawText("✕", closeBtnRect.centerX(), closeBtnRect.centerY() + 6f, centerTextPaint)

        // ─── 4. Section: Save States Hub ───
        cursorY = cardRect.top + 60f
        textPaint.textSize = 10.5f
        textPaint.color = Color.argb(220, 0, 229, 255)
        canvas.drawText("SAVE STATES HUB", contentLeft, cursorY, textPaint)

        val slotInfos = saveStateManager?.getAllSlots()
        for (i in 0 until 6) {
            val isSelected = (i == selectedSlot)
            val slotRect = slotCardRects[i]
            val slotInfo = slotInfos?.getOrNull(i)
            val hasData = slotInfo?.exists == true

            bgPaint.color = when {
                isSelected -> Color.argb(255, 24, 48, 76)
                hasData -> Color.argb(220, 28, 36, 52)
                else -> Color.argb(180, 22, 26, 36)
            }
            canvas.drawRoundRect(slotRect, 8f, 8f, bgPaint)

            cardBorderPaint.color = if (isSelected) Color.argb(255, 0, 229, 255)
                                    else if (hasData) Color.argb(160, 80, 120, 170)
                                    else Color.argb(100, 45, 55, 75)
            canvas.drawRoundRect(slotRect, 8f, 8f, cardBorderPaint)

            centerTextPaint.textSize = 10f
            centerTextPaint.color = if (isSelected) Color.argb(255, 0, 229, 255) else Color.WHITE
            val slotLabel = if (i == 0) "Auto" else "Slot $i"
            canvas.drawText(slotLabel, slotRect.centerX(), slotRect.top + 16f, centerTextPaint)

            centerTextPaint.textSize = 8.5f
            centerTextPaint.color = if (hasData) Color.argb(220, 160, 210, 255) else Color.argb(140, 120, 130, 150)
            val subLabel = if (hasData) "Saved" else "Empty"
            canvas.drawText(subLabel, slotRect.centerX(), slotRect.bottom - 8f, centerTextPaint)
        }

        // Save State Button
        bgPaint.color = Color.argb(240, 0, 130, 200)
        canvas.drawRoundRect(saveStateBtnRect, 8f, 8f, bgPaint)
        centerTextPaint.textSize = 11.5f
        centerTextPaint.color = Color.WHITE
        canvas.drawText("💾 Save State", saveStateBtnRect.centerX(), saveStateBtnRect.centerY() + 4f, centerTextPaint)

        // Load State Button
        val activeInfo = slotInfos?.getOrNull(selectedSlot)
        val canLoad = activeInfo?.exists == true
        bgPaint.color = if (canLoad) Color.argb(240, 40, 110, 80) else Color.argb(160, 30, 38, 48)
        canvas.drawRoundRect(loadStateBtnRect, 8f, 8f, bgPaint)
        centerTextPaint.color = if (canLoad) Color.argb(255, 120, 255, 180) else Color.argb(120, 140, 150, 165)
        canvas.drawText("📂 Load State", loadStateBtnRect.centerX(), loadStateBtnRect.centerY() + 4f, centerTextPaint)

        // ─── 5. Section: Emulation Speed & Audio ───
        cursorY = saveStateBtnRect.bottom + 18f
        textPaint.textSize = 10.5f
        textPaint.color = Color.argb(220, 255, 190, 0)
        canvas.drawText("EMULATION SPEED & AUDIO", contentLeft, cursorY, textPaint)

        for (i in speedRects.indices) {
            val isSelected = (fastForwardSpeed == speedMultipliers[i])
            val sRect = speedRects[i]
            bgPaint.color = if (isSelected) Color.argb(255, 180, 110, 0) else Color.argb(190, 28, 34, 48)
            canvas.drawRoundRect(sRect, 6f, 6f, bgPaint)
            centerTextPaint.textSize = 10.5f
            centerTextPaint.color = if (isSelected) Color.WHITE else Color.argb(200, 170, 185, 205)
            canvas.drawText(speedLabels[i], sRect.centerX(), sRect.centerY() + 4f, centerTextPaint)
        }

        // Mute Audio Toggle
        textPaint.textSize = 11f
        textPaint.color = Color.WHITE
        canvas.drawText("Mute Audio on Fast-Forward", contentLeft, muteFfToggleRect.centerY() + 4f, textPaint)
        drawToggleSwitch(canvas, muteFfToggleRect, muteAudioOnFastForward)

        // ─── 6. Section: Controls & Superpowers ───
        cursorY = muteFfToggleRect.bottom + 16f
        textPaint.textSize = 10.5f
        textPaint.color = Color.argb(220, 180, 120, 255)
        canvas.drawText("CONTROLS & SUPERPOWERS", contentLeft, cursorY, textPaint)

        // Floating Dynamic D-Pad Toggle
        textPaint.textSize = 11f
        textPaint.color = Color.WHITE
        canvas.drawText("Floating Dynamic D-Pad", contentLeft, floatingDpadToggleRect.centerY() + 4f, textPaint)
        drawToggleSwitch(canvas, floatingDpadToggleRect, floatingDpadEnabled)

        // Multi-Touch Gestures Toggle
        canvas.drawText("Multi-Touch Gesture Shortcuts", contentLeft, gesturesToggleRect.centerY() + 4f, textPaint)
        drawToggleSwitch(canvas, gesturesToggleRect, gesturesEnabled)

        // Turbo Buttons Toggle
        canvas.drawText("Turbo Auto-Fire (Hold A/B)", contentLeft, turboToggleRect.centerY() + 4f, textPaint)
        drawToggleSwitch(canvas, turboToggleRect, turboButtonsEnabled)

        // A+B Macro Toggle
        canvas.drawText("A+B Combo Macro Pill", contentLeft, comboMacroToggleRect.centerY() + 4f, textPaint)
        drawToggleSwitch(canvas, comboMacroToggleRect, comboMacroEnabled)

        // Touch Theme Selector
        for (i in themeRects.indices) {
            val isSelected = (touchTheme == themeNames[i])
            val thRect = themeRects[i]
            bgPaint.color = if (isSelected) Color.argb(255, 120, 45, 190) else Color.argb(190, 28, 34, 48)
            canvas.drawRoundRect(thRect, 6f, 6f, bgPaint)
            centerTextPaint.textSize = 10.5f
            centerTextPaint.color = if (isSelected) Color.WHITE else Color.argb(200, 170, 185, 205)
            canvas.drawText(themeLabels[i], thRect.centerX(), thRect.centerY() + 4f, centerTextPaint)
        }

        // Gamepad Remapping Button
        bgPaint.color = Color.argb(220, 40, 36, 68)
        canvas.drawRoundRect(gamepadRemapBtnRect, 8f, 8f, bgPaint)
        cardBorderPaint.color = Color.argb(180, 160, 120, 240)
        canvas.drawRoundRect(gamepadRemapBtnRect, 8f, 8f, cardBorderPaint)
        centerTextPaint.textSize = 11f
        centerTextPaint.color = Color.argb(255, 190, 160, 255)
        canvas.drawText("🎮 Gamepad Remap", gamepadRemapBtnRect.centerX(), gamepadRemapBtnRect.centerY() + 4f, centerTextPaint)

        // Edit Controls Placement Button
        bgPaint.color = Color.argb(220, 28, 44, 70)
        canvas.drawRoundRect(editControlsBtnRect, 8f, 8f, bgPaint)
        cardBorderPaint.color = Color.argb(180, 0, 190, 240)
        canvas.drawRoundRect(editControlsBtnRect, 8f, 8f, cardBorderPaint)
        centerTextPaint.color = Color.argb(255, 0, 229, 255)
        canvas.drawText("📐 Layout Editor", editControlsBtnRect.centerX(), editControlsBtnRect.centerY() + 4f, centerTextPaint)

        // ─── 7. Section: Motion Sensors & Gyro ───
        cursorY = gamepadRemapBtnRect.bottom + 16f
        textPaint.textSize = 10.5f
        textPaint.color = Color.argb(220, 255, 140, 80)
        canvas.drawText("MOTION SENSORS & TILT STEERING", contentLeft, cursorY, textPaint)

        for (i in sensorModeRects.indices) {
            val isSelected = (sensorMode == sensorModes[i])
            val smRect = sensorModeRects[i]
            bgPaint.color = if (isSelected) Color.argb(255, 200, 80, 20) else Color.argb(190, 28, 34, 48)
            canvas.drawRoundRect(smRect, 6f, 6f, bgPaint)
            centerTextPaint.textSize = 10.5f
            centerTextPaint.color = if (isSelected) Color.WHITE else Color.argb(200, 170, 185, 205)
            canvas.drawText(sensorModeLabels[i], smRect.centerX(), smRect.centerY() + 4f, centerTextPaint)
        }

        // Calibrate Zero-Point Button
        bgPaint.color = Color.argb(200, 36, 42, 56)
        canvas.drawRoundRect(sensorCalibrateBtnRect, 6f, 6f, bgPaint)
        centerTextPaint.textSize = 11f
        centerTextPaint.color = Color.argb(255, 255, 180, 120)
        canvas.drawText("🎯 Calibrate Neutral Resting Position", sensorCalibrateBtnRect.centerX(), sensorCalibrateBtnRect.centerY() + 4f, centerTextPaint)

        // ─── 8. Section: Display & Shaders ───
        cursorY = sensorCalibrateBtnRect.bottom + 16f
        textPaint.textSize = 10.5f
        textPaint.color = Color.argb(220, 100, 210, 255)
        canvas.drawText("DISPLAY & SHADERS", contentLeft, cursorY, textPaint)

        // LCD Grid Filter
        textPaint.textSize = 11f
        textPaint.color = Color.WHITE
        canvas.drawText("LCD Pixel Grid Filter", contentLeft, lcdGridToggleRect.centerY() + 4f, textPaint)
        drawToggleSwitch(canvas, lcdGridToggleRect, lcdGridEnabled)

        // GBA Color Correction
        canvas.drawText("GBA Color Correction", contentLeft, gbaColorToggleRect.centerY() + 4f, textPaint)
        drawToggleSwitch(canvas, gbaColorToggleRect, gbaColorCorrectionEnabled)

        // Handheld Screen Bezel
        canvas.drawText("Handheld Screen Bezel", contentLeft, bezelToggleRect.centerY() + 4f, textPaint)
        drawToggleSwitch(canvas, bezelToggleRect, bezelEnabled)

        // Display Scaling Tabs
        val isAspect = (scaleMode == ScaleMode.ASPECT_FIT)
        bgPaint.color = if (isAspect) Color.argb(255, 0, 140, 220) else Color.argb(190, 28, 34, 48)
        canvas.drawRoundRect(scaleAspectRect, 6f, 6f, bgPaint)
        centerTextPaint.textSize = 10.5f
        centerTextPaint.color = if (isAspect) Color.WHITE else Color.argb(200, 160, 175, 195)
        canvas.drawText("Aspect Fit", scaleAspectRect.centerX(), scaleAspectRect.centerY() + 4f, centerTextPaint)

        bgPaint.color = if (!isAspect) Color.argb(255, 0, 140, 220) else Color.argb(190, 28, 34, 48)
        canvas.drawRoundRect(scaleIntegerRect, 6f, 6f, bgPaint)
        centerTextPaint.color = if (!isAspect) Color.WHITE else Color.argb(200, 160, 175, 195)
        canvas.drawText("Integer Fit (1:1)", scaleIntegerRect.centerX(), scaleIntegerRect.centerY() + 4f, centerTextPaint)

        // ─── 9. Footer: Done Button ───
        bgPaint.color = Color.argb(255, 0, 140, 220)
        canvas.drawRoundRect(doneBtnRect, 10f, 10f, bgPaint)
        centerTextPaint.textSize = 13f
        centerTextPaint.color = Color.WHITE
        canvas.drawText("Done", doneBtnRect.centerX(), doneBtnRect.centerY() + 5f, centerTextPaint)
    }

    private fun drawToggleSwitch(canvas: Canvas, rect: RectF, isOn: Boolean) {
        bgPaint.color = if (isOn) Color.argb(255, 0, 160, 220) else Color.argb(200, 42, 48, 62)
        canvas.drawRoundRect(rect, rect.height() * 0.5f, rect.height() * 0.5f, bgPaint)
        centerTextPaint.textSize = 10f
        centerTextPaint.color = Color.WHITE
        val toggleText = if (isOn) "ON" else "OFF"
        canvas.drawText(toggleText, rect.centerX(), rect.centerY() + 3.5f, centerTextPaint)
    }
}
