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
 * Virtual multi-touch controller overlay view and PPSSPP/Lemuroid-inspired
 * interactive controls placement editor for RetroPack.
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

    var isEditMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    clearPointers()
                    isControlsVisible = true
                } else {
                    selectedClusterId = null
                }
                onEditModeChanged?.invoke(value)
                invalidate()
            }
        }

    var layout: TouchLayout = TouchLayout.create(1080f, 1920f, opacity)
        private set

    /** Callback invoked whenever the composite RetroKey bitmask changes. */
    var onKeyMaskChanged: ((Int) -> Unit)? = null

    /** Test hook callback invoked when haptic feedback is triggered. */
    var onHapticFeedbackRequested: (() -> Unit)? = null

    /** Callback invoked when edit mode is toggled. */
    var onEditModeChanged: ((Boolean) -> Unit)? = null

    /** Callback invoked when custom layout is saved in edit mode. */
    var onLayoutSaved: ((TouchLayout) -> Unit)? = null

    /** Callback invoked when custom layout is reset to defaults. */
    var onLayoutReset: (() -> Unit)? = null

    // Edit mode drag tracking state
    var selectedClusterId: String? = null
        private set
    private var dragTouchStartX: Float = 0f
    private var dragTouchStartY: Float = 0f
    private var initialAnchorX: Float = 0f
    private var initialAnchorY: Float = 0f

    // Top banner hit testing areas for edit mode
    val topBannerRect = RectF()
    val resetBtnRect = RectF()
    val saveBtnRect = RectF()

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
    private val editorBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val editorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tempRect = RectF()

    /**
     * Updates the layout to match the provided dimensions or re-generates
     * default responsive coordinates for portrait/landscape orientation.
     */
    fun updateLayout(width: Float, height: Float) {
        if (width > 0f && height > 0f) {
            val isLandscape = width > height
            val defaultLayout = TouchLayout.create(width, height, opacity)
            val savedPositions = ControlsPreferences.loadLayoutPositions(context, isLandscape)
            layout = if (savedPositions != null) {
                defaultLayout.applyNormalizedClusterPositions(savedPositions)
            } else {
                defaultLayout
            }
            invalidate()
        }
    }

    /**
     * Loads and applies previously saved custom layout coordinates if present.
     */
    fun applySavedCustomLayout() {
        val isLandscape = if (width > 0 && height > 0) width > height else layout.width > layout.height
        val saved = ControlsPreferences.loadLayoutPositions(context, isLandscape)
        if (saved != null) {
            layout = layout.applyNormalizedClusterPositions(saved)
            invalidate()
        }
    }

    /**
     * Resets the active touch layout back to default geometric placement and clears persisted coordinates.
     */
    fun resetToDefaultLayout() {
        val w = if (width > 0) width.toFloat() else layout.width
        val h = if (height > 0) height.toFloat() else layout.height
        val isLandscape = w > h
        layout = TouchLayout.create(w, h, opacity)
        ControlsPreferences.clearCustomLayout(context, isLandscape)
        onLayoutReset?.invoke()
        invalidate()
    }

    /**
     * Persists current normalized coordinates to SharedPreferences and exits edit mode.
     */
    fun saveCurrentLayout() {
        val isLandscape = if (width > 0 && height > 0) width > height else layout.width > layout.height
        val positions = layout.getNormalizedClusterPositions()
        ControlsPreferences.saveLayoutPositions(context, isLandscape, positions)
        isEditMode = false
        onLayoutSaved?.invoke(layout)
        invalidate()
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
        if (isEditMode) {
            return handleEditModeTouchEvent(event)
        }

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

    private fun handleEditModeTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Check if user tapped banner buttons
                if (resetBtnRect.contains(x, y)) {
                    if (hapticFeedbackEnabledState) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onHapticFeedbackRequested?.invoke()
                    }
                    resetToDefaultLayout()
                    return true
                }
                if (saveBtnRect.contains(x, y)) {
                    if (hapticFeedbackEnabledState) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onHapticFeedbackRequested?.invoke()
                    }
                    saveCurrentLayout()
                    return true
                }

                // Hit test clusters
                val cluster = layout.findClusterAt(x, y, 28f)
                if (cluster != null) {
                    selectedClusterId = cluster.id
                    dragTouchStartX = x
                    dragTouchStartY = y
                    initialAnchorX = cluster.anchorX
                    initialAnchorY = cluster.anchorY
                    if (hapticFeedbackEnabledState) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onHapticFeedbackRequested?.invoke()
                    }
                    invalidate()
                    return true
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val clusterId = selectedClusterId
                if (clusterId != null) {
                    val cluster = layout.getCluster(clusterId)
                    if (cluster != null) {
                        val dx = x - dragTouchStartX
                        val dy = y - dragTouchStartY
                        val viewW = if (width > 0) width.toFloat() else layout.width
                        val viewH = if (height > 0) height.toFloat() else layout.height

                        val halfW = cluster.width * 0.5f
                        val halfH = cluster.height * 0.5f

                        val targetX = (initialAnchorX + dx).coerceIn(halfW + 16f, viewW - halfW - 16f)
                        val targetY = (initialAnchorY + dy).coerceIn(halfH + 72f, viewH - halfH - 16f)

                        layout = layout.withClusterPosition(clusterId, targetX, targetY)
                        invalidate()
                    }
                    return true
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (selectedClusterId != null) {
                    selectedClusterId = null
                    invalidate()
                }
                return true
            }
        }
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
        if (isEditMode) return // Suppress game input while editing layout

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

    fun renderForTesting(canvas: Canvas) {
        onDraw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isControlsVisible && !isEditMode) return

        val viewW = if (width > 0) width.toFloat() else layout.width
        val viewH = if (height > 0) height.toFloat() else layout.height

        // 1. Draw Edit Mode Background & Bounds
        if (isEditMode) {
            drawEditModeOverlay(canvas, viewW, viewH)
        }

        // 2. Draw Virtual Controls
        val alphaInt = if (isEditMode) 220 else (opacity * 255).toInt().coerceIn(0, 255)
        val highlightAlphaInt = if (isEditMode) 255 else ((opacity * 1.5f).coerceAtMost(1.0f) * 255).toInt()

        for (control in layout.controls) {
            val isPressed = !isEditMode && isControlPressed(control)
            val effectiveAlpha = if (isPressed) highlightAlphaInt else alphaInt

            basePaint.color = if (isPressed) Color.argb(effectiveAlpha, 120, 160, 220)
                              else Color.argb(effectiveAlpha, 45, 52, 68)
            strokePaint.color = if (isPressed) Color.argb(effectiveAlpha, 200, 220, 255)
                                else Color.argb(effectiveAlpha, 160, 180, 210)
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

    private fun drawEditModeOverlay(canvas: Canvas, viewW: Float, viewH: Float) {
        // Dim background
        editorFillPaint.color = Color.argb(90, 8, 12, 20)
        canvas.drawRect(0f, 0f, viewW, viewH, editorFillPaint)

        // Draw Cluster Bounding Boxes
        for (cluster in layout.getClusters()) {
            val isSelected = cluster.id == selectedClusterId
            val pad = 12f
            tempRect.left = cluster.left - pad
            tempRect.top = cluster.top - pad
            tempRect.right = cluster.right + pad
            tempRect.bottom = cluster.bottom + pad

            if (isSelected) {
                editorFillPaint.color = Color.argb(45, 0, 229, 255)
                canvas.drawRoundRect(tempRect, 12f, 12f, editorFillPaint)
                editorBoxPaint.color = Color.argb(255, 0, 229, 255)
                editorBoxPaint.strokeWidth = 3.5f
                canvas.drawRoundRect(tempRect, 12f, 12f, editorBoxPaint)

                // Drag handles on corners
                editorFillPaint.color = Color.argb(255, 255, 255, 255)
                canvas.drawCircle(tempRect.left, tempRect.top, 6f, editorFillPaint)
                canvas.drawCircle(tempRect.right, tempRect.top, 6f, editorFillPaint)
                canvas.drawCircle(tempRect.left, tempRect.bottom, 6f, editorFillPaint)
                canvas.drawCircle(tempRect.right, tempRect.bottom, 6f, editorFillPaint)
            } else {
                editorBoxPaint.color = Color.argb(140, 100, 180, 255)
                editorBoxPaint.strokeWidth = 2f
                canvas.drawRoundRect(tempRect, 12f, 12f, editorBoxPaint)
            }

            // Draw cluster label badge
            textPaint.textSize = 22f
            textPaint.color = if (isSelected) Color.argb(255, 0, 229, 255) else Color.argb(200, 180, 210, 255)
            canvas.drawText(cluster.label, cluster.anchorX, tempRect.top - 8f, textPaint)
        }

        // Top Banner
        val bannerW = Math.min(viewW * 0.90f, 520f)
        val bannerH = 50f
        val bannerLeft = (viewW - bannerW) * 0.5f
        val bannerTop = 20f
        topBannerRect.set(bannerLeft, bannerTop, bannerLeft + bannerW, bannerTop + bannerH)

        // Banner Glass Background
        editorFillPaint.color = Color.argb(235, 20, 26, 38)
        canvas.drawRoundRect(topBannerRect, 16f, 16f, editorFillPaint)
        editorBoxPaint.color = Color.argb(180, 60, 90, 140)
        editorBoxPaint.strokeWidth = 2f
        canvas.drawRoundRect(topBannerRect, 16f, 16f, editorBoxPaint)

        // "Reset Defaults" Button (Left)
        val btnW = 100f
        val btnH = 34f
        val btnY = bannerTop + (bannerH - btnH) * 0.5f
        resetBtnRect.set(bannerLeft + 8f, btnY, bannerLeft + 8f + btnW, btnY + btnH)

        editorFillPaint.color = Color.argb(220, 42, 53, 75)
        canvas.drawRoundRect(resetBtnRect, 10f, 10f, editorFillPaint)
        textPaint.textSize = 18f
        textPaint.color = Color.WHITE
        canvas.drawText("↺ Reset", resetBtnRect.centerX(), resetBtnRect.centerY() + 6f, textPaint)

        // Title (Center)
        textPaint.textSize = 20f
        textPaint.color = Color.argb(255, 0, 229, 255)
        canvas.drawText("Controls Placement", topBannerRect.centerX(), topBannerRect.centerY() + 7f, textPaint)

        // "Save & Exit" Button (Right)
        saveBtnRect.set(topBannerRect.right - btnW - 8f, btnY, topBannerRect.right - 8f, btnY + btnH)
        editorFillPaint.color = Color.argb(255, 0, 160, 240)
        canvas.drawRoundRect(saveBtnRect, 10f, 10f, editorFillPaint)
        textPaint.textSize = 18f
        textPaint.color = Color.WHITE
        canvas.drawText("✓ Save", saveBtnRect.centerX(), saveBtnRect.centerY() + 6f, textPaint)
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
