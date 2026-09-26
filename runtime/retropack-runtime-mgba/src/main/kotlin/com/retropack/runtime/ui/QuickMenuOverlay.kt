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
 * Inspired by Lemuroid and PPSSPP's ergonomic floating action badge and radial menu design.
 * Renders an unobtrusive translucent gamepad FAB docked near the screen edge.
 * Tapping expands four satellite action buttons:
 *  1. 🕹️ Joystick Button: Quick-toggles virtual touch controls visibility.
 *  2. ⚡ Fast-Forward Button: Cycles emulation speed (1x -> 2x -> 4x -> 8x -> Max -> 1x).
 *  3. ⚙️ Settings Button: Opens the in-game quick settings sheet.
 *  4. ⋯ More Button: Opens the full-page in-game app sheet (MoreFeaturesSheet).
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

    var fastForwardSpeed: Int = 1
        set(value) {
            field = value
            invalidate()
        }

    var idleAlpha: Float = 0.40f
    var expandedAlpha: Float = 0.95f
    var hapticFeedbackEnabledState: Boolean = true

    var onToggleControls: (() -> Unit)? = null
    var onCycleFastForward: (() -> Unit)? = null
    var onFastForwardSpeedChanged: ((Int) -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null
    var onOpenMore: (() -> Unit)? = null
    var onHapticFeedbackRequested: (() -> Unit)? = null

    // FAB Coordinates and Dimensions
    val fabRadius: Float = 26f
    val satelliteRadius: Float = 22f
    val satelliteSpacing: Float = 56f

    // Hit Testing Rectangles & Coordinates
    val fabHitRect = RectF()
    val satControlsHitRect = RectF()
    val satFastForwardHitRect = RectF()
    val satSettingsHitRect = RectF()
    val satMoreHitRect = RectF()

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

    fun cycleFastForwardSpeed(): Int {
        val nextSpeed = when (fastForwardSpeed) {
            1 -> 2
            2 -> 4
            4 -> 8
            8 -> 16
            else -> 1
        }
        fastForwardSpeed = nextSpeed
        onFastForwardSpeedChanged?.invoke(nextSpeed)
        onCycleFastForward?.invoke()
        invalidate()
        return nextSpeed
    }

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

                // Sat 1: Controls Toggle
                val sat1Cy = fabCy + satelliteSpacing * 1f
                if (isInsideCircle(x, y, fabCx, sat1Cy, satelliteRadius + 14f) ||
                    satControlsHitRect.contains(x, y)) {
                    triggerHaptic()
                    isControlsActive = !isControlsActive
                    onToggleControls?.invoke()
                    invalidate()
                    return true
                }

                // Sat 2: Fast-Forward Speed Cycle
                val sat2Cy = fabCy + satelliteSpacing * 2f
                if (isInsideCircle(x, y, fabCx, sat2Cy, satelliteRadius + 14f) ||
                    satFastForwardHitRect.contains(x, y)) {
                    triggerHaptic()
                    cycleFastForwardSpeed()
                    return true
                }

                // Sat 3: Settings Dialog
                val sat3Cy = fabCy + satelliteSpacing * 3f
                if (isInsideCircle(x, y, fabCx, sat3Cy, satelliteRadius + 14f) ||
                    satSettingsHitRect.contains(x, y)) {
                    triggerHaptic()
                    isExpanded = false
                    onOpenSettings?.invoke()
                    return true
                }

                // Sat 4: More Features Full Sheet Hub
                val sat4Cy = fabCy + satelliteSpacing * 4f
                if (isInsideCircle(x, y, fabCx, sat4Cy, satelliteRadius + 14f) ||
                    satMoreHitRect.contains(x, y)) {
                    triggerHaptic()
                    isExpanded = false
                    onOpenMore?.invoke()
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

        val sat1Cy = fabCy + satelliteSpacing * 1f
        satControlsHitRect.set(fabCx - 160f, sat1Cy - 18f, fabCx + satelliteRadius + 10f, sat1Cy + 18f)

        val sat2Cy = fabCy + satelliteSpacing * 2f
        satFastForwardHitRect.set(fabCx - 160f, sat2Cy - 18f, fabCx + satelliteRadius + 10f, sat2Cy + 18f)

        val sat3Cy = fabCy + satelliteSpacing * 3f
        satSettingsHitRect.set(fabCx - 150f, sat3Cy - 18f, fabCx + satelliteRadius + 10f, sat3Cy + 18f)

        val sat4Cy = fabCy + satelliteSpacing * 4f
        satMoreHitRect.set(fabCx - 140f, sat4Cy - 18f, fabCx + satelliteRadius + 10f, sat4Cy + 18f)
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
        pillTextPaint.textSize = 14f

        // ─── SATELLITE 1: Joystick / Controls Toggle ───
        val sat1Cy = fabCy + satelliteSpacing * 1f
        val sat1BgColor = if (isControlsActive) Color.argb(245, 14, 38, 54) else Color.argb(230, 28, 32, 42)
        val sat1RingColor = if (isControlsActive) Color.argb(255, 0, 229, 255) else Color.argb(160, 90, 100, 120)

        val label1Text = if (isControlsActive) "Controls: ON" else "Controls: OFF"
        pillTextPaint.color = if (isControlsActive) Color.argb(255, 0, 229, 255) else Color.argb(220, 160, 170, 185)
        val label1W = pillTextPaint.measureText(label1Text) + 20f

        tempRect.set(fabCx - satelliteRadius - label1W - 8f, sat1Cy - 15f, fabCx - satelliteRadius - 8f, sat1Cy + 15f)
        pillPaint.color = Color.argb(230, 16, 20, 30)
        canvas.drawRoundRect(tempRect, 10f, 10f, pillPaint)
        canvas.drawText(label1Text, tempRect.right - 10f, sat1Cy + 5f, pillTextPaint)

        bgPaint.color = sat1BgColor
        canvas.drawCircle(fabCx, sat1Cy, satelliteRadius, bgPaint)
        ringPaint.color = sat1RingColor
        canvas.drawCircle(fabCx, sat1Cy, satelliteRadius, ringPaint)

        textPaint.textSize = 16f
        textPaint.alpha = 255
        canvas.drawText("🕹️", fabCx, sat1Cy + 5f, textPaint)

        // ─── SATELLITE 2: Fast-Forward Speed Floater ───
        val sat2Cy = fabCy + satelliteSpacing * 2f
        val isFastForwarding = fastForwardSpeed > 1
        val sat2BgColor = if (isFastForwarding) Color.argb(245, 54, 42, 14) else Color.argb(230, 28, 32, 42)
        val sat2RingColor = if (isFastForwarding) Color.argb(255, 255, 180, 0) else Color.argb(160, 120, 130, 150)

        val speedLabel = when {
            fastForwardSpeed <= 1 -> "Speed: 1x"
            fastForwardSpeed >= 16 -> "Speed: Max"
            else -> "Speed: ${fastForwardSpeed}x"
        }
        pillTextPaint.color = if (isFastForwarding) Color.argb(255, 255, 200, 50) else Color.argb(220, 180, 190, 210)
        val label2W = pillTextPaint.measureText(speedLabel) + 20f

        tempRect.set(fabCx - satelliteRadius - label2W - 8f, sat2Cy - 15f, fabCx - satelliteRadius - 8f, sat2Cy + 15f)
        pillPaint.color = Color.argb(230, 16, 20, 30)
        canvas.drawRoundRect(tempRect, 10f, 10f, pillPaint)
        canvas.drawText(speedLabel, tempRect.right - 10f, sat2Cy + 5f, pillTextPaint)

        bgPaint.color = sat2BgColor
        canvas.drawCircle(fabCx, sat2Cy, satelliteRadius, bgPaint)
        ringPaint.color = sat2RingColor
        canvas.drawCircle(fabCx, sat2Cy, satelliteRadius, ringPaint)

        textPaint.textSize = 16f
        textPaint.alpha = 255
        canvas.drawText("⚡", fabCx, sat2Cy + 5f, textPaint)

        // ─── SATELLITE 3: Quick Settings Floater ───
        val sat3Cy = fabCy + satelliteSpacing * 3f
        val sat3BgColor = Color.argb(235, 20, 26, 38)
        val sat3RingColor = Color.argb(200, 100, 160, 240)

        val label3Text = "Settings"
        pillTextPaint.color = Color.argb(240, 220, 230, 250)
        val label3W = pillTextPaint.measureText(label3Text) + 20f

        tempRect.set(fabCx - satelliteRadius - label3W - 8f, sat3Cy - 15f, fabCx - satelliteRadius - 8f, sat3Cy + 15f)
        pillPaint.color = Color.argb(230, 16, 20, 30)
        canvas.drawRoundRect(tempRect, 10f, 10f, pillPaint)
        canvas.drawText(label3Text, tempRect.right - 10f, sat3Cy + 5f, pillTextPaint)

        bgPaint.color = sat3BgColor
        canvas.drawCircle(fabCx, sat3Cy, satelliteRadius, bgPaint)
        ringPaint.color = sat3RingColor
        canvas.drawCircle(fabCx, sat3Cy, satelliteRadius, ringPaint)

        textPaint.textSize = 16f
        textPaint.alpha = 255
        canvas.drawText("⚙️", fabCx, sat3Cy + 5f, textPaint)

        // ─── SATELLITE 4: More Features Hub Floater ───
        val sat4Cy = fabCy + satelliteSpacing * 4f
        val sat4BgColor = Color.argb(240, 28, 20, 48)
        val sat4RingColor = Color.argb(220, 180, 120, 255)

        val label4Text = "More Hub"
        pillTextPaint.color = Color.argb(255, 200, 160, 255)
        val label4W = pillTextPaint.measureText(label4Text) + 20f

        tempRect.set(fabCx - satelliteRadius - label4W - 8f, sat4Cy - 15f, fabCx - satelliteRadius - 8f, sat4Cy + 15f)
        pillPaint.color = Color.argb(230, 16, 20, 30)
        canvas.drawRoundRect(tempRect, 10f, 10f, pillPaint)
        canvas.drawText(label4Text, tempRect.right - 10f, sat4Cy + 5f, pillTextPaint)

        bgPaint.color = sat4BgColor
        canvas.drawCircle(fabCx, sat4Cy, satelliteRadius, bgPaint)
        ringPaint.color = sat4RingColor
        canvas.drawCircle(fabCx, sat4Cy, satelliteRadius, ringPaint)

        textPaint.textSize = 16f
        textPaint.alpha = 255
        canvas.drawText("⋯", fabCx, sat4Cy + 5f, textPaint)
    }
}

