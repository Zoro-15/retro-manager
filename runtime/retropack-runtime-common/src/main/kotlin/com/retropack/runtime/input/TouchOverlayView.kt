package com.retropack.runtime.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.retropack.runtime.core.RetroKey
import java.util.Arrays

/**
 * Virtual multi-touch controller overlay view and PPSSPP/Lemuroid-inspired
 * interactive controls placement editor for RetroPack.
 *
 * Implements specifications from:
 * - masterplan.md Section 5.4: "Virtual TouchOverlayView (custom coords, opacity, haptics)
 *   + physical Bluetooth/USB gamepad HID handler. Auto-hides virtual controls when physical
 *   gamepad buttons are pressed."
 * - Virtual Analog Joystick Engine with Cardinal Grid Snapping (RPG vs Action Mode),
 *   Dual-Mode Origin (Fixed Movable vs Dynamic Floating), Pitch Black & Glowing Indigo Aesthetics,
 *   and Quadrant Detent Haptic Feedback.
 * - Touch ergonomics: 20% hitbox expansion, thumb-roll assist, inactivity auto-dimming (10% alpha after 4s),
 *   and per-cluster scale controls (0.5x to 2.0x).
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

    // Inactivity Auto-Dimming
    var autoDimEnabled: Boolean = true
    var inactivityTimeoutMs: Long = 4000L
    var lastTouchTimestampMs: Long = System.currentTimeMillis()
    var dimAlphaFactor: Float = 0.10f

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

    var theme: TouchTheme = TouchTheme.CLASSIC_INDIGO
        set(value) {
            field = value
            invalidate()
        }

    var turboEnabled: Boolean = false
        set(value) {
            field = value
            updateSuperpowerLayout()
        }

    var comboMacroEnabled: Boolean = false
        set(value) {
            field = value
            updateSuperpowerLayout()
        }

    // ─── Virtual Analog Joystick & D-Pad Engine ───
    val joystick: VirtualJoystick = VirtualJoystick()

    var dpadType: DpadType = DpadType.CLASSIC_CROSS
        set(value) {
            field = value
            if (value == DpadType.CLASSIC_CROSS) {
                joystick.onUp()
                dynamicDpadActive = false
                dynamicDpadPointerId = null
            } else {
                syncJoystickGeometry()
            }
            invalidate()
        }

    var joystickSnapMode: JoystickSnapMode
        get() = joystick.snapMode
        set(value) {
            joystick.snapMode = value
            invalidate()
        }

    var joystickDeadzone: Float
        get() = joystick.deadzoneRadius
        set(value) {
            joystick.deadzoneRadius = value
            invalidate()
        }

    var joystickSensitivity: Float = 1.0f

    /** Backward compatible property for dynamic floating D-Pad toggle. */
    var floatingDpadEnabled: Boolean
        get() = dpadType == DpadType.FLOATING_JOYSTICK
        set(value) {
            dpadType = if (value) DpadType.FLOATING_JOYSTICK else DpadType.CLASSIC_CROSS
            if (!value) {
                dynamicDpadActive = false
                dynamicDpadPointerId = null
                joystick.onUp()
            }
            invalidate()
        }

    var dynamicDpadActive: Boolean = false
        private set
    var dynamicDpadPointerId: Int? = null
        private set

    val floatingDpadActive: Boolean
        get() = dynamicDpadActive || (dpadType == DpadType.FLOATING_JOYSTICK && joystick.isActive)

    val floatingDpadX: Float
        get() = if (dpadType != DpadType.CLASSIC_CROSS && (joystick.isActive || dpadType == DpadType.FIXED_JOYSTICK)) {
            joystick.baseCenterX
        } else {
            layout.getCluster(TouchLayout.CLUSTER_DPAD)?.anchorX ?: 0f
        }

    val floatingDpadY: Float
        get() = if (dpadType != DpadType.CLASSIC_CROSS && (joystick.isActive || dpadType == DpadType.FIXED_JOYSTICK)) {
            joystick.baseCenterY
        } else {
            layout.getCluster(TouchLayout.CLUSTER_DPAD)?.anchorY ?: 0f
        }

    var hapticIntensity: Float = 1.0f

    var gesturesEnabled: Boolean = true
    val gestureDetector: RetroGestureDetector = RetroGestureDetector().apply {
        onTwoFingerSwipeLeft = { onQuickSaveRequested?.invoke() }
        onTwoFingerSwipeRight = { onQuickLoadRequested?.invoke() }
        onTwoFingerDoubleTap = { onToggleFastForwardRequested?.invoke() }
        onThreeFingerTap = { onToggleQuickMenuRequested?.invoke() }
    }

    // Multi-touch Gesture Callbacks
    var onQuickSaveRequested: (() -> Unit)? = null
    var onQuickLoadRequested: (() -> Unit)? = null
    var onToggleFastForwardRequested: (() -> Unit)? = null
    var onToggleQuickMenuRequested: (() -> Unit)? = null

    /** Stylus touch callback for Nintendo DS: (ndsX: 0..255, ndsY: 0..191, isTouching: Boolean) */
    var onStylusTouch: ((x: Int, y: Int, isTouching: Boolean) -> Unit)? = null

    /** Analog stick deflection callback: (normX: -1.0f..1.0f, normY: -1.0f..1.0f) */
    var onAnalogAxisChanged: ((Float, Float) -> Unit)? = null

    var platform: String = "gba"
        set(value) {
            field = value.lowercase().trim()
            val isN64 = field in setOf("n64", "z64", "v64")
            val isPsp = field in setOf("psp", "ppsspp", "ppsspp-unified")
            if ((isN64 || isPsp) && dpadType == DpadType.CLASSIC_CROSS) {
                dpadType = DpadType.FIXED_JOYSTICK
                joystickSnapMode = JoystickSnapMode.ANALOG_FREE
            }
            val w = if (width > 0) width.toFloat() else layout.width
            val h = if (height > 0) height.toFloat() else layout.height
            if (w > 0f && h > 0f) {
                updateLayout(w, h)
            }
        }

    var layout: TouchLayout = TouchLayout.createForPlatform(platform, 1080f, 1920f, opacity, turboEnabled, comboMacroEnabled)
        private set(value) {
            field = value
            rebuildHitboxCache()
        }

    val clusterScales = mutableMapOf<String, Float>()

    /** Callback invoked whenever the composite RetroKey bitmask changes. */
    var onKeyMaskChanged: ((Int) -> Unit)? = null

    /** Test hook callback invoked when haptic feedback is triggered. */
    var onHapticFeedbackRequested: (() -> Unit)? = null

    /** Test hook callback invoked when detent quadrant tick haptic is triggered. */
    var onHapticTickRequested: (() -> Unit)? = null

    /** Test hook callback invoked when haptic release is triggered. */
    var onHapticReleaseRequested: (() -> Unit)? = null

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

    // Top banner and scale controls hit testing areas for edit mode
    val topBannerRect = RectF()
    val resetBtnRect = RectF()
    val saveBtnRect = RectF()
    val scaleControlRect = RectF()
    val scaleDownBtnRect = RectF()
    val scaleUpBtnRect = RectF()

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

    /* ─────────────────────────────────────────────────────────────────────
     * Zero-allocation touch tracking (issue #44)
     *
     * Pointer state lives in parallel primitive arrays instead of a
     * MutableMap<Int, Pair<Float, Float>>, eliminating the map entry and
     * Pair allocations that previously fired on every pointer update of
     * every MotionEvent (up to 120 Hz on modern digitizers).
     *
     * Pre-computed hitbox regions: layout.controls are flattened into
     * contiguous primitive float/boolean/int arrays whenever the layout
     * changes, so the per-frame hit test never allocates a RectF, never
     * boxes, and never iterates a LinkedList of data classes.
     * ───────────────────────────────────────────────────────────────────── */

    private companion object {
        const val MAX_TRACKED_POINTERS = 16
    }

    private val pointerIds = IntArray(MAX_TRACKED_POINTERS) { -1 }
    private val pointerX = FloatArray(MAX_TRACKED_POINTERS)
    private val pointerY = FloatArray(MAX_TRACKED_POINTERS)
    private val pointerActive = BooleanArray(MAX_TRACKED_POINTERS)

    // Flattened hitbox cache (columns indexed by control index).
    private var hitCount: Int = 0
    private var hitCx = FloatArray(0)
    private var hitCy = FloatArray(0)
    private var hitHalfW = FloatArray(0)      // slop-expanded
    private var hitHalfH = FloatArray(0)      // slop-expanded
    private var hitShapeRect = BooleanArray(0) // true: PILL/DPAD rect test, false: CIRCLE
    private var hitIsDpad = BooleanArray(0)
    private var hitDpadDeadzone = FloatArray(0) // raw (non-sloped) halfWidth * 0.20
    private var hitCustomMask = IntArray(0)     // customKeyMask (0 when unset)
    private var hitKeyMaskBase = IntArray(0)    // key?.mask (0 when unset)
    private var hitIsTurbo = BooleanArray(0)

    // Thumb Roll Assist anchors (B → A transition zone), cached from layout.
    private var rollEnabled: Boolean = false
    private var rollACx: Float = 0f
    private var rollACy: Float = 0f
    private var rollAHalfW: Float = 0f
    private var rollBCx: Float = 0f
    private var rollBCy: Float = 0f
    private var rollBHalfW: Float = 0f

    init {
        rebuildHitboxCache()
    }

    private fun rebuildHitboxCache() {
        val controls = layout.controls
        hitCount = controls.size
        if (hitCx.size < hitCount) {
            hitCx = FloatArray(hitCount)
            hitCy = FloatArray(hitCount)
            hitHalfW = FloatArray(hitCount)
            hitHalfH = FloatArray(hitCount)
            hitShapeRect = BooleanArray(hitCount)
            hitIsDpad = BooleanArray(hitCount)
            hitDpadDeadzone = FloatArray(hitCount)
            hitCustomMask = IntArray(hitCount)
            hitKeyMaskBase = IntArray(hitCount)
            hitIsTurbo = BooleanArray(hitCount)
        }
        var aIdx = -1
        var bIdx = -1
        for (i in 0 until hitCount) {
            val c = controls[i]
            hitCx[i] = c.cx
            hitCy[i] = c.cy
            hitHalfW[i] = c.halfWidth * TouchLayout.DEFAULT_HIT_SLOP
            hitHalfH[i] = c.halfHeight * TouchLayout.DEFAULT_HIT_SLOP
            hitShapeRect[i] = c.shape != ControlShape.CIRCLE
            hitIsDpad[i] = c.shape == ControlShape.DPAD
            hitDpadDeadzone[i] = c.halfWidth * 0.20f
            hitCustomMask[i] = c.customKeyMask
            hitKeyMaskBase[i] = c.key?.mask ?: RetroKey.NO_KEYS_MASK
            hitIsTurbo[i] = c.isTurbo
            when (c.id) {
                TouchLayout.ID_A -> aIdx = i
                TouchLayout.ID_B -> bIdx = i
            }
        }
        rollEnabled = aIdx >= 0 && bIdx >= 0
        if (rollEnabled) {
            rollACx = controls[aIdx].cx
            rollACy = controls[aIdx].cy
            rollAHalfW = controls[aIdx].halfWidth
            rollBCx = controls[bIdx].cx
            rollBCy = controls[bIdx].cy
            rollBHalfW = controls[bIdx].halfWidth
        }
    }

    private fun slotForPointer(id: Int): Int {
        for (i in 0 until MAX_TRACKED_POINTERS) {
            if (pointerActive[i] && pointerIds[i] == id) return i
        }
        return -1
    }

    private fun assignPointerSlot(id: Int): Int {
        var freeSlot = -1
        for (i in 0 until MAX_TRACKED_POINTERS) {
            if (pointerActive[i]) {
                if (pointerIds[i] == id) return i
            } else if (freeSlot < 0) {
                freeSlot = i
            }
        }
        if (freeSlot >= 0) {
            pointerIds[freeSlot] = id
            pointerActive[freeSlot] = true
            pointerX[freeSlot] = 0f
            pointerY[freeSlot] = 0f
        }
        return freeSlot
    }

    private fun releasePointerSlot(id: Int) {
        for (i in 0 until MAX_TRACKED_POINTERS) {
            if (pointerActive[i] && pointerIds[i] == id) {
                pointerActive[i] = false
                pointerIds[i] = -1
                return
            }
        }
    }

    private fun clearPointerSlots() {
        Arrays.fill(pointerIds, -1)
        Arrays.fill(pointerActive, false)
    }

    private fun anyPointerActive(): Boolean {
        for (i in 0 until MAX_TRACKED_POINTERS) {
            if (pointerActive[i]) return true
        }
        return false
    }

    /**
     * Cache-based, allocation-free equivalent of TouchLayout.inputAt():
     * OR-composes the key masks of every control whose slop-expanded hitbox
     * contains (x, y), including the D-pad 8-way resolution and the
     * Thumb Roll Assist transition zone. When [skipDpad] is set (joystick
     * dpad modes), D-pad shaped controls contribute no mask.
     */
    private fun hitTestMaskAt(x: Float, y: Float, isTurboPhase: Boolean, skipDpad: Boolean = false): Int {
        var mask = RetroKey.NO_KEYS_MASK
        for (i in 0 until hitCount) {
            if (skipDpad && hitIsDpad[i]) continue

            val dx = x - hitCx[i]
            val dy = y - hitCy[i]
            val sw = hitHalfW[i]
            val sh = hitHalfH[i]
            if (sw <= 0f || sh <= 0f) continue
            val inside = if (hitShapeRect[i]) {
                dx >= -sw && dx <= sw && dy >= -sh && dy <= sh
            } else {
                val nx = dx / sw
                val ny = dy / sh
                nx * nx + ny * ny <= 1.0f
            }
            if (!inside) continue

            mask = mask or when {
                hitIsDpad[i] -> {
                    var d = RetroKey.NO_KEYS_MASK
                    val dead = hitDpadDeadzone[i]
                    if (dx < -dead) d = d or RetroKey.KEY_LEFT
                    if (dx > dead) d = d or RetroKey.KEY_RIGHT
                    if (dy < -dead) d = d or RetroKey.KEY_UP
                    if (dy > dead) d = d or RetroKey.KEY_DOWN
                    d
                }
                hitCustomMask[i] != RetroKey.NO_KEYS_MASK -> hitCustomMask[i]
                hitIsTurbo[i] && !isTurboPhase -> RetroKey.NO_KEYS_MASK
                else -> hitKeyMaskBase[i]
            }
        }

        if (!skipDpad && rollEnabled) {
            // Thumb Roll Assist: touch falling into the transition zone between B and A.
            val distA = dist(x, y, rollACx, rollACy)
            val distB = dist(x, y, rollBCx, rollBCy)
            val btnDistance = dist(rollACx, rollACy, rollBCx, rollBCy)
            if (distA + distB <= btnDistance * 1.25f && distA <= rollAHalfW * 1.5f && distB <= rollBHalfW * 1.5f) {
                mask = mask or RetroKey.KEY_A or RetroKey.KEY_B
            }
        }
        return mask
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    /**
     * Resets inactivity timer on any touch interaction.
     */
    fun notifyTouchActivity(currentTimeMs: Long = System.currentTimeMillis()) {
        lastTouchTimestampMs = currentTimeMs
        invalidate()
    }

    /**
     * Returns effective visual opacity accounting for inactivity auto-dimming.
     */
    fun getEffectiveOpacity(currentTimeMs: Long = System.currentTimeMillis()): Float {
        if (!isEditMode && autoDimEnabled && (currentTimeMs - lastTouchTimestampMs >= inactivityTimeoutMs)) {
            return (opacity * dimAlphaFactor).coerceAtLeast(0.05f)
        }
        return opacity
    }

    private fun syncJoystickGeometry() {
        val dpadCluster = layout.getCluster(TouchLayout.CLUSTER_DPAD)
        val scale = clusterScales[TouchLayout.CLUSTER_DPAD] ?: 1.0f
        if (dpadCluster != null) {
            if (!joystick.isActive || dpadType == DpadType.FIXED_JOYSTICK) {
                joystick.baseCenterX = dpadCluster.anchorX
                joystick.baseCenterY = dpadCluster.anchorY
            }
        }
        joystick.baseRadius = VirtualJoystick.DEFAULT_BASE_RADIUS * scale
        joystick.knobRadius = VirtualJoystick.DEFAULT_KNOB_RADIUS * scale
        joystick.deadzoneRadius = VirtualJoystick.DEFAULT_DEADZONE_RADIUS * scale
    }

    private fun updateSuperpowerLayout() {
        val isLandscape = if (width > 0 && height > 0) width > height else layout.width > layout.height
        val w = if (width > 0) width.toFloat() else layout.width
        val h = if (height > 0) height.toFloat() else layout.height
        val defaultLayout = TouchLayout.createForPlatform(platform, w, h, opacity, turboEnabled, comboMacroEnabled)
        val savedPositions = ControlsPreferences.loadLayoutPositions(context, isLandscape)
        val savedScales = ControlsPreferences.loadLayoutScales(context, isLandscape)

        var newLayout = defaultLayout
        if (savedScales != null) {
            newLayout = newLayout.applyClusterScales(savedScales)
        }
        if (savedPositions != null) {
            newLayout = newLayout.applyNormalizedClusterPositions(savedPositions)
        }
        layout = newLayout
        syncJoystickGeometry()
        invalidate()
    }

    /**
     * Updates the layout to match the provided dimensions or re-generates
     * default responsive coordinates for portrait/landscape orientation.
     */
    fun updateLayout(width: Float, height: Float) {
        if (width > 0f && height > 0f) {
            val isLandscape = width > height
            val defaultLayout = TouchLayout.createForPlatform(platform, width, height, opacity, turboEnabled, comboMacroEnabled)
            val savedPositions = ControlsPreferences.loadLayoutPositions(context, isLandscape)
            val savedScales = ControlsPreferences.loadLayoutScales(context, isLandscape)

            clusterScales.clear()
            if (savedScales != null) {
                clusterScales.putAll(savedScales)
            }

            var newLayout = defaultLayout
            if (savedScales != null) {
                newLayout = newLayout.applyClusterScales(savedScales)
            }
            if (savedPositions != null) {
                newLayout = newLayout.applyNormalizedClusterPositions(savedPositions)
            }
            layout = newLayout
            syncJoystickGeometry()
            invalidate()
        }
    }

    /**
     * Loads and applies previously saved custom layout coordinates and scales if present.
     */
    fun applySavedCustomLayout() {
        val isLandscape = if (width > 0 && height > 0) width > height else layout.width > layout.height
        val savedPositions = ControlsPreferences.loadLayoutPositions(context, isLandscape)
        val savedScales = ControlsPreferences.loadLayoutScales(context, isLandscape)

        clusterScales.clear()
        if (savedScales != null) {
            clusterScales.putAll(savedScales)
        }

        var newLayout = TouchLayout.createForPlatform(
            platform,
            if (width > 0) width.toFloat() else layout.width,
            if (height > 0) height.toFloat() else layout.height,
            opacity, turboEnabled, comboMacroEnabled
        )
        if (savedScales != null) {
            newLayout = newLayout.applyClusterScales(savedScales)
        }
        if (savedPositions != null) {
            newLayout = newLayout.applyNormalizedClusterPositions(savedPositions)
        }
        layout = newLayout
        syncJoystickGeometry()
        invalidate()
    }

    /**
     * Resets the active touch layout back to default geometric placement and clears persisted coordinates.
     */
    fun resetToDefaultLayout() {
        val w = if (width > 0) width.toFloat() else layout.width
        val h = if (height > 0) height.toFloat() else layout.height
        val isLandscape = w > h
        clusterScales.clear()
        layout = TouchLayout.createForPlatform(platform, w, h, opacity, turboEnabled, comboMacroEnabled)
        ControlsPreferences.clearCustomLayout(context, isLandscape)
        syncJoystickGeometry()
        onLayoutReset?.invoke()
        invalidate()
    }

    /**
     * Persists current normalized coordinates and cluster scales to SharedPreferences and exits edit mode.
     */
    fun saveCurrentLayout() {
        val isLandscape = if (width > 0 && height > 0) width > height else layout.width > layout.height
        val positions = layout.getNormalizedClusterPositions()
        ControlsPreferences.saveLayoutPositions(context, isLandscape, positions)
        ControlsPreferences.saveLayoutScales(context, isLandscape, clusterScales)
        isEditMode = false
        onLayoutSaved?.invoke(layout)
        invalidate()
    }

    /**
     * Adjusts the scale factor for a specific control cluster (clamped between 0.5x and 2.0x).
     */
    fun setClusterScale(clusterId: String, scale: Float) {
        val clamped = scale.coerceIn(0.5f, 2.0f)
        clusterScales[clusterId] = clamped
        layout = layout.withClusterScale(clusterId, clamped)
        syncJoystickGeometry()
        invalidate()
    }

    /**
     * Replaces the active layout with a customized [TouchLayout] instance.
     */
    fun setCustomLayout(customLayout: TouchLayout) {
        this.layout = customLayout
        this.opacity = customLayout.opacity
        syncJoystickGeometry()
        invalidate()
    }

    /**
     * Returns the currently active composite RetroKey bitmask.
     */
    val activeKeyMask: Int
        get() = currentKeyMask

    fun getKeyMask(): Int = currentKeyMask

    override fun onTouchEvent(event: MotionEvent): Boolean {
        notifyTouchActivity()

        if (isEditMode) {
            return handleEditModeTouchEvent(event)
        }

        // 1. Process Multi-Touch Gestures
        if (gesturesEnabled) {
            gestureDetector.gesturesEnabled = true
            gestureDetector.onTouchEvent(event)
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
        val viewW = if (width > 0) width.toFloat() else layout.width

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                clearPointerSlots()
                val id0 = event.getPointerId(0)
                val x0 = event.getX(0)
                val y0 = event.getY(0)
                val slot0 = assignPointerSlot(id0)
                if (slot0 >= 0) {
                    pointerX[slot0] = x0
                    pointerY[slot0] = y0
                }

                syncJoystickGeometry()

                if (dpadType == DpadType.FLOATING_JOYSTICK) {
                    if (x0 < viewW * 0.5f) {
                        dynamicDpadPointerId = id0
                        dynamicDpadActive = true
                        layout = layout.withClusterPosition(TouchLayout.CLUSTER_DPAD, x0, y0)
                        val res = joystick.onDown(x0, y0, id0, isFloating = true)
                        if (res.detentCrossed && !res.isDeadzone) {
                            triggerDetentHaptic()
                        }
                    }
                } else if (dpadType == DpadType.FIXED_JOYSTICK) {
                    val dx = x0 - joystick.baseCenterX
                    val dy = y0 - joystick.baseCenterY
                    val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (dist <= joystick.baseRadius * 1.5f || (x0 < viewW * 0.45f && Math.abs(dy) <= joystick.baseRadius * 1.8f)) {
                        dynamicDpadPointerId = id0
                        dynamicDpadActive = true
                        val res = joystick.onDown(x0, y0, id0, isFloating = false)
                        if (res.detentCrossed && !res.isDeadzone) {
                            triggerDetentHaptic()
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (actionIndex in 0 until event.pointerCount) {
                    val id = event.getPointerId(actionIndex)
                    val px = event.getX(actionIndex)
                    val py = event.getY(actionIndex)
                    val slot = assignPointerSlot(id)
                    if (slot >= 0) {
                        pointerX[slot] = px
                        pointerY[slot] = py
                    }

                    syncJoystickGeometry()

                    if (dpadType == DpadType.FLOATING_JOYSTICK && !joystick.isActive) {
                        if (px < viewW * 0.5f) {
                            dynamicDpadPointerId = id
                            dynamicDpadActive = true
                            layout = layout.withClusterPosition(TouchLayout.CLUSTER_DPAD, px, py)
                            val res = joystick.onDown(px, py, id, isFloating = true)
                            if (res.detentCrossed && !res.isDeadzone) {
                                triggerDetentHaptic()
                            }
                        }
                    } else if (dpadType == DpadType.FIXED_JOYSTICK && !joystick.isActive) {
                        val dx = px - joystick.baseCenterX
                        val dy = py - joystick.baseCenterY
                        val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (dist <= joystick.baseRadius * 1.5f || (px < viewW * 0.45f && Math.abs(dy) <= joystick.baseRadius * 1.8f)) {
                            dynamicDpadPointerId = id
                            dynamicDpadActive = true
                            val res = joystick.onDown(px, py, id, isFloating = false)
                            if (res.detentCrossed && !res.isDeadzone) {
                                triggerDetentHaptic()
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Issue #44: historical motion event coalescing. On 90/120 Hz
                // digitizers Android batches several samples into one
                // MotionEvent; only reading getX/getY drops the intermediate
                // touch points. Every historical sample feeds the joystick
                // (keeping fast flicks continuous); the newest sample of the
                // batch additionally refreshes the stored hit-test position.
                val historySize = event.historySize
                for (h in 0 until historySize) {
                    for (i in 0 until event.pointerCount) {
                        val id = event.getPointerId(i)
                        val px = event.getHistoricalX(i, h)
                        val py = event.getHistoricalY(i, h)
                        val slot = assignPointerSlot(id)
                        if (slot < 0) continue
                        pointerX[slot] = px
                        pointerY[slot] = py
                        if (joystick.isActive && joystick.pointerId == id) {
                            joystick.update(px, py)
                            onAnalogAxisChanged?.invoke(joystick.normX, joystick.normY)
                        }
                    }
                }
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val px = event.getX(i)
                    val py = event.getY(i)
                    val slot = assignPointerSlot(id)
                    if (slot < 0) continue
                    pointerX[slot] = px
                    pointerY[slot] = py
                    if (joystick.isActive && joystick.pointerId == id) {
                        val res = joystick.update(px, py)
                        onAnalogAxisChanged?.invoke(res.normX, res.normY)
                        if (res.detentCrossed && !res.isDeadzone) {
                            triggerDetentHaptic()
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (actionIndex in 0 until event.pointerCount) {
                    val id = event.getPointerId(actionIndex)
                    releasePointerSlot(id)
                    if (joystick.isActive && joystick.pointerId == id) {
                        joystick.onUp()
                        onAnalogAxisChanged?.invoke(0f, 0f)
                        dynamicDpadActive = false
                        dynamicDpadPointerId = null
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                clearPointerSlots()
                joystick.onUp()
                onAnalogAxisChanged?.invoke(0f, 0f)
                dynamicDpadActive = false
                dynamicDpadPointerId = null
                onStylusTouch?.invoke(0, 0, false)
            }
        }

        updateStylusTouch()
        updateKeyMask()
        return true
    }

    private fun updateStylusTouch() {
        val callback = onStylusTouch ?: return
        val viewW = if (width > 0) width.toFloat() else layout.width
        val viewH = if (height > 0) height.toFloat() else layout.height
        if (viewW <= 0f || viewH <= 0f) return

        // Check active pointers that are not hitting physical buttons or joystick
        // (issue #44: primitive array iteration, no Map/Pair allocation).
        for (slot in 0 until MAX_TRACKED_POINTERS) {
            if (!pointerActive[slot]) continue
            val px = pointerX[slot]
            val py = pointerY[slot]
            // If pointer is inside a button or active joystick, skip it
            if (hitTestMaskAt(px, py, isTurboPhase()) != RetroKey.NO_KEYS_MASK) continue
            if (joystick.isActive && Math.hypot((px - joystick.baseCenterX).toDouble(), (py - joystick.baseCenterY).toDouble()) <= joystick.baseRadius * 1.5) continue

            // Bottom screen region for NDS (256x384 stacked display)
            val targetAspect = 256.0f / 384.0f
            val currentAspect = viewW / viewH
            val vpW: Float
            val vpH: Float
            val vpX: Float
            val vpY: Float

            if (currentAspect > targetAspect) {
                vpH = viewH
                vpW = viewH * targetAspect
                vpX = (viewW - vpW) * 0.5f
                vpY = 0f
            } else {
                vpW = viewW
                vpH = viewW / targetAspect
                vpX = 0f
                vpY = (viewH - vpH) * 0.5f
            }

            val bottomTop = vpY + vpH * 0.5f
            val bottomHeight = vpH * 0.5f

            if (px >= vpX && px <= vpX + vpW && py >= bottomTop && py <= vpY + vpH) {
                val ndsX = (((px - vpX) / vpW) * 256f).toInt().coerceIn(0, 255)
                val ndsY = (((py - bottomTop) / bottomHeight) * 192f).toInt().coerceIn(0, 191)
                callback.invoke(ndsX, ndsY, true)
                return
            }
        }
    }

    private fun handleEditModeTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Check if user tapped banner buttons
                if (resetBtnRect.contains(x, y)) {
                    triggerHaptic()
                    resetToDefaultLayout()
                    return true
                }
                if (saveBtnRect.contains(x, y)) {
                    triggerHaptic()
                    saveCurrentLayout()
                    return true
                }

                // Check if user tapped scale adjustment buttons
                val clusterId = selectedClusterId
                if (clusterId != null) {
                    if (scaleDownBtnRect.contains(x, y)) {
                        triggerHaptic()
                        val currentScale = clusterScales[clusterId] ?: 1.0f
                        setClusterScale(clusterId, (currentScale - 0.1f).coerceAtLeast(0.5f))
                        return true
                    }
                    if (scaleUpBtnRect.contains(x, y)) {
                        triggerHaptic()
                        val currentScale = clusterScales[clusterId] ?: 1.0f
                        setClusterScale(clusterId, (currentScale + 0.1f).coerceAtMost(2.0f))
                        return true
                    }
                }

                // Hit test clusters
                val cluster = layout.findClusterAt(x, y, 28f)
                if (cluster != null) {
                    selectedClusterId = cluster.id
                    dragTouchStartX = x
                    dragTouchStartY = y
                    initialAnchorX = cluster.anchorX
                    initialAnchorY = cluster.anchorY
                    triggerHaptic()
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
                        syncJoystickGeometry()
                        invalidate()
                    }
                    return true
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Keep cluster selected for scale adjustment until another touch
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

    private fun triggerDetentHaptic() {
        if (hapticFeedbackEnabledState) {
            HapticEngine.triggerDetent(context, hapticIntensity, this)
            onHapticTickRequested?.invoke()
            onHapticFeedbackRequested?.invoke()
        }
    }

    private fun clearPointers() {
        if (anyPointerActive() || currentKeyMask != RetroKey.NO_KEYS_MASK) {
            clearPointerSlots()
            joystick.onUp()
            onAnalogAxisChanged?.invoke(0f, 0f)
            dynamicDpadActive = false
            dynamicDpadPointerId = null
            val oldMask = currentKeyMask
            currentKeyMask = RetroKey.NO_KEYS_MASK
            if (oldMask != currentKeyMask) {
                if (hapticFeedbackEnabledState) {
                    HapticEngine.triggerRelease(context, hapticIntensity, this)
                    onHapticReleaseRequested?.invoke()
                }
                onKeyMaskChanged?.invoke(currentKeyMask)
            }
        }
    }

    fun isTurboPhase(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        // 30Hz auto-repeat: ~33ms alternating press/release cycle
        return (currentTimeMs / 33) % 2L == 0L
    }

    private fun updateKeyMask() {
        if (isEditMode) return // Suppress game input while editing layout

        // Issue #44: hit-testing runs against the flattened primitive hitbox
        // cache with zero per-event allocation (no Map.filter, no Pair, no
        // RectF). Touch pointer state lives in parallel primitive arrays.
        val turboPhase = isTurboPhase()
        val newMask: Int = if (dpadType == DpadType.CLASSIC_CROSS) {
            var mask = RetroKey.NO_KEYS_MASK
            for (i in 0 until MAX_TRACKED_POINTERS) {
                if (!pointerActive[i]) continue
                mask = mask or hitTestMaskAt(pointerX[i], pointerY[i], turboPhase)
            }
            mask
        } else {
            val joystickSlot = if (joystick.isActive) slotForPointer(joystick.pointerId ?: -1) else -1
            var mask = RetroKey.NO_KEYS_MASK
            for (i in 0 until MAX_TRACKED_POINTERS) {
                if (!pointerActive[i] || i == joystickSlot) continue
                // Joystick dpad modes resolve the D-pad from the analog
                // engine; pointer hit-testing contributes buttons only.
                mask = mask or hitTestMaskAt(pointerX[i], pointerY[i], turboPhase, skipDpad = true)
            }
            mask or joystick.currentKeyMask
        }

        if (newMask != currentKeyMask) {
            val newlyPressed = newMask and currentKeyMask.inv()
            val newlyReleased = currentKeyMask and newMask.inv()
            if (hapticFeedbackEnabledState) {
                if (newlyPressed != 0) {
                    HapticEngine.triggerPress(context, hapticIntensity, this)
                    onHapticFeedbackRequested?.invoke()
                } else if (newlyReleased != 0) {
                    HapticEngine.triggerRelease(context, hapticIntensity, this)
                    onHapticReleaseRequested?.invoke()
                }
            }
            currentKeyMask = newMask
            onKeyMaskChanged?.invoke(currentKeyMask)
            invalidate()
        }
    }

    private fun getControlColors(control: VirtualControl, isPressed: Boolean): Triple<Int, Int, Int> {
        val isSnes = platform in setOf("snes", "sfc", "smc")
        val isGenesis = platform in setOf("genesis", "md", "smd", "gen", "sms", "gg", "megadrive")
        val isNes = platform in setOf("nes", "fds", "unf")
        val isPce = platform in setOf("pce", "tg16", "sgx")
        val isPsx = platform in setOf("psx", "ps1", "ps")
        val isPsp = platform in setOf("psp", "ppsspp", "ppsspp-unified")
        val isNds = platform in setOf("nds", "dsi", "melonds", "melonds-unified")
        val isArcade = platform in setOf("arcade", "fbneo", "neogeo", "cps", "cps1", "cps2", "cps3")

        val (fill, stroke, text) = when (control.id) {
            TouchLayout.ID_DPAD -> Triple(theme.dpadFillColor, theme.dpadStrokeColor, theme.dpadTextColor)
            TouchLayout.ID_A -> when {
                platform in setOf("n64", "z64", "v64") ->
                    Triple(Color.argb(210, 20, 80, 200), Color.argb(255, 60, 130, 240), Color.WHITE)
                isSnes ->
                    Triple(Color.argb(230, 75, 45, 125), Color.argb(255, 105, 75, 160), Color.WHITE)
                isGenesis ->
                    Triple(Color.argb(230, 24, 24, 28), Color.argb(255, 220, 45, 45), Color.WHITE)
                isNes ->
                    Triple(Color.argb(230, 220, 30, 30), Color.argb(255, 240, 75, 75), Color.WHITE)
                isPce ->
                    Triple(Color.argb(230, 235, 100, 20), Color.argb(255, 255, 140, 50), Color.WHITE)
                isPsx || isPsp ->
                    Triple(Color.argb(220, 100, 25, 30), Color.argb(255, 255, 60, 70), Color.argb(255, 255, 60, 70))
                isNds ->
                    Triple(Color.argb(230, 40, 44, 54), Color.argb(255, 245, 70, 80), Color.WHITE)
                isArcade ->
                    Triple(Color.argb(230, 220, 30, 40), Color.argb(255, 255, 70, 80), Color.WHITE)
                else ->
                    Triple(theme.actionAFillColor, theme.actionAStrokeColor, theme.actionTextColor)
            }
            TouchLayout.ID_B -> when {
                platform in setOf("n64", "z64", "v64") ->
                    Triple(Color.argb(210, 20, 150, 60), Color.argb(255, 50, 200, 90), Color.WHITE)
                isSnes ->
                    Triple(Color.argb(230, 75, 45, 125), Color.argb(255, 105, 75, 160), Color.WHITE)
                isGenesis ->
                    Triple(Color.argb(230, 24, 24, 28), Color.argb(255, 210, 210, 215), Color.WHITE)
                isNes ->
                    Triple(Color.argb(230, 220, 30, 30), Color.argb(255, 240, 75, 75), Color.WHITE)
                isPce ->
                    Triple(Color.argb(230, 235, 100, 20), Color.argb(255, 255, 140, 50), Color.WHITE)
                isPsx || isPsp ->
                    Triple(Color.argb(220, 25, 50, 110), Color.argb(255, 68, 136, 255), Color.argb(255, 68, 136, 255))
                isNds ->
                    Triple(Color.argb(230, 40, 44, 54), Color.argb(255, 245, 195, 40), Color.WHITE)
                isArcade ->
                    Triple(Color.argb(230, 225, 185, 20), Color.argb(255, 255, 220, 50), Color.BLACK)
                else ->
                    Triple(theme.actionBFillColor, theme.actionBStrokeColor, theme.actionTextColor)
            }
            TouchLayout.ID_C -> when {
                isGenesis ->
                    Triple(Color.argb(230, 24, 24, 28), Color.argb(255, 0, 229, 255), Color.WHITE)
                isArcade ->
                    Triple(Color.argb(230, 30, 180, 80), Color.argb(255, 60, 225, 120), Color.WHITE)
                else ->
                    Triple(theme.actionAFillColor, theme.actionAStrokeColor, theme.actionTextColor)
            }
            TouchLayout.ID_X -> when {
                isSnes ->
                    Triple(Color.argb(230, 160, 150, 200), Color.argb(255, 195, 185, 230), Color.argb(255, 45, 25, 75))
                isGenesis ->
                    Triple(Color.argb(230, 24, 24, 28), Color.argb(255, 220, 45, 45), Color.argb(255, 220, 45, 45))
                isPsx || isPsp ->
                    Triple(Color.argb(220, 20, 90, 50), Color.argb(255, 0, 230, 118), Color.argb(255, 0, 230, 118))
                isNds ->
                    Triple(Color.argb(230, 40, 44, 54), Color.argb(255, 0, 210, 255), Color.WHITE)
                isArcade ->
                    Triple(Color.argb(230, 220, 30, 40), Color.argb(255, 255, 70, 80), Color.WHITE)
                else ->
                    Triple(theme.actionAFillColor, theme.actionAStrokeColor, theme.actionTextColor)
            }
            TouchLayout.ID_Y -> when {
                isSnes ->
                    Triple(Color.argb(230, 160, 150, 200), Color.argb(255, 195, 185, 230), Color.argb(255, 45, 25, 75))
                isGenesis ->
                    Triple(Color.argb(230, 24, 24, 28), Color.argb(255, 210, 210, 215), Color.WHITE)
                isPsx || isPsp ->
                    Triple(Color.argb(220, 100, 30, 75), Color.argb(255, 255, 85, 187), Color.argb(255, 255, 85, 187))
                isNds ->
                    Triple(Color.argb(230, 40, 44, 54), Color.argb(255, 60, 225, 120), Color.WHITE)
                isArcade ->
                    Triple(Color.argb(230, 225, 185, 20), Color.argb(255, 255, 220, 50), Color.BLACK)
                else ->
                    Triple(theme.actionBFillColor, theme.actionBStrokeColor, theme.actionTextColor)
            }
            TouchLayout.ID_Z -> when {
                isGenesis ->
                    Triple(Color.argb(230, 24, 24, 28), Color.argb(255, 0, 229, 255), Color.argb(255, 0, 229, 255))
                isArcade ->
                    Triple(Color.argb(230, 30, 100, 220), Color.argb(255, 70, 150, 255), Color.WHITE)
                else ->
                    Triple(Color.argb(200, 60, 65, 75), Color.argb(240, 100, 110, 125), Color.WHITE)
            }
            TouchLayout.ID_C_UP, TouchLayout.ID_C_DOWN, TouchLayout.ID_C_LEFT, TouchLayout.ID_C_RIGHT ->
                Triple(Color.argb(210, 230, 175, 10), Color.argb(255, 255, 205, 30), Color.argb(255, 40, 30, 10))
            TouchLayout.ID_TURBO_A, TouchLayout.ID_TURBO_B -> when {
                isNes -> Triple(Color.argb(220, 180, 25, 25), Color.argb(255, 240, 80, 80), Color.WHITE)
                isPce -> Triple(Color.argb(220, 200, 80, 15), Color.argb(255, 255, 140, 50), Color.WHITE)
                isPsx || isPsp -> Triple(Color.argb(220, 40, 50, 80), Color.argb(255, 68, 136, 255), Color.WHITE)
                isNds -> Triple(Color.argb(220, 35, 40, 50), Color.argb(255, 0, 210, 255), Color.WHITE)
                else -> Triple(theme.turboFillColor, theme.actionAStrokeColor, theme.actionTextColor)
            }
            TouchLayout.ID_COMBO_AB -> Triple(theme.comboFillColor, theme.accentColor, Color.WHITE)
            TouchLayout.ID_L, TouchLayout.ID_R, TouchLayout.ID_L2, TouchLayout.ID_R2 -> when {
                isSnes ->
                    Triple(Color.argb(210, 85, 90, 105), Color.argb(255, 130, 135, 150), Color.WHITE)
                isPsx || isPsp ->
                    Triple(Color.argb(220, 38, 42, 54), Color.argb(255, 80, 90, 115), Color.WHITE)
                isNds ->
                    Triple(Color.argb(220, 42, 46, 56), Color.argb(255, 90, 100, 120), Color.WHITE)
                else ->
                    Triple(theme.shoulderFillColor, theme.shoulderStrokeColor, theme.shoulderTextColor)
            }
            TouchLayout.ID_L3, TouchLayout.ID_R3 ->
                Triple(Color.argb(220, 32, 36, 46), Color.argb(255, 70, 80, 100), Color.WHITE)
            TouchLayout.ID_MODE -> when {
                isGenesis ->
                    Triple(Color.argb(220, 30, 30, 35), Color.argb(255, 100, 100, 110), Color.WHITE)
                isPsp ->
                    Triple(Color.argb(220, 28, 32, 42), Color.argb(255, 68, 136, 255), Color.WHITE)
                else ->
                    Triple(theme.systemFillColor, theme.systemStrokeColor, theme.systemTextColor)
            }
            TouchLayout.ID_START -> when {
                isGenesis ->
                    Triple(Color.argb(220, 30, 30, 35), Color.argb(255, 220, 45, 45), Color.WHITE)
                isPce ->
                    Triple(Color.argb(220, 35, 35, 40), Color.argb(255, 235, 100, 20), Color.WHITE)
                isNes ->
                    Triple(Color.argb(220, 35, 35, 40), Color.argb(255, 90, 90, 100), Color.argb(255, 220, 30, 30))
                isArcade ->
                    Triple(Color.argb(220, 20, 45, 30), Color.argb(255, 50, 220, 120), Color.argb(255, 50, 220, 120))
                isNds || isPsp ->
                    Triple(Color.argb(220, 35, 40, 50), Color.argb(255, 80, 90, 110), Color.WHITE)
                else ->
                    Triple(theme.systemFillColor, theme.systemStrokeColor, theme.systemTextColor)
            }
            TouchLayout.ID_SELECT -> when {
                isNes ->
                    Triple(Color.argb(220, 35, 35, 40), Color.argb(255, 90, 90, 100), Color.argb(255, 220, 30, 30))
                isArcade ->
                    Triple(Color.argb(220, 45, 38, 20), Color.argb(255, 255, 200, 40), Color.argb(255, 255, 200, 40))
                isNds || isPsp ->
                    Triple(Color.argb(220, 35, 40, 50), Color.argb(255, 80, 90, 110), Color.WHITE)
                else ->
                    Triple(theme.systemFillColor, theme.systemStrokeColor, theme.systemTextColor)
            }
            else -> Triple(theme.dpadFillColor, theme.dpadStrokeColor, theme.dpadTextColor)
        }

        return if (isPressed) {
            Triple(theme.accentColor, Color.WHITE, Color.WHITE)
        } else {
            Triple(fill, stroke, text)
        }
    }

    private fun applyAlpha(color: Int, alphaFactor: Float): Int {
        val a = ((Color.alpha(color) / 255f) * alphaFactor * 255f).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
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

        // 2. Draw Virtual Controls (incorporating auto-dimming opacity & theme)
        val currentEffectiveOpacity = getEffectiveOpacity()
        val opacityFactor = if (isEditMode) 0.95f else currentEffectiveOpacity

        // Draw Virtual Analog Joystick when enabled
        if (dpadType != DpadType.CLASSIC_CROSS) {
            val dpadCluster = layout.getCluster(TouchLayout.CLUSTER_DPAD)
            val scale = clusterScales[TouchLayout.CLUSTER_DPAD] ?: 1.0f
            val baseX = if (joystick.isActive || dpadType == DpadType.FIXED_JOYSTICK) joystick.baseCenterX else (dpadCluster?.anchorX ?: 0f)
            val baseY = if (joystick.isActive || dpadType == DpadType.FIXED_JOYSTICK) joystick.baseCenterY else (dpadCluster?.anchorY ?: 0f)

            val shouldDrawJoystick = when {
                isEditMode -> true
                dpadType == DpadType.FIXED_JOYSTICK -> true
                dpadType == DpadType.FLOATING_JOYSTICK -> joystick.isActive || dynamicDpadActive
                else -> false
            }

            if (shouldDrawJoystick && baseX > 0f && baseY > 0f) {
                drawVirtualJoystick(canvas, baseX, baseY, scale, opacityFactor)
            }
        }

        for (control in layout.controls) {
            // If joystick mode is enabled, skip drawing the classic DPAD shape
            if (dpadType != DpadType.CLASSIC_CROSS && control.id == TouchLayout.ID_DPAD) {
                continue
            }

            // If floating classic D-Pad is enabled and inactive, don't draw D-Pad
            if (floatingDpadEnabled && !isEditMode && !dynamicDpadActive && control.id == TouchLayout.ID_DPAD) {
                continue
            }

            val isPressed = !isEditMode && isControlPressed(control)
            val (baseFill, baseStroke, baseTextColor) = getControlColors(control, isPressed)

            basePaint.color = applyAlpha(baseFill, if (isPressed) 1.0f else opacityFactor)
            strokePaint.color = applyAlpha(baseStroke, if (isPressed) 1.0f else opacityFactor)
            textPaint.color = applyAlpha(baseTextColor, if (isPressed) 1.0f else opacityFactor)
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

    /**
     * Renders the glowing Virtual Analog Joystick outer base and inner floating thumb knob.
     * Material 3 Pitch Black & Indigo Theme:
     * - Outer Base Ring: radius 54dp, Glowing Indigo border (#6366F1), translucent pitch-black fill (rgba(10, 10, 16, 0.65)).
     * - Inner Floating Knob: radius 24dp, gradient Indigo fill (#4F46E5 to #818CF8), white center reticle.
     */
    private fun drawVirtualJoystick(
        canvas: Canvas,
        baseX: Float,
        baseY: Float,
        scale: Float,
        opacityFactor: Float
    ) {
        val baseR = VirtualJoystick.DEFAULT_BASE_RADIUS * scale
        val knobR = VirtualJoystick.DEFAULT_KNOB_RADIUS * scale
        val deadzoneR = VirtualJoystick.DEFAULT_DEADZONE_RADIUS * scale

        val kx = if (joystick.isActive) joystick.knobX else baseX
        val ky = if (joystick.isActive) joystick.knobY else baseY

        // 1. Outer Base Ring (rgba(10, 10, 16, 0.65))
        basePaint.color = Color.argb((0.65f * 255 * opacityFactor).toInt(), 10, 10, 16)
        canvas.drawCircle(baseX, baseY, baseR, basePaint)

        // Outer Glow Ring (Indigo #6366F1 with 25% alpha, stroke width 6f)
        strokePaint.color = Color.argb((0.25f * 255 * opacityFactor).toInt(), 99, 102, 241)
        strokePaint.strokeWidth = 6f * scale
        canvas.drawCircle(baseX, baseY, baseR + 2f * scale, strokePaint)

        // Outer Glowing Border Ring (Indigo #6366F1, stroke width 3f)
        strokePaint.color = Color.argb((0.95f * 255 * opacityFactor).toInt(), 99, 102, 241)
        strokePaint.strokeWidth = 3f * scale
        canvas.drawCircle(baseX, baseY, baseR, strokePaint)

        // Deadzone Guide Ring (Subtle Indigo #6366F1 with 20% alpha)
        strokePaint.color = Color.argb((0.20f * 255 * opacityFactor).toInt(), 99, 102, 241)
        strokePaint.strokeWidth = 1.5f * scale
        canvas.drawCircle(baseX, baseY, deadzoneR, strokePaint)

        // Cardinal Direction Reticles / Notches on Base Perimeter (0°, 90°, 180°, 270°)
        val tickLength = 7f * scale
        val tickInner = baseR - tickLength
        val tickOuter = baseR - 1f
        strokePaint.color = Color.argb((0.70f * 255 * opacityFactor).toInt(), 129, 140, 248)
        strokePaint.strokeWidth = 2.5f * scale

        // Right
        canvas.drawLine(baseX + tickInner, baseY, baseX + tickOuter, baseY, strokePaint)
        // Down
        canvas.drawLine(baseX, baseY + tickInner, baseX, baseY + tickOuter, strokePaint)
        // Left
        canvas.drawLine(baseX - tickInner, baseY, baseX - tickOuter, baseY, strokePaint)
        // Up
        canvas.drawLine(baseX, baseY - tickInner, baseX, baseY - tickOuter, strokePaint)

        // Diagonal Subtle Sector Ticks if RPG Grid mode (30°, 60°, 120°, 150°, 210°, 240°, 300°, 330°)
        if (joystickSnapMode == JoystickSnapMode.RPG_GRID_4WAY) {
            strokePaint.color = Color.argb((0.35f * 255 * opacityFactor).toInt(), 129, 140, 248)
            strokePaint.strokeWidth = 1.5f * scale
            val diagAngles = floatArrayOf(30f, 60f, 120f, 150f, 210f, 240f, 300f, 330f)
            for (ang in diagAngles) {
                val rad = Math.toRadians(ang.toDouble())
                val cosA = Math.cos(rad).toFloat()
                val sinA = Math.sin(rad).toFloat()
                val x1 = baseX + (baseR - 4f * scale) * cosA
                val y1 = baseY + (baseR - 4f * scale) * sinA
                val x2 = baseX + baseR * cosA
                val y2 = baseY + baseR * sinA
                canvas.drawLine(x1, y1, x2, y2, strokePaint)
            }
        }

        // 2. Inner Floating Thumb Knob (Gradient Indigo #4F46E5 to #818CF8)
        // Outer Knob Base Ring (#4F46E5)
        basePaint.color = Color.argb((0.92f * 255 * opacityFactor).toInt(), 79, 70, 229)
        canvas.drawCircle(kx, ky, knobR, basePaint)

        // Inner Knob Dome (#6366F1)
        basePaint.color = Color.argb((0.95f * 255 * opacityFactor).toInt(), 99, 102, 241)
        canvas.drawCircle(kx, ky, knobR * 0.75f, basePaint)

        // Knob Top Bevel Highlight (#818CF8)
        basePaint.color = Color.argb((0.90f * 255 * opacityFactor).toInt(), 129, 140, 248)
        canvas.drawCircle(kx, ky, knobR * 0.45f, basePaint)

        // Knob Outer Stroke (#A5B4FC)
        strokePaint.color = Color.argb((0.85f * 255 * opacityFactor).toInt(), 165, 180, 252)
        strokePaint.strokeWidth = 2f * scale
        canvas.drawCircle(kx, ky, knobR, strokePaint)

        // White Center Reticle (#FFFFFF)
        basePaint.color = Color.argb((0.95f * 255 * opacityFactor).toInt(), 255, 255, 255)
        canvas.drawCircle(kx, ky, 3.5f * scale, basePaint)
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
            val currentScale = clusterScales[cluster.id] ?: 1.0f
            val baseLabel = if (cluster.id == TouchLayout.CLUSTER_DPAD) {
                when (dpadType) {
                    DpadType.CLASSIC_CROSS -> "D-PAD"
                    DpadType.FIXED_JOYSTICK -> "JOYSTICK"
                    DpadType.FLOATING_JOYSTICK -> "JOYSTICK (Floating)"
                }
            } else {
                cluster.label
            }
            val labelWithScale = "${baseLabel} (${String.format("%.1f", currentScale)}x)"
            canvas.drawText(labelWithScale, cluster.anchorX, tempRect.top - 8f, textPaint)
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
        canvas.drawText("Controls Editor", topBannerRect.centerX(), topBannerRect.centerY() + 7f, textPaint)

        // "Save & Exit" Button (Right)
        saveBtnRect.set(topBannerRect.right - btnW - 8f, btnY, topBannerRect.right - 8f, btnY + btnH)
        editorFillPaint.color = Color.argb(255, 0, 160, 240)
        canvas.drawRoundRect(saveBtnRect, 10f, 10f, editorFillPaint)
        textPaint.textSize = 18f
        textPaint.color = Color.WHITE
        canvas.drawText("✓ Save", saveBtnRect.centerX(), saveBtnRect.centerY() + 6f, textPaint)

        // Bottom Cluster Scale Adjustment Toolbar (when cluster selected)
        val selId = selectedClusterId
        if (selId != null) {
            val scaleToolbarW = Math.min(viewW * 0.80f, 360f)
            val scaleToolbarH = 44f
            val scaleToolbarLeft = (viewW - scaleToolbarW) * 0.5f
            val scaleToolbarTop = viewH - scaleToolbarH - 24f
            scaleControlRect.set(scaleToolbarLeft, scaleToolbarTop, scaleToolbarLeft + scaleToolbarW, scaleToolbarTop + scaleToolbarH)

            editorFillPaint.color = Color.argb(240, 22, 28, 42)
            canvas.drawRoundRect(scaleControlRect, 12f, 12f, editorFillPaint)
            editorBoxPaint.color = Color.argb(200, 0, 229, 255)
            canvas.drawRoundRect(scaleControlRect, 12f, 12f, editorBoxPaint)

            val ctrlBtnW = 44f
            val ctrlBtnH = 34f
            val ctrlBtnY = scaleToolbarTop + 5f

            // Scale - Button
            scaleDownBtnRect.set(scaleToolbarLeft + 8f, ctrlBtnY, scaleToolbarLeft + 8f + ctrlBtnW, ctrlBtnY + ctrlBtnH)
            editorFillPaint.color = Color.argb(255, 45, 55, 75)
            canvas.drawRoundRect(scaleDownBtnRect, 8f, 8f, editorFillPaint)
            textPaint.textSize = 20f
            textPaint.color = Color.WHITE
            canvas.drawText("➖", scaleDownBtnRect.centerX(), scaleDownBtnRect.centerY() + 7f, textPaint)

            // Current Scale Label
            val curScale = clusterScales[selId] ?: 1.0f
            textPaint.textSize = 16f
            textPaint.color = Color.argb(255, 0, 229, 255)
            canvas.drawText("Cluster Scale: ${String.format("%.1f", curScale)}x", scaleControlRect.centerX(), scaleControlRect.centerY() + 6f, textPaint)

            // Scale + Button
            scaleUpBtnRect.set(scaleControlRect.right - ctrlBtnW - 8f, ctrlBtnY, scaleControlRect.right - 8f, ctrlBtnY + ctrlBtnH)
            editorFillPaint.color = Color.argb(255, 45, 55, 75)
            canvas.drawRoundRect(scaleUpBtnRect, 8f, 8f, editorFillPaint)
            textPaint.textSize = 20f
            textPaint.color = Color.WHITE
            canvas.drawText("➕", scaleUpBtnRect.centerX(), scaleUpBtnRect.centerY() + 7f, textPaint)
        }
    }

    private fun isControlPressed(control: VirtualControl): Boolean {
        // Issue #44: primitive pointer-slot scan; hit-test via the flattened
        // cache keeps rendering-time press lookups allocation-free.
        for (slot in 0 until MAX_TRACKED_POINTERS) {
            if (!pointerActive[slot]) continue
            val px = pointerX[slot]
            val py = pointerY[slot]
            if (control.contains(px, py)) {
                return true
            }
        }
        return false
    }
}
