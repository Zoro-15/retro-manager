#include "osd_menu.hpp"
#include "font_renderer.hpp"
#include "storage/state_manager.hpp"

#include <android/log.h>
#include <cmath>
#include <algorithm>
#include <cstring>

#include "common/logger.hpp"

#define LOG_TAG "RetroEngine-Osd"

namespace retropack {

namespace {

const char* kOsdVertexShader =
    "attribute vec2 aPosition;\n"
    "uniform vec2 uScreenSize;\n"
    "void main() {\n"
    "    vec2 zeroToOne = aPosition / uScreenSize;\n"
    "    vec2 zeroToTwo = zeroToOne * 2.0;\n"
    "    vec2 clipSpace = zeroToTwo - 1.0;\n"
    "    gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);\n"
    "}\n";

const char* kOsdFragmentShader =
    "precision mediump float;\n"
    "uniform vec4 uColor;\n"
    "void main() {\n"
    "    gl_FragColor = uColor;\n"
    "}\n";

static bool isInside(float px, float py, float rx, float ry, float rw, float rh) {
    return (px >= rx && px <= rx + rw && py >= ry && py <= ry + rh);
}

} // namespace

OsdMenu::OsdMenu() = default;

OsdMenu::~OsdMenu() {
    if (m_program != 0) {
        glDeleteProgram(m_program);
        m_program = 0;
    }
}

void OsdMenu::open() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_open = true;
    LOGI("OSD menu opened");
}

void OsdMenu::close() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_open = false;
    LOGI("OSD menu closed");
}

void OsdMenu::toggle() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_open = !m_open;
    LOGI("OSD menu toggled: %s", m_open ? "OPEN" : "CLOSED");
}

void OsdMenu::updateLayout(int screenWidth, int screenHeight) {
    m_screenWidth = screenWidth;
    m_screenHeight = screenHeight;

    m_buttons.clear();
    m_slotPills.clear();

    if (screenWidth <= 0 || screenHeight <= 0) return;

    float baseScale = static_cast<float>(std::min(screenWidth, screenHeight)) / 720.0f;
    if (baseScale < 0.90f) baseScale = 0.90f;
    if (baseScale > 1.80f) baseScale = 1.80f;
    m_uiScale = baseScale;

    // Generous card sizing for touch screens
    float cardW = std::min(static_cast<float>(screenWidth) * 0.84f, 660.0f * baseScale);
    float cardH = std::min(static_cast<float>(screenHeight) * 0.92f, 540.0f * baseScale);
    float cardX = (static_cast<float>(screenWidth) - cardW) * 0.5f;
    float cardY = (static_cast<float>(screenHeight) - cardH) * 0.5f;

    float padX = 22.0f * baseScale;
    float headerH = 46.0f * baseScale;
    float currentY = cardY + headerH + 12.0f * baseScale;
    float usableW = cardW - (padX * 2.0f);

    // 1. Slot Selector Pills (5 slots across)
    float pillSpacing = 8.0f * baseScale;
    float pillW = (usableW - (pillSpacing * 4.0f)) / 5.0f;
    float pillH = 40.0f * baseScale;

    for (int i = 1; i <= 5; ++i) {
        OsdButton pill;
        pill.action = OSD_NONE;
        pill.slotIndex = i;
        pill.x = cardX + padX + static_cast<float>(i - 1) * (pillW + pillSpacing);
        pill.y = currentY;
        pill.width = pillW;
        pill.height = pillH;
        pill.title = "SLOT";
        m_slotPills.push_back(pill);
    }

    currentY += pillH + 14.0f * baseScale;

    // 2. Action Buttons
    float btnH = 46.0f * baseScale;
    float rowSpacing = 9.0f * baseScale;
    float colW = (usableW - (12.0f * baseScale)) * 0.5f;

    // Row A: Resume Game (Large Full Width)
    OsdButton btnResume;
    btnResume.action = OSD_RESUME;
    btnResume.x = cardX + padX;
    btnResume.y = currentY;
    btnResume.width = usableW;
    btnResume.height = btnH;
    btnResume.title = "Resume Game";
    m_buttons.push_back(btnResume);
    currentY += btnH + rowSpacing;

    // Row B: Customize Controls & Left Input Mode (Dual columns)
    OsdButton btnCustomize;
    btnCustomize.action = OSD_CUSTOMIZE_CONTROLS;
    btnCustomize.x = cardX + padX;
    btnCustomize.y = currentY;
    btnCustomize.width = colW;
    btnCustomize.height = btnH;
    btnCustomize.title = "Customize Controls";
    m_buttons.push_back(btnCustomize);

    OsdButton btnInputMode;
    btnInputMode.action = OSD_TOGGLE_INPUT_MODE;
    btnInputMode.x = cardX + padX + colW + (12.0f * baseScale);
    btnInputMode.y = currentY;
    btnInputMode.width = colW;
    btnInputMode.height = btnH;
    btnInputMode.title = "Input Mode";
    m_buttons.push_back(btnInputMode);
    currentY += btnH + rowSpacing;

    // Row C: Save State & Load State (Dual columns)
    OsdButton btnSave;
    btnSave.action = OSD_SAVE_STATE;
    btnSave.x = cardX + padX;
    btnSave.y = currentY;
    btnSave.width = colW;
    btnSave.height = btnH;
    btnSave.title = "Save State";
    m_buttons.push_back(btnSave);

    OsdButton btnLoad;
    btnLoad.action = OSD_LOAD_STATE;
    btnLoad.x = cardX + padX + colW + (12.0f * baseScale);
    btnLoad.y = currentY;
    btnLoad.width = colW;
    btnLoad.height = btnH;
    btnLoad.title = "Load State";
    m_buttons.push_back(btnLoad);
    currentY += btnH + rowSpacing;

    // Row D: Fast-Forward Toggle (Full width)
    OsdButton btnFF;
    btnFF.action = OSD_FAST_FORWARD;
    btnFF.x = cardX + padX;
    btnFF.y = currentY;
    btnFF.width = usableW;
    btnFF.height = btnH;
    btnFF.title = "Fast-Forward";
    m_buttons.push_back(btnFF);
    currentY += btnH + rowSpacing;

    // Row E: Reset & Exit (Dual columns)
    OsdButton btnReset;
    btnReset.action = OSD_RESET;
    btnReset.x = cardX + padX;
    btnReset.y = currentY;
    btnReset.width = colW;
    btnReset.height = btnH;
    btnReset.title = "Reset Emulation";
    m_buttons.push_back(btnReset);

    OsdButton btnExit;
    btnExit.action = OSD_EXIT;
    btnExit.x = cardX + padX + colW + (12.0f * baseScale);
    btnExit.y = currentY;
    btnExit.width = colW;
    btnExit.height = btnH;
    btnExit.title = "Exit to Launcher";
    m_buttons.push_back(btnExit);
}

int OsdMenu::handleInputEvent(const AInputEvent* event) {
    if (!m_open || AInputEvent_getType(event) != AINPUT_EVENT_TYPE_MOTION) {
        return 0;
    }

    int32_t action = AMotionEvent_getAction(event);
    int32_t actionMasked = action & AMOTION_EVENT_ACTION_MASK;

    if (actionMasked != AMOTION_EVENT_ACTION_UP) {
        return 1; // Consume touch during menu display
    }

    size_t pointerIndex = (action & AMOTION_EVENT_ACTION_POINTER_INDEX_MASK) >> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT;
    float px = AMotionEvent_getX(event, pointerIndex);
    float py = AMotionEvent_getY(event, pointerIndex);

    std::lock_guard<std::mutex> lock(m_mutex);

    // 1. Check Slot Pills
    for (const auto& pill : m_slotPills) {
        if (isInside(px, py, pill.x, pill.y, pill.width, pill.height)) {
            m_selectedSlot = pill.slotIndex;
            LOGI("OSD slot selected: %d", m_selectedSlot);
            return 1;
        }
    }

    // 2. Check Action Buttons
    for (const auto& btn : m_buttons) {
        if (isInside(px, py, btn.x, btn.y, btn.width, btn.height)) {
            switch (btn.action) {
                case OSD_RESUME:
                    m_open = false;
                    if (m_onResume) m_onResume();
                    break;

                case OSD_CUSTOMIZE_CONTROLS:
                    m_open = false;
                    if (m_onCustomizeControls) m_onCustomizeControls();
                    break;

                case OSD_TOGGLE_INPUT_MODE:
                    if (m_onToggleInputMode) m_onToggleInputMode();
                    break;

                case OSD_SAVE_STATE:
                    if (m_onSave) m_onSave(m_selectedSlot);
                    break;

                case OSD_LOAD_STATE:
                    if (m_onLoad) m_onLoad(m_selectedSlot);
                    break;

                case OSD_FAST_FORWARD:
                    if (m_fastForwardSpeed == 1) m_fastForwardSpeed = 2;
                    else if (m_fastForwardSpeed == 2) m_fastForwardSpeed = 4;
                    else if (m_fastForwardSpeed == 4) m_fastForwardSpeed = 8;
                    else m_fastForwardSpeed = 1;

                    LOGI("Fast-forward toggled to %dx", m_fastForwardSpeed);
                    if (m_onFastForward) m_onFastForward(m_fastForwardSpeed);
                    break;

                case OSD_RESET:
                    m_open = false;
                    if (m_onReset) m_onReset();
                    break;

                case OSD_EXIT:
                    if (m_onExit) m_onExit();
                    break;

                default:
                    break;
            }
            return 1;
        }
    }

    return 1;
}

bool OsdMenu::initGL() {
    if (m_glInitialized) return true;

    GLuint vs = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vs, 1, &kOsdVertexShader, nullptr);
    glCompileShader(vs);

    GLuint fs = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fs, 1, &kOsdFragmentShader, nullptr);
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

void OsdMenu::renderRect(float x, float y, float w, float h, float r, float g, float b, float a) {
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

void OsdMenu::renderBorderedRect(float x, float y, float w, float h, float borderWidth,
                                float bgR, float bgG, float bgB, float bgA,
                                float borderR, float borderG, float borderB, float borderA) {
    renderRect(x, y, w, h, bgR, bgG, bgB, bgA);
    renderRect(x, y, w, borderWidth, borderR, borderG, borderB, borderA);
    renderRect(x, y + h - borderWidth, w, borderWidth, borderR, borderG, borderB, borderA);
    renderRect(x, y, borderWidth, h, borderR, borderG, borderB, borderA);
    renderRect(x + w - borderWidth, y, borderWidth, h, borderR, borderG, borderB, borderA);
}

void OsdMenu::render(int screenWidth, int screenHeight) {
    if (!m_open || screenWidth <= 0 || screenHeight <= 0) return;

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

    // 1. Semi-transparent backdrop overlay
    renderRect(0, 0, static_cast<float>(screenWidth), static_cast<float>(screenHeight), 0.02f, 0.03f, 0.05f, 0.88f);

    float cardW = std::min(static_cast<float>(screenWidth) * 0.84f, 660.0f * m_uiScale);
    float cardH = std::min(static_cast<float>(screenHeight) * 0.92f, 540.0f * m_uiScale);
    float cardX = (static_cast<float>(screenWidth) - cardW) * 0.5f;
    float cardY = (static_cast<float>(screenHeight) - cardH) * 0.5f;

    // 2. Dialog Container Card with glowing border
    renderBorderedRect(cardX, cardY, cardW, cardH, 2.5f,
                       0.08f, 0.10f, 0.15f, 0.98f,
                       0.25f, 0.45f, 0.75f, 1.0f);

    // 3. Header Accent Bar
    float headerH = 46.0f * m_uiScale;
    renderRect(cardX, cardY, cardW, headerH, 0.14f, 0.18f, 0.28f, 1.0f);
    renderRect(cardX, cardY + headerH - 2.0f, cardW, 2.0f, 0.30f, 0.60f, 1.0f, 1.0f);

    // 4. Slot Selector Pills
    for (const auto& pill : m_slotPills) {
        bool selected = (pill.slotIndex == m_selectedSlot);
        bool hasSave = m_stateManager ? m_stateManager->hasSlot(pill.slotIndex) : false;

        float bgR = selected ? 0.20f : 0.12f;
        float bgG = selected ? 0.50f : 0.16f;
        float bgB = selected ? 0.90f : 0.24f;

        float bdR = selected ? 0.45f : 0.25f;
        float bdG = selected ? 0.75f : 0.32f;
        float bdB = selected ? 1.00f : 0.45f;

        renderBorderedRect(pill.x, pill.y, pill.width, pill.height, 1.5f,
                           bgR, bgG, bgB, 1.0f,
                           bdR, bdG, bdB, 1.0f);

        // Indicator dot if slot contains save data
        if (hasSave) {
            float dotR = pill.x + pill.width - 10.0f * m_uiScale;
            float dotY = pill.y + 6.0f * m_uiScale;
            float dotSz = 6.0f * m_uiScale;
            renderRect(dotR, dotY, dotSz, dotSz, 0.20f, 0.95f, 0.35f, 1.0f);
        }
    }

    // 5. Action Buttons
    for (const auto& btn : m_buttons) {
        float bgR = 0.16f, bgG = 0.20f, bgB = 0.26f;
        float bdR = 0.30f, bdG = 0.38f, bdB = 0.50f;

        if (btn.action == OSD_RESUME) {
            bgR = 0.12f; bgG = 0.48f; bgB = 0.28f; // Emerald green
            bdR = 0.25f; bdG = 0.85f; bdB = 0.50f;
        } else if (btn.action == OSD_CUSTOMIZE_CONTROLS) {
            bgR = 0.14f; bgG = 0.35f; bgB = 0.60f; // Glowing Cyan / Blue
            bdR = 0.30f; bdG = 0.70f; bdB = 1.00f;
        } else if (btn.action == OSD_TOGGLE_INPUT_MODE) {
            bgR = 0.25f; bgG = 0.18f; bgB = 0.42f; // Indigo / Amber
            bdR = 0.65f; bdG = 0.45f; bdB = 0.95f;
        } else if (btn.action == OSD_SAVE_STATE) {
            bgR = 0.18f; bgG = 0.38f; bgB = 0.72f; // Cobalt blue
            bdR = 0.35f; bdG = 0.65f; bdB = 1.00f;
        } else if (btn.action == OSD_LOAD_STATE) {
            bgR = 0.35f; bgG = 0.28f; bgB = 0.65f; // Purple
            bdR = 0.65f; bdG = 0.50f; bdB = 0.95f;
        } else if (btn.action == OSD_FAST_FORWARD) {
            if (m_fastForwardSpeed > 1) {
                bgR = 0.80f; bgG = 0.50f; bgB = 0.12f; // Orange active FF
                bdR = 1.00f; bdG = 0.75f; bdB = 0.25f;
            } else {
                bgR = 0.18f; bgG = 0.22f; bgB = 0.30f;
                bdR = 0.35f; bdG = 0.42f; bdB = 0.55f;
            }
        } else if (btn.action == OSD_RESET) {
            bgR = 0.60f; bgG = 0.25f; bgB = 0.18f; // Warm red
            bdR = 0.90f; bdG = 0.45f; bdB = 0.35f;
        } else if (btn.action == OSD_EXIT) {
            bgR = 0.50f; bgG = 0.15f; bgB = 0.15f; // Crimson
            bdR = 0.85f; bdG = 0.30f; bdB = 0.30f;
        }

        renderBorderedRect(btn.x, btn.y, btn.width, btn.height, 1.5f,
                           bgR, bgG, bgB, 1.0f,
                           bdR, bdG, bdB, 1.0f);
    }

    glDisable(GL_BLEND);

    // 6. Draw Crisp Text Labels using FontRenderer
    FontRenderer& font = FontRenderer::instance();

    // 6a. Header Title
    font.renderText("RETROPACK MENU", cardX + cardW * 0.5f, cardY + headerH * 0.5f, 2.4f * m_uiScale,
                    1.0f, 1.0f, 1.0f, 1.0f, screenWidth, screenHeight, true, true);

    // 6b. Slot Selector Labels
    for (const auto& pill : m_slotPills) {
        std::string slotLabel = "SLOT " + std::to_string(pill.slotIndex);
        bool selected = (pill.slotIndex == m_selectedSlot);
        float textR = selected ? 1.0f : 0.80f;
        float textG = selected ? 1.0f : 0.88f;
        float textB = selected ? 1.0f : 0.95f;

        font.renderText(slotLabel, pill.x + pill.width * 0.5f, pill.y + pill.height * 0.5f, 1.8f * m_uiScale,
                        textR, textG, textB, 1.0f, screenWidth, screenHeight, true, true);
    }

    // 6c. Action Button Labels
    for (const auto& btn : m_buttons) {
        std::string labelText = btn.title;
        if (btn.action == OSD_FAST_FORWARD) {
            labelText = "Fast-Forward (" + std::to_string(m_fastForwardSpeed) + "x)";
        } else if (btn.action == OSD_TOGGLE_INPUT_MODE) {
            LeftInputMode mode = m_inputModeQuery ? m_inputModeQuery() : LeftInputMode::JOYSTICK;
            labelText = (mode == LeftInputMode::DPAD) ? "Input: [D-Pad]" : "Input: [Joystick]";
        } else if (btn.action == OSD_SAVE_STATE) {
            labelText = "Save Slot " + std::to_string(m_selectedSlot);
        } else if (btn.action == OSD_LOAD_STATE) {
            labelText = "Load Slot " + std::to_string(m_selectedSlot);
        }

        font.renderText(labelText, btn.x + btn.width * 0.5f, btn.y + btn.height * 0.5f, 2.0f * m_uiScale,
                        1.0f, 1.0f, 1.0f, 1.0f, screenWidth, screenHeight, true, true);
    }
}

} // namespace retropack
