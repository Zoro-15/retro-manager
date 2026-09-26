package com.retropack.runtime.input

import com.retropack.runtime.core.RetroKey
import java.util.Collections

/**
 * Geometric shape for virtual on-screen controls.
 */
enum class ControlShape {
    CIRCLE,
    PILL,
    DPAD
}

/**
 * Represents an individual on-screen virtual touch control element.
 */
data class VirtualControl(
    val id: String,
    val key: RetroKey?,
    val label: String,
    val shape: ControlShape,
    val cx: Float,
    val cy: Float,
    val halfWidth: Float,
    val halfHeight: Float,
    val isTurbo: Boolean = false,
    val customKeyMask: Int = RetroKey.NO_KEYS_MASK
) {
    val radius: Float get() = Math.max(halfWidth, halfHeight)

    /**
     * Determines whether the given coordinate falls within the boundary of this control,
     * applying an optional [slopFactor] (defaults to 1.20f for a 20% invisible touch expansion).
     */
    fun contains(x: Float, y: Float, slopFactor: Float = 1.20f): Boolean {
        val dx = x - cx
        val dy = y - cy
        val sw = halfWidth * slopFactor
        val sh = halfHeight * slopFactor
        return when (shape) {
            ControlShape.CIRCLE -> {
                val nx = dx / sw
                val ny = dy / sh
                (nx * nx + ny * ny) <= 1.0f
            }
            ControlShape.PILL, ControlShape.DPAD -> {
                Math.abs(dx) <= sw && Math.abs(dy) <= sh
            }
        }
    }

    /**
     * Evaluates the active hardware keymask at coordinate (x, y) with 20% touch slop padding.
     *
     * For D-pad, resolves 8-way directional inputs (UP, DOWN, LEFT, RIGHT, and diagonals)
     * with an inner center deadzone.
     */
    fun hitKeyMask(x: Float, y: Float, slopFactor: Float = 1.20f, isTurboPhase: Boolean = true): Int {
        if (!contains(x, y, slopFactor)) return RetroKey.NO_KEYS_MASK

        return if (shape == ControlShape.DPAD) {
            var mask = RetroKey.NO_KEYS_MASK
            val dx = x - cx
            val dy = y - cy
            val dead = halfWidth * 0.20f
            if (dx < -dead) mask = mask or RetroKey.KEY_LEFT
            if (dx > dead) mask = mask or RetroKey.KEY_RIGHT
            if (dy < -dead) mask = mask or RetroKey.KEY_UP
            if (dy > dead) mask = mask or RetroKey.KEY_DOWN
            mask
        } else if (customKeyMask != RetroKey.NO_KEYS_MASK) {
            customKeyMask
        } else if (isTurbo) {
            if (isTurboPhase) key?.mask ?: RetroKey.NO_KEYS_MASK else RetroKey.NO_KEYS_MASK
        } else {
            key?.mask ?: RetroKey.NO_KEYS_MASK
        }
    }
}

/**
 * Immutable layout geometry and hit-testing engine for virtual touch controls.
 *
 * Designed with zero dependency on Android framework classes so it can be fully
 * unit-tested in JVM environments.
 */
class TouchLayout(
    val width: Float,
    val height: Float,
    val controls: List<VirtualControl>,
    val opacity: Float = DEFAULT_OPACITY,
    val turboEnabled: Boolean = false,
    val comboEnabled: Boolean = false
) {
    companion object {
        const val DEFAULT_OPACITY = 0.6f
        const val DEFAULT_HIT_SLOP = 1.20f // 20% invisible touch slop expansion

        const val ID_DPAD = "dpad"
        const val ID_A = "btn_a"
        const val ID_B = "btn_b"
        const val ID_TURBO_A = "btn_turbo_a"
        const val ID_TURBO_B = "btn_turbo_b"
        const val ID_COMBO_AB = "btn_combo_ab"
        const val ID_L = "btn_l"
        const val ID_R = "btn_r"
        const val ID_START = "btn_start"
        const val ID_SELECT = "btn_select"

        // Clustered control identifiers for PPSSPP/Lemuroid-style layout positioning
        const val CLUSTER_DPAD = "dpad"
        const val CLUSTER_ACTION = "action"
        const val CLUSTER_SHOULDER_L = "shoulder_l"
        const val CLUSTER_SHOULDER_R = "shoulder_r"
        const val CLUSTER_SYSTEM = "system"
        const val CLUSTER_TURBO = "turbo"
        const val CLUSTER_COMBO = "combo"

        /**
         * Creates a [TouchLayout] automatically picking portrait or landscape geometry.
         */
        fun create(
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            return if (width > height) {
                landscape(width, height, opacity, turboEnabled, comboEnabled)
            } else {
                portrait(width, height, opacity, turboEnabled, comboEnabled)
            }
        }

        /**
         * Standard ergonomic portrait touch layout.
         * Game viewport occupies the top half; controls occupy the lower thumb zone.
         */
        fun portrait(
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            val unit = Math.min(width, height)
            val margin = unit * 0.03f

            // GBA aspect ratio is 240x160 (3:2 = 1.5)
            val gameWidth = width
            var gameHeight = gameWidth * 160f / 240f
            if (gameHeight > height * 0.5f) {
                gameHeight = height * 0.5f
            }
            val gameBottom = unit * 0.08f + gameHeight

            val controlsTop = gameBottom + margin
            val controlsHeight = height - controlsTop
            val mainY = controlsTop + controlsHeight * 0.48f

            val controls = mutableListOf<VirtualControl>()

            // Shoulder Triggers (L / R)
            val shoulderHalfW = unit * 0.16f
            val shoulderHalfH = unit * 0.06f
            val shoulderY = controlsTop + controlsHeight * 0.16f
            controls.add(
                VirtualControl(ID_L, RetroKey.L, "L", ControlShape.PILL,
                    width * 0.20f, shoulderY, shoulderHalfW, shoulderHalfH)
            )
            controls.add(
                VirtualControl(ID_R, RetroKey.R, "R", ControlShape.PILL,
                    width * 0.80f, shoulderY, shoulderHalfW, shoulderHalfH)
            )

            // D-Pad
            val dpadHalf = unit * 0.22f
            controls.add(
                VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD,
                    width * 0.25f, mainY, dpadHalf, dpadHalf)
            )

            // Action Buttons A and B (angled for ergonomic thumb reach and thumb rolling)
            val btnRadius = unit * 0.11f
            controls.add(
                VirtualControl(ID_B, RetroKey.B, "B", ControlShape.CIRCLE,
                    width * 0.65f, mainY + unit * 0.05f, btnRadius, btnRadius)
            )
            controls.add(
                VirtualControl(ID_A, RetroKey.A, "A", ControlShape.CIRCLE,
                    width * 0.87f, mainY - unit * 0.03f, btnRadius, btnRadius)
            )

            // Turbo A & Turbo B (Superpowers)
            if (turboEnabled) {
                val turboRadius = btnRadius * 0.72f
                controls.add(
                    VirtualControl(ID_TURBO_B, RetroKey.B, "TB", ControlShape.CIRCLE,
                        width * 0.65f, mainY + unit * 0.05f - btnRadius * 1.55f, turboRadius, turboRadius, isTurbo = true)
                )
                controls.add(
                    VirtualControl(ID_TURBO_A, RetroKey.A, "TA", ControlShape.CIRCLE,
                        width * 0.87f, mainY - unit * 0.03f - btnRadius * 1.55f, turboRadius, turboRadius, isTurbo = true)
                )
            }

            // A+B Combo Macro Pill (Superpowers)
            if (comboEnabled) {
                controls.add(
                    VirtualControl(ID_COMBO_AB, null, "A+B", ControlShape.PILL,
                        width * 0.76f, mainY + unit * 0.13f, btnRadius * 0.95f, btnRadius * 0.50f,
                        customKeyMask = RetroKey.KEY_A or RetroKey.KEY_B)
                )
            }

            // System Buttons (Select / Start)
            val smallHalfW = unit * 0.13f
            val smallHalfH = unit * 0.045f
            val smallY = controlsTop + controlsHeight * 0.84f
            controls.add(
                VirtualControl(ID_SELECT, RetroKey.SELECT, "SELECT", ControlShape.PILL,
                    width * 0.35f, smallY, smallHalfW, smallHalfH)
            )
            controls.add(
                VirtualControl(ID_START, RetroKey.START, "START", ControlShape.PILL,
                    width * 0.65f, smallY, smallHalfW, smallHalfH)
            )

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled)
        }

        /**
         * Standard ergonomic landscape touch layout.
         * Game viewport is centered; controls are placed in left and right thumb gutters.
         */
        fun landscape(
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            val unit = Math.min(width, height)
            val gameWidth = height * 240f / 160f
            val leftGutter = Math.max((width - gameWidth) / 2f, unit * 0.28f)
            val rightGutterStart = width - leftGutter

            val controls = mutableListOf<VirtualControl>()

            // D-Pad in left gutter
            val dpadHalf = unit * 0.20f
            val dpadX = leftGutter * 0.52f
            val mainY = height * 0.62f
            controls.add(
                VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD,
                    dpadX, mainY, dpadHalf, dpadHalf)
            )

            // Shoulder Triggers (L on top left, R on top right)
            val shoulderHalfW = unit * 0.14f
            val shoulderHalfH = unit * 0.055f
            controls.add(
                VirtualControl(ID_L, RetroKey.L, "L", ControlShape.PILL,
                    dpadX, height * 0.22f, shoulderHalfW, shoulderHalfH)
            )
            val actionCenterX = rightGutterStart + (width - rightGutterStart) * 0.5f
            controls.add(
                VirtualControl(ID_R, RetroKey.R, "R", ControlShape.PILL,
                    actionCenterX, height * 0.22f, shoulderHalfW, shoulderHalfH)
            )

            // Action Buttons A and B in right gutter
            val btnRadius = unit * 0.09f
            val rightWidth = width - rightGutterStart
            val btnBX = rightGutterStart + rightWidth * 0.30f
            val btnBY = mainY + unit * 0.05f
            val btnAX = rightGutterStart + rightWidth * 0.75f
            val btnAY = mainY - unit * 0.03f

            controls.add(
                VirtualControl(ID_B, RetroKey.B, "B", ControlShape.CIRCLE,
                    btnBX, btnBY, btnRadius, btnRadius)
            )
            controls.add(
                VirtualControl(ID_A, RetroKey.A, "A", ControlShape.CIRCLE,
                    btnAX, btnAY, btnRadius, btnRadius)
            )

            // Turbo A & Turbo B (Superpowers)
            if (turboEnabled) {
                val turboRadius = btnRadius * 0.72f
                controls.add(
                    VirtualControl(ID_TURBO_B, RetroKey.B, "TB", ControlShape.CIRCLE,
                        btnBX, btnBY - btnRadius * 1.55f, turboRadius, turboRadius, isTurbo = true)
                )
                controls.add(
                    VirtualControl(ID_TURBO_A, RetroKey.A, "TA", ControlShape.CIRCLE,
                        btnAX, btnAY - btnRadius * 1.55f, turboRadius, turboRadius, isTurbo = true)
                )
            }

            // A+B Combo Macro Pill (Superpowers)
            if (comboEnabled) {
                controls.add(
                    VirtualControl(ID_COMBO_AB, null, "A+B", ControlShape.PILL,
                        rightGutterStart + rightWidth * 0.525f, mainY + unit * 0.13f, btnRadius * 0.95f, btnRadius * 0.50f,
                        customKeyMask = RetroKey.KEY_A or RetroKey.KEY_B)
                )
            }

            // System Buttons (Select / Start)
            val smallHalfW = unit * 0.09f
            val smallHalfH = unit * 0.035f
            val smallY = height * 0.90f
            controls.add(
                VirtualControl(ID_SELECT, RetroKey.SELECT, "SELECT", ControlShape.PILL,
                    rightGutterStart + rightWidth * 0.30f, smallY, smallHalfW, smallHalfH)
            )
            controls.add(
                VirtualControl(ID_START, RetroKey.START, "START", ControlShape.PILL,
                    rightGutterStart + rightWidth * 0.75f, smallY, smallHalfW, smallHalfH)
            )

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled)
        }
    }

    /**
     * Hit-tests a single coordinate point and returns the composite [RetroKey] bitmask,
     * incorporating 20% touch slop padding and thumb-roll assist across adjacent action buttons.
     */
    fun inputAt(x: Float, y: Float, slopFactor: Float = DEFAULT_HIT_SLOP, isTurboPhase: Boolean = true): Int {
        var mask = RetroKey.NO_KEYS_MASK
        for (control in controls) {
            mask = mask or control.hitKeyMask(x, y, slopFactor, isTurboPhase)
        }

        // Thumb Roll Assist: If touch falls into the transition zone between B and A,
        // activate both buttons seamlessly without requiring the user to lift their finger.
        val btnA = controls.firstOrNull { it.id == ID_A }
        val btnB = controls.firstOrNull { it.id == ID_B }
        if (btnA != null && btnB != null) {
            val distA = Math.hypot((x - btnA.cx).toDouble(), (y - btnA.cy).toDouble()).toFloat()
            val distB = Math.hypot((x - btnB.cx).toDouble(), (y - btnB.cy).toDouble()).toFloat()
            val btnDistance = Math.hypot((btnA.cx - btnB.cx).toDouble(), (btnA.cy - btnB.cy).toDouble()).toFloat()
            // If touch is along the segment between A and B
            if (distA + distB <= btnDistance * 1.25f && distA <= btnA.halfWidth * 1.5f && distB <= btnB.halfWidth * 1.5f) {
                mask = mask or RetroKey.KEY_A or RetroKey.KEY_B
            }
        }

        return mask
    }

    /**
     * Resolves multiple concurrent touch pointer coordinates into a single composite [RetroKey] bitmask.
     */
    fun resolvePointers(
        pointers: Iterable<Pair<Float, Float>>,
        slopFactor: Float = DEFAULT_HIT_SLOP,
        isTurboPhase: Boolean = true
    ): Int {
        var compositeMask = RetroKey.NO_KEYS_MASK
        for (pointer in pointers) {
            compositeMask = compositeMask or inputAt(pointer.first, pointer.second, slopFactor, isTurboPhase)
        }
        return compositeMask
    }

    /**
     * Creates a copy of this layout with an adjusted opacity.
     */
    fun withOpacity(newOpacity: Float): TouchLayout {
        return TouchLayout(width, height, controls, newOpacity.coerceIn(0.0f, 1.0f), turboEnabled, comboEnabled)
    }

    /**
     * Creates a copy of this layout toggling Turbo and Combo Macro controls.
     */
    fun withSuperpowers(newTurboEnabled: Boolean, newComboEnabled: Boolean): TouchLayout {
        return create(width, height, opacity, newTurboEnabled, newComboEnabled)
    }

    /**
     * Creates a copy of this layout with custom position and scaling for a specific control.
     */
    fun withControlPosition(id: String, newCx: Float, newCy: Float, scale: Float = 1.0f): TouchLayout {
        val updated = controls.map { c ->
            if (c.id == id) {
                VirtualControl(
                    c.id, c.key, c.label, c.shape,
                    newCx, newCy,
                    c.halfWidth * scale,
                    c.halfHeight * scale,
                    c.isTurbo,
                    c.customKeyMask
                )
            } else {
                c
            }
        }
        return TouchLayout(width, height, Collections.unmodifiableList(updated), opacity, turboEnabled, comboEnabled)
    }

    /**
     * Evaluates and returns all control clusters in this layout.
     */
    fun getClusters(): List<ClusterInfo> {
        val clusterSpecs = listOf(
            Triple(CLUSTER_DPAD, "D-PAD", listOf(ID_DPAD)),
            Triple(CLUSTER_ACTION, "ACTION", listOf(ID_B, ID_A)),
            Triple(CLUSTER_SHOULDER_L, "L", listOf(ID_L)),
            Triple(CLUSTER_SHOULDER_R, "R", listOf(ID_R)),
            Triple(CLUSTER_SYSTEM, "SYSTEM", listOf(ID_SELECT, ID_START)),
            Triple(CLUSTER_TURBO, "TURBO", listOf(ID_TURBO_B, ID_TURBO_A)),
            Triple(CLUSTER_COMBO, "COMBO", listOf(ID_COMBO_AB))
        )

        val result = mutableListOf<ClusterInfo>()
        for ((clusterId, label, ids) in clusterSpecs) {
            val matchingControls = controls.filter { it.id in ids }
            if (matchingControls.isEmpty()) continue

            val avgX = matchingControls.map { it.cx }.average().toFloat()
            val avgY = matchingControls.map { it.cy }.average().toFloat()

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            for (c in matchingControls) {
                minX = Math.min(minX, c.cx - c.halfWidth)
                minY = Math.min(minY, c.cy - c.halfHeight)
                maxX = Math.max(maxX, c.cx + c.halfWidth)
                maxY = Math.max(maxY, c.cy + c.halfHeight)
            }

            result.add(
                ClusterInfo(
                    id = clusterId,
                    label = label,
                    controlIds = matchingControls.map { it.id },
                    anchorX = avgX,
                    anchorY = avgY,
                    left = minX,
                    top = minY,
                    right = maxX,
                    bottom = maxY
                )
            )
        }
        return result
    }

    /**
     * Finds a cluster by its cluster identifier.
     */
    fun getCluster(clusterId: String): ClusterInfo? {
        return getClusters().firstOrNull { it.id == clusterId }
    }

    /**
     * Finds a cluster containing the given coordinates, with an optional touch slop expansion.
     */
    fun findClusterAt(x: Float, y: Float, touchSlop: Float = 16f): ClusterInfo? {
        for (cluster in getClusters()) {
            if (x >= cluster.left - touchSlop && x <= cluster.right + touchSlop &&
                y >= cluster.top - touchSlop && y <= cluster.bottom + touchSlop) {
                return cluster
            }
        }
        return null
    }

    /**
     * Moves all controls in [clusterId] so that the cluster's anchor aligns with ([newAnchorX], [newAnchorY]),
     * preserving internal relative spacing between controls.
     */
    fun withClusterPosition(clusterId: String, newAnchorX: Float, newAnchorY: Float): TouchLayout {
        val cluster = getCluster(clusterId) ?: return this
        val dx = newAnchorX - cluster.anchorX
        val dy = newAnchorY - cluster.anchorY
        val targetIds = cluster.controlIds.toSet()

        val updated = controls.map { c ->
            if (c.id in targetIds) {
                VirtualControl(
                    id = c.id,
                    key = c.key,
                    label = c.label,
                    shape = c.shape,
                    cx = c.cx + dx,
                    cy = c.cy + dy,
                    halfWidth = c.halfWidth,
                    halfHeight = c.halfHeight,
                    isTurbo = c.isTurbo,
                    customKeyMask = c.customKeyMask
                )
            } else {
                c
            }
        }
        return TouchLayout(width, height, Collections.unmodifiableList(updated), opacity, turboEnabled, comboEnabled)
    }

    /**
     * Scales all controls in [clusterId] by [scale] (clamped to 0.5x..2.0x) around the cluster's anchor center,
     * maintaining proportional relative spacing and geometry.
     */
    fun withClusterScale(clusterId: String, scale: Float): TouchLayout {
        val cluster = getCluster(clusterId) ?: return this
        val clampedScale = scale.coerceIn(0.5f, 2.0f)
        val targetIds = cluster.controlIds.toSet()

        val updated = controls.map { c ->
            if (c.id in targetIds) {
                val relX = c.cx - cluster.anchorX
                val relY = c.cy - cluster.anchorY
                VirtualControl(
                    id = c.id,
                    key = c.key,
                    label = c.label,
                    shape = c.shape,
                    cx = cluster.anchorX + relX * clampedScale,
                    cy = cluster.anchorY + relY * clampedScale,
                    halfWidth = c.halfWidth * clampedScale,
                    halfHeight = c.halfHeight * clampedScale,
                    isTurbo = c.isTurbo,
                    customKeyMask = c.customKeyMask
                )
            } else {
                c
            }
        }
        return TouchLayout(width, height, Collections.unmodifiableList(updated), opacity, turboEnabled, comboEnabled)
    }

    /**
     * Applies scaling factors for multiple clusters in one operation.
     */
    fun applyClusterScales(scales: Map<String, Float>): TouchLayout {
        if (scales.isEmpty()) return this
        var currentLayout = this
        for ((clusterId, scale) in scales) {
            currentLayout = currentLayout.withClusterScale(clusterId, scale)
        }
        return currentLayout
    }

    /**
     * Computes normalized screen ratio coordinates (0.0 to 1.0) for each control cluster.
     */
    fun getNormalizedClusterPositions(): Map<String, Pair<Float, Float>> {
        if (width <= 0f || height <= 0f) return emptyMap()
        return getClusters().associate { cluster ->
            cluster.id to Pair(cluster.anchorX / width, cluster.anchorY / height)
        }
    }

    /**
     * Applies normalized screen ratio coordinates (0.0 to 1.0) to all matching control clusters.
     */
    fun applyNormalizedClusterPositions(positions: Map<String, Pair<Float, Float>>): TouchLayout {
        if (width <= 0f || height <= 0f || positions.isEmpty()) return this
        var currentLayout = this
        for ((clusterId, normPos) in positions) {
            val targetX = (normPos.first * width).coerceIn(0f, width)
            val targetY = (normPos.second * height).coerceIn(0f, height)
            currentLayout = currentLayout.withClusterPosition(clusterId, targetX, targetY)
        }
        return currentLayout
    }
}

/**
 * Geometric cluster descriptor grouping related virtual controls.
 */
data class ClusterInfo(
    val id: String,
    val label: String,
    val controlIds: List<String>,
    val anchorX: Float,
    val anchorY: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom
}

