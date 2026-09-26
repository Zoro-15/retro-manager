package com.retropack.runtime.video

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.retropack.runtime.core.ScaleMode

/**
 * Dedicated OpenGL ES 2.0/3.0 SurfaceView hosting [RetroGlRenderer] for full-screen retro emulation presentation.
 */
class RetroSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    val renderer: RetroGlRenderer = RetroGlRenderer()
) : GLSurfaceView(context, attrs) {

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    var scaleMode: ScaleMode
        get() = renderer.scaleMode
        set(value) {
            renderer.updateScaleMode(value)
            requestRender()
        }

    fun setFrameDimensions(width: Int, height: Int) {
        renderer.setNativeDimensions(width, height)
        requestRender()
    }

    fun requestRenderFrame() {
        requestRender()
    }

    fun pause() {
        onPause()
    }

    fun resume() {
        onResume()
    }
}
