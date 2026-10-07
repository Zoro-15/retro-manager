#ifndef RETROPACK_GLES_RENDERER_HPP
#define RETROPACK_GLES_RENDERER_HPP

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>

#include <cstdint>
#include <cstddef>
#include <vector>
#include <mutex>

#include "core/libretro.h"

namespace retropack {

/**
 * ViewportRect defines the integer bounding box for aspect-ratio
 * preserved letterboxed/pillarboxed rendering.
 */
struct ViewportRect {
    int x{0};
    int y{0};
    int width{0};
    int height{0};
};

/**
 * GlesRenderer manages the EGL lifecycle on ANativeWindow and executes
 * a high-performance OpenGL ES 2.0 texture quad blitter supporting
 * multiple Libretro pixel formats (RGB565, 0RGB1555, XRGB8888) with
 * aspect-ratio preserving viewport letterboxing.
 */
class GlesRenderer {
public:
    GlesRenderer();
    ~GlesRenderer();

    // Non-copyable and non-movable
    GlesRenderer(const GlesRenderer&) = delete;
    GlesRenderer& operator=(const GlesRenderer&) = delete;

    /**
     * Initialize EGL display, context, and surface attached to ANativeWindow.
     * @param window Pointer to ANativeWindow from android_app.
     * @return true if EGL context and GL shaders were initialized.
     */
    bool initEGL(ANativeWindow* window);

    /**
     * Tear down EGL surface and context.
     */
    void terminateEGL();

    /**
     * Set the desired aspect ratio (e.g. 1.5 for GBA 3:2, 1.3333 for SNES 4:3).
     * @param aspect Desired width/height aspect ratio.
     */
    void setTargetAspectRatio(float aspect);

    /**
     * Update the active video texture with new frame bytes from retro_video_refresh.
     * @param data Raw frame buffer pointer.
     * @param width Frame width in pixels.
     * @param height Frame height in pixels.
     * @param pitch Bytes per line of video buffer.
     * @param format Libretro pixel format.
     */
    void updateFrame(
        const void* data,
        unsigned width,
        unsigned height,
        size_t pitch,
        enum retro_pixel_format format
    );

    /**
     * Draw the textured video quad to the active framebuffer.
     */
    void renderFrame();

    /**
     * Swap EGL back buffer to the display surface.
     */
    void present();

    /**
     * Status queries.
     */
    bool isInitialized() const { return m_initialized; }
    int getScreenWidth() const { return m_screenWidth; }
    int getScreenHeight() const { return m_screenHeight; }
    const ViewportRect& getViewport() const { return m_viewport; }

private:
    bool initGL();
    void updateViewport();
    GLuint createShader(GLenum type, const char* source);
    GLuint createProgram(const char* vertexSource, const char* fragmentSource);

    mutable std::mutex m_mutex;
    bool m_initialized{false};

    EGLDisplay m_display{EGL_NO_DISPLAY};
    EGLSurface m_surface{EGL_NO_SURFACE};
    EGLContext m_context{EGL_NO_CONTEXT};
    EGLConfig m_config{nullptr};
    ANativeWindow* m_window{nullptr};

    int m_screenWidth{0};
    int m_screenHeight{0};
    float m_targetAspectRatio{4.0f / 3.0f};
    ViewportRect m_viewport{};

    GLuint m_program{0};
    GLint m_locPosition{-1};
    GLint m_locTexCoord{-1};
    GLint m_locSampler{-1};

    GLuint m_textureId{0};
    unsigned m_texWidth{0};
    unsigned m_texHeight{0};
    enum retro_pixel_format m_pixelFormat{RETRO_PIXEL_FORMAT_RGB565};

    // Staging buffer for pitch/stride conversions or pixel conversions
    std::vector<uint8_t> m_conversionBuffer;
};

} // namespace retropack

#endif // RETROPACK_GLES_RENDERER_HPP
