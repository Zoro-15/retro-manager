package com.retropack.runtime.video

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Manages OpenGL ES 2.0/3.0 shader compilation, program linking, and vertex/UV attribute buffers
 * for full-screen retro quad presentation.
 */
object RetroGlShader {
    const val VERTEX_SHADER_SRC = """
        attribute vec4 a_Position;
        attribute vec2 a_TexCoord;
        varying vec2 v_TexCoord;
        void main() {
            gl_Position = a_Position;
            v_TexCoord = a_TexCoord;
        }
    """

    const val FRAGMENT_SHADER_SRC = """
        precision mediump float;
        varying vec2 v_TexCoord;
        uniform sampler2D u_Texture;
        uniform vec2 u_TextureSize;
        uniform float u_LcdGridEnabled;
        uniform float u_ColorCorrectionEnabled;

        // Authentic GBA Color Profile (Pokefan531 / higan matrix with gamma 1.2)
        vec3 applyGbaColor(vec3 color) {
            vec3 gammaColor = pow(color, vec3(1.2));
            // Column-major mat3 in GLSL:
            // col0: (0.84, 0.08, 0.10), col1: (0.12, 0.80, 0.14), col2: (0.04, 0.12, 0.76)
            mat3 gbaMatrix = mat3(
                0.84, 0.08, 0.10,
                0.12, 0.80, 0.14,
                0.04, 0.12, 0.76
            );
            return clamp(gbaMatrix * gammaColor, 0.0, 1.0);
        }

        // Authentic 3-subpixel vertical RGB grid with scanline row separation
        vec3 applyLcdGrid(vec3 color, vec2 texCoord, vec2 textureSize) {
            vec2 pixelCoord = texCoord * textureSize;
            vec2 subCoord = fract(pixelCoord);

            vec3 subpixelMask;
            float subX = subCoord.x * 3.0;
            if (subX < 1.0) {
                subpixelMask = vec3(1.0, 0.72, 0.72);
            } else if (subX < 2.0) {
                subpixelMask = vec3(0.72, 1.0, 0.72);
            } else {
                subpixelMask = vec3(0.72, 0.72, 1.0);
            }

            float scanline = 1.0;
            if (subCoord.y < 0.10 || subCoord.y > 0.90) {
                scanline = 0.82;
            }

            return color * subpixelMask * scanline;
        }

        void main() {
            vec4 col = texture2D(u_Texture, v_TexCoord);
            vec3 finalRgb = col.rgb;

            if (u_ColorCorrectionEnabled > 0.5) {
                finalRgb = applyGbaColor(finalRgb);
            }

            if (u_LcdGridEnabled > 0.5) {
                finalRgb = applyLcdGrid(finalRgb, v_TexCoord, u_TextureSize);
            }

            gl_FragColor = vec4(finalRgb, 1.0);
        }
    """

    /**
     * Normalized device coordinates (NDC) spanning a 2D quad (triangle strip).
     */
    private val QUAD_VERTICES = floatArrayOf(
        -1.0f,  1.0f,
        -1.0f, -1.0f,
         1.0f,  1.0f,
         1.0f, -1.0f
    )

    /**
     * UV texture coordinates mapping direct frame memory onto the quad.
     * Normalized device coordinates (triangle strip):
     * 0: (-1.0,  1.0) -> Top-Left     -> UV (0.0, 0.0)
     * 1: (-1.0, -1.0) -> Bottom-Left  -> UV (0.0, 1.0)
     * 2: ( 1.0,  1.0) -> Top-Right    -> UV (1.0, 0.0)
     * 3: ( 1.0, -1.0) -> Bottom-Right -> UV (1.0, 1.0)
     *
     * In direct frame memory from mGBA, row 0 is the top scanline (UV V=0.0).
     */
    private val QUAD_TEX_COORDS = floatArrayOf(
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 0.0f,
        1.0f, 1.0f
    )

    fun createVertexBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_VERTICES)
                position(0)
            }

    fun createTexCoordBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(QUAD_TEX_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_TEX_COORDS)
                position(0)
            }

    fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            throw RuntimeException("Failed to allocate shader of type $type")
        }
        GLES20.glShaderSource(shader, shaderCode.trimIndent())
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed: $log")
        }
        return shader
    }

    fun createProgram(vertexSrc: String = VERTEX_SHADER_SRC, fragmentSrc: String = FRAGMENT_SHADER_SRC): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)

        val program = GLES20.glCreateProgram()
        if (program == 0) {
            throw RuntimeException("Failed to allocate OpenGL program")
        }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Program linking failed: $log")
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }
}
