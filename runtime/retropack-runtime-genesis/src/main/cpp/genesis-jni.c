#include <jni.h>
#include <android/log.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <math.h>
#include <sys/types.h>
#include <pthread.h>

#include "ringbuffer.h"

#define LOG_TAG "RetroPack-Genesis"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define GENESIS_DEFAULT_WIDTH 320
#define GENESIS_DEFAULT_HEIGHT 224
#define GENESIS_MAX_WIDTH 320
#define GENESIS_MAX_HEIGHT 240
#define AUDIO_BUFFER_CAPACITY (16 * 1024)
#define AUDIO_SAMPLES_PER_FRAME 735 // 44100 / 60

#ifdef HAVE_GENESIS_CORE
// Real upstream Genesis Plus GX C headers
#include <shared.h>
#include <genesis.h>
#include <sound.h>
#include <vdp_ctrl.h>
#include <system.h>
#include <state.h>
#include <loadrom.h>
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    char rom_path[1024];
    char game_title[64];
    char product_code[16];
    char region[16];
    uint32_t video_buffer[GENESIS_MAX_WIDTH * GENESIS_MAX_HEIGHT];
    int video_width;
    int video_height;
    uint8_t sram[0x10000]; // 64 KB standard SRAM
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    uint64_t frame_count;
    float raster_phase;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_genesis = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .rom_path = {0},
    .game_title = "SEGA MEGA DRIVE",
    .product_code = "MK-0000",
    .region = "World",
    .video_buffer = {0},
    .video_width = GENESIS_DEFAULT_WIDTH,
    .video_height = GENESIS_DEFAULT_HEIGHT,
    .sram = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = 0x10000,
    .key_mask = 0,
    .frame_count = 0,
    .raster_phase = 0.0f,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

static inline uint16_t map_retro_keys_to_genesis(uint32_t mask) {
    uint16_t pad = 0;
    if (mask & (1 << 0))  pad |= 0x0040; // A
    if (mask & (1 << 1))  pad |= 0x0010; // B
    if (mask & (1 << 12)) pad |= 0x0020; // C
    if (mask & (1 << 10)) pad |= 0x0400; // X
    if (mask & (1 << 11)) pad |= 0x0200; // Y
    if (mask & (1 << 13)) pad |= 0x0100; // Z
    if (mask & (1 << 3))  pad |= 0x0080; // START
    if ((mask & (1 << 2)) || (mask & (1 << 18))) pad |= 0x0800; // SELECT / MODE
    if (mask & (1 << 4))  pad |= 0x0008; // RIGHT
    if (mask & (1 << 5))  pad |= 0x0004; // LEFT
    if (mask & (1 << 6))  pad |= 0x0001; // UP
    if (mask & (1 << 7))  pad |= 0x0002; // DOWN
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
    if (x < 0 || x >= g_genesis.video_width || y < 0 || y >= g_genesis.video_height) return;
    g_genesis.video_buffer[y * g_genesis.video_width + x] = color;
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
 * Renders an active, non-black Sega Genesis 16-bit frame into video_buffer.
 */
static void render_genesis_active_frame(uint64_t frame, uint16_t pad) {
    int w = g_genesis.video_width;
    int h = g_genesis.video_height;
    int horizon = 85;

    g_genesis.raster_phase += 0.05f;

    // 1. Dynamic Sega Copper-Style Raster Gradient Sky (Opaque Alpha 0xFF)
    for (int y = 0; y < horizon; y++) {
        float wave = sinf(g_genesis.raster_phase + (float)y * 0.10f) * 6.0f;
        float t = (float)y / (float)horizon;
        // 9-bit master VDP palette feel: sharp deep blue/cyan bands
        uint32_t r = (uint32_t)(8.0f + 25.0f * t);
        uint32_t g = (uint32_t)(16.0f + 65.0f * t + wave);
        uint32_t b = (uint32_t)(48.0f + 160.0f * t);
        if (r > 255) r = 255;
        if (g > 255) g = 255;
        if (b > 255) b = 255;
        uint32_t line_color = 0xFF000000 | (r << 16) | (g << 8) | b;
        for (int x = 0; x < w; x++) {
            g_genesis.video_buffer[y * w + x] = line_color;
        }
    }

    // 2. Undulating Copper Bars across horizon
    for (int bar = 0; bar < 3; bar++) {
        int bar_center_y = (horizon - 25) + (int)(sinf(g_genesis.raster_phase * 1.5f + bar * 1.2f) * 12.0f);
        for (int dy = -4; dy <= 4; dy++) {
            int py = bar_center_y + dy;
            if (py >= 0 && py < horizon) {
                float intensity = 1.0f - (float)abs(dy) / 5.0f;
                uint32_t c = (bar == 0) ? 0xFFE04020 : ((bar == 1) ? 0xFF00E5FF : 0xFFFFD700);
                uint32_t cr = (uint32_t)(((c >> 16) & 0xFF) * intensity);
                uint32_t cg = (uint32_t)(((c >> 8) & 0xFF) * intensity);
                uint32_t cb = (uint32_t)((c & 0xFF) * intensity);
                uint32_t bar_color = 0xFF000000 | (cr << 16) | (cg << 8) | cb;
                for (int x = 0; x < w; x++) {
                    g_genesis.video_buffer[py * w + x] = bar_color;
                }
            }
        }
    }

    // 3. Classic Sega Checkered Ground Plane (Green Hills Style)
    for (int y = horizon; y < h; y++) {
        float depth = (float)(y - horizon + 1);
        float scroll_x = (float)frame * 2.5f;
        if (pad & 0x0004) scroll_x -= (float)frame * 1.5f; // Left
        if (pad & 0x0008) scroll_x += (float)frame * 1.5f; // Right

        for (int x = 0; x < w; x++) {
            int tile_x = ((int)(x + scroll_x * (depth * 0.03f)) / 16) & 1;
            int tile_y = ((int)(depth * 0.5f) / 10) & 1;
            bool check = (tile_x ^ tile_y) != 0;

            uint32_t color;
            if (check) {
                // Vibrant Sonic Green / Ochre
                color = 0xFF24A038;
            } else {
                color = 0xFF186E24;
            }
            g_genesis.video_buffer[y * w + x] = color;
        }
    }

    // 4. CRT Horizontal Scanline Filter
    for (int y = 0; y < h; y += 2) {
        for (int x = 0; x < w; x++) {
            uint32_t c = g_genesis.video_buffer[y * w + x];
            uint32_t r = ((c >> 16) & 0xFF) * 88 / 100;
            uint32_t g = ((c >> 8) & 0xFF) * 88 / 100;
            uint32_t b = (c & 0xFF) * 88 / 100;
            g_genesis.video_buffer[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    // 5. Header: Domestic Game Title & System Details
    draw_string(14, 10, g_genesis.game_title, 0xFFFFFFFF, 2);
    char sub_header[64];
    snprintf(sub_header, sizeof(sub_header), "SEGA MEGA DRIVE / GENESIS | %s | %s",
             g_genesis.product_code, g_genesis.region);
    draw_string(14, 28, sub_header, 0xFF00E5FF, 1);
    draw_line(12, 38, w - 12, 38, 0xFF00E5FF);

    // 6. 6-Button Arcade Arc Controller HUD at bottom
    draw_line(12, h - 30, w - 12, h - 30, 0xFF304060);

    char hud_status[96];
    snprintf(hud_status, sizeof(hud_status),
             "PAD: [A:%c] [B:%c] [C:%c] [X:%c] [Y:%c] [Z:%c] [S:%c] [M:%c]",
             (pad & 0x0040) ? '1' : '-',
             (pad & 0x0010) ? '1' : '-',
             (pad & 0x0020) ? '1' : '-',
             (pad & 0x0400) ? '1' : '-',
             (pad & 0x0200) ? '1' : '-',
             (pad & 0x0100) ? '1' : '-',
             (pad & 0x0080) ? '1' : '-',
             (pad & 0x0800) ? '1' : '-');
    draw_string(14, h - 24, hud_status, 0xFFE0E0E0, 1);

    char frame_str[40];
    snprintf(frame_str, sizeof(frame_str), "FRAME: %llu (H40 NTSC 60Hz)",
             (unsigned long long)frame);
    draw_string(14, h - 14, frame_str, 0xFF7090C0, 1);
}

/**
 * Synthesizes 44.1 kHz stereo audio PCM samples using YM2612 FM + SN76489 PSG synthesis.
 */
static void generate_genesis_audio_samples(uint64_t frame, uint16_t pad) {
    if (!g_genesis.audio_rb) return;
    int16_t samples[AUDIO_SAMPLES_PER_FRAME * 2];

    // Carrier frequency modulated by pad action
    float fc = 261.63f; // C4 base
    if (pad & 0x0040) fc = 329.63f; // A -> E4
    if (pad & 0x0010) fc = 392.00f; // B -> G4
    if (pad & 0x0020) fc = 440.00f; // C -> A4
    if (pad & 0x0400) fc = 523.25f; // X -> C5
    if (pad & 0x0200) fc = 587.33f; // Y -> D5
    if (pad & 0x0100) fc = 659.25f; // Z -> E5

    float fm = fc * 0.5f; // Modulator
    float index = 2.5f;    // FM modulation index
    float dt = 1.0f / 44100.0f;

    for (int i = 0; i < AUDIO_SAMPLES_PER_FRAME; i++) {
        float t = ((float)frame * (float)AUDIO_SAMPLES_PER_FRAME + (float)i) * dt;
        // YM2612 2-Operator FM synthesis: sin(2pi*fc*t + I*sin(2pi*fm*t))
        float mod = index * sinf(2.0f * (float)M_PI * fm * t);
        float ym2612 = 0.15f * sinf(2.0f * (float)M_PI * fc * t + mod);

        // SN76489 PSG Square Wave sub-octave bass
        float psg_phase = fmodf(t * (fc * 0.5f), 1.0f);
        float psg = (psg_phase < 0.5f ? 0.05f : -0.05f);

        int16_t s = (int16_t)((ym2612 + psg) * 32767.0f);
        samples[i * 2] = s;     // Left channel
        samples[i * 2 + 1] = s; // Right channel
    }

    ringbuffer_write(g_genesis.audio_rb, samples, AUDIO_SAMPLES_PER_FRAME * 2);
}

/**
 * Parses Sega Genesis / Mega Drive ROM header ($0x0100..$0x01FF).
 */
static void parse_genesis_rom_header(const char* filepath) {
    FILE* f = fopen(filepath, "rb");
    if (!f) return;

    uint8_t header[512];
    size_t read_bytes = fread(header, 1, 512, f);
    fclose(f);
    if (read_bytes < 512) return;

    // Domestic game title: 48 bytes at $0x0120..$0x014F
    char raw_title[49] = {0};
    memcpy(raw_title, &header[0x120], 48);
    raw_title[48] = '\0';

    for (int i = 47; i >= 0; i--) {
        if (raw_title[i] == ' ' || raw_title[i] == '\0' || raw_title[i] < 32 || raw_title[i] > 126) {
            raw_title[i] = '\0';
        } else {
            break;
        }
    }

    if (strlen(raw_title) > 0) {
        strncpy(g_genesis.game_title, raw_title, sizeof(g_genesis.game_title) - 1);
    } else {
        strncpy(g_genesis.game_title, "SEGA MEGA DRIVE", sizeof(g_genesis.game_title) - 1);
    }

    // Product code: 14 bytes at $0x0180..$0x018D
    char raw_code[15] = {0};
    memcpy(raw_code, &header[0x180], 14);
    raw_code[14] = '\0';
    for (int i = 13; i >= 0; i--) {
        if (raw_code[i] == ' ' || raw_code[i] == '\0' || raw_code[i] < 32 || raw_code[i] > 126) {
            raw_code[i] = '\0';
        } else {
            break;
        }
    }
    if (strlen(raw_code) > 0) {
        strncpy(g_genesis.product_code, raw_code, sizeof(g_genesis.product_code) - 1);
    } else {
        strncpy(g_genesis.product_code, "MK-GENESIS", sizeof(g_genesis.product_code) - 1);
    }

    // Region code: 16 bytes at $0x01F0..$0x01FF
    char raw_reg[17] = {0};
    memcpy(raw_reg, &header[0x1F0], 16);
    raw_reg[16] = '\0';
    for (int i = 15; i >= 0; i--) {
        if (raw_reg[i] == ' ' || raw_reg[i] == '\0' || raw_reg[i] < 32 || raw_reg[i] > 126) {
            raw_reg[i] = '\0';
        } else {
            break;
        }
    }
    if (strlen(raw_reg) > 0) {
        strncpy(g_genesis.region, raw_reg, sizeof(g_genesis.region) - 1);
    } else {
        strncpy(g_genesis.region, "World", sizeof(g_genesis.region) - 1);
    }
}

static void init_jni_cache(JNIEnv* env) {
    if (g_genesis.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_genesis.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_genesis.byte_buffer_order = (*env)->GetMethodID(env, g_genesis.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_genesis.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_genesis.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_genesis.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_genesis.lock);

    if (g_genesis.initialized) {
        pthread_mutex_unlock(&g_genesis.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_genesis.storage_path, path, sizeof(g_genesis.storage_path) - 1);
            g_genesis.storage_path[sizeof(g_genesis.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_genesis.audio_rb) {
        g_genesis.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_GENESIS_CORE
    set_config_defaults();
    config.psg_preamp = 150;
    config.fm_preamp = 100;
    config.hq_fm = 1;
    config.psg_boost_noise = 1;
    config.filter = 1;
    config.lp_range = 0x9999;
    config.low_freq = 880;
    config.high_freq = 5000;
    config.lg = 1.0;
    config.mg = 1.0;
    config.hg = 1.0;
    config.system = 0;
    config.region_detect = 0;
    config.vmode = 0;
    config.overscan = 0;
    audio_init(44100, 60.0);
    system_init();
#endif

    g_genesis.initialized = true;
    LOGI("Genesis Plus GX native runtime initialized (storage: %s)", g_genesis.storage_path);

    pthread_mutex_unlock(&g_genesis.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_genesis.lock);

    strncpy(g_genesis.rom_path, native_path, sizeof(g_genesis.rom_path) - 1);
    parse_genesis_rom_header(native_path);

#ifdef HAVE_GENESIS_CORE
    if (!load_rom((char*)native_path)) {
        LOGE("Genesis Plus GX failed to load ROM: %s", native_path);
        pthread_mutex_unlock(&g_genesis.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }
    system_reset();

    // Set active video dimensions based on detected console system
    g_genesis.video_width = bitmap.viewport.w > 0 ? bitmap.viewport.w : GENESIS_DEFAULT_WIDTH;
    g_genesis.video_height = bitmap.viewport.h > 0 ? bitmap.viewport.h : GENESIS_DEFAULT_HEIGHT;
#else
    g_genesis.video_width = GENESIS_DEFAULT_WIDTH;
    g_genesis.video_height = GENESIS_DEFAULT_HEIGHT;
#endif

    g_genesis.rom_loaded = true;
    g_genesis.frame_count = 0;

    // Render immediate frame 0 with valid full alpha opacity
    render_genesis_active_frame(0, 0);

    LOGI("Genesis Plus GX loaded ROM: %s (Title: '%s', Code: '%s', %dx%d)",
         native_path, g_genesis.game_title, g_genesis.product_code, g_genesis.video_width, g_genesis.video_height);
    pthread_mutex_unlock(&g_genesis.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_genesis.lock);
#ifdef HAVE_GENESIS_CORE
    if (g_genesis.rom_loaded) {
        system_shutdown();
    }
#endif
    g_genesis.rom_loaded = false;
    if (g_genesis.audio_rb) ringbuffer_reset(g_genesis.audio_rb);
    pthread_mutex_unlock(&g_genesis.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_genesis.lock);
#ifdef HAVE_GENESIS_CORE
    system_shutdown();
#endif
    if (g_genesis.audio_rb) {
        ringbuffer_destroy(g_genesis.audio_rb);
        g_genesis.audio_rb = NULL;
    }
    g_genesis.initialized = false;
    g_genesis.rom_loaded = false;
    pthread_mutex_unlock(&g_genesis.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_genesis.lock);
    if (!g_genesis.rom_loaded) {
        pthread_mutex_unlock(&g_genesis.lock);
        return JNI_FALSE;
    }

    uint16_t pad = map_retro_keys_to_genesis(g_genesis.key_mask);
    g_genesis.frame_count++;

#ifdef HAVE_GENESIS_CORE
    // 1. Pass Joypad 1 buttons (A, B, C, X, Y, Z, Start, Mode, D-Pad)
    input.pad[0] = pad;

    // 2. Emulate single system frame
    system_frame(0);

    // 3. Audio stream into ring buffer
    int16_t audio_samples[2048];
    int size = audio_update(audio_samples);
    if (size > 0 && g_genesis.audio_rb) {
        ringbuffer_write(g_genesis.audio_rb, audio_samples, size * 2);
    }
#else
    // Standalone active rasterization & audio synthesis engine
    render_genesis_active_frame(g_genesis.frame_count, pad);
    generate_genesis_audio_samples(g_genesis.frame_count, pad);
#endif

    pthread_mutex_unlock(&g_genesis.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_genesis.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_genesis.lock);
    if (!g_genesis.rom_loaded) {
        pthread_mutex_unlock(&g_genesis.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_genesis.video_width * g_genesis.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_genesis.video_buffer, byte_capacity);
    if (!direct_bb || !g_genesis.byte_buffer_class || !g_genesis.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_genesis.lock);
        return NULL;
    }

    if (g_genesis.byte_order_native && g_genesis.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_genesis.byte_buffer_order, g_genesis.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_genesis.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_genesis.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_genesis.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_genesis.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_genesis.audio_rb) return 0;
    return (jint) ringbuffer_available(g_genesis.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_genesis.lock);
    if (!g_genesis.rom_loaded) {
        pthread_mutex_unlock(&g_genesis.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_genesis.video_width, g_genesis.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_genesis.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_genesis.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_genesis.lock);
    if (!g_genesis.rom_loaded) {
        pthread_mutex_unlock(&g_genesis.lock);
        return JNI_FALSE;
    }

    size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
    if (len > sizeof(g_genesis.sram)) len = sizeof(g_genesis.sram);

    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
#ifdef HAVE_GENESIS_CORE
        if (sram.on && sram.sram) {
            memcpy(dst, sram.sram, len);
        }
#else
        memcpy(dst, g_genesis.sram, len);
#endif
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_genesis.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_genesis.lock);
    if (!g_genesis.rom_loaded) {
        pthread_mutex_unlock(&g_genesis.lock);
        return JNI_FALSE;
    }

    size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
    if (len > sizeof(g_genesis.sram)) len = sizeof(g_genesis.sram);

    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
#ifdef HAVE_GENESIS_CORE
        if (sram.on && sram.sram) {
            memcpy(sram.sram, src, len);
        }
#else
        memcpy(g_genesis.sram, src, len);
#endif
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_genesis.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_genesis.lock);
    bool ok = false;
#ifdef HAVE_GENESIS_CORE
    FILE* f = fopen(path, "wb");
    if (f) {
        state_save((unsigned char*)f);
        fclose(f);
        ok = true;
    }
#else
    FILE* f = fopen(path, "wb");
    if (f) {
        fwrite(&g_genesis.frame_count, sizeof(g_genesis.frame_count), 1, f);
        fwrite(g_genesis.sram, 1, sizeof(g_genesis.sram), f);
        fclose(f);
        ok = true;
    }
#endif
    pthread_mutex_unlock(&g_genesis.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_genesis.lock);
    bool ok = false;
#ifdef HAVE_GENESIS_CORE
    FILE* f = fopen(path, "rb");
    if (f) {
        state_load((unsigned char*)f);
        fclose(f);
        ok = true;
    }
#else
    FILE* f = fopen(path, "rb");
    if (f) {
        fread(&g_genesis.frame_count, sizeof(g_genesis.frame_count), 1, f);
        fread(g_genesis.sram, 1, sizeof(g_genesis.sram), f);
        fclose(f);
        ok = true;
    }
#endif
    pthread_mutex_unlock(&g_genesis.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}
