package com.retropack.runtime.video

import com.retropack.runtime.core.ScaleMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RetroGlRendererTest {

    @Test
    fun `initializes with default integer fit and GBA dimensions`() {
        val renderer = RetroGlRenderer()
        assertEquals(ScaleMode.INTEGER_FIT, renderer.scaleMode)
        assertEquals(240, renderer.nativeWidth)
        assertEquals(160, renderer.nativeHeight)
    }

    @Test
    fun `updates scale mode correctly`() {
        val renderer = RetroGlRenderer(ScaleMode.INTEGER_FIT)
        renderer.updateScaleMode(ScaleMode.ASPECT_FIT)
        assertEquals(ScaleMode.ASPECT_FIT, renderer.scaleMode)

        renderer.updateScaleMode(ScaleMode.INTEGER_FIT)
        assertEquals(ScaleMode.INTEGER_FIT, renderer.scaleMode)
    }

    @Test
    fun `updates native dimensions for Game Boy and Game Boy Advance`() {
        val renderer = RetroGlRenderer()

        // GB / GBC resolution
        renderer.setNativeDimensions(160, 144)
        assertEquals(160, renderer.nativeWidth)
        assertEquals(144, renderer.nativeHeight)

        // GBA resolution
        renderer.setNativeDimensions(240, 160)
        assertEquals(240, renderer.nativeWidth)
        assertEquals(160, renderer.nativeHeight)

        assertThrows<IllegalArgumentException> {
            renderer.setNativeDimensions(0, 160)
        }
        assertThrows<IllegalArgumentException> {
            renderer.setNativeDimensions(240, -1)
        }
    }

    @Test
    fun `executes lifecycle callbacks without errors`() {
        val fakeBuffer = ByteBuffer.allocateDirect(240 * 160 * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()

        val renderer = RetroGlRenderer(
            initialScaleMode = ScaleMode.ASPECT_FIT,
            frameBufferSupplier = { fakeBuffer }
        )

        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, 1080, 720)
        renderer.onDrawFrame(null)

        assertEquals(ScaleMode.ASPECT_FIT, renderer.scaleMode)
        assertEquals(240, renderer.nativeWidth)
        assertEquals(160, renderer.nativeHeight)
    }

    @Test
    fun `handles null frame buffer gracefully`() {
        val renderer = RetroGlRenderer(
            initialScaleMode = ScaleMode.INTEGER_FIT,
            frameBufferSupplier = { null }
        )

        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, 800, 600)
        // Should execute cleanly when no frame buffer is available
        renderer.onDrawFrame(null)
    }

    @Test
    fun `RetroSurfaceView delegates scale mode and dimensions to renderer`() {
        val context = android.content.Context()
        val renderer = RetroGlRenderer(ScaleMode.INTEGER_FIT)
        val surfaceView = RetroSurfaceView(context, null, renderer)

        assertEquals(ScaleMode.INTEGER_FIT, surfaceView.scaleMode)

        surfaceView.scaleMode = ScaleMode.ASPECT_FIT
        assertEquals(ScaleMode.ASPECT_FIT, surfaceView.scaleMode)

        surfaceView.setFrameDimensions(160, 144)
        assertEquals(160, renderer.nativeWidth)
        assertEquals(144, renderer.nativeHeight)

        surfaceView.requestRenderFrame()
        surfaceView.pause()
        surfaceView.resume()
    }
}
