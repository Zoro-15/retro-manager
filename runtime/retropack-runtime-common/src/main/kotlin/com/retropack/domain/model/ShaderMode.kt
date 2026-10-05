package com.retropack.domain.model

/**
 * Curated GLSL post-processing shaders for RetroPack.
 *
 * Implements Feature 5 specifications:
 * - Clean pixel passthrough
 * - 90s CRT Trinitron scanlines with phosphor aperture grille mask and vignette
 * - LCD dot-matrix subpixel grid with pixel borders (DMG, GBC, GBA)
 * - GBA/GBC color boost & gamma correction
 * - Sharp bilinear subpixel scaling (no shimmering)
 * - 1989 monochrome 4-shade pea-green Game Boy palette LUT
 */
enum class ShaderMode(
    val id: String,
    val displayName: String,
    val description: String,
    val recommendedFor: String
) {
    NONE(
        id = "none",
        displayName = "Clean Pixel",
        description = "Crisp, unmodified emulator pixel rendering",
        recommendedFor = "All Consoles"
    ),
    CRT_SCANLINES(
        id = "crt_scanlines",
        displayName = "90s CRT Scanlines",
        description = "Authentic CRT TV scanlines with phosphor mask, bloom, and subtle curvature",
        recommendedFor = "NES, SNES, Genesis, PC Engine"
    ),
    LCD_DOTMATRIX(
        id = "lcd_dotmatrix",
        displayName = "LCD Dot-Matrix",
        description = "Sub-pixel dot-matrix grid with authentic physical LCD gap borders",
        recommendedFor = "Game Boy, GBC, GBA"
    ),
    COLOR_BOOST(
        id = "color_boost",
        displayName = "Vibrant Color Boost",
        description = "Gamma 1.2 tone mapping restoring non-backlit GBA / GBC colors",
        recommendedFor = "Game Boy Advance & Color"
    ),
    SHARP_BILINEAR(
        id = "sharp_bilinear",
        displayName = "Sharp Bilinear",
        description = "Subpixel anti-aliasing eliminating uneven pixel shimmering during scrolling",
        recommendedFor = "2D Platformers & RPGs"
    ),
    DMG_PEA_GREEN(
        id = "dmg_pea_green",
        displayName = "1989 Pea-Green DMG",
        description = "Authentic 4-shade monochrome pea-green Game Boy LCD palette",
        recommendedFor = "Original Game Boy (DMG-01)"
    );

    companion object {
        fun fromId(id: String?): ShaderMode {
            if (id.isNullOrBlank()) return NONE
            val norm = id.lowercase().trim().replace("-", "_").replace(" ", "_")
            return entries.find { it.id == norm } ?: when (norm) {
                "crt", "scanlines", "trinitron" -> CRT_SCANLINES
                "lcd", "grid", "dotmatrix" -> LCD_DOTMATRIX
                "color", "gba_color", "boost" -> COLOR_BOOST
                "sharp", "bilinear" -> SHARP_BILINEAR
                "dmg", "pea_green", "green" -> DMG_PEA_GREEN
                else -> NONE
            }
        }
    }
}
