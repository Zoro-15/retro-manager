#ifndef RETROPACK_FONT_RENDERER_HPP
#define RETROPACK_FONT_RENDERER_HPP

#include <GLES2/gl2.h>
#include <string>
#include <vector>

namespace retropack {

/**
 * Lightweight procedural vector bitmap font renderer for OpenGL ES 2.0.
 * Renders crisp, scalable text labels, button glyphs ("A", "B", "X", "Y", "L", "R"),
 * and in-engine OSD menus without external dependencies.
 */
class FontRenderer {
public:
    static FontRenderer& instance();

    ~FontRenderer();

    // Non-copyable and non-movable
    FontRenderer(const FontRenderer&) = delete;
    FontRenderer& operator=(const FontRenderer&) = delete;

    bool initGL();
    void terminateGL();

    /**
     * Render a text string onto the screen.
     * @param text String to render.
     * @param x Target X coordinate in screen pixels.
     * @param y Target Y coordinate in screen pixels.
     * @param scale Pixel height multiplier (e.g. 2.0f - 4.0f).
     * @param r Red [0.0, 1.0]
     * @param g Green [0.0, 1.0]
     * @param b Blue [0.0, 1.0]
     * @param a Alpha [0.0, 1.0]
     * @param screenW Current screen width.
     * @param screenH Current screen height.
     * @param centerX Center text horizontally around X.
     * @param centerY Center text vertically around Y.
     */
    void renderText(
        const std::string& text,
        float x,
        float y,
        float scale,
        float r,
        float g,
        float b,
        float a,
        int screenW,
        int screenH,
        bool centerX = false,
        bool centerY = false
    );

    float getTextWidth(const std::string& text, float scale) const;
    float getTextHeight(float scale) const;

private:
    FontRenderer();

    bool m_initialized{false};
    GLuint m_textureId{0};
    GLuint m_program{0};
    GLint m_locPosition{-1};
    GLint m_locTexCoord{-1};
    GLint m_locColor{-1};
    GLint m_locScreenSize{-1};
    GLint m_locSampler{-1};
};

} // namespace retropack

#endif // RETROPACK_FONT_RENDERER_HPP
