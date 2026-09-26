package com.retropack.runtime.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ScaleModeTest {

    @Test
    fun `verify ScaleMode config resolution`() {
        assertEquals(ScaleMode.INTEGER_FIT, ScaleMode.fromConfig("integer_fit"))
        assertEquals(ScaleMode.INTEGER_FIT, ScaleMode.fromConfig("INTEGER_FIT"))
        assertEquals(ScaleMode.ASPECT_FIT, ScaleMode.fromConfig("aspect_fit"))
        assertEquals(ScaleMode.ASPECT_FIT, ScaleMode.fromConfig("Aspect_Fit"))

        // Defaults to INTEGER_FIT
        assertEquals(ScaleMode.INTEGER_FIT, ScaleMode.fromConfig(null))
        assertEquals(ScaleMode.INTEGER_FIT, ScaleMode.fromConfig(""))
        assertEquals(ScaleMode.INTEGER_FIT, ScaleMode.fromConfig("unknown_mode"))
    }

    @Test
    fun `verify INTEGER_FIT viewport calculation on portrait display`() {
        // GBA native resolution: 240 x 160
        // Surface: 1080 x 1920 (portrait phone)
        // scaleX = 1080 / 240 = 4
        // scaleY = 1920 / 160 = 12
        // integerScale = min(4, 12) = 4
        val viewport = ScaleMode.INTEGER_FIT.calculateViewport(
            surfaceWidth = 1080,
            surfaceHeight = 1920,
            nativeWidth = 240,
            nativeHeight = 160
        )

        assertEquals(960, viewport.width)
        assertEquals(640, viewport.height)
        assertEquals(4.0f, viewport.scaleFactor)
        // Centering: (1080 - 960) / 2 = 60, (1920 - 640) / 2 = 640
        assertEquals(60, viewport.x)
        assertEquals(640, viewport.y)
    }

    @Test
    fun `verify INTEGER_FIT viewport calculation on landscape display`() {
        // GBA native resolution: 240 x 160
        // Surface: 2400 x 1080 (landscape phone)
        // scaleX = 2400 / 240 = 10
        // scaleY = 1080 / 160 = 6
        // integerScale = min(10, 6) = 6
        val viewport = ScaleMode.INTEGER_FIT.calculateViewport(
            surfaceWidth = 2400,
            surfaceHeight = 1080,
            nativeWidth = 240,
            nativeHeight = 160
        )

        assertEquals(1440, viewport.width)
        assertEquals(960, viewport.height)
        assertEquals(6.0f, viewport.scaleFactor)
        // Centering: (2400 - 1440) / 2 = 480, (1080 - 960) / 2 = 60
        assertEquals(480, viewport.x)
        assertEquals(60, viewport.y)
    }

    @Test
    fun `verify INTEGER_FIT enforces minimum 1x scale`() {
        // Surface smaller than native
        val viewport = ScaleMode.INTEGER_FIT.calculateViewport(
            surfaceWidth = 100,
            surfaceHeight = 100,
            nativeWidth = 240,
            nativeHeight = 160
        )

        assertEquals(240, viewport.width)
        assertEquals(160, viewport.height)
        assertEquals(1.0f, viewport.scaleFactor)
    }

    @Test
    fun `verify ASPECT_FIT viewport calculation on portrait display`() {
        // GBA native resolution: 240 x 160 (3:2)
        // Surface: 1080 x 1920
        // scaleX = 1080 / 240 = 4.5
        // scaleY = 1920 / 160 = 12.0
        // floatScale = 4.5
        val viewport = ScaleMode.ASPECT_FIT.calculateViewport(
            surfaceWidth = 1080,
            surfaceHeight = 1920,
            nativeWidth = 240,
            nativeHeight = 160
        )

        assertEquals(1080, viewport.width)
        assertEquals(720, viewport.height)
        assertEquals(4.5f, viewport.scaleFactor)
        assertEquals(0, viewport.x)
        assertEquals((1920 - 720) / 2, viewport.y)
    }

    @Test
    fun `verify ASPECT_FIT viewport calculation on landscape display`() {
        // GBA native resolution: 240 x 160 (3:2)
        // Surface: 2400 x 1080
        // scaleX = 2400 / 240 = 10.0
        // scaleY = 1080 / 160 = 6.75
        // floatScale = 6.75
        val viewport = ScaleMode.ASPECT_FIT.calculateViewport(
            surfaceWidth = 2400,
            surfaceHeight = 1080,
            nativeWidth = 240,
            nativeHeight = 160
        )

        assertEquals((240 * 6.75f).toInt(), viewport.width) // 1620
        assertEquals(1080, viewport.height)
        assertEquals(6.75f, viewport.scaleFactor)
        assertEquals((2400 - 1620) / 2, viewport.x)
        assertEquals(0, viewport.y)
    }

    @Test
    fun `verify invalid dimensions throw IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            ScaleMode.INTEGER_FIT.calculateViewport(0, 100, 240, 160)
        }
        assertThrows<IllegalArgumentException> {
            ScaleMode.ASPECT_FIT.calculateViewport(100, 100, 0, 160)
        }
    }
}
