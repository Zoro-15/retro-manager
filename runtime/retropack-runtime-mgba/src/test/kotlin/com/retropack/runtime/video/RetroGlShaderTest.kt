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
        // Standard UV mapping for frame buffers where row 0 is top scanline:
        // TL=(0,0), BL=(0,1), TR=(1,0), BR=(1,1).
        assertEquals(0.0f, values[0]) // u0 (TL)
        assertEquals(0.0f, values[1]) // v0 (TL)
        assertEquals(0.0f, values[2]) // u1 (BL)
        assertEquals(1.0f, values[3]) // v1 (BL)
        assertEquals(1.0f, values[4]) // u2 (TR)
        assertEquals(0.0f, values[5]) // v2 (TR)
        assertEquals(1.0f, values[6]) // u3 (BR)
        assertEquals(1.0f, values[7]) // v3 (BR)
    }
}
