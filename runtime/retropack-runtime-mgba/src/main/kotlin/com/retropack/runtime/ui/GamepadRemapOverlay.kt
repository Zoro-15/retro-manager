package com.retropack.runtime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.retropack.runtime.core.RetroKey
import com.retropack.runtime.input.GamepadMapper
import com.retropack.runtime.input.HapticEngine

/**
 * Physical Controller Interactive Remapping UI Overlay for RetroPack.
 *
 * Implements specifications from:
 * - PPSSPP (ControlMappingScreen.cpp) & Lemuroid (GamepadManager.kt).
 * - Detects connected external gamepad profiles (Xbox Wireless, PlayStation DualSense, Nintendo Switch Pro, 8BitDo).
 * - Interactive Wizard: Sequential button prompts ("Press button for D-Pad UP", "Press button for A", etc.).
 * - Single-button remapping and per-controller UUID/descriptor persistence.
 */
class GamepadRemapOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        visibility = GONE
    }

    var gamepadMapper: GamepadMapper? = null

    var hapticFeedbackEnabledState: Boolean = true
    var hapticIntensity: Float = 1.0f

    // Wizard / Listening State
    var activeListeningKey: RetroKey? = null
        private set
    var isWizardMode: Boolean = false
        private set
    var wizardStepIndex: Int = 0
        private set

    private val remapKeySequence = listOf(
        RetroKey.UP,
        RetroKey.DOWN,
        RetroKey.LEFT,
        RetroKey.RIGHT,
        RetroKey.A,
        RetroKey.B,
        RetroKey.L,
        RetroKey.R,
        RetroKey.START,
        RetroKey.SELECT
    )

    // Callbacks
    var onDismissed: (() -> Unit)? = null
    var onMappingSaved: (() -> Unit)? = null
    var onHapticFeedbackRequested: (() -> Unit)? = null

    // Hit Testing Rectangles
    val cardRect = RectF()
    val closeBtnRect = RectF()
    val wizardBtnRect = RectF()
    val resetDefaultsBtnRect = RectF()
    val saveBtnRect = RectF()

    val rowRects = Array(10) { RectF() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    fun show() {
        visibility = VISIBLE
        isWizardMode = false
        activeListeningKey = null
        invalidate()
    }

    fun hide() {
        if (visibility == VISIBLE) {
            visibility = GONE
            isWizardMode = false
            activeListeningKey = null
            onDismissed?.invoke()
            invalidate()
        }
    }

    fun isShowing(): Boolean = visibility == VISIBLE

    /**
     * Starts the interactive step-by-step remapping wizard.
     */
    fun startWizard() {
        isWizardMode = true
        wizardStepIndex = 0
        activeListeningKey = remapKeySequence[0]
        triggerHaptic()
        invalidate()
    }

    /**
     * Starts listening for a single button binding.
     */
    fun startListeningFor(key: RetroKey) {
        isWizardMode = false
        activeListeningKey = key
        triggerHaptic()
        invalidate()
    }

    /**
     * Cancels active listening mode.
     */
    fun cancelListening() {
        isWizardMode = false
        activeListeningKey = null
        invalidate()
    }

    /**
     * Handles physical key events during remapping overlay visibility.
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (visibility != VISIBLE) return false

        val targetKey = activeListeningKey
        if (targetKey != null && event.action == KeyEvent.ACTION_DOWN) {
            // Bind the incoming keyCode
            val mapper = gamepadMapper
            if (mapper != null) {
                mapper.setKeyBinding(event.keyCode, targetKey)
                triggerHaptic()

                if (isWizardMode) {
                    wizardStepIndex++
                    if (wizardStepIndex < remapKeySequence.size) {
                        activeListeningKey = remapKeySequence[wizardStepIndex]
                    } else {
                        // Wizard Complete!
                        isWizardMode = false
                        activeListeningKey = null
                        mapper.saveProfile(context)
                        onMappingSaved?.invoke()
                    }
                } else {
                    activeListeningKey = null
                    mapper.saveProfile(context)
                    onMappingSaved?.invoke()
                }
                invalidate()
                return true
            }
        }
        return false
    }

    /**
     * Handles analog motion events during remapping.
     */
    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        // Could bind axes here if in wizard mode, but standard buttons use handleKeyEvent
        return false
    }

    fun updateLayoutGeometry(viewW: Float, viewH: Float) {
        val cardW = Math.min(viewW * 0.94f, 580f)
        val cardH = Math.min(viewH * 0.94f, 840f)
        val cardLeft = (viewW - cardW) * 0.5f
        val cardTop = (viewH - cardH) * 0.5f
        cardRect.set(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)

        closeBtnRect.set(cardRect.right - 44f, cardTop + 14f, cardRect.right - 14f, cardTop + 44f)

        val contentLeft = cardLeft + 20f
        val contentW = cardW - 40f
        var cursorY = cardTop + 62f

        // Wizard button
        val wizH = 38f
        wizardBtnRect.set(contentLeft, cursorY, contentLeft + contentW, cursorY + wizH)

        cursorY += wizH + 16f
        // 10 Button rows in 2 columns
        val colGap = 10f
        val colW = (contentW - colGap) * 0.5f
        val rowH = 42f
        val rowGap = 8f

        for (i in 0 until 10) {
            val col = i / 5
            val row = i % 5
            val rLeft = contentLeft + col * (colW + colGap)
            val rTop = cursorY + row * (rowH + rowGap)
            rowRects[i].set(rLeft, rTop, rLeft + colW, rTop + rowH)
        }

        // Bottom footer buttons
        val footBtnH = 38f
        val footBtnW = (contentW - 12f) * 0.5f
        val footY = cardRect.bottom - footBtnH - 16f
        resetDefaultsBtnRect.set(contentLeft, footY, contentLeft + footBtnW, footY + footBtnH)
        saveBtnRect.set(contentLeft + footBtnW + 12f, footY, contentLeft + contentW, footY + footBtnH)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false

        val viewW = if (width > 0) width.toFloat() else 1080f
        val viewH = if (height > 0) height.toFloat() else 1920f
        updateLayoutGeometry(viewW, viewH)

        val x = event.x
        val y = event.y

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            // Outside card tap
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

            // Wizard button
            if (wizardBtnRect.contains(x, y)) {
                if (isWizardMode || activeListeningKey != null) {
                    cancelListening()
                } else {
                    startWizard()
                }
                return true
            }

            // Button rows
            for (i in 0 until 10) {
                if (rowRects[i].contains(x, y)) {
                    val key = remapKeySequence[i]
                    if (activeListeningKey == key) {
                        cancelListening()
                    } else {
                        startListeningFor(key)
                    }
                    return true
                }
            }

            // Reset Defaults button
            if (resetDefaultsBtnRect.contains(x, y)) {
                triggerHaptic()
                gamepadMapper?.resetBindingsToDefault(context)
                cancelListening()
                return true
            }

            // Save & Close button
            if (saveBtnRect.contains(x, y)) {
                triggerHaptic()
                gamepadMapper?.saveProfile(context)
                onMappingSaved?.invoke()
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
        bgPaint.color = Color.argb(250, 16, 20, 30)
        canvas.drawRoundRect(cardRect, 20f, 20f, bgPaint)
        borderPaint.color = Color.argb(180, 50, 70, 105)
        canvas.drawRoundRect(cardRect, 20f, 20f, borderPaint)

        val contentLeft = cardRect.left + 20f

        // 3. Header: Controller Name & Status
        val devName = gamepadMapper?.activeDeviceName ?: "Physical Gamepad"
        var cursorY = cardRect.top + 32f
        textPaint.textSize = 18f
        textPaint.color = Color.WHITE
        canvas.drawText("🎮 Gamepad Remapping", contentLeft, cursorY, textPaint)

        cursorY += 16f
        textPaint.textSize = 12f
        textPaint.color = Color.argb(220, 0, 229, 255)
        canvas.drawText("Device: $devName", contentLeft, cursorY, textPaint)

        // Close button (X)
        centerTextPaint.textSize = 18f
        centerTextPaint.color = Color.argb(220, 170, 185, 210)
        canvas.drawText("✕", closeBtnRect.centerX(), closeBtnRect.centerY() + 6f, centerTextPaint)

        // 4. Interactive Wizard / Prompt Banner
        val listeningKey = activeListeningKey
        if (listeningKey != null) {
            bgPaint.color = Color.argb(255, 220, 80, 0)
            canvas.drawRoundRect(wizardBtnRect, 10f, 10f, bgPaint)
            centerTextPaint.textSize = 14f
            centerTextPaint.color = Color.WHITE
            val prompt = if (isWizardMode) {
                "Step ${wizardStepIndex + 1}/10: Press button for ${listeningKey.name}"
            } else {
                "Press any controller button for ${listeningKey.name} (Tap to cancel)"
            }
            canvas.drawText(prompt, wizardBtnRect.centerX(), wizardBtnRect.centerY() + 5f, centerTextPaint)
        } else {
            bgPaint.color = Color.argb(240, 0, 130, 200)
            canvas.drawRoundRect(wizardBtnRect, 10f, 10f, bgPaint)
            centerTextPaint.textSize = 14f
            centerTextPaint.color = Color.WHITE
            canvas.drawText("⚡ Interactive Remap All Buttons", wizardBtnRect.centerX(), wizardBtnRect.centerY() + 5f, centerTextPaint)
        }

        // 5. Button Mapping Rows
        val mapper = gamepadMapper
        for (i in 0 until 10) {
            val key = remapKeySequence[i]
            val rect = rowRects[i]
            val isListening = (listeningKey == key)

            bgPaint.color = if (isListening) Color.argb(255, 60, 40, 90) else Color.argb(200, 26, 32, 46)
            canvas.drawRoundRect(rect, 8f, 8f, bgPaint)

            borderPaint.color = if (isListening) Color.argb(255, 0, 229, 255) else Color.argb(120, 50, 65, 90)
            canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

            // GBA Key Label (Left)
            textPaint.textSize = 13f
            textPaint.color = if (isListening) Color.argb(255, 0, 229, 255) else Color.WHITE
            canvas.drawText(key.name, rect.left + 12f, rect.centerY() + 5f, textPaint)

            // Mapped Code Label (Right)
            val assignedCode = mapper?.getKeyCodeFor(key)
            val codeLabel = if (isListening) "PRESS..." else if (assignedCode != null) GamepadMapper.getKeyLabel(assignedCode) else "UNMAPPED"
            textPaint.textSize = 11f
            textPaint.color = if (isListening) Color.argb(255, 255, 200, 0) else Color.argb(190, 160, 190, 220)
            val labelW = textPaint.measureText(codeLabel)
            canvas.drawText(codeLabel, rect.right - 12f - labelW, rect.centerY() + 4f, textPaint)
        }

        // 6. Footer Buttons
        // Reset Defaults
        bgPaint.color = Color.argb(200, 42, 52, 70)
        canvas.drawRoundRect(resetDefaultsBtnRect, 10f, 10f, bgPaint)
        centerTextPaint.textSize = 13f
        centerTextPaint.color = Color.argb(240, 220, 230, 245)
        canvas.drawText("↺ Reset Defaults", resetDefaultsBtnRect.centerX(), resetDefaultsBtnRect.centerY() + 5f, centerTextPaint)

        // Save & Exit
        bgPaint.color = Color.argb(255, 0, 140, 220)
        canvas.drawRoundRect(saveBtnRect, 10f, 10f, bgPaint)
        centerTextPaint.color = Color.WHITE
        canvas.drawText("✓ Save & Exit", saveBtnRect.centerX(), saveBtnRect.centerY() + 5f, centerTextPaint)
    }
}
