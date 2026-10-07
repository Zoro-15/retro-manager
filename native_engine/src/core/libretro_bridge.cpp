#include "libretro_bridge.hpp"

#include <dlfcn.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdarg>

#include "common/logger.hpp"

#define LOG_TAG "RetroEngine-Bridge"

namespace retropack {

LibretroBridge* LibretroBridge::s_activeInstance = nullptr;

LibretroBridge::LibretroBridge() {
    s_activeInstance = this;
    std::memset(&m_core, 0, sizeof(m_core));
    std::memset(&m_systemInfo, 0, sizeof(m_systemInfo));
    std::memset(&m_avInfo, 0, sizeof(m_avInfo));
}

LibretroBridge::~LibretroBridge() {
    unloadGame();
    unloadCore();
    if (s_activeInstance == this) {
        s_activeInstance = nullptr;
    }
}

LibretroBridge* LibretroBridge::getActiveInstance() {
    return s_activeInstance;
}

void LibretroBridge::setError(const std::string& message) {
    m_lastError = message;
    LOGE("%s", message.c_str());
}

void LibretroBridge::setSystemDirectory(const std::string& systemDir) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_systemDir = systemDir;
}

void LibretroBridge::setSaveDirectory(const std::string& saveDir) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_saveDir = saveDir;
}

void LibretroBridge::setVideoCallback(VideoRefreshCallback cb) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_videoCallback = std::move(cb);
}

void LibretroBridge::setAudioCallback(AudioSampleBatchCallback cb) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_audioCallback = std::move(cb);
}

void LibretroBridge::setInputPollCallback(InputPollCallback cb) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_inputPollCallback = std::move(cb);
}

void LibretroBridge::setInputStateCallback(InputStateCallback cb) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_inputStateCallback = std::move(cb);
}

void LibretroBridge::setShutdownCallback(ShutdownCallback cb) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_shutdownCallback = std::move(cb);
}

#define RESOLVE_REQUIRED(sym) \
    do { \
        *(void**)(&m_core.sym) = dlsym(m_core.handle, #sym); \
        if (!m_core.sym) { \
            setError(std::string("Required Libretro symbol missing: ") + #sym + " (" + dlerror() + ")"); \
            unloadCore(); \
            return false; \
        } \
    } while (0)

#define RESOLVE_OPTIONAL(sym) \
    do { \
        *(void**)(&m_core.sym) = dlsym(m_core.handle, #sym); \
        if (!m_core.sym) { \
            LOGW("Optional Libretro symbol not present: %s", #sym); \
        } \
    } while (0)

bool LibretroBridge::loadCore(const std::string& corePath) {
    std::lock_guard<std::mutex> lock(m_mutex);

    if (m_coreLoaded) {
        unloadCore();
    }

    m_lastError.clear();
    LOGI("Loading Libretro core: %s", corePath.c_str());

    m_core.handle = dlopen(corePath.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!m_core.handle) {
        setError(std::string("dlopen failed for core '") + corePath + "': " + dlerror());
        return false;
    }

    RESOLVE_REQUIRED(retro_init);
    RESOLVE_REQUIRED(retro_deinit);
    RESOLVE_REQUIRED(retro_api_version);
    RESOLVE_REQUIRED(retro_get_system_info);
    RESOLVE_REQUIRED(retro_get_system_av_info);
    RESOLVE_REQUIRED(retro_set_environment);
    RESOLVE_REQUIRED(retro_set_video_refresh);
    RESOLVE_REQUIRED(retro_set_audio_sample);
    RESOLVE_REQUIRED(retro_set_audio_sample_batch);
    RESOLVE_REQUIRED(retro_set_input_poll);
    RESOLVE_REQUIRED(retro_set_input_state);
    RESOLVE_REQUIRED(retro_reset);
    RESOLVE_REQUIRED(retro_run);
    RESOLVE_REQUIRED(retro_load_game);
    RESOLVE_REQUIRED(retro_unload_game);

    RESOLVE_OPTIONAL(retro_set_controller_port_device);
    RESOLVE_OPTIONAL(retro_serialize_size);
    RESOLVE_OPTIONAL(retro_serialize);
    RESOLVE_OPTIONAL(retro_unserialize);
    RESOLVE_OPTIONAL(retro_get_region);
    RESOLVE_OPTIONAL(retro_get_memory_data);
    RESOLVE_OPTIONAL(retro_get_memory_size);

    s_activeInstance = this;

    // Connect core to bridge static callbacks
    m_core.retro_set_environment(retroEnvironmentCb);
    m_core.retro_set_video_refresh(retroVideoRefreshCb);
    m_core.retro_set_audio_sample(retroAudioSampleCb);
    m_core.retro_set_audio_sample_batch(retroAudioSampleBatchCb);
    m_core.retro_set_input_poll(retroInputPollCb);
    m_core.retro_set_input_state(retroInputStateCb);

    unsigned apiVer = m_core.retro_api_version();
    if (apiVer != RETRO_API_VERSION) {
        LOGW("Libretro API version mismatch: core reported %u, expected %u", apiVer, RETRO_API_VERSION);
    }

    m_core.retro_get_system_info(&m_systemInfo);
    LOGI("Core loaded: %s (version: %s, valid_extensions: %s, need_fullpath: %d)",
         m_systemInfo.library_name ? m_systemInfo.library_name : "Unknown",
         m_systemInfo.library_version ? m_systemInfo.library_version : "Unknown",
         m_systemInfo.valid_extensions ? m_systemInfo.valid_extensions : "all",
         m_systemInfo.need_fullpath);

    m_core.retro_init();
    m_coreLoaded = true;
    m_loadedCorePath = corePath;

    return true;
}

void LibretroBridge::unloadCore() {
    std::lock_guard<std::mutex> lock(m_mutex);

    if (m_gameLoaded) {
        if (m_core.retro_unload_game) {
            m_core.retro_unload_game();
        }
        m_gameLoaded = false;
        m_romBuffer.clear();
        m_romBuffer.shrink_to_fit();
    }

    if (m_coreLoaded) {
        if (m_core.retro_deinit) {
            m_core.retro_deinit();
        }
        if (m_core.handle) {
            dlclose(m_core.handle);
            m_core.handle = nullptr;
        }
        m_coreLoaded = false;
        LOGI("Core unloaded: %s", m_loadedCorePath.c_str());
        m_loadedCorePath.clear();
    }
}

bool LibretroBridge::loadGameFromAsset(AAssetManager* assetManager, const char* assetPath) {
    std::lock_guard<std::mutex> lock(m_mutex);

    if (!m_coreLoaded) {
        setError("Cannot load game: No Libretro core is currently loaded");
        return false;
    }

    if (!assetManager || !assetPath) {
        setError("Cannot load game: Null asset manager or asset path");
        return false;
    }

    LOGI("Streaming ROM asset from APK: %s", assetPath);

    // Sanitize path: strip leading '/' and 'assets/' prefix if present
    const char* sanitizedPath = assetPath;
    while (sanitizedPath[0] == '/') sanitizedPath++;
    if (std::strncmp(sanitizedPath, "assets/", 7) == 0) {
        sanitizedPath += 7;
    }
    while (sanitizedPath[0] == '/') sanitizedPath++;

    AAsset* asset = AAssetManager_open(assetManager, sanitizedPath, AASSET_MODE_BUFFER);
    if (!asset) {
        asset = AAssetManager_open(assetManager, assetPath, AASSET_MODE_BUFFER);
    }

    if (!asset) {
        setError(std::string("Failed to open APK asset: ") + assetPath + " (also tried " + sanitizedPath + ")");
        return false;
    }

    off_t romSize = AAsset_getLength(asset);
    if (romSize <= 0) {
        AAsset_close(asset);
        setError(std::string("Invalid ROM size in APK asset: ") + assetPath);
        return false;
    }

    m_romBuffer.resize(static_cast<size_t>(romSize));
    int bytesRead = AAsset_read(asset, m_romBuffer.data(), m_romBuffer.size());
    AAsset_close(asset);

    if (bytesRead != static_cast<int>(romSize)) {
        m_romBuffer.clear();
        setError(std::string("Failed to read complete ROM asset: ") + assetPath);
        return false;
    }

    LOGI("ROM buffer loaded into memory (%zu bytes, need_fullpath: %d)", m_romBuffer.size(), m_systemInfo.need_fullpath);

    bool loaded = false;

    // 1. If core does not require fullpath, attempt in-memory load first
    if (!m_systemInfo.need_fullpath) {
        struct retro_game_info game_info;
        std::memset(&game_info, 0, sizeof(game_info));
        game_info.path = assetPath;
        game_info.data = m_romBuffer.data();
        game_info.size = m_romBuffer.size();
        game_info.meta = "";

        if (m_core.retro_load_game(&game_info)) {
            loaded = true;
            LOGI("Core successfully loaded game in-memory from asset: %s", assetPath);
        } else {
            LOGW("In-memory retro_load_game returned false for %s, trying staged file fallback...", assetPath);
        }
    }

    // 2. If need_fullpath is true or in-memory load failed, stage ROM to disk and load from file
    if (!loaded) {
        std::string stagedRomPath = m_saveDir.empty() ? "/data/data/com.retro.game/files/game.rom" : (m_saveDir + "/game.rom");
        FILE* stagedFile = std::fopen(stagedRomPath.c_str(), "wb");
        if (stagedFile) {
            std::fwrite(m_romBuffer.data(), 1, m_romBuffer.size(), stagedFile);
            std::fflush(stagedFile);
            int fd = fileno(stagedFile);
            if (fd >= 0) fsync(fd);
            std::fclose(stagedFile);

            struct retro_game_info file_game_info;
            std::memset(&file_game_info, 0, sizeof(file_game_info));
            file_game_info.path = stagedRomPath.c_str();
            file_game_info.data = m_systemInfo.need_fullpath ? nullptr : m_romBuffer.data();
            file_game_info.size = m_systemInfo.need_fullpath ? 0 : m_romBuffer.size();
            file_game_info.meta = "";

            if (m_core.retro_load_game(&file_game_info)) {
                loaded = true;
                LOGI("Core successfully loaded staged ROM from filesystem: %s", stagedRomPath.c_str());
            } else {
                LOGE("Core failed to load staged game from filesystem: %s", stagedRomPath.c_str());
            }
        } else {
            LOGE("Failed to open staging ROM file for writing: %s", stagedRomPath.c_str());
        }
    }

    if (!loaded) {
        m_romBuffer.clear();
        setError(std::string("Core failed to load game from asset: ") + assetPath);
        return false;
    }

    m_core.retro_get_system_av_info(&m_avInfo);
    m_gameLoaded = true;

    LOGI("Game loaded successfully. Geometry: %ux%u (max %ux%u, aspect %.2f), FPS: %.2f, Sample Rate: %.1f Hz",
         m_avInfo.geometry.base_width, m_avInfo.geometry.base_height,
         m_avInfo.geometry.max_width, m_avInfo.geometry.max_height,
         m_avInfo.geometry.aspect_ratio,
         m_avInfo.timing.fps,
         m_avInfo.timing.sample_rate);

    return true;
}

bool LibretroBridge::loadGameFromFile(const std::string& romPath) {
    std::lock_guard<std::mutex> lock(m_mutex);

    if (!m_coreLoaded) {
        setError("Cannot load game: No Libretro core is currently loaded");
        return false;
    }

    LOGI("Loading ROM from filesystem: %s", romPath.c_str());

    FILE* file = std::fopen(romPath.c_str(), "rb");
    if (!file) {
        setError("Failed to open ROM file: " + romPath);
        return false;
    }

    std::fseek(file, 0, SEEK_END);
    long size = std::ftell(file);
    std::fseek(file, 0, SEEK_SET);

    if (size <= 0) {
        std::fclose(file);
        setError("ROM file is empty: " + romPath);
        return false;
    }

    m_romBuffer.resize(static_cast<size_t>(size));
    size_t readBytes = std::fread(m_romBuffer.data(), 1, m_romBuffer.size(), file);
    std::fclose(file);

    if (readBytes != m_romBuffer.size()) {
        m_romBuffer.clear();
        setError("Failed to read entire ROM file: " + romPath);
        return false;
    }

    struct retro_game_info game_info;
    std::memset(&game_info, 0, sizeof(game_info));
    game_info.path = romPath.c_str();
    game_info.data = m_romBuffer.data();
    game_info.size = m_romBuffer.size();
    game_info.meta = "";

    if (!m_core.retro_load_game(&game_info)) {
        m_romBuffer.clear();
        setError("Core failed to load game from file: " + romPath);
        return false;
    }

    m_core.retro_get_system_av_info(&m_avInfo);
    m_gameLoaded = true;
    return true;
}

void LibretroBridge::unloadGame() {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_gameLoaded) {
        if (m_core.retro_unload_game) {
            m_core.retro_unload_game();
        }
        m_gameLoaded = false;
        m_romBuffer.clear();
        m_romBuffer.shrink_to_fit();
        LOGI("Game unloaded");
    }
}

void LibretroBridge::reset() {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_gameLoaded && m_core.retro_reset) {
        m_core.retro_reset();
        LOGI("Emulation reset");
    }
}

void LibretroBridge::runFrame() {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_gameLoaded && m_core.retro_run) {
        m_core.retro_run();
    }
}

size_t LibretroBridge::getSramSize() const {
    if (!m_gameLoaded || !m_core.retro_get_memory_size) return 0;
    return m_core.retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
}

void* LibretroBridge::getSramData() {
    if (!m_gameLoaded || !m_core.retro_get_memory_data) return nullptr;
    return m_core.retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
}

bool LibretroBridge::saveSramToFile(const std::string& filePath) {
    std::lock_guard<std::mutex> lock(m_mutex);
    size_t size = getSramSize();
    void* data = getSramData();

    if (!data || size == 0) {
        return false;
    }

    std::string tmpPath = filePath + ".tmp";
    FILE* file = std::fopen(tmpPath.c_str(), "wb");
    if (!file) {
        LOGE("Failed to open SRAM tmp file for writing: %s", tmpPath.c_str());
        return false;
    }

    size_t written = std::fwrite(data, 1, size, file);
    std::fflush(file);
    int fd = fileno(file);
    if (fd >= 0) {
        fsync(fd);
    }
    std::fclose(file);

    if (written != size) {
        LOGE("SRAM write truncated (%zu of %zu bytes written)", written, size);
        std::remove(tmpPath.c_str());
        return false;
    }

    if (std::rename(tmpPath.c_str(), filePath.c_str()) != 0) {
        LOGE("Failed to atomically rename SRAM tmp to %s", filePath.c_str());
        return false;
    }

    LOGI("Battery SRAM saved (%zu bytes) -> %s", size, filePath.c_str());
    return true;
}

bool LibretroBridge::loadSramFromFile(const std::string& filePath) {
    std::lock_guard<std::mutex> lock(m_mutex);
    size_t sramSize = getSramSize();
    void* sramData = getSramData();

    if (!sramData || sramSize == 0) {
        return false;
    }

    FILE* file = std::fopen(filePath.c_str(), "rb");
    if (!file) {
        return false;
    }

    std::fseek(file, 0, SEEK_END);
    long fileSize = std::ftell(file);
    std::fseek(file, 0, SEEK_SET);

    if (fileSize <= 0) {
        std::fclose(file);
        return false;
    }

    size_t bytesToRead = (static_cast<size_t>(fileSize) < sramSize) ? static_cast<size_t>(fileSize) : sramSize;
    size_t readBytes = std::fread(sramData, 1, bytesToRead, file);
    std::fclose(file);

    LOGI("Battery SRAM loaded (%zu bytes) from %s", readBytes, filePath.c_str());
    return true;
}

bool LibretroBridge::saveStateToFile(const std::string& filePath) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (!m_gameLoaded || !m_core.retro_serialize_size || !m_core.retro_serialize) {
        return false;
    }

    size_t stateSize = m_core.retro_serialize_size();
    if (stateSize == 0) return false;

    std::vector<uint8_t> buffer(stateSize);
    if (!m_core.retro_serialize(buffer.data(), buffer.size())) {
        LOGE("retro_serialize failed");
        return false;
    }

    std::string tmpPath = filePath + ".tmp";
    FILE* file = std::fopen(tmpPath.c_str(), "wb");
    if (!file) return false;

    size_t written = std::fwrite(buffer.data(), 1, buffer.size(), file);
    std::fflush(file);
    int fd = fileno(file);
    if (fd >= 0) fsync(fd);
    std::fclose(file);

    if (written != buffer.size()) {
        std::remove(tmpPath.c_str());
        return false;
    }

    std::rename(tmpPath.c_str(), filePath.c_str());
    LOGI("Savestate written (%zu bytes) -> %s", stateSize, filePath.c_str());
    return true;
}

bool LibretroBridge::loadStateFromFile(const std::string& filePath) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (!m_gameLoaded || !m_core.retro_unserialize) {
        return false;
    }

    FILE* file = std::fopen(filePath.c_str(), "rb");
    if (!file) return false;

    std::fseek(file, 0, SEEK_END);
    long size = std::ftell(file);
    std::fseek(file, 0, SEEK_SET);

    if (size <= 0) {
        std::fclose(file);
        return false;
    }

    std::vector<uint8_t> buffer(static_cast<size_t>(size));
    size_t readBytes = std::fread(buffer.data(), 1, buffer.size(), file);
    std::fclose(file);

    if (readBytes != buffer.size()) return false;

    bool success = m_core.retro_unserialize(buffer.data(), buffer.size());
    if (success) {
        LOGI("Savestate loaded (%zu bytes) from %s", buffer.size(), filePath.c_str());
    } else {
        LOGE("retro_unserialize failed for %s", filePath.c_str());
    }
    return success;
}

bool LibretroBridge::handleEnvironment(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            if (!data) return false;
            auto fmt = *static_cast<const enum retro_pixel_format*>(data);
            switch (fmt) {
                case RETRO_PIXEL_FORMAT_0RGB1555:
                case RETRO_PIXEL_FORMAT_XRGB8888:
                case RETRO_PIXEL_FORMAT_RGB565:
                    m_pixelFormat = fmt;
                    LOGI("Pixel format set to %d", fmt);
                    return true;
                default:
                    LOGE("Unsupported pixel format: %d", fmt);
                    return false;
            }
        }

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
            if (!data) return false;
            *static_cast<const char**>(data) = m_systemDir.c_str();
            return true;
        }

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
            if (!data) return false;
            *static_cast<const char**>(data) = m_saveDir.c_str();
            return true;
        }

        case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
            if (!data) return false;
            *static_cast<bool*>(data) = true;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            if (!data) return false;
            m_avInfo.geometry = *static_cast<const struct retro_game_geometry*>(data);
            LOGI("Geometry updated: %ux%u (aspect %.2f)",
                 m_avInfo.geometry.base_width, m_avInfo.geometry.base_height, m_avInfo.geometry.aspect_ratio);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            if (!data) return false;
            m_avInfo = *static_cast<const struct retro_system_av_info*>(data);
            LOGI("System AV info updated: %ux%u (aspect %.2f, fps %.2f)",
                 m_avInfo.geometry.base_width, m_avInfo.geometry.base_height, m_avInfo.geometry.aspect_ratio, m_avInfo.timing.fps);
            return true;
        }

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            if (!data) return false;
            auto* cb = static_cast<struct retro_log_callback*>(data);
            cb->log = retroLogCb;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            if (!data) return false;
            auto* var = static_cast<struct retro_variable*>(data);
            if (var && var->key) {
                // Default unhandled variables
                var->value = nullptr;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_SET_VARIABLES: {
            return true;
        }

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            if (!data) return false;
            *static_cast<bool*>(data) = false;
            return true;
        }

        case RETRO_ENVIRONMENT_SHUTDOWN: {
            LOGI("Core requested shutdown");
            if (m_shutdownCallback) {
                m_shutdownCallback();
            }
            return true;
        }

        default:
            return false;
    }
}

/* Static Callbacks */

bool LibretroBridge::retroEnvironmentCb(unsigned cmd, void *data) {
    if (s_activeInstance) {
        return s_activeInstance->handleEnvironment(cmd, data);
    }
    return false;
}

void LibretroBridge::retroVideoRefreshCb(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (s_activeInstance && s_activeInstance->m_videoCallback) {
        s_activeInstance->m_videoCallback(data, width, height, pitch, s_activeInstance->m_pixelFormat);
    }
}

void LibretroBridge::retroAudioSampleCb(int16_t left, int16_t right) {
    int16_t frame[2] = {left, right};
    retroAudioSampleBatchCb(frame, 1);
}

size_t LibretroBridge::retroAudioSampleBatchCb(const int16_t *data, size_t frames) {
    if (s_activeInstance && s_activeInstance->m_audioCallback) {
        return s_activeInstance->m_audioCallback(data, frames);
    }
    return frames;
}

void LibretroBridge::retroInputPollCb(void) {
    if (s_activeInstance && s_activeInstance->m_inputPollCallback) {
        s_activeInstance->m_inputPollCallback();
    }
}

int16_t LibretroBridge::retroInputStateCb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (s_activeInstance && s_activeInstance->m_inputStateCallback) {
        return s_activeInstance->m_inputStateCallback(port, device, index, id);
    }
    return 0;
}

void LibretroBridge::retroLogCb(enum retro_log_level level, const char *fmt, ...) {
    int androidLevel = ANDROID_LOG_INFO;
    switch (level) {
        case RETRO_LOG_DEBUG: androidLevel = ANDROID_LOG_DEBUG; break;
        case RETRO_LOG_INFO:  androidLevel = ANDROID_LOG_INFO;  break;
        case RETRO_LOG_WARN:  androidLevel = ANDROID_LOG_WARN;  break;
        case RETRO_LOG_ERROR: androidLevel = ANDROID_LOG_ERROR; break;
        default: break;
    }

    va_list args;
    va_start(args, fmt);
    __android_log_vprint(androidLevel, "RetroCore", fmt, args);
    va_end(args);
}

} // namespace retropack
