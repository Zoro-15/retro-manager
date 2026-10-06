#ifndef RETROPACK_LIBRETRO_BRIDGE_HPP
#define RETROPACK_LIBRETRO_BRIDGE_HPP

#include <string>
#include <vector>
#include <functional>
#include <cstdint>
#include <cstddef>
#include <memory>
#include <mutex>

#include <android/asset_manager.h>
#include <android/log.h>

#include "libretro.h"

namespace retropack {

// Audio callback: receives interleaved 16-bit PCM stereo samples and frame count
using AudioSampleBatchCallback = std::function<size_t(const int16_t* data, size_t frames)>;

// Video callback: receives raw frame buffer, geometry, pitch, and pixel format
using VideoRefreshCallback = std::function<void(const void* data, unsigned width, unsigned height, size_t pitch, enum retro_pixel_format format)>;

// Input poll callback: signals input system to poll active hardware/touch states
using InputPollCallback = std::function<void()>;

// Input state callback: queries digital bitmask or analog state for port/device/index/id
using InputStateCallback = std::function<int16_t(unsigned port, unsigned device, unsigned index, unsigned id)>;

// Core shutdown callback: called if core triggers RETRO_ENVIRONMENT_SHUTDOWN
using ShutdownCallback = std::function<void()>;

/**
 * LibretroCoreDispatch encapsulates the function pointer table resolved from
 * a dynamic Libretro core shared library via dlopen/dlsym.
 */
struct LibretroCoreDispatch {
    void* handle{nullptr};

    void (*retro_init)(void){nullptr};
    void (*retro_deinit)(void){nullptr};
    unsigned (*retro_api_version)(void){nullptr};
    void (*retro_get_system_info)(struct retro_system_info *info){nullptr};
    void (*retro_get_system_av_info)(struct retro_system_av_info *info){nullptr};
    void (*retro_set_environment)(retro_environment_t){nullptr};
    void (*retro_set_video_refresh)(retro_video_refresh_t){nullptr};
    void (*retro_set_audio_sample)(retro_audio_sample_t){nullptr};
    void (*retro_set_audio_sample_batch)(retro_audio_sample_batch_t){nullptr};
    void (*retro_set_input_poll)(retro_input_poll_t){nullptr};
    void (*retro_set_input_state)(retro_input_state_t){nullptr};
    void (*retro_set_controller_port_device)(unsigned port, unsigned device){nullptr};
    void (*retro_reset)(void){nullptr};
    void (*retro_run)(void){nullptr};
    size_t (*retro_serialize_size)(void){nullptr};
    bool (*retro_serialize)(void *data, size_t size){nullptr};
    bool (*retro_unserialize)(const void *data, size_t size){nullptr};
    bool (*retro_load_game)(const struct retro_game_info *game){nullptr};
    void (*retro_unload_game)(void){nullptr};
    unsigned (*retro_get_region)(void){nullptr};
    void *(*retro_get_memory_data)(unsigned id){nullptr};
    size_t (*retro_get_memory_size)(unsigned id){nullptr};
};

/**
 * LibretroBridge provides a thread-safe, robust C++ host bridge managing
 * dynamic Libretro core loading, lifecycle, AAssetManager ROM injection,
 * environment callbacks, and audio/video dispatch.
 */
class LibretroBridge {
public:
    LibretroBridge();
    ~LibretroBridge();

    // Prevent copy and move
    LibretroBridge(const LibretroBridge&) = delete;
    LibretroBridge& operator=(const LibretroBridge&) = delete;

    /**
     * Set directories for system BIOS/assets and SRAM/savestates.
     */
    void setSystemDirectory(const std::string& systemDir);
    void setSaveDirectory(const std::string& saveDir);

    /**
     * Register callbacks for AV pipeline and input.
     */
    void setVideoCallback(VideoRefreshCallback cb);
    void setAudioCallback(AudioSampleBatchCallback cb);
    void setInputPollCallback(InputPollCallback cb);
    void setInputStateCallback(InputStateCallback cb);
    void setShutdownCallback(ShutdownCallback cb);

    /**
     * Dynamically load a Libretro core shared library (.so).
     * @param corePath Absolute path or filename of core shared library.
     * @return true if all required symbols were resolved successfully.
     */
    bool loadCore(const std::string& corePath);

    /**
     * Unload active core and release dynamic library handle.
     */
    void unloadCore();

    /**
     * Load a ROM directly from APK assets via AAssetManager.
     * @param assetManager Pointer to Android AAssetManager.
     * @param assetPath Path to ROM inside assets (e.g. "rom.bin" or "assets/rom.bin").
     * @return true if ROM was successfully parsed and core initialized.
     */
    bool loadGameFromAsset(AAssetManager* assetManager, const char* assetPath);

    /**
     * Load a ROM from a standard filesystem path.
     * @param romPath Absolute path to ROM file.
     * @return true if ROM was loaded successfully.
     */
    bool loadGameFromFile(const std::string& romPath);

    /**
     * Unload currently running game.
     */
    void unloadGame();

    /**
     * Reset the running emulation.
     */
    void reset();

    /**
     * Execute a single emulation frame (ticks video, audio, input).
     */
    void runFrame();

    /**
     * Query runtime audio/video specifications from core.
     */
    const struct retro_system_info& getSystemInfo() const { return m_systemInfo; }
    const struct retro_system_av_info& getAvInfo() const { return m_avInfo; }
    enum retro_pixel_format getPixelFormat() const { return m_pixelFormat; }
    double getTargetFps() const { return m_avInfo.timing.fps > 0 ? m_avInfo.timing.fps : 60.0; }
    double getSampleRate() const { return m_avInfo.timing.sample_rate > 0 ? m_avInfo.timing.sample_rate : 44100.0; }

    /**
     * Battery SRAM durability methods.
     */
    size_t getSramSize() const;
    void* getSramData();
    bool saveSramToFile(const std::string& filePath);
    bool loadSramFromFile(const std::string& filePath);

    /**
     * Savestate serialization methods.
     */
    bool saveStateToFile(const std::string& filePath);
    bool loadStateFromFile(const std::string& filePath);

    /**
     * Status queries.
     */
    bool isCoreLoaded() const { return m_coreLoaded; }
    bool isGameLoaded() const { return m_gameLoaded; }
    const std::string& getLastError() const { return m_lastError; }

    /**
     * Global active instance retrieval for C callbacks.
     */
    static LibretroBridge* getActiveInstance();

    /**
     * Static Libretro C callbacks.
     */
    static bool retroEnvironmentCb(unsigned cmd, void *data);
    static void retroVideoRefreshCb(const void *data, unsigned width, unsigned height, size_t pitch);
    static void retroAudioSampleCb(int16_t left, int16_t right);
    static size_t retroAudioSampleBatchCb(const int16_t *data, size_t frames);
    static void retroInputPollCb(void);
    static int16_t retroInputStateCb(unsigned port, unsigned device, unsigned index, unsigned id);
    static void retroLogCb(enum retro_log_level level, const char *fmt, ...);

private:
    void setError(const std::string& message);
    bool handleEnvironment(unsigned cmd, void* data);

    mutable std::mutex m_mutex;
    LibretroCoreDispatch m_core{};
    struct retro_system_info m_systemInfo{};
    struct retro_system_av_info m_avInfo{};

    bool m_coreLoaded{false};
    bool m_gameLoaded{false};
    enum retro_pixel_format m_pixelFormat{RETRO_PIXEL_FORMAT_0RGB1555};

    std::string m_systemDir;
    std::string m_saveDir;
    std::string m_lastError;
    std::string m_loadedCorePath;

    std::vector<uint8_t> m_romBuffer;

    VideoRefreshCallback m_videoCallback;
    AudioSampleBatchCallback m_audioCallback;
    InputPollCallback m_inputPollCallback;
    InputStateCallback m_inputStateCallback;
    ShutdownCallback m_shutdownCallback;

    static LibretroBridge* s_activeInstance;
};

} // namespace retropack

#endif // RETROPACK_LIBRETRO_BRIDGE_HPP
