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
#include <dlfcn.h>

#include "core/libretro_bridge.hpp"
#include "video/gles_renderer.hpp"
#include "audio/aaudio_player.hpp"
#include "input/virtual_pad.hpp"
#include "ui/osd_menu.hpp"
#include "storage/state_manager.hpp"

#include <csignal>
#include <cstdlib>
#include <unistd.h>
#include <cstdio>
#include <ctime>
#include <fstream>

#define LOG_TAG "RetroEngine-Main"

namespace {

static std::vector<std::string> g_logFilePaths;
static std::mutex g_logMutex;

static void ensureDirectoryRecursive(const std::string& path) {
    if (path.empty()) return;
    std::string current = "";
    for (size_t i = 0; i < path.length(); ++i) {
        current += path[i];
        if (path[i] == '/' || i == path.length() - 1) {
            struct stat st;
            if (stat(current.c_str(), &st) != 0) {
                mkdir(current.c_str(), 0777);
            }
        }
    }
}

static void writeEngineLog(const char* level, const char* fmt, ...) {
    char buffer[2048];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);

    // 1. Android system logcat
    int androidLevel = ANDROID_LOG_INFO;
    if (strcmp(level, "ERROR") == 0) androidLevel = ANDROID_LOG_ERROR;
    else if (strcmp(level, "WARN") == 0) androidLevel = ANDROID_LOG_WARN;
    else if (strcmp(level, "DEBUG") == 0) androidLevel = ANDROID_LOG_DEBUG;
    __android_log_print(androidLevel, LOG_TAG, "%s", buffer);

    // 2. Persistent file logging across all registered targets
    std::lock_guard<std::mutex> lock(g_logMutex);
    time_t now = time(nullptr);
    struct tm* tmInfo = localtime(&now);
    char timeBuf[64];
    strftime(timeBuf, sizeof(timeBuf), "%Y-%m-%d %H:%M:%S", tmInfo);

    for (const auto& path : g_logFilePaths) {
        if (!path.empty()) {
            FILE* f = fopen(path.c_str(), "a");
            if (f) {
                fprintf(f, "[%s] [%s] %s\n", timeBuf, level, buffer);
                fflush(f);
                fclose(f);
            }
        }
    }
}

#define LOGI(...) writeEngineLog("INFO", __VA_ARGS__)
#define LOGW(...) writeEngineLog("WARN", __VA_ARGS__)
#define LOGE(...) writeEngineLog("ERROR", __VA_ARGS__)

static void signalCrashHandler(int sig) {
    const char* sigName = "UNKNOWN";
    switch (sig) {
        case SIGSEGV: sigName = "SIGSEGV (Segmentation Fault)"; break;
        case SIGABRT: sigName = "SIGABRT (Abort)"; break;
        case SIGBUS:  sigName = "SIGBUS (Bus Error)"; break;
        case SIGFPE:  sigName = "SIGFPE (Floating Point Exception)"; break;
        case SIGILL:  sigName = "SIGILL (Illegal Instruction)"; break;
    }
    LOGE("FATAL CRASH SIGNAL RECEIVED: %s (%d)", sigName, sig);
    _exit(128 + sig);
}

static void installCrashHandlers() {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = signalCrashHandler;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGSEGV, &sa, nullptr);
    sigaction(SIGABRT, &sa, nullptr);
    sigaction(SIGBUS, &sa, nullptr);
    sigaction(SIGFPE, &sa, nullptr);
    sigaction(SIGILL, &sa, nullptr);
}

} // namespace

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

static std::string scanDirectoryForCores(const std::string& dirPath) {
    DIR* dir = opendir(dirPath.c_str());
    if (!dir) return "";

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        std::string name = entry->d_name;
        // Match standard libretro cores: libretro_*.so or *_libretro_android.so
        if ((name.rfind("libretro_", 0) == 0 || name.find("libretro") != std::string::npos) &&
            name.rfind(".so") == name.length() - 3) {
            if (name != "libretro_engine.so") {
                std::string fullPath = dirPath + "/" + name;
                closedir(dir);
                LOGI("Discovered Libretro core: %s", fullPath.c_str());
                return fullPath;
            }
        }
    }
    closedir(dir);
    return "";
}

static std::string discoverCoreLibrary(struct android_app* app) {
    if (!app || !app->activity) return "";

    std::string preferredCore = "";

    // 0. Inspect assets/retropack.json to extract configured core name if available
    if (app->activity->assetManager) {
        AAsset* configAsset = AAssetManager_open(app->activity->assetManager, "retropack.json", AASSET_MODE_BUFFER);
        if (!configAsset) {
            configAsset = AAssetManager_open(app->activity->assetManager, "assets/retropack.json", AASSET_MODE_BUFFER);
        }
        if (configAsset) {
            off_t len = AAsset_getLength(configAsset);
            if (len > 0 && len < 65536) {
                std::vector<char> buf(len + 1, 0);
                AAsset_read(configAsset, buf.data(), len);
                std::string jsonStr(buf.data());
                // Simple search for "core": "<core_id>"
                size_t coreKeyPos = jsonStr.find("\"core\"");
                if (coreKeyPos != std::string::npos) {
                    size_t colonPos = jsonStr.find(':', coreKeyPos);
                    if (colonPos != std::string::npos) {
                        size_t quoteStart = jsonStr.find('\"', colonPos);
                        if (quoteStart != std::string::npos) {
                            size_t quoteEnd = jsonStr.find('\"', quoteStart + 1);
                            if (quoteEnd != std::string::npos) {
                                preferredCore = jsonStr.substr(quoteStart + 1, quoteEnd - quoteStart - 1);
                                LOGI("Configured core in retropack.json: %s", preferredCore.c_str());
                            }
                        }
                    }
                }
            }
            AAsset_close(configAsset);
        }
    }

    if (!preferredCore.empty()) {
        std::string preferredLibName = "libretro_" + preferredCore + ".so";
        void* handle = dlopen(preferredLibName.c_str(), RTLD_NOW);
        if (handle) {
            dlclose(handle);
            LOGI("Discovered configured core via dynamic linker: %s", preferredLibName.c_str());
            return preferredLibName;
        }
    }

    // 1. Query JNI ApplicationInfo.nativeLibraryDir
    if (app->activity->vm && app->activity->clazz) {
        JNIEnv* env = nullptr;
        JavaVM* vm = app->activity->vm;
        if (vm->AttachCurrentThread(&env, nullptr) == JNI_OK && env) {
            jclass activityClass = env->GetObjectClass(app->activity->clazz);
            if (activityClass) {
                jmethodID getAppInfoMethod = env->GetMethodID(activityClass, "getApplicationInfo", "()Landroid/content/pm/ApplicationInfo;");
                if (getAppInfoMethod) {
                    jobject appInfo = env->CallObjectMethod(app->activity->clazz, getAppInfoMethod);
                    if (appInfo) {
                        jclass appInfoClass = env->GetObjectClass(appInfo);
                        jfieldID nativeLibDirField = env->GetFieldID(appInfoClass, "nativeLibraryDir", "Ljava/lang/String;");
                        if (nativeLibDirField) {
                            jstring nativeLibDir = (jstring)env->GetObjectField(appInfo, nativeLibDirField);
                            if (nativeLibDir) {
                                const char* pathStr = env->GetStringUTFChars(nativeLibDir, nullptr);
                                if (pathStr) {
                                    std::string found = scanDirectoryForCores(pathStr);
                                    env->ReleaseStringUTFChars(nativeLibDir, pathStr);
                                    if (!found.empty()) {
                                        return found;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. Search in app's internal library directory
    const char* internalPath = app->activity->internalDataPath;
    if (internalPath) {
        std::string libDir = std::string(internalPath) + "/../lib";
        std::string found = scanDirectoryForCores(libDir);
        if (!found.empty()) return found;
    }

    // 3. Candidate core names directly resolvable via Android dynamic linker
    const char* fallbackCores[] = {
        "libretro_mgba.so",
        "libretro_snes9x.so",
        "libretro_genesis_plus_gx.so",
        "libretro_fceumm.so",
        "libretro_mednafen_pce_fast.so",
        "mgba_libretro_android.so",
        "snes9x_libretro_android.so",
        "genesis_plus_gx_libretro_android.so",
        "fceumm_libretro_android.so",
        "mednafen_pce_fast_libretro_android.so"
    };

    for (const char* coreName : fallbackCores) {
        void* handle = dlopen(coreName, RTLD_NOW);
        if (handle) {
            dlclose(handle);
            LOGI("Discovered Libretro core via dynamic linker: %s", coreName);
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
    installCrashHandlers();

    EngineContext ctx;
    ctx.app = app;
    app->userData = &ctx;
    app->onAppCmd = handleEngineCommand;
    app->onInputEvent = handleEngineInput;

    // 1. Configure filesystem directories & logging paths
    if (app->activity && app->activity->internalDataPath) {
        ctx.internalDataPath = app->activity->internalDataPath;
    } else {
        ctx.internalDataPath = "/data/data/com.retro.game/files";
    }

    ensureDirectoryRecursive(ctx.internalDataPath);

    // Derive package name & app slug
    std::string packageName = "retro_game";
    if (app->activity && app->activity->internalDataPath) {
        std::string raw(app->activity->internalDataPath);
        // Typical format: /data/user/0/<packageName>/files or /data/data/<packageName>/files
        size_t filesPos = raw.rfind("/files");
        if (filesPos != std::string::npos) {
            std::string sub = raw.substr(0, filesPos);
            size_t slashPos = sub.rfind('/');
            if (slashPos != std::string::npos) {
                packageName = sub.substr(slashPos + 1);
            }
        }
    }

    std::string appSlug = packageName;
    size_t lastDot = packageName.rfind('.');
    if (lastDot != std::string::npos) {
        appSlug = packageName.substr(lastDot + 1);
    }

    // Register all internal and public Download log destinations
    {
        std::lock_guard<std::mutex> lock(g_logMutex);
        g_logFilePaths.clear();
        
        // 1. App-private internal storage: files/engine.log, launch.log, game_launch.log
        g_logFilePaths.push_back(ctx.internalDataPath + "/engine.log");
        g_logFilePaths.push_back(ctx.internalDataPath + "/launch.log");
        g_logFilePaths.push_back(ctx.internalDataPath + "/game_launch.log");
        g_logFilePaths.push_back(ctx.internalDataPath + "/" + appSlug + ".log");

        // 2. App-specific external storage if available
        if (app->activity && app->activity->externalDataPath) {
            std::string extPath(app->activity->externalDataPath);
            ensureDirectoryRecursive(extPath);
            g_logFilePaths.push_back(extPath + "/" + appSlug + ".log");
            g_logFilePaths.push_back(extPath + "/retropack_runtime.log");
        }

        // 3. Public Download logs directory: /sdcard/Download/logs/<app_slug>/<app_slug>.log
        std::vector<std::string> downloadBases = {
            "/sdcard/Download",
            "/storage/emulated/0/Download"
        };

        for (const auto& base : downloadBases) {
            std::string slugDir = base + "/logs/" + appSlug;
            ensureDirectoryRecursive(slugDir);
            g_logFilePaths.push_back(slugDir + "/" + appSlug + ".log");

            std::string generalLogsDir = base + "/logs";
            ensureDirectoryRecursive(generalLogsDir);
            g_logFilePaths.push_back(generalLogsDir + "/" + appSlug + ".log");
            g_logFilePaths.push_back(generalLogsDir + "/game_launch.log");

            std::string retroPackLogsDir = base + "/RetroPack/Logs";
            ensureDirectoryRecursive(retroPackLogsDir);
            g_logFilePaths.push_back(retroPackLogsDir + "/" + appSlug + ".log");
        }
    }

    LOGI("=================================================");
    LOGI("   RetroPack Pure C++ Native Engine Starting     ");
    LOGI("=================================================");
    LOGI("Package Name       : %s", packageName.c_str());
    LOGI("App Slug           : %s", appSlug.c_str());
    LOGI("Internal Data Path : %s", ctx.internalDataPath.c_str());
    LOGI("Public Log Target  : /sdcard/Download/logs/%s/%s.log", appSlug.c_str(), appSlug.c_str());

    ctx.systemDir = ctx.internalDataPath + "/system";
    ctx.saveDir = ctx.internalDataPath + "/saves";

    ensureDirectoryRecursive(ctx.systemDir);
    ensureDirectoryRecursive(ctx.saveDir);

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
