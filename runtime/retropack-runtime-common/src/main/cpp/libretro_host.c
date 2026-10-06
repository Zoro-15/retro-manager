#include "libretro_host.h"

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <dlfcn.h>
#include <sys/stat.h>
#include <jni.h>

#ifdef __ANDROID__
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#define LOG_TAG "RetroPack-LibretroHost"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...) do { printf("[INFO] " __VA_ARGS__); printf("\n"); } while(0)
#define LOGW(...) do { printf("[WARN] " __VA_ARGS__); printf("\n"); } while(0)
#define LOGE(...) do { fprintf(stderr, "[ERROR] " __VA_ARGS__); fprintf(stderr, "\n"); } while(0)
#endif

#define AUDIO_DEFAULT_CAPACITY (16 * 1024)
#define VIDEO_INITIAL_WIDTH 256
#define VIDEO_INITIAL_HEIGHT 240
#define VIDEO_MAX_FALLBACK_WIDTH 2048
#define VIDEO_MAX_FALLBACK_HEIGHT 2048

/* RetroKey bit positions matching RetroKey.kt */
#define RETROKEY_A        (1 << 0)
#define RETROKEY_B        (1 << 1)
#define RETROKEY_SELECT   (1 << 2)
#define RETROKEY_START    (1 << 3)
#define RETROKEY_RIGHT    (1 << 4)
#define RETROKEY_LEFT     (1 << 5)
#define RETROKEY_UP       (1 << 6)
#define RETROKEY_DOWN     (1 << 7)
#define RETROKEY_R        (1 << 8)
#define RETROKEY_L        (1 << 9)
#define RETROKEY_X        (1 << 10)
#define RETROKEY_Y        (1 << 11)
#define RETROKEY_C        (1 << 12)
#define RETROKEY_Z        (1 << 13)
#define RETROKEY_L2       (1 << 14)
#define RETROKEY_R2       (1 << 15)
#define RETROKEY_L3       (1 << 16)
#define RETROKEY_R3       (1 << 17)

static LibretroHostState g_host = {
    .core_loaded = false,
    .game_loaded = false,
    .initialized = false,
    .system_dir = {0},
    .save_dir = {0},
    .loaded_core_path = {0},
    .pixel_format = RETRO_PIXEL_FORMAT_0RGB1555,
    .video_buffer = NULL,
    .video_buffer_capacity = 0,
    .video_width = 0,
    .video_height = 0,
    .base_width = 0,
    .base_height = 0,
    .max_width = 0,
    .max_height = 0,
    .aspect_ratio = 4.0f / 3.0f,
    .target_fps = 60.0,
    .sample_rate = 44100.0,
    .audio_rb = NULL,
    .input_mask = 0,
    .analog_left_x = 0,
    .analog_left_y = 0,
    .analog_right_x = 0,
    .analog_right_y = 0,
    .pointer_x = 0,
    .pointer_y = 0,
    .pointer_pressed = false,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .rom_data = NULL,
    .rom_size = 0,
};

/* Cached JNI Reflection Globals */
static jclass g_byte_buffer_class = NULL;
static jmethodID g_byte_buffer_order = NULL;
static jmethodID g_byte_buffer_as_int_buffer = NULL;
static jobject g_byte_order_native = NULL;

LibretroHostState* host_get_instance(void) {
    return &g_host;
}

/* Forward declarations of callbacks */
static bool retro_environment_cb(unsigned cmd, void *data);
static void retro_video_refresh_cb(const void *data, unsigned width, unsigned height, size_t pitch);
static void retro_audio_sample_cb(int16_t left, int16_t right);
static size_t retro_audio_sample_batch_cb(const int16_t *data, size_t frames);
static void retro_input_poll_cb(void);
static int16_t retro_input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id);
static void core_log_cb(enum retro_log_level level, const char *fmt, ...);

static char g_last_error[1024] = {0};

static void set_last_error(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vsnprintf(g_last_error, sizeof(g_last_error), fmt, args);
    va_end(args);
    LOGE("HostError: %s", g_last_error);
}

#define RESOLVE_REQUIRED_SYM(core_struct, sym_name) \
    do { \
        core_struct.sym_name = (void*) dlsym(core_struct.handle, #sym_name); \
        if (!core_struct.sym_name) { \
            set_last_error("Failed to resolve required Libretro symbol '%s': %s", #sym_name, dlerror()); \
            return false; \
        } \
    } while(0)

#define RESOLVE_OPTIONAL_SYM(core_struct, sym_name) \
    do { \
        core_struct.sym_name = (void*) dlsym(core_struct.handle, #sym_name); \
        if (!core_struct.sym_name) { \
            LOGW("Optional Libretro symbol not present: %s", #sym_name); \
        } \
    } while(0)

bool host_init(const char* system_dir, const char* save_dir) {
    pthread_mutex_lock(&g_host.lock);

    if (system_dir) {
        snprintf(g_host.system_dir, sizeof(g_host.system_dir), "%s", system_dir);
    }
    if (save_dir) {
        snprintf(g_host.save_dir, sizeof(g_host.save_dir), "%s", save_dir);
    } else if (system_dir) {
        snprintf(g_host.save_dir, sizeof(g_host.save_dir), "%s", system_dir);
    }

    if (!g_host.audio_rb) {
        g_host.audio_rb = ringbuffer_create(AUDIO_DEFAULT_CAPACITY);
    }

    if (!g_host.video_buffer) {
        size_t initial_cap = VIDEO_INITIAL_WIDTH * VIDEO_INITIAL_HEIGHT;
        g_host.video_buffer = (uint32_t*) calloc(initial_cap, sizeof(uint32_t));
        g_host.video_buffer_capacity = initial_cap;
    }

    g_host.initialized = true;
    LOGI("host_init: initialized with system_dir='%s', save_dir='%s'", g_host.system_dir, g_host.save_dir);

    pthread_mutex_unlock(&g_host.lock);
    return true;
}

void host_destroy(void) {
    pthread_mutex_lock(&g_host.lock);

    if (g_host.game_loaded) {
        if (g_host.core.retro_unload_game) {
            g_host.core.retro_unload_game();
        }
        g_host.game_loaded = false;
    }

    if (g_host.core_loaded) {
        if (g_host.core.retro_deinit) {
            g_host.core.retro_deinit();
        }
        if (g_host.core.handle) {
            dlclose(g_host.core.handle);
        }
        memset(&g_host.core, 0, sizeof(g_host.core));
        g_host.core_loaded = false;
        g_host.loaded_core_path[0] = '\0';
    }

    if (g_host.rom_data) {
        free(g_host.rom_data);
        g_host.rom_data = NULL;
        g_host.rom_size = 0;
    }

    if (g_host.video_buffer) {
        free(g_host.video_buffer);
        g_host.video_buffer = NULL;
        g_host.video_buffer_capacity = 0;
    }

    if (g_host.audio_rb) {
        ringbuffer_destroy(g_host.audio_rb);
        g_host.audio_rb = NULL;
    }

    g_host.video_width = 0;
    g_host.video_height = 0;
    g_host.initialized = false;

    pthread_mutex_unlock(&g_host.lock);
    LOGI("host_destroy: subsystem destroyed");
}

bool host_load_core(const char* core_path) {
    if (!core_path || strlen(core_path) == 0) {
        set_last_error("host_load_core: empty or null core_path");
        LOGE("host_load_core: empty or null core_path");
        return false;
    }

    pthread_mutex_lock(&g_host.lock);

    if (g_host.core_loaded) {
        LOGI("host_load_core: unloading previously loaded core: %s", g_host.loaded_core_path);
        pthread_mutex_unlock(&g_host.lock);
        host_unload_core();
        pthread_mutex_lock(&g_host.lock);
    }

    LOGI("host_load_core: loading core from %s", core_path);

    void* handle = dlopen(core_path, RTLD_LAZY | RTLD_LOCAL);
    if (!handle) {
        LOGW("host_load_core: dlopen failed for %s (%s), retrying with RTLD_GLOBAL", core_path, dlerror());
        handle = dlopen(core_path, RTLD_LAZY | RTLD_GLOBAL);
    }
    if (!handle) {
        const char* slash = strrchr(core_path, '/');
        const char* bslash = strrchr(core_path, '\\');
        const char* basename = slash ? (slash + 1) : (bslash ? (bslash + 1) : NULL);
        if (basename && strlen(basename) > 0) {
            handle = dlopen(basename, RTLD_LAZY | RTLD_LOCAL);
            if (!handle) {
                handle = dlopen(basename, RTLD_LAZY | RTLD_GLOBAL);
            }
        }
    }
    if (!handle) {
        char alt_name[256];
        if (strncmp(core_path, "libretro_", 9) != 0 && strstr(core_path, "lib") == NULL) {
            snprintf(alt_name, sizeof(alt_name), "libretro_%s.so", core_path);
            handle = dlopen(alt_name, RTLD_LAZY | RTLD_LOCAL);
            if (!handle) handle = dlopen(alt_name, RTLD_LAZY | RTLD_GLOBAL);
        }
    }
    if (!handle) {
        LOGW("host_load_core: dlopen failed for %s, retrying process lookup with dlopen(NULL)", core_path);
        handle = dlopen(NULL, RTLD_NOW);
    }
    if (!handle) {
        set_last_error("host_load_core: all dlopen attempts failed for %s: %s", core_path, dlerror());
        LOGE("host_load_core: all dlopen attempts failed for %s: %s", core_path, dlerror());
        pthread_mutex_unlock(&g_host.lock);
        return false;
    }

    struct LibretroCore new_core;
    memset(&new_core, 0, sizeof(new_core));
    new_core.handle = handle;

    RESOLVE_REQUIRED_SYM(new_core, retro_init);
    RESOLVE_REQUIRED_SYM(new_core, retro_deinit);
    RESOLVE_REQUIRED_SYM(new_core, retro_api_version);
    RESOLVE_REQUIRED_SYM(new_core, retro_get_system_info);
    RESOLVE_REQUIRED_SYM(new_core, retro_get_system_av_info);
    RESOLVE_REQUIRED_SYM(new_core, retro_set_environment);
    RESOLVE_REQUIRED_SYM(new_core, retro_set_video_refresh);
    RESOLVE_REQUIRED_SYM(new_core, retro_set_audio_sample);
    RESOLVE_REQUIRED_SYM(new_core, retro_set_audio_sample_batch);
    RESOLVE_REQUIRED_SYM(new_core, retro_set_input_poll);
    RESOLVE_REQUIRED_SYM(new_core, retro_set_input_state);
    RESOLVE_REQUIRED_SYM(new_core, retro_reset);
    RESOLVE_REQUIRED_SYM(new_core, retro_run);
    RESOLVE_REQUIRED_SYM(new_core, retro_load_game);
    RESOLVE_REQUIRED_SYM(new_core, retro_unload_game);
    RESOLVE_REQUIRED_SYM(new_core, retro_get_memory_data);
    RESOLVE_REQUIRED_SYM(new_core, retro_get_memory_size);

    RESOLVE_OPTIONAL_SYM(new_core, retro_set_controller_port_device);
    RESOLVE_OPTIONAL_SYM(new_core, retro_serialize_size);
    RESOLVE_OPTIONAL_SYM(new_core, retro_serialize);
    RESOLVE_OPTIONAL_SYM(new_core, retro_unserialize);
    RESOLVE_OPTIONAL_SYM(new_core, retro_get_region);

    unsigned api_ver = new_core.retro_api_version();
    if (api_ver != RETRO_API_VERSION) {
        set_last_error("host_load_core: API version mismatch (core=%u, host=%u)", api_ver, RETRO_API_VERSION);
        LOGE("host_load_core: API version mismatch (core=%u, host=%u)", api_ver, RETRO_API_VERSION);
        dlclose(handle);
        pthread_mutex_unlock(&g_host.lock);
        return false;
    }

    g_last_error[0] = '\0';

    memset(&g_host.system_info, 0, sizeof(g_host.system_info));
    new_core.retro_get_system_info(&g_host.system_info);
    LOGI("host_load_core: Core loaded: %s (v%s), need_fullpath=%d",
         g_host.system_info.library_name ? g_host.system_info.library_name : "Unknown",
         g_host.system_info.library_version ? g_host.system_info.library_version : "Unknown",
         g_host.system_info.need_fullpath);

    g_host.core = new_core;
    g_host.core_loaded = true;
    snprintf(g_host.loaded_core_path, sizeof(g_host.loaded_core_path), "%s", core_path);

    // Reset default pixel format before core environment callback negotiation
    g_host.pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;

    g_host.core.retro_set_environment(retro_environment_cb);
    g_host.core.retro_set_video_refresh(retro_video_refresh_cb);
    g_host.core.retro_set_audio_sample(retro_audio_sample_cb);
    g_host.core.retro_set_audio_sample_batch(retro_audio_sample_batch_cb);
    g_host.core.retro_set_input_poll(retro_input_poll_cb);
    g_host.core.retro_set_input_state(retro_input_state_cb);

    g_host.core.retro_init();

    if (!g_host.audio_rb) {
        g_host.audio_rb = ringbuffer_create(AUDIO_DEFAULT_CAPACITY);
    } else {
        ringbuffer_reset(g_host.audio_rb);
    }

    pthread_mutex_unlock(&g_host.lock);
    return true;
}

void host_unload_core(void) {
    pthread_mutex_lock(&g_host.lock);

    if (g_host.game_loaded) {
        if (g_host.core.retro_unload_game) {
            g_host.core.retro_unload_game();
        }
        g_host.game_loaded = false;
    }

    if (g_host.core_loaded) {
        if (g_host.core.retro_deinit) {
            g_host.core.retro_deinit();
        }
        if (g_host.core.handle) {
            dlclose(g_host.core.handle);
        }
        memset(&g_host.core, 0, sizeof(g_host.core));
        g_host.core_loaded = false;
        g_host.loaded_core_path[0] = '\0';
    }

    if (g_host.rom_data) {
        free(g_host.rom_data);
        g_host.rom_data = NULL;
        g_host.rom_size = 0;
    }

    g_host.video_width = 0;
    g_host.video_height = 0;
    g_host.base_width = 0;
    g_host.base_height = 0;
    g_host.max_width = 0;
    g_host.max_height = 0;

    if (g_host.audio_rb) {
        ringbuffer_reset(g_host.audio_rb);
    }

    pthread_mutex_unlock(&g_host.lock);
    LOGI("host_unload_core: Core unloaded");
}

static bool is_disc_or_streamable_format(const char* path) {
    if (!path) return false;
    const char* dot = strrchr(path, '.');
    if (!dot) return false;
    if (strcasecmp(dot, ".iso") == 0 ||
        strcasecmp(dot, ".cue") == 0 ||
        strcasecmp(dot, ".bin") == 0 ||
        strcasecmp(dot, ".chd") == 0 ||
        strcasecmp(dot, ".pbp") == 0 ||
        strcasecmp(dot, ".cso") == 0 ||
        strcasecmp(dot, ".mdf") == 0 ||
        strcasecmp(dot, ".img") == 0 ||
        strcasecmp(dot, ".m3u") == 0 ||
        strcasecmp(dot, ".ccd") == 0 ||
        strcasecmp(dot, ".toc") == 0 ||
        strcasecmp(dot, ".prx") == 0 ||
        strcasecmp(dot, ".elf") == 0) {
        return true;
    }
    return false;
}

bool host_load_game(const char* rom_path) {
    if (!rom_path || strlen(rom_path) == 0) {
        set_last_error("host_load_game: invalid or empty rom path");
        LOGE("host_load_game: invalid rom path");
        return false;
    }

    pthread_mutex_lock(&g_host.lock);

    if (!g_host.core_loaded) {
        LOGI("host_load_game: no core loaded yet, attempting auto-discovery of candidate libretro cores");
        pthread_mutex_unlock(&g_host.lock);

        const char* auto_candidates[] = {
            "libretro_mgba.so",
            "libretro_snes9x.so",
            "libretro_genesis_plus_gx.so",
            "libretro_fceumm.so",
            "libretro_mednafen_pce_fast.so",
            "libmgba.so",
            "mgba",
            NULL
        };
        bool auto_loaded = false;
        for (int i = 0; auto_candidates[i] != NULL; ++i) {
            if (host_load_core(auto_candidates[i])) {
                auto_loaded = true;
                LOGI("host_load_game: successfully auto-loaded fallback core %s", auto_candidates[i]);
                break;
            }
        }
        if (!auto_loaded) {
            set_last_error("host_load_game: no libretro core loaded and auto-discovery failed");
            LOGE("host_load_game: no core loaded and auto-discovery failed");
            return false;
        }
        pthread_mutex_lock(&g_host.lock);
    }

    if (g_host.game_loaded) {
        if (g_host.core.retro_unload_game) {
            g_host.core.retro_unload_game();
        }
        g_host.game_loaded = false;
    }

    if (g_host.rom_data) {
        free(g_host.rom_data);
        g_host.rom_data = NULL;
        g_host.rom_size = 0;
    }

    struct retro_game_info game_info;
    memset(&game_info, 0, sizeof(game_info));
    game_info.path = rom_path;

    long file_size = 0;
    FILE* fp = fopen(rom_path, "rb");
    if (fp) {
        fseek(fp, 0, SEEK_END);
        file_size = ftell(fp);
        fseek(fp, 0, SEEK_SET);
    }

    // Disc images (.iso, .bin, .cue, .chd) and files > 32 MB must stream directly from disk
    bool use_fullpath = g_host.system_info.need_fullpath ||
                        is_disc_or_streamable_format(rom_path) ||
                        file_size > (32 * 1024 * 1024) ||
                        (fp == NULL);

    if (!use_fullpath && fp != NULL && file_size > 0) {
        g_host.rom_data = malloc((size_t) file_size);
        if (g_host.rom_data) {
            size_t read_bytes = fread(g_host.rom_data, 1, (size_t) file_size, fp);
            if (read_bytes == (size_t) file_size) {
                g_host.rom_size = (size_t) file_size;
                game_info.data = g_host.rom_data;
                game_info.size = g_host.rom_size;
                LOGI("host_load_game: loaded ROM into memory (%zu bytes)", g_host.rom_size);
            } else {
                LOGW("host_load_game: read mismatch (%zu of %ld bytes), falling back to full path", read_bytes, file_size);
                free(g_host.rom_data);
                g_host.rom_data = NULL;
                g_host.rom_size = 0;
                game_info.data = NULL;
                game_info.size = 0;
            }
        } else {
            LOGW("host_load_game: failed to allocate %ld bytes in RAM, falling back to full path", file_size);
            game_info.data = NULL;
            game_info.size = 0;
        }
    } else {
        LOGI("host_load_game: path-based streaming for '%s' (size=%ld, need_fullpath=%d)",
             rom_path, file_size, g_host.system_info.need_fullpath);
        game_info.data = NULL;
        game_info.size = 0;
    }

    if (fp) {
        fclose(fp);
    }

    bool success = g_host.core.retro_load_game(&game_info);

    // Fallback: If in-memory loading was rejected by core, retry with path-based streaming
    if (!success && game_info.data != NULL) {
        LOGW("host_load_game: in-memory retro_load_game returned false for %s, retrying with path streaming", rom_path);
        if (g_host.rom_data) {
            free(g_host.rom_data);
            g_host.rom_data = NULL;
            g_host.rom_size = 0;
        }
        game_info.data = NULL;
        game_info.size = 0;
        game_info.path = rom_path;
        success = g_host.core.retro_load_game(&game_info);
    }

    if (!success) {
        set_last_error("host_load_game: retro_load_game returned false for '%s' (core='%s', size=%ld, need_fullpath=%d)",
                       rom_path, g_host.system_info.library_name ? g_host.system_info.library_name : "unknown",
                       file_size, g_host.system_info.need_fullpath);
        LOGE("host_load_game: retro_load_game returned false for %s", rom_path);
        if (g_host.rom_data) {
            free(g_host.rom_data);
            g_host.rom_data = NULL;
            g_host.rom_size = 0;
        }
        pthread_mutex_unlock(&g_host.lock);
        return false;
    }

    g_last_error[0] = '\0';
    g_host.game_loaded = true;

    memset(&g_host.av_info, 0, sizeof(g_host.av_info));
    g_host.core.retro_get_system_av_info(&g_host.av_info);

    g_host.base_width = g_host.av_info.geometry.base_width > 0 ? g_host.av_info.geometry.base_width : VIDEO_INITIAL_WIDTH;
    g_host.base_height = g_host.av_info.geometry.base_height > 0 ? g_host.av_info.geometry.base_height : VIDEO_INITIAL_HEIGHT;
    g_host.max_width = g_host.av_info.geometry.max_width > 0 ? g_host.av_info.geometry.max_width : VIDEO_MAX_FALLBACK_WIDTH;
    g_host.max_height = g_host.av_info.geometry.max_height > 0 ? g_host.av_info.geometry.max_height : VIDEO_MAX_FALLBACK_HEIGHT;

    g_host.video_width = g_host.base_width;
    g_host.video_height = g_host.base_height;

    if (g_host.av_info.geometry.aspect_ratio > 0.0f) {
        g_host.aspect_ratio = g_host.av_info.geometry.aspect_ratio;
    } else {
        g_host.aspect_ratio = (float) g_host.base_width / (float) g_host.base_height;
    }

    g_host.target_fps = g_host.av_info.timing.fps > 0.0 ? g_host.av_info.timing.fps : 60.0;
    g_host.sample_rate = g_host.av_info.timing.sample_rate > 0.0 ? g_host.av_info.timing.sample_rate : 44100.0;

    size_t required_capacity = (size_t) g_host.max_width * (size_t) g_host.max_height;
    if (required_capacity > g_host.video_buffer_capacity) {
        free(g_host.video_buffer);
        g_host.video_buffer = (uint32_t*) calloc(required_capacity, sizeof(uint32_t));
        g_host.video_buffer_capacity = required_capacity;
    }

    if (g_host.audio_rb) {
        ringbuffer_reset(g_host.audio_rb);
    }

    LOGI("host_load_game: game loaded (%ux%u, aspect=%.2f, fps=%.2f, rate=%.1f)",
         g_host.base_width, g_host.base_height, g_host.aspect_ratio, g_host.target_fps, g_host.sample_rate);

    pthread_mutex_unlock(&g_host.lock);
    return true;
}

void host_unload_game(void) {
    pthread_mutex_lock(&g_host.lock);
    if (g_host.game_loaded && g_host.core.retro_unload_game) {
        g_host.core.retro_unload_game();
        g_host.game_loaded = false;
    }
    if (g_host.rom_data) {
        free(g_host.rom_data);
        g_host.rom_data = NULL;
        g_host.rom_size = 0;
    }
    g_host.video_width = 0;
    g_host.video_height = 0;
    if (g_host.audio_rb) {
        ringbuffer_reset(g_host.audio_rb);
    }
    pthread_mutex_unlock(&g_host.lock);
    LOGI("host_unload_game: game unloaded");
}

void host_reset(void) {
    pthread_mutex_lock(&g_host.lock);
    if (g_host.core_loaded && g_host.game_loaded && g_host.core.retro_reset) {
        g_host.core.retro_reset();
    }
    if (g_host.audio_rb) {
        ringbuffer_reset(g_host.audio_rb);
    }
    pthread_mutex_unlock(&g_host.lock);
    LOGI("host_reset: core reset");
}

bool host_run_frame(void) {
    pthread_mutex_lock(&g_host.lock);
    if (!g_host.core_loaded || !g_host.game_loaded || !g_host.core.retro_run) {
        pthread_mutex_unlock(&g_host.lock);
        return false;
    }

    g_host.core.retro_run();

    pthread_mutex_unlock(&g_host.lock);
    return true;
}

void host_set_keys(uint32_t key_mask) {
    pthread_mutex_lock(&g_host.lock);
    g_host.input_mask = key_mask;
    pthread_mutex_unlock(&g_host.lock);
}

void host_set_analog(float left_x, float left_y, float right_x, float right_y) {
    pthread_mutex_lock(&g_host.lock);
    if (left_x < -1.0f) left_x = -1.0f; else if (left_x > 1.0f) left_x = 1.0f;
    if (left_y < -1.0f) left_y = -1.0f; else if (left_y > 1.0f) left_y = 1.0f;
    if (right_x < -1.0f) right_x = -1.0f; else if (right_x > 1.0f) right_x = 1.0f;
    if (right_y < -1.0f) right_y = -1.0f; else if (right_y > 1.0f) right_y = 1.0f;

    g_host.analog_left_x = (int16_t) (left_x * 32767.0f);
    g_host.analog_left_y = (int16_t) (left_y * 32767.0f);
    g_host.analog_right_x = (int16_t) (right_x * 32767.0f);
    g_host.analog_right_y = (int16_t) (right_y * 32767.0f);
    pthread_mutex_unlock(&g_host.lock);
}

void host_set_pointer(int16_t x, int16_t y, bool pressed) {
    pthread_mutex_lock(&g_host.lock);
    g_host.pointer_x = x;
    g_host.pointer_y = y;
    g_host.pointer_pressed = pressed;
    pthread_mutex_unlock(&g_host.lock);
}

uint32_t* host_get_video_buffer(unsigned* out_width, unsigned* out_height) {
    pthread_mutex_lock(&g_host.lock);
    if (!g_host.core_loaded || !g_host.game_loaded || g_host.video_width == 0 || g_host.video_height == 0) {
        pthread_mutex_unlock(&g_host.lock);
        if (out_width) *out_width = 0;
        if (out_height) *out_height = 0;
        return NULL;
    }
    if (out_width) *out_width = g_host.video_width;
    if (out_height) *out_height = g_host.video_height;
    uint32_t* buf = g_host.video_buffer;
    pthread_mutex_unlock(&g_host.lock);
    return buf;
}

size_t host_get_audio_samples(int16_t* out_samples, size_t max_samples) {
    pthread_mutex_lock(&g_host.lock);
    if (!out_samples || max_samples == 0 || !g_host.audio_rb) {
        pthread_mutex_unlock(&g_host.lock);
        return 0;
    }
    size_t read_count = ringbuffer_read(g_host.audio_rb, out_samples, max_samples);
    pthread_mutex_unlock(&g_host.lock);
    return read_count;
}

size_t host_get_audio_available(void) {
    pthread_mutex_lock(&g_host.lock);
    if (!g_host.audio_rb) {
        pthread_mutex_unlock(&g_host.lock);
        return 0;
    }
    size_t avail = ringbuffer_available(g_host.audio_rb);
    pthread_mutex_unlock(&g_host.lock);
    return avail;
}

size_t host_get_sram_size(void) {
    pthread_mutex_lock(&g_host.lock);
    if (!g_host.core_loaded || !g_host.core.retro_get_memory_size) {
        pthread_mutex_unlock(&g_host.lock);
        return 0;
    }
    size_t sz = g_host.core.retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    pthread_mutex_unlock(&g_host.lock);
    return sz;
}

bool host_read_sram(uint8_t* out_buffer, size_t max_size) {
    pthread_mutex_lock(&g_host.lock);
    if (!g_host.core_loaded || !g_host.core.retro_get_memory_data || !g_host.core.retro_get_memory_size || !out_buffer) {
        pthread_mutex_unlock(&g_host.lock);
        return false;
    }
    void* sram = g_host.core.retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t sram_sz = g_host.core.retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!sram || sram_sz == 0) {
        pthread_mutex_unlock(&g_host.lock);
        return false;
    }
    size_t copy_sz = max_size < sram_sz ? max_size : sram_sz;
    memcpy(out_buffer, sram, copy_sz);
    pthread_mutex_unlock(&g_host.lock);
    return true;
}

bool host_write_sram(const uint8_t* in_buffer, size_t size) {
    pthread_mutex_lock(&g_host.lock);
    if (!g_host.core_loaded || !g_host.core.retro_get_memory_data || !g_host.core.retro_get_memory_size || !in_buffer) {
        pthread_mutex_unlock(&g_host.lock);
        return false;
    }
    void* sram = g_host.core.retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t sram_sz = g_host.core.retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!sram || sram_sz == 0) {
        pthread_mutex_unlock(&g_host.lock);
        return false;
    }
    size_t copy_sz = size < sram_sz ? size : sram_sz;
    memcpy(sram, in_buffer, copy_sz);
    pthread_mutex_unlock(&g_host.lock);
    return true;
}

bool host_save_state(const char* file_path) {
    if (!file_path || strlen(file_path) == 0) return false;

    pthread_mutex_lock(&g_host.lock);
    if (!g_host.core_loaded || !g_host.game_loaded || !g_host.core.retro_serialize || !g_host.core.retro_serialize_size) {
        pthread_mutex_unlock(&g_host.lock);
        return false;
    }

    size_t state_size = g_host.core.retro_serialize_size();
    if (state_size == 0) {
        LOGE("host_save_state: core returned 0 serialize size");
        pthread_mutex_unlock(&g_host.lock);
        return false;
    }

    void* state_buffer = malloc(state_size);
    if (!state_buffer) {
        LOGE("host_save_state: failed to allocate %zu bytes for state", state_size);
        pthread_mutex_unlock(&g_host.lock);
        return false;
    }

    bool serialized = g_host.core.retro_serialize(state_buffer, state_size);
    pthread_mutex_unlock(&g_host.lock);

    if (!serialized) {
        LOGE("host_save_state: retro_serialize failed");
        free(state_buffer);
        return false;
    }

    FILE* fp = fopen(file_path, "wb");
    if (!fp) {
        LOGE("host_save_state: failed to open file for writing: %s", file_path);
        free(state_buffer);
        return false;
    }

    size_t written = fwrite(state_buffer, 1, state_size, fp);
    fclose(fp);
    free(state_buffer);

    if (written != state_size) {
        LOGE("host_save_state: incomplete write (%zu of %zu bytes)", written, state_size);
        return false;
    }

    LOGI("host_save_state: state saved successfully to %s (%zu bytes)", file_path, state_size);
    return true;
}

bool host_load_state(const char* file_path) {
    if (!file_path || strlen(file_path) == 0) return false;

    FILE* fp = fopen(file_path, "rb");
    if (!fp) {
        LOGE("host_load_state: failed to open file for reading: %s", file_path);
        return false;
    }

    fseek(fp, 0, SEEK_END);
    long file_size = ftell(fp);
    fseek(fp, 0, SEEK_SET);

    if (file_size <= 0) {
        LOGE("host_load_state: invalid state file size: %ld", file_size);
        fclose(fp);
        return false;
    }

    void* state_buffer = malloc((size_t) file_size);
    if (!state_buffer) {
        LOGE("host_load_state: failed to allocate %ld bytes", file_size);
        fclose(fp);
        return false;
    }

    size_t read_bytes = fread(state_buffer, 1, (size_t) file_size, fp);
    fclose(fp);

    if (read_bytes != (size_t) file_size) {
        LOGE("host_load_state: failed to read state file");
        free(state_buffer);
        return false;
    }

    pthread_mutex_lock(&g_host.lock);
    if (!g_host.core_loaded || !g_host.game_loaded || !g_host.core.retro_unserialize) {
        pthread_mutex_unlock(&g_host.lock);
        free(state_buffer);
        return false;
    }

    bool success = g_host.core.retro_unserialize(state_buffer, (size_t) file_size);
    pthread_mutex_unlock(&g_host.lock);
    free(state_buffer);

    if (!success) {
        LOGE("host_load_state: retro_unserialize failed for %s", file_path);
        return false;
    }

    LOGI("host_load_state: state loaded successfully from %s", file_path);
    return true;
}

/* ========================================================================= */
/* Frontend Callback Implementations                                         */
/* ========================================================================= */

static void core_log_cb(enum retro_log_level level, const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    char buf[1024];
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    switch (level) {
        case RETRO_LOG_DEBUG:
        case RETRO_LOG_INFO:
            LOGI("[Core] %s", buf);
            break;
        case RETRO_LOG_WARN:
            LOGW("[Core] %s", buf);
            break;
        case RETRO_LOG_ERROR:
        default:
            LOGE("[Core] %s", buf);
            break;
    }
}

static uintptr_t retro_hw_get_current_framebuffer(void) {
    return 0;
}

static retro_proc_address_t retro_hw_get_proc_address(const char *sym) {
    if (!sym) return NULL;
#if defined(__ANDROID__)
    void *proc = (void *) eglGetProcAddress(sym);
    if (proc) return (retro_proc_address_t) proc;
#endif
    return (retro_proc_address_t) dlsym(RTLD_DEFAULT, sym);
}

#ifndef RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2
#define RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2 67
#endif

#ifndef RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL
#define RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL 68
#endif

static bool retro_environment_cb(unsigned cmd, void *data) {

    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            const enum retro_pixel_format *fmt = (const enum retro_pixel_format *) data;
            if (!fmt) return false;
            g_host.pixel_format = *fmt;
            LOGI("retro_environment_cb: Pixel format set to %u (0=0RGB1555, 1=XRGB8888, 2=RGB565)", *fmt);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_HW_RENDER: {
            struct retro_hw_render_callback *cb = (struct retro_hw_render_callback *) data;
            if (cb) {
                cb->get_current_framebuffer = retro_hw_get_current_framebuffer;
                cb->get_proc_address = retro_hw_get_proc_address;
                g_host.hw_render = *cb;
                g_host.use_hw_render = true;
                LOGI("retro_environment_cb: HW render context negotiated (type=%d, version=%u.%u)",
                     cb->context_type, cb->version_major, cb->version_minor);
                return true;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
            const char **dir = (const char **) data;
            if (dir) {
                *dir = g_host.system_dir;
                return true;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
            const char **dir = (const char **) data;
            if (dir) {
                *dir = (g_host.save_dir[0] != '\0') ? g_host.save_dir : g_host.system_dir;
                return true;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
            bool *can_dupe = (bool *) data;
            if (can_dupe) {
                *can_dupe = true;
                return true;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL:
        case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
            return true;

        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME: {
            bool *support = (bool *) data;
            if (support) *support = false;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION: {
            unsigned *version = (unsigned *) data;
            if (version) {
                *version = 2;
                return true;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            struct retro_variable *var = (struct retro_variable *) data;
            if (var && var->key) {
                var->value = NULL;
                return true;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            bool *updated = (bool *) data;
            if (updated) {
                *updated = false;
                return true;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            struct retro_log_callback *cb = (struct retro_log_callback *) data;
            if (cb) {
                cb->log = core_log_cb;
                return true;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            const struct retro_game_geometry *geom = (const struct retro_game_geometry *) data;
            if (geom) {
                g_host.base_width = geom->base_width;
                g_host.base_height = geom->base_height;
                g_host.max_width = geom->max_width;
                g_host.max_height = geom->max_height;
                if (geom->aspect_ratio > 0.0f) {
                    g_host.aspect_ratio = geom->aspect_ratio;
                }
                LOGI("retro_environment_cb: Geometry updated: %ux%u (aspect %.2f)",
                     geom->base_width, geom->base_height, geom->aspect_ratio);
                return true;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE: {
            int *mask = (int *) data;
            if (mask) {
                *mask = 1 | 2; // 1 = Video enabled, 2 = Audio enabled
                return true;
            }
            return false;
        }

        default:
            return false;
    }
}

static void retro_video_refresh_cb(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (data == RETRO_HW_FRAME_BUFFER_VALID || (data == NULL && g_host.use_hw_render)) {
        if (width > 0) g_host.video_width = width;
        if (height > 0) g_host.video_height = height;
        return;
    }

    if (!data || width == 0 || height == 0) {
        return;
    }

    g_host.video_width = width;
    g_host.video_height = height;

    size_t pixel_count = (size_t) width * (size_t) height;
    if (pixel_count > g_host.video_buffer_capacity) {
        free(g_host.video_buffer);
        g_host.video_buffer = (uint32_t *) calloc(pixel_count, sizeof(uint32_t));
        g_host.video_buffer_capacity = pixel_count;
    }

    if (!g_host.video_buffer) {
        return;
    }

    switch (g_host.pixel_format) {
        case RETRO_PIXEL_FORMAT_XRGB8888: {
            for (unsigned y = 0; y < height; ++y) {
                const uint32_t *src_row = (const uint32_t *) ((const uint8_t *) data + y * pitch);
                uint32_t *dst_row = g_host.video_buffer + y * width;
                for (unsigned x = 0; x < width; ++x) {
                    uint32_t p = src_row[x];
                    // Little-endian uint32_t 0x00RRGGBB in RAM is [Byte 0: B, Byte 1: G, Byte 2: R, Byte 3: X].
                    // OpenGL ES GL_RGBA with GL_UNSIGNED_BYTE maps Byte 0 to Red and Byte 2 to Blue.
                    // Swap Red and Blue in the stored uint32_t so memory order becomes [R, G, B, 0xFF]:
                    dst_row[x] = 0xFF000000 | ((p & 0x000000FF) << 16) | (p & 0x0000FF00) | ((p & 0x00FF0000) >> 16);
                }
            }
            break;
        }

        case RETRO_PIXEL_FORMAT_RGB565: {
            for (unsigned y = 0; y < height; ++y) {
                const uint16_t *src_row = (const uint16_t *) ((const uint8_t *) data + y * pitch);
                uint32_t *dst_row = g_host.video_buffer + y * width;
                for (unsigned x = 0; x < width; ++x) {
                    uint16_t p = src_row[x];
                    uint32_t r = (p >> 11) & 0x1F; r = (r << 3) | (r >> 2);
                    uint32_t g = (p >> 5) & 0x3F;  g = (g << 2) | (g >> 4);
                    uint32_t b = p & 0x1F;         b = (b << 3) | (b >> 2);
                    // Memory order for GL_RGBA must be [Byte 0: r, Byte 1: g, Byte 2: b, Byte 3: 0xFF].
                    // On little-endian, bits 0-7 = byte 0 (r), bits 16-23 = byte 2 (b).
                    dst_row[x] = 0xFF000000 | (b << 16) | (g << 8) | r;
                }
            }
            break;
        }

        case RETRO_PIXEL_FORMAT_0RGB1555:
        default: {
            for (unsigned y = 0; y < height; ++y) {
                const uint16_t *src_row = (const uint16_t *) ((const uint8_t *) data + y * pitch);
                uint32_t *dst_row = g_host.video_buffer + y * width;
                for (unsigned x = 0; x < width; ++x) {
                    uint16_t p = src_row[x];
                    uint32_t r = (p >> 10) & 0x1F; r = (r << 3) | (r >> 2);
                    uint32_t g = (p >> 5) & 0x1F;  g = (g << 3) | (g >> 2);
                    uint32_t b = p & 0x1F;         b = (b << 3) | (b >> 2);
                    // Memory order for GL_RGBA must be [Byte 0: r, Byte 1: g, Byte 2: b, Byte 3: 0xFF].
                    dst_row[x] = 0xFF000000 | (b << 16) | (g << 8) | r;
                }
            }
            break;
        }
    }
}

static void retro_audio_sample_cb(int16_t left, int16_t right) {
    int16_t samples[2] = {left, right};
    if (g_host.audio_rb) {
        ringbuffer_write(g_host.audio_rb, samples, 2);
    }
}

static size_t retro_audio_sample_batch_cb(const int16_t *data, size_t frames) {
    if (!data || frames == 0 || !g_host.audio_rb) {
        return 0;
    }
    return ringbuffer_write(g_host.audio_rb, data, frames * 2) / 2;
}

static void retro_input_poll_cb(void) {
}

static int16_t retro_input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0) {
        return 0;
    }

    unsigned device_type = device & RETRO_DEVICE_MASK;

    if (device_type == RETRO_DEVICE_JOYPAD) {
        uint32_t mask = g_host.input_mask;
        switch (id) {
            case RETRO_DEVICE_ID_JOYPAD_B:      return (mask & RETROKEY_B) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_Y:      return (mask & RETROKEY_Y) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_SELECT: return (mask & RETROKEY_SELECT) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_START:  return (mask & RETROKEY_START) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_UP:     return (mask & RETROKEY_UP) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_DOWN:   return (mask & RETROKEY_DOWN) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_LEFT:   return (mask & RETROKEY_LEFT) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_RIGHT:  return (mask & RETROKEY_RIGHT) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_A:      return (mask & RETROKEY_A) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_X:      return (mask & RETROKEY_X) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_L:      return (mask & RETROKEY_L) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_R:      return (mask & RETROKEY_R) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_L2:     return (mask & RETROKEY_L2) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_R2:     return (mask & RETROKEY_R2) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_L3:     return (mask & RETROKEY_L3) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_R3:     return (mask & RETROKEY_R3) ? 1 : 0;
            default: return 0;
        }
    } else if (device_type == RETRO_DEVICE_ANALOG) {
        if (index == RETRO_DEVICE_INDEX_ANALOG_LEFT) {
            if (id == RETRO_DEVICE_ID_ANALOG_X) return g_host.analog_left_x;
            if (id == RETRO_DEVICE_ID_ANALOG_Y) return g_host.analog_left_y;
        } else if (index == RETRO_DEVICE_INDEX_ANALOG_RIGHT) {
            if (id == RETRO_DEVICE_ID_ANALOG_X) return g_host.analog_right_x;
            if (id == RETRO_DEVICE_ID_ANALOG_Y) return g_host.analog_right_y;
        }
    } else if (device_type == RETRO_DEVICE_POINTER) {
        if (id == RETRO_DEVICE_ID_POINTER_X) return g_host.pointer_x;
        if (id == RETRO_DEVICE_ID_POINTER_Y) return g_host.pointer_y;
        if (id == RETRO_DEVICE_ID_POINTER_PRESSED) return g_host.pointer_pressed ? 1 : 0;
    }

    return 0;
}

/* ========================================================================= */
/* JNI Export Bindings (matching UniversalLibretroCore.kt)                  */
/* ========================================================================= */

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    const char* path = NULL;
    if (internalStoragePath) {
        path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
    }
    bool result = host_init(path, path);
    if (path) {
        (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
    }
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeLoadCore(
        JNIEnv* env, jobject thiz, jstring corePath) {
    (void) thiz;
    if (!corePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, corePath, NULL);
    bool result = host_load_core(path);
    (*env)->ReleaseStringUTFChars(env, corePath, path);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeUnloadCore(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    host_unload_core();
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, romPath, NULL);
    bool result = host_load_game(path);
    (*env)->ReleaseStringUTFChars(env, romPath, path);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeUnloadRom(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    host_unload_game();
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeReset(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    host_reset();
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeDestroy(
        JNIEnv* env, jobject thiz) {
    (void) thiz;
    host_destroy();

    pthread_mutex_lock(&g_host.lock);
    if (g_byte_buffer_class) {
        (*env)->DeleteGlobalRef(env, g_byte_buffer_class);
        g_byte_buffer_class = NULL;
    }
    if (g_byte_order_native) {
        (*env)->DeleteGlobalRef(env, g_byte_order_native);
        g_byte_order_native = NULL;
    }
    g_byte_buffer_order = NULL;
    g_byte_buffer_as_int_buffer = NULL;
    pthread_mutex_unlock(&g_host.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeRunFrame(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    return host_run_frame() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env;
    (void) thiz;
    host_set_keys((uint32_t) keyMask);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeSetAnalogAxis(
        JNIEnv* env, jobject thiz, jfloat axisX, jfloat axisY) {
    (void) env;
    (void) thiz;
    host_set_analog(axisX, axisY, 0.0f, 0.0f);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeSetTouch(
        JNIEnv* env, jobject thiz, jint x, jint y, jboolean isTouching) {
    (void) env;
    (void) thiz;
    host_set_pointer((int16_t) x, (int16_t) y, (bool) isTouching);
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeGetVideoBuffer(
        JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_host.lock);

    if (!g_host.core_loaded || !g_host.game_loaded || g_host.video_width == 0 || g_host.video_height == 0 || !g_host.video_buffer) {
        pthread_mutex_unlock(&g_host.lock);
        return NULL;
    }

    size_t byte_count = (size_t) g_host.video_width * (size_t) g_host.video_height * sizeof(uint32_t);
    jobject byteBuffer = (*env)->NewDirectByteBuffer(env, (void*) g_host.video_buffer, (jlong) byte_count);
    if (!byteBuffer) {
        pthread_mutex_unlock(&g_host.lock);
        return NULL;
    }

    if (!g_byte_buffer_class || !g_byte_order_native) {
        jclass localBbClass = (*env)->GetObjectClass(env, byteBuffer);
        jclass localOrderClass = (*env)->FindClass(env, "java/nio/ByteOrder");
        if (!localBbClass || !localOrderClass) {
            pthread_mutex_unlock(&g_host.lock);
            return NULL;
        }
        jmethodID nativeOrderMethod = (*env)->GetStaticMethodID(
            env, localOrderClass, "nativeOrder", "()Ljava/nio/ByteOrder;");
        jobject localNativeOrder = nativeOrderMethod
            ? (*env)->CallStaticObjectMethod(env, localOrderClass, nativeOrderMethod)
            : NULL;
        jmethodID orderMethod = (*env)->GetMethodID(
            env, localBbClass, "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
        jmethodID asIntBufferMethod = (*env)->GetMethodID(
            env, localBbClass, "asIntBuffer", "()Ljava/nio/IntBuffer;");
        if (!localNativeOrder || !orderMethod || !asIntBufferMethod) {
            pthread_mutex_unlock(&g_host.lock);
            return NULL;
        }
        g_byte_buffer_class = (*env)->NewGlobalRef(env, localBbClass);
        g_byte_order_native = (*env)->NewGlobalRef(env, localNativeOrder);
        g_byte_buffer_order = orderMethod;
        g_byte_buffer_as_int_buffer = asIntBufferMethod;
    }

    jobject orderedByteBuffer = (*env)->CallObjectMethod(
        env, byteBuffer, g_byte_buffer_order, g_byte_order_native);
    jobject intBuffer = orderedByteBuffer
        ? (*env)->CallObjectMethod(env, orderedByteBuffer, g_byte_buffer_as_int_buffer)
        : NULL;

    pthread_mutex_unlock(&g_host.lock);
    return intBuffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    pthread_mutex_lock(&g_host.lock);
    RingBuffer* rb = g_host.audio_rb;
    if (!outSamples || maxSamples <= 0 || !rb) {
        pthread_mutex_unlock(&g_host.lock);
        return 0;
    }

    jsize arrayLen = (*env)->GetArrayLength(env, outSamples);
    size_t to_read = (size_t) (maxSamples < arrayLen ? maxSamples : arrayLen);
    if (to_read == 0) {
        pthread_mutex_unlock(&g_host.lock);
        return 0;
    }

    int16_t temp_buf[1024];
    size_t total_read = 0;
    while (to_read > 0) {
        size_t chunk = to_read > 1024 ? 1024 : to_read;
        size_t read_count = ringbuffer_read(rb, temp_buf, chunk);
        if (read_count == 0) {
            break;
        }
        (*env)->SetShortArrayRegion(env, outSamples, (jsize) total_read, (jsize) read_count, (const jshort*) temp_buf);
        total_read += read_count;
        to_read -= read_count;
        if (read_count < chunk) {
            break;
        }
    }

    pthread_mutex_unlock(&g_host.lock);
    return (jint) total_read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeGetAudioAvailable(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    return (jint) host_get_audio_available();
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeGetVideoSize(
        JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_host.lock);
    int w = (int) g_host.video_width;
    int h = (int) g_host.video_height;
    bool valid = (g_host.core_loaded && g_host.game_loaded && w > 0 && h > 0);
    pthread_mutex_unlock(&g_host.lock);

    if (!valid) {
        return NULL;
    }

    jintArray out = (*env)->NewIntArray(env, 2);
    if (!out) return NULL;
    jint dims[2] = { w, h };
    (*env)->SetIntArrayRegion(env, out, 0, 2, dims);
    return out;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeGetSramSize(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    return (jint) host_get_sram_size();
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;

    pthread_mutex_lock(&g_host.lock);
    if (!g_host.core_loaded || !g_host.core.retro_get_memory_data || !g_host.core.retro_get_memory_size) {
        pthread_mutex_unlock(&g_host.lock);
        return JNI_FALSE;
    }

    void* sram = g_host.core.retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t size = g_host.core.retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!sram || size == 0) {
        pthread_mutex_unlock(&g_host.lock);
        return JNI_FALSE;
    }

    jsize buffer_capacity = (*env)->GetArrayLength(env, outBuffer);
    if ((size_t) buffer_capacity < size) {
        LOGE("nativeReadSram: buffer capacity (%d) < SRAM size (%zu)", buffer_capacity, size);
        pthread_mutex_unlock(&g_host.lock);
        return JNI_FALSE;
    }

    (*env)->SetByteArrayRegion(env, outBuffer, 0, (jsize) size, (const jbyte*) sram);
    pthread_mutex_unlock(&g_host.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;

    jsize size = (*env)->GetArrayLength(env, inBuffer);
    if (size <= 0) return JNI_FALSE;

    pthread_mutex_lock(&g_host.lock);
    if (!g_host.core_loaded || !g_host.core.retro_get_memory_data || !g_host.core.retro_get_memory_size) {
        pthread_mutex_unlock(&g_host.lock);
        return JNI_FALSE;
    }

    void* sram = g_host.core.retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t sram_sz = g_host.core.retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!sram || sram_sz == 0) {
        pthread_mutex_unlock(&g_host.lock);
        return JNI_FALSE;
    }

    size_t copy_sz = (size_t) size < sram_sz ? (size_t) size : sram_sz;
    (*env)->GetByteArrayRegion(env, inBuffer, 0, (jsize) copy_sz, (jbyte*) sram);
    pthread_mutex_unlock(&g_host.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz;
    (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, filePath, NULL);
    bool result = host_save_state(native_path);
    (*env)->ReleaseStringUTFChars(env, filePath, native_path);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz;
    (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, filePath, NULL);
    bool result = host_load_state(native_path);
    (*env)->ReleaseStringUTFChars(env, filePath, native_path);
    return result ? JNI_TRUE : JNI_FALSE;
}

/* ========================================================================= */
/* Backward Compatibility JNI Aliases (matching NativeCore.kt)              */
/* ========================================================================= */

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeInit(env, thiz, internalStoragePath);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeLoadRom(env, thiz, romPath);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeUnloadRom(
        JNIEnv* env, jobject thiz) {
    Java_com_retropack_runtime_core_UniversalLibretroCore_nativeUnloadRom(env, thiz);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeDestroy(
        JNIEnv* env, jobject thiz) {
    Java_com_retropack_runtime_core_UniversalLibretroCore_nativeDestroy(env, thiz);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeRunFrame(
        JNIEnv* env, jobject thiz) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeRunFrame(env, thiz);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    Java_com_retropack_runtime_core_UniversalLibretroCore_nativeSetKeys(env, thiz, keyMask);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeSetAnalogAxis(
        JNIEnv* env, jobject thiz, jfloat axisX, jfloat axisY) {
    Java_com_retropack_runtime_core_UniversalLibretroCore_nativeSetAnalogAxis(env, thiz, axisX, axisY);
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeGetVideoBuffer(
        JNIEnv* env, jobject thiz) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeGetVideoBuffer(env, thiz);
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeGetAudioSamples(env, thiz, outSamples, maxSamples);
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeGetAudioAvailable(
        JNIEnv* env, jobject thiz) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeGetAudioAvailable(env, thiz);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeGetVideoSize(
        JNIEnv* env, jobject thiz) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeGetVideoSize(env, thiz);
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeGetSramSize(
        JNIEnv* env, jobject thiz) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeGetSramSize(env, thiz);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeReadSram(env, thiz, outBuffer);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeWriteSram(env, thiz, inBuffer);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeSaveState(env, thiz, slot, filePath);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeLoadState(env, thiz, slot, filePath);
}

JNIEXPORT jstring JNICALL
Java_com_retropack_runtime_core_UniversalLibretroCore_nativeGetLastError(
        JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_host.lock);
    if (g_last_error[0] == '\0') {
        pthread_mutex_unlock(&g_host.lock);
        return NULL;
    }
    jstring result = (*env)->NewStringUTF(env, g_last_error);
    pthread_mutex_unlock(&g_host.lock);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeGetLastError(
        JNIEnv* env, jobject thiz) {
    return Java_com_retropack_runtime_core_UniversalLibretroCore_nativeGetLastError(env, thiz);
}

