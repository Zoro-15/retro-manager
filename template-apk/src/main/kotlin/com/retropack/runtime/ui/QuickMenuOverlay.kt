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
 * Floating In-Game Quick Menu and Radial Speed-Dial Overlay for RetroPack.
 *
 * Inspired by Lemuroid's ergonomic floating action badge and radial menu design.
 * Renders an unobtrusive translucent gamepad FAB docked near the screen edge.
 * Tapping expands two satellite action buttons:
 *  1. Joystick Button: Quick-toggles virtual touch controls visibility.
 *  2. Settings Button: Opens the in-game modal settings sheet.
 */
class QuickMenuOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var isExpanded: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isControlsActive: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var idleAlpha: Float = 0.40f
    var expandedAlpha: Float = 0.95f
    var hapticFeedbackEnabledState: Boolean = true

    var onToggleControls: (() -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null
    var onHapticFeedbackRequested: (() -> Unit)? = null

    // FAB Coordinates and Dimensions
    val fabRadius: Float = 26f
    val satelliteRadius: Float = 22f
    val satelliteSpacing: Float = 60f

    // Hit Testing Rectangles & Coordinates
    val fabHitRect = RectF()
    val satControlsHitRect = RectF()
    val satSettingsHitRect = RectF()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = Color.WHITE
    }
    private val tempRect = RectF()

    fun getFabCenterX(): Float {
        val viewW = if (width > 0) width.toFloat() else 1080f
        return viewW - 44f
    }

    fun getFabCenterY(): Float = 48f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val fabCx = getFabCenterX()
        val fabCy = getFabCenterY()

        updateHitRects(fabCx, fabCy)

        if (!isExpanded) {
            // Collapsed: only consume touches within the FAB
            if (isInsideCircle(x, y, fabCx, fabCy, fabRadius + 14f)) {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    triggerHaptic()
                    isExpanded = true
                }
                return true
            }
            return false // Pass-through to game viewport / virtual controls
        }

        // Expanded State
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                if (isInsideCircle(x, y, fabCx, fabCy, fabRadius + 10f)) {
                    // Tap main FAB -> collapse
                    triggerHaptic()
                    isExpanded = false
                    return true
                }

                val sat1Cy = fabCy + satelliteSpacing
                if (isInsideCircle(x, y, fabCx, sat1Cy, satelliteRadius + 14f) ||
                    satControlsHitRect.contains(x, y)) {
                    // Tap Controls Toggle
                    triggerHaptic()
                    isControlsActive = !isControlsActive
                    onToggleControls?.invoke()
                    invalidate()
                    return true
                }

                val sat2Cy = fabCy + satelliteSpacing * 2f
                if (isInsideCircle(x, y, fabCx, sat2Cy, satelliteRadius + 14f) ||
                    satSettingsHitRect.contains(x, y)) {
                    // Tap Settings
                    triggerHaptic()
                    isExpanded = false
                    onOpenSettings?.invoke()
                    return true
                }

                // Tap outside -> collapse menu
                isExpanded = false
                return true
            }
        }
        return true
    }

    private fun updateHitRects(fabCx: Float, fabCy: Float) {
        fabHitRect.set(fabCx - fabRadius, fabCy - fabRadius, fabCx + fabRadius, fabCy + fabRadius)

        val sat1Cy = fabCy + satelliteSpacing
        satControlsHitRect.set(fabCx - 160f, sat1Cy - 20f, fabCx + satelliteRadius + 10f, sat1Cy + 20f)

        val sat2Cy = fabCy + satelliteSpacing * 2f
        satSettingsHitRect.set(fabCx - 140f, sat2Cy - 20f, fabCx + satelliteRadius + 10f, sat2Cy + 20f)
    }

    private fun triggerHaptic() {
        if (hapticFeedbackEnabledState) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onHapticFeedbackRequested?.invoke()
        }
    }

    private fun isInsideCircle(x: Float, y: Float, cx: Float, cy: Float, radius: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        return (dx * dx + dy * dy) <= (radius * radius)
    }

    fun renderForTesting(canvas: Canvas) {
        onDraw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val fabCx = getFabCenterX()
        val fabCy = getFabCenterY()
        updateHitRects(fabCx, fabCy)

        val currentAlpha = if (isExpanded) expandedAlpha else idleAlpha
        val alphaInt = (currentAlpha * 255).toInt().coerceIn(0, 255)

        // 1. Draw Expanded Satellite Buttons
        if (isExpanded) {
            drawSatelliteButtons(canvas, fabCx, fabCy)
        }

        // 2. Draw Main Floating Gamepad FAB
        bgPaint.color = Color.argb(alphaInt, 18, 22, 32)
        canvas.drawCircle(fabCx, fabCy, fabRadius, bgPaint)

        ringPaint.color = if (isExpanded) Color.argb(255, 0, 229, 255)
                          else Color.argb((alphaInt * 0.9f).toInt(), 80, 140, 220)
        canvas.drawCircle(fabCx, fabCy, fabRadius, ringPaint)

        textPaint.textSize = if (isExpanded) 22f else 18f
        textPaint.alpha = alphaInt
        val iconText = if (isExpanded) "✕" else "🎮"
        canvas.drawText(iconText, fabCx, fabCy + textPaint.textSize * 0.35f, textPaint)
    }

    private fun drawSatelliteButtons(canvas: Canvas, fabCx: Float, fabCy: Float) {
        // Satellite 1: Joystick / Controls Toggle
        val sat1Cy = fabCy + satelliteSpacing
        val sat1BgColor = if (isControlsActive) Color.argb(245, 14, 38, 54) else Color.argb(230, 28, 32, 42)
        val sat1RingColor = if (isControlsActive) Color.argb(255, 0, 229, 255) else Color.argb(160, 90, 100, 120)

        // Pill Label 1 (to the left)
        val label1Text = if (isControlsActive) "Controls: ON" else "Controls: OFF"
        pillTextPaint.textSize = 15f
        pillTextPaint.color = if (isControlsActive) Color.argb(255, 0, 229, 255) else Color.argb(220, 160, 170, 185)
        val label1W = pillTextPaint.measureText(label1Text) + 20f

        tempRect.set(fabCx - satelliteRadius - label1W - 8f, sat1Cy - 16f, fabCx - satelliteRadius - 8f, sat1Cy + 16f)

        pillPaint.color = Color.argb(230, 16, 20, 30)
        canvas.drawRoundRect(tempRect, 10f, 10f, pillPaint)
        canvas.drawText(label1Text, tempRect.right - 10f, sat1Cy + 5f, pillTextPaint)

        // Satellite Circle 1
        bgPaint.color = sat1BgColor
        canvas.drawCircle(fabCx, sat1Cy, satelliteRadius, bgPaint)
        ringPaint.color = sat1RingColor
        canvas.drawCircle(fabCx, sat1Cy, satelliteRadius, ringPaint)

        textPaint.textSize = 17f
        textPaint.alpha = 255
        canvas.drawText("🕹️", fabCx, sat1Cy + 6f, textPaint)

        // Satellite 2: Settings Button
        val sat2Cy = fabCy + satelliteSpacing * 2f
        val sat2BgColor = Color.argb(235, 20, 26, 38)
        val sat2RingColor = Color.argb(200, 100, 160, 240)

        // Pill Label 2
        val label2Text = "Settings"
        pillTextPaint.color = Color.argb(240, 220, 230, 250)
        val label2W = pillTextPaint.measureText(label2Text) + 20f

        tempRect.set(fabCx - satelliteRadius - label2W - 8f, sat2Cy - 16f, fabCx - satelliteRadius - 8f, sat2Cy + 16f)

        pillPaint.color = Color.argb(230, 16, 20, 30)
        canvas.drawRoundRect(tempRect, 10f, 10f, pillPaint)
        canvas.drawText(label2Text, tempRect.right - 10f, sat2Cy + 5f, pillTextPaint)

        // Satellite Circle 2
        bgPaint.color = sat2BgColor
        canvas.drawCircle(fabCx, sat2Cy, satelliteRadius, bgPaint)
        ringPaint.color = sat2RingColor
        canvas.drawCircle(fabCx, sat2Cy, satelliteRadius, ringPaint)

        textPaint.textSize = 17f
        textPaint.alpha = 255
        canvas.drawText("⚙️", fabCx, sat2Cy + 6f, textPaint)
    }
}
