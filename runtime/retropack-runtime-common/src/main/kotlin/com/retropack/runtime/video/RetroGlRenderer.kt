package com.retropack.runtime.video

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.retropack.domain.model.BezelMode
import com.retropack.domain.model.ShaderMode
import com.retropack.runtime.core.NativeCore
import com.retropack.runtime.core.ScaleMode
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * High-performance OpenGL ES 2.0/3.0 SurfaceView renderer for RetroPack.
 *
 * Implements Feature 5 (GLSL Shaders) and Feature 6 (Console Bezels & OLED Pure Black):
 * - Dynamic runtime switching between 6 curated GLSL shaders (Clean, CRT Scanlines, LCD Grid, Color Boost, Sharp Bilinear, DMG Pea Green).
 * - Viewport cutout integration with BezelConfig (Landscape & Portrait calibration).
 * - Dynamic scaling modes (Integer Fit, Aspect Fit, Full Screen Stretch).
 */
class RetroGlRenderer(
    initialScaleMode: ScaleMode = ScaleMode.INTEGER_FIT,
    initialShaderMode: ShaderMode = ShaderMode.NONE,
    initialBezelMode: BezelMode = BezelMode.NONE_OLED_BLACK,
    private val frameBufferSupplier: () -> IntBuffer? = { null }
) : GLSurfaceView.Renderer {

    @Volatile
    var scaleMode: ScaleMode = initialScaleMode
        private set

    @Volatile
    var shaderMode: ShaderMode = initialShaderMode
        private set

    @Volatile
    var bezelMode: BezelMode = initialBezelMode
        private set

    @Volatile
    var bfiEnabled: Boolean = false

    private var bfiBlackFrameToggle: Boolean = false

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
    private var shaderDirty: Boolean = true

    @Volatile
    private var filterModeDirty: Boolean = true

    // Shader Programs Cache: ShaderMode -> ProgramHandle
    private val programs = mutableMapOf<ShaderMode, Int>()
    private var currentProgram: Int = 0

    private var positionHandle: Int = -1
    private var texCoordHandle: Int = -1
    private var samplerHandle: Int = -1
    private var textureSizeHandle: Int = -1
    private var textureId: Int = 0

    private val vertexBuffer: FloatBuffer = RetroGlShader.createVertexBuffer()
    private val texCoordBuffer: FloatBuffer = RetroGlShader.createTexCoordBuffer()
    private val textureArray = IntArray(1)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Precompile default passthrough program
        loadProgramForMode(shaderMode)

        GLES20.glGenTextures(1, textureArray, 0)
        textureId = textureArray[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        applyTextureFiltering(scaleMode, shaderMode)
        viewportDirty = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        viewportDirty = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (bfiEnabled) {
            bfiBlackFrameToggle = !bfiBlackFrameToggle
            if (bfiBlackFrameToggle) {
                // Black sub-frame insertion for CRT phosphor decay simulation
                return
            }
        }

        if (shaderDirty) {
            loadProgramForMode(shaderMode)
            shaderDirty = false
        }

        if (filterModeDirty) {
            applyTextureFiltering(scaleMode, shaderMode)
            filterModeDirty = false
        }

        if (viewportDirty && surfaceWidth > 0 && surfaceHeight > 0) {
            val cutout = BezelConfig.calculateCutout(
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
                nativeWidth = nativeWidth,
                nativeHeight = nativeHeight,
                bezelMode = bezelMode,
                scaleMode = scaleMode
            )
            GLES20.glViewport(cutout.viewportX, cutout.viewportY, cutout.viewportWidth, cutout.viewportHeight)
            viewportDirty = false
        }

        val buffer = frameBufferSupplier() ?: return
        buffer.position(0)

        if (currentProgram == 0) return
        GLES20.glUseProgram(currentProgram)

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

        if (samplerHandle >= 0) {
            GLES20.glUniform1i(samplerHandle, 0)
        }
        if (textureSizeHandle >= 0) {
            GLES20.glUniform2f(textureSizeHandle, nativeWidth.toFloat(), nativeHeight.toFloat())
        }

        vertexBuffer.position(0)
        if (positionHandle >= 0) {
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        }

        texCoordBuffer.position(0)
        if (texCoordHandle >= 0) {
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        if (positionHandle >= 0) GLES20.glDisableVertexAttribArray(positionHandle)
        if (texCoordHandle >= 0) GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun loadProgramForMode(mode: ShaderMode) {
        val existing = programs[mode]
        val prog = if (existing != null && existing != 0) {
            existing
        } else {
            val fragSrc = RetroGlShader.getFragmentShaderSourceForMode(mode)
            val newProg = try {
                RetroGlShader.createProgram(fragmentSrc = fragSrc)
            } catch (e: Exception) {
                // Fallback to passthrough if specific shader fails
                RetroGlShader.createProgram(fragmentSrc = RetroGlShader.FRAGMENT_PASSTHROUGH_SRC)
            }
            programs[mode] = newProg
            newProg
        }

        currentProgram = prog
        positionHandle = GLES20.glGetAttribLocation(prog, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(prog, "a_TexCoord")
        samplerHandle = GLES20.glGetUniformLocation(prog, "u_Texture")
        textureSizeHandle = GLES20.glGetUniformLocation(prog, "u_TextureSize")
    }

    fun updateShaderMode(mode: ShaderMode) {
        if (shaderMode != mode) {
            shaderMode = mode
            shaderDirty = true
            filterModeDirty = true
        }
    }

    fun updateScaleMode(mode: ScaleMode) {
        if (scaleMode != mode) {
            scaleMode = mode
            filterModeDirty = true
            viewportDirty = true
        }
    }

    fun updateBezelMode(mode: BezelMode) {
        if (bezelMode != mode) {
            bezelMode = mode
            viewportDirty = true
        }
    }

    fun updateBfiEnabled(enabled: Boolean) {
        bfiEnabled = enabled
    }

    fun setNativeDimensions(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Dimensions must be positive" }
        if (nativeWidth != width || nativeHeight != height) {
            nativeWidth = width
            nativeHeight = height
            viewportDirty = true
        }
    }

    private fun applyTextureFiltering(scale: ScaleMode, shader: ShaderMode) {
        if (textureId == 0) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Shaders like CRT scanlines and Sharp Bilinear require nearest/linear sampling correctly
        val filter = when {
            shader == ShaderMode.SHARP_BILINEAR -> GLES20.GL_LINEAR
            shader == ShaderMode.CRT_SCANLINES -> GLES20.GL_LINEAR
            scale == ScaleMode.INTEGER_FIT -> GLES20.GL_NEAREST
            else -> GLES20.GL_LINEAR
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
        for (prog in programs.values) {
            if (prog != 0) {
                GLES20.glDeleteProgram(prog)
            }
        }
        programs.clear()
        currentProgram = 0
    }
}
