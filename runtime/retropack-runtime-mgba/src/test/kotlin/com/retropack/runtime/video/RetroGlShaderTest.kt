package com.retropack.runtime.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RetroGlShaderTest {

    @Test
    fun `vertex shader contains required attributes and varying`() {
        val src = RetroGlShader.VERTEX_SHADER_SRC
        assertTrue(src.contains("a_Position"), "Shader must declare a_Position")
        assertTrue(src.contains("a_TexCoord"), "Shader must declare a_TexCoord")
        assertTrue(src.contains("v_TexCoord"), "Shader must declare varying v_TexCoord")
        assertTrue(src.contains("gl_Position"), "Shader must assign gl_Position")
    }

    @Test
    fun `fragment shader contains texture sampler and varying`() {
        val src = RetroGlShader.FRAGMENT_SHADER_SRC
        assertTrue(src.contains("u_Texture"), "Shader must declare u_Texture")
        assertTrue(src.contains("v_TexCoord"), "Shader must accept varying v_TexCoord")
        assertTrue(src.contains("gl_FragColor"), "Shader must output gl_FragColor")
    }

    @Test
    fun `vertex buffer provides 4 NDC vertices`() {
        val buffer = RetroGlShader.createVertexBuffer()
        assertNotNull(buffer)
        assertEquals(8, buffer.capacity(), "Quad triangle strip requires 4 2D vertices (8 floats)")
        assertEquals(0, buffer.position())

        val values = FloatArray(8)
        buffer.get(values)
        assertEquals(-1.0f, values[0]) // top-left x
        assertEquals(1.0f, values[1])  // top-left y
        assertEquals(1.0f, values[6])  // bottom-right x
        assertEquals(-1.0f, values[7]) // bottom-right y
    }

    @Test
    fun `tex coord buffer provides 4 UV coordinates`() {
        val buffer = RetroGlShader.createTexCoordBuffer()
        assertNotNull(buffer)
        assertEquals(8, buffer.capacity(), "Quad UV mapping requires 4 2D texture coordinates")
        assertEquals(0, buffer.position())

        val values = FloatArray(8)
        buffer.get(values)
        assertEquals(0.0f, values[0]) // u0
        assertEquals(0.0f, values[1]) // v0
        assertEquals(1.0f, values[6]) // u3
        assertEquals(1.0f, values[7]) // v3
    }
}
