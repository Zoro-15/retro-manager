package com.retropack.domain.model

/**
 * Console Bezel & Screen Frame Overlay styles for RetroPack.
 *
 * Implements Feature 6 specifications:
 * - OLED Pure Black (#000000) for battery saving on modern AMOLED screens
 * - Auto-Detect console frame based on active ROM platform
 * - GBA Classic Indigo & Platinum SP casings
 * - DMG-01 1989 Off-White Game Boy frame
 * - GBC Atomic Purple translucent frame
 * - 90s CRT Trinitron television frame for home consoles
 */
enum class BezelMode(
    val id: String,
    val displayName: String,
    val description: String,
    val isOledBlack: Boolean = false
) {
    NONE_OLED_BLACK(
        id = "none_oled_black",
        displayName = "OLED Pure Black (#000000)",
        description = "Turns off unused AMOLED pixels to save up to 40% battery with glowing cyber accents",
        isOledBlack = true
    ),
    AUTO(
        id = "auto",
        displayName = "Auto Handheld / Console Bezel",
        description = "Automatically renders authentic console housing based on ROM architecture"
    ),
    GBA_INDIGO(
        id = "gba_indigo",
        displayName = "GBA Classic Indigo Frame",
        description = "Iconic indigo handheld casing with speaker grills and chamfered screen lens"
    ),
    GBA_SP_PLATINUM(
        id = "gba_sp_platinum",
        displayName = "GBA SP Platinum Edition",
        description = "Sleek metallic platinum clamshell bezel with battery LED"
    ),
    DMG_RETRO_OFFWHITE(
        id = "dmg_retro_offwhite",
        displayName = "1989 DMG-01 Game Boy",
        description = "Authentic off-white casing with maroon button badge and glass lens lines"
    ),
    GBC_ATOMIC_PURPLE(
        id = "gbc_atomic_purple",
        displayName = "Atomic Purple GBC",
        description = "Translucent purple casing revealing retro internal electronics"
    ),
    CRT_TRINITRON_90S(
        id = "crt_trinitron_90s",
        displayName = "90s CRT TV Frame",
        description = "Retro living room CRT television frame with speaker grills and power knob"
    );

    companion object {
        fun fromId(id: String?): BezelMode {
            if (id.isNullOrBlank()) return NONE_OLED_BLACK
            val norm = id.lowercase().trim().replace("-", "_").replace(" ", "_")
            return entries.find { it.id == norm } ?: when (norm) {
                "none", "black", "oled", "off" -> NONE_OLED_BLACK
                "auto" -> AUTO
                "gba", "indigo" -> GBA_INDIGO
                "sp", "platinum" -> GBA_SP_PLATINUM
                "dmg", "gameboy", "offwhite" -> DMG_RETRO_OFFWHITE
                "gbc", "purple", "atomic" -> GBC_ATOMIC_PURPLE
                "crt", "tv", "trinitron" -> CRT_TRINITRON_90S
                else -> NONE_OLED_BLACK
            }
        }
    }
}
