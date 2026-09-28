package com.retropack.runtime.video

import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.retropack.domain.model.BezelMode
import com.retropack.domain.model.ShaderMode
import com.retropack.runtime.core.NativeCore
import com.retropack.runtime.core.ScaleMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    initialBezelMode: BezelMode = BezelMode.AUTO,
    private val frameBufferSupplier: () -> IntBuffer? = { NativeCore.nativeGetVideoBuffer() }
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

    // Tracks the dimensions currently allocated in VRAM so that per-frame uploads
    // can use glTexSubImage2D instead of re-allocating texture storage every frame.
    @Volatile
    private var allocatedTextureWidth: Int = 0

    @Volatile
    private var allocatedTextureHeight: Int = 0

    /* ── Issue #46: asynchronous PBO double-buffered texture streaming ──
     * Two GL_PIXEL_UNPACK_BUFFER slots ping-pong: the CPU maps PBO[write]
     * (glMapBufferRange + DMA) for the CURRENT frame while the GPU is still
     * consuming PBO[read] from the previous upload, so glTexSubImage2D becomes
     * a zero-copy offset-0 operation that never stalls the pipeline. */
    private val pboIds = IntArray(2)
    private var pboAllocatedBytes: Int = 0
    private var pboWriteIndex: Int = 0
    private val pboHasFrame = BooleanArray(2)
    private var pboEnabled: Boolean = true
    private var pboHandlesGenerated: Boolean = false

    // Zero-allocation uniform caches (issue #46): one direct FloatBuffer per
    // matrix, rewritten in place only when geometry changes.
    private val mvpMatrixBuffer: FloatBuffer = RetroGlShader.createMatrixBuffer()
    private val viewportSizeBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private var mvpHandle: Int = -1
    private var outputSizeHandle: Int = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        allocatedTextureWidth = 0
        allocatedTextureHeight = 0
        // New GL context: previous PBO handles are invalid.
        pboHandlesGenerated = false
        pboAllocatedBytes = 0
        pboWriteIndex = 0
        pboHasFrame[0] = false
        pboHasFrame[1] = false
        pboEnabled = true

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
            // Issue #46: cache the output viewport dimensions in the direct
            // FloatBuffer so per-frame uniform uploads read from static memory.
            viewportSizeBuffer.clear()
            viewportSizeBuffer.put(cutout.viewportWidth.toFloat())
            viewportSizeBuffer.put(cutout.viewportHeight.toFloat())
            viewportSizeBuffer.position(0)
            viewportDirty = false
        }

        val buffer = frameBufferSupplier() ?: return
        buffer.position(0)

        if (currentProgram == 0) return
        GLES20.glUseProgram(currentProgram)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        if (allocatedTextureWidth != nativeWidth || allocatedTextureHeight != nativeHeight) {
            // Dimensions changed: (re)allocate texture storage, then record it.
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
            allocatedTextureWidth = nativeWidth
            allocatedTextureHeight = nativeHeight
            pboHasFrame[0] = false
            pboHasFrame[1] = false
        } else if (!uploadViaPbo(buffer)) {
            // PBO streaming unavailable: upload pixels in-place without
            // reallocating VRAM (synchronous fallback).
            GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                0,
                0,
                nativeWidth,
                nativeHeight,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                buffer
            )
        }

        if (samplerHandle >= 0) {
            GLES20.glUniform1i(samplerHandle, 0)
        }
        if (textureSizeHandle >= 0) {
            GLES20.glUniform2f(textureSizeHandle, nativeWidth.toFloat(), nativeHeight.toFloat())
        }
        if (mvpHandle >= 0) {
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrixBuffer)
        }
        if (outputSizeHandle >= 0) {
            GLES20.glUniform2f(outputSizeHandle, viewportSizeBuffer.get(0), viewportSizeBuffer.get(1))
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
        mvpHandle = GLES20.glGetUniformLocation(prog, "u_MVP")
        outputSizeHandle = GLES20.glGetUniformLocation(prog, "u_OutputSize")
    }

    /**
     * Issue #46: asynchronous PBO ping-pong upload.
     *
     * 1. Stage the current frame into PBO[write] via glMapBufferRange: the CPU
     *    copy overlaps the GPU's DMA of the previous upload.
     * 2. Feed glTexSubImage2D from PBO[read] at offset 0 (zero-copy: the
     *    pixels pointer is interpreted as an offset into the bound
     *    GL_PIXEL_UNPACK_BUFFER and the DMA proceeds asynchronously).
     * 3. Swap the slots.
     *
     * The staged frame is consumed by the NEXT draw, adding exactly one
     * display frame of pipeline latency in exchange for removing the
     * CPU-GPU synchronization bubble that synchronous TexSubImage2D caused
     * during high-resolution texture uploads. Returns false (and disables
     * itself) when GLES3/PBO support is unavailable, in which case the
     * caller falls back to the synchronous path.
     */
    private fun uploadViaPbo(buffer: IntBuffer): Boolean {
        if (!pboEnabled) return false

        try {
            if (!pboHandlesGenerated) {
                GLES30.glGenBuffers(2, pboIds, 0)
                pboHandlesGenerated = true
                pboAllocatedBytes = 0
            }

            val byteCount = nativeWidth * nativeHeight * 4
            if (byteCount <= 0) return false
            if (pboAllocatedBytes != byteCount) {
                for (i in 0..1) {
                    GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER, pboIds[i])
                    GLES30.glBufferData(GLES30.GL_PIXEL_UNPACK_BUFFER, byteCount, null, GLES30.GL_STREAM_DRAW)
                }
                GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER, 0)
                pboAllocatedBytes = byteCount
                pboHasFrame[0] = false
                pboHasFrame[1] = false
                pboWriteIndex = 0
            }

            val writeIdx = pboWriteIndex
            val readIdx = 1 - writeIdx

            // 1. CPU stages the current frame into PBO[write].
            GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER, pboIds[writeIdx])
            val mapped = GLES30.glMapBufferRange(
                GLES30.GL_PIXEL_UNPACK_BUFFER,
                0,
                byteCount,
                GLES30.GL_MAP_WRITE_BIT or GLES30.GL_MAP_INVALIDATE_BUFFER_BIT
            )
            if (mapped == null) {
                GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER, 0)
                pboEnabled = false
                return false
            }
            mapped.order(ByteOrder.nativeOrder())
            val intView = mapped.asIntBuffer()
            intView.position(0)
            intView.put(buffer)
            GLES30.glUnmapBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER)

            // 2. GPU consumes the previously staged frame (offset 0, zero-copy).
            if (pboHasFrame[readIdx]) {
                GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER, pboIds[readIdx])
                GLES20.glTexSubImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    nativeWidth,
                    nativeHeight,
                    GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE,
                    null
                )
            } else {
                // First frame after (re)allocation: synchronous direct upload
                // so the very first frame is not sourced from an empty PBO.
                GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER, 0)
                GLES20.glTexSubImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    nativeWidth,
                    nativeHeight,
                    GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE,
                    buffer
                )
            }
            GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER, 0)

            // 3. Swap.
            pboHasFrame[writeIdx] = true
            pboWriteIndex = readIdx
            return true
        } catch (_: Throwable) {
            GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER, 0)
            pboEnabled = false
            return false
        }
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
        if (pboHandlesGenerated) {
            GLES30.glDeleteBuffers(2, pboIds, 0)
            pboHandlesGenerated = false
        }
        pboAllocatedBytes = 0
        pboHasFrame[0] = false
        pboHasFrame[1] = false
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
        allocatedTextureWidth = 0
        allocatedTextureHeight = 0
    }
}
