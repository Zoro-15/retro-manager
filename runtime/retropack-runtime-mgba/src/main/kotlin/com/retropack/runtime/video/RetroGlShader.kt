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
        void main() {
            gl_FragColor = texture2D(u_Texture, v_TexCoord);
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
