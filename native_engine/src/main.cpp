#include <android/log.h>
#include <android/asset_manager.h>
#include <android/native_window.h>
#include <android/configuration.h>
#include <android_native_app_glue.h>

#include <chrono>
#include <thread>
#include <string>
#include <vector>
#include <memory>
#include <sys/stat.h>
#include <dirent.h>

#include "core/libretro_bridge.hpp"
#include "video/gles_renderer.hpp"
#include "audio/aaudio_player.hpp"
#include "input/virtual_pad.hpp"
#include "ui/osd_menu.hpp"
#include "storage/state_manager.hpp"

#define LOG_TAG "RetroEngine-Main"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct EngineContext {
    struct android_app* app{nullptr};
    retropack::LibretroBridge bridge;
    retropack::GlesRenderer renderer;
    retropack::AAudioPlayer audioPlayer;
    retropack::VirtualPad virtualPad;
    retropack::OsdMenu osdMenu;
    retropack::StateManager stateManager;

    bool running{false};
    bool hasFocus{false};
    bool windowInitialized{false};
    int fastForwardMultiplier{1};

    std::string internalDataPath;
    std::string systemDir;
    std::string saveDir;
    std::string activeCorePath;

    std::chrono::steady_clock::time_point lastFrameTime;
    std::chrono::nanoseconds baseFrameInterval{16666666}; // Default ~60 FPS (16.66ms)
    std::chrono::nanoseconds currentFrameInterval{16666666};

    void updateFrameTiming() {
        double fps = bridge.getTargetFps();
        if (fps <= 0.0 || fps > 240.0) {
            fps = 60.0;
        }
        int64_t ns = static_cast<int64_t>(1000000000.0 / fps);
        baseFrameInterval = std::chrono::nanoseconds(ns);
        recomputeEffectiveInterval();
        LOGI("Frame timing configured: %.2f FPS (base: %lld ns, active: %lld ns)",
             fps, static_cast<long long>(ns), static_cast<long long>(currentFrameInterval.count()));
    }

    void setFastForward(int multiplier) {
        fastForwardMultiplier = (multiplier >= 1) ? multiplier : 1;
        recomputeEffectiveInterval();
        LOGI("Fast-forward multiplier set to %dx", fastForwardMultiplier);
    }

    void recomputeEffectiveInterval() {
        int64_t ns = baseFrameInterval.count() / fastForwardMultiplier;
        currentFrameInterval = std::chrono::nanoseconds(ns);
    }

    void persistSram() {
        stateManager.saveSram();
    }
};

static void ensureDirectoryExists(const std::string& path) {
    struct stat st;
    if (stat(path.c_str(), &st) != 0) {
        mkdir(path.c_str(), 0755);
    }
}

static std::string discoverCoreLibrary(struct android_app* app) {
    if (!app || !app->activity) return "";

    // 1. Search in app's native library directory
    const char* internalPath = app->activity->internalDataPath;
    if (internalPath) {
        std::string libDir = std::string(internalPath) + "/../lib";
        DIR* dir = opendir(libDir.c_str());
        if (dir) {
            struct dirent* entry;
            while ((entry = readdir(dir)) != nullptr) {
                std::string name = entry->d_name;
                // Match standard libretro cores: libretro_*.so
                if (name.rfind("libretro_", 0) == 0 && name.rfind(".so") == name.length() - 3) {
                    // Ignore our own host engine library: libretro_engine.so
                    if (name != "libretro_engine.so") {
                        std::string fullPath = libDir + "/" + name;
                        closedir(dir);
                        LOGI("Discovered Libretro core in libDir: %s", fullPath.c_str());
                        return fullPath;
                    }
                }
            }
            closedir(dir);
        }
    }

    // 2. Fallback candidate core names directly resolvable via system linker
    const char* fallbackCores[] = {
        "libretro_mgba.so",
        "libretro_snes9x.so",
        "libretro_genesis_plus_gx.so",
        "libretro_fceumm.so",
        "libretro_mednafen_pce_fast.so"
    };

    for (const char* coreName : fallbackCores) {
        void* handle = dlopen(coreName, RTLD_NOW | RTLD_NOLOAD);
        if (handle) {
            dlclose(handle);
            return coreName;
        }
    }

    // Return default mGBA core name
    return "libretro_mgba.so";
}

static void handleEngineCommand(struct android_app* app, int32_t cmd) {
    auto* ctx = static_cast<EngineContext*>(app->userData);
    if (!ctx) return;

    switch (cmd) {
        case APP_CMD_INIT_WINDOW:
            LOGI("Lifecycle event: APP_CMD_INIT_WINDOW");
            if (app->window != nullptr) {
                if (ctx->renderer.initEGL(app->window)) {
                    ctx->windowInitialized = true;
                    int screenW = ctx->renderer.getScreenWidth();
                    int screenH = ctx->renderer.getScreenHeight();

                    ctx->virtualPad.updateLayout(screenW, screenH);

                    if (ctx->bridge.isGameLoaded()) {
                        float aspect = ctx->bridge.getAvInfo().geometry.aspect_ratio;
                        ctx->renderer.setTargetAspectRatio(aspect);
                    }
                }
            }
            break;

        case APP_CMD_TERM_WINDOW:
            LOGI("Lifecycle event: APP_CMD_TERM_WINDOW");
            ctx->windowInitialized = false;
            ctx->renderer.terminateEGL();
            break;

        case APP_CMD_GAINED_FOCUS:
            LOGI("Lifecycle event: APP_CMD_GAINED_FOCUS");
            ctx->hasFocus = true;
            if (ctx->running && ctx->bridge.isGameLoaded() && !ctx->osdMenu.isOpen()) {
                ctx->audioPlayer.start();
            }
            break;

        case APP_CMD_LOST_FOCUS:
            LOGI("Lifecycle event: APP_CMD_LOST_FOCUS");
            ctx->hasFocus = false;
            ctx->audioPlayer.pause();
            ctx->persistSram();
            break;

        case APP_CMD_PAUSE:
            LOGI("Lifecycle event: APP_CMD_PAUSE");
            ctx->running = false;
            ctx->audioPlayer.pause();
            ctx->persistSram();
            break;

        case APP_CMD_RESUME:
            LOGI("Lifecycle event: APP_CMD_RESUME");
            ctx->running = true;
            ctx->lastFrameTime = std::chrono::steady_clock::now();
            if (ctx->hasFocus && ctx->bridge.isGameLoaded() && !ctx->osdMenu.isOpen()) {
                ctx->audioPlayer.start();
            }
            break;

        case APP_CMD_SAVE_STATE:
            LOGI("Lifecycle event: APP_CMD_SAVE_STATE");
            ctx->persistSram();
            break;

        case APP_CMD_CONFIG_CHANGED:
            LOGI("Lifecycle event: APP_CMD_CONFIG_CHANGED");
            if (ctx->windowInitialized) {
                int screenW = ctx->renderer.getScreenWidth();
                int screenH = ctx->renderer.getScreenHeight();
                ctx->virtualPad.updateLayout(screenW, screenH);

                if (ctx->bridge.isGameLoaded()) {
                    ctx->renderer.setTargetAspectRatio(ctx->bridge.getAvInfo().geometry.aspect_ratio);
                }
            }
            break;

        case APP_CMD_LOW_MEMORY:
            LOGW("Lifecycle event: APP_CMD_LOW_MEMORY");
            break;

        case APP_CMD_DESTROY:
            LOGI("Lifecycle event: APP_CMD_DESTROY");
            ctx->persistSram();
            ctx->audioPlayer.destroy();
            ctx->renderer.terminateEGL();
            ctx->running = false;
            break;

        default:
            break;
    }
}

static int32_t handleEngineInput(struct android_app* app, AInputEvent* event) {
    auto* ctx = static_cast<EngineContext*>(app->userData);
    if (!ctx) return 0;

    // 1. If OSD menu is active, route touches exclusively to OSD Menu
    if (ctx->osdMenu.isOpen()) {
        return ctx->osdMenu.handleInputEvent(event);
    }

    // 2. Otherwise route to VirtualPad multi-touch controller
    int handled = ctx->virtualPad.handleInputEvent(event);

    // 3. Check if menu trigger was pressed
    if (ctx->virtualPad.consumeMenuRequest()) {
        ctx->osdMenu.open();
        ctx->audioPlayer.pause();
        ctx->persistSram();
    }

    return handled;
}

} // namespace

/**
 * Pure NativeActivity Main Entry Point
 */
void android_main(struct android_app* app) {
    LOGI("=================================================");
    LOGI("   RetroPack Pure C++ Native Engine Starting     ");
    LOGI("=================================================");

    EngineContext ctx;
    ctx.app = app;
    app->userData = &ctx;
    app->onAppCmd = handleEngineCommand;
    app->onInputEvent = handleEngineInput;

    // 1. Configure filesystem directories
    if (app->activity && app->activity->internalDataPath) {
        ctx.internalDataPath = app->activity->internalDataPath;
    } else {
        ctx.internalDataPath = "/data/data/com.retro.game/files";
    }

    ctx.systemDir = ctx.internalDataPath + "/system";
    ctx.saveDir = ctx.internalDataPath + "/saves";

    ensureDirectoryExists(ctx.internalDataPath);
    ensureDirectoryExists(ctx.systemDir);
    ensureDirectoryExists(ctx.saveDir);

    ctx.bridge.setSystemDirectory(ctx.systemDir);
    ctx.bridge.setSaveDirectory(ctx.saveDir);
    ctx.bridge.setShutdownCallback([app]() {
        LOGI("Shutdown requested: terminating NativeActivity");
        ANativeActivity_finish(app->activity);
    });

    // 2. Initialize StateManager
    ctx.stateManager.init(ctx.saveDir, &ctx.bridge);
    ctx.osdMenu.setStateManager(&ctx.stateManager);

    // 3. Configure OSD Menu Handlers
    ctx.osdMenu.setResumeHandler([&ctx]() {
        if (ctx.hasFocus && ctx.running) {
            ctx.audioPlayer.start();
        }
    });

    ctx.osdMenu.setSaveHandler([&ctx](int slot) {
        ctx.stateManager.saveSlot(slot);
    });

    ctx.osdMenu.setLoadHandler([&ctx](int slot) {
        ctx.stateManager.loadSlot(slot);
    });

    ctx.osdMenu.setFastForwardHandler([&ctx](int speed) {
        ctx.setFastForward(speed);
        if (speed > 1) {
            // Mute audio during fast forward to prevent buffer distortion
            ctx.audioPlayer.pause();
        } else if (ctx.hasFocus && ctx.running && !ctx.osdMenu.isOpen()) {
            ctx.audioPlayer.start();
        }
    });

    ctx.osdMenu.setResetHandler([&ctx]() {
        ctx.bridge.reset();
        if (ctx.hasFocus && ctx.running) {
            ctx.audioPlayer.start();
        }
    });

    ctx.osdMenu.setExitHandler([app, &ctx]() {
        ctx.persistSram();
        ANativeActivity_finish(app->activity);
    });

    // 4. Connect LibretroBridge AV and Input delegates
    ctx.bridge.setVideoCallback([&ctx](const void* data, unsigned width, unsigned height, size_t pitch, enum retro_pixel_format format) {
        ctx.renderer.updateFrame(data, width, height, pitch, format);
    });

    ctx.bridge.setAudioCallback([&ctx](const int16_t* data, size_t frames) -> size_t {
        if (ctx.fastForwardMultiplier > 1) {
            return frames; // Drop audio frames during fast-forward
        }
        return ctx.audioPlayer.writeSamples(data, frames);
    });

    ctx.bridge.setInputPollCallback([&ctx]() {
        ctx.virtualPad.poll();
    });

    ctx.bridge.setInputStateCallback([&ctx](unsigned port, unsigned device, unsigned index, unsigned id) -> int16_t {
        if (ctx.osdMenu.isOpen()) {
            return 0; // Inhibit game input while in OSD menu
        }
        return ctx.virtualPad.getInputState(port, device, index, id);
    });

    // 5. Discover and load Libretro Core
    ctx.activeCorePath = discoverCoreLibrary(app);
    LOGI("Discovered target core: %s", ctx.activeCorePath.c_str());

    if (!ctx.bridge.loadCore(ctx.activeCorePath)) {
        LOGE("Failed to load Libretro core: %s. Error: %s",
             ctx.activeCorePath.c_str(), ctx.bridge.getLastError().c_str());
    }

    // 6. Ingest and mount ROM directly from APK assets
    if (app->activity && app->activity->assetManager && ctx.bridge.isCoreLoaded()) {
        const char* romCandidatePaths[] = {
            "rom.bin",
            "assets/rom.bin",
            "game.rom",
            "assets/game.rom"
        };

        bool loaded = false;
        for (const char* romPath : romCandidatePaths) {
            if (ctx.bridge.loadGameFromAsset(app->activity->assetManager, romPath)) {
                LOGI("Successfully loaded ROM from asset: %s", romPath);
                loaded = true;
                break;
            }
        }

        if (loaded) {
            ctx.updateFrameTiming();

            // Configure audio stream with core sample rate
            int sampleRate = static_cast<int>(ctx.bridge.getSampleRate());
            ctx.audioPlayer.init(sampleRate);
            ctx.audioPlayer.start();

            // Set renderer aspect ratio
            float aspect = ctx.bridge.getAvInfo().geometry.aspect_ratio;
            ctx.renderer.setTargetAspectRatio(aspect);

            // Restore battery SRAM save if present
            ctx.stateManager.loadSram();
        } else {
            LOGE("Failed to load ROM from APK assets: %s", ctx.bridge.getLastError().c_str());
        }
    }

    ctx.running = true;
    ctx.lastFrameTime = std::chrono::steady_clock::now();

    // 7. Native Engine Hardware Synchronized Frame Loop
    while (true) {
        int events = 0;
        struct android_poll_source* source = nullptr;

        // Poll events: non-blocking (0) during active play, blocking (-1) when idle/paused
        int pollTimeout = (ctx.running && ctx.hasFocus && ctx.windowInitialized && ctx.bridge.isGameLoaded()) ? 0 : -1;

        while (ALooper_pollOnce(pollTimeout, nullptr, &events, (void**)&source) >= 0) {
            if (source != nullptr) {
                source->process(app, source);
            }

            if (app->destroyRequested != 0) {
                LOGI("NativeActivity destroy requested, exiting frame loop");
                ctx.persistSram();
                ctx.audioPlayer.destroy();
                ctx.renderer.terminateEGL();
                ctx.bridge.unloadGame();
                ctx.bridge.unloadCore();
                return;
            }
        }

        // Execute and render emulation frame if active
        if (ctx.running && ctx.hasFocus && ctx.windowInitialized && ctx.bridge.isGameLoaded()) {
            auto now = std::chrono::steady_clock::now();
            auto elapsed = now - ctx.lastFrameTime;

            if (elapsed >= ctx.currentFrameInterval) {
                ctx.lastFrameTime = now;

                // Step core emulation (if not in OSD menu)
                if (!ctx.osdMenu.isOpen()) {
                    ctx.bridge.runFrame();
                }

                // Render game texture quad
                ctx.renderer.renderFrame();

                // Render in-engine UI layers
                int screenW = ctx.renderer.getScreenWidth();
                int screenH = ctx.renderer.getScreenHeight();

                if (ctx.osdMenu.isOpen()) {
                    ctx.osdMenu.render(screenW, screenH);
                } else {
                    ctx.virtualPad.render(screenW, screenH);
                }
            } else {
                // Yield briefly to avoid CPU starvation
                auto remaining = ctx.currentFrameInterval - elapsed;
                if (remaining > std::chrono::microseconds(500)) {
                    std::this_thread::sleep_for(std::chrono::microseconds(500));
                }
            }
        }
    }
}
