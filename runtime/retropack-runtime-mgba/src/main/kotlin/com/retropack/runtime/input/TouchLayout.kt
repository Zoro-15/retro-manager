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
    val halfHeight: Float
) {
    /**
     * Determines whether the given coordinate falls within the boundary of this control.
     */
    fun contains(x: Float, y: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        return when (shape) {
            ControlShape.CIRCLE -> {
                val nx = dx / halfWidth
                val ny = dy / halfHeight
                (nx * nx + ny * ny) <= 1.0f
            }
            ControlShape.PILL, ControlShape.DPAD -> {
                Math.abs(dx) <= halfWidth && Math.abs(dy) <= halfHeight
            }
        }
    }

    /**
     * Evaluates the active hardware keymask at coordinate (x, y).
     *
     * For D-pad, resolves 8-way directional inputs (UP, DOWN, LEFT, RIGHT, and diagonals)
     * with an inner center deadzone.
     */
    fun hitKeyMask(x: Float, y: Float): Int {
        if (!contains(x, y)) return RetroKey.NO_KEYS_MASK

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
    val opacity: Float = DEFAULT_OPACITY
) {
    companion object {
        const val DEFAULT_OPACITY = 0.6f
        const val ID_DPAD = "dpad"
        const val ID_A = "btn_a"
        const val ID_B = "btn_b"
        const val ID_L = "btn_l"
        const val ID_R = "btn_r"
        const val ID_START = "btn_start"
        const val ID_SELECT = "btn_select"

        /**
         * Creates a [TouchLayout] automatically picking portrait or landscape geometry.
         */
        fun create(width: Float, height: Float, opacity: Float = DEFAULT_OPACITY): TouchLayout {
            return if (width > height) {
                landscape(width, height, opacity)
            } else {
                portrait(width, height, opacity)
            }
        }

        /**
         * Standard ergonomic portrait touch layout.
         * Game viewport occupies the top half; controls occupy the lower thumb zone.
         */
        fun portrait(width: Float, height: Float, opacity: Float = DEFAULT_OPACITY): TouchLayout {
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

            // Action Buttons A and B (angled for ergonomic thumb reach)
            val btnRadius = unit * 0.11f
            controls.add(
                VirtualControl(ID_B, RetroKey.B, "B", ControlShape.CIRCLE,
                    width * 0.65f, mainY + unit * 0.05f, btnRadius, btnRadius)
            )
            controls.add(
                VirtualControl(ID_A, RetroKey.A, "A", ControlShape.CIRCLE,
                    width * 0.87f, mainY - unit * 0.03f, btnRadius, btnRadius)
            )

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

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity)
        }

        /**
         * Standard ergonomic landscape touch layout.
         * Game viewport is centered; controls are placed in left and right thumb gutters.
         */
        fun landscape(width: Float, height: Float, opacity: Float = DEFAULT_OPACITY): TouchLayout {
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
            controls.add(
                VirtualControl(ID_B, RetroKey.B, "B", ControlShape.CIRCLE,
                    rightGutterStart + rightWidth * 0.30f, mainY + unit * 0.05f, btnRadius, btnRadius)
            )
            controls.add(
                VirtualControl(ID_A, RetroKey.A, "A", ControlShape.CIRCLE,
                    rightGutterStart + rightWidth * 0.75f, mainY - unit * 0.03f, btnRadius, btnRadius)
            )

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

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity)
        }
    }

    /**
     * Hit-tests a single coordinate point and returns the composite [RetroKey] bitmask.
     */
    fun inputAt(x: Float, y: Float): Int {
        var mask = RetroKey.NO_KEYS_MASK
        for (control in controls) {
            mask = mask or control.hitKeyMask(x, y)
        }
        return mask
    }

    /**
     * Resolves multiple concurrent touch pointer coordinates into a single composite [RetroKey] bitmask.
     */
    fun resolvePointers(pointers: Iterable<Pair<Float, Float>>): Int {
        var compositeMask = RetroKey.NO_KEYS_MASK
        for (pointer in pointers) {
            compositeMask = compositeMask or inputAt(pointer.first, pointer.second)
        }
        return compositeMask
    }

    /**
     * Creates a copy of this layout with an adjusted opacity.
     */
    fun withOpacity(newOpacity: Float): TouchLayout {
        return TouchLayout(width, height, controls, newOpacity.coerceIn(0.0f, 1.0f))
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
                    c.halfHeight * scale
                )
            } else {
                c
            }
        }
        return TouchLayout(width, height, Collections.unmodifiableList(updated), opacity)
    }
}
