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
 * Supports system-specific layout presets for SNES, Sega Genesis, NES, PS1, N64, NDS, and GBA/GBC/GB.
 */
class TouchLayout(
    val width: Float,
    val height: Float,
    val controls: List<VirtualControl>,
    val opacity: Float = DEFAULT_OPACITY,
    val turboEnabled: Boolean = false,
    val comboEnabled: Boolean = false,
    val platform: String = "gba"
) {
    companion object {
        const val DEFAULT_OPACITY = 0.6f
        const val DEFAULT_HIT_SLOP = 1.20f // 20% invisible touch slop expansion

        const val ID_DPAD = "dpad"
        const val ID_A = "btn_a"
        const val ID_B = "btn_b"
        const val ID_X = "btn_x"
        const val ID_Y = "btn_y"
        const val ID_C = "btn_c"
        const val ID_Z = "btn_z"
        const val ID_TURBO_A = "btn_turbo_a"
        const val ID_TURBO_B = "btn_turbo_b"
        const val ID_COMBO_AB = "btn_combo_ab"
        const val ID_L = "btn_l"
        const val ID_R = "btn_r"
        const val ID_L2 = "btn_l2"
        const val ID_R2 = "btn_r2"
        const val ID_START = "btn_start"
        const val ID_SELECT = "btn_select"
        const val ID_MODE = "btn_mode"

        // N64 C-Buttons
        const val ID_C_UP = "btn_c_up"
        const val ID_C_DOWN = "btn_c_down"
        const val ID_C_LEFT = "btn_c_left"
        const val ID_C_RIGHT = "btn_c_right"

        // Clustered control identifiers for layout positioning
        const val CLUSTER_DPAD = "dpad"
        const val CLUSTER_ACTION = "action"
        const val CLUSTER_SHOULDER_L = "shoulder_l"
        const val CLUSTER_SHOULDER_R = "shoulder_r"
        const val CLUSTER_SYSTEM = "system"
        const val CLUSTER_TURBO = "turbo"
        const val CLUSTER_COMBO = "combo"
        const val CLUSTER_C_BUTTONS = "c_buttons"

        /**
         * Dispatches to the appropriate platform-specific touch layout.
         */
        fun createForPlatform(
            platform: String,
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            return when (platform.lowercase()) {
                "snes" -> snes(width, height, opacity, turboEnabled, comboEnabled)
                "genesis", "megadrive", "sms", "gg" -> genesis(width, height, opacity, turboEnabled, comboEnabled)
                "nes" -> nes(width, height, opacity, turboEnabled, comboEnabled)
                "pce", "tg16", "sgx" -> pce(width, height, opacity, turboEnabled, comboEnabled)
                "arcade", "fbneo", "neogeo", "cps1", "cps2", "cps3" -> arcade(width, height, opacity, turboEnabled, comboEnabled)
                "psx", "ps1" -> ps1(width, height, opacity, turboEnabled, comboEnabled)
                "n64" -> n64(width, height, opacity, turboEnabled, comboEnabled)
                "psp" -> psp(width, height, opacity, turboEnabled, comboEnabled)
                "nds" -> nds(width, height, opacity, turboEnabled, comboEnabled)
                else -> create(width, height, opacity, turboEnabled, comboEnabled)
            }
        }

        /**
         * Creates a standard GBA [TouchLayout] automatically picking portrait or landscape geometry.
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
         * Standard ergonomic GBA portrait layout.
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

            val shoulderHalfW = unit * 0.16f
            val shoulderHalfH = unit * 0.06f
            val shoulderY = controlsTop + controlsHeight * 0.16f
            controls.add(VirtualControl(ID_L, RetroKey.L, "L", ControlShape.PILL, width * 0.20f, shoulderY, shoulderHalfW, shoulderHalfH))
            controls.add(VirtualControl(ID_R, RetroKey.R, "R", ControlShape.PILL, width * 0.80f, shoulderY, shoulderHalfW, shoulderHalfH))

            val dpadHalf = unit * 0.22f
            controls.add(VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD, width * 0.25f, mainY, dpadHalf, dpadHalf))

            val btnRadius = unit * 0.11f
            controls.add(VirtualControl(ID_B, RetroKey.B, "B", ControlShape.CIRCLE, width * 0.65f, mainY + unit * 0.05f, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_A, RetroKey.A, "A", ControlShape.CIRCLE, width * 0.87f, mainY - unit * 0.03f, btnRadius, btnRadius))

            if (turboEnabled) {
                val turboRadius = btnRadius * 0.72f
                controls.add(VirtualControl(ID_TURBO_B, RetroKey.B, "TB", ControlShape.CIRCLE, width * 0.65f, mainY + unit * 0.05f - btnRadius * 1.55f, turboRadius, turboRadius, isTurbo = true))
                controls.add(VirtualControl(ID_TURBO_A, RetroKey.A, "TA", ControlShape.CIRCLE, width * 0.87f, mainY - unit * 0.03f - btnRadius * 1.55f, turboRadius, turboRadius, isTurbo = true))
            }

            if (comboEnabled) {
                controls.add(VirtualControl(ID_COMBO_AB, null, "A+B", ControlShape.PILL, width * 0.76f, mainY + unit * 0.13f, btnRadius * 0.95f, btnRadius * 0.50f, customKeyMask = RetroKey.KEY_A or RetroKey.KEY_B))
            }

            val smallHalfW = unit * 0.13f
            val smallHalfH = unit * 0.045f
            val smallY = controlsTop + controlsHeight * 0.84f
            controls.add(VirtualControl(ID_SELECT, RetroKey.SELECT, "SELECT", ControlShape.PILL, width * 0.35f, smallY, smallHalfW, smallHalfH))
            controls.add(VirtualControl(ID_START, RetroKey.START, "START", ControlShape.PILL, width * 0.65f, smallY, smallHalfW, smallHalfH))

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled, "gba")
        }

        /**
         * Standard ergonomic GBA landscape layout.
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

            val dpadHalf = unit * 0.20f
            val dpadX = leftGutter * 0.52f
            val mainY = height * 0.62f
            controls.add(VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD, dpadX, mainY, dpadHalf, dpadHalf))

            val shoulderHalfW = unit * 0.14f
            val shoulderHalfH = unit * 0.055f
            controls.add(VirtualControl(ID_L, RetroKey.L, "L", ControlShape.PILL, dpadX, height * 0.22f, shoulderHalfW, shoulderHalfH))
            val actionCenterX = rightGutterStart + (width - rightGutterStart) * 0.5f
            controls.add(VirtualControl(ID_R, RetroKey.R, "R", ControlShape.PILL, actionCenterX, height * 0.22f, shoulderHalfW, shoulderHalfH))

            val btnRadius = unit * 0.09f
            val rightWidth = width - rightGutterStart
            val btnBX = rightGutterStart + rightWidth * 0.30f
            val btnBY = mainY + unit * 0.05f
            val btnAX = rightGutterStart + rightWidth * 0.75f
            val btnAY = mainY - unit * 0.03f

            controls.add(VirtualControl(ID_B, RetroKey.B, "B", ControlShape.CIRCLE, btnBX, btnBY, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_A, RetroKey.A, "A", ControlShape.CIRCLE, btnAX, btnAY, btnRadius, btnRadius))

            if (turboEnabled) {
                val turboRadius = btnRadius * 0.72f
                controls.add(VirtualControl(ID_TURBO_B, RetroKey.B, "TB", ControlShape.CIRCLE, btnBX, btnBY - btnRadius * 1.55f, turboRadius, turboRadius, isTurbo = true))
                controls.add(VirtualControl(ID_TURBO_A, RetroKey.A, "TA", ControlShape.CIRCLE, btnAX, btnAY - btnRadius * 1.55f, turboRadius, turboRadius, isTurbo = true))
            }

            if (comboEnabled) {
                controls.add(VirtualControl(ID_COMBO_AB, null, "A+B", ControlShape.PILL, rightGutterStart + rightWidth * 0.525f, mainY + unit * 0.13f, btnRadius * 0.95f, btnRadius * 0.50f, customKeyMask = RetroKey.KEY_A or RetroKey.KEY_B))
            }

            val smallHalfW = unit * 0.09f
            val smallHalfH = unit * 0.035f
            val smallY = height * 0.90f
            controls.add(VirtualControl(ID_SELECT, RetroKey.SELECT, "SELECT", ControlShape.PILL, rightGutterStart + rightWidth * 0.30f, smallY, smallHalfW, smallHalfH))
            controls.add(VirtualControl(ID_START, RetroKey.START, "START", ControlShape.PILL, rightGutterStart + rightWidth * 0.75f, smallY, smallHalfW, smallHalfH))

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled, "gba")
        }

        /**
         * Super Nintendo (SNES) Diamond 4-Button layout (A, B, X, Y, L, R, Select, Start).
         */
        fun snes(
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            val isLandscape = width > height
            val unit = Math.min(width, height)
            val controls = mutableListOf<VirtualControl>()

            val dpadHalf = if (isLandscape) unit * 0.19f else unit * 0.22f
            val dpadX = if (isLandscape) width * 0.14f else width * 0.25f
            val mainY = if (isLandscape) height * 0.65f else height * 0.75f

            controls.add(VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD, dpadX, mainY, dpadHalf, dpadHalf))

            // Shoulders L and R
            val shoulderHalfW = if (isLandscape) unit * 0.14f else unit * 0.16f
            val shoulderHalfH = if (isLandscape) unit * 0.055f else unit * 0.06f
            val shoulderY = if (isLandscape) height * 0.20f else height * 0.55f
            controls.add(VirtualControl(ID_L, RetroKey.L, "L", ControlShape.PILL, if (isLandscape) width * 0.14f else width * 0.20f, shoulderY, shoulderHalfW, shoulderHalfH))
            controls.add(VirtualControl(ID_R, RetroKey.R, "R", ControlShape.PILL, if (isLandscape) width * 0.86f else width * 0.80f, shoulderY, shoulderHalfW, shoulderHalfH))

            // SNES 4-Button Diamond
            val diamondRadius = if (isLandscape) unit * 0.08f else unit * 0.09f
            val diamondCenterX = if (isLandscape) width * 0.86f else width * 0.75f
            val diamondCenterY = mainY
            val spacing = diamondRadius * 1.55f

            controls.add(VirtualControl(ID_X, RetroKey.X, "X", ControlShape.CIRCLE, diamondCenterX, diamondCenterY - spacing, diamondRadius, diamondRadius))
            controls.add(VirtualControl(ID_Y, RetroKey.Y, "Y", ControlShape.CIRCLE, diamondCenterX - spacing, diamondCenterY, diamondRadius, diamondRadius))
            controls.add(VirtualControl(ID_A, RetroKey.A, "A", ControlShape.CIRCLE, diamondCenterX + spacing, diamondCenterY, diamondRadius, diamondRadius))
            controls.add(VirtualControl(ID_B, RetroKey.B, "B", ControlShape.CIRCLE, diamondCenterX, diamondCenterY + spacing, diamondRadius, diamondRadius))

            // Select and Start
            val smallHalfW = if (isLandscape) unit * 0.08f else unit * 0.12f
            val smallHalfH = if (isLandscape) unit * 0.035f else unit * 0.045f
            val sysY = if (isLandscape) height * 0.90f else height * 0.93f
            controls.add(VirtualControl(ID_SELECT, RetroKey.SELECT, "SELECT", ControlShape.PILL, if (isLandscape) width * 0.40f else width * 0.35f, sysY, smallHalfW, smallHalfH))
            controls.add(VirtualControl(ID_START, RetroKey.START, "START", ControlShape.PILL, if (isLandscape) width * 0.60f else width * 0.65f, sysY, smallHalfW, smallHalfH))

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled, "snes")
        }

        /**
         * Sega Genesis 6-Button Arcade Arc layout (A, B, C, X, Y, Z, Start, Mode).
         */
        fun genesis(
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            val isLandscape = width > height
            val unit = Math.min(width, height)
            val controls = mutableListOf<VirtualControl>()

            val dpadHalf = if (isLandscape) unit * 0.19f else unit * 0.22f
            val dpadX = if (isLandscape) width * 0.14f else width * 0.25f
            val mainY = if (isLandscape) height * 0.65f else height * 0.75f

            controls.add(VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD, dpadX, mainY, dpadHalf, dpadHalf))

            val btnRadius = if (isLandscape) unit * 0.065f else unit * 0.075f
            val arcCenterX = if (isLandscape) width * 0.82f else width * 0.72f
            val spacingX = btnRadius * 2.15f
            val spacingY = btnRadius * 2.15f

            // Top row: X, Y, Z
            val topRowY = mainY - spacingY * 0.7f
            controls.add(VirtualControl(ID_X, RetroKey.X, "X", ControlShape.CIRCLE, arcCenterX - spacingX, topRowY + btnRadius * 0.3f, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_Y, RetroKey.Y, "Y", ControlShape.CIRCLE, arcCenterX, topRowY, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_Z, RetroKey.Z, "Z", ControlShape.CIRCLE, arcCenterX + spacingX, topRowY - btnRadius * 0.3f, btnRadius, btnRadius))

            // Bottom row: A, B, C
            val bottomRowY = mainY + spacingY * 0.7f
            controls.add(VirtualControl(ID_A, RetroKey.A, "A", ControlShape.CIRCLE, arcCenterX - spacingX, bottomRowY + btnRadius * 0.3f, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_B, RetroKey.B, "B", ControlShape.CIRCLE, arcCenterX, bottomRowY, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_C, RetroKey.C, "C", ControlShape.CIRCLE, arcCenterX + spacingX, bottomRowY - btnRadius * 0.3f, btnRadius, btnRadius))

            // Start & Mode
            val smallHalfW = if (isLandscape) unit * 0.08f else unit * 0.12f
            val smallHalfH = if (isLandscape) unit * 0.035f else unit * 0.045f
            val sysY = if (isLandscape) height * 0.90f else height * 0.93f
            controls.add(VirtualControl(ID_MODE, RetroKey.MODE, "MODE", ControlShape.PILL, if (isLandscape) width * 0.40f else width * 0.35f, sysY, smallHalfW, smallHalfH))
            controls.add(VirtualControl(ID_START, RetroKey.START, "START", ControlShape.PILL, if (isLandscape) width * 0.60f else width * 0.65f, sysY, smallHalfW, smallHalfH))

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled, "genesis")
        }

        /**
         * NES 2-Button angled layout (B, A, Select, Start).
         */
        fun nes(
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            val isLandscape = width > height
            val unit = Math.min(width, height)
            val controls = mutableListOf<VirtualControl>()

            val dpadHalf = if (isLandscape) unit * 0.19f else unit * 0.22f
            val dpadX = if (isLandscape) width * 0.14f else width * 0.25f
            val mainY = if (isLandscape) height * 0.65f else height * 0.75f

            controls.add(VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD, dpadX, mainY, dpadHalf, dpadHalf))

            val btnRadius = if (isLandscape) unit * 0.10f else unit * 0.12f
            val rightCenterX = if (isLandscape) width * 0.82f else width * 0.75f

            controls.add(VirtualControl(ID_B, RetroKey.B, "B", ControlShape.CIRCLE, rightCenterX - btnRadius * 1.3f, mainY + btnRadius * 0.4f, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_A, RetroKey.A, "A", ControlShape.CIRCLE, rightCenterX + btnRadius * 1.3f, mainY - btnRadius * 0.3f, btnRadius, btnRadius))

            val smallHalfW = if (isLandscape) unit * 0.08f else unit * 0.12f
            val smallHalfH = if (isLandscape) unit * 0.035f else unit * 0.045f
            val sysY = if (isLandscape) height * 0.90f else height * 0.93f
            controls.add(VirtualControl(ID_SELECT, RetroKey.SELECT, "SELECT", ControlShape.PILL, if (isLandscape) width * 0.40f else width * 0.35f, sysY, smallHalfW, smallHalfH))
            controls.add(VirtualControl(ID_START, RetroKey.START, "START", ControlShape.PILL, if (isLandscape) width * 0.60f else width * 0.65f, sysY, smallHalfW, smallHalfH))

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled, "nes")
        }

        /**
         * PlayStation 1 layout (Cross, Circle, Square, Triangle, L1, R1, L2, R2, Select, Start).
         */
        fun ps1(
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            val isLandscape = width > height
            val unit = Math.min(width, height)
            val controls = mutableListOf<VirtualControl>()

            val dpadHalf = if (isLandscape) unit * 0.19f else unit * 0.22f
            val dpadX = if (isLandscape) width * 0.14f else width * 0.25f
            val mainY = if (isLandscape) height * 0.65f else height * 0.75f

            controls.add(VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD, dpadX, mainY, dpadHalf, dpadHalf))

            // Dual Shoulders: L1/L2 and R1/R2
            val shoulderW = if (isLandscape) unit * 0.12f else unit * 0.14f
            val shoulderH = if (isLandscape) unit * 0.045f else unit * 0.05f
            val topRowShoulderY = if (isLandscape) height * 0.15f else height * 0.50f
            val botRowShoulderY = if (isLandscape) height * 0.26f else height * 0.58f

            controls.add(VirtualControl(ID_L2, RetroKey.L2, "L2", ControlShape.PILL, if (isLandscape) width * 0.14f else width * 0.20f, topRowShoulderY, shoulderW, shoulderH))
            controls.add(VirtualControl(ID_L, RetroKey.L, "L1", ControlShape.PILL, if (isLandscape) width * 0.14f else width * 0.20f, botRowShoulderY, shoulderW, shoulderH))
            controls.add(VirtualControl(ID_R2, RetroKey.R2, "R2", ControlShape.PILL, if (isLandscape) width * 0.86f else width * 0.80f, topRowShoulderY, shoulderW, shoulderH))
            controls.add(VirtualControl(ID_R, RetroKey.R, "R1", ControlShape.PILL, if (isLandscape) width * 0.86f else width * 0.80f, botRowShoulderY, shoulderW, shoulderH))

            // 4 Face Buttons (Triangle, Square, Circle, Cross)
            val faceRadius = if (isLandscape) unit * 0.08f else unit * 0.09f
            val faceCenterX = if (isLandscape) width * 0.86f else width * 0.75f
            val spacing = faceRadius * 1.55f

            controls.add(VirtualControl(ID_X, RetroKey.X, "▲", ControlShape.CIRCLE, faceCenterX, mainY - spacing, faceRadius, faceRadius))
            controls.add(VirtualControl(ID_Y, RetroKey.Y, "■", ControlShape.CIRCLE, faceCenterX - spacing, mainY, faceRadius, faceRadius))
            controls.add(VirtualControl(ID_A, RetroKey.A, "●", ControlShape.CIRCLE, faceCenterX + spacing, mainY, faceRadius, faceRadius))
            controls.add(VirtualControl(ID_B, RetroKey.B, "✖", ControlShape.CIRCLE, faceCenterX, mainY + spacing, faceRadius, faceRadius))

            // Select & Start
            val smallHalfW = if (isLandscape) unit * 0.08f else unit * 0.12f
            val smallHalfH = if (isLandscape) unit * 0.035f else unit * 0.045f
            val sysY = if (isLandscape) height * 0.90f else height * 0.93f
            controls.add(VirtualControl(ID_SELECT, RetroKey.SELECT, "SELECT", ControlShape.PILL, if (isLandscape) width * 0.40f else width * 0.35f, sysY, smallHalfW, smallHalfH))
            controls.add(VirtualControl(ID_START, RetroKey.START, "START", ControlShape.PILL, if (isLandscape) width * 0.60f else width * 0.65f, sysY, smallHalfW, smallHalfH))

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled, "psx")
        }

        /**
         * Nintendo 64 layout (A, B, Z, C-Buttons, L, R, Start).
         */
        fun n64(
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            val isLandscape = width > height
            val unit = Math.min(width, height)
            val controls = mutableListOf<VirtualControl>()

            val dpadHalf = if (isLandscape) unit * 0.19f else unit * 0.22f
            val dpadX = if (isLandscape) width * 0.14f else width * 0.25f
            val mainY = if (isLandscape) height * 0.65f else height * 0.75f

            controls.add(VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD, dpadX, mainY, dpadHalf, dpadHalf))

            // Shoulders & Z-Trigger
            val shoulderW = if (isLandscape) unit * 0.12f else unit * 0.14f
            val shoulderH = if (isLandscape) unit * 0.045f else unit * 0.05f
            val shoulderY = if (isLandscape) height * 0.18f else height * 0.55f
            controls.add(VirtualControl(ID_L, RetroKey.L, "L", ControlShape.PILL, if (isLandscape) width * 0.14f else width * 0.20f, shoulderY, shoulderW, shoulderH))
            controls.add(VirtualControl(ID_Z, RetroKey.Z, "Z", ControlShape.PILL, if (isLandscape) width * 0.50f else width * 0.50f, shoulderY, shoulderW, shoulderH))
            controls.add(VirtualControl(ID_R, RetroKey.R, "R", ControlShape.PILL, if (isLandscape) width * 0.86f else width * 0.80f, shoulderY, shoulderW, shoulderH))

            // A and B buttons
            val btnRadius = if (isLandscape) unit * 0.08f else unit * 0.09f
            controls.add(VirtualControl(ID_B, RetroKey.B, "B", ControlShape.CIRCLE, width * 0.68f, mainY + btnRadius * 0.8f, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_A, RetroKey.A, "A", ControlShape.CIRCLE, width * 0.76f, mainY + btnRadius * 1.5f, btnRadius, btnRadius))

            // 4 C-Buttons Diamond
            val cRadius = btnRadius * 0.65f
            val cCenterX = if (isLandscape) width * 0.88f else width * 0.82f
            val cCenterY = mainY - btnRadius * 0.5f
            val cSpacing = cRadius * 1.5f

            controls.add(VirtualControl(ID_C_UP, RetroKey.C_UP, "▲", ControlShape.CIRCLE, cCenterX, cCenterY - cSpacing, cRadius, cRadius))
            controls.add(VirtualControl(ID_C_LEFT, RetroKey.C_LEFT, "◀", ControlShape.CIRCLE, cCenterX - cSpacing, cCenterY, cRadius, cRadius))
            controls.add(VirtualControl(ID_C_RIGHT, RetroKey.C_RIGHT, "▶", ControlShape.CIRCLE, cCenterX + cSpacing, cCenterY, cRadius, cRadius))
            controls.add(VirtualControl(ID_C_DOWN, RetroKey.C_DOWN, "▼", ControlShape.CIRCLE, cCenterX, cCenterY + cSpacing, cRadius, cRadius))

            // Start
            val smallHalfW = if (isLandscape) unit * 0.09f else unit * 0.12f
            val smallHalfH = if (isLandscape) unit * 0.04f else unit * 0.045f
            val sysY = if (isLandscape) height * 0.90f else height * 0.93f
            controls.add(VirtualControl(ID_START, RetroKey.START, "START", ControlShape.PILL, width * 0.50f, sysY, smallHalfW, smallHalfH))

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled, "n64")
        }

        /**
         * Arcade (FBNeo) 6-button layout.
         */
        fun arcade(
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            val isLandscape = width > height
            val unit = Math.min(width, height)
            val controls = mutableListOf<VirtualControl>()

            val dpadHalf = if (isLandscape) unit * 0.19f else unit * 0.22f
            val dpadX = if (isLandscape) width * 0.14f else width * 0.25f
            val mainY = if (isLandscape) height * 0.65f else height * 0.75f

            controls.add(VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD, dpadX, mainY, dpadHalf, dpadHalf))

            val btnRadius = if (isLandscape) unit * 0.065f else unit * 0.075f
            val arcCenterX = if (isLandscape) width * 0.82f else width * 0.72f
            val spacingX = btnRadius * 2.15f
            val spacingY = btnRadius * 2.15f

            // Top row: LP(X), MP(Y), HP(Z)
            val topRowY = mainY - spacingY * 0.7f
            controls.add(VirtualControl(ID_X, RetroKey.X, "LP", ControlShape.CIRCLE, arcCenterX - spacingX, topRowY + btnRadius * 0.3f, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_Y, RetroKey.Y, "MP", ControlShape.CIRCLE, arcCenterX, topRowY, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_Z, RetroKey.Z, "HP", ControlShape.CIRCLE, arcCenterX + spacingX, topRowY - btnRadius * 0.3f, btnRadius, btnRadius))

            // Bottom row: LK(A), MK(B), HK(C)
            val bottomRowY = mainY + spacingY * 0.7f
            controls.add(VirtualControl(ID_A, RetroKey.A, "LK", ControlShape.CIRCLE, arcCenterX - spacingX, bottomRowY + btnRadius * 0.3f, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_B, RetroKey.B, "MK", ControlShape.CIRCLE, arcCenterX, bottomRowY, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_C, RetroKey.C, "HK", ControlShape.CIRCLE, arcCenterX + spacingX, bottomRowY - btnRadius * 0.3f, btnRadius, btnRadius))

            // Coin & Start
            val smallHalfW = if (isLandscape) unit * 0.08f else unit * 0.12f
            val smallHalfH = if (isLandscape) unit * 0.035f else unit * 0.045f
            val sysY = if (isLandscape) height * 0.90f else height * 0.93f
            controls.add(VirtualControl(ID_SELECT, RetroKey.SELECT, "COIN", ControlShape.PILL, if (isLandscape) width * 0.40f else width * 0.35f, sysY, smallHalfW, smallHalfH))
            controls.add(VirtualControl(ID_START, RetroKey.START, "1P START", ControlShape.PILL, if (isLandscape) width * 0.60f else width * 0.65f, sysY, smallHalfW, smallHalfH))

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled, "arcade")
        }

        /**
         * PC Engine / TurboGrafx-16 layout (I, II, Run, Select).
         */
        fun pce(
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            val isLandscape = width > height
            val unit = Math.min(width, height)
            val controls = mutableListOf<VirtualControl>()

            val dpadHalf = if (isLandscape) unit * 0.19f else unit * 0.22f
            val dpadX = if (isLandscape) width * 0.14f else width * 0.25f
            val mainY = if (isLandscape) height * 0.65f else height * 0.75f

            controls.add(VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD, dpadX, mainY, dpadHalf, dpadHalf))

            val btnRadius = if (isLandscape) unit * 0.10f else unit * 0.12f
            val rightCenterX = if (isLandscape) width * 0.82f else width * 0.75f

            controls.add(VirtualControl(ID_B, RetroKey.B, "II", ControlShape.CIRCLE, rightCenterX - btnRadius * 1.3f, mainY + btnRadius * 0.4f, btnRadius, btnRadius))
            controls.add(VirtualControl(ID_A, RetroKey.A, "I", ControlShape.CIRCLE, rightCenterX + btnRadius * 1.3f, mainY - btnRadius * 0.3f, btnRadius, btnRadius))

            val smallHalfW = if (isLandscape) unit * 0.08f else unit * 0.12f
            val smallHalfH = if (isLandscape) unit * 0.035f else unit * 0.045f
            val sysY = if (isLandscape) height * 0.90f else height * 0.93f
            controls.add(VirtualControl(ID_SELECT, RetroKey.SELECT, "SELECT", ControlShape.PILL, if (isLandscape) width * 0.40f else width * 0.35f, sysY, smallHalfW, smallHalfH))
            controls.add(VirtualControl(ID_START, RetroKey.START, "RUN", ControlShape.PILL, if (isLandscape) width * 0.60f else width * 0.65f, sysY, smallHalfW, smallHalfH))

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled, "pce")
        }

        /**
         * PlayStation Portable (PSP) layout.
         */
        fun psp(
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            val isLandscape = width > height
            val unit = Math.min(width, height)
            val controls = mutableListOf<VirtualControl>()

            val dpadHalf = if (isLandscape) unit * 0.19f else unit * 0.22f
            val dpadX = if (isLandscape) width * 0.14f else width * 0.25f
            val mainY = if (isLandscape) height * 0.65f else height * 0.75f

            controls.add(VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD, dpadX, mainY, dpadHalf, dpadHalf))

            // Shoulders L and R
            val shoulderHalfW = if (isLandscape) unit * 0.14f else unit * 0.16f
            val shoulderHalfH = if (isLandscape) unit * 0.055f else unit * 0.06f
            val shoulderY = if (isLandscape) height * 0.20f else height * 0.55f
            controls.add(VirtualControl(ID_L, RetroKey.L, "L", ControlShape.PILL, if (isLandscape) width * 0.14f else width * 0.20f, shoulderY, shoulderHalfW, shoulderHalfH))
            controls.add(VirtualControl(ID_R, RetroKey.R, "R", ControlShape.PILL, if (isLandscape) width * 0.86f else width * 0.80f, shoulderY, shoulderHalfW, shoulderHalfH))

            // 4 Face Buttons (Triangle, Square, Circle, Cross)
            val faceRadius = if (isLandscape) unit * 0.08f else unit * 0.09f
            val faceCenterX = if (isLandscape) width * 0.86f else width * 0.75f
            val spacing = faceRadius * 1.55f

            controls.add(VirtualControl(ID_X, RetroKey.X, "▲", ControlShape.CIRCLE, faceCenterX, mainY - spacing, faceRadius, faceRadius))
            controls.add(VirtualControl(ID_Y, RetroKey.Y, "■", ControlShape.CIRCLE, faceCenterX - spacing, mainY, faceRadius, faceRadius))
            controls.add(VirtualControl(ID_A, RetroKey.A, "●", ControlShape.CIRCLE, faceCenterX + spacing, mainY, faceRadius, faceRadius))
            controls.add(VirtualControl(ID_B, RetroKey.B, "✖", ControlShape.CIRCLE, faceCenterX, mainY + spacing, faceRadius, faceRadius))

            // Select & Start
            val smallHalfW = if (isLandscape) unit * 0.08f else unit * 0.12f
            val smallHalfH = if (isLandscape) unit * 0.035f else unit * 0.045f
            val sysY = if (isLandscape) height * 0.90f else height * 0.93f
            controls.add(VirtualControl(ID_SELECT, RetroKey.SELECT, "SELECT", ControlShape.PILL, if (isLandscape) width * 0.40f else width * 0.35f, sysY, smallHalfW, smallHalfH))
            controls.add(VirtualControl(ID_START, RetroKey.START, "START", ControlShape.PILL, if (isLandscape) width * 0.60f else width * 0.65f, sysY, smallHalfW, smallHalfH))

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled, "psp")
        }

        /**
         * Nintendo DS (NDS) layout.
         */
        fun nds(
            width: Float,
            height: Float,
            opacity: Float = DEFAULT_OPACITY,
            turboEnabled: Boolean = false,
            comboEnabled: Boolean = false
        ): TouchLayout {
            val isLandscape = width > height
            val unit = Math.min(width, height)
            val controls = mutableListOf<VirtualControl>()

            val dpadHalf = if (isLandscape) unit * 0.19f else unit * 0.22f
            val dpadX = if (isLandscape) width * 0.14f else width * 0.25f
            val mainY = if (isLandscape) height * 0.65f else height * 0.75f

            controls.add(VirtualControl(ID_DPAD, null, "+", ControlShape.DPAD, dpadX, mainY, dpadHalf, dpadHalf))

            // Shoulders L and R
            val shoulderHalfW = if (isLandscape) unit * 0.14f else unit * 0.16f
            val shoulderHalfH = if (isLandscape) unit * 0.055f else unit * 0.06f
            val shoulderY = if (isLandscape) height * 0.20f else height * 0.55f
            controls.add(VirtualControl(ID_L, RetroKey.L, "L", ControlShape.PILL, if (isLandscape) width * 0.14f else width * 0.20f, shoulderY, shoulderHalfW, shoulderHalfH))
            controls.add(VirtualControl(ID_R, RetroKey.R, "R", ControlShape.PILL, if (isLandscape) width * 0.86f else width * 0.80f, shoulderY, shoulderHalfW, shoulderHalfH))

            // NDS 4-Button Diamond: X (top), Y (left), A (right), B (bottom)
            val diamondRadius = if (isLandscape) unit * 0.08f else unit * 0.09f
            val diamondCenterX = if (isLandscape) width * 0.86f else width * 0.75f
            val spacing = diamondRadius * 1.55f

            controls.add(VirtualControl(ID_X, RetroKey.X, "X", ControlShape.CIRCLE, diamondCenterX, mainY - spacing, diamondRadius, diamondRadius))
            controls.add(VirtualControl(ID_Y, RetroKey.Y, "Y", ControlShape.CIRCLE, diamondCenterX - spacing, mainY, diamondRadius, diamondRadius))
            controls.add(VirtualControl(ID_A, RetroKey.A, "A", ControlShape.CIRCLE, diamondCenterX + spacing, mainY, diamondRadius, diamondRadius))
            controls.add(VirtualControl(ID_B, RetroKey.B, "B", ControlShape.CIRCLE, diamondCenterX, mainY + spacing, diamondRadius, diamondRadius))

            // Select & Start
            val smallHalfW = if (isLandscape) unit * 0.08f else unit * 0.12f
            val smallHalfH = if (isLandscape) unit * 0.035f else unit * 0.045f
            val sysY = if (isLandscape) height * 0.90f else height * 0.93f
            controls.add(VirtualControl(ID_SELECT, RetroKey.SELECT, "SELECT", ControlShape.PILL, if (isLandscape) width * 0.40f else width * 0.35f, sysY, smallHalfW, smallHalfH))
            controls.add(VirtualControl(ID_START, RetroKey.START, "START", ControlShape.PILL, if (isLandscape) width * 0.60f else width * 0.65f, sysY, smallHalfW, smallHalfH))

            return TouchLayout(width, height, Collections.unmodifiableList(controls), opacity, turboEnabled, comboEnabled, "nds")
        }
    }

    /**
     * Hit-tests a single coordinate point and returns the composite [RetroKey] bitmask.
     */
    fun inputAt(x: Float, y: Float, slopFactor: Float = DEFAULT_HIT_SLOP, isTurboPhase: Boolean = true): Int {
        var mask = RetroKey.NO_KEYS_MASK
        for (control in controls) {
            mask = mask or control.hitKeyMask(x, y, slopFactor, isTurboPhase)
        }

        // Thumb Roll Assist: If touch falls into the transition zone between B and A
        val btnA = controls.firstOrNull { it.id == ID_A }
        val btnB = controls.firstOrNull { it.id == ID_B }
        if (btnA != null && btnB != null) {
            val distA = Math.hypot((x - btnA.cx).toDouble(), (y - btnA.cy).toDouble()).toFloat()
            val distB = Math.hypot((x - btnB.cx).toDouble(), (y - btnB.cy).toDouble()).toFloat()
            val btnDistance = Math.hypot((btnA.cx - btnB.cx).toDouble(), (btnA.cy - btnB.cy).toDouble()).toFloat()
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

    fun withOpacity(newOpacity: Float): TouchLayout {
        return TouchLayout(width, height, controls, newOpacity.coerceIn(0.0f, 1.0f), turboEnabled, comboEnabled, platform)
    }

    fun withSuperpowers(newTurboEnabled: Boolean, newComboEnabled: Boolean): TouchLayout {
        return createForPlatform(platform, width, height, opacity, newTurboEnabled, newComboEnabled)
    }

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
        return TouchLayout(width, height, Collections.unmodifiableList(updated), opacity, turboEnabled, comboEnabled, platform)
    }

    fun getClusters(): List<ClusterInfo> {
        val clusterSpecs = listOf(
            Triple(CLUSTER_DPAD, "D-PAD", listOf(ID_DPAD)),
            Triple(CLUSTER_ACTION, "ACTION", listOf(ID_B, ID_A, ID_X, ID_Y, ID_C, ID_Z)),
            Triple(CLUSTER_SHOULDER_L, "L", listOf(ID_L, ID_L2)),
            Triple(CLUSTER_SHOULDER_R, "R", listOf(ID_R, ID_R2)),
            Triple(CLUSTER_SYSTEM, "SYSTEM", listOf(ID_SELECT, ID_START, ID_MODE)),
            Triple(CLUSTER_TURBO, "TURBO", listOf(ID_TURBO_B, ID_TURBO_A)),
            Triple(CLUSTER_COMBO, "COMBO", listOf(ID_COMBO_AB)),
            Triple(CLUSTER_C_BUTTONS, "C-BUTTONS", listOf(ID_C_UP, ID_C_DOWN, ID_C_LEFT, ID_C_RIGHT))
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

    fun getCluster(clusterId: String): ClusterInfo? = getClusters().firstOrNull { it.id == clusterId }

    fun findClusterAt(x: Float, y: Float, touchSlop: Float = 16f): ClusterInfo? {
        for (cluster in getClusters()) {
            if (x >= cluster.left - touchSlop && x <= cluster.right + touchSlop &&
                y >= cluster.top - touchSlop && y <= cluster.bottom + touchSlop) {
                return cluster
            }
        }
        return null
    }

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
        return TouchLayout(width, height, Collections.unmodifiableList(updated), opacity, turboEnabled, comboEnabled, platform)
    }

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
        return TouchLayout(width, height, Collections.unmodifiableList(updated), opacity, turboEnabled, comboEnabled, platform)
    }

    fun applyClusterScales(scales: Map<String, Float>): TouchLayout {
        if (scales.isEmpty()) return this
        var currentLayout = this
        for ((clusterId, scale) in scales) {
            currentLayout = currentLayout.withClusterScale(clusterId, scale)
        }
        return currentLayout
    }

    fun getNormalizedClusterPositions(): Map<String, Pair<Float, Float>> {
        if (width <= 0f || height <= 0f) return emptyMap()
        return getClusters().associate { cluster ->
            cluster.id to Pair(cluster.anchorX / width, cluster.anchorY / height)
        }
    }

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
