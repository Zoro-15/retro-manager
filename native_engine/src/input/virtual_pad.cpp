#include "virtual_pad.hpp"
#include "ui/font_renderer.hpp"

#include <android/log.h>
#define _USE_MATH_DEFINES
#include <cmath>
#include <algorithm>
#include <cstring>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#include "common/logger.hpp"

#define LOG_TAG "RetroEngine-Pad"

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

static bool hitTestCircle(float px, float py, float cx, float cy, float radius) {
    float dx = px - cx;
    float dy = py - cy;
    return (dx * dx + dy * dy) <= (radius * radius);
}

static bool hitTestRect(float px, float py, float rx, float ry, float rw, float rh) {
    return (px >= rx && px <= rx + rw && py >= ry && py <= ry + rh);
}

} // namespace

VirtualPad::VirtualPad() {
    for (size_t i = 0; i < MAX_TOUCH_POINTERS; ++i) {
        m_pointers[i].id = -1;
        m_pointers[i].active = false;
    }
    setConsoleLayout(ConsoleLayout::GBA);
}

VirtualPad::~VirtualPad() {
    if (m_program != 0) {
        glDeleteProgram(m_program);
        m_program = 0;
    }
}

void VirtualPad::setConsoleLayout(ConsoleLayout layout) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_consoleLayout = layout;

    switch (layout) {
        case ConsoleLayout::GBA:
            m_hasX = false;
            m_hasY = false;
            m_hasShoulders = true;
            break;

        case ConsoleLayout::NES:
        case ConsoleLayout::PCE:
            m_hasX = false;
            m_hasY = false;
            m_hasShoulders = false;
            break;

        case ConsoleLayout::SNES:
        case ConsoleLayout::GENESIS:
        default:
            m_hasX = true;
            m_hasY = true;
            m_hasShoulders = true;
            break;
    }

    if (m_screenWidth > 0 && m_screenHeight > 0) {
        updateLayout(m_screenWidth, m_screenHeight);
    }
}

void VirtualPad::updateLayout(int screenWidth, int screenHeight) {
    m_screenWidth = screenWidth;
    m_screenHeight = screenHeight;

    if (screenWidth <= 0 || screenHeight <= 0) return;

    float base = static_cast<float>(std::min(screenWidth, screenHeight));
    float scale = base / 720.0f;
    if (scale < 0.65f) scale = 0.65f;

    float w = static_cast<float>(screenWidth);
    float h = static_cast<float>(screenHeight);

    // 1. PPSSPP Floating Thumbstick Dimensions
    m_stickOuterRadius = 78.0f * scale;
    m_stickNubRadius = 34.0f * scale;
    m_stickMaxDist = 65.0f * scale;
    m_stickDeadzone = 12.0f * scale;

    m_defaultStickX = 180.0f * scale;
    m_defaultStickY = h - (180.0f * scale);

    if (!m_stickActive) {
        m_stickBaseX = m_defaultStickX;
        m_stickBaseY = m_defaultStickY;
        m_stickNubX = m_defaultStickX;
        m_stickNubY = m_defaultStickY;
    }

    // 2. Action Buttons
    float btnRadius = 40.0f * scale;
    float rightCenterX = w - (180.0f * scale);
    float rightCenterY = h - (180.0f * scale);

    if (m_consoleLayout == ConsoleLayout::GBA) {
        // GBA: Natural 25-degree thumb sweep arc
        // A is top-right, B is bottom-left
        float angleRad = 25.0f * (static_cast<float>(M_PI) / 180.0f);
        float spacing = 58.0f * scale;

        m_btnA.x = rightCenterX + (std::cos(angleRad) * spacing);
        m_btnA.y = rightCenterY - (std::sin(angleRad) * spacing);
        m_btnA.radius = btnRadius * 1.08f;
        m_btnA.mask = BTN_A;
        m_btnA.label = "A";
        m_btnA.enabled = true;

        m_btnB.x = rightCenterX - (std::cos(angleRad) * spacing);
        m_btnB.y = rightCenterY + (std::sin(angleRad) * spacing);
        m_btnB.radius = btnRadius * 1.08f;
        m_btnB.mask = BTN_B;
        m_btnB.label = "B";
        m_btnB.enabled = true;

        m_btnX.enabled = false;
        m_btnY.enabled = false;
    } else if (m_consoleLayout == ConsoleLayout::NES || m_consoleLayout == ConsoleLayout::PCE) {
        // NES / PCE: Horizontal 2-button layout
        float spacing = 52.0f * scale;

        m_btnB.x = rightCenterX - spacing;
        m_btnB.y = rightCenterY;
        m_btnB.radius = btnRadius;
        m_btnB.mask = BTN_B;
        m_btnB.label = (m_consoleLayout == ConsoleLayout::PCE) ? "I" : "B";
        m_btnB.enabled = true;

        m_btnA.x = rightCenterX + spacing;
        m_btnA.y = rightCenterY;
        m_btnA.radius = btnRadius;
        m_btnA.mask = BTN_A;
        m_btnA.label = (m_consoleLayout == ConsoleLayout::PCE) ? "II" : "A";
        m_btnA.enabled = true;

        m_btnX.enabled = false;
        m_btnY.enabled = false;
    } else {
        // SNES / Genesis: 4-Button Diamond Cluster
        float diamondDist = 65.0f * scale;

        m_btnA.x = rightCenterX + diamondDist;
        m_btnA.y = rightCenterY;
        m_btnA.radius = btnRadius;
        m_btnA.mask = BTN_A;
        m_btnA.label = "A";
        m_btnA.enabled = true;

        m_btnB.x = rightCenterX;
        m_btnB.y = rightCenterY + diamondDist;
        m_btnB.radius = btnRadius;
        m_btnB.mask = BTN_B;
        m_btnB.label = "B";
        m_btnB.enabled = true;

        m_btnX.x = rightCenterX;
        m_btnX.y = rightCenterY - diamondDist;
        m_btnX.radius = btnRadius;
        m_btnX.mask = BTN_X;
        m_btnX.label = "X";
        m_btnX.enabled = true;

        m_btnY.x = rightCenterX - diamondDist;
        m_btnY.y = rightCenterY;
        m_btnY.radius = btnRadius;
        m_btnY.mask = BTN_Y;
        m_btnY.label = "Y";
        m_btnY.enabled = true;
    }

    // 3. Shoulder Buttons (Top Corners)
    m_btnL.x = 24.0f * scale;
    m_btnL.y = 16.0f * scale;
    m_btnL.width = 145.0f * scale;
    m_btnL.height = 54.0f * scale;
    m_btnL.mask = BTN_L;
    m_btnL.label = "L";
    m_btnL.enabled = m_hasShoulders;

    m_btnR.x = w - (169.0f * scale);
    m_btnR.y = 16.0f * scale;
    m_btnR.width = 145.0f * scale;
    m_btnR.height = 54.0f * scale;
    m_btnR.mask = BTN_R;
    m_btnR.label = "R";
    m_btnR.enabled = m_hasShoulders;

    // 4. Utility Buttons (Bottom-Center)
    m_btnSelect.x = (w * 0.5f) - (110.0f * scale);
    m_btnSelect.y = h - (52.0f * scale);
    m_btnSelect.width = 92.0f * scale;
    m_btnSelect.height = 38.0f * scale;
    m_btnSelect.mask = BTN_SELECT;
    m_btnSelect.label = "SELECT";
    m_btnSelect.enabled = true;

    m_btnStart.x = (w * 0.5f) + (18.0f * scale);
    m_btnStart.y = h - (52.0f * scale);
    m_btnStart.width = 92.0f * scale;
    m_btnStart.height = 38.0f * scale;
    m_btnStart.mask = BTN_START;
    m_btnStart.label = "START";
    m_btnStart.enabled = true;

    // 5. In-Engine OSD Menu Trigger (Top-Center)
    m_btnMenu.x = (w * 0.5f) - (65.0f * scale);
    m_btnMenu.y = 14.0f * scale;
    m_btnMenu.width = 130.0f * scale;
    m_btnMenu.height = 42.0f * scale;
    m_btnMenu.mask = BTN_MENU;
    m_btnMenu.label = "MENU";
    m_btnMenu.enabled = true;

    LOGI("VirtualPad layout configured for display %dx%d (scale: %.2f, layout: %d)",
         screenWidth, screenHeight, scale, static_cast<int>(m_consoleLayout));
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

    float leftZoneMaxX = static_cast<float>(m_screenWidth) * 0.45f;

    switch (actionMasked) {
        case AMOTION_EVENT_ACTION_DOWN:
        case AMOTION_EVENT_ACTION_POINTER_DOWN: {
            int pointerId = AMotionEvent_getPointerId(event, pointerIndex);
            float x = AMotionEvent_getX(event, pointerIndex);
            float y = AMotionEvent_getY(event, pointerIndex);

            // If touch lands in the left control zone, dynamically anchor the PPSSPP floating stick
            if (x < leftZoneMaxX && !m_stickActive) {
                m_stickActive = true;
                m_stickPointerId = pointerId;
                m_stickBaseX = x;
                m_stickBaseY = y;
                m_stickNubX = x;
                m_stickNubY = y;
            }

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

                // Update stick position if this pointer is controlling the floating thumbstick
                if (m_stickActive && pointerId == m_stickPointerId) {
                    float dx = x - m_stickBaseX;
                    float dy = y - m_stickBaseY;
                    float dist = std::sqrt(dx * dx + dy * dy);

                    if (dist > m_stickMaxDist && dist > 0.0f) {
                        dx = (dx / dist) * m_stickMaxDist;
                        dy = (dy / dist) * m_stickMaxDist;
                    }

                    m_stickNubX = m_stickBaseX + dx;
                    m_stickNubY = m_stickBaseY + dy;
                }

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
            if (m_stickActive && pointerId == m_stickPointerId) {
                m_stickActive = false;
                m_stickPointerId = -1;
                m_stickBaseX = m_defaultStickX;
                m_stickBaseY = m_defaultStickY;
                m_stickNubX = m_defaultStickX;
                m_stickNubY = m_defaultStickY;
            }

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
            m_stickActive = false;
            m_stickPointerId = -1;
            m_stickBaseX = m_defaultStickX;
            m_stickBaseY = m_defaultStickY;
            m_stickNubX = m_defaultStickX;
            m_stickNubY = m_defaultStickY;

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

void VirtualPad::recomputeBitmask() {
    uint32_t mask = 0;

    // 1. PPSSPP Floating Thumbstick Directional Angle Mapping (360 -> 8-Way Digital)
    if (m_stickActive) {
        float dx = m_stickNubX - m_stickBaseX;
        float dy = m_stickNubY - m_stickBaseY;
        float dist = std::sqrt(dx * dx + dy * dy);

        if (dist > m_stickDeadzone) {
            float angleDeg = std::atan2(dy, dx) * (180.0f / static_cast<float>(M_PI)); // [-180, 180]

            if (angleDeg >= -22.5f && angleDeg <= 22.5f) {
                mask |= BTN_RIGHT;
            } else if (angleDeg > 22.5f && angleDeg < 67.5f) {
                mask |= (BTN_DOWN | BTN_RIGHT);
            } else if (angleDeg >= 67.5f && angleDeg <= 112.5f) {
                mask |= BTN_DOWN;
            } else if (angleDeg > 112.5f && angleDeg < 157.5f) {
                mask |= (BTN_DOWN | BTN_LEFT);
            } else if (angleDeg >= 157.5f || angleDeg <= -157.5f) {
                mask |= BTN_LEFT;
            } else if (angleDeg < -112.5f && angleDeg > -157.5f) {
                mask |= (BTN_UP | BTN_LEFT);
            } else if (angleDeg <= -67.5f && angleDeg >= -112.5f) {
                mask |= BTN_UP;
            } else if (angleDeg < -22.5f && angleDeg > -67.5f) {
                mask |= (BTN_UP | BTN_RIGHT);
            }
        }
    }

    // 2. Action & Utility Buttons Hit-Testing
    for (const auto& p : m_pointers) {
        if (!p.active) continue;
        if (p.id == m_stickPointerId) continue; // Skip stick controlling thumb

        // Action Buttons
        if (m_btnA.enabled && hitTestCircle(p.x, p.y, m_btnA.x, m_btnA.y, m_btnA.radius * 1.35f)) mask |= BTN_A;
        if (m_btnB.enabled && hitTestCircle(p.x, p.y, m_btnB.x, m_btnB.y, m_btnB.radius * 1.35f)) mask |= BTN_B;
        if (m_btnX.enabled && hitTestCircle(p.x, p.y, m_btnX.x, m_btnX.y, m_btnX.radius * 1.35f)) mask |= BTN_X;
        if (m_btnY.enabled && hitTestCircle(p.x, p.y, m_btnY.x, m_btnY.y, m_btnY.radius * 1.35f)) mask |= BTN_Y;

        // Shoulder Buttons
        if (m_btnL.enabled && hitTestRect(p.x, p.y, m_btnL.x, m_btnL.y, m_btnL.width, m_btnL.height)) mask |= BTN_L;
        if (m_btnR.enabled && hitTestRect(p.x, p.y, m_btnR.x, m_btnR.y, m_btnR.width, m_btnR.height)) mask |= BTN_R;

        // Utility Buttons
        if (m_btnSelect.enabled && hitTestRect(p.x, p.y, m_btnSelect.x, m_btnSelect.y, m_btnSelect.width, m_btnSelect.height)) mask |= BTN_SELECT;
        if (m_btnStart.enabled && hitTestRect(p.x, p.y, m_btnStart.x, m_btnStart.y, m_btnStart.width, m_btnStart.height)) mask |= BTN_START;

        // Menu Trigger
        if (m_btnMenu.enabled && hitTestRect(p.x, p.y, m_btnMenu.x, m_btnMenu.y, m_btnMenu.width, m_btnMenu.height)) {
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
    constexpr int SEGMENTS = 28;
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

void VirtualPad::renderRing(float cx, float cy, float innerRadius, float outerRadius, float r, float g, float b, float a) {
    constexpr int SEGMENTS = 32;
    GLfloat vertices[(SEGMENTS + 1) * 4];

    for (int i = 0; i <= SEGMENTS; ++i) {
        float angle = static_cast<float>(i) * (2.0f * static_cast<float>(M_PI) / SEGMENTS);
        float cosA = std::cos(angle);
        float sinA = std::sin(angle);

        vertices[i * 4 + 0] = cx + cosA * outerRadius;
        vertices[i * 4 + 1] = cy + sinA * outerRadius;

        vertices[i * 4 + 2] = cx + cosA * innerRadius;
        vertices[i * 4 + 3] = cy + sinA * innerRadius;
    }

    glUniform4f(m_locColor, r, g, b, a);
    glVertexAttribPointer(m_locPosition, 2, GL_FLOAT, GL_FALSE, 0, vertices);
    glEnableVertexAttribArray(m_locPosition);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, (SEGMENTS + 1) * 2);
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

void VirtualPad::renderBorderedRect(float x, float y, float w, float h, float borderWidth,
                                    float bgR, float bgG, float bgB, float bgA,
                                    float borderR, float borderG, float borderB, float borderA) {
    renderRect(x, y, w, h, bgR, bgG, bgB, bgA);
    renderRect(x, y, w, borderWidth, borderR, borderG, borderB, borderA);
    renderRect(x, y + h - borderWidth, w, borderWidth, borderR, borderG, borderB, borderA);
    renderRect(x, y, borderWidth, h, borderR, borderG, borderB, borderA);
    renderRect(x + w - borderWidth, y, borderWidth, h, borderR, borderG, borderB, borderA);
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

    float baseAlpha = m_opacity;                          // ~0.30f default
    float activeAlpha = std::min(1.0f, baseAlpha * 2.5f); // ~0.75f upon active touch

    // 1. PPSSPP Floating Thumbstick Rendering
    // 1a. Outer Base Disc & Glowing Ring
    renderCircle(m_stickBaseX, m_stickBaseY, m_stickOuterRadius, 0.08f, 0.12f, 0.18f, baseAlpha * 0.8f);
    renderRing(m_stickBaseX, m_stickBaseY, m_stickOuterRadius - 3.0f, m_stickOuterRadius,
               0.35f, 0.65f, 1.0f, m_stickActive ? activeAlpha : baseAlpha * 1.5f);

    // 1b. Directional Guide Ticks (Up, Down, Left, Right)
    float tickLen = 10.0f;
    renderRect(m_stickBaseX - 1.5f, m_stickBaseY - m_stickOuterRadius - tickLen, 3.0f, tickLen, 0.45f, 0.70f, 1.0f, baseAlpha * 1.2f);
    renderRect(m_stickBaseX - 1.5f, m_stickBaseY + m_stickOuterRadius, 3.0f, tickLen, 0.45f, 0.70f, 1.0f, baseAlpha * 1.2f);
    renderRect(m_stickBaseX - m_stickOuterRadius - tickLen, m_stickBaseY - 1.5f, tickLen, 3.0f, 0.45f, 0.70f, 1.0f, baseAlpha * 1.2f);
    renderRect(m_stickBaseX + m_stickOuterRadius, m_stickBaseY - 1.5f, tickLen, 3.0f, 0.45f, 0.70f, 1.0f, baseAlpha * 1.2f);

    // 1c. Inner Draggable Nub
    renderCircle(m_stickNubX, m_stickNubY, m_stickNubRadius, 0.20f, 0.35f, 0.60f, m_stickActive ? activeAlpha : baseAlpha * 1.5f);
    renderRing(m_stickNubX, m_stickNubY, m_stickNubRadius - 2.5f, m_stickNubRadius, 0.60f, 0.85f, 1.0f, m_stickActive ? activeAlpha : baseAlpha * 1.8f);
    renderCircle(m_stickNubX, m_stickNubY, m_stickNubRadius * 0.35f, 0.40f, 0.65f, 0.95f, m_stickActive ? activeAlpha : baseAlpha * 1.2f);

    // 2. Action Buttons (A, B, X, Y)
    bool aActive = (m_activeBitmask & BTN_A) != 0;
    bool bActive = (m_activeBitmask & BTN_B) != 0;
    bool xActive = (m_activeBitmask & BTN_X) != 0;
    bool yActive = (m_activeBitmask & BTN_Y) != 0;

    // Button A (Coral Red Accent)
    if (m_btnA.enabled) {
        float bgAlpha = aActive ? activeAlpha * 0.85f : baseAlpha * 1.2f;
        renderCircle(m_btnA.x, m_btnA.y, m_btnA.radius, aActive ? 0.90f : 0.09f, aActive ? 0.20f : 0.12f, aActive ? 0.30f : 0.18f, bgAlpha);
        renderRing(m_btnA.x, m_btnA.y, m_btnA.radius - 3.0f, m_btnA.radius, 1.0f, 0.35f, 0.48f, aActive ? activeAlpha : baseAlpha * 1.8f);
    }

    // Button B (Warm Amber Accent)
    if (m_btnB.enabled) {
        float bgAlpha = bActive ? activeAlpha * 0.85f : baseAlpha * 1.2f;
        renderCircle(m_btnB.x, m_btnB.y, m_btnB.radius, bActive ? 0.90f : 0.09f, bActive ? 0.60f : 0.12f, bActive ? 0.15f : 0.18f, bgAlpha);
        renderRing(m_btnB.x, m_btnB.y, m_btnB.radius - 3.0f, m_btnB.radius, 0.95f, 0.70f, 0.25f, bActive ? activeAlpha : baseAlpha * 1.8f);
    }

    // Button X (Sky Blue Accent)
    if (m_btnX.enabled) {
        float bgAlpha = xActive ? activeAlpha * 0.85f : baseAlpha * 1.2f;
        renderCircle(m_btnX.x, m_btnX.y, m_btnX.radius, xActive ? 0.20f : 0.09f, xActive ? 0.50f : 0.12f, xActive ? 0.90f : 0.18f, bgAlpha);
        renderRing(m_btnX.x, m_btnX.y, m_btnX.radius - 3.0f, m_btnX.radius, 0.30f, 0.65f, 1.0f, xActive ? activeAlpha : baseAlpha * 1.8f);
    }

    // Button Y (Emerald Green Accent)
    if (m_btnY.enabled) {
        float bgAlpha = yActive ? activeAlpha * 0.85f : baseAlpha * 1.2f;
        renderCircle(m_btnY.x, m_btnY.y, m_btnY.radius, yActive ? 0.20f : 0.09f, yActive ? 0.75f : 0.12f, yActive ? 0.40f : 0.18f, bgAlpha);
        renderRing(m_btnY.x, m_btnY.y, m_btnY.radius - 3.0f, m_btnY.radius, 0.35f, 0.85f, 0.55f, yActive ? activeAlpha : baseAlpha * 1.8f);
    }

    // 3. Shoulder Buttons (L, R)
    bool lActive = false;
    bool rActive = false;
    if (m_hasShoulders) {
        lActive = (m_activeBitmask & BTN_L) != 0;
        rActive = (m_activeBitmask & BTN_R) != 0;

        renderBorderedRect(m_btnL.x, m_btnL.y, m_btnL.width, m_btnL.height, 2.0f,
                           lActive ? 0.25f : 0.08f, lActive ? 0.40f : 0.12f, lActive ? 0.65f : 0.18f, lActive ? activeAlpha : baseAlpha * 1.2f,
                           0.40f, 0.65f, 0.95f, lActive ? activeAlpha : baseAlpha * 1.8f);

        renderBorderedRect(m_btnR.x, m_btnR.y, m_btnR.width, m_btnR.height, 2.0f,
                           rActive ? 0.25f : 0.08f, rActive ? 0.40f : 0.12f, rActive ? 0.65f : 0.18f, rActive ? activeAlpha : baseAlpha * 1.2f,
                           0.40f, 0.65f, 0.95f, rActive ? activeAlpha : baseAlpha * 1.8f);
    }

    // 4. Utility Buttons (Select, Start)
    bool selActive = (m_activeBitmask & BTN_SELECT) != 0;
    bool staActive = (m_activeBitmask & BTN_START) != 0;

    renderBorderedRect(m_btnSelect.x, m_btnSelect.y, m_btnSelect.width, m_btnSelect.height, 1.5f,
                       selActive ? 0.25f : 0.08f, selActive ? 0.35f : 0.12f, selActive ? 0.55f : 0.18f, selActive ? activeAlpha : baseAlpha * 1.2f,
                       0.40f, 0.55f, 0.75f, selActive ? activeAlpha : baseAlpha * 1.6f);

    renderBorderedRect(m_btnStart.x, m_btnStart.y, m_btnStart.width, m_btnStart.height, 1.5f,
                       staActive ? 0.25f : 0.08f, staActive ? 0.35f : 0.12f, staActive ? 0.55f : 0.18f, staActive ? activeAlpha : baseAlpha * 1.2f,
                       0.40f, 0.55f, 0.75f, staActive ? activeAlpha : baseAlpha * 1.6f);

    // 5. Menu Trigger Button
    renderBorderedRect(m_btnMenu.x, m_btnMenu.y, m_btnMenu.width, m_btnMenu.height, 1.5f,
                       0.10f, 0.20f, 0.35f, baseAlpha * 1.4f,
                       0.35f, 0.70f, 1.0f, baseAlpha * 1.9f);

    glDisable(GL_BLEND);

    // 6. Draw Crisp Button Glyphs & Text Labels using FontRenderer
    FontRenderer& font = FontRenderer::instance();
    float actionFontScale = std::max(2.8f, (m_btnA.radius * 0.7f) / 8.0f);

    if (m_btnA.enabled) {
        font.renderText(m_btnA.label, m_btnA.x, m_btnA.y, actionFontScale,
                        1.0f, 1.0f, 1.0f, aActive ? 1.0f : 0.85f, screenWidth, screenHeight, true, true);
    }

    if (m_btnB.enabled) {
        font.renderText(m_btnB.label, m_btnB.x, m_btnB.y, actionFontScale,
                        1.0f, 1.0f, 1.0f, bActive ? 1.0f : 0.85f, screenWidth, screenHeight, true, true);
    }

    if (m_btnX.enabled) {
        font.renderText(m_btnX.label, m_btnX.x, m_btnX.y, actionFontScale,
                        1.0f, 1.0f, 1.0f, xActive ? 1.0f : 0.85f, screenWidth, screenHeight, true, true);
    }

    if (m_btnY.enabled) {
        font.renderText(m_btnY.label, m_btnY.x, m_btnY.y, actionFontScale,
                        1.0f, 1.0f, 1.0f, yActive ? 1.0f : 0.85f, screenWidth, screenHeight, true, true);
    }

    if (m_hasShoulders) {
        font.renderText("L", m_btnL.x + m_btnL.width * 0.5f, m_btnL.y + m_btnL.height * 0.5f, 2.6f,
                        1.0f, 1.0f, 1.0f, lActive ? 1.0f : 0.85f, screenWidth, screenHeight, true, true);

        font.renderText("R", m_btnR.x + m_btnR.width * 0.5f, m_btnR.y + m_btnR.height * 0.5f, 2.6f,
                        1.0f, 1.0f, 1.0f, rActive ? 1.0f : 0.85f, screenWidth, screenHeight, true, true);
    }

    font.renderText("SELECT", m_btnSelect.x + m_btnSelect.width * 0.5f, m_btnSelect.y + m_btnSelect.height * 0.5f, 1.7f,
                    0.85f, 0.92f, 1.0f, selActive ? 1.0f : 0.75f, screenWidth, screenHeight, true, true);

    font.renderText("START", m_btnStart.x + m_btnStart.width * 0.5f, m_btnStart.y + m_btnStart.height * 0.5f, 1.7f,
                    0.85f, 0.92f, 1.0f, staActive ? 1.0f : 0.75f, screenWidth, screenHeight, true, true);

    font.renderText("MENU", m_btnMenu.x + m_btnMenu.width * 0.5f, m_btnMenu.y + m_btnMenu.height * 0.5f, 1.8f,
                    0.50f, 0.85f, 1.0f, 0.90f, screenWidth, screenHeight, true, true);
}

} // namespace retropack

