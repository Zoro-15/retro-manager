package com.retropack.runtime.video

import com.retropack.runtime.core.ScaleMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RetroGlShaderTest {

    @Test
    fun `fragment shader source contains GBA color and LCD grid filter routines`() {
        val src = RetroGlShader.FRAGMENT_SHADER_SRC
        assertTrue(src.contains("u_LcdGridEnabled"), "Fragment shader must declare u_LcdGridEnabled uniform")
        assertTrue(src.contains("u_ColorCorrectionEnabled"), "Fragment shader must declare u_ColorCorrectionEnabled uniform")
        assertTrue(src.contains("applyGbaColor"), "Fragment shader must implement GBA color transformation matrix")
        assertTrue(src.contains("applyLcdGrid"), "Fragment shader must implement LCD 3-subpixel grid calculation")
    }

    @Test
    fun `renderer properties manage LCD grid and color correction states`() {
        val renderer = RetroGlRenderer(ScaleMode.ASPECT_FIT)
        assertFalse(renderer.lcdGridEnabled)
        assertFalse(renderer.colorCorrectionEnabled)

        renderer.lcdGridEnabled = true
        renderer.colorCorrectionEnabled = true

        assertTrue(renderer.lcdGridEnabled)
        assertTrue(renderer.colorCorrectionEnabled)
    }

    @Test
    fun `native dimensions updates dirty viewport properly`() {
        val renderer = RetroGlRenderer()
        assertEquals(240, renderer.nativeWidth)
        assertEquals(160, renderer.nativeHeight)

        renderer.setNativeDimensions(160, 144)
        assertEquals(160, renderer.nativeWidth)
        assertEquals(144, renderer.nativeHeight)
    }
}
