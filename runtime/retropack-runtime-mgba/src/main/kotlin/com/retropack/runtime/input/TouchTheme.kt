package com.retropack.runtime.input

import android.graphics.Color

/**
 * Visual skin theme engine for RetroPack virtual touch controls.
 *
 * Implements specifications for:
 * 1. Classic Indigo: Original purple Game Boy Advance palette with cyan/grey accents.
 * 2. Glacier Clear Glass: Translucent frosted glassmorphism with vivid cyan borders.
 * 3. Onyx Stealth: Dark minimalist aesthetic with deep slate and carbon tones.
 * 4. Retro DMG Gray: Classic 1989 Game Boy off-white with magenta D-Pad and maroon buttons.
 */
enum class TouchTheme(
    val id: String,
    val displayName: String,
    val dpadFillColor: Int,
    val dpadStrokeColor: Int,
    val dpadTextColor: Int,
    val actionAFillColor: Int,
    val actionAStrokeColor: Int,
    val actionBFillColor: Int,
    val actionBStrokeColor: Int,
    val actionTextColor: Int,
    val shoulderFillColor: Int,
    val shoulderStrokeColor: Int,
    val shoulderTextColor: Int,
    val systemFillColor: Int,
    val systemStrokeColor: Int,
    val systemTextColor: Int,
    val turboFillColor: Int,
    val comboFillColor: Int,
    val accentColor: Int
) {
    CLASSIC_INDIGO(
        id = "classic_indigo",
        displayName = "Classic Indigo",
        dpadFillColor = Color.argb(190, 78, 72, 141),
        dpadStrokeColor = Color.argb(230, 46, 42, 92),
        dpadTextColor = Color.argb(240, 200, 200, 225),
        actionAFillColor = Color.argb(210, 46, 42, 92),
        actionAStrokeColor = Color.argb(255, 76, 208, 224),
        actionBFillColor = Color.argb(210, 46, 42, 92),
        actionBStrokeColor = Color.argb(255, 76, 208, 224),
        actionTextColor = Color.argb(255, 76, 208, 224),
        shoulderFillColor = Color.argb(180, 165, 165, 192),
        shoulderStrokeColor = Color.argb(220, 120, 120, 150),
        shoulderTextColor = Color.argb(255, 46, 42, 92),
        systemFillColor = Color.argb(170, 78, 72, 141),
        systemStrokeColor = Color.argb(200, 46, 42, 92),
        systemTextColor = Color.argb(240, 210, 210, 230),
        turboFillColor = Color.argb(200, 180, 60, 110),
        comboFillColor = Color.argb(200, 50, 120, 160),
        accentColor = Color.argb(255, 76, 208, 224)
    ),
    GLACIER(
        id = "glacier",
        displayName = "Glacier Glass",
        dpadFillColor = Color.argb(110, 26, 48, 56),
        dpadStrokeColor = Color.argb(220, 0, 229, 255),
        dpadTextColor = Color.argb(255, 0, 229, 255),
        actionAFillColor = Color.argb(120, 20, 60, 70),
        actionAStrokeColor = Color.argb(240, 0, 229, 255),
        actionBFillColor = Color.argb(120, 20, 60, 70),
        actionBStrokeColor = Color.argb(240, 0, 229, 255),
        actionTextColor = Color.argb(255, 224, 247, 250),
        shoulderFillColor = Color.argb(100, 26, 48, 56),
        shoulderStrokeColor = Color.argb(200, 0, 229, 255),
        shoulderTextColor = Color.argb(255, 0, 229, 255),
        systemFillColor = Color.argb(100, 26, 48, 56),
        systemStrokeColor = Color.argb(180, 0, 229, 255),
        systemTextColor = Color.argb(255, 224, 247, 250),
        turboFillColor = Color.argb(130, 0, 180, 210),
        comboFillColor = Color.argb(130, 0, 140, 180),
        accentColor = Color.argb(255, 0, 229, 255)
    ),
    ONYX_STEALTH(
        id = "onyx_stealth",
        displayName = "Onyx Stealth",
        dpadFillColor = Color.argb(200, 30, 34, 42),
        dpadStrokeColor = Color.argb(240, 14, 16, 21),
        dpadTextColor = Color.argb(220, 171, 178, 191),
        actionAFillColor = Color.argb(210, 22, 26, 32),
        actionAStrokeColor = Color.argb(230, 97, 175, 239),
        actionBFillColor = Color.argb(210, 22, 26, 32),
        actionBStrokeColor = Color.argb(230, 97, 175, 239),
        actionTextColor = Color.argb(255, 97, 175, 239),
        shoulderFillColor = Color.argb(190, 30, 34, 42),
        shoulderStrokeColor = Color.argb(220, 50, 56, 68),
        shoulderTextColor = Color.argb(220, 171, 178, 191),
        systemFillColor = Color.argb(180, 22, 26, 32),
        systemStrokeColor = Color.argb(200, 40, 46, 56),
        systemTextColor = Color.argb(200, 171, 178, 191),
        turboFillColor = Color.argb(200, 190, 80, 60),
        comboFillColor = Color.argb(200, 60, 120, 180),
        accentColor = Color.argb(255, 97, 175, 239)
    ),
    RETRO_DMG(
        id = "retro_dmg",
        displayName = "DMG Gray",
        dpadFillColor = Color.argb(210, 48, 48, 48),
        dpadStrokeColor = Color.argb(250, 20, 20, 20),
        dpadTextColor = Color.argb(255, 196, 192, 176),
        actionAFillColor = Color.argb(220, 139, 29, 75),
        actionAStrokeColor = Color.argb(250, 90, 15, 45),
        actionBFillColor = Color.argb(220, 139, 29, 75),
        actionBStrokeColor = Color.argb(250, 90, 15, 45),
        actionTextColor = Color.argb(255, 255, 220, 235),
        shoulderFillColor = Color.argb(190, 139, 139, 122),
        shoulderStrokeColor = Color.argb(230, 95, 95, 80),
        shoulderTextColor = Color.argb(255, 30, 30, 30),
        systemFillColor = Color.argb(180, 139, 139, 122),
        systemStrokeColor = Color.argb(210, 95, 95, 80),
        systemTextColor = Color.argb(255, 30, 30, 30),
        turboFillColor = Color.argb(210, 160, 43, 93),
        comboFillColor = Color.argb(200, 100, 100, 90),
        accentColor = Color.argb(255, 160, 43, 93)
    );

    companion object {
        fun fromId(id: String?): TouchTheme {
            if (id == null) return CLASSIC_INDIGO
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLASSIC_INDIGO
        }
    }
}
