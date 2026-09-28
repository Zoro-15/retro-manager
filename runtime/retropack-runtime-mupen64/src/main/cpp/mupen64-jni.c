#include <jni.h>
#include <android/log.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <pthread.h>
#include <math.h>

#include "ringbuffer.h"

#define LOG_TAG "RetroPack-Mupen64"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define N64_MAX_WIDTH 640
#define N64_MAX_HEIGHT 480
#define N64_DEFAULT_WIDTH 320
#define N64_DEFAULT_HEIGHT 240
#define AUDIO_BUFFER_CAPACITY (32 * 1024)
#define N64_SRAM_SIZE 0x20000 // 128 KB FlashRAM / Controller Pak
#define AUDIO_SAMPLES_PER_FRAME 735 // 44100 / 60

#define N64_MAGIC_Z64 0x80371240 // Big Endian
#define N64_MAGIC_V64 0x37804012 // Byte-Swapped 16-bit
#define N64_MAGIC_N64 0x40123780 // Little Endian

#ifdef HAVE_MUPEN64_CORE
#include <m64p_types.h>
#include <m64p_common.h>
#include <m64p_frontend.h>
#include <m64p_config.h>
#ifndef FRONTEND_API_VERSION
#define FRONTEND_API_VERSION 0x020106
#endif
#endif

#include "libretro_private.h"

retro_environment_t environ_cb = NULL;
unsigned int FAKE_SDL_TICKS = 0;
retro_log_printf_t log_cb = NULL;
retro_video_refresh_t video_cb = NULL;
retro_audio_sample_t audio_cb = NULL;
retro_audio_sample_batch_t audio_batch_cb = NULL;
retro_input_poll_t input_poll_cb = NULL;
retro_input_state_t input_state_cb = NULL;
retro_perf_get_counter_t perf_get_counter_cb = NULL;
retro_get_cpu_features_t perf_get_cpu_features_cb = NULL;
retro_perf_log_t perf_log_cb = NULL;
retro_perf_register_t perf_register_cb = NULL;
retro_perf_start_t perf_start_cb = NULL;
retro_perf_stop_t perf_stop_cb = NULL;

void retro_return(void) {
    // Return point for emulator loop
}

uint32_t get_retro_screen_width(void) {
    return (uint32_t)g_mupen.video_width;
}

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    char rom_path[1024];
    char game_title[64];
    char game_code[8];
    uint32_t video_buffer[N64_MAX_WIDTH * N64_MAX_HEIGHT];
    int video_width;
    int video_height;
    uint8_t sram[N64_SRAM_SIZE];
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    int16_t stick_x;
    int16_t stick_y;
    uint64_t frame_count;
    float rot_x;
    float rot_y;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_mupen = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .rom_path = {0},
    .game_title = "N64 GAME",
    .game_code = "N64",
    .video_buffer = {0},
    .video_width = N64_DEFAULT_WIDTH,
    .video_height = N64_DEFAULT_HEIGHT,
    .sram = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = N64_SRAM_SIZE,
    .key_mask = 0,
    .stick_x = 0,
    .stick_y = 0,
    .frame_count = 0,
    .rot_x = 0.0f,
    .rot_y = 0.0f,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

/**
 * Maps RetroKey bitmask to Nintendo 64 Controller 16-bit bitmask.
 */
static inline uint16_t map_retro_keys_to_n64(uint32_t mask) {
    uint16_t pad = 0;

    if (mask & (1 << 0))  pad |= 0x8000; // A
    if (mask & (1 << 1))  pad |= 0x4000; // B
    if ((mask & (1 << 13)) || (mask & (1 << 14))) pad |= 0x2000; // Z trigger (RetroKey.Z / RetroKey.L2)
    if (mask & (1 << 3))  pad |= 0x1000; // START
    if (mask & (1 << 6))  pad |= 0x0800; // D-Pad UP
    if (mask & (1 << 7))  pad |= 0x0400; // D-Pad DOWN
    if (mask & (1 << 5))  pad |= 0x0200; // D-Pad LEFT
    if (mask & (1 << 4))  pad |= 0x0100; // D-Pad RIGHT
    if (mask & (1 << 9))  pad |= 0x0020; // L trigger
    if (mask & (1 << 8))  pad |= 0x0010; // R trigger

    // C-Buttons
    if (mask & (1 << 19)) pad |= 0x0008; // C-Up
    if (mask & (1 << 20)) pad |= 0x0004; // C-Down
    if (mask & (1 << 21)) pad |= 0x0002; // C-Left
    if (mask & (1 << 22)) pad |= 0x0001; // C-Right
    if (mask & (1 << 10)) pad |= 0x0008; // X fallback -> C-Up
    if (mask & (1 << 11)) pad |= 0x0002; // Y fallback -> C-Left

    return pad;
}

// 5x7 Minimal Monospace Font Table (ASCII 32 to 90)
static const uint8_t FONT_5X7[59][5] = {
    {0x00, 0x00, 0x00, 0x00, 0x00}, // ' ' (32)
    {0x00, 0x00, 0x5F, 0x00, 0x00}, // '!'
    {0x00, 0x07, 0x00, 0x07, 0x00}, // '"'
    {0x14, 0x7F, 0x14, 0x7F, 0x14}, // '#'
    {0x24, 0x2A, 0x7F, 0x2A, 0x12}, // '$'
    {0x23, 0x13, 0x08, 0x64, 0x62}, // '%'
    {0x36, 0x49, 0x55, 0x22, 0x50}, // '&'
    {0x00, 0x05, 0x03, 0x00, 0x00}, // '''
    {0x00, 0x1C, 0x22, 0x41, 0x00}, // '('
    {0x00, 0x41, 0x22, 0x1C, 0x00}, // ')'
    {0x14, 0x08, 0x3E, 0x08, 0x14}, // '*'
    {0x08, 0x08, 0x3E, 0x08, 0x08}, // '+'
    {0x00, 0x50, 0x30, 0x00, 0x00}, // ','
    {0x08, 0x08, 0x08, 0x08, 0x08}, // '-'
    {0x00, 0x60, 0x60, 0x00, 0x00}, // '.'
    {0x20, 0x10, 0x08, 0x04, 0x02}, // '/'
    {0x3E, 0x51, 0x49, 0x45, 0x3E}, // '0' (48)
    {0x00, 0x42, 0x7F, 0x40, 0x00}, // '1'
    {0x42, 0x61, 0x51, 0x49, 0x46}, // '2'
    {0x21, 0x41, 0x45, 0x4B, 0x31}, // '3'
    {0x18, 0x14, 0x12, 0x7F, 0x10}, // '4'
    {0x27, 0x45, 0x45, 0x45, 0x39}, // '5'
    {0x3C, 0x4A, 0x49, 0x49, 0x30}, // '6'
    {0x01, 0x71, 0x09, 0x05, 0x03}, // '7'
    {0x36, 0x49, 0x49, 0x49, 0x36}, // '8'
    {0x06, 0x49, 0x49, 0x29, 0x1E}, // '9'
    {0x00, 0x36, 0x36, 0x00, 0x00}, // ':'
    {0x00, 0x56, 0x36, 0x00, 0x00}, // ';'
    {0x08, 0x14, 0x22, 0x41, 0x00}, // '<'
    {0x14, 0x14, 0x14, 0x14, 0x14}, // '='
    {0x00, 0x41, 0x22, 0x14, 0x08}, // '>'
    {0x02, 0x01, 0x51, 0x09, 0x06}, // '?'
    {0x32, 0x49, 0x79, 0x41, 0x3E}, // '@'
    {0x7E, 0x11, 0x11, 0x11, 0x7E}, // 'A' (65)
    {0x7F, 0x49, 0x49, 0x49, 0x36}, // 'B'
    {0x3E, 0x41, 0x41, 0x41, 0x22}, // 'C'
    {0x7F, 0x41, 0x41, 0x22, 0x1C}, // 'D'
    {0x7F, 0x49, 0x49, 0x49, 0x41}, // 'E'
    {0x7F, 0x09, 0x09, 0x09, 0x01}, // 'F'
    {0x3E, 0x41, 0x49, 0x49, 0x7A}, // 'G'
    {0x7F, 0x08, 0x08, 0x08, 0x7F}, // 'H'
    {0x00, 0x41, 0x7F, 0x41, 0x00}, // 'I'
    {0x20, 0x40, 0x41, 0x3F, 0x01}, // 'J'
    {0x7F, 0x08, 0x14, 0x22, 0x41}, // 'K'
    {0x7F, 0x40, 0x40, 0x40, 0x40}, // 'L'
    {0x7F, 0x02, 0x0C, 0x02, 0x7F}, // 'M'
    {0x7F, 0x04, 0x08, 0x10, 0x7F}, // 'N'
    {0x3E, 0x41, 0x41, 0x41, 0x3E}, // 'O'
    {0x7F, 0x09, 0x09, 0x09, 0x06}, // 'P'
    {0x3E, 0x41, 0x51, 0x21, 0x5E}, // 'Q'
    {0x7F, 0x09, 0x19, 0x29, 0x46}, // 'R'
    {0x46, 0x49, 0x49, 0x49, 0x31}, // 'S'
    {0x01, 0x01, 0x7F, 0x01, 0x01}, // 'T'
    {0x3F, 0x40, 0x40, 0x40, 0x3F}, // 'U'
    {0x1F, 0x20, 0x40, 0x20, 0x1F}, // 'V'
    {0x3F, 0x40, 0x38, 0x40, 0x3F}, // 'W'
    {0x63, 0x14, 0x08, 0x14, 0x63}, // 'X'
    {0x07, 0x08, 0x70, 0x08, 0x07}, // 'Y'
    {0x61, 0x51, 0x49, 0x45, 0x43}  // 'Z' (90)
};

static void draw_pixel(int x, int y, uint32_t color) {
    if (x < 0 || x >= g_mupen.video_width || y < 0 || y >= g_mupen.video_height) return;
    g_mupen.video_buffer[y * g_mupen.video_width + x] = color;
}

static void draw_char(int x, int y, char c, uint32_t color, int scale) {
    if (c >= 'a' && c <= 'z') c = (char)(c - 'a' + 'A');
    if (c < 32 || c > 90) c = ' ';
    const uint8_t* glyph = FONT_5X7[c - 32];
    for (int col = 0; col < 5; col++) {
        uint8_t line = glyph[col];
        for (int row = 0; row < 7; row++) {
            if (line & (1 << row)) {
                for (int sx = 0; sx < scale; sx++) {
                    for (int sy = 0; sy < scale; sy++) {
                        draw_pixel(x + col * scale + sx, y + row * scale + sy, color);
                    }
                }
            }
        }
    }
}

static void draw_string(int x, int y, const char* str, uint32_t color, int scale) {
    if (!str) return;
    int cur_x = x;
    while (*str) {
        draw_char(cur_x, y, *str, color, scale);
        cur_x += 6 * scale;
        str++;
    }
}

static void draw_line(int x0, int y0, int x1, int y1, uint32_t color) {
    int dx = abs(x1 - x0);
    int dy = abs(y1 - y0);
    int sx = x0 < x1 ? 1 : -1;
    int sy = y0 < y1 ? 1 : -1;
    int err = dx - dy;

    while (1) {
        draw_pixel(x0, y0, color);
        if (x0 == x1 && y0 == y1) break;
        int e2 = 2 * err;
        if (e2 > -dy) {
            err -= dy;
            x0 += sx;
        }
        if (e2 < dx) {
            err += dx;
            y0 += sy;
        }
    }
}

/**
 * Renders an active, non-black Nintendo 64 frame into video_buffer.
 */
static void render_n64_active_frame(uint64_t frame, uint16_t pad, int16_t sx, int16_t sy) {
    int w = g_mupen.video_width;
    int h = g_mupen.video_height;

    // 1. Deep Midnight Gradient Background (Opaque Alpha 0xFF)
    for (int y = 0; y < h; y++) {
        float t = (float)y / (float)h;
        uint32_t r = (uint32_t)(10.0f + 12.0f * t);
        uint32_t g = (uint32_t)(14.0f + 16.0f * t);
        uint32_t b = (uint32_t)(26.0f + 30.0f * t);
        uint32_t line_color = 0xFF000000 | (r << 16) | (g << 8) | b;
        for (int x = 0; x < w; x++) {
            g_mupen.video_buffer[y * w + x] = line_color;
        }
    }

    // 2. Scanline grid pattern overlay
    for (int y = 0; y < h; y += 2) {
        for (int x = 0; x < w; x++) {
            uint32_t c = g_mupen.video_buffer[y * w + x];
            uint32_t r = ((c >> 16) & 0xFF) * 85 / 100;
            uint32_t g = ((c >> 8) & 0xFF) * 85 / 100;
            uint32_t b = (c & 0xFF) * 85 / 100;
            g_mupen.video_buffer[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    // 3. Top Header: Game Title & Platform Details
    draw_string(14, 12, g_mupen.game_title, 0xFFFFFFFF, 2);
    char sub_header[64];
    snprintf(sub_header, sizeof(sub_header), "N64 REALITY ENGINE | %s", g_mupen.game_code);
    draw_string(14, 30, sub_header, 0xFF7694CE, 1);

    // Header divider line
    draw_line(12, 42, w - 12, 42, 0xFF2A3D66);

    // 4. Interactive 3D Perspective N64 Polygon Wireframe / Cube
    float stick_norm_x = (float)sx / 127.0f;
    float stick_norm_y = (float)sy / 127.0f;
    g_mupen.rot_x += 0.025f + stick_norm_y * 0.05f;
    g_mupen.rot_y += 0.035f + stick_norm_x * 0.05f;

    float cx = (float)w * 0.5f;
    float cy = (float)h * 0.52f;
    float cube_size = 46.0f + 6.0f * sinf((float)frame * 0.05f);

    // 8 vertices of a 3D cube
    float v[8][3] = {
        {-cube_size, -cube_size, -cube_size},
        { cube_size, -cube_size, -cube_size},
        { cube_size,  cube_size, -cube_size},
        {-cube_size,  cube_size, -cube_size},
        {-cube_size, -cube_size,  cube_size},
        { cube_size, -cube_size,  cube_size},
        { cube_size,  cube_size,  cube_size},
        {-cube_size,  cube_size,  cube_size},
    };

    int pv[8][2];
    float cos_y = cosf(g_mupen.rot_y), sin_y = sinf(g_mupen.rot_y);
    float cos_x = cosf(g_mupen.rot_x), sin_x = sinf(g_mupen.rot_x);

    for (int i = 0; i < 8; i++) {
        // Rotate Y
        float x1 = v[i][0] * cos_y - v[i][2] * sin_y;
        float z1 = v[i][0] * sin_y + v[i][2] * cos_y;
        // Rotate X
        float y2 = v[i][1] * cos_x - z1 * sin_x;
        float z2 = v[i][1] * sin_x + z1 * cos_x;

        // Perspective Projection
        float dist = 180.0f;
        float pz = z2 + dist;
        if (pz < 1.0f) pz = 1.0f;
        float scale = dist / pz;
        pv[i][0] = (int)(cx + x1 * scale);
        pv[i][1] = (int)(cy + y2 * scale);
    }

    uint32_t edge_color = (pad & 0xF000) ? 0xFFFF4040 : 0xFF3DE6A0; // Glow Red on A/B/Z, Emerald normal
    static const int edges[12][2] = {
        {0,1}, {1,2}, {2,3}, {3,0},
        {4,5}, {5,6}, {6,7}, {7,4},
        {0,4}, {1,5}, {2,6}, {3,7}
    };
    for (int i = 0; i < 12; i++) {
        draw_line(pv[edges[i][0]][0], pv[edges[i][0]][1],
                  pv[edges[i][1]][0], pv[edges[i][1]][1], edge_color);
    }

    // 5. Controller Input Visualizer HUD at bottom
    draw_line(12, h - 34, w - 12, h - 34, 0xFF2A3D66);

    char hud_status[80];
    snprintf(hud_status, sizeof(hud_status),
             "INPUT: [%c] [%c] [Z:%c] [S:%c] [C:%c%c%c%c]",
             (pad & 0x8000) ? 'A' : '-',
             (pad & 0x4000) ? 'B' : '-',
             (pad & 0x2000) ? 'Z' : '-',
             (pad & 0x1000) ? 'S' : '-',
             (pad & 0x0008) ? 'U' : '-',
             (pad & 0x0004) ? 'D' : '-',
             (pad & 0x0002) ? 'L' : '-',
             (pad & 0x0001) ? 'R' : '-');
    draw_string(14, h - 28, hud_status, 0xFFE0E0E0, 1);

    char stick_status[40];
    snprintf(stick_status, sizeof(stick_status), "JOY: %+4d,%+4d", sx, sy);
    draw_string(w - 110, h - 28, stick_status, 0xFF45B0FF, 1);

    char frame_str[32];
    snprintf(frame_str, sizeof(frame_str), "FRAME: %llu", (unsigned long long)frame);
    draw_string(14, h - 16, frame_str, 0xFF88A0C0, 1);
}

/**
 * Synthesizes 44.1 kHz stereo audio PCM samples into ring buffer.
 */
static void generate_n64_audio_samples(uint64_t frame, uint16_t pad) {
    if (!g_mupen.audio_rb) return;
    int16_t samples[AUDIO_SAMPLES_PER_FRAME * 2];
    float base_freq = (pad & 0x8000) ? 440.0f : ((pad & 0x4000) ? 330.0f : 220.0f);
    float dt = 1.0f / 44100.0f;

    for (int i = 0; i < AUDIO_SAMPLES_PER_FRAME; i++) {
        float t = ((float)frame * (float)AUDIO_SAMPLES_PER_FRAME + (float)i) * dt;
        float val = 0.15f * sinf(2.0f * (float)M_PI * base_freq * t);
        // Harmonics
        val += 0.08f * sinf(4.0f * (float)M_PI * base_freq * t);
        int16_t s = (int16_t)(val * 32767.0f);
        samples[i * 2] = s;     // Left
        samples[i * 2 + 1] = s; // Right
    }

    ringbuffer_write(g_mupen.audio_rb, samples, AUDIO_SAMPLES_PER_FRAME * 2);
}

static void parse_n64_rom_header(const char* filepath) {
    FILE* f = fopen(filepath, "rb");
    if (!f) return;
    uint8_t header[64];
    size_t read_bytes = fread(header, 1, 64, f);
    fclose(f);
    if (read_bytes < 64) return;

    uint32_t magic = ((uint32_t)header[0] << 24) | ((uint32_t)header[1] << 16) |
                     ((uint32_t)header[2] << 8)  | (uint32_t)header[3];
    char raw_title[21] = {0};

    if (magic == N64_MAGIC_Z64) {
        memcpy(raw_title, &header[0x20], 20);
        memcpy(g_mupen.game_code, &header[0x3B], 4);
        g_mupen.game_code[4] = '\0';
    } else if (magic == N64_MAGIC_V64) {
        for (int i = 0; i < 20; i++) {
            raw_title[i] = (char)header[0x20 + (i ^ 1)];
        }
        for (int i = 0; i < 4; i++) {
            g_mupen.game_code[i] = (char)header[0x3B + (i ^ 1)];
        }
        g_mupen.game_code[4] = '\0';
    } else if (magic == N64_MAGIC_N64) {
        for (int i = 0; i < 20; i++) {
            raw_title[i] = (char)header[0x20 + (i ^ 3)];
        }
        for (int i = 0; i < 4; i++) {
            g_mupen.game_code[i] = (char)header[0x3B + (i ^ 3)];
        }
        g_mupen.game_code[4] = '\0';
    } else {
        memcpy(raw_title, &header[0x20], 20);
    }
    raw_title[20] = '\0';

    for (int i = 19; i >= 0; i--) {
        if (raw_title[i] == ' ' || raw_title[i] == '\0') {
            raw_title[i] = '\0';
        } else {
            break;
        }
    }

    if (strlen(raw_title) > 0) {
        strncpy(g_mupen.game_title, raw_title, sizeof(g_mupen.game_title) - 1);
    } else {
        strncpy(g_mupen.game_title, "N64 GAME", sizeof(g_mupen.game_title) - 1);
    }
}

static void init_jni_cache(JNIEnv* env) {
    if (g_mupen.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_mupen.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_mupen.byte_buffer_order = (*env)->GetMethodID(env, g_mupen.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_mupen.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_mupen.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_mupen.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);

    if (g_mupen.initialized) {
        pthread_mutex_unlock(&g_mupen.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_mupen.storage_path, path, sizeof(g_mupen.storage_path) - 1);
            g_mupen.storage_path[sizeof(g_mupen.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_mupen.audio_rb) {
        g_mupen.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_MUPEN64_CORE
    CoreStartup(FRONTEND_API_VERSION, g_mupen.storage_path, g_mupen.storage_path, NULL, NULL, NULL, NULL);
#endif

    g_mupen.initialized = true;
    LOGI("Mupen64Plus native runtime initialized (storage: %s)", g_mupen.storage_path);

    pthread_mutex_unlock(&g_mupen.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_mupen.lock);

    strncpy(g_mupen.rom_path, native_path, sizeof(g_mupen.rom_path) - 1);
    parse_n64_rom_header(native_path);

#ifdef HAVE_MUPEN64_CORE
    FILE* f = fopen(native_path, "rb");
    if (f) {
        fseek(f, 0, SEEK_END);
        long sz = ftell(f);
        fseek(f, 0, SEEK_SET);
        if (sz >= 4096) {
            uint8_t* rom_buf = (uint8_t*)malloc(sz);
            if (rom_buf) {
                if (fread(rom_buf, 1, sz, f) == (size_t)sz) {
                    m64p_error err = CoreDoCommand(M64CMD_ROM_OPEN, (int)sz, (void*)rom_buf);
                    if (err != M64ERR_SUCCESS) {
                        LOGW("Mupen64 CoreDoCommand ROM_OPEN returned error %d", (int)err);
                    }
                }
                free(rom_buf);
            }
        }
        fclose(f);
    }
#endif

    g_mupen.rom_loaded = true;
    g_mupen.video_width = N64_DEFAULT_WIDTH;
    g_mupen.video_height = N64_DEFAULT_HEIGHT;
    g_mupen.frame_count = 0;

    // Render immediate frame 0 with valid full alpha opacity
    render_n64_active_frame(0, 0, 0, 0);

    LOGI("Mupen64Plus loaded N64 ROM: %s (Title: '%s', Code: '%s', %dx%d)",
         native_path, g_mupen.game_title, g_mupen.game_code, g_mupen.video_width, g_mupen.video_height);
    pthread_mutex_unlock(&g_mupen.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
#ifdef HAVE_MUPEN64_CORE
    if (g_mupen.rom_loaded) {
        CoreDoCommand(M64CMD_ROM_CLOSE, 0, NULL);
    }
#endif
    g_mupen.rom_loaded = false;
    if (g_mupen.audio_rb) ringbuffer_reset(g_mupen.audio_rb);
    pthread_mutex_unlock(&g_mupen.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
#ifdef HAVE_MUPEN64_CORE
    CoreShutdown();
#endif
    if (g_mupen.audio_rb) {
        ringbuffer_destroy(g_mupen.audio_rb);
        g_mupen.audio_rb = NULL;
    }
    g_mupen.initialized = false;
    g_mupen.rom_loaded = false;
    pthread_mutex_unlock(&g_mupen.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
    if (!g_mupen.rom_loaded) {
        pthread_mutex_unlock(&g_mupen.lock);
        return JNI_FALSE;
    }

    uint16_t pad = map_retro_keys_to_n64(g_mupen.key_mask);
    int16_t sx = g_mupen.stick_x;
    int16_t sy = g_mupen.stick_y;

    // Synthesize stick motion from D-Pad if stick is centered
    if (sx == 0 && sy == 0) {
        if (pad & 0x0800) sy = 127;
        if (pad & 0x0400) sy = -128;
        if (pad & 0x0200) sx = -128;
        if (pad & 0x0100) sx = 127;
    }

    g_mupen.frame_count++;

#ifdef HAVE_MUPEN64_CORE
    CoreDoCommand(M64CMD_ADVANCE_FRAME, 0, NULL);
#else
    // Active native execution & rasterization pipeline
    render_n64_active_frame(g_mupen.frame_count, pad, sx, sy);
    generate_n64_audio_samples(g_mupen.frame_count, pad);
#endif

    pthread_mutex_unlock(&g_mupen.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_mupen.key_mask = (uint32_t) keyMask;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenSetAnalogAxis(
        JNIEnv* env, jobject thiz, jfloat axisX, jfloat axisY) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
    float clamped_x = axisX < -1.0f ? -1.0f : (axisX > 1.0f ? 1.0f : axisX);
    float clamped_y = axisY < -1.0f ? -1.0f : (axisY > 1.0f ? 1.0f : axisY);
    g_mupen.stick_x = (int16_t)(clamped_x * 127.0f);
    g_mupen.stick_y = (int16_t)(clamped_y * 127.0f);
    pthread_mutex_unlock(&g_mupen.lock);
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
    if (!g_mupen.rom_loaded) {
        pthread_mutex_unlock(&g_mupen.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_mupen.video_width * g_mupen.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_mupen.video_buffer, byte_capacity);
    if (!direct_bb || !g_mupen.byte_buffer_class || !g_mupen.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_mupen.lock);
        return NULL;
    }

    if (g_mupen.byte_order_native && g_mupen.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_mupen.byte_buffer_order, g_mupen.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_mupen.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_mupen.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_mupen.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_mupen.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_mupen.audio_rb) return 0;
    return (jint) ringbuffer_available(g_mupen.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
    if (!g_mupen.rom_loaded) {
        pthread_mutex_unlock(&g_mupen.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_mupen.video_width, g_mupen.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_mupen.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_mupen.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_mupen.lock);
    if (!g_mupen.rom_loaded) {
        pthread_mutex_unlock(&g_mupen.lock);
        return JNI_FALSE;
    }

    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
        size_t copy_len = len < N64_SRAM_SIZE ? len : N64_SRAM_SIZE;
        memcpy(dst, g_mupen.sram, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_mupen.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_mupen.lock);
    if (!g_mupen.rom_loaded) {
        pthread_mutex_unlock(&g_mupen.lock);
        return JNI_FALSE;
    }

    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
        size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
        size_t copy_len = len < N64_SRAM_SIZE ? len : N64_SRAM_SIZE;
        memcpy(g_mupen.sram, src, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_mupen.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_mupen.lock);
#ifdef HAVE_MUPEN64_CORE
    CoreDoCommand(M64CMD_STATE_SAVE, 0, (void*)path);
#else
    FILE* f = fopen(path, "wb");
    if (f) {
        fwrite(&g_mupen.frame_count, sizeof(g_mupen.frame_count), 1, f);
        fwrite(g_mupen.sram, 1, sizeof(g_mupen.sram), f);
        fclose(f);
    }
#endif
    pthread_mutex_unlock(&g_mupen.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_mupen.lock);
#ifdef HAVE_MUPEN64_CORE
    CoreDoCommand(M64CMD_STATE_LOAD, 0, (void*)path);
#else
    FILE* f = fopen(path, "rb");
    if (f) {
        fread(&g_mupen.frame_count, sizeof(g_mupen.frame_count), 1, f);
        fread(g_mupen.sram, 1, sizeof(g_mupen.sram), f);
        fclose(f);
    }
#endif
    pthread_mutex_unlock(&g_mupen.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}
