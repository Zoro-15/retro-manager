package com.retropack.runtime.video

import android.opengl.GLES20
import com.retropack.domain.model.ShaderMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Manages OpenGL ES 2.0/3.0 shader compilation, program linking, and curated GLSL retro post-processing filters.
 *
 * Implements Feature 5 specifications:
 * - Clean Pixel (unmodified emulator frame)
 * - 90s CRT Trinitron scanlines with phosphor triad aperture grille, subtle barrel curvature, and bloom vignette
 * - LCD sub-pixel dot-matrix grid with physical gap borders (Game Boy / GBC / GBA)
 * - Color Boost with gamma 1.2 matrix restoration
 * - Sharp Bilinear subpixel anti-aliasing (no pixel shimmering)
 * - Authentic 1989 4-shade monochrome pea-green Game Boy palette
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

    // 1. Clean Pixel / Passthrough
    const val FRAGMENT_PASSTHROUGH_SRC = """
        precision mediump float;
        varying vec2 v_TexCoord;
        uniform sampler2D u_Texture;
        void main() {
            gl_FragColor = vec4(texture2D(u_Texture, v_TexCoord).rgb, 1.0);
        }
    """

    // 2. 90s CRT Trinitron Scanlines with Phosphor Triads & Subtle Vignette
    const val FRAGMENT_CRT_SCANLINES_SRC = """
        precision mediump float;
        varying vec2 v_TexCoord;
        uniform sampler2D u_Texture;
        uniform vec2 u_TextureSize;

        vec2 applyCurvature(vec2 uv) {
            vec2 cc = uv - 0.5;
            float dist = dot(cc, cc);
            return uv + cc * (dist * 0.05);
        }

        void main() {
            vec2 uv = applyCurvature(v_TexCoord);
            if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                return;
            }

            vec4 color = texture2D(u_Texture, uv);

            // Scanline Intensity Modulation
            float scanline = sin(uv.y * u_TextureSize.y * 3.14159265 * 2.0);
            scanline = 0.5 * scanline + 0.5;
            float scanFactor = mix(0.72, 1.05, scanline);

            // Phosphor Triad Subpixel Mask (Aperture Grille)
            float pixelX = fract(uv.x * u_TextureSize.x * 3.0);
            vec3 mask = vec3(1.0);
            if (pixelX < 0.33) {
                mask = vec3(1.08, 0.88, 0.88);
            } else if (pixelX < 0.66) {
                mask = vec3(0.88, 1.08, 0.88);
            } else {
                mask = vec3(0.88, 0.88, 1.08);
            }

            // Radial Bloom & Corner Vignette
            vec2 vigCoord = (uv - 0.5) * 2.0;
            float vignette = clamp(1.0 - dot(vigCoord, vigCoord) * 0.18, 0.0, 1.0);

            vec3 finalRgb = color.rgb * scanFactor * mask * vignette;
            gl_FragColor = vec4(clamp(finalRgb, 0.0, 1.0), 1.0);
        }
    """

    // 3. LCD Dot-Matrix Subpixel Grid (Game Boy / GBC / GBA)
    const val FRAGMENT_LCD_DOTMATRIX_SRC = """
        precision mediump float;
        varying vec2 v_TexCoord;
        uniform sampler2D u_Texture;
        uniform vec2 u_TextureSize;

        void main() {
            vec4 color = texture2D(u_Texture, v_TexCoord);
            vec2 pixelCoord = v_TexCoord * u_TextureSize;
            vec2 subCoord = fract(pixelCoord);

            // Subpixel vertical RGB stripes
            vec3 subpixelMask;
            float subX = subCoord.x * 3.0;
            if (subX < 1.0) {
                subpixelMask = vec3(1.05, 0.75, 0.75);
            } else if (subX < 2.0) {
                subpixelMask = vec3(0.75, 1.05, 0.75);
            } else {
                subpixelMask = vec3(0.75, 0.75, 1.05);
            }

            // Dark Physical Grid Gaps around each pixel cell
            float borderX = step(0.08, subCoord.x) * step(subCoord.x, 0.92);
            float borderY = step(0.08, subCoord.y) * step(subCoord.y, 0.92);
            float gridGap = mix(0.75, 1.0, borderX * borderY);

            vec3 finalRgb = color.rgb * subpixelMask * gridGap;
            gl_FragColor = vec4(clamp(finalRgb, 0.0, 1.0), 1.0);
        }
    """

    // 4. Vibrant Color Boost (GBA Non-Backlit Screen Restoration)
    const val FRAGMENT_COLOR_BOOST_SRC = """
        precision mediump float;
        varying vec2 v_TexCoord;
        uniform sampler2D u_Texture;

        vec3 applyGbaColorMatrix(vec3 color) {
            vec3 gammaColor = pow(color, vec3(1.2));
            mat3 gbaMatrix = mat3(
                0.86, 0.07, 0.07,
                0.10, 0.82, 0.12,
                0.04, 0.11, 0.81
            );
            return clamp(gbaMatrix * gammaColor, 0.0, 1.0);
        }

        void main() {
            vec4 col = texture2D(u_Texture, v_TexCoord);
            vec3 boosted = applyGbaColorMatrix(col.rgb);
            gl_FragColor = vec4(boosted, 1.0);
        }
    """

    // 5. Sharp Bilinear Filtering (Zero Shimmering Pixel-Art Scaling)
    const val FRAGMENT_SHARP_BILINEAR_SRC = """
        precision mediump float;
        varying vec2 v_TexCoord;
        uniform sampler2D u_Texture;
        uniform vec2 u_TextureSize;

        void main() {
            vec2 texCoord = v_TexCoord * u_TextureSize - 0.5;
            vec2 f = fract(texCoord);
            vec2 snap = clamp(f * 2.0 - 0.5, 0.0, 1.0);
            vec2 uv = (floor(texCoord) + 0.5 + snap) / u_TextureSize;

            gl_FragColor = vec4(texture2D(u_Texture, uv).rgb, 1.0);
        }
    """

    // 6. 1989 Authentic Pea-Green Game Boy Monochrome Palette LUT
    const val FRAGMENT_DMG_PEA_GREEN_SRC = """
        precision mediump float;
        varying vec2 v_TexCoord;
        uniform sampler2D u_Texture;

        void main() {
            vec4 color = texture2D(u_Texture, v_TexCoord);
            // Compute ITU-R BT.601 perceptual luminance
            float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));

            // 4-Shade Authentic DMG-01 Pea-Green Color Palette
            vec3 c0 = vec3(0.0588, 0.2196, 0.0588); // #0f380f Darkest
            vec3 c1 = vec3(0.1882, 0.3843, 0.1882); // #306230 Dark
            vec3 c2 = vec3(0.5255, 0.7490, 0.0588); // #8bac0f Light
            vec3 c3 = vec3(0.6078, 0.7373, 0.0588); // #9bbc0f Lightest

            vec3 greenRgb;
            if (lum < 0.25) {
                greenRgb = c0;
            } else if (lum < 0.50) {
                greenRgb = c1;
            } else if (lum < 0.75) {
                greenRgb = c2;
            } else {
                greenRgb = c3;
            }

            gl_FragColor = vec4(greenRgb, 1.0);
        }
    """

    private val QUAD_VERTICES = floatArrayOf(
        -1.0f,  1.0f,
        -1.0f, -1.0f,
         1.0f,  1.0f,
         1.0f, -1.0f
    )

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

    fun getFragmentShaderSourceForMode(mode: ShaderMode): String {
        return when (mode) {
            ShaderMode.NONE -> FRAGMENT_PASSTHROUGH_SRC
            ShaderMode.CRT_SCANLINES -> FRAGMENT_CRT_SCANLINES_SRC
            ShaderMode.LCD_DOTMATRIX -> FRAGMENT_LCD_DOTMATRIX_SRC
            ShaderMode.COLOR_BOOST -> FRAGMENT_COLOR_BOOST_SRC
            ShaderMode.SHARP_BILINEAR -> FRAGMENT_SHARP_BILINEAR_SRC
            ShaderMode.DMG_PEA_GREEN -> FRAGMENT_DMG_PEA_GREEN_SRC
        }
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

    fun createProgram(vertexSrc: String = VERTEX_SHADER_SRC, fragmentSrc: String = FRAGMENT_PASSTHROUGH_SRC): Int {
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
