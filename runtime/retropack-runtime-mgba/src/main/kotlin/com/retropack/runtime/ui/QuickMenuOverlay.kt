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
    var onFastForwardSpeedChanged: ((Int) -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null
    var onOpenMore: (() -> Unit)? = null
    var onHapticFeedbackRequested: (() -> Unit)? = null

    // FAB Coordinates and Dimensions (1.5x scaled for mobile ergonomics)
    val fabRadius: Float = 39f
    val satelliteRadius: Float = 33f
    val satelliteSpacing: Float = 84f

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
        strokeWidth = 3.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = true
    }

    /**
     * FAB Center docked to the right edge of the screen at 40% height.
     * Sticks strictly to the right side in both horizontal and vertical modes.
     */
    fun getFabCenterX(): Float {
        val viewW = if (width > 0) width.toFloat() else 1080f
        return viewW - fabRadius - 16f
    }

    fun getFabCenterY(): Float {
        val viewH = if (height > 0) height.toFloat() else 1920f
        return viewH * 0.40f
    }

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
            if (isInsideCircle(x, y, fabCx, fabCy, fabRadius + 18f)) {
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
                if (isInsideCircle(x, y, fabCx, fabCy, fabRadius + 16f)) {
                    // Tap main FAB -> collapse
                    triggerHaptic()
                    isExpanded = false
                    return true
                }

                // Sat 1: Controls Toggle
                val sat1Cy = fabCy + satelliteSpacing * 1f
                if (isInsideCircle(x, y, fabCx, sat1Cy, satelliteRadius + 16f)) {
                    triggerHaptic()
                    isControlsActive = !isControlsActive
                    onToggleControls?.invoke()
                    invalidate()
                    return true
                }

                // Sat 2: Fast-Forward Speed Cycle
                val sat2Cy = fabCy + satelliteSpacing * 2f
                if (isInsideCircle(x, y, fabCx, sat2Cy, satelliteRadius + 16f)) {
                    triggerHaptic()
                    cycleFastForwardSpeed()
                    return true
                }

                // Sat 3: Settings Dialog
                val sat3Cy = fabCy + satelliteSpacing * 3f
                if (isInsideCircle(x, y, fabCx, sat3Cy, satelliteRadius + 16f)) {
                    triggerHaptic()
                    isExpanded = false
                    onOpenSettings?.invoke()
                    return true
                }

                // Sat 4: More Features Full Sheet Hub
                val sat4Cy = fabCy + satelliteSpacing * 4f
                if (isInsideCircle(x, y, fabCx, sat4Cy, satelliteRadius + 16f)) {
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
        satControlsHitRect.set(fabCx - satelliteRadius, sat1Cy - satelliteRadius, fabCx + satelliteRadius, sat1Cy + satelliteRadius)

        val sat2Cy = fabCy + satelliteSpacing * 2f
        satFastForwardHitRect.set(fabCx - satelliteRadius, sat2Cy - satelliteRadius, fabCx + satelliteRadius, sat2Cy + satelliteRadius)

        val sat3Cy = fabCy + satelliteSpacing * 3f
        satSettingsHitRect.set(fabCx - satelliteRadius, sat3Cy - satelliteRadius, fabCx + satelliteRadius, sat3Cy + satelliteRadius)

        val sat4Cy = fabCy + satelliteSpacing * 4f
        satMoreHitRect.set(fabCx - satelliteRadius, sat4Cy - satelliteRadius, fabCx + satelliteRadius, sat4Cy + satelliteRadius)
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

        // 1. Draw Expanded Satellite Buttons (No text brackets, 1.5x scaled, Black & Indigo)
        if (isExpanded) {
            drawSatelliteButtons(canvas, fabCx, fabCy)
        }

        // 2. Draw Main Floating Gamepad FAB
        bgPaint.color = Color.argb(alphaInt, 11, 13, 20)
        canvas.drawCircle(fabCx, fabCy, fabRadius, bgPaint)

        ringPaint.color = Color.argb(255, 99, 102, 241) // Crisp Indigo ring
        canvas.drawCircle(fabCx, fabCy, fabRadius, ringPaint)

        textPaint.textSize = if (isExpanded) 32f else 28f
        textPaint.alpha = alphaInt
        val iconText = if (isExpanded) "✕" else "🎮"
        canvas.drawText(iconText, fabCx, fabCy + textPaint.textSize * 0.35f, textPaint)
    }

    private fun drawSatelliteButtons(canvas: Canvas, fabCx: Float, fabCy: Float) {
        // ─── SATELLITE 1: Joystick / Controls Toggle ───
        val sat1Cy = fabCy + satelliteSpacing * 1f
        val sat1BgColor = if (isControlsActive) Color.argb(245, 79, 70, 229) else Color.argb(230, 18, 20, 30)
        val sat1RingColor = if (isControlsActive) Color.argb(255, 165, 180, 252) else Color.argb(160, 79, 70, 229)

        bgPaint.color = sat1BgColor
        canvas.drawCircle(fabCx, sat1Cy, satelliteRadius, bgPaint)
        ringPaint.color = sat1RingColor
        canvas.drawCircle(fabCx, sat1Cy, satelliteRadius, ringPaint)

        textPaint.textSize = 24f
        textPaint.alpha = 255
        canvas.drawText("🕹️", fabCx, sat1Cy + 8f, textPaint)

        // ─── SATELLITE 2: Fast-Forward Speed Floater ───
        val sat2Cy = fabCy + satelliteSpacing * 2f
        val isFastForwarding = fastForwardSpeed > 1
        val sat2BgColor = if (isFastForwarding) Color.argb(245, 99, 102, 241) else Color.argb(230, 18, 20, 30)
        val sat2RingColor = if (isFastForwarding) Color.argb(255, 224, 231, 255) else Color.argb(160, 79, 70, 229)

        bgPaint.color = sat2BgColor
        canvas.drawCircle(fabCx, sat2Cy, satelliteRadius, bgPaint)
        ringPaint.color = sat2RingColor
        canvas.drawCircle(fabCx, sat2Cy, satelliteRadius, ringPaint)

        textPaint.textSize = 24f
        textPaint.alpha = 255
        canvas.drawText("⚡", fabCx, sat2Cy + 8f, textPaint)

        // Draw speed badge indicator
        if (isFastForwarding) {
            badgePaint.color = Color.argb(255, 224, 231, 255)
            val badgeX = fabCx + satelliteRadius * 0.65f
            val badgeY = sat2Cy - satelliteRadius * 0.65f
            canvas.drawCircle(badgeX, badgeY, 12f, badgePaint)
            badgeTextPaint.textSize = 10f
            badgeTextPaint.color = Color.argb(255, 15, 17, 26)
            val badgeText = if (fastForwardSpeed >= 16) "M" else "${fastForwardSpeed}x"
            canvas.drawText(badgeText, badgeX, badgeY + 3.5f, badgeTextPaint)
        }

        // ─── SATELLITE 3: Quick Settings Floater ───
        val sat3Cy = fabCy + satelliteSpacing * 3f
        val sat3BgColor = Color.argb(240, 49, 46, 129)
        val sat3RingColor = Color.argb(255, 129, 140, 248)

        bgPaint.color = sat3BgColor
        canvas.drawCircle(fabCx, sat3Cy, satelliteRadius, bgPaint)
        ringPaint.color = sat3RingColor
        canvas.drawCircle(fabCx, sat3Cy, satelliteRadius, ringPaint)

        textPaint.textSize = 24f
        textPaint.alpha = 255
        canvas.drawText("⚙️", fabCx, sat3Cy + 8f, textPaint)

        // ─── SATELLITE 4: More Features Hub Floater ───
        val sat4Cy = fabCy + satelliteSpacing * 4f
        val sat4BgColor = Color.argb(240, 67, 56, 202)
        val sat4RingColor = Color.argb(255, 165, 180, 252)

        bgPaint.color = sat4BgColor
        canvas.drawCircle(fabCx, sat4Cy, satelliteRadius, bgPaint)
        ringPaint.color = sat4RingColor
        canvas.drawCircle(fabCx, sat4Cy, satelliteRadius, ringPaint)

        textPaint.textSize = 24f
        textPaint.alpha = 255
        canvas.drawText("⋯", fabCx, sat4Cy + 8f, textPaint)
    }
}

