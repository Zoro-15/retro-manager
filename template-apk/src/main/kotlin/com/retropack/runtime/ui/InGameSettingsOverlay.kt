package com.retropack.runtime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.retropack.runtime.core.ScaleMode

/**
 * In-Game Settings Modal Sheet for RetroPack.
 *
 * Provides a dark glassmorphic dialog with controls for:
 *  - "Edit Controls Placement" (triggers PPSSPP/Lemuroid-style layout editor)
 *  - "Display Scaling Mode" (Aspect Fit vs Integer Fit)
 *  - "Touch Controls Opacity" (20% to 100%)
 *  - "Haptic Feedback" toggle
 *  - "Reset All Settings" to defaults
 */
class InGameSettingsOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        visibility = GONE
    }

    var scaleMode: ScaleMode = ScaleMode.ASPECT_FIT
        set(value) {
            field = value
            invalidate()
        }

    var touchOpacity: Float = 0.6f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
            invalidate()
        }

    var hapticsEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var gameTitle: String = "RetroPack"
        set(value) {
            field = value
            invalidate()
        }

    var hapticFeedbackEnabledState: Boolean = true

    // Callbacks
    var onEditControlsClicked: (() -> Unit)? = null
    var onScaleModeChanged: ((ScaleMode) -> Unit)? = null
    var onOpacityChanged: ((Float) -> Unit)? = null
    var onHapticsChanged: ((Boolean) -> Unit)? = null
    var onResetDefaultsClicked: (() -> Unit)? = null
    var onDismissed: (() -> Unit)? = null
    var onHapticFeedbackRequested: (() -> Unit)? = null

    // Layout hit testing rects
    val cardRect = RectF()
    val closeBtnRect = RectF()
    val editControlsBtnRect = RectF()
    val scaleAspectRect = RectF()
    val scaleIntegerRect = RectF()
    val opacityRects = Array(5) { RectF() }
    val hapticsToggleRect = RectF()
    val resetAllBtnRect = RectF()
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

    fun updateCardLayout(viewW: Float, viewH: Float) {
        val cardW = Math.min(viewW * 0.90f, 440f)
        val cardH = Math.min(viewH * 0.85f, 480f)
        val cardLeft = (viewW - cardW) * 0.5f
        val cardTop = (viewH - cardH) * 0.5f
        cardRect.set(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)

        closeBtnRect.set(cardRect.right - 44f, cardTop + 14f, cardRect.right - 14f, cardTop + 44f)

        var cursorY = cardTop + 34f + 20f + 26f
        val contentW = cardW - 48f
        val btnH = 44f
        editControlsBtnRect.set(cardLeft + 24f, cursorY, cardLeft + 24f + contentW, cursorY + btnH)

        cursorY += btnH + 28f + 10f
        val segW = (contentW - 8f) * 0.5f
        val segH = 36f
        scaleAspectRect.set(cardLeft + 24f, cursorY, cardLeft + 24f + segW, cursorY + segH)
        scaleIntegerRect.set(cardLeft + 24f + segW + 8f, cursorY, cardLeft + 24f + contentW, cursorY + segH)

        cursorY += segH + 26f + 10f
        val pillSpacing = 6f
        val pillW = (contentW - (pillSpacing * 4)) / 5f
        val pillH = 32f
        for (i in 0 until 5) {
            val pLeft = cardLeft + 24f + i * (pillW + pillSpacing)
            opacityRects[i].set(pLeft, cursorY, pLeft + pillW, cursorY + pillH)
        }

        cursorY += pillH + 26f
        val toggleW = 76f
        val toggleH = 32f
        hapticsToggleRect.set(cardRect.right - 24f - toggleW, cursorY + 2f, cardRect.right - 24f, cursorY + 2f + toggleH)

        cursorY += 56f
        val footBtnH = 38f
        val footBtnW = (contentW - 12f) * 0.5f
        resetAllBtnRect.set(cardLeft + 24f, cursorY, cardLeft + 24f + footBtnW, cursorY + footBtnH)
        doneBtnRect.set(cardLeft + 24f + footBtnW + 12f, cursorY, cardLeft + 24f + contentW, cursorY + footBtnH)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false

        val viewW = if (width > 0) width.toFloat() else 1080f
        val viewH = if (height > 0) height.toFloat() else 1920f
        updateCardLayout(viewW, viewH)

        val x = event.x
        val y = event.y

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            // 1. Outside tap -> dismiss
            if (!cardRect.contains(x, y)) {
                hide()
                return true
            }

            // 2. Close button
            if (closeBtnRect.contains(x, y)) {
                triggerHaptic()
                hide()
                return true
            }

            // 3. Edit Controls Button
            if (editControlsBtnRect.contains(x, y)) {
                triggerHaptic()
                hide()
                onEditControlsClicked?.invoke()
                return true
            }

            // 4. Scale Mode Tabs
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

            // 5. Opacity Stepped Pills
            val opacities = floatArrayOf(0.20f, 0.40f, 0.60f, 0.80f, 1.00f)
            for (i in opacityRects.indices) {
                if (opacityRects[i].contains(x, y)) {
                    triggerHaptic()
                    touchOpacity = opacities[i]
                    onOpacityChanged?.invoke(touchOpacity)
                    return true
                }
            }

            // 6. Haptics Toggle
            if (hapticsToggleRect.contains(x, y)) {
                triggerHaptic()
                hapticsEnabled = !hapticsEnabled
                onHapticsChanged?.invoke(hapticsEnabled)
                return true
            }

            // 7. Reset All
            if (resetAllBtnRect.contains(x, y)) {
                triggerHaptic()
                onResetDefaultsClicked?.invoke()
                return true
            }

            // 8. Done Button
            if (doneBtnRect.contains(x, y)) {
                triggerHaptic()
                hide()
                return true
            }
        }
        return true // Modal consumes all touches while active
    }

    private fun triggerHaptic() {
        if (hapticFeedbackEnabledState) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
        updateCardLayout(viewW, viewH)

        // 1. Scrim Backdrop
        bgPaint.color = Color.argb(195, 6, 9, 14)
        canvas.drawRect(0f, 0f, viewW, viewH, bgPaint)

        // Card Glass Background
        bgPaint.color = Color.argb(245, 18, 22, 32)
        canvas.drawRoundRect(cardRect, 20f, 20f, bgPaint)
        cardBorderPaint.color = Color.argb(180, 50, 70, 105)
        canvas.drawRoundRect(cardRect, 20f, 20f, cardBorderPaint)

        // 3. Header
        val cardLeft = cardRect.left
        val cardTop = cardRect.top
        var cursorY = cardTop + 34f
        textPaint.textSize = 20f
        textPaint.color = Color.WHITE
        canvas.drawText("⚙️ Game Settings", cardLeft + 24f, cursorY, textPaint)

        // Subtitle
        cursorY += 20f
        textPaint.textSize = 12f
        textPaint.color = Color.argb(200, 140, 160, 190)
        canvas.drawText(gameTitle, cardLeft + 24f, cursorY, textPaint)

        // Close Button (top-right)
        centerTextPaint.textSize = 18f
        centerTextPaint.color = Color.argb(220, 170, 185, 210)
        canvas.drawText("✕", closeBtnRect.centerX(), closeBtnRect.centerY() + 6f, centerTextPaint)

        // 4. Section: Controls Placement Primary Button
        bgPaint.color = Color.argb(235, 28, 44, 70)
        canvas.drawRoundRect(editControlsBtnRect, 12f, 12f, bgPaint)
        cardBorderPaint.color = Color.argb(220, 0, 190, 240)
        canvas.drawRoundRect(editControlsBtnRect, 12f, 12f, cardBorderPaint)

        centerTextPaint.textSize = 15f
        centerTextPaint.color = Color.argb(255, 0, 229, 255)
        canvas.drawText("📐 Edit Controls Placement", editControlsBtnRect.centerX(), editControlsBtnRect.centerY() + 5f, centerTextPaint)

        // 5. Section: Display Scaling
        cursorY = editControlsBtnRect.bottom + 28f
        textPaint.textSize = 11f
        textPaint.color = Color.argb(220, 130, 150, 180)
        canvas.drawText("DISPLAY SCALING MODE", cardLeft + 24f, cursorY, textPaint)

        val isAspect = scaleMode == ScaleMode.ASPECT_FIT
        // Aspect Tab
        bgPaint.color = if (isAspect) Color.argb(255, 0, 140, 220) else Color.argb(200, 28, 34, 48)
        canvas.drawRoundRect(scaleAspectRect, 10f, 10f, bgPaint)
        centerTextPaint.textSize = 13f
        centerTextPaint.color = if (isAspect) Color.WHITE else Color.argb(200, 160, 175, 195)
        canvas.drawText("Aspect Fit", scaleAspectRect.centerX(), scaleAspectRect.centerY() + 5f, centerTextPaint)

        // Integer Tab
        bgPaint.color = if (!isAspect) Color.argb(255, 0, 140, 220) else Color.argb(200, 28, 34, 48)
        canvas.drawRoundRect(scaleIntegerRect, 10f, 10f, bgPaint)
        centerTextPaint.color = if (!isAspect) Color.WHITE else Color.argb(200, 160, 175, 195)
        canvas.drawText("Integer Fit (1:1)", scaleIntegerRect.centerX(), scaleIntegerRect.centerY() + 5f, centerTextPaint)

        // 6. Section: Touch Opacity Stepped Selector
        cursorY = scaleAspectRect.bottom + 26f
        val opacityPercent = (touchOpacity * 100).toInt()
        textPaint.textSize = 11f
        textPaint.color = Color.argb(220, 130, 150, 180)
        canvas.drawText("TOUCH CONTROLS OPACITY: $opacityPercent%", cardLeft + 24f, cursorY, textPaint)

        val opacities = floatArrayOf(0.20f, 0.40f, 0.60f, 0.80f, 1.00f)
        val pillLabels = arrayOf("20%", "40%", "60%", "80%", "100%")

        for (i in 0 until 5) {
            val isSelected = Math.abs(touchOpacity - opacities[i]) < 0.05f
            bgPaint.color = if (isSelected) Color.argb(255, 0, 160, 240) else Color.argb(190, 28, 34, 48)
            canvas.drawRoundRect(opacityRects[i], 8f, 8f, bgPaint)

            centerTextPaint.textSize = 12f
            centerTextPaint.color = if (isSelected) Color.WHITE else Color.argb(200, 160, 175, 195)
            canvas.drawText(pillLabels[i], opacityRects[i].centerX(), opacityRects[i].centerY() + 4f, centerTextPaint)
        }

        // 7. Section: Haptic Feedback Switch
        cursorY = opacityRects[0].bottom + 26f
        textPaint.textSize = 13f
        textPaint.color = Color.WHITE
        canvas.drawText("Haptic Feedback", cardLeft + 24f, cursorY + 18f, textPaint)

        bgPaint.color = if (hapticsEnabled) Color.argb(255, 0, 160, 220) else Color.argb(200, 42, 48, 62)
        canvas.drawRoundRect(hapticsToggleRect, 16f, 16f, bgPaint)
        centerTextPaint.textSize = 12f
        centerTextPaint.color = Color.WHITE
        val toggleText = if (hapticsEnabled) "ON" else "OFF"
        canvas.drawText(toggleText, hapticsToggleRect.centerX(), hapticsToggleRect.centerY() + 4f, centerTextPaint)

        // 8. Footer: Reset Defaults and Done Button
        bgPaint.color = Color.argb(200, 36, 42, 58)
        canvas.drawRoundRect(resetAllBtnRect, 10f, 10f, bgPaint)
        centerTextPaint.textSize = 13f
        centerTextPaint.color = Color.argb(220, 200, 210, 225)
        canvas.drawText("↺ Reset Defaults", resetAllBtnRect.centerX(), resetAllBtnRect.centerY() + 5f, centerTextPaint)

        // Done Button
        bgPaint.color = Color.argb(255, 0, 140, 220)
        canvas.drawRoundRect(doneBtnRect, 10f, 10f, bgPaint)
        centerTextPaint.color = Color.WHITE
        canvas.drawText("Done", doneBtnRect.centerX(), doneBtnRect.centerY() + 5f, centerTextPaint)
    }
}
