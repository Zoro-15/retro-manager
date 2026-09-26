package com.retropack.runtime.input

import com.retropack.runtime.core.RetroKey

/**
 * Directional Pad and Virtual Analog Stick Modes.
 */
enum class DpadType {
    CLASSIC_CROSS,
    FIXED_JOYSTICK,
    FLOATING_JOYSTICK;

    companion object {
        fun fromString(value: String?): DpadType {
            if (value == null) return CLASSIC_CROSS
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CLASSIC_CROSS
        }
    }
}

/**
 * Sector Gate and Cardinal Snapping Strategy.
 */
enum class JoystickSnapMode {
    /**
     * RPG Grid Mode (Optimized for Pokémon / Zelda):
     * 60° wide cardinal window (UP, DOWN, LEFT, RIGHT) and 30° narrowed diagonal window
     * to eliminate accidental turns on tile-based RPG grids.
     */
    RPG_GRID_4WAY,

    /**
     * 8-Way Action Mode (Optimized for Platformers / Fighters):
     * Balanced 45° octagonal sector gate for fluid diagonal inputs.
     */
    ACTION_8WAY,

    /**
     * True 360° Analog Mode (Optimized for PS1, N64, PSP 3D camera & movement):
     * Full precision continuous analog deflection.
     */
    ANALOG_FREE;

    companion object {
        fun fromString(value: String?): JoystickSnapMode {
            if (value == null) return RPG_GRID_4WAY
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: RPG_GRID_4WAY
        }
    }
}

/**
 * Result data holder for joystick touch evaluations.
 */
data class JoystickResult(
    val keyMask: Int,
    val sector: Int,          // -1 = Deadzone/Center, 0..7 = Direction sectors
    val distance: Float,      // Deflection distance r
    val angleDegrees: Float,  // Angle in degrees [0, 360)
    val isDeadzone: Boolean,
    val detentCrossed: Boolean, // True if sector changed or transitioned in/out of deadzone
    val normX: Float = 0f,    // Normalized X deflection [-1.0f, +1.0f]
    val normY: Float = 0f     // Normalized Y deflection [-1.0f, +1.0f]
)

/**
 * Core Virtual Analog Joystick Engine with Cardinal Grid Snapping,
 * Continuous 360° Analog Vectors, Deadzone Core, and Detent Tracking.
 */
class VirtualJoystick(
    var baseCenterX: Float = 0f,
    var baseCenterY: Float = 0f,
    var baseRadius: Float = DEFAULT_BASE_RADIUS,
    var knobRadius: Float = DEFAULT_KNOB_RADIUS,
    var deadzoneRadius: Float = DEFAULT_DEADZONE_RADIUS,
    var snapMode: JoystickSnapMode = JoystickSnapMode.RPG_GRID_4WAY
) {
    companion object {
        const val DEFAULT_BASE_RADIUS = 54f      // 54dp
        const val DEFAULT_KNOB_RADIUS = 24f      // 24dp
        const val DEFAULT_DEADZONE_RADIUS = 12f  // 12dp

        // Sector Constants (0 to 7)
        const val SECTOR_DEADZONE = -1
        const val SECTOR_RIGHT = 0
        const val SECTOR_DOWN_RIGHT = 1
        const val SECTOR_DOWN = 2
        const val SECTOR_DOWN_LEFT = 3
        const val SECTOR_LEFT = 4
        const val SECTOR_UP_LEFT = 5
        const val SECTOR_UP = 6
        const val SECTOR_UP_RIGHT = 7
    }

    var knobX: Float = baseCenterX
        private set
    var knobY: Float = baseCenterY
        private set

    var normX: Float = 0f
        private set
    var normY: Float = 0f
        private set

    var isActive: Boolean = false
        private set
    var pointerId: Int? = null
        private set

    var currentSector: Int = SECTOR_DEADZONE
        private set
    var currentKeyMask: Int = RetroKey.NO_KEYS_MASK
        private set

    /**
     * Initializes a touch gesture on the joystick.
     */
    fun onDown(touchX: Float, touchY: Float, id: Int, isFloating: Boolean = false): JoystickResult {
        isActive = true
        pointerId = id
        if (isFloating) {
            baseCenterX = touchX
            baseCenterY = touchY
        }
        return update(touchX, touchY)
    }

    /**
     * Updates thumb deflection and evaluates direction snapping and detents.
     */
    fun update(touchX: Float, touchY: Float): JoystickResult {
        val dx = touchX - baseCenterX
        val dy = touchY - baseCenterY
        val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

        // Clamp knob displacement to baseRadius
        if (dist > baseRadius && dist > 0f) {
            val ratio = baseRadius / dist
            knobX = baseCenterX + dx * ratio
            knobY = baseCenterY + dy * ratio
            normX = (dx * ratio) / baseRadius
            normY = (dy * ratio) / baseRadius
        } else {
            knobX = touchX
            knobY = touchY
            normX = if (baseRadius > 0f) dx / baseRadius else 0f
            normY = if (baseRadius > 0f) dy / baseRadius else 0f
        }

        // Check deadzone
        if (dist < deadzoneRadius) {
            val previous = currentSector
            currentSector = SECTOR_DEADZONE
            currentKeyMask = RetroKey.NO_KEYS_MASK
            val detent = previous != SECTOR_DEADZONE
            normX = 0f
            normY = 0f
            return JoystickResult(
                keyMask = RetroKey.NO_KEYS_MASK,
                sector = SECTOR_DEADZONE,
                distance = dist,
                angleDegrees = 0f,
                isDeadzone = true,
                detentCrossed = detent,
                normX = 0f,
                normY = 0f
            )
        }

        var degrees = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (degrees < 0f) {
            degrees += 360f
        }

        val newSector = resolveSector(degrees, snapMode)
        val newMask = sectorToKeyMask(newSector)

        val previous = currentSector
        val detent = (previous != newSector)
        currentSector = newSector
        currentKeyMask = newMask

        return JoystickResult(
            keyMask = newMask,
            sector = newSector,
            distance = dist,
            angleDegrees = degrees,
            isDeadzone = false,
            detentCrossed = detent,
            normX = normX.coerceIn(-1.0f, 1.0f),
            normY = normY.coerceIn(-1.0f, 1.0f)
        )
    }

    /**
     * Resets joystick knob position and active state on touch release.
     */
    fun onUp(): JoystickResult {
        isActive = false
        pointerId = null
        knobX = baseCenterX
        knobY = baseCenterY
        normX = 0f
        normY = 0f
        val detent = currentSector != SECTOR_DEADZONE
        currentSector = SECTOR_DEADZONE
        currentKeyMask = RetroKey.NO_KEYS_MASK
        return JoystickResult(
            keyMask = RetroKey.NO_KEYS_MASK,
            sector = SECTOR_DEADZONE,
            distance = 0f,
            angleDegrees = 0f,
            isDeadzone = true,
            detentCrossed = detent,
            normX = 0f,
            normY = 0f
        )
    }

    fun setCenter(cx: Float, cy: Float) {
        baseCenterX = cx
        baseCenterY = cy
        if (!isActive) {
            knobX = cx
            knobY = cy
        }
    }

    fun resolveSector(degrees: Float, mode: JoystickSnapMode): Int {
        val normDeg = (degrees % 360f + 360f) % 360f
        return when (mode) {
            JoystickSnapMode.RPG_GRID_4WAY -> {
                when {
                    normDeg >= 330f || normDeg < 30f -> SECTOR_RIGHT
                    normDeg < 60f -> SECTOR_DOWN_RIGHT
                    normDeg < 120f -> SECTOR_DOWN
                    normDeg < 150f -> SECTOR_DOWN_LEFT
                    normDeg < 210f -> SECTOR_LEFT
                    normDeg < 240f -> SECTOR_UP_LEFT
                    normDeg < 300f -> SECTOR_UP
                    else -> SECTOR_UP_RIGHT
                }
            }
            JoystickSnapMode.ACTION_8WAY, JoystickSnapMode.ANALOG_FREE -> {
                when {
                    normDeg >= 337.5f || normDeg < 22.5f -> SECTOR_RIGHT
                    normDeg < 67.5f -> SECTOR_DOWN_RIGHT
                    normDeg < 112.5f -> SECTOR_DOWN
                    normDeg < 157.5f -> SECTOR_DOWN_LEFT
                    normDeg < 202.5f -> SECTOR_LEFT
                    normDeg < 247.5f -> SECTOR_UP_LEFT
                    normDeg < 292.5f -> SECTOR_UP
                    else -> SECTOR_UP_RIGHT
                }
            }
        }
    }

    fun sectorToKeyMask(sector: Int): Int {
        return when (sector) {
            SECTOR_RIGHT -> RetroKey.KEY_RIGHT
            SECTOR_DOWN_RIGHT -> RetroKey.KEY_RIGHT or RetroKey.KEY_DOWN
            SECTOR_DOWN -> RetroKey.KEY_DOWN
            SECTOR_DOWN_LEFT -> RetroKey.KEY_DOWN or RetroKey.KEY_LEFT
            SECTOR_LEFT -> RetroKey.KEY_LEFT
            SECTOR_UP_LEFT -> RetroKey.KEY_LEFT or RetroKey.KEY_UP
            SECTOR_UP -> RetroKey.KEY_UP
            SECTOR_UP_RIGHT -> RetroKey.KEY_UP or RetroKey.KEY_RIGHT
            else -> RetroKey.NO_KEYS_MASK
        }
    }
}
