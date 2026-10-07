#include "virtual_pad.hpp"
#include "ui/font_renderer.hpp"

#include <android/log.h>
#define _USE_MATH_DEFINES
#include <cmath>
#include <algorithm>
#include <cstring>
#include <cstdio>
#include <fstream>
#include <sstream>

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

static int consoleToIndex(ConsoleLayout layout) {
    switch (layout) {
        case ConsoleLayout::GBA: return 0;
        case ConsoleLayout::SNES: return 1;
        case ConsoleLayout::NES: return 2;
        case ConsoleLayout::GENESIS: return 3;
        case ConsoleLayout::PCE: return 4;
        default: return 0;
    }
}

} // namespace

VirtualPad::VirtualPad() {
    for (size_t i = 0; i < MAX_TOUCH_POINTERS; ++i) {
        m_pointers[i].id = -1;
        m_pointers[i].active = false;
    }

    // Initialize all 10 Control Elements with baseline identities
    m_elements[ELEM_STICK_OR_DPAD] = { ELEM_STICK_OR_DPAD, "Left Control", "", 0, true, true, 0, 0, 1.0f, 0.35f, 0, 0, 0, 0, 0 };
    m_elements[ELEM_BTN_A]         = { ELEM_BTN_A, "Button A", "A", BTN_A, true, true, 0, 0, 1.0f, 0.35f, 0, 0, 0, 0, 0 };
    m_elements[ELEM_BTN_B]         = { ELEM_BTN_B, "Button B", "B", BTN_B, true, true, 0, 0, 1.0f, 0.35f, 0, 0, 0, 0, 0 };
    m_elements[ELEM_BTN_X]         = { ELEM_BTN_X, "Button X", "X", BTN_X, true, true, 0, 0, 1.0f, 0.35f, 0, 0, 0, 0, 0 };
    m_elements[ELEM_BTN_Y]         = { ELEM_BTN_Y, "Button Y", "Y", BTN_Y, true, true, 0, 0, 1.0f, 0.35f, 0, 0, 0, 0, 0 };
    m_elements[ELEM_BTN_L]         = { ELEM_BTN_L, "Button L", "L", BTN_L, false, true, 0, 0, 1.0f, 0.35f, 0, 0, 0, 0, 0 };
    m_elements[ELEM_BTN_R]         = { ELEM_BTN_R, "Button R", "R", BTN_R, false, true, 0, 0, 1.0f, 0.35f, 0, 0, 0, 0, 0 };
    m_elements[ELEM_BTN_SELECT]    = { ELEM_BTN_SELECT, "SELECT", "SELECT", BTN_SELECT, false, true, 0, 0, 1.0f, 0.35f, 0, 0, 0, 0, 0 };
    m_elements[ELEM_BTN_START]     = { ELEM_BTN_START, "START", "START", BTN_START, false, true, 0, 0, 1.0f, 0.35f, 0, 0, 0, 0, 0 };
    m_elements[ELEM_BTN_MENU]      = { ELEM_BTN_MENU, "MENU", "MENU", BTN_MENU, false, true, 0, 0, 1.0f, 0.40f, 0, 0, 0, 0, 0 };

    setConsoleLayout(ConsoleLayout::GBA);
}

VirtualPad::~VirtualPad() {
    if (m_program != 0) {
        glDeleteProgram(m_program);
        m_program = 0;
    }
}

void VirtualPad::initConfig(const std::string& configFilePath) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_configFilePath = configFilePath;
    loadCustomLayout();
}

void VirtualPad::setLeftInputMode(LeftInputMode mode) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_leftInputMode = mode;
    m_stickActive = false;
    m_elements[ELEM_STICK_OR_DPAD].name = (mode == LeftInputMode::DPAD) ? "D-Pad" : "Thumbstick";
    LOGI("VirtualPad left input mode set to: %s", (mode == LeftInputMode::DPAD) ? "D-PAD" : "JOYSTICK");
}

void VirtualPad::toggleLeftInputMode() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_leftInputMode = (m_leftInputMode == LeftInputMode::JOYSTICK) ? LeftInputMode::DPAD : LeftInputMode::JOYSTICK;
    m_stickActive = false;
    m_elements[ELEM_STICK_OR_DPAD].name = (m_leftInputMode == LeftInputMode::DPAD) ? "D-Pad" : "Thumbstick";
    LOGI("VirtualPad left input mode toggled to: %s", (m_leftInputMode == LeftInputMode::DPAD) ? "D-PAD" : "JOYSTICK");
    saveCustomLayout();
}

void VirtualPad::startCustomizing() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_isCustomizing = true;
    m_stickActive = false;
    m_selectedElement = 0;
    m_isDraggingElement = false;
    m_draggingOpacitySlider = false;
    m_draggingSizeSlider = false;
    m_hudToastMessage = "HUD CUSTOMIZER ACTIVE";
    m_hudToastFrames = 120;
    LOGI("VirtualPad custom HUD editor opened");
}

void VirtualPad::stopCustomizing() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_isCustomizing = false;
    m_isDraggingElement = false;
    m_draggingOpacitySlider = false;
    m_draggingSizeSlider = false;
    saveCustomLayout();
    LOGI("VirtualPad custom HUD editor closed & saved");
}

void VirtualPad::setActiveProfile(int profile) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (profile < 0 || profile > 1) profile = 0;
    
    // Save current elements to existing profile first
    int curLayoutIdx = consoleToIndex(m_consoleLayout);
    for (size_t i = 0; i < ELEM_COUNT; ++i) {
        m_profiles[m_activeProfile][curLayoutIdx][i] = {
            m_elements[i].normX,
            m_elements[i].normY,
            m_elements[i].scale,
            m_elements[i].opacity
        };
    }

    m_activeProfile = profile;

    // Load new profile for current layout
    for (size_t i = 0; i < ELEM_COUNT; ++i) {
        const auto& p = m_profiles[m_activeProfile][curLayoutIdx][i];
        if (p.scale > 0.1f && p.opacity > 0.05f) {
            m_elements[i].normX = p.normX;
            m_elements[i].normY = p.normY;
            m_elements[i].scale = p.scale;
            m_elements[i].opacity = p.opacity;
        }
    }

    if (m_screenWidth > 0 && m_screenHeight > 0) {
        for (size_t i = 0; i < ELEM_COUNT; ++i) {
            updateElementPixelMetrics(static_cast<int>(i));
        }
    }

    m_hudToastMessage = (profile == 0) ? "PROFILE 1 LOADED" : "PROFILE 2 LOADED";
    m_hudToastFrames = 90;
    saveCustomLayout();
}

void VirtualPad::setOpacity(float opacity) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_globalOpacity = std::clamp(opacity, 0.10f, 1.00f);
    for (auto& elem : m_elements) {
        elem.opacity = m_globalOpacity;
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
            m_elements[ELEM_BTN_A].label = "A";
            m_elements[ELEM_BTN_B].label = "B";
            break;

        case ConsoleLayout::NES:
            m_hasX = false;
            m_hasY = false;
            m_hasShoulders = false;
            m_elements[ELEM_BTN_A].label = "A";
            m_elements[ELEM_BTN_B].label = "B";
            break;

        case ConsoleLayout::PCE:
            m_hasX = false;
            m_hasY = false;
            m_hasShoulders = false;
            m_elements[ELEM_BTN_A].label = "II";
            m_elements[ELEM_BTN_B].label = "I";
            break;

        case ConsoleLayout::SNES:
        case ConsoleLayout::GENESIS:
        default:
            m_hasX = true;
            m_hasY = true;
            m_hasShoulders = true;
            m_elements[ELEM_BTN_A].label = "A";
            m_elements[ELEM_BTN_B].label = "B";
            m_elements[ELEM_BTN_X].label = "X";
            m_elements[ELEM_BTN_Y].label = "Y";
            break;
    }

    m_elements[ELEM_BTN_X].enabled = m_hasX;
    m_elements[ELEM_BTN_Y].enabled = m_hasY;
    m_elements[ELEM_BTN_L].enabled = m_hasShoulders;
    m_elements[ELEM_BTN_R].enabled = m_hasShoulders;
    m_elements[ELEM_BTN_A].enabled = true;
    m_elements[ELEM_BTN_B].enabled = true;
    m_elements[ELEM_BTN_SELECT].enabled = true;
    m_elements[ELEM_BTN_START].enabled = true;
    m_elements[ELEM_BTN_MENU].enabled = true;
    m_elements[ELEM_STICK_OR_DPAD].enabled = true;

    if (m_screenWidth > 0 && m_screenHeight > 0) {
        updateLayout(m_screenWidth, m_screenHeight);
    }
}

void VirtualPad::applyConsoleLayoutDefaults() {
    float w = static_cast<float>(m_screenWidth);
    float h = static_cast<float>(m_screenHeight);
    if (w <= 0.0f || h <= 0.0f) return;

    float scale = m_uiScale;

    // 1. Left Input (Thumbstick / D-Pad)
    float stickX = 180.0f * scale;
    float stickY = h - (180.0f * scale);
    m_elements[ELEM_STICK_OR_DPAD].normX = stickX / w;
    m_elements[ELEM_STICK_OR_DPAD].normY = stickY / h;
    m_elements[ELEM_STICK_OR_DPAD].scale = 1.0f;
    m_elements[ELEM_STICK_OR_DPAD].opacity = 0.35f;

    // 2. Action Buttons
    float rightCenterX = w - (180.0f * scale);
    float rightCenterY = h - (180.0f * scale);

    if (m_consoleLayout == ConsoleLayout::GBA) {
        float angleRad = 25.0f * (static_cast<float>(M_PI) / 180.0f);
        float spacing = 58.0f * scale;

        m_elements[ELEM_BTN_A].normX = (rightCenterX + (std::cos(angleRad) * spacing)) / w;
        m_elements[ELEM_BTN_A].normY = (rightCenterY - (std::sin(angleRad) * spacing)) / h;
        m_elements[ELEM_BTN_A].scale = 1.08f;
        m_elements[ELEM_BTN_A].opacity = 0.35f;

        m_elements[ELEM_BTN_B].normX = (rightCenterX - (std::cos(angleRad) * spacing)) / w;
        m_elements[ELEM_BTN_B].normY = (rightCenterY + (std::sin(angleRad) * spacing)) / h;
        m_elements[ELEM_BTN_B].scale = 1.08f;
        m_elements[ELEM_BTN_B].opacity = 0.35f;
    } else if (m_consoleLayout == ConsoleLayout::NES || m_consoleLayout == ConsoleLayout::PCE) {
        float spacing = 52.0f * scale;

        m_elements[ELEM_BTN_B].normX = (rightCenterX - spacing) / w;
        m_elements[ELEM_BTN_B].normY = rightCenterY / h;
        m_elements[ELEM_BTN_B].scale = 1.0f;
        m_elements[ELEM_BTN_B].opacity = 0.35f;

        m_elements[ELEM_BTN_A].normX = (rightCenterX + spacing) / w;
        m_elements[ELEM_BTN_A].normY = rightCenterY / h;
        m_elements[ELEM_BTN_A].scale = 1.0f;
        m_elements[ELEM_BTN_A].opacity = 0.35f;
    } else {
        float diamondDist = 65.0f * scale;

        m_elements[ELEM_BTN_A].normX = (rightCenterX + diamondDist) / w;
        m_elements[ELEM_BTN_A].normY = rightCenterY / h;
        m_elements[ELEM_BTN_A].scale = 1.0f;
        m_elements[ELEM_BTN_A].opacity = 0.35f;

        m_elements[ELEM_BTN_B].normX = rightCenterX / w;
        m_elements[ELEM_BTN_B].normY = (rightCenterY + diamondDist) / h;
        m_elements[ELEM_BTN_B].scale = 1.0f;
        m_elements[ELEM_BTN_B].opacity = 0.35f;

        m_elements[ELEM_BTN_X].normX = rightCenterX / w;
        m_elements[ELEM_BTN_X].normY = (rightCenterY - diamondDist) / h;
        m_elements[ELEM_BTN_X].scale = 1.0f;
        m_elements[ELEM_BTN_X].opacity = 0.35f;

        m_elements[ELEM_BTN_Y].normX = (rightCenterX - diamondDist) / w;
        m_elements[ELEM_BTN_Y].normY = rightCenterY / h;
        m_elements[ELEM_BTN_Y].scale = 1.0f;
        m_elements[ELEM_BTN_Y].opacity = 0.35f;
    }

    // 3. Shoulders
    m_elements[ELEM_BTN_L].normX = (24.0f * scale + (145.0f * scale * 0.5f)) / w;
    m_elements[ELEM_BTN_L].normY = (16.0f * scale + (54.0f * scale * 0.5f)) / h;
    m_elements[ELEM_BTN_L].scale = 1.0f;
    m_elements[ELEM_BTN_L].opacity = 0.35f;

    m_elements[ELEM_BTN_R].normX = (w - (169.0f * scale) + (145.0f * scale * 0.5f)) / w;
    m_elements[ELEM_BTN_R].normY = (16.0f * scale + (54.0f * scale * 0.5f)) / h;
    m_elements[ELEM_BTN_R].scale = 1.0f;
    m_elements[ELEM_BTN_R].opacity = 0.35f;

    // 4. Utility
    m_elements[ELEM_BTN_SELECT].normX = ((w * 0.5f) - (110.0f * scale) + (92.0f * scale * 0.5f)) / w;
    m_elements[ELEM_BTN_SELECT].normY = (h - (52.0f * scale) + (38.0f * scale * 0.5f)) / h;
    m_elements[ELEM_BTN_SELECT].scale = 1.0f;
    m_elements[ELEM_BTN_SELECT].opacity = 0.35f;

    m_elements[ELEM_BTN_START].normX = ((w * 0.5f) + (18.0f * scale) + (92.0f * scale * 0.5f)) / w;
    m_elements[ELEM_BTN_START].normY = (h - (52.0f * scale) + (38.0f * scale * 0.5f)) / h;
    m_elements[ELEM_BTN_START].scale = 1.0f;
    m_elements[ELEM_BTN_START].opacity = 0.35f;

    // 5. Menu Button
    m_elements[ELEM_BTN_MENU].normX = (w * 0.5f) / w;
    m_elements[ELEM_BTN_MENU].normY = (14.0f * scale + (42.0f * scale * 0.5f)) / h;
    m_elements[ELEM_BTN_MENU].scale = 1.0f;
    m_elements[ELEM_BTN_MENU].opacity = 0.40f;
}

void VirtualPad::updateElementPixelMetrics(int elementIndex) {
    if (elementIndex < 0 || elementIndex >= ELEM_COUNT) return;
    auto& elem = m_elements[elementIndex];

    float w = static_cast<float>(m_screenWidth);
    float h = static_cast<float>(m_screenHeight);
    float scale = m_uiScale * elem.scale;

    elem.px = elem.normX * w;
    elem.py = elem.normY * h;

    if (elem.id == ELEM_STICK_OR_DPAD) {
        elem.radius = 78.0f * scale;
        m_stickOuterRadius = 78.0f * scale;
        m_stickNubRadius = 34.0f * scale;
        m_stickMaxDist = 65.0f * scale;
        m_stickDeadzone = 12.0f * scale;
        if (!m_stickActive) {
            m_stickBaseX = elem.px;
            m_stickBaseY = elem.py;
            m_stickNubX = elem.px;
            m_stickNubY = elem.py;
        }
    } else if (elem.isCircle) {
        elem.radius = 40.0f * scale;
    } else if (elem.id == ELEM_BTN_L || elem.id == ELEM_BTN_R) {
        elem.width = 145.0f * scale;
        elem.height = 54.0f * scale;
    } else if (elem.id == ELEM_BTN_SELECT || elem.id == ELEM_BTN_START) {
        elem.width = 92.0f * scale;
        elem.height = 38.0f * scale;
    } else if (elem.id == ELEM_BTN_MENU) {
        elem.width = 130.0f * scale;
        elem.height = 42.0f * scale;
    }
}

void VirtualPad::resetCustomLayout() {
    applyConsoleLayoutDefaults();
    for (size_t i = 0; i < ELEM_COUNT; ++i) {
        updateElementPixelMetrics(static_cast<int>(i));
    }
    saveCustomLayout();
}

void VirtualPad::updateLayout(int screenWidth, int screenHeight) {
    m_screenWidth = screenWidth;
    m_screenHeight = screenHeight;

    if (screenWidth <= 0 || screenHeight <= 0) return;

    float base = static_cast<float>(std::min(screenWidth, screenHeight));
    m_uiScale = base / 720.0f;
    if (m_uiScale < 0.65f) m_uiScale = 0.65f;

    // Check if defaults need to be applied
    if (m_elements[ELEM_STICK_OR_DPAD].normX <= 0.001f && m_elements[ELEM_STICK_OR_DPAD].normY <= 0.001f) {
        applyConsoleLayoutDefaults();
    }

    for (size_t i = 0; i < ELEM_COUNT; ++i) {
        updateElementPixelMetrics(static_cast<int>(i));
    }

    LOGI("VirtualPad layout updated for display %dx%d (scale: %.2f, mode: %d)",
         screenWidth, screenHeight, m_uiScale, static_cast<int>(m_leftInputMode));
}

void VirtualPad::saveCustomLayout() {
    int curLayoutIdx = consoleToIndex(m_consoleLayout);
    for (size_t i = 0; i < ELEM_COUNT; ++i) {
        m_profiles[m_activeProfile][curLayoutIdx][i] = {
            m_elements[i].normX,
            m_elements[i].normY,
            m_elements[i].scale,
            m_elements[i].opacity
        };
    }

    if (m_configFilePath.empty()) return;

    std::string tmpPath = m_configFilePath + ".tmp";
    std::ofstream out(tmpPath, std::ios::out | std::ios::trunc);
    if (!out.is_open()) return;

    out << "version 1\n";
    out << "profile " << m_activeProfile << "\n";
    out << "left_mode " << static_cast<int>(m_leftInputMode) << "\n";

    for (int p = 0; p < 2; ++p) {
        for (int lay = 0; lay < 5; ++lay) {
            for (int e = 0; e < ELEM_COUNT; ++e) {
                const auto& prof = m_profiles[p][lay][e];
                out << p << " " << lay << " " << e << " "
                    << prof.normX << " " << prof.normY << " "
                    << prof.scale << " " << prof.opacity << "\n";
            }
        }
    }

    out.flush();
    out.close();
    rename(tmpPath.c_str(), m_configFilePath.c_str());
    LOGI("VirtualPad configuration saved atomically to %s", m_configFilePath.c_str());
}

void VirtualPad::loadCustomLayout() {
    if (m_configFilePath.empty()) return;

    std::ifstream in(m_configFilePath);
    if (!in.is_open()) return;

    std::string header;
    int ver = 0;
    if (!(in >> header >> ver) || header != "version") return;

    std::string line;
    while (in >> line) {
        if (line == "profile") {
            in >> m_activeProfile;
            if (m_activeProfile < 0 || m_activeProfile > 1) m_activeProfile = 0;
        } else if (line == "left_mode") {
            int modeVal = 0;
            in >> modeVal;
            m_leftInputMode = (modeVal == 1) ? LeftInputMode::DPAD : LeftInputMode::JOYSTICK;
        } else {
            // Profile entry: p lay e normX normY scale opacity
            int p = std::atoi(line.c_str());
            int lay = 0, e = 0;
            float nx = 0, ny = 0, sc = 1, op = 0.35f;
            if (in >> lay >> e >> nx >> ny >> sc >> op) {
                if (p >= 0 && p < 2 && lay >= 0 && lay < 5 && e >= 0 && e < ELEM_COUNT) {
                    m_profiles[p][lay][e] = { nx, ny, sc, op };
                }
            }
        }
    }

    m_profilesInitialized = true;

    // Apply active profile to current elements
    int curLayoutIdx = consoleToIndex(m_consoleLayout);
    for (size_t i = 0; i < ELEM_COUNT; ++i) {
        const auto& prof = m_profiles[m_activeProfile][curLayoutIdx][i];
        if (prof.scale > 0.1f && prof.opacity > 0.05f && prof.normX > 0.001f) {
            m_elements[i].normX = prof.normX;
            m_elements[i].normY = prof.normY;
            m_elements[i].scale = prof.scale;
            m_elements[i].opacity = prof.opacity;
        }
    }

    if (m_screenWidth > 0 && m_screenHeight > 0) {
        for (size_t i = 0; i < ELEM_COUNT; ++i) {
            updateElementPixelMetrics(static_cast<int>(i));
        }
    }
    LOGI("VirtualPad custom configuration loaded successfully");
}

int VirtualPad::handleInputEvent(const AInputEvent* event) {
    if (AInputEvent_getType(event) != AINPUT_EVENT_TYPE_MOTION) {
        return 0;
    }

    std::lock_guard<std::mutex> lock(m_mutex);

    if (m_isCustomizing) {
        return handleCustomHudInput(event);
    }

    updatePointers(event);
    recomputeBitmask();
    return 1;
}

int VirtualPad::handleCustomHudInput(const AInputEvent* event) {
    int32_t action = AMotionEvent_getAction(event);
    int32_t actionMasked = action & AMOTION_EVENT_ACTION_MASK;
    size_t pointerIndex = (action & AMOTION_EVENT_ACTION_POINTER_INDEX_MASK) >> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT;
    float px = AMotionEvent_getX(event, pointerIndex);
    float py = AMotionEvent_getY(event, pointerIndex);

    float w = static_cast<float>(m_screenWidth);
    float h = static_cast<float>(m_screenHeight);

    float topBarH = 46.0f * m_uiScale;
    float panelW = std::min(w * 0.90f, 560.0f * m_uiScale);
    float panelH = m_inspectorCollapsed ? (32.0f * m_uiScale) : (140.0f * m_uiScale);
    float panelX = (w - panelW) * 0.5f;
    float panelY = topBarH + 6.0f;

    switch (actionMasked) {
        case AMOTION_EVENT_ACTION_DOWN:
        case AMOTION_EVENT_ACTION_POINTER_DOWN: {
            // 1. Check Top Toolbar Buttons
            if (py <= topBarH) {
                // [ < EXIT ]
                if (px <= 110.0f * m_uiScale) {
                    m_isCustomizing = false;
                    saveCustomLayout();
                    return 1;
                }

                // [ < PROFILE 1/2 > ]
                float profX = 120.0f * m_uiScale;
                float profW = 140.0f * m_uiScale;
                if (px >= profX && px <= profX + profW) {
                    setActiveProfile(1 - m_activeProfile);
                    return 1;
                }

                // [ ↺ RESET ]
                float resetX = profX + profW + 10.0f;
                float resetW = 95.0f * m_uiScale;
                if (px >= resetX && px <= resetX + resetW) {
                    resetCustomLayout();
                    m_hudToastMessage = "RESET TO DEFAULTS";
                    m_hudToastFrames = 90;
                    return 1;
                }

                // [ 💾 SAVE ]
                float saveX = resetX + resetW + 10.0f;
                float saveW = 90.0f * m_uiScale;
                if (px >= saveX && px <= saveX + saveW) {
                    saveCustomLayout();
                    m_hudToastMessage = "LAYOUT SAVED!";
                    m_hudToastFrames = 90;
                    return 1;
                }

                // [ MODE: JOYSTICK / D-PAD ]
                float modeX = saveX + saveW + 10.0f;
                float modeW = 160.0f * m_uiScale;
                if (px >= modeX && px <= modeX + modeW) {
                    toggleLeftInputMode();
                    m_hudToastMessage = (m_leftInputMode == LeftInputMode::DPAD) ? "MODE: D-PAD" : "MODE: JOYSTICK";
                    m_hudToastFrames = 90;
                    return 1;
                }
                return 1;
            }

            // 2. Check Property Inspector Panel
            if (px >= panelX && px <= panelX + panelW && py >= panelY && py <= panelY + panelH) {
                // Check collapse toggle handle (bottom strip of panel)
                if (py >= panelY + panelH - (24.0f * m_uiScale)) {
                    m_inspectorCollapsed = !m_inspectorCollapsed;
                    return 1;
                }

                if (!m_inspectorCollapsed && m_selectedElement >= 0 && m_selectedElement < ELEM_COUNT) {
                    auto& selElem = m_elements[m_selectedElement];
                    float sliderStartX = panelX + (16.0f * m_uiScale);
                    float sliderW = panelW * 0.52f;

                    // Opacity Slider track
                    float opY = panelY + (48.0f * m_uiScale);
                    if (py >= opY - 14.0f && py <= opY + 22.0f && px >= sliderStartX && px <= sliderStartX + sliderW) {
                        m_draggingOpacitySlider = true;
                        float frac = std::clamp((px - sliderStartX) / sliderW, 0.0f, 1.0f);
                        selElem.opacity = 0.10f + frac * 0.90f;
                        return 1;
                    }

                    // Size Slider track
                    float szY = panelY + (86.0f * m_uiScale);
                    if (py >= szY - 14.0f && py <= szY + 22.0f && px >= sliderStartX && px <= sliderStartX + sliderW) {
                        m_draggingSizeSlider = true;
                        float frac = std::clamp((px - sliderStartX) / sliderW, 0.0f, 1.0f);
                        selElem.scale = 0.50f + frac * 1.50f;
                        updateElementPixelMetrics(m_selectedElement);
                        return 1;
                    }

                    // Nudge D-Pad Buttons (Right side of inspector)
                    float nudgeBaseX = panelX + panelW - (120.0f * m_uiScale);
                    float nudgeBaseY = panelY + (40.0f * m_uiScale);
                    float btnSz = 34.0f * m_uiScale;

                    // Nudge Up
                    if (hitTestRect(px, py, nudgeBaseX + btnSz, nudgeBaseY, btnSz, btnSz)) {
                        selElem.normY = std::max(0.02f, selElem.normY - (4.0f / h));
                        updateElementPixelMetrics(m_selectedElement);
                        return 1;
                    }
                    // Nudge Down
                    if (hitTestRect(px, py, nudgeBaseX + btnSz, nudgeBaseY + btnSz * 1.8f, btnSz, btnSz)) {
                        selElem.normY = std::min(0.98f, selElem.normY + (4.0f / h));
                        updateElementPixelMetrics(m_selectedElement);
                        return 1;
                    }
                    // Nudge Left
                    if (hitTestRect(px, py, nudgeBaseX, nudgeBaseY + btnSz * 0.9f, btnSz, btnSz)) {
                        selElem.normX = std::max(0.02f, selElem.normX - (4.0f / w));
                        updateElementPixelMetrics(m_selectedElement);
                        return 1;
                    }
                    // Nudge Right
                    if (hitTestRect(px, py, nudgeBaseX + btnSz * 2.0f, nudgeBaseY + btnSz * 0.9f, btnSz, btnSz)) {
                        selElem.normX = std::min(0.98f, selElem.normX + (4.0f / w));
                        updateElementPixelMetrics(m_selectedElement);
                        return 1;
                    }
                }
                return 1;
            }

            // 3. Canvas Hit-Test: Select & Start Dragging Control Elements
            for (int i = static_cast<int>(ELEM_COUNT) - 1; i >= 0; --i) {
                const auto& elem = m_elements[i];
                if (!elem.enabled) continue;

                bool hit = false;
                if (elem.isCircle) {
                    hit = hitTestCircle(px, py, elem.px, elem.py, elem.radius * 1.35f);
                } else {
                    hit = hitTestRect(px, py, elem.px - elem.width * 0.5f, elem.py - elem.height * 0.5f, elem.width, elem.height);
                }

                if (hit) {
                    m_selectedElement = i;
                    m_isDraggingElement = true;
                    m_dragTouchStartX = px;
                    m_dragTouchStartY = py;
                    m_elementStartX = elem.px;
                    m_elementStartY = elem.py;
                    return 1;
                }
            }
            break;
        }

        case AMOTION_EVENT_ACTION_MOVE: {
            if (m_draggingOpacitySlider && m_selectedElement >= 0 && m_selectedElement < ELEM_COUNT) {
                float sliderStartX = panelX + (16.0f * m_uiScale);
                float sliderW = panelW * 0.52f;
                float frac = std::clamp((px - sliderStartX) / sliderW, 0.0f, 1.0f);
                m_elements[m_selectedElement].opacity = 0.10f + frac * 0.90f;
                return 1;
            }

            if (m_draggingSizeSlider && m_selectedElement >= 0 && m_selectedElement < ELEM_COUNT) {
                float sliderStartX = panelX + (16.0f * m_uiScale);
                float sliderW = panelW * 0.52f;
                float frac = std::clamp((px - sliderStartX) / sliderW, 0.0f, 1.0f);
                m_elements[m_selectedElement].scale = 0.50f + frac * 1.50f;
                updateElementPixelMetrics(m_selectedElement);
                return 1;
            }

            if (m_isDraggingElement && m_selectedElement >= 0 && m_selectedElement < ELEM_COUNT) {
                auto& elem = m_elements[m_selectedElement];
                float dx = px - m_dragTouchStartX;
                float dy = py - m_dragTouchStartY;

                float targetPx = std::clamp(m_elementStartX + dx, 20.0f, w - 20.0f);
                float targetPy = std::clamp(m_elementStartY + dy, 20.0f, h - 20.0f);

                elem.px = targetPx;
                elem.py = targetPy;
                elem.normX = elem.px / w;
                elem.normY = elem.py / h;
                updateElementPixelMetrics(m_selectedElement);
                return 1;
            }
            break;
        }

        case AMOTION_EVENT_ACTION_UP:
        case AMOTION_EVENT_ACTION_POINTER_UP:
        case AMOTION_EVENT_ACTION_CANCEL: {
            m_isDraggingElement = false;
            m_draggingOpacitySlider = false;
            m_draggingSizeSlider = false;
            break;
        }

        default:
            break;
    }

    return 1;
}

void VirtualPad::updatePointers(const AInputEvent* event) {
    int32_t action = AMotionEvent_getAction(event);
    int32_t actionMasked = action & AMOTION_EVENT_ACTION_MASK;
    size_t pointerIndex = (action & AMOTION_EVENT_ACTION_POINTER_INDEX_MASK) >> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT;
    size_t pointerCount = AMotionEvent_getPointerCount(event);

    float leftZoneMaxX = static_cast<float>(m_screenWidth) * 0.45f;
    const auto& stickElem = m_elements[ELEM_STICK_OR_DPAD];

    switch (actionMasked) {
        case AMOTION_EVENT_ACTION_DOWN:
        case AMOTION_EVENT_ACTION_POINTER_DOWN: {
            int pointerId = AMotionEvent_getPointerId(event, pointerIndex);
            float x = AMotionEvent_getX(event, pointerIndex);
            float y = AMotionEvent_getY(event, pointerIndex);

            if (m_leftInputMode == LeftInputMode::JOYSTICK) {
                if (x < leftZoneMaxX && !m_stickActive) {
                    m_stickActive = true;
                    m_stickPointerId = pointerId;
                    m_stickBaseX = x;
                    m_stickBaseY = y;
                    m_stickNubX = x;
                    m_stickNubY = y;
                }
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

                if (m_leftInputMode == LeftInputMode::JOYSTICK && m_stickActive && pointerId == m_stickPointerId) {
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
            if (m_leftInputMode == LeftInputMode::JOYSTICK && m_stickActive && pointerId == m_stickPointerId) {
                m_stickActive = false;
                m_stickPointerId = -1;
                m_stickBaseX = stickElem.px;
                m_stickBaseY = stickElem.py;
                m_stickNubX = stickElem.px;
                m_stickNubY = stickElem.py;
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
            m_stickBaseX = stickElem.px;
            m_stickBaseY = stickElem.py;
            m_stickNubX = stickElem.px;
            m_stickNubY = stickElem.py;

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

    // 1. Process Left Input: Floating Stick or D-Pad
    if (m_leftInputMode == LeftInputMode::JOYSTICK) {
        if (m_stickActive) {
            float dx = m_stickNubX - m_stickBaseX;
            float dy = m_stickNubY - m_stickBaseY;
            float dist = std::sqrt(dx * dx + dy * dy);

            if (dist > m_stickDeadzone) {
                float angleDeg = std::atan2(dy, dx) * (180.0f / static_cast<float>(M_PI));

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
    } else {
        // D-Pad Hit-Testing
        const auto& dpad = m_elements[ELEM_STICK_OR_DPAD];
        float dpadRadius = dpad.radius * 1.25f;

        for (const auto& p : m_pointers) {
            if (!p.active) continue;

            float dx = p.x - dpad.px;
            float dy = p.y - dpad.py;
            float dist = std::sqrt(dx * dx + dy * dy);

            if (dist <= dpadRadius && dist >= (10.0f * m_uiScale)) {
                float angleDeg = std::atan2(dy, dx) * (180.0f / static_cast<float>(M_PI));

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
    }

    // 2. Action & Utility Buttons Hit-Testing
    for (const auto& p : m_pointers) {
        if (!p.active) continue;
        if (m_leftInputMode == LeftInputMode::JOYSTICK && p.id == m_stickPointerId) continue;

        for (size_t i = 1; i < ELEM_COUNT; ++i) {
            const auto& elem = m_elements[i];
            if (!elem.enabled) continue;

            bool hit = false;
            if (elem.isCircle) {
                hit = hitTestCircle(p.x, p.y, elem.px, elem.py, elem.radius * 1.35f);
            } else {
                hit = hitTestRect(p.x, p.y, elem.px - elem.width * 0.5f, elem.py - elem.height * 0.5f, elem.width, elem.height);
            }

            if (hit) {
                if (elem.id == ELEM_BTN_MENU) {
                    m_menuRequested = true;
                } else {
                    mask |= elem.mask;
                }
            }
        }
    }

    m_activeBitmask = mask;
}

void VirtualPad::poll() {
    // libretro poll loop
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

void VirtualPad::renderDpad(float cx, float cy, float span, float opacity, uint32_t activeMask) {
    float armLen = span * 0.38f;
    float armW = span * 0.32f;
    float halfW = armW * 0.5f;

    float baseAlpha = opacity;
    float activeAlpha = std::min(1.0f, baseAlpha * 2.5f);

    bool upActive    = (activeMask & BTN_UP) != 0;
    bool downActive  = (activeMask & BTN_DOWN) != 0;
    bool leftActive  = (activeMask & BTN_LEFT) != 0;
    bool rightActive = (activeMask & BTN_RIGHT) != 0;

    // Cross background body
    renderBorderedRect(cx - halfW, cy - halfW - armLen, armW, armLen * 2.0f + armW, 2.0f,
                       0.08f, 0.12f, 0.18f, baseAlpha * 0.85f,
                       0.35f, 0.65f, 1.0f, baseAlpha * 1.5f);

    renderBorderedRect(cx - halfW - armLen, cy - halfW, armLen * 2.0f + armW, armW, 2.0f,
                       0.08f, 0.12f, 0.18f, baseAlpha * 0.85f,
                       0.35f, 0.65f, 1.0f, baseAlpha * 1.5f);

    // Active highlights for directional arms
    if (upActive) {
        renderRect(cx - halfW + 2.0f, cy - halfW - armLen + 2.0f, armW - 4.0f, armLen, 0.30f, 0.70f, 1.0f, activeAlpha);
    }
    if (downActive) {
        renderRect(cx - halfW + 2.0f, cy + halfW, armW - 4.0f, armLen - 2.0f, 0.30f, 0.70f, 1.0f, activeAlpha);
    }
    if (leftActive) {
        renderRect(cx - halfW - armLen + 2.0f, cy - halfW + 2.0f, armLen, armW - 4.0f, 0.30f, 0.70f, 1.0f, activeAlpha);
    }
    if (rightActive) {
        renderRect(cx + halfW, cy - halfW + 2.0f, armLen - 2.0f, armW - 4.0f, 0.30f, 0.70f, 1.0f, activeAlpha);
    }

    // Center jewel
    renderCircle(cx, cy, halfW * 0.65f, 0.18f, 0.28f, 0.45f, baseAlpha * 1.4f);
    renderRing(cx, cy, halfW * 0.55f, halfW * 0.65f, 0.45f, 0.75f, 1.0f, baseAlpha * 1.8f);
}

void VirtualPad::render(int screenWidth, int screenHeight) {
    if (!m_visible || screenWidth <= 0 || screenHeight <= 0) return;

    std::lock_guard<std::mutex> lock(m_mutex);
    if (!initGL()) return;

    if (m_screenWidth != screenWidth || m_screenHeight != screenHeight) {
        updateLayout(screenWidth, screenHeight);
    }

    if (m_isCustomizing) {
        renderCustomHudEditor(screenWidth, screenHeight);
        return;
    }

    glViewport(0, 0, screenWidth, screenHeight);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    glUseProgram(m_program);
    glUniform2f(m_locScreenSize, static_cast<float>(screenWidth), static_cast<float>(screenHeight));

    // 1. Left Input: PPSSPP Floating Thumbstick or D-Pad
    const auto& stickElem = m_elements[ELEM_STICK_OR_DPAD];
    float stickAlpha = stickElem.opacity;
    float activeStickAlpha = std::min(1.0f, stickAlpha * 2.5f);

    if (m_leftInputMode == LeftInputMode::JOYSTICK) {
        // Base Disc & Glowing Ring
        renderCircle(m_stickBaseX, m_stickBaseY, m_stickOuterRadius, 0.08f, 0.12f, 0.18f, stickAlpha * 0.8f);
        renderRing(m_stickBaseX, m_stickBaseY, m_stickOuterRadius - 3.0f, m_stickOuterRadius,
                   0.35f, 0.65f, 1.0f, m_stickActive ? activeStickAlpha : stickAlpha * 1.5f);

        // Directional Guide Ticks
        float tickLen = 10.0f * m_uiScale * stickElem.scale;
        renderRect(m_stickBaseX - 1.5f, m_stickBaseY - m_stickOuterRadius - tickLen, 3.0f, tickLen, 0.45f, 0.70f, 1.0f, stickAlpha * 1.2f);
        renderRect(m_stickBaseX - 1.5f, m_stickBaseY + m_stickOuterRadius, 3.0f, tickLen, 0.45f, 0.70f, 1.0f, stickAlpha * 1.2f);
        renderRect(m_stickBaseX - m_stickOuterRadius - tickLen, m_stickBaseY - 1.5f, tickLen, 3.0f, 0.45f, 0.70f, 1.0f, stickAlpha * 1.2f);
        renderRect(m_stickBaseX + m_stickOuterRadius, m_stickBaseY - 1.5f, tickLen, 3.0f, 0.45f, 0.70f, 1.0f, stickAlpha * 1.2f);

        // Draggable Inner Nub
        renderCircle(m_stickNubX, m_stickNubY, m_stickNubRadius, 0.20f, 0.35f, 0.60f, m_stickActive ? activeStickAlpha : stickAlpha * 1.5f);
        renderRing(m_stickNubX, m_stickNubY, m_stickNubRadius - 2.5f, m_stickNubRadius, 0.60f, 0.85f, 1.0f, m_stickActive ? activeStickAlpha : stickAlpha * 1.8f);
        renderCircle(m_stickNubX, m_stickNubY, m_stickNubRadius * 0.35f, 0.40f, 0.65f, 0.95f, m_stickActive ? activeStickAlpha : stickAlpha * 1.2f);
    } else {
        renderDpad(stickElem.px, stickElem.py, stickElem.radius * 2.2f, stickElem.opacity, m_activeBitmask);
    }

    // 2. Action Buttons (A, B, X, Y)
    bool aActive = (m_activeBitmask & BTN_A) != 0;
    bool bActive = (m_activeBitmask & BTN_B) != 0;
    bool xActive = (m_activeBitmask & BTN_X) != 0;
    bool yActive = (m_activeBitmask & BTN_Y) != 0;

    const auto& btnA = m_elements[ELEM_BTN_A];
    const auto& btnB = m_elements[ELEM_BTN_B];
    const auto& btnX = m_elements[ELEM_BTN_X];
    const auto& btnY = m_elements[ELEM_BTN_Y];

    if (btnA.enabled) {
        float aAlpha = btnA.opacity;
        float actAlpha = std::min(1.0f, aAlpha * 2.5f);
        renderCircle(btnA.px, btnA.py, btnA.radius, aActive ? 0.90f : 0.09f, aActive ? 0.20f : 0.12f, aActive ? 0.30f : 0.18f, aActive ? actAlpha * 0.85f : aAlpha * 1.2f);
        renderRing(btnA.px, btnA.py, btnA.radius - 3.0f, btnA.radius, 1.0f, 0.35f, 0.48f, aActive ? actAlpha : aAlpha * 1.8f);
    }

    if (btnB.enabled) {
        float bAlpha = btnB.opacity;
        float actAlpha = std::min(1.0f, bAlpha * 2.5f);
        renderCircle(btnB.px, btnB.py, btnB.radius, bActive ? 0.90f : 0.09f, bActive ? 0.60f : 0.12f, bActive ? 0.15f : 0.18f, bActive ? actAlpha * 0.85f : bAlpha * 1.2f);
        renderRing(btnB.px, btnB.py, btnB.radius - 3.0f, btnB.radius, 0.95f, 0.70f, 0.25f, bActive ? actAlpha : bAlpha * 1.8f);
    }

    if (btnX.enabled) {
        float xAlpha = btnX.opacity;
        float actAlpha = std::min(1.0f, xAlpha * 2.5f);
        renderCircle(btnX.px, btnX.py, btnX.radius, xActive ? 0.20f : 0.09f, xActive ? 0.50f : 0.12f, xActive ? 0.90f : 0.18f, xActive ? actAlpha * 0.85f : xAlpha * 1.2f);
        renderRing(btnX.px, btnX.py, btnX.radius - 3.0f, btnX.radius, 0.30f, 0.65f, 1.0f, xActive ? actAlpha : xAlpha * 1.8f);
    }

    if (btnY.enabled) {
        float yAlpha = btnY.opacity;
        float actAlpha = std::min(1.0f, yAlpha * 2.5f);
        renderCircle(btnY.px, btnY.py, btnY.radius, yActive ? 0.20f : 0.09f, yActive ? 0.75f : 0.12f, yActive ? 0.40f : 0.18f, yActive ? actAlpha * 0.85f : yAlpha * 1.2f);
        renderRing(btnY.px, btnY.py, btnY.radius - 3.0f, btnY.radius, 0.35f, 0.85f, 0.55f, yActive ? actAlpha : yAlpha * 1.8f);
    }

    // 3. Shoulders (L, R)
    const auto& btnL = m_elements[ELEM_BTN_L];
    const auto& btnR = m_elements[ELEM_BTN_R];
    bool lActive = (m_activeBitmask & BTN_L) != 0;
    bool rActive = (m_activeBitmask & BTN_R) != 0;

    if (btnL.enabled) {
        float lAlpha = btnL.opacity;
        float actAlpha = std::min(1.0f, lAlpha * 2.5f);
        renderBorderedRect(btnL.px - btnL.width * 0.5f, btnL.py - btnL.height * 0.5f, btnL.width, btnL.height, 2.0f,
                           lActive ? 0.25f : 0.08f, lActive ? 0.40f : 0.12f, lActive ? 0.65f : 0.18f, lActive ? actAlpha : lAlpha * 1.2f,
                           0.40f, 0.65f, 0.95f, lActive ? actAlpha : lAlpha * 1.8f);
    }

    if (btnR.enabled) {
        float rAlpha = btnR.opacity;
        float actAlpha = std::min(1.0f, rAlpha * 2.5f);
        renderBorderedRect(btnR.px - btnR.width * 0.5f, btnR.py - btnR.height * 0.5f, btnR.width, btnR.height, 2.0f,
                           rActive ? 0.25f : 0.08f, rActive ? 0.40f : 0.12f, rActive ? 0.65f : 0.18f, rActive ? actAlpha : rAlpha * 1.2f,
                           0.40f, 0.65f, 0.95f, rActive ? actAlpha : rAlpha * 1.8f);
    }

    // 4. Utility Buttons (Select, Start)
    const auto& btnSel = m_elements[ELEM_BTN_SELECT];
    const auto& btnSta = m_elements[ELEM_BTN_START];
    bool selActive = (m_activeBitmask & BTN_SELECT) != 0;
    bool staActive = (m_activeBitmask & BTN_START) != 0;

    if (btnSel.enabled) {
        float selAlpha = btnSel.opacity;
        float actAlpha = std::min(1.0f, selAlpha * 2.5f);
        renderBorderedRect(btnSel.px - btnSel.width * 0.5f, btnSel.py - btnSel.height * 0.5f, btnSel.width, btnSel.height, 1.5f,
                           selActive ? 0.25f : 0.08f, selActive ? 0.35f : 0.12f, selActive ? 0.55f : 0.18f, selActive ? actAlpha : selAlpha * 1.2f,
                           0.40f, 0.55f, 0.75f, selActive ? actAlpha : selAlpha * 1.6f);
    }

    if (btnSta.enabled) {
        float staAlpha = btnSta.opacity;
        float actAlpha = std::min(1.0f, staAlpha * 2.5f);
        renderBorderedRect(btnSta.px - btnSta.width * 0.5f, btnSta.py - btnSta.height * 0.5f, btnSta.width, btnSta.height, 1.5f,
                           staActive ? 0.25f : 0.08f, staActive ? 0.35f : 0.12f, staActive ? 0.55f : 0.18f, staActive ? actAlpha : staAlpha * 1.2f,
                           0.40f, 0.55f, 0.75f, staActive ? actAlpha : staAlpha * 1.6f);
    }

    // 5. Menu Button
    const auto& btnMenu = m_elements[ELEM_BTN_MENU];
    if (btnMenu.enabled) {
        renderBorderedRect(btnMenu.px - btnMenu.width * 0.5f, btnMenu.py - btnMenu.height * 0.5f, btnMenu.width, btnMenu.height, 1.5f,
                           0.10f, 0.20f, 0.35f, btnMenu.opacity * 1.4f,
                           0.35f, 0.70f, 1.0f, btnMenu.opacity * 1.9f);
    }

    glDisable(GL_BLEND);

    // 6. Draw Crisp Glyphs & Labels
    FontRenderer& font = FontRenderer::instance();
    float actionFontScale = std::max(2.8f, (btnA.radius * 0.7f) / 8.0f);

    if (btnA.enabled) {
        font.renderText(btnA.label, btnA.px, btnA.py, actionFontScale,
                        1.0f, 1.0f, 1.0f, aActive ? 1.0f : 0.85f, screenWidth, screenHeight, true, true);
    }

    if (btnB.enabled) {
        font.renderText(btnB.label, btnB.px, btnB.py, actionFontScale,
                        1.0f, 1.0f, 1.0f, bActive ? 1.0f : 0.85f, screenWidth, screenHeight, true, true);
    }

    if (btnX.enabled) {
        font.renderText(btnX.label, btnX.px, btnX.py, actionFontScale,
                        1.0f, 1.0f, 1.0f, xActive ? 1.0f : 0.85f, screenWidth, screenHeight, true, true);
    }

    if (btnY.enabled) {
        font.renderText(btnY.label, btnY.px, btnY.py, actionFontScale,
                        1.0f, 1.0f, 1.0f, yActive ? 1.0f : 0.85f, screenWidth, screenHeight, true, true);
    }

    if (btnL.enabled) {
        font.renderText("L", btnL.px, btnL.py, 2.6f * m_uiScale * btnL.scale,
                        1.0f, 1.0f, 1.0f, lActive ? 1.0f : 0.85f, screenWidth, screenHeight, true, true);
    }

    if (btnR.enabled) {
        font.renderText("R", btnR.px, btnR.py, 2.6f * m_uiScale * btnR.scale,
                        1.0f, 1.0f, 1.0f, rActive ? 1.0f : 0.85f, screenWidth, screenHeight, true, true);
    }

    if (btnSel.enabled) {
        font.renderText("SELECT", btnSel.px, btnSel.py, 1.7f * m_uiScale * btnSel.scale,
                        0.85f, 0.92f, 1.0f, selActive ? 1.0f : 0.75f, screenWidth, screenHeight, true, true);
    }

    if (btnSta.enabled) {
        font.renderText("START", btnSta.px, btnSta.py, 1.7f * m_uiScale * btnSta.scale,
                        0.85f, 0.92f, 1.0f, staActive ? 1.0f : 0.75f, screenWidth, screenHeight, true, true);
    }

    if (btnMenu.enabled) {
        font.renderText("MENU", btnMenu.px, btnMenu.py, 1.8f * m_uiScale * btnMenu.scale,
                        0.50f, 0.85f, 1.0f, 0.90f, screenWidth, screenHeight, true, true);
    }

    // Directional arrows for D-Pad
    if (m_leftInputMode == LeftInputMode::DPAD) {
        float dpadSpan = stickElem.radius * 2.2f;
        float armLen = dpadSpan * 0.38f;
        float fScale = 1.6f * m_uiScale * stickElem.scale;

        font.renderText("^", stickElem.px, stickElem.py - armLen * 0.85f, fScale, 1.0f, 1.0f, 1.0f, 0.8f, screenWidth, screenHeight, true, true);
        font.renderText("v", stickElem.px, stickElem.py + armLen * 0.85f, fScale, 1.0f, 1.0f, 1.0f, 0.8f, screenWidth, screenHeight, true, true);
        font.renderText("<", stickElem.px - armLen * 0.85f, stickElem.py, fScale, 1.0f, 1.0f, 1.0f, 0.8f, screenWidth, screenHeight, true, true);
        font.renderText(">", stickElem.px + armLen * 0.85f, stickElem.py, fScale, 1.0f, 1.0f, 1.0f, 0.8f, screenWidth, screenHeight, true, true);
    }
}

void VirtualPad::renderCustomHudEditor(int screenWidth, int screenHeight) {
    float w = static_cast<float>(screenWidth);
    float h = static_cast<float>(screenHeight);

    glViewport(0, 0, screenWidth, screenHeight);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    glUseProgram(m_program);
    glUniform2f(m_locScreenSize, w, h);

    // 1. Semi-transparent backdrop grid
    renderRect(0, 0, w, h, 0.03f, 0.05f, 0.08f, 0.70f);

    // 2. Render all visible elements with highlight
    for (size_t i = 0; i < ELEM_COUNT; ++i) {
        const auto& elem = m_elements[i];
        if (!elem.enabled) continue;

        bool isSelected = (static_cast<int>(i) == m_selectedElement);

        if (elem.id == ELEM_STICK_OR_DPAD) {
            if (m_leftInputMode == LeftInputMode::JOYSTICK) {
                renderCircle(elem.px, elem.py, elem.radius, 0.12f, 0.20f, 0.32f, 0.80f);
                renderRing(elem.px, elem.py, elem.radius - 3.0f, elem.radius, 0.40f, 0.75f, 1.0f, 1.0f);
                renderCircle(elem.px, elem.py, m_stickNubRadius, 0.30f, 0.50f, 0.85f, 0.90f);
            } else {
                renderDpad(elem.px, elem.py, elem.radius * 2.2f, 0.80f, 0);
            }
        } else if (elem.isCircle) {
            renderCircle(elem.px, elem.py, elem.radius, 0.15f, 0.22f, 0.35f, 0.80f);
            renderRing(elem.px, elem.py, elem.radius - 2.5f, elem.radius, 0.45f, 0.80f, 1.0f, 1.0f);
        } else {
            renderBorderedRect(elem.px - elem.width * 0.5f, elem.py - elem.height * 0.5f, elem.width, elem.height, 2.0f,
                               0.15f, 0.22f, 0.35f, 0.80f,
                               0.45f, 0.80f, 1.0f, 1.0f);
        }

        // Selection Box & Glowing Brackets
        if (isSelected) {
            float boxW = elem.isCircle ? elem.radius * 2.4f : elem.width + 16.0f;
            float boxH = elem.isCircle ? elem.radius * 2.4f : elem.height + 16.0f;
            float boxX = elem.px - boxW * 0.5f;
            float boxY = elem.py - boxH * 0.5f;

            // Gold/Cyan animated selection frame
            renderBorderedRect(boxX, boxY, boxW, boxH, 2.0f,
                               1.0f, 0.80f, 0.20f, 0.12f,
                               1.0f, 0.85f, 0.25f, 0.95f);

            // Corner brackets
            float cLen = 8.0f * m_uiScale;
            renderRect(boxX, boxY, cLen, 3.0f, 1.0f, 1.0f, 1.0f, 1.0f);
            renderRect(boxX, boxY, 3.0f, cLen, 1.0f, 1.0f, 1.0f, 1.0f);
            renderRect(boxX + boxW - cLen, boxY, cLen, 3.0f, 1.0f, 1.0f, 1.0f, 1.0f);
            renderRect(boxX + boxW - 3.0f, boxY, 3.0f, cLen, 1.0f, 1.0f, 1.0f, 1.0f);
            renderRect(boxX, boxY + boxH - 3.0f, cLen, 3.0f, 1.0f, 1.0f, 1.0f, 1.0f);
            renderRect(boxX, boxY + boxH - cLen, 3.0f, cLen, 1.0f, 1.0f, 1.0f, 1.0f);
            renderRect(boxX + boxW - cLen, boxY + boxH - 3.0f, cLen, 3.0f, 1.0f, 1.0f, 1.0f, 1.0f);
            renderRect(boxX + boxW - 3.0f, boxY + boxH - cLen, 3.0f, cLen, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    // 3. Top Toolbar Header (Free Fire HUD Editor Style)
    float topBarH = 46.0f * m_uiScale;
    renderBorderedRect(0, 0, w, topBarH, 1.5f,
                       0.08f, 0.10f, 0.15f, 0.98f,
                       0.20f, 0.30f, 0.50f, 1.0f);

    // [ < EXIT ]
    renderBorderedRect(8.0f, 6.0f, 100.0f * m_uiScale, topBarH - 12.0f, 1.5f,
                       0.25f, 0.12f, 0.15f, 1.0f,
                       0.85f, 0.35f, 0.40f, 1.0f);

    // [ < PROFILE 1/2 > ]
    float profX = 120.0f * m_uiScale;
    float profW = 140.0f * m_uiScale;
    renderBorderedRect(profX, 6.0f, profW, topBarH - 12.0f, 1.5f,
                       0.15f, 0.22f, 0.38f, 1.0f,
                       0.35f, 0.60f, 0.95f, 1.0f);

    // [ ↺ RESET ]
    float resetX = profX + profW + 10.0f;
    float resetW = 95.0f * m_uiScale;
    renderBorderedRect(resetX, 6.0f, resetW, topBarH - 12.0f, 1.5f,
                       0.30f, 0.20f, 0.10f, 1.0f,
                       0.95f, 0.65f, 0.20f, 1.0f);

    // [ 💾 SAVE ]
    float saveX = resetX + resetW + 10.0f;
    float saveW = 90.0f * m_uiScale;
    renderBorderedRect(saveX, 6.0f, saveW, topBarH - 12.0f, 1.5f,
                       0.12f, 0.35f, 0.20f, 1.0f,
                       0.30f, 0.85f, 0.45f, 1.0f);

    // [ MODE: JOYSTICK / D-PAD ]
    float modeX = saveX + saveW + 10.0f;
    float modeW = 160.0f * m_uiScale;
    renderBorderedRect(modeX, 6.0f, modeW, topBarH - 12.0f, 1.5f,
                       0.22f, 0.15f, 0.35f, 1.0f,
                       0.65f, 0.45f, 0.95f, 1.0f);

    // 4. Free Fire Style Property Inspector Panel (Collapsible)
    float panelW = std::min(w * 0.90f, 560.0f * m_uiScale);
    float panelH = m_inspectorCollapsed ? (32.0f * m_uiScale) : (140.0f * m_uiScale);
    float panelX = (w - panelW) * 0.5f;
    float panelY = topBarH + 6.0f;

    renderBorderedRect(panelX, panelY, panelW, panelH, 2.0f,
                       0.07f, 0.09f, 0.14f, 0.96f,
                       0.25f, 0.38f, 0.60f, 1.0f);

    if (!m_inspectorCollapsed && m_selectedElement >= 0 && m_selectedElement < ELEM_COUNT) {
        const auto& selElem = m_elements[m_selectedElement];

        // Sliders background tracks & fills
        float sliderStartX = panelX + (16.0f * m_uiScale);
        float sliderW = panelW * 0.52f;
        float sliderH = 12.0f * m_uiScale;

        // Opacity Slider Bar
        float opY = panelY + (48.0f * m_uiScale);
        float opFrac = std::clamp((selElem.opacity - 0.10f) / 0.90f, 0.0f, 1.0f);
        renderBorderedRect(sliderStartX, opY, sliderW, sliderH, 1.0f,
                           0.12f, 0.15f, 0.22f, 1.0f,
                           0.30f, 0.40f, 0.55f, 1.0f);
        renderRect(sliderStartX + 1.0f, opY + 1.0f, (sliderW - 2.0f) * opFrac, sliderH - 2.0f,
                   1.0f, 0.75f, 0.15f, 1.0f);
        renderCircle(sliderStartX + (sliderW * opFrac), opY + sliderH * 0.5f, 9.0f * m_uiScale,
                     1.0f, 0.90f, 0.40f, 1.0f);

        // Size Slider Bar
        float szY = panelY + (86.0f * m_uiScale);
        float szFrac = std::clamp((selElem.scale - 0.50f) / 1.50f, 0.0f, 1.0f);
        renderBorderedRect(sliderStartX, szY, sliderW, sliderH, 1.0f,
                           0.12f, 0.15f, 0.22f, 1.0f,
                           0.30f, 0.40f, 0.55f, 1.0f);
        renderRect(sliderStartX + 1.0f, szY + 1.0f, (sliderW - 2.0f) * szFrac, sliderH - 2.0f,
                   1.0f, 0.75f, 0.15f, 1.0f);
        renderCircle(sliderStartX + (sliderW * szFrac), szY + sliderH * 0.5f, 9.0f * m_uiScale,
                     1.0f, 0.90f, 0.40f, 1.0f);

        // Nudge Buttons (Right Side of Inspector)
        float nudgeBaseX = panelX + panelW - (120.0f * m_uiScale);
        float nudgeBaseY = panelY + (40.0f * m_uiScale);
        float btnSz = 34.0f * m_uiScale;

        // Up, Down, Left, Right Nudge Buttons
        renderBorderedRect(nudgeBaseX + btnSz, nudgeBaseY, btnSz, btnSz, 1.5f,
                           0.12f, 0.16f, 0.24f, 1.0f, 0.35f, 0.55f, 0.85f, 1.0f);
        renderBorderedRect(nudgeBaseX + btnSz, nudgeBaseY + btnSz * 1.8f, btnSz, btnSz, 1.5f,
                           0.12f, 0.16f, 0.24f, 1.0f, 0.35f, 0.55f, 0.85f, 1.0f);
        renderBorderedRect(nudgeBaseX, nudgeBaseY + btnSz * 0.9f, btnSz, btnSz, 1.5f,
                           0.12f, 0.16f, 0.24f, 1.0f, 0.35f, 0.55f, 0.85f, 1.0f);
        renderBorderedRect(nudgeBaseX + btnSz * 2.0f, nudgeBaseY + btnSz * 0.9f, btnSz, btnSz, 1.5f,
                           0.12f, 0.16f, 0.24f, 1.0f, 0.35f, 0.55f, 0.85f, 1.0f);
    }

    // Collapse handle bar at bottom
    renderBorderedRect(panelX + panelW * 0.45f, panelY + panelH - (18.0f * m_uiScale), panelW * 0.10f, 14.0f * m_uiScale, 1.0f,
                       0.18f, 0.25f, 0.40f, 1.0f, 0.45f, 0.70f, 1.0f, 1.0f);

    // Toast notification overlay
    if (m_hudToastFrames > 0) {
        --m_hudToastFrames;
        float toastW = 280.0f * m_uiScale;
        float toastH = 44.0f * m_uiScale;
        float toastX = (w - toastW) * 0.5f;
        float toastY = h * 0.42f;

        renderBorderedRect(toastX, toastY, toastW, toastH, 2.0f,
                           0.08f, 0.22f, 0.15f, 0.95f,
                           0.25f, 0.95f, 0.50f, 1.0f);
    }

    glDisable(GL_BLEND);

    // 5. Draw Font Renderer Texts
    FontRenderer& font = FontRenderer::instance();

    // Toolbar texts
    font.renderText("< EXIT", 8.0f + 50.0f * m_uiScale, topBarH * 0.5f, 1.8f * m_uiScale,
                    1.0f, 0.85f, 0.85f, 1.0f, screenWidth, screenHeight, true, true);

    std::string profTitle = (m_activeProfile == 0) ? "< HUD 1 >" : "< HUD 2 >";
    font.renderText(profTitle, profX + profW * 0.5f, topBarH * 0.5f, 1.8f * m_uiScale,
                    0.85f, 0.95f, 1.0f, 1.0f, screenWidth, screenHeight, true, true);

    font.renderText("RESET", resetX + resetW * 0.5f, topBarH * 0.5f, 1.7f * m_uiScale,
                    1.0f, 0.85f, 0.50f, 1.0f, screenWidth, screenHeight, true, true);

    font.renderText("SAVE", saveX + saveW * 0.5f, topBarH * 0.5f, 1.8f * m_uiScale,
                    0.60f, 1.0f, 0.60f, 1.0f, screenWidth, screenHeight, true, true);

    std::string modeTitle = (m_leftInputMode == LeftInputMode::DPAD) ? "MODE: D-PAD" : "MODE: STICK";
    font.renderText(modeTitle, modeX + modeW * 0.5f, topBarH * 0.5f, 1.6f * m_uiScale,
                    0.90f, 0.80f, 1.0f, 1.0f, screenWidth, screenHeight, true, true);

    // Inspector texts
    if (m_inspectorCollapsed) {
        font.renderText("CUSTOM HUD INSPECTOR (TAP TO EXPAND)", panelX + panelW * 0.5f, panelY + panelH * 0.5f, 1.6f * m_uiScale,
                        0.75f, 0.88f, 1.0f, 1.0f, screenWidth, screenHeight, true, true);
    } else if (m_selectedElement >= 0 && m_selectedElement < ELEM_COUNT) {
        const auto& selElem = m_elements[m_selectedElement];

        std::string inspectTitle = "Adjust: " + selElem.name + " (Drag anywhere on screen)";
        font.renderText(inspectTitle, panelX + (16.0f * m_uiScale), panelY + (16.0f * m_uiScale), 1.8f * m_uiScale,
                        1.0f, 1.0f, 1.0f, 1.0f, screenWidth, screenHeight, false, true);

        // Opacity label
        int opPercent = static_cast<int>(selElem.opacity * 100.0f);
        std::string opStr = "Opacity: " + std::to_string(opPercent) + "%";
        font.renderText(opStr, panelX + (16.0f * m_uiScale), panelY + (38.0f * m_uiScale), 1.5f * m_uiScale,
                        1.0f, 0.85f, 0.35f, 1.0f, screenWidth, screenHeight, false, true);

        // Size label
        int szPercent = static_cast<int>(selElem.scale * 100.0f);
        std::string szStr = "Size: " + std::to_string(szPercent) + "%";
        font.renderText(szStr, panelX + (16.0f * m_uiScale), panelY + (76.0f * m_uiScale), 1.5f * m_uiScale,
                        1.0f, 0.85f, 0.35f, 1.0f, screenWidth, screenHeight, false, true);

        // Nudge Pad arrow glyphs
        float nudgeBaseX = panelX + panelW - (120.0f * m_uiScale);
        float nudgeBaseY = panelY + (40.0f * m_uiScale);
        float btnSz = 34.0f * m_uiScale;

        font.renderText("^", nudgeBaseX + btnSz * 1.5f, nudgeBaseY + btnSz * 0.5f, 2.0f * m_uiScale,
                        1.0f, 1.0f, 1.0f, 1.0f, screenWidth, screenHeight, true, true);
        font.renderText("v", nudgeBaseX + btnSz * 1.5f, nudgeBaseY + btnSz * 2.3f, 2.0f * m_uiScale,
                        1.0f, 1.0f, 1.0f, 1.0f, screenWidth, screenHeight, true, true);
        font.renderText("<", nudgeBaseX + btnSz * 0.5f, nudgeBaseY + btnSz * 1.4f, 2.0f * m_uiScale,
                        1.0f, 1.0f, 1.0f, 1.0f, screenWidth, screenHeight, true, true);
        font.renderText(">", nudgeBaseX + btnSz * 2.5f, nudgeBaseY + btnSz * 1.4f, 2.0f * m_uiScale,
                        1.0f, 1.0f, 1.0f, 1.0f, screenWidth, screenHeight, true, true);

        // Collapse chevron
        font.renderText("^", panelX + panelW * 0.5f, panelY + panelH - (11.0f * m_uiScale), 1.6f * m_uiScale,
                        0.70f, 0.85f, 1.0f, 1.0f, screenWidth, screenHeight, true, true);
    }

    // Element badge labels on canvas
    for (size_t i = 0; i < ELEM_COUNT; ++i) {
        const auto& elem = m_elements[i];
        if (!elem.enabled) continue;

        if (elem.id == ELEM_STICK_OR_DPAD) {
            std::string stickLabel = (m_leftInputMode == LeftInputMode::DPAD) ? "D-PAD" : "STICK";
            font.renderText(stickLabel, elem.px, elem.py, 2.0f * m_uiScale * elem.scale,
                            1.0f, 1.0f, 1.0f, 0.95f, screenWidth, screenHeight, true, true);
        } else if (!elem.label.empty()) {
            float fSize = elem.isCircle ? std::max(2.8f, (elem.radius * 0.7f) / 8.0f) : 2.0f * m_uiScale * elem.scale;
            font.renderText(elem.label, elem.px, elem.py, fSize,
                            1.0f, 1.0f, 1.0f, 0.95f, screenWidth, screenHeight, true, true);
        }
    }

    // Toast message
    if (m_hudToastFrames > 0 && !m_hudToastMessage.empty()) {
        font.renderText(m_hudToastMessage, w * 0.5f, h * 0.42f + (22.0f * m_uiScale), 2.0f * m_uiScale,
                        1.0f, 1.0f, 1.0f, 1.0f, screenWidth, screenHeight, true, true);
    }
}

} // namespace retropack
