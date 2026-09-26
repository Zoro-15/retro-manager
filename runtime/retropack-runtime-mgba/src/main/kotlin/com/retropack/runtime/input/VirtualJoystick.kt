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
    ACTION_8WAY;

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
    val detentCrossed: Boolean // True if sector changed or transitioned in/out of deadzone
)

/**
 * Core Virtual Analog Joystick Engine with Cardinal Grid Snapping,
 * Smooth Angle Clamping, Deadzone Core, and Detent Tracking.
 *
 * Implements specifications:
 * - Base Radius: 54dp (diameter 108dp)
 * - Knob Radius: 24dp (diameter 48dp)
 * - Deadzone Radius: 12dp
 * - Pitch Black & Glowing Indigo Aesthetics
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
        } else {
            knobX = touchX
            knobY = touchY
        }

        // Check deadzone
        if (dist < deadzoneRadius) {
            val previous = currentSector
            currentSector = SECTOR_DEADZONE
            currentKeyMask = RetroKey.NO_KEYS_MASK
            val detent = previous != SECTOR_DEADZONE
            return JoystickResult(
                keyMask = RetroKey.NO_KEYS_MASK,
                sector = SECTOR_DEADZONE,
                distance = dist,
                angleDegrees = 0f,
                isDeadzone = true,
                detentCrossed = detent
            )
        }

        // Calculate angle in degrees [0, 360)
        // atan2 returns [-PI, PI], 0 = Right, PI/2 = Down, PI = Left, -PI/2 = Up
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
            detentCrossed = detent
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
        val detent = currentSector != SECTOR_DEADZONE
        currentSector = SECTOR_DEADZONE
        currentKeyMask = RetroKey.NO_KEYS_MASK
        return JoystickResult(
            keyMask = RetroKey.NO_KEYS_MASK,
            sector = SECTOR_DEADZONE,
            distance = 0f,
            angleDegrees = 0f,
            isDeadzone = true,
            detentCrossed = detent
        )
    }

    /**
     * Resets center coordinates and knob position.
     */
    fun setCenter(cx: Float, cy: Float) {
        baseCenterX = cx
        baseCenterY = cy
        if (!isActive) {
            knobX = cx
            knobY = cy
        }
    }

    /**
     * Resolves the sector index (0..7) based on angle and snap mode.
     */
    fun resolveSector(degrees: Float, mode: JoystickSnapMode): Int {
        val normDeg = (degrees % 360f + 360f) % 360f
        return when (mode) {
            JoystickSnapMode.RPG_GRID_4WAY -> {
                // Biased sector gate:
                // RIGHT: 330° to 30° (60° window)
                // DOWN_RIGHT: 30° to 60° (30° window)
                // DOWN: 60° to 120° (60° window)
                // DOWN_LEFT: 120° to 150° (30° window)
                // LEFT: 150° to 210° (60° window)
                // UP_LEFT: 210° to 240° (30° window)
                // UP: 240° to 300° (60° window)
                // UP_RIGHT: 300° to 330° (30° window)
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
            JoystickSnapMode.ACTION_8WAY -> {
                // Balanced 45° octagonal sectors:
                // RIGHT: 337.5° to 22.5°
                // DOWN_RIGHT: 22.5° to 67.5°
                // DOWN: 67.5° to 112.5°
                // DOWN_LEFT: 112.5° to 157.5°
                // LEFT: 157.5° to 202.5°
                // UP_LEFT: 202.5° to 247.5°
                // UP: 247.5° to 292.5°
                // UP_RIGHT: 292.5° to 337.5°
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

    /**
     * Converts a sector index (0..7) to native RetroKey bitmask.
     */
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
