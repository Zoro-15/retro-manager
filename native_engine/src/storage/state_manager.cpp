#include "state_manager.hpp"
#include "core/libretro_bridge.hpp"

#include <android/log.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>

#include "common/logger.hpp"

#define LOG_TAG "RetroEngine-State"

namespace retropack {

StateManager::StateManager() = default;

StateManager::~StateManager() = default;

void StateManager::init(const std::string& saveDirectory, LibretroBridge* bridge) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_saveDirectory = saveDirectory;
    m_bridge = bridge;

    struct stat st;
    if (stat(m_saveDirectory.c_str(), &st) != 0) {
        mkdir(m_saveDirectory.c_str(), 0755);
    }

    LOGI("StateManager initialized at directory: %s", m_saveDirectory.c_str());
}

std::string StateManager::getSramPath() const {
    return m_saveDirectory + "/game.sav";
}

std::string StateManager::getSlotPath(int slot) const {
    if (slot < 1) slot = 1;
    if (slot > 5) slot = 5;
    return m_saveDirectory + "/slot_" + std::to_string(slot) + ".state";
}

bool StateManager::hasSram() const {
    std::string path = getSramPath();
    struct stat st;
    return (stat(path.c_str(), &st) == 0 && st.st_size > 0);
}

bool StateManager::saveSram() {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (!m_bridge || !m_bridge->isGameLoaded()) return false;

    std::string path = getSramPath();
    bool result = m_bridge->saveSramToFile(path);
    if (result) {
        LOGI("Battery SRAM persisted successfully to %s", path.c_str());
    }
    return result;
}

bool StateManager::loadSram() {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (!m_bridge || !m_bridge->isGameLoaded()) return false;

    std::string path = getSramPath();
    if (!hasSram()) return false;

    bool result = m_bridge->loadSramFromFile(path);
    if (result) {
        LOGI("Battery SRAM restored from %s", path.c_str());
    }
    return result;
}

bool StateManager::saveSlot(int slot) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (!m_bridge || !m_bridge->isGameLoaded()) return false;

    std::string path = getSlotPath(slot);
    bool result = m_bridge->saveStateToFile(path);
    if (result) {
        LOGI("Savestate Slot %d saved to %s", slot, path.c_str());
    } else {
        LOGE("Failed to save Savestate Slot %d to %s", slot, path.c_str());
    }
    return result;
}

bool StateManager::loadSlot(int slot) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (!m_bridge || !m_bridge->isGameLoaded()) return false;

    std::string path = getSlotPath(slot);
    if (!hasSlot(slot)) {
        LOGW("Savestate Slot %d does not exist on disk (%s)", slot, path.c_str());
        return false;
    }

    bool result = m_bridge->loadStateFromFile(path);
    if (result) {
        LOGI("Savestate Slot %d loaded successfully from %s", slot, path.c_str());
    } else {
        LOGE("Failed to load Savestate Slot %d from %s", slot, path.c_str());
    }
    return result;
}

bool StateManager::hasSlot(int slot) const {
    std::string path = getSlotPath(slot);
    struct stat st;
    return (stat(path.c_str(), &st) == 0 && st.st_size > 0);
}

StateSlotInfo StateManager::getSlotInfo(int slot) const {
    StateSlotInfo info;
    info.slotNumber = slot;

    std::string path = getSlotPath(slot);
    struct stat st;
    if (stat(path.c_str(), &st) == 0 && st.st_size > 0) {
        info.exists = true;
        info.sizeBytes = static_cast<size_t>(st.st_size);
        info.lastModifiedSec = static_cast<uint64_t>(st.st_mtime);
    } else {
        info.exists = false;
        info.sizeBytes = 0;
        info.lastModifiedSec = 0;
    }

    return info;
}

void StateManager::triggerAutoSave() {
    saveSram();
}

} // namespace retropack
