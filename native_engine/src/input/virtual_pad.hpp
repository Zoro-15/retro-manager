#ifndef RETROPACK_VIRTUAL_PAD_HPP
#define RETROPACK_VIRTUAL_PAD_HPP

#include <android/input.h>
#include <GLES2/gl2.h>

#include <cstdint>
#include <cstddef>
#include <vector>
#include <mutex>
#include <string>

#include "core/libretro.h"

namespace retropack {

enum VirtualButtonId {
    BTN_UP       = (1 << 0),
    BTN_DOWN     = (1 << 1),
    BTN_LEFT     = (1 << 2),
    BTN_RIGHT    = (1 << 3),
    BTN_A        = (1 << 4),
    BTN_B        = (1 << 5),
    BTN_X        = (1 << 6),
    BTN_Y        = (1 << 7),
    BTN_L        = (1 << 8),
    BTN_R        = (1 << 9),
    BTN_SELECT   = (1 << 10),
    BTN_START    = (1 << 11),
    BTN_MENU     = (1 << 12)
};

enum class ConsoleLayout {
    GBA,
    SNES,
    NES,
    GENESIS,
    PCE
};

struct TouchPoint {
    int id{-1};
    float x{0.0f};
    float y{0.0f};
    bool active{false};
};

struct CircleButton {
    float x{0.0f};
    float y{0.0f};
    float radius{0.0f};
    uint32_t mask{0};
    const char* label{""};
    bool enabled{true};
};

struct RectButton {
    float x{0.0f};
    float y{0.0f};
    float width{0.0f};
    float height{0.0f};
    uint32_t mask{0};
    const char* label{""};
    bool enabled{true};
};

/**
 * VirtualPad provides a high-responsiveness multi-touch virtual controller
 * featuring a PPSSPP-style dynamic Floating Thumbstick, console-adaptive
 * button layouts (GBA, SNES, NES, Genesis, PCE), and modern translucent graphics.
 */
class VirtualPad {
public:
    VirtualPad();
    ~VirtualPad();

    // Non-copyable and non-movable
    VirtualPad(const VirtualPad&) = delete;
    VirtualPad& operator=(const VirtualPad&) = delete;

    /**
     * Set console target layout (GBA, SNES, NES, Genesis, PCE)
     * which dynamically enables/disables X, Y, and Shoulder buttons.
     */
    void setConsoleLayout(ConsoleLayout layout);
    ConsoleLayout getConsoleLayout() const { return m_consoleLayout; }

    /**
     * Update screen geometry and compute responsive control layouts.
     */
    void updateLayout(int screenWidth, int screenHeight);

    /**
     * Process Android motion events from AInputQueue.
     * @param event Pointer to AInputEvent.
     * @return 1 if handled, 0 otherwise.
     */
    int handleInputEvent(const AInputEvent* event);

    /**
     * Poll and update the active button state bitmask.
     */
    void poll();

    /**
     * Libretro input callback query.
     */
    int16_t getInputState(unsigned port, unsigned device, unsigned index, unsigned id) const;

    /**
     * Query and consume in-engine OSD menu open request.
     */
    bool consumeMenuRequest();

    /**
     * Render the virtual controller overlay in OpenGL ES 2.0.
     */
    void render(int screenWidth, int screenHeight);

    /**
     * Configuration.
     */
    void setVisible(bool visible) { m_visible = visible; }
    bool isVisible() const { return m_visible; }
    void setOpacity(float opacity) { m_opacity = opacity; }
    float getOpacity() const { return m_opacity; }

private:
    void updatePointers(const AInputEvent* event);
    void recomputeBitmask();
    bool initGL();
    void renderCircle(float cx, float cy, float radius, float r, float g, float b, float a);
    void renderRing(float cx, float cy, float innerRadius, float outerRadius, float r, float g, float b, float a);
    void renderRect(float x, float y, float w, float h, float r, float g, float b, float a);

    mutable std::mutex m_mutex;
    bool m_visible{true};
    float m_opacity{0.30f}; // Default 30% translucent glass style
    bool m_menuRequested{false};

    ConsoleLayout m_consoleLayout{ConsoleLayout::GBA};
    bool m_hasX{false};
    bool m_hasY{false};
    bool m_hasShoulders{true};

    int m_screenWidth{0};
    int m_screenHeight{0};

    static constexpr size_t MAX_TOUCH_POINTERS = 10;
    TouchPoint m_pointers[MAX_TOUCH_POINTERS];

    uint32_t m_activeBitmask{0};

    // PPSSPP Dynamic Floating Thumbstick State
    bool m_stickActive{false};
    int m_stickPointerId{-1};
    float m_defaultStickX{0.0f};
    float m_defaultStickY{0.0f};
    float m_stickBaseX{0.0f};
    float m_stickBaseY{0.0f};
    float m_stickNubX{0.0f};
    float m_stickNubY{0.0f};
    float m_stickOuterRadius{70.0f};
    float m_stickNubRadius{32.0f};
    float m_stickMaxDist{60.0f};
    float m_stickDeadzone{12.0f};

    // Action Buttons
    CircleButton m_btnA;
    CircleButton m_btnB;
    CircleButton m_btnX;
    CircleButton m_btnY;

    // Shoulder & Utility Buttons
    RectButton m_btnL;
    RectButton m_btnR;
    RectButton m_btnSelect;
    RectButton m_btnStart;
    RectButton m_btnMenu;

    // Shader program for procedural vector UI
    bool m_glInitialized{false};
    GLuint m_program{0};
    GLint m_locPosition{-1};
    GLint m_locColor{-1};
    GLint m_locScreenSize{-1};
};

} // namespace retropack

#endif // RETROPACK_VIRTUAL_PAD_HPP
