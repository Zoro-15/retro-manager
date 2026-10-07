#include "osd_menu.hpp"
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

    float cardW = std::min(static_cast<float>(screenWidth) * 0.75f, 560.0f);
    float cardH = std::min(static_cast<float>(screenHeight) * 0.88f, 440.0f);
    float cardX = (static_cast<float>(screenWidth) - cardW) * 0.5f;
    float cardY = (static_cast<float>(screenHeight) - cardH) * 0.5f;

    float padX = 24.0f;
    float currentY = cardY + 56.0f;
    float usableW = cardW - (padX * 2.0f);

    // 1. Slot Selector Pills (5 slots across)
    float pillSpacing = 8.0f;
    float pillW = (usableW - (pillSpacing * 4.0f)) / 5.0f;
    float pillH = 38.0f;

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

    currentY += pillH + 16.0f;

    // 2. Action Buttons
    float btnH = 44.0f;
    float rowSpacing = 10.0f;

    // Row A: Resume Game
    OsdButton btnResume;
    btnResume.action = OSD_RESUME;
    btnResume.x = cardX + padX;
    btnResume.y = currentY;
    btnResume.width = usableW;
    btnResume.height = btnH;
    btnResume.title = "Resume Game";
    m_buttons.push_back(btnResume);
    currentY += btnH + rowSpacing;

    // Row B: Save State & Load State (Dual columns)
    float colW = (usableW - 12.0f) * 0.5f;

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
    btnLoad.x = cardX + padX + colW + 12.0f;
    btnLoad.y = currentY;
    btnLoad.width = colW;
    btnLoad.height = btnH;
    btnLoad.title = "Load State";
    m_buttons.push_back(btnLoad);
    currentY += btnH + rowSpacing;

    // Row C: Fast-Forward Toggle
    OsdButton btnFF;
    btnFF.action = OSD_FAST_FORWARD;
    btnFF.x = cardX + padX;
    btnFF.y = currentY;
    btnFF.width = usableW;
    btnFF.height = btnH;
    btnFF.title = "Fast-Forward";
    m_buttons.push_back(btnFF);
    currentY += btnH + rowSpacing;

    // Row D: Reset & Exit (Dual columns)
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
    btnExit.x = cardX + padX + colW + 12.0f;
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
    renderRect(0, 0, static_cast<float>(screenWidth), static_cast<float>(screenHeight), 0.02f, 0.03f, 0.05f, 0.85f);

    float cardW = std::min(static_cast<float>(screenWidth) * 0.75f, 560.0f);
    float cardH = std::min(static_cast<float>(screenHeight) * 0.88f, 440.0f);
    float cardX = (static_cast<float>(screenWidth) - cardW) * 0.5f;
    float cardY = (static_cast<float>(screenHeight) - cardH) * 0.5f;

    // 2. Dialog Container Card
    renderRect(cardX, cardY, cardW, cardH, 0.10f, 0.12f, 0.16f, 0.98f);

    // 3. Header Accent Bar
    renderRect(cardX, cardY, cardW, 44.0f, 0.18f, 0.22f, 0.32f, 1.0f);

    // 4. Slot Selector Pills
    for (const auto& pill : m_slotPills) {
        bool selected = (pill.slotIndex == m_selectedSlot);
        bool hasSave = m_stateManager ? m_stateManager->hasSlot(pill.slotIndex) : false;

        float r = selected ? 0.20f : 0.14f;
        float g = selected ? 0.55f : 0.16f;
        float b = selected ? 0.90f : 0.22f;
        float a = 1.0f;

        renderRect(pill.x, pill.y, pill.width, pill.height, r, g, b, a);

        // Small indicator dot if slot contains save data
        if (hasSave) {
            float dotR = pill.x + pill.width - 10.0f;
            float dotY = pill.y + 6.0f;
            renderRect(dotR, dotY, 6.0f, 6.0f, 0.2f, 0.85f, 0.3f, 1.0f);
        }
    }

    // 5. Action Buttons
    for (const auto& btn : m_buttons) {
        float r = 0.16f, g = 0.20f, b = 0.26f, a = 1.0f;

        if (btn.action == OSD_RESUME) {
            r = 0.15f; g = 0.55f; b = 0.35f; // Green accent
        } else if (btn.action == OSD_SAVE_STATE) {
            r = 0.20f; g = 0.45f; b = 0.75f; // Blue accent
        } else if (btn.action == OSD_LOAD_STATE) {
            r = 0.40f; g = 0.35f; b = 0.70f; // Purple accent
        } else if (btn.action == OSD_FAST_FORWARD) {
            if (m_fastForwardSpeed > 1) {
                r = 0.85f; g = 0.55f; b = 0.15f; // Orange active FF
            } else {
                r = 0.22f; g = 0.26f; b = 0.34f;
            }
        } else if (btn.action == OSD_RESET) {
            r = 0.65f; g = 0.30f; b = 0.20f; // Warm red
        } else if (btn.action == OSD_EXIT) {
            r = 0.55f; g = 0.18f; b = 0.18f; // Dark red
        }

        renderRect(btn.x, btn.y, btn.width, btn.height, r, g, b, a);
    }

    glDisable(GL_BLEND);
}

} // namespace retropack
