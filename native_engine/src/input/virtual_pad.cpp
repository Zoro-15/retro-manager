#include "virtual_pad.hpp"

#include <android/log.h>
#include <cmath>
#include <algorithm>
#include <cstring>

#define LOG_TAG "RetroEngine-Pad"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace retropack {

namespace {

const char* kPadVertexShader =
    "attribute vec2 aPosition;\n"
    "uniform vec2 uScreenSize;\n"
    "void main() {\n"
    "    vec2 zeroToOne = aPosition / uScreenSize;\n"
    "    vec2 zeroToTwo = zeroToOne * 2.0;\n"
    "    vec2 clipSpace = zeroToTwo - 1.0;\n"
    "    gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);\n"
    "}\n";

const char* kPadFragmentShader =
    "precision mediump float;\n"
    "uniform vec4 uColor;\n"
    "void main() {\n"
    "    gl_FragColor = uColor;\n"
    "}\n";

} // namespace

VirtualPad::VirtualPad() {
    for (size_t i = 0; i < MAX_TOUCH_POINTERS; ++i) {
        m_pointers[i].id = -1;
        m_pointers[i].active = false;
    }
}

VirtualPad::~VirtualPad() {
    if (m_program != 0) {
        glDeleteProgram(m_program);
        m_program = 0;
    }
}

void VirtualPad::updateLayout(int screenWidth, int screenHeight) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_screenWidth = screenWidth;
    m_screenHeight = screenHeight;

    if (screenWidth <= 0 || screenHeight <= 0) return;

    float base = static_cast<float>(std::min(screenWidth, screenHeight));
    float scale = base / 720.0f;
    if (scale < 0.6f) scale = 0.6f;

    float w = static_cast<float>(screenWidth);
    float h = static_cast<float>(screenHeight);

    // 1. D-Pad (Bottom-Left)
    m_dpadCenter.x = 180.0f * scale;
    m_dpadCenter.y = h - (180.0f * scale);
    m_dpadCenter.radius = 120.0f * scale;
    m_dpadCenter.label = "DPAD";

    // 2. Action Diamond Buttons (Bottom-Right)
    float btnRadius = 42.0f * scale;
    float diamondCenterDist = 72.0f * scale;
    float rightCenterX = w - (180.0f * scale);
    float rightCenterY = h - (180.0f * scale);

    m_btnA.x = rightCenterX + diamondCenterDist;
    m_btnA.y = rightCenterY;
    m_btnA.radius = btnRadius;
    m_btnA.mask = BTN_A;
    m_btnA.label = "A";

    m_btnB.x = rightCenterX;
    m_btnB.y = rightCenterY + diamondCenterDist;
    m_btnB.radius = btnRadius;
    m_btnB.mask = BTN_B;
    m_btnB.label = "B";

    m_btnX.x = rightCenterX;
    m_btnX.y = rightCenterY - diamondCenterDist;
    m_btnX.radius = btnRadius;
    m_btnX.mask = BTN_X;
    m_btnX.label = "X";

    m_btnY.x = rightCenterX - diamondCenterDist;
    m_btnY.y = rightCenterY;
    m_btnY.radius = btnRadius;
    m_btnY.mask = BTN_Y;
    m_btnY.label = "Y";

    // 3. Shoulder Buttons (Top Corners)
    m_btnL.x = 24.0f * scale;
    m_btnL.y = 20.0f * scale;
    m_btnL.width = 140.0f * scale;
    m_btnL.height = 55.0f * scale;
    m_btnL.mask = BTN_L;
    m_btnL.label = "L";

    m_btnR.x = w - (164.0f * scale);
    m_btnR.y = 20.0f * scale;
    m_btnR.width = 140.0f * scale;
    m_btnR.height = 55.0f * scale;
    m_btnR.mask = BTN_R;
    m_btnR.label = "R";

    // 4. Utility Buttons (Bottom-Center)
    m_btnSelect.x = (w * 0.5f) - (110.0f * scale);
    m_btnSelect.y = h - (55.0f * scale);
    m_btnSelect.width = 90.0f * scale;
    m_btnSelect.height = 40.0f * scale;
    m_btnSelect.mask = BTN_SELECT;
    m_btnSelect.label = "SELECT";

    m_btnStart.x = (w * 0.5f) + (20.0f * scale);
    m_btnStart.y = h - (55.0f * scale);
    m_btnStart.width = 90.0f * scale;
    m_btnStart.height = 40.0f * scale;
    m_btnStart.mask = BTN_START;
    m_btnStart.label = "START";

    // 5. In-Engine OSD Menu Trigger (Top-Center)
    m_btnMenu.x = (w * 0.5f) - (60.0f * scale);
    m_btnMenu.y = 15.0f * scale;
    m_btnMenu.width = 120.0f * scale;
    m_btnMenu.height = 45.0f * scale;
    m_btnMenu.mask = BTN_MENU;
    m_btnMenu.label = "MENU";

    LOGI("VirtualPad layout configured for display %dx%d (scale: %.2f)", screenWidth, screenHeight, scale);
}

int VirtualPad::handleInputEvent(const AInputEvent* event) {
    if (AInputEvent_getType(event) != AINPUT_EVENT_TYPE_MOTION) {
        return 0;
    }

    std::lock_guard<std::mutex> lock(m_mutex);
    updatePointers(event);
    recomputeBitmask();
    return 1;
}

void VirtualPad::updatePointers(const AInputEvent* event) {
    int32_t action = AMotionEvent_getAction(event);
    int32_t actionMasked = action & AMOTION_EVENT_ACTION_MASK;
    size_t pointerIndex = (action & AMOTION_EVENT_ACTION_POINTER_INDEX_MASK) >> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT;
    size_t pointerCount = AMotionEvent_getPointerCount(event);

    switch (actionMasked) {
        case AMOTION_EVENT_ACTION_DOWN:
        case AMOTION_EVENT_ACTION_POINTER_DOWN: {
            int pointerId = AMotionEvent_getPointerId(event, pointerIndex);
            float x = AMotionEvent_getX(event, pointerIndex);
            float y = AMotionEvent_getY(event, pointerIndex);

            for (auto& p : m_pointers) {
                if (!p.active) {
                    p.id = pointerId;
                    p.x = x;
                    p.y = y;
                    p.active = true;
                    break;
                }
            }
            break;
        }

        case AMOTION_EVENT_ACTION_MOVE: {
            for (size_t i = 0; i < pointerCount; ++i) {
                int pointerId = AMotionEvent_getPointerId(event, i);
                float x = AMotionEvent_getX(event, i);
                float y = AMotionEvent_getY(event, i);

                for (auto& p : m_pointers) {
                    if (p.active && p.id == pointerId) {
                        p.x = x;
                        p.y = y;
                        break;
                    }
                }
            }
            break;
        }

        case AMOTION_EVENT_ACTION_POINTER_UP: {
            int pointerId = AMotionEvent_getPointerId(event, pointerIndex);
            for (auto& p : m_pointers) {
                if (p.active && p.id == pointerId) {
                    p.active = false;
                    p.id = -1;
                    break;
                }
            }
            break;
        }

        case AMOTION_EVENT_ACTION_UP:
        case AMOTION_EVENT_ACTION_CANCEL: {
            for (auto& p : m_pointers) {
                p.active = false;
                p.id = -1;
            }
            break;
        }

        default:
            break;
    }
}

static bool hitTestCircle(float px, float py, float cx, float cy, float radius) {
    float dx = px - cx;
    float dy = py - cy;
    return (dx * dx + dy * dy) <= (radius * radius);
}

static bool hitTestRect(float px, float py, float rx, float ry, float rw, float rh) {
    return (px >= rx && px <= rx + rw && py >= ry && py <= ry + rh);
}

void VirtualPad::recomputeBitmask() {
    uint32_t mask = 0;

    for (const auto& p : m_pointers) {
        if (!p.active) continue;

        // 1. Test D-Pad
        float dx = p.x - m_dpadCenter.x;
        float dy = p.y - m_dpadCenter.y;
        float distSq = dx * dx + dy * dy;

        if (distSq <= (m_dpadCenter.radius * m_dpadCenter.radius)) {
            float deadzone = m_dpadCenter.radius * 0.22f;
            if (dy < -deadzone) mask |= BTN_UP;
            if (dy > deadzone)  mask |= BTN_DOWN;
            if (dx < -deadzone) mask |= BTN_LEFT;
            if (dx > deadzone)  mask |= BTN_RIGHT;
        }

        // 2. Test Action Buttons
        if (hitTestCircle(p.x, p.y, m_btnA.x, m_btnA.y, m_btnA.radius * 1.3f)) mask |= BTN_A;
        if (hitTestCircle(p.x, p.y, m_btnB.x, m_btnB.y, m_btnB.radius * 1.3f)) mask |= BTN_B;
        if (hitTestCircle(p.x, p.y, m_btnX.x, m_btnX.y, m_btnX.radius * 1.3f)) mask |= BTN_X;
        if (hitTestCircle(p.x, p.y, m_btnY.x, m_btnY.y, m_btnY.radius * 1.3f)) mask |= BTN_Y;

        // 3. Test Shoulders & Utilities
        if (hitTestRect(p.x, p.y, m_btnL.x, m_btnL.y, m_btnL.width, m_btnL.height)) mask |= BTN_L;
        if (hitTestRect(p.x, p.y, m_btnR.x, m_btnR.y, m_btnR.width, m_btnR.height)) mask |= BTN_R;
        if (hitTestRect(p.x, p.y, m_btnSelect.x, m_btnSelect.y, m_btnSelect.width, m_btnSelect.height)) mask |= BTN_SELECT;
        if (hitTestRect(p.x, p.y, m_btnStart.x, m_btnStart.y, m_btnStart.width, m_btnStart.height)) mask |= BTN_START;

        // 4. Test Menu Trigger
        if (hitTestRect(p.x, p.y, m_btnMenu.x, m_btnMenu.y, m_btnMenu.width, m_btnMenu.height)) {
            m_menuRequested = true;
        }
    }

    m_activeBitmask = mask;
}

void VirtualPad::poll() {
    // Polled each frame by Libretro core
}

int16_t VirtualPad::getInputState(unsigned port, unsigned device, unsigned /*index*/, unsigned id) const {
    if (port != 0 || device != RETRO_DEVICE_JOYPAD) {
        return 0;
    }

    std::lock_guard<std::mutex> lock(m_mutex);

    switch (id) {
        case RETRO_DEVICE_ID_JOYPAD_B:      return (m_activeBitmask & BTN_B) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_Y:      return (m_activeBitmask & BTN_Y) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_SELECT: return (m_activeBitmask & BTN_SELECT) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_START:  return (m_activeBitmask & BTN_START) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_UP:     return (m_activeBitmask & BTN_UP) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_DOWN:   return (m_activeBitmask & BTN_DOWN) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_LEFT:   return (m_activeBitmask & BTN_LEFT) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_RIGHT:  return (m_activeBitmask & BTN_RIGHT) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_A:      return (m_activeBitmask & BTN_A) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_X:      return (m_activeBitmask & BTN_X) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_L:      return (m_activeBitmask & BTN_L) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_R:      return (m_activeBitmask & BTN_R) ? 1 : 0;
        default: return 0;
    }
}

bool VirtualPad::consumeMenuRequest() {
    std::lock_guard<std::mutex> lock(m_mutex);
    bool req = m_menuRequested;
    m_menuRequested = false;
    return req;
}

bool VirtualPad::initGL() {
    if (m_glInitialized) return true;

    GLuint vs = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vs, 1, &kPadVertexShader, nullptr);
    glCompileShader(vs);

    GLuint fs = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fs, 1, &kPadFragmentShader, nullptr);
    glCompileShader(fs);

    m_program = glCreateProgram();
    glAttachShader(m_program, vs);
    glAttachShader(m_program, fs);
    glLinkProgram(m_program);

    glDeleteShader(vs);
    glDeleteShader(fs);

    m_locPosition = glGetAttribLocation(m_program, "aPosition");
    m_locColor = glGetUniformLocation(m_program, "uColor");
    m_locScreenSize = glGetUniformLocation(m_program, "uScreenSize");

    m_glInitialized = (m_program != 0);
    return m_glInitialized;
}

void VirtualPad::renderCircle(float cx, float cy, float radius, float r, float g, float b, float a) {
    constexpr int SEGMENTS = 24;
    GLfloat vertices[(SEGMENTS + 2) * 2];

    vertices[0] = cx;
    vertices[1] = cy;

    for (int i = 0; i <= SEGMENTS; ++i) {
        float angle = static_cast<float>(i) * (2.0f * static_cast<float>(M_PI) / SEGMENTS);
        vertices[(i + 1) * 2] = cx + std::cos(angle) * radius;
        vertices[(i + 1) * 2 + 1] = cy + std::sin(angle) * radius;
    }

    glUniform4f(m_locColor, r, g, b, a);
    glVertexAttribPointer(m_locPosition, 2, GL_FLOAT, GL_FALSE, 0, vertices);
    glEnableVertexAttribArray(m_locPosition);
    glDrawArrays(GL_TRIANGLE_FAN, 0, SEGMENTS + 2);
}

void VirtualPad::renderRect(float x, float y, float w, float h, float r, float g, float b, float a) {
    GLfloat vertices[8] = {
        x,     y,
        x,     y + h,
        x + w, y,
        x + w, y + h
    };

    glUniform4f(m_locColor, r, g, b, a);
    glVertexAttribPointer(m_locPosition, 2, GL_FLOAT, GL_FALSE, 0, vertices);
    glEnableVertexAttribArray(m_locPosition);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

void VirtualPad::render(int screenWidth, int screenHeight) {
    if (!m_visible || screenWidth <= 0 || screenHeight <= 0) return;

    std::lock_guard<std::mutex> lock(m_mutex);
    if (!initGL()) return;

    if (m_screenWidth != screenWidth || m_screenHeight != screenHeight) {
        updateLayout(screenWidth, screenHeight);
    }

    glViewport(0, 0, screenWidth, screenHeight);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    glUseProgram(m_program);
    glUniform2f(m_locScreenSize, static_cast<float>(screenWidth), static_cast<float>(screenHeight));

    float normalAlpha = m_opacity * 0.45f;
    float activeAlpha = m_opacity * 0.85f;

    // 1. D-Pad Base Circle
    renderCircle(m_dpadCenter.x, m_dpadCenter.y, m_dpadCenter.radius, 0.2f, 0.2f, 0.25f, normalAlpha);

    // D-Pad Directional Arrows
    float armDist = m_dpadCenter.radius * 0.55f;
    float arrowR = m_dpadCenter.radius * 0.32f;

    bool upActive = (m_activeBitmask & BTN_UP) != 0;
    bool downActive = (m_activeBitmask & BTN_DOWN) != 0;
    bool leftActive = (m_activeBitmask & BTN_LEFT) != 0;
    bool rightActive = (m_activeBitmask & BTN_RIGHT) != 0;

    renderCircle(m_dpadCenter.x, m_dpadCenter.y - armDist, arrowR, upActive ? 0.3f : 0.4f, upActive ? 0.8f : 0.4f, upActive ? 0.9f : 0.5f, upActive ? activeAlpha : normalAlpha);
    renderCircle(m_dpadCenter.x, m_dpadCenter.y + armDist, arrowR, downActive ? 0.3f : 0.4f, downActive ? 0.8f : 0.4f, downActive ? 0.9f : 0.5f, downActive ? activeAlpha : normalAlpha);
    renderCircle(m_dpadCenter.x - armDist, m_dpadCenter.y, arrowR, leftActive ? 0.3f : 0.4f, leftActive ? 0.8f : 0.4f, leftActive ? 0.9f : 0.5f, leftActive ? activeAlpha : normalAlpha);
    renderCircle(m_dpadCenter.x + armDist, m_dpadCenter.y, arrowR, rightActive ? 0.3f : 0.4f, rightActive ? 0.8f : 0.4f, rightActive ? 0.9f : 0.5f, rightActive ? activeAlpha : normalAlpha);

    // 2. Action Buttons (A, B, X, Y)
    bool aActive = (m_activeBitmask & BTN_A) != 0;
    bool bActive = (m_activeBitmask & BTN_B) != 0;
    bool xActive = (m_activeBitmask & BTN_X) != 0;
    bool yActive = (m_activeBitmask & BTN_Y) != 0;

    renderCircle(m_btnA.x, m_btnA.y, m_btnA.radius, aActive ? 0.9f : 0.8f, aActive ? 0.3f : 0.3f, aActive ? 0.3f : 0.3f, aActive ? activeAlpha : normalAlpha);
    renderCircle(m_btnB.x, m_btnB.y, m_btnB.radius, bActive ? 0.9f : 0.9f, bActive ? 0.7f : 0.6f, bActive ? 0.2f : 0.2f, bActive ? activeAlpha : normalAlpha);
    renderCircle(m_btnX.x, m_btnX.y, m_btnX.radius, xActive ? 0.3f : 0.3f, xActive ? 0.6f : 0.5f, xActive ? 0.9f : 0.8f, xActive ? activeAlpha : normalAlpha);
    renderCircle(m_btnY.x, m_btnY.y, m_btnY.radius, yActive ? 0.3f : 0.3f, yActive ? 0.8f : 0.7f, yActive ? 0.4f : 0.4f, yActive ? activeAlpha : normalAlpha);

    // 3. Shoulder Buttons (L, R)
    bool lActive = (m_activeBitmask & BTN_L) != 0;
    bool rActive = (m_activeBitmask & BTN_R) != 0;

    renderRect(m_btnL.x, m_btnL.y, m_btnL.width, m_btnL.height, 0.4f, 0.45f, 0.5f, lActive ? activeAlpha : normalAlpha);
    renderRect(m_btnR.x, m_btnR.y, m_btnR.width, m_btnR.height, 0.4f, 0.45f, 0.5f, rActive ? activeAlpha : normalAlpha);

    // 4. Utility Buttons (Select, Start)
    bool selActive = (m_activeBitmask & BTN_SELECT) != 0;
    bool staActive = (m_activeBitmask & BTN_START) != 0;

    renderRect(m_btnSelect.x, m_btnSelect.y, m_btnSelect.width, m_btnSelect.height, 0.35f, 0.35f, 0.4f, selActive ? activeAlpha : normalAlpha);
    renderRect(m_btnStart.x, m_btnStart.y, m_btnStart.width, m_btnStart.height, 0.35f, 0.35f, 0.4f, staActive ? activeAlpha : normalAlpha);

    // 5. Menu Trigger Button
    renderRect(m_btnMenu.x, m_btnMenu.y, m_btnMenu.width, m_btnMenu.height, 0.2f, 0.5f, 0.8f, normalAlpha);

    glDisable(GL_BLEND);
}

} // namespace retropack
