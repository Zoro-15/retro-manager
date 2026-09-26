package com.retropack.runtime.video

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.retropack.runtime.core.NativeCore
import com.retropack.runtime.core.ScaleMode
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * High-performance OpenGL ES 2.0/3.0 SurfaceView renderer for RetroPack.
 *
 * Implements specifications from:
 * - masterplan.md Section 5.4: "OpenGL ES 2.0/3.0 SurfaceView rendering; supports integer_fit
 *   (pixel-perfect square scaling) and aspect_fit with bilinear filter."
 * - architechture.md Section 7 & Contract 2.
 */
class RetroGlRenderer(
    initialScaleMode: ScaleMode = ScaleMode.INTEGER_FIT,
    private val frameBufferSupplier: () -> IntBuffer? = { NativeCore.nativeGetVideoBuffer() }
) : GLSurfaceView.Renderer {

    @Volatile
    var scaleMode: ScaleMode = initialScaleMode
        private set

    @Volatile
    var nativeWidth: Int = 240
        private set

    @Volatile
    var nativeHeight: Int = 160
        private set

    @Volatile
    private var surfaceWidth: Int = 0

    @Volatile
    private var surfaceHeight: Int = 0

    @Volatile
    private var viewportDirty: Boolean = true

    @Volatile
    var lcdGridEnabled: Boolean = false

    @Volatile
    var colorCorrectionEnabled: Boolean = false

    @Volatile
    private var filterModeDirty: Boolean = true

    private var program: Int = 0
    private var positionHandle: Int = -1
    private var texCoordHandle: Int = -1
    private var samplerHandle: Int = -1
    private var textureSizeHandle: Int = -1
    private var lcdGridHandle: Int = -1
    private var colorCorrectionHandle: Int = -1
    private var textureId: Int = 0

    private val vertexBuffer: FloatBuffer = RetroGlShader.createVertexBuffer()
    private val texCoordBuffer: FloatBuffer = RetroGlShader.createTexCoordBuffer()
    private val textureArray = IntArray(1)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        program = RetroGlShader.createProgram()
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        samplerHandle = GLES20.glGetUniformLocation(program, "u_Texture")
        textureSizeHandle = GLES20.glGetUniformLocation(program, "u_TextureSize")
        lcdGridHandle = GLES20.glGetUniformLocation(program, "u_LcdGridEnabled")
        colorCorrectionHandle = GLES20.glGetUniformLocation(program, "u_ColorCorrectionEnabled")

        GLES20.glGenTextures(1, textureArray, 0)
        textureId = textureArray[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        applyTextureFiltering(scaleMode)
        viewportDirty = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        viewportDirty = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (filterModeDirty) {
            applyTextureFiltering(scaleMode)
            filterModeDirty = false
        }

        if (viewportDirty && surfaceWidth > 0 && surfaceHeight > 0) {
            val rect = scaleMode.calculateViewport(
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
                nativeWidth = nativeWidth,
                nativeHeight = nativeHeight
            )
            GLES20.glViewport(rect.x, rect.y, rect.width, rect.height)
            viewportDirty = false
        }

        val buffer = frameBufferSupplier() ?: return
        buffer.position(0)

        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            nativeWidth,
            nativeHeight,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer
        )

        GLES20.glUniform1i(samplerHandle, 0)
        GLES20.glUniform2f(textureSizeHandle, nativeWidth.toFloat(), nativeHeight.toFloat())
        GLES20.glUniform1f(lcdGridHandle, if (lcdGridEnabled) 1.0f else 0.0f)
        GLES20.glUniform1f(colorCorrectionHandle, if (colorCorrectionEnabled) 1.0f else 0.0f)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        texCoordBuffer.position(0)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    fun updateScaleMode(mode: ScaleMode) {
        if (scaleMode != mode) {
            scaleMode = mode
            filterModeDirty = true
            viewportDirty = true
        }
    }

    fun setNativeDimensions(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Dimensions must be positive" }
        if (nativeWidth != width || nativeHeight != height) {
            nativeWidth = width
            nativeHeight = height
            viewportDirty = true
        }
    }

    private fun applyTextureFiltering(mode: ScaleMode) {
        if (textureId == 0) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        val filter = when (mode) {
            ScaleMode.INTEGER_FIT -> GLES20.GL_NEAREST
            ScaleMode.ASPECT_FIT -> GLES20.GL_LINEAR
        }
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
    }

    fun release() {
        if (textureId != 0) {
            textureArray[0] = textureId
            GLES20.glDeleteTextures(1, textureArray, 0)
            textureId = 0
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }
}
