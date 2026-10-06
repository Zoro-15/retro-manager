#ifndef RETROPACK_STATE_MANAGER_HPP
#define RETROPACK_STATE_MANAGER_HPP

#include <string>
#include <vector>
#include <cstdint>
#include <cstddef>
#include <mutex>

namespace retropack {

class LibretroBridge;

struct StateSlotInfo {
    int slotNumber{1};
    bool exists{false};
    uint64_t lastModifiedSec{0};
    size_t sizeBytes{0};
};

/**
 * StateManager provides crash-consistent, atomic durability for
 * Battery SRAM saves and multi-slot savestates (Slots 1–5).
 */
class StateManager {
public:
    StateManager();
    ~StateManager();

    // Non-copyable and non-movable
    StateManager(const StateManager&) = delete;
    StateManager& operator=(const StateManager&) = delete;

    /**
     * Initialize the state manager with storage path and core bridge.
     * @param saveDirectory Base folder for save files (e.g. /data/data/.../files/saves).
     * @param bridge Pointer to active LibretroBridge.
     */
    void init(const std::string& saveDirectory, LibretroBridge* bridge);

    /**
     * Battery SRAM Durability.
     */
    bool saveSram();
    bool loadSram();
    bool hasSram() const;

    /**
     * Savestate Slot Management (1-indexed, Slots 1 through 5).
     */
    bool saveSlot(int slot);
    bool loadSlot(int slot);
    bool hasSlot(int slot) const;
    StateSlotInfo getSlotInfo(int slot) const;

    /**
     * Auto-save handler invoked on lifecycle events.
     */
    void triggerAutoSave();

    /**
     * Query slot file path.
     */
    std::string getSlotPath(int slot) const;
    std::string getSramPath() const;

private:
    bool atomicWriteFile(const std::string& targetPath, const uint8_t* data, size_t size);

    mutable std::mutex m_mutex;
    std::string m_saveDirectory;
    LibretroBridge* m_bridge{nullptr};
};

} // namespace retropack

#endif // RETROPACK_STATE_MANAGER_HPP
