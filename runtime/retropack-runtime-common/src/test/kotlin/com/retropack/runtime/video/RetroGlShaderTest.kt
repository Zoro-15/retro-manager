package com.retropack.runtime.video

import com.retropack.domain.model.ShaderMode
import com.retropack.runtime.core.ScaleMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RetroGlShaderTest {

    @Test
    fun `fragment shader sources contain CRT scanlines and LCD dotmatrix routines`() {
        val crtSrc = RetroGlShader.FRAGMENT_CRT_SCANLINES_SRC
        assertTrue(crtSrc.contains("applyCurvature"), "CRT shader must implement curvature")
        assertTrue(crtSrc.contains("scanFactor"), "CRT shader must modulate scanlines")

        val lcdSrc = RetroGlShader.FRAGMENT_LCD_DOTMATRIX_SRC
        assertTrue(lcdSrc.contains("subpixelMask"), "LCD shader must implement 3-subpixel mask")
        assertTrue(lcdSrc.contains("gridGap"), "LCD shader must calculate physical grid gaps")

        val colorBoostSrc = RetroGlShader.FRAGMENT_COLOR_BOOST_SRC
        assertTrue(colorBoostSrc.contains("applyGbaColorMatrix"), "Color boost must implement GBA color transformation matrix")

        val peaGreenSrc = RetroGlShader.FRAGMENT_DMG_PEA_GREEN_SRC
        assertTrue(peaGreenSrc.contains("lum"), "Pea green shader must compute luminance")
    }

    @Test
    fun `renderer properties manage shader modes and scale modes`() {
        val renderer = RetroGlRenderer(initialScaleMode = ScaleMode.ASPECT_FIT, initialShaderMode = ShaderMode.NONE)
        assertEquals(ShaderMode.NONE, renderer.shaderMode)
        assertEquals(ScaleMode.ASPECT_FIT, renderer.scaleMode)

        renderer.updateShaderMode(ShaderMode.LCD_DOTMATRIX)
        assertEquals(ShaderMode.LCD_DOTMATRIX, renderer.shaderMode)

        renderer.updateShaderMode(ShaderMode.COLOR_BOOST)
        assertEquals(ShaderMode.COLOR_BOOST, renderer.shaderMode)
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
