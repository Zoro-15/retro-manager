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

/**
 * In-Game Virtual Disc Switcher Modal Overlay for Multi-Disc PS1 and PC Engine CD Games.
 *
 * Implements Feature 1 specifications:
 * - Displays available discs with active checkmark.
 * - Selecting a disc triggers atomic ejection -> ISO unmount -> new image mount -> virtual tray close.
 */
class DiscSwitcherOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        visibility = GONE
    }

    var gameTitle: String = "Multi-Disc CD Game"
        set(value) {
            field = value
            invalidate()
        }

    var discLabels: List<String> = listOf("Disc 1", "Disc 2")
        set(value) {
            field = if (value.isNotEmpty()) value else listOf("Disc 1")
            discSlotRects = Array(field.size) { RectF() }
            invalidate()
        }

    var currentDiscIndex: Int = 0
        set(value) {
            field = value.coerceIn(0, (discLabels.size - 1).coerceAtLeast(0))
            invalidate()
        }

    var hapticFeedbackEnabledState: Boolean = true

    // Callbacks
    var onDiscSelected: ((discIndex: Int, discLabel: String) -> Unit)? = null
    var onDismissed: (() -> Unit)? = null
    var onHapticFeedbackRequested: (() -> Unit)? = null

    // Layout hit testing rects
    val cardRect = RectF()
    val closeBtnRect = RectF()
    var discSlotRects = Array(2) { RectF() }
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
        val count = discLabels.size.coerceAtLeast(1)
        val cardW = Math.min(viewW * 0.88f, 460f)
        val cardH = Math.min(viewH * 0.85f, 160f + count * 58f)
        val cardLeft = (viewW - cardW) * 0.5f
        val cardTop = (viewH - cardH) * 0.5f
        cardRect.set(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)

        closeBtnRect.set(cardRect.right - 44f, cardTop + 14f, cardRect.right - 14f, cardTop + 44f)

        var cursorY = cardTop + 72f
        val contentW = cardW - 48f
        val slotH = 48f

        if (discSlotRects.size != count) {
            discSlotRects = Array(count) { RectF() }
        }

        for (i in 0 until count) {
            discSlotRects[i].set(cardLeft + 24f, cursorY, cardLeft + 24f + contentW, cursorY + slotH)
            cursorY += slotH + 10f
        }

        cursorY += 8f
        val doneH = 40f
        doneBtnRect.set(cardLeft + 24f, cursorY, cardLeft + 24f + contentW, cursorY + doneH)
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

            // 3. Disc Slots
            for (i in discSlotRects.indices) {
                if (discSlotRects[i].contains(x, y)) {
                    triggerHaptic()
                    currentDiscIndex = i
                    val label = discLabels.getOrElse(i) { "Disc ${i + 1}" }
                    onDiscSelected?.invoke(i, label)
                    hide()
                    return true
                }
            }

            // 4. Done button
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

        // 2. Card Container
        bgPaint.color = Color.argb(248, 15, 18, 28)
        canvas.drawRoundRect(cardRect, 22f, 22f, bgPaint)
        cardBorderPaint.color = Color.argb(180, 99, 102, 241)
        cardBorderPaint.strokeWidth = 2.0f
        canvas.drawRoundRect(cardRect, 22f, 22f, cardBorderPaint)

        // 3. Header
        val cardLeft = cardRect.left
        val cardTop = cardRect.top
        var cursorY = cardTop + 34f
        textPaint.textSize = 20f
        textPaint.color = Color.WHITE
        textPaint.isFakeBoldText = true
        canvas.drawText("💿 Virtual Disc Switcher", cardLeft + 24f, cursorY, textPaint)
        textPaint.isFakeBoldText = false

        // Subtitle
        cursorY += 20f
        textPaint.textSize = 12f
        textPaint.color = Color.argb(200, 165, 180, 252)
        canvas.drawText(gameTitle, cardLeft + 24f, cursorY, textPaint)

        // Close Button
        centerTextPaint.textSize = 18f
        centerTextPaint.color = Color.argb(220, 170, 185, 210)
        canvas.drawText("✕", closeBtnRect.centerX(), closeBtnRect.centerY() + 6f, centerTextPaint)

        // 4. Disc Slot Cards
        for (i in discSlotRects.indices) {
            val sRect = discSlotRects[i]
            val isSelected = (i == currentDiscIndex)
            val label = discLabels.getOrElse(i) { "Disc ${i + 1}" }

            bgPaint.color = if (isSelected) Color.argb(255, 79, 70, 229) else Color.argb(200, 24, 28, 42)
            canvas.drawRoundRect(sRect, 12f, 12f, bgPaint)

            cardBorderPaint.color = if (isSelected) Color.argb(255, 199, 210, 254) else Color.argb(120, 79, 70, 229)
            cardBorderPaint.strokeWidth = if (isSelected) 2.5f else 1.5f
            canvas.drawRoundRect(sRect, 12f, 12f, cardBorderPaint)

            // Disc Icon
            textPaint.textSize = 20f
            textPaint.color = if (isSelected) Color.WHITE else Color.argb(220, 199, 210, 254)
            canvas.drawText("💿", sRect.left + 16f, sRect.centerY() + 7f, textPaint)

            // Disc Label
            textPaint.textSize = 16f
            textPaint.isFakeBoldText = isSelected
            canvas.drawText(label, sRect.left + 52f, sRect.centerY() + 5.5f, textPaint)
            textPaint.isFakeBoldText = false

            // Active Checkmark indicator
            if (isSelected) {
                centerTextPaint.textSize = 15f
                centerTextPaint.color = Color.argb(255, 52, 211, 153) // Green accent check
                centerTextPaint.isFakeBoldText = true
                canvas.drawText("✓ INSERTED", sRect.right - 58f, sRect.centerY() + 5f, centerTextPaint)
                centerTextPaint.isFakeBoldText = false
            } else {
                centerTextPaint.textSize = 13f
                centerTextPaint.color = Color.argb(180, 148, 163, 184)
                canvas.drawText("Mount", sRect.right - 40f, sRect.centerY() + 4.5f, centerTextPaint)
            }
        }

        // 5. Done / Cancel Button
        bgPaint.color = Color.argb(220, 36, 42, 58)
        canvas.drawRoundRect(doneBtnRect, 10f, 10f, bgPaint)
        centerTextPaint.textSize = 14f
        centerTextPaint.color = Color.argb(220, 200, 210, 225)
        canvas.drawText("Close", doneBtnRect.centerX(), doneBtnRect.centerY() + 5f, centerTextPaint)
    }
}
