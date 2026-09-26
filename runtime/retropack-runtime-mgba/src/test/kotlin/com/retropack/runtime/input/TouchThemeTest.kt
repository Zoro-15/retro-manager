package com.retropack.runtime.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class TouchThemeTest {

    @Test
    fun `all four predefined themes are discoverable by id`() {
        val indigo = TouchTheme.fromId("classic_indigo")
        assertEquals(TouchTheme.CLASSIC_INDIGO, indigo)
        assertEquals("Classic Indigo", indigo.displayName)

        val glacier = TouchTheme.fromId("glacier")
        assertEquals(TouchTheme.GLACIER, glacier)
        assertEquals("Glacier Glass", glacier.displayName)

        val onyx = TouchTheme.fromId("onyx_stealth")
        assertEquals(TouchTheme.ONYX_STEALTH, onyx)
        assertEquals("Onyx Stealth", onyx.displayName)

        val dmg = TouchTheme.fromId("retro_dmg")
        assertEquals(TouchTheme.RETRO_DMG, dmg)
        assertEquals("DMG Gray", dmg.displayName)
    }

    @Test
    fun `fromId fallback defaults to classic indigo for null or unknown id`() {
        assertEquals(TouchTheme.CLASSIC_INDIGO, TouchTheme.fromId(null))
        assertEquals(TouchTheme.CLASSIC_INDIGO, TouchTheme.fromId("unknown_theme_xyz"))
        assertEquals(TouchTheme.CLASSIC_INDIGO, TouchTheme.fromId(""))
    }

    @Test
    fun `themes define valid fill, stroke, and accent color tokens`() {
        for (theme in TouchTheme.entries) {
            assertNotNull(theme.displayName)
            assertNotNull(theme.dpadFillColor)
            assertNotNull(theme.dpadStrokeColor)
            assertNotNull(theme.actionAFillColor)
            assertNotNull(theme.actionBFillColor)
            assertNotNull(theme.shoulderFillColor)
            assertNotNull(theme.systemFillColor)
            assertNotNull(theme.turboFillColor)
            assertNotNull(theme.comboFillColor)
            assertNotNull(theme.accentColor)
        }
    }
}
