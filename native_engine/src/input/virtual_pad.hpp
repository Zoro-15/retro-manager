#ifndef RETROPACK_VIRTUAL_PAD_HPP
#define RETROPACK_VIRTUAL_PAD_HPP

#include <android/input.h>
#include <GLES2/gl2.h>

#include <cstdint>
#include <cstddef>
#include <vector>
#include <mutex>
#include <string>
#include <array>

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

enum class LeftInputMode {
    JOYSTICK = 0,
    DPAD = 1
};

enum ElementId {
    ELEM_STICK_OR_DPAD = 0,
    ELEM_BTN_A,
    ELEM_BTN_B,
    ELEM_BTN_X,
    ELEM_BTN_Y,
    ELEM_BTN_L,
    ELEM_BTN_R,
    ELEM_BTN_SELECT,
    ELEM_BTN_START,
    ELEM_BTN_MENU,
    ELEM_COUNT
};

struct TouchPoint {
    int id{-1};
    float x{0.0f};
    float y{0.0f};
    bool active{false};
};

struct ElementProfile {
    float normX{0.0f};      // Normalized X [0.0, 1.0]
    float normY{0.0f};      // Normalized Y [0.0, 1.0]
    float scale{1.0f};      // Size multiplier [0.5, 2.0]
    float opacity{0.35f};   // Opacity [0.1, 1.0]
};

struct ControlElement {
    ElementId id{ELEM_STICK_OR_DPAD};
    std::string name;
    std::string label;
    uint32_t mask{0};
    bool isCircle{true};
    bool enabled{true};

    // User customized properties (relative / normalized)
    float normX{0.0f};
    float normY{0.0f};
    float scale{1.0f};
    float opacity{0.35f};

    // Computed screen pixel metrics
    float px{0.0f};
    float py{0.0f};
    float radius{0.0f};
    float width{0.0f};
    float height{0.0f};
};

/**
 * VirtualPad provides a high-responsiveness multi-touch virtual controller
 * featuring:
 *  - Dynamic Floating Thumbstick or Classic Frosted Glass D-Pad modes
 *  - Fully movable, resizable, and opacity-customizable controls (Free Fire HUD editor)
 *  - Console-adaptive button layouts (GBA, SNES, NES, Genesis, PCE)
 *  - Multi-profile layout persistence
 */
class VirtualPad {
public:
    VirtualPad();
    ~VirtualPad();

    // Non-copyable and non-movable
    VirtualPad(const VirtualPad&) = delete;
    VirtualPad& operator=(const VirtualPad&) = delete;

    /**
     * Initialize config persistence file path.
     */
    void initConfig(const std::string& configFilePath);

    /**
     * Set console target layout (GBA, SNES, NES, Genesis, PCE)
     * which dynamically enables/disables X, Y, and Shoulder buttons.
     */
    void setConsoleLayout(ConsoleLayout layout);
    ConsoleLayout getConsoleLayout() const { return m_consoleLayout; }

    /**
     * Left Input Mode (Floating Thumbstick vs Cross D-Pad)
     */
    void setLeftInputMode(LeftInputMode mode);
    LeftInputMode getLeftInputMode() const { return m_leftInputMode; }
    void toggleLeftInputMode();

    /**
     * Custom HUD Editor Mode
     */
    void startCustomizing();
    void stopCustomizing();
    bool isCustomizing() const { return m_isCustomizing; }
    void resetCustomLayout();
    void saveCustomLayout();
    void loadCustomLayout();

    /**
     * Profile switcher (Profile 0 / Profile 1)
     */
    void setActiveProfile(int profile);
    int getActiveProfile() const { return m_activeProfile; }

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
     * Global Configuration.
     */
    void setVisible(bool visible) { m_visible = visible; }
    bool isVisible() const { return m_visible; }
    void setOpacity(float opacity);
    float getOpacity() const { return m_globalOpacity; }

private:
    void updatePointers(const AInputEvent* event);
    void recomputeBitmask();
    int handleCustomHudInput(const AInputEvent* event);
    void updateElementPixelMetrics(int elementIndex);
    void applyConsoleLayoutDefaults();

    bool initGL();
    void renderCircle(float cx, float cy, float radius, float r, float g, float b, float a);
    void renderRing(float cx, float cy, float innerRadius, float outerRadius, float r, float g, float b, float a);
    void renderRect(float x, float y, float w, float h, float r, float g, float b, float a);
    void renderBorderedRect(float x, float y, float w, float h, float borderWidth,
                            float bgR, float bgG, float bgB, float bgA,
                            float borderR, float borderG, float borderB, float borderA);
    void renderDpad(float cx, float cy, float span, float opacity, uint32_t activeMask);
    void renderCustomHudEditor(int screenWidth, int screenHeight);

    mutable std::mutex m_mutex;
    bool m_visible{true};
    float m_globalOpacity{0.35f};
    bool m_menuRequested{false};

    ConsoleLayout m_consoleLayout{ConsoleLayout::GBA};
    LeftInputMode m_leftInputMode{LeftInputMode::JOYSTICK};
    int m_activeProfile{0}; // 0 = Profile 1, 1 = Profile 2
    std::string m_configFilePath;

    bool m_hasX{false};
    bool m_hasY{false};
    bool m_hasShoulders{true};

    int m_screenWidth{0};
    int m_screenHeight{0};
    float m_uiScale{1.0f};

    static constexpr size_t MAX_TOUCH_POINTERS = 10;
    TouchPoint m_pointers[MAX_TOUCH_POINTERS];

    uint32_t m_activeBitmask{0};

    // Array of all 10 Control Elements
    std::array<ControlElement, ELEM_COUNT> m_elements;

    // Profiles storage [profileIndex 0..1][consoleLayout 0..4][elem 0..9]
    ElementProfile m_profiles[2][5][ELEM_COUNT];
    bool m_profilesInitialized{false};

    // PPSSPP Dynamic Floating Thumbstick State
    bool m_stickActive{false};
    int m_stickPointerId{-1};
    float m_stickBaseX{0.0f};
    float m_stickBaseY{0.0f};
    float m_stickNubX{0.0f};
    float m_stickNubY{0.0f};
    float m_stickOuterRadius{78.0f};
    float m_stickNubRadius{34.0f};
    float m_stickMaxDist{65.0f};
    float m_stickDeadzone{12.0f};

    // Custom HUD Editing State
    bool m_isCustomizing{false};
    int m_selectedElement{0};
    bool m_inspectorCollapsed{false};
    bool m_isDraggingElement{false};
    float m_dragTouchStartX{0.0f};
    float m_dragTouchStartY{0.0f};
    float m_elementStartX{0.0f};
    float m_elementStartY{0.0f};
    bool m_draggingOpacitySlider{false};
    bool m_draggingSizeSlider{false};
    std::string m_hudToastMessage;
    int m_hudToastFrames{0};

    // Shader program for procedural vector UI
    bool m_glInitialized{false};
    GLuint m_program{0};
    GLint m_locPosition{-1};
    GLint m_locColor{-1};
    GLint m_locScreenSize{-1};
};

} // namespace retropack

#endif // RETROPACK_VIRTUAL_PAD_HPP
