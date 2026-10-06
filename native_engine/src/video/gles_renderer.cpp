#include "gles_renderer.hpp"

#include <android/log.h>
#include <cmath>
#include <cstring>

#define LOG_TAG "RetroEngine-Gles"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace retropack {

namespace {

const char* kVertexShader =
    "attribute vec2 aPosition;\n"
    "attribute vec2 aTexCoord;\n"
    "varying vec2 vTexCoord;\n"
    "void main() {\n"
    "    gl_Position = vec4(aPosition, 0.0, 1.0);\n"
    "    vTexCoord = aTexCoord;\n"
    "}\n";

const char* kFragmentShader =
    "precision mediump float;\n"
    "varying vec2 vTexCoord;\n"
    "uniform sampler2D uTexture;\n"
    "void main() {\n"
    "    gl_FragColor = texture2D(uTexture, vTexCoord);\n"
    "}\n";

// Fullscreen textured quad vertices
const GLfloat kQuadVertices[] = {
    // aPosition (X, Y), aTexCoord (U, V)
    -1.0f,  1.0f,  0.0f, 0.0f, // Top-Left
    -1.0f, -1.0f,  0.0f, 1.0f, // Bottom-Left
     1.0f,  1.0f,  1.0f, 0.0f, // Top-Right
     1.0f, -1.0f,  1.0f, 1.0f  // Bottom-Right
};

} // namespace

GlesRenderer::GlesRenderer() = default;

GlesRenderer::~GlesRenderer() {
    terminateEGL();
}

bool GlesRenderer::initEGL(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(m_mutex);

    if (m_initialized) {
        terminateEGL();
    }

    if (!window) {
        LOGE("Cannot init EGL with null ANativeWindow");
        return false;
    }

    m_window = window;

    // 1. Initialize EGL Display
    m_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (m_display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed: 0x%x", eglGetError());
        return false;
    }

    if (!eglInitialize(m_display, nullptr, nullptr)) {
        LOGE("eglInitialize failed: 0x%x", eglGetError());
        return false;
    }

    // 2. Select OpenGL ES 2.0 Config
    const EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_BLUE_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_RED_SIZE, 8,
        EGL_DEPTH_SIZE, 0,
        EGL_NONE
    };

    EGLint numConfigs = 0;
    if (!eglChooseConfig(m_display, attribs, &m_config, 1, &numConfigs) || numConfigs <= 0) {
        LOGE("eglChooseConfig failed: 0x%x", eglGetError());
        terminateEGL();
        return false;
    }

    // 3. Set native window format to match EGL config
    EGLint format = 0;
    eglGetConfigAttrib(m_display, m_config, EGL_NATIVE_VISUAL_ID, &format);
    ANativeWindow_setBuffersGeometry(m_window, 0, 0, format);

    // 4. Create EGL Surface
    m_surface = eglCreateWindowSurface(m_display, m_config, m_window, nullptr);
    if (m_surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed: 0x%x", eglGetError());
        terminateEGL();
        return false;
    }

    // 5. Create EGL Context
    const EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };
    m_context = eglCreateContext(m_display, m_config, EGL_NO_CONTEXT, contextAttribs);
    if (m_context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed: 0x%x", eglGetError());
        terminateEGL();
        return false;
    }

    // 6. Bind context to surface
    if (!eglMakeCurrent(m_display, m_surface, m_surface, m_context)) {
        LOGE("eglMakeCurrent failed: 0x%x", eglGetError());
        terminateEGL();
        return false;
    }

    // Query actual screen geometry
    eglQuerySurface(m_display, m_surface, EGL_WIDTH, &m_screenWidth);
    eglQuerySurface(m_display, m_surface, EGL_HEIGHT, &m_screenHeight);

    // Enable VSync
    eglSwapInterval(m_display, 1);

    LOGI("EGL initialized successfully. Display resolution: %dx%d", m_screenWidth, m_screenHeight);

    if (!initGL()) {
        terminateEGL();
        return false;
    }

    updateViewport();
    m_initialized = true;
    return true;
}

void GlesRenderer::terminateEGL() {
    std::lock_guard<std::mutex> lock(m_mutex);

    if (m_textureId != 0) {
        glDeleteTextures(1, &m_textureId);
        m_textureId = 0;
    }

    if (m_program != 0) {
        glDeleteProgram(m_program);
        m_program = 0;
    }

    if (m_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(m_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (m_surface != EGL_NO_SURFACE) {
            eglDestroySurface(m_display, m_surface);
            m_surface = EGL_NO_SURFACE;
        }
        if (m_context != EGL_NO_CONTEXT) {
            eglDestroyContext(m_display, m_context);
            m_context = EGL_NO_CONTEXT;
        }
        eglTerminate(m_display);
        m_display = EGL_NO_DISPLAY;
    }

    m_initialized = false;
    m_texWidth = 0;
    m_texHeight = 0;
    LOGI("EGL terminated");
}

GLuint GlesRenderer::createShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    if (shader == 0) {
        LOGE("glCreateShader failed: 0x%x", glGetError());
        return 0;
    }

    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);

    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetShaderInfoLog(shader, infoLen, nullptr, infoLog.data());
            LOGE("Shader compilation error:\n%s", infoLog.data());
        }
        glDeleteShader(shader);
        return 0;
    }

    return shader;
}

GLuint GlesRenderer::createProgram(const char* vertexSource, const char* fragmentSource) {
    GLuint vertexShader = createShader(GL_VERTEX_SHADER, vertexSource);
    if (vertexShader == 0) return 0;

    GLuint fragmentShader = createShader(GL_FRAGMENT_SHADER, fragmentSource);
    if (fragmentShader == 0) {
        glDeleteShader(vertexShader);
        return 0;
    }

    GLuint program = glCreateProgram();
    if (program == 0) {
        LOGE("glCreateProgram failed: 0x%x", glGetError());
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        return 0;
    }

    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);
    glLinkProgram(program);

    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint infoLen = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetProgramInfoLog(program, infoLen, nullptr, infoLog.data());
            LOGE("Program linking error:\n%s", infoLog.data());
        }
        glDeleteProgram(program);
        program = 0;
    }

    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);
    return program;
}

bool GlesRenderer::initGL() {
    m_program = createProgram(kVertexShader, kFragmentShader);
    if (m_program == 0) {
        LOGE("Failed to build GL shader program");
        return false;
    }

    m_locPosition = glGetAttribLocation(m_program, "aPosition");
    m_locTexCoord = glGetAttribLocation(m_program, "aTexCoord");
    m_locSampler = glGetUniformLocation(m_program, "uTexture");

    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

    LOGI("OpenGL ES 2.0 shaders initialized");
    return true;
}

void GlesRenderer::setTargetAspectRatio(float aspect) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_targetAspectRatio = (aspect > 0.0f) ? aspect : (4.0f / 3.0f);
    updateViewport();
}

void GlesRenderer::updateViewport() {
    if (m_screenWidth <= 0 || m_screenHeight <= 0) return;

    float screenAspect = static_cast<float>(m_screenWidth) / static_cast<float>(m_screenHeight);
    float targetAspect = (m_targetAspectRatio > 0.0f) ? m_targetAspectRatio : (4.0f / 3.0f);

    if (screenAspect > targetAspect) {
        // Screen is wider than target: Pillarbox (black bars left and right)
        m_viewport.height = m_screenHeight;
        m_viewport.width = static_cast<int>(std::round(m_screenHeight * targetAspect));
        m_viewport.x = (m_screenWidth - m_viewport.width) / 2;
        m_viewport.y = 0;
    } else {
        // Screen is taller than target: Letterbox (black bars top and bottom)
        m_viewport.width = m_screenWidth;
        m_viewport.height = static_cast<int>(std::round(m_screenWidth / targetAspect));
        m_viewport.x = 0;
        m_viewport.y = (m_screenHeight - m_viewport.height) / 2;
    }

    LOGI("Viewport updated: %dx%d at (%d, %d) [Screen: %dx%d, Target Aspect: %.2f]",
         m_viewport.width, m_viewport.height, m_viewport.x, m_viewport.y,
         m_screenWidth, m_screenHeight, targetAspect);
}

void GlesRenderer::updateFrame(
    const void* data,
    unsigned width,
    unsigned height,
    size_t pitch,
    enum retro_pixel_format format
) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (!m_initialized || !data || width == 0 || height == 0) return;

    GLenum glFormat = GL_RGB;
    GLenum glType = GL_UNSIGNED_SHORT_5_6_5;
    size_t bytesPerPixel = 2;

    switch (format) {
        case RETRO_PIXEL_FORMAT_RGB565:
            glFormat = GL_RGB;
            glType = GL_UNSIGNED_SHORT_5_6_5;
            bytesPerPixel = 2;
            break;
        case RETRO_PIXEL_FORMAT_XRGB8888:
            glFormat = GL_RGBA;
            glType = GL_UNSIGNED_BYTE;
            bytesPerPixel = 4;
            break;
        case RETRO_PIXEL_FORMAT_0RGB1555:
            glFormat = GL_RGBA;
            glType = GL_UNSIGNED_SHORT_5_5_5_1;
            bytesPerPixel = 2;
            break;
        default:
            LOGE("Unsupported pixel format: %d", format);
            return;
    }

    const void* uploadData = data;
    size_t expectedPitch = width * bytesPerPixel;

    // Handle padded row stride if pitch != width * bpp
    if (pitch != expectedPitch) {
        size_t contiguousSize = width * height * bytesPerPixel;
        if (m_conversionBuffer.size() < contiguousSize) {
            m_conversionBuffer.resize(contiguousSize);
        }

        const auto* src = static_cast<const uint8_t*>(data);
        uint8_t* dst = m_conversionBuffer.data();

        for (unsigned y = 0; y < height; ++y) {
            std::memcpy(dst + (y * expectedPitch), src + (y * pitch), expectedPitch);
        }
        uploadData = m_conversionBuffer.data();
    }

    if (m_textureId == 0) {
        glGenTextures(1, &m_textureId);
        glBindTexture(GL_TEXTURE_2D, m_textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    } else {
        glBindTexture(GL_TEXTURE_2D, m_textureId);
    }

    // Reallocate texture memory only if dimensions or format changed
    if (m_texWidth != width || m_texHeight != height || m_pixelFormat != format) {
        m_texWidth = width;
        m_texHeight = height;
        m_pixelFormat = format;

        glTexImage2D(GL_TEXTURE_2D, 0, glFormat, width, height, 0, glFormat, glType, uploadData);
        LOGI("Allocated new GL texture: %ux%u format=%d", width, height, format);
    } else {
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, glFormat, glType, uploadData);
    }
}

void GlesRenderer::renderFrame() {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (!m_initialized || m_textureId == 0) return;

    // 1. Clear whole screen with black background
    glViewport(0, 0, m_screenWidth, m_screenHeight);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    // 2. Set aspect-ratio preserved letterboxed viewport for game quad
    glViewport(m_viewport.x, m_viewport.y, m_viewport.width, m_viewport.height);

    // 3. Render textured quad
    glUseProgram(m_program);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, m_textureId);
    glUniform1i(m_locSampler, 0);

    glEnableVertexAttribArray(m_locPosition);
    glVertexAttribPointer(m_locPosition, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), kQuadVertices);

    glEnableVertexAttribArray(m_locTexCoord);
    glVertexAttribPointer(m_locTexCoord, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), &kQuadVertices[2]);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(m_locPosition);
    glDisableVertexAttribArray(m_locTexCoord);

    // 4. Present frame to display
    eglSwapBuffers(m_display, m_surface);
}

} // namespace retropack
