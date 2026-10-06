#ifndef RETROPACK_OSD_MENU_HPP
#define RETROPACK_OSD_MENU_HPP

#include <android/input.h>
#include <GLES2/gl2.h>

#include <functional>
#include <string>
#include <vector>
#include <mutex>

namespace retropack {

class StateManager;

enum OsdAction {
    OSD_NONE = 0,
    OSD_RESUME,
    OSD_SAVE_STATE,
    OSD_LOAD_STATE,
    OSD_FAST_FORWARD,
    OSD_RESET,
    OSD_EXIT
};

struct OsdButton {
    OsdAction action{OSD_NONE};
    int slotIndex{0};
    float x{0.0f};
    float y{0.0f};
    float width{0.0f};
    float height{0.0f};
    const char* title{""};
    bool active{false};
};

/**
 * OsdMenu provides a standalone, in-engine pause overlay menu supporting
 * Save/Load States (Slots 1–5), Fast-Forward toggle (1x–8x), Reset, and Exit.
 */
class OsdMenu {
public:
    OsdMenu();
    ~OsdMenu();

    // Non-copyable and non-movable
    OsdMenu(const OsdMenu&) = delete;
    OsdMenu& operator=(const OsdMenu&) = delete;

    /**
     * Connect external handlers for menu actions.
     */
    void setResumeHandler(std::function<void()> handler) { m_onResume = std::move(handler); }
    void setSaveHandler(std::function<void(int slot)> handler) { m_onSave = std::move(handler); }
    void setLoadHandler(std::function<void(int slot)> handler) { m_onLoad = std::move(handler); }
    void setFastForwardHandler(std::function<void(int speed)> handler) { m_onFastForward = std::move(handler); }
    void setResetHandler(std::function<void()> handler) { m_onReset = std::move(handler); }
    void setExitHandler(std::function<void()> handler) { m_onExit = std::move(handler); }
    void setStateManager(StateManager* stateManager) { m_stateManager = stateManager; }

    /**
     * Menu state management.
     */
    void open();
    void close();
    void toggle();
    bool isOpen() const { return m_open; }

    /**
     * Input processing.
     * @return 1 if handled, 0 otherwise.
     */
    int handleInputEvent(const AInputEvent* event);

    /**
     * Render the OSD overlay in OpenGL ES 2.0.
     */
    void render(int screenWidth, int screenHeight);

    /**
     * Fast-forward speed query (1x, 2x, 4x, 8x).
     */
    int getFastForwardSpeed() const { return m_fastForwardSpeed; }

private:
    void updateLayout(int screenWidth, int screenHeight);
    bool initGL();
    void renderRect(float x, float y, float w, float h, float r, float g, float b, float a);

    mutable std::mutex m_mutex;
    bool m_open{false};
    int m_selectedSlot{1};
    int m_fastForwardSpeed{1}; // 1x, 2x, 4x, 8x
    std::string m_statusMessage;

    StateManager* m_stateManager{nullptr};

    std::function<void()> m_onResume;
    std::function<void(int slot)> m_onSave;
    std::function<void(int slot)> m_onLoad;
    std::function<void(int speed)> m_onFastForward;
    std::function<void()> m_onReset;
    std::function<void()> m_onExit;

    int m_screenWidth{0};
    int m_screenHeight{0};

    std::vector<OsdButton> m_buttons;
    std::vector<OsdButton> m_slotPills;

    bool m_glInitialized{false};
    GLuint m_program{0};
    GLint m_locPosition{-1};
    GLint m_locColor{-1};
    GLint m_locScreenSize{-1};
};

} // namespace retropack

#endif // RETROPACK_OSD_MENU_HPP
