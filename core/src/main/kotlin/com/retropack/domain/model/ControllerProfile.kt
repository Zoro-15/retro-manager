package com.retropack.domain.model

/**
 * Controller button binding and deadzone calibration profile for RetroPack.
 *
 * Implements Feature 4 specifications:
 * - Per-console button mappings (GBA, SNES, Genesis, NES, PS1, N64, PCE, NDS, Arcade).
 * - Deadzone calibration for analog sticks (0% to 30%) and trigger sensitivity.
 * - JSON serialization for runtime configuration injection into retropack.json.
 */
data class ControllerProfile(
    val profileName: String = "Default Profile",
    val platform: String = "gba",
    val deviceDescriptor: String = "default_gamepad",
    val buttonBindings: Map<String, Int> = emptyMap(),
    val deadzonePercent: Int = 15,
    val triggerThresholdPercent: Int = 50
) {
    companion object {
        const val MIN_DEADZONE_PERCENT = 0
        const val MAX_DEADZONE_PERCENT = 30
        const val DEFAULT_DEADZONE_PERCENT = 15

        /**
         * Returns canonical button labels and default Android KeyCodes for the given [platform].
         */
        fun defaultBindingsForPlatform(platform: String): Map<String, Int> {
            val map = mutableMapOf<String, Int>()
            val norm = platform.lowercase().trim()

            // Common D-Pad & System Buttons across all retro architectures
            map["DPAD_UP"] = 19       // KeyEvent.KEYCODE_DPAD_UP
            map["DPAD_DOWN"] = 20     // KeyEvent.KEYCODE_DPAD_DOWN
            map["DPAD_LEFT"] = 21     // KeyEvent.KEYCODE_DPAD_LEFT
            map["DPAD_RIGHT"] = 22    // KeyEvent.KEYCODE_DPAD_RIGHT
            map["START"] = 108        // KeyEvent.KEYCODE_BUTTON_START
            map["SELECT"] = 109       // KeyEvent.KEYCODE_BUTTON_SELECT

            when (norm) {
                "gba", "gbc", "gb" -> {
                    map["A"] = 96          // KEYCODE_BUTTON_A (South)
                    map["B"] = 97          // KEYCODE_BUTTON_B (East)
                    map["L"] = 102         // KEYCODE_BUTTON_L1
                    map["R"] = 103         // KEYCODE_BUTTON_R1
                }
                "snes", "sfc" -> {
                    map["B"] = 96          // KEYCODE_BUTTON_A (South)
                    map["A"] = 97          // KEYCODE_BUTTON_B (East)
                    map["Y"] = 99          // KEYCODE_BUTTON_X (West)
                    map["X"] = 100         // KEYCODE_BUTTON_Y (North)
                    map["L"] = 102         // KEYCODE_BUTTON_L1
                    map["R"] = 103         // KEYCODE_BUTTON_R1
                }
                "genesis", "md", "smd" -> {
                    map["A"] = 99          // KEYCODE_BUTTON_X (West)
                    map["B"] = 96          // KEYCODE_BUTTON_A (South)
                    map["C"] = 97          // KEYCODE_BUTTON_B (East)
                    map["X"] = 102         // KEYCODE_BUTTON_L1
                    map["Y"] = 100         // KEYCODE_BUTTON_Y (North)
                    map["Z"] = 103         // KEYCODE_BUTTON_R1
                    map["MODE"] = 109      // KEYCODE_BUTTON_SELECT
                }
                "nes", "fceumm" -> {
                    map["B"] = 99          // KEYCODE_BUTTON_X (West)
                    map["A"] = 96          // KEYCODE_BUTTON_A (South)
                    map["TURBO_B"] = 100   // KEYCODE_BUTTON_Y
                    map["TURBO_A"] = 97    // KEYCODE_BUTTON_B
                }
                "psx", "ps1" -> {
                    map["CROSS"] = 96      // KEYCODE_BUTTON_A (South)
                    map["CIRCLE"] = 97     // KEYCODE_BUTTON_B (East)
                    map["SQUARE"] = 99     // KEYCODE_BUTTON_X (West)
                    map["TRIANGLE"] = 100  // KEYCODE_BUTTON_Y (North)
                    map["L1"] = 102        // KEYCODE_BUTTON_L1
                    map["R1"] = 103        // KEYCODE_BUTTON_R1
                    map["L2"] = 104        // KEYCODE_BUTTON_L2
                    map["R2"] = 105        // KEYCODE_BUTTON_R2
                    map["L3"] = 106        // KEYCODE_BUTTON_THUMBL
                    map["R3"] = 107        // KEYCODE_BUTTON_THUMBR
                }
                "n64" -> {
                    map["A"] = 96          // KEYCODE_BUTTON_A (South)
                    map["B"] = 99          // KEYCODE_BUTTON_X (West)
                    map["Z"] = 104         // KEYCODE_BUTTON_L2 (Z-Trigger)
                    map["L"] = 102         // KEYCODE_BUTTON_L1
                    map["R"] = 103         // KEYCODE_BUTTON_R1
                    map["C_UP"] = 100      // KEYCODE_BUTTON_Y (North)
                    map["C_DOWN"] = 97     // KEYCODE_BUTTON_B (East)
                    map["C_LEFT"] = 105    // KEYCODE_BUTTON_R2
                    map["C_RIGHT"] = 107   // KEYCODE_BUTTON_THUMBR
                }
                "pce", "tg16" -> {
                    map["I"] = 97          // KEYCODE_BUTTON_B (East)
                    map["II"] = 96         // KEYCODE_BUTTON_A (South)
                    map["RUN"] = 108       // KEYCODE_BUTTON_START
                    map["SELECT"] = 109    // KEYCODE_BUTTON_SELECT
                }
                else -> {
                    map["A"] = 96
                    map["B"] = 97
                    map["X"] = 99
                    map["Y"] = 100
                    map["L"] = 102
                    map["R"] = 103
                }
            }

            return map
        }

        fun getConsoleButtonList(platform: String): List<String> {
            return when (platform.lowercase().trim()) {
                "psx", "ps1" -> listOf("CROSS", "CIRCLE", "SQUARE", "TRIANGLE", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "L1", "R1", "L2", "R2", "L3", "R3", "START", "SELECT")
                "n64" -> listOf("A", "B", "Z", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "C_UP", "C_DOWN", "C_LEFT", "C_RIGHT", "L", "R", "START")
                "snes", "sfc" -> listOf("A", "B", "X", "Y", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "L", "R", "START", "SELECT")
                "genesis", "md" -> listOf("A", "B", "C", "X", "Y", "Z", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "START", "MODE")
                "nes" -> listOf("A", "B", "TURBO_A", "TURBO_B", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "START", "SELECT")
                "pce", "tg16" -> listOf("I", "II", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "RUN", "SELECT")
                else -> listOf("A", "B", "L", "R", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "START", "SELECT")
            }
        }
    }
}
