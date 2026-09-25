package com.retropack.runtime.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.retropack.runtime.core.RetroKey

/**
 * Virtual multi-touch controller overlay view for RetroPack.
 *
 * Implements specifications from:
 * - masterplan.md Section 5.4: "Virtual TouchOverlayView (custom coords, opacity, haptics)
 *   + physical Bluetooth/USB gamepad HID handler. Auto-hides virtual controls when physical
 *   gamepad buttons are pressed."
 * - roadmap.md Part 2.5.
 */
class TouchOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var opacity: Float = TouchLayout.DEFAULT_OPACITY
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
            layout = layout.withOpacity(field)
            invalidate()
        }

    var hapticFeedbackEnabledState: Boolean = true

    var isControlsVisible: Boolean = true
        set(value) {
            field = value
            if (!value) {
                clearPointers()
            }
            invalidate()
        }

    var revealOnTouchWhenHidden: Boolean = true

    var layout: TouchLayout = TouchLayout.create(1080f, 1920f, opacity)
        private set

    /** Callback invoked whenever the composite RetroKey bitmask changes. */
    var onKeyMaskChanged: ((Int) -> Unit)? = null

    /** Test hook callback invoked when haptic feedback is triggered. */
    var onHapticFeedbackRequested: (() -> Unit)? = null

    private val activePointers = mutableMapOf<Int, Pair<Float, Float>>()
    private var currentKeyMask: Int = RetroKey.NO_KEYS_MASK

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val tempRect = RectF()

    /**
     * Updates the layout to match the provided dimensions or re-generates
     * default responsive coordinates for portrait/landscape orientation.
     */
    fun updateLayout(width: Float, height: Float) {
        if (width > 0f && height > 0f) {
            layout = TouchLayout.create(width, height, opacity)
            invalidate()
        }
    }

    /**
     * Replaces the active layout with a customized [TouchLayout] instance.
     */
    fun setCustomLayout(customLayout: TouchLayout) {
        this.layout = customLayout
        this.opacity = customLayout.opacity
        invalidate()
    }

    /**
     * Returns the currently active composite RetroKey bitmask.
     */
    fun getKeyMask(): Int = currentKeyMask

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isControlsVisible) {
            if (revealOnTouchWhenHidden && event.actionMasked == MotionEvent.ACTION_DOWN) {
                isControlsVisible = true
                return true
            }
            return false
        }

        val action = event.actionMasked
        val actionIndex = event.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                activePointers.clear()
                activePointers[event.getPointerId(0)] = Pair(event.getX(0), event.getY(0))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (actionIndex in 0 until event.pointerCount) {
                    val id = event.getPointerId(actionIndex)
                    activePointers[id] = Pair(event.getX(actionIndex), event.getY(actionIndex))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    activePointers[id] = Pair(event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (actionIndex in 0 until event.pointerCount) {
                    val id = event.getPointerId(actionIndex)
                    activePointers.remove(id)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointers.clear()
            }
        }

        updateKeyMask()
        return true
    }

    private fun clearPointers() {
        if (activePointers.isNotEmpty() || currentKeyMask != RetroKey.NO_KEYS_MASK) {
            activePointers.clear()
            val oldMask = currentKeyMask
            currentKeyMask = RetroKey.NO_KEYS_MASK
            if (oldMask != currentKeyMask) {
                onKeyMaskChanged?.invoke(currentKeyMask)
            }
        }
    }

    private fun updateKeyMask() {
        val newMask = layout.resolvePointers(activePointers.values)
        if (newMask != currentKeyMask) {
            val newlyPressed = newMask and currentKeyMask.inv()
            if (newlyPressed != 0 && hapticFeedbackEnabledState) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onHapticFeedbackRequested?.invoke()
            }
            currentKeyMask = newMask
            onKeyMaskChanged?.invoke(currentKeyMask)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isControlsVisible) return

        val alphaInt = (opacity * 255).toInt().coerceIn(0, 255)
        val highlightAlphaInt = ((opacity * 1.5f).coerceAtMost(1.0f) * 255).toInt()

        for (control in layout.controls) {
            val isPressed = isControlPressed(control)
            val effectiveAlpha = if (isPressed) highlightAlphaInt else alphaInt

            basePaint.color = if (isPressed) Color.argb(effectiveAlpha, 120, 160, 220)
                              else Color.argb(effectiveAlpha, 60, 60, 60)
            strokePaint.color = if (isPressed) Color.argb(effectiveAlpha, 200, 220, 255)
                                else Color.argb(effectiveAlpha, 180, 180, 180)
            textPaint.alpha = effectiveAlpha
            textPaint.textSize = control.halfHeight * 0.7f

            when (control.shape) {
                ControlShape.CIRCLE -> {
                    canvas.drawCircle(control.cx, control.cy, control.halfWidth, basePaint)
                    canvas.drawCircle(control.cx, control.cy, control.halfWidth, strokePaint)
                }
                ControlShape.PILL -> {
                    tempRect.left = control.cx - control.halfWidth
                    tempRect.top = control.cy - control.halfHeight
                    tempRect.right = control.cx + control.halfWidth
                    tempRect.bottom = control.cy + control.halfHeight
                    val rx = control.halfHeight
                    canvas.drawRoundRect(tempRect, rx, rx, basePaint)
                    canvas.drawRoundRect(tempRect, rx, rx, strokePaint)
                }
                ControlShape.DPAD -> {
                    // Draw horizontal and vertical bars for DPAD
                    val barWidth = control.halfWidth * 0.38f
                    val rx = barWidth * 0.25f

                    // Vertical bar
                    tempRect.left = control.cx - barWidth
                    tempRect.top = control.cy - control.halfHeight
                    tempRect.right = control.cx + barWidth
                    tempRect.bottom = control.cy + control.halfHeight
                    canvas.drawRoundRect(tempRect, rx, rx, basePaint)
                    canvas.drawRoundRect(tempRect, rx, rx, strokePaint)

                    // Horizontal bar
                    tempRect.left = control.cx - control.halfWidth
                    tempRect.top = control.cy - barWidth
                    tempRect.right = control.cx + control.halfWidth
                    tempRect.bottom = control.cy + barWidth
                    canvas.drawRoundRect(tempRect, rx, rx, basePaint)
                    canvas.drawRoundRect(tempRect, rx, rx, strokePaint)
                }
            }

            if (control.label.isNotEmpty()) {
                canvas.drawText(control.label, control.cx, control.cy + textPaint.textSize * 0.35f, textPaint)
            }
        }
    }

    private fun isControlPressed(control: VirtualControl): Boolean {
        for (point in activePointers.values) {
            if (control.contains(point.first, point.second)) {
                return true
            }
        }
        return false
    }
}
