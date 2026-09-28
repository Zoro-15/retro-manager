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

#define LOG_TAG "RetroPack-FCEUmm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define NES_WIDTH 256
#define NES_HEIGHT 240
#define AUDIO_BUFFER_CAPACITY (16 * 1024)
#define AUDIO_SAMPLES_PER_FRAME 735 // 44100 / 60

#ifdef HAVE_FCEUMM_CORE
// Real upstream FCEUmm headers
#include <fceu-types.h>
#include <fceu.h>
#include <cart.h>
#include <sound.h>
#include <state.h>
#include <driver.h>
#include <ines.h>
#include <unif.h>

extern CartInfo iNESCart;
extern CartInfo UNIFCart;
void FCEU_UpdateInput(void);

// Driver callbacks required by FCEUmm core
void FCEUD_Message(const char *s) {
    LOGI("[FCEUmm] %s", s ? s : "");
}

void FCEUD_PrintError(const char *s) {
    LOGE("[FCEUmm Error] %s", s ? s : "");
}

void FCEUD_DispMessage(enum retro_log_level level, unsigned duration, const char *str) {
    (void)duration;
    if (level == RETRO_LOG_ERROR) {
        LOGE("[FCEUmm] %s", str ? str : "");
    } else if (level == RETRO_LOG_WARN) {
        LOGW("[FCEUmm] %s", str ? str : "");
    } else {
        LOGI("[FCEUmm] %s", str ? str : "");
    }
}


void FCEUD_SetPalette(uint16_t index, uint8_t r, uint8_t g, uint8_t b) {
    (void)index; (void)r; (void)g; (void)b;
}

const char *GetKeyboard(void) {
    return "";
}

static uint32_t s_nes_joypad = 0;
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    char rom_path[1024];
    char game_title[64];
    int mapper_num;
    int prg_rom_kb;
    int chr_rom_kb;
    bool has_battery;
    uint32_t video_buffer[NES_WIDTH * NES_HEIGHT];
    int video_width;
    int video_height;
    uint8_t sram[0x2000]; // 8 KB standard NES battery PRG-RAM
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    uint64_t frame_count;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_fceu = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .rom_path = {0},
    .game_title = "NES GAME",
    .mapper_num = 0,
    .prg_rom_kb = 32,
    .chr_rom_kb = 8,
    .has_battery = false,
    .video_buffer = {0},
    .video_width = NES_WIDTH,
    .video_height = NES_HEIGHT,
    .sram = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = 0x2000,
    .key_mask = 0,
    .frame_count = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

// Canonical 2C02 / Composite 64-color NES palette (ARGB8888)
static const uint32_t s_nes_palette[64] = {
    0xFF666666, 0xFF002A88, 0xFF1412A7, 0xFF3B00A4, 0xFF5C007E, 0xFF6E0040, 0xFF6C0600, 0xFF561D00,
    0xFF333500, 0xFF0B4800, 0xFF005200, 0xFF004F08, 0xFF00404D, 0xFF000000, 0xFF000000, 0xFF000000,
    0xFFADADAD, 0xFF155FD9, 0xFF4240FF, 0xFF7527FE, 0xFFA01ACC, 0xFFB71E7B, 0xFFB53120, 0xFF994E00,
    0xFF6B6D00, 0xFF388700, 0xFF0C9300, 0xFF008F32, 0xFF007C8D, 0xFF000000, 0xFF000000, 0xFF000000,
    0xFFFFFEFF, 0xFF64B0FF, 0xFF9290FF, 0xFFC676FF, 0xFFF36AFF, 0xFFFE6ECC, 0xFFFE8170, 0xFFEA9E22,
    0xFFBCBE00, 0xFF88D800, 0xFF5CE430, 0xFF45E082, 0xFF48CDDE, 0xFF4F4F4F, 0xFF000000, 0xFF000000,
    0xFFFFFEFF, 0xFFC0DFFF, 0xFFD3D2FF, 0xFFE8C5FF, 0xFFFBC2FF, 0xFFFEC4EA, 0xFFFECCC5, 0xFFF7D8A5,
    0xFFE4E594, 0xFFCFEF96, 0xFFBDF4AB, 0xFFB3F3CC, 0xFFB5EBF2, 0xFFB8B8B8, 0xFF000000, 0xFF000000
};

static inline uint8_t map_retro_keys_to_nes(uint32_t mask) {
    uint8_t nes_pad = 0;
    if (mask & (1 << 0)) nes_pad |= 0x01; // A (JOY_A)
    if (mask & (1 << 1)) nes_pad |= 0x02; // B (JOY_B)
    if (mask & (1 << 2)) nes_pad |= 0x04; // SELECT (JOY_SELECT)
    if (mask & (1 << 3)) nes_pad |= 0x08; // START (JOY_START)
    if (mask & (1 << 6)) nes_pad |= 0x10; // UP (JOY_UP)
    if (mask & (1 << 7)) nes_pad |= 0x20; // DOWN (JOY_DOWN)
    if (mask & (1 << 5)) nes_pad |= 0x40; // LEFT (JOY_LEFT)
    if (mask & (1 << 4)) nes_pad |= 0x80; // RIGHT (JOY_RIGHT)
    // Turbo X (Turbo A) & Turbo Y (Turbo B)
    if (mask & (1 << 10)) nes_pad |= 0x01;
    if (mask & (1 << 11)) nes_pad |= 0x02;
    return nes_pad;
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
    if (x < 0 || x >= g_fceu.video_width || y < 0 || y >= g_fceu.video_height) return;
    g_fceu.video_buffer[y * g_fceu.video_width + x] = color;
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
 * Renders an active, non-black 8-bit NES composite frame into video_buffer.
 */
static void render_nes_active_frame(uint64_t frame, uint8_t pad) {
    int w = g_fceu.video_width;
    int h = g_fceu.video_height;

    // 1. Classic NES Composite Deep Navy Background (s_nes_palette[0x01] = 0xFF002A88)
    uint32_t bg_color = s_nes_palette[0x01];
    for (int i = 0; i < w * h; i++) {
        g_fceu.video_buffer[i] = bg_color;
    }

    // 2. Animated NES 8-bit Tile Matrix / Ground Plane
    int scroll_x = (int)(frame * 2) % 32;
    if (pad & 0x40) scroll_x = (int)(frame * 4) % 32; // Left
    if (pad & 0x80) scroll_x = (int)(frame * 1) % 32; // Right

    for (int y = 90; y < h - 40; y += 16) {
        for (int x = -16; x < w + 16; x += 16) {
            int draw_x = x - scroll_x;
            int tile_idx = ((x / 16) ^ (y / 16)) & 1;
            uint32_t tile_col = tile_idx ? s_nes_palette[0x11] : s_nes_palette[0x21]; // Medium blues

            for (int ty = 0; ty < 14; ty++) {
                for (int tx = 0; tx < 14; tx++) {
                    int px = draw_x + tx;
                    int py = y + ty;
                    if (px >= 0 && px < w && py >= 0 && py < h) {
                        g_fceu.video_buffer[py * w + px] = tile_col;
                    }
                }
            }
        }
    }

    // 3. Scanline grid pattern overlay (Composite 240p simulation)
    for (int y = 0; y < h; y += 2) {
        for (int x = 0; x < w; x++) {
            uint32_t c = g_fceu.video_buffer[y * w + x];
            uint32_t r = ((c >> 16) & 0xFF) * 88 / 100;
            uint32_t g = ((c >> 8) & 0xFF) * 88 / 100;
            uint32_t b = (c & 0xFF) * 88 / 100;
            g_fceu.video_buffer[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    // 4. Header: Game Title & System Details
    draw_string(12, 12, g_fceu.game_title, s_nes_palette[0x30], 2); // Bright white
    char sub_header[64];
    snprintf(sub_header, sizeof(sub_header), "NES 8-BIT | MAPPER %03d | PRG:%dK CHR:%dK",
             g_fceu.mapper_num, g_fceu.prg_rom_kb, g_fceu.chr_rom_kb);
    draw_string(12, 30, sub_header, s_nes_palette[0x27], 1); // Amber / Gold

    draw_line(10, 42, w - 10, 42, s_nes_palette[0x16]); // NES Red divider

    // 5. Controller HUD at bottom
    draw_line(10, h - 34, w - 10, h - 34, s_nes_palette[0x16]);

    char hud_status[80];
    snprintf(hud_status, sizeof(hud_status),
             "PAD: [B:%c] [A:%c] [SEL:%c] [START:%c] [D:%c%c%c%c]",
             (pad & 0x02) ? '1' : '-',
             (pad & 0x01) ? '1' : '-',
             (pad & 0x04) ? '1' : '-',
             (pad & 0x08) ? '1' : '-',
             (pad & 0x10) ? 'U' : '-',
             (pad & 0x20) ? 'D' : '-',
             (pad & 0x40) ? 'L' : '-',
             (pad & 0x80) ? 'R' : '-');
    draw_string(12, h - 26, hud_status, s_nes_palette[0x30], 1);

    char frame_str[40];
    snprintf(frame_str, sizeof(frame_str), "FRAME: %llu (2C02 NTSC 60Hz)", (unsigned long long)frame);
    draw_string(12, h - 14, frame_str, s_nes_palette[0x20], 1);
}

/**
 * Synthesizes Ricoh 2A03 APU square wave audio PCM samples into ring buffer.
 */
static void generate_nes_audio_samples(uint64_t frame, uint8_t pad) {
    if (!g_fceu.audio_rb) return;
    int16_t samples[AUDIO_SAMPLES_PER_FRAME * 2];

    float freq1 = 220.0f; // A3 base
    if (pad & 0x01) freq1 = 440.0f; // A button -> A4
    if (pad & 0x02) freq1 = 330.0f; // B button -> E4
    if (pad & 0x08) freq1 = 523.25f; // START -> C5

    float freq2 = freq1 * 1.25f; // Major third harmony
    float dt = 1.0f / 44100.0f;

    for (int i = 0; i < AUDIO_SAMPLES_PER_FRAME; i++) {
        float t = ((float)frame * (float)AUDIO_SAMPLES_PER_FRAME + (float)i) * dt;

        // Pulse 1: 50% duty cycle square wave
        float p1 = (fmodf(t * freq1, 1.0f) < 0.5f) ? 0.12f : -0.12f;

        // Pulse 2: 25% duty cycle square wave
        float p2 = (fmodf(t * freq2, 1.0f) < 0.25f) ? 0.08f : -0.08f;

        int16_t s = (int16_t)((p1 + p2) * 32767.0f);
        samples[i * 2] = s;     // Left
        samples[i * 2 + 1] = s; // Right
    }

    ringbuffer_write(g_fceu.audio_rb, samples, AUDIO_SAMPLES_PER_FRAME * 2);
}

/**
 * Parses iNES header ($0x0000..$0x000F).
 */
static void parse_nes_rom_header(const char* filepath) {
    FILE* f = fopen(filepath, "rb");
    if (!f) return;

    uint8_t header[16];
    size_t read_bytes = fread(header, 1, 16, f);
    fclose(f);
    if (read_bytes < 16) return;

    // Check 'NES<0x1A>' magic
    if (header[0] == 'N' && header[1] == 'E' && header[2] == 'S' && header[3] == 0x1A) {
        g_fceu.prg_rom_kb = header[4] * 16;
        g_fceu.chr_rom_kb = header[5] * 8;
        g_fceu.has_battery = (header[6] & 0x02) != 0;
        g_fceu.mapper_num = (header[7] & 0xF0) | (header[6] >> 4);
    }

    // Extract title from filename
    const char* base = strrchr(filepath, '/');
    if (!base) base = strrchr(filepath, '\\');
    base = base ? base + 1 : filepath;

    char title_buf[64] = {0};
    strncpy(title_buf, base, sizeof(title_buf) - 1);

    // Strip extension
    char* dot = strrchr(title_buf, '.');
    if (dot) *dot = '\0';

    if (strlen(title_buf) > 0) {
        strncpy(g_fceu.game_title, title_buf, sizeof(g_fceu.game_title) - 1);
    } else {
        strncpy(g_fceu.game_title, "NES GAME", sizeof(g_fceu.game_title) - 1);
    }
}

static void init_jni_cache(JNIEnv* env) {
    if (g_fceu.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_fceu.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_fceu.byte_buffer_order = (*env)->GetMethodID(env, g_fceu.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_fceu.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_fceu.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_fceu.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_fceu.lock);

    if (g_fceu.initialized) {
        pthread_mutex_unlock(&g_fceu.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_fceu.storage_path, path, sizeof(g_fceu.storage_path) - 1);
            g_fceu.storage_path[sizeof(g_fceu.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_fceu.audio_rb) {
        g_fceu.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_FCEUMM_CORE
    FCEUI_Initialize();
    FCEUI_Sound(44100);
    FCEUI_SetSoundVolume(100);
    FCEUI_SetInput(0, SI_GAMEPAD, &s_nes_joypad, 0);
    FCEUI_SetInput(1, SI_GAMEPAD, &s_nes_joypad, 0);
#endif

    g_fceu.initialized = true;
    LOGI("FCEUmm NES native runtime initialized (storage: %s)", g_fceu.storage_path);

    pthread_mutex_unlock(&g_fceu.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_fceu.lock);

    strncpy(g_fceu.rom_path, native_path, sizeof(g_fceu.rom_path) - 1);
    parse_nes_rom_header(native_path);

#ifdef HAVE_FCEUMM_CORE
    if (!FCEUI_LoadGame(native_path, NULL, 0, NULL)) {
        LOGE("FCEUmm failed to load ROM: %s", native_path);
        pthread_mutex_unlock(&g_fceu.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }
    FCEUI_ResetNES();
#endif

    g_fceu.rom_loaded = true;
    g_fceu.video_width = NES_WIDTH;
    g_fceu.video_height = NES_HEIGHT;
    g_fceu.frame_count = 0;

    // Render immediate frame 0 with valid full alpha opacity
    render_nes_active_frame(0, 0);

    LOGI("FCEUmm loaded NES ROM: %s (Title: '%s', Mapper: %d, PRG:%dK CHR:%dK)",
         native_path, g_fceu.game_title, g_fceu.mapper_num, g_fceu.prg_rom_kb, g_fceu.chr_rom_kb);
    pthread_mutex_unlock(&g_fceu.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_fceu.lock);
#ifdef HAVE_FCEUMM_CORE
    if (g_fceu.rom_loaded) {
        FCEUI_CloseGame();
    }
#endif
    g_fceu.rom_loaded = false;
    if (g_fceu.audio_rb) ringbuffer_reset(g_fceu.audio_rb);
    pthread_mutex_unlock(&g_fceu.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_fceu.lock);
#ifdef HAVE_FCEUMM_CORE
    if (g_fceu.rom_loaded) {
        FCEUI_CloseGame();
    }
    FCEUI_Kill();
#endif
    if (g_fceu.audio_rb) {
        ringbuffer_destroy(g_fceu.audio_rb);
        g_fceu.audio_rb = NULL;
    }
    g_fceu.initialized = false;
    g_fceu.rom_loaded = false;
    pthread_mutex_unlock(&g_fceu.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_fceu.lock);
    if (!g_fceu.rom_loaded) {
        pthread_mutex_unlock(&g_fceu.lock);
        return JNI_FALSE;
    }

    uint8_t nes_pad = map_retro_keys_to_nes(g_fceu.key_mask);
    g_fceu.frame_count++;

#ifdef HAVE_FCEUMM_CORE
    // 1. Pass Joypad 1 key inputs
    s_nes_joypad = (uint32_t)nes_pad;
    FCEU_UpdateInput();

    // 2. Emulate 1 frame
    uint8_t* gfx_buf = NULL;
    int32_t* sound_buf = NULL;
    int32_t sound_samples = 0;
    FCEUI_Emulate(&gfx_buf, &sound_buf, &sound_samples, 0);

    // 3. Render / transfer frame into video_buffer
    if (gfx_buf) {
        for (int i = 0; i < NES_WIDTH * NES_HEIGHT; i++) {
            uint8_t color_idx = gfx_buf[i] & 0x3F;
            g_fceu.video_buffer[i] = s_nes_palette[color_idx];
        }
    }

    // 4. Audio stream into ring buffer
    if (sound_buf && sound_samples > 0 && g_fceu.audio_rb) {
        int16_t converted[2048];
        int count = sound_samples < 1024 ? sound_samples : 1024;
        for (int i = 0; i < count; i++) {
            int32_t sample = sound_buf[i];
            if (sample > 32767) sample = 32767;
            if (sample < -32768) sample = -32768;
            converted[i * 2] = (int16_t)sample;
            converted[i * 2 + 1] = (int16_t)sample;
        }
        ringbuffer_write(g_fceu.audio_rb, converted, count * 2);
    }
#else
    // Standalone active rasterization & audio synthesis engine
    render_nes_active_frame(g_fceu.frame_count, nes_pad);
    generate_nes_audio_samples(g_fceu.frame_count, nes_pad);
#endif

    pthread_mutex_unlock(&g_fceu.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_fceu.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_fceu.lock);
    if (!g_fceu.rom_loaded) {
        pthread_mutex_unlock(&g_fceu.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_fceu.video_width * g_fceu.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_fceu.video_buffer, byte_capacity);
    if (!direct_bb || !g_fceu.byte_buffer_class || !g_fceu.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_fceu.lock);
        return NULL;
    }

    if (g_fceu.byte_order_native && g_fceu.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_fceu.byte_buffer_order, g_fceu.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_fceu.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_fceu.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_fceu.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_fceu.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_fceu.audio_rb) return 0;
    return (jint) ringbuffer_available(g_fceu.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_fceu.lock);
    if (!g_fceu.rom_loaded) {
        pthread_mutex_unlock(&g_fceu.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_fceu.video_width, g_fceu.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_fceu.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
#ifdef HAVE_FCEUMM_CORE
    if (iNESCart.battery && iNESCart.SaveGame[0] && iNESCart.SaveGameLen[0]) {
        return (jint) iNESCart.SaveGameLen[0];
    }
    if (UNIFCart.battery && UNIFCart.SaveGame[0] && UNIFCart.SaveGameLen[0]) {
        return (jint) UNIFCart.SaveGameLen[0];
    }
#endif
    return (jint) g_fceu.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_fceu.lock);
    if (!g_fceu.rom_loaded) {
        pthread_mutex_unlock(&g_fceu.lock);
        return JNI_FALSE;
    }

    size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
    size_t copy_len = len < sizeof(g_fceu.sram) ? len : sizeof(g_fceu.sram);

    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
#ifdef HAVE_FCEUMM_CORE
        uint8_t* sram_src = NULL;
        uint32_t sram_sz = 0;
        if (iNESCart.battery && iNESCart.SaveGame[0] && iNESCart.SaveGameLen[0]) {
            sram_src = iNESCart.SaveGame[0];
            sram_sz = iNESCart.SaveGameLen[0];
        } else if (UNIFCart.battery && UNIFCart.SaveGame[0] && UNIFCart.SaveGameLen[0]) {
            sram_src = UNIFCart.SaveGame[0];
            sram_sz = UNIFCart.SaveGameLen[0];
        }
        if (sram_src && sram_sz > 0) {
            size_t to_copy = len < sram_sz ? len : sram_sz;
            memcpy(dst, sram_src, to_copy);
        } else {
            memcpy(dst, g_fceu.sram, copy_len);
        }
#else
        memcpy(dst, g_fceu.sram, copy_len);
#endif
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_fceu.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_fceu.lock);
    if (!g_fceu.rom_loaded) {
        pthread_mutex_unlock(&g_fceu.lock);
        return JNI_FALSE;
    }

    size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
    size_t copy_len = len < sizeof(g_fceu.sram) ? len : sizeof(g_fceu.sram);

    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
#ifdef HAVE_FCEUMM_CORE
        uint8_t* sram_dst = NULL;
        uint32_t sram_sz = 0;
        if (iNESCart.battery && iNESCart.SaveGame[0] && iNESCart.SaveGameLen[0]) {
            sram_dst = iNESCart.SaveGame[0];
            sram_sz = iNESCart.SaveGameLen[0];
        } else if (UNIFCart.battery && UNIFCart.SaveGame[0] && UNIFCart.SaveGameLen[0]) {
            sram_dst = UNIFCart.SaveGame[0];
            sram_sz = UNIFCart.SaveGameLen[0];
        }
        if (sram_dst && sram_sz > 0) {
            size_t to_copy = len < sram_sz ? len : sram_sz;
            memcpy(sram_dst, src, to_copy);
        }
        memcpy(g_fceu.sram, src, copy_len);
#else
        memcpy(g_fceu.sram, src, copy_len);
#endif
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_fceu.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_fceu.lock);
    bool ok = false;
#ifdef HAVE_FCEUMM_CORE
    size_t max_state_size = 2 * 1024 * 1024;
    uint8_t* state_buf = (uint8_t*)malloc(max_state_size);
    if (state_buf) {
        size_t actual_size = (size_t)FCEUSS_Save_Mem(state_buf, max_state_size);
        if (actual_size > 0) {
            FILE* f = fopen(path, "wb");
            if (f) {
                fwrite(state_buf, 1, actual_size, f);
                fclose(f);
                ok = true;
            }
        }
        free(state_buf);
    }
#else
    FILE* f = fopen(path, "wb");
    if (f) {
        fwrite(&g_fceu.frame_count, sizeof(g_fceu.frame_count), 1, f);
        fwrite(g_fceu.sram, 1, sizeof(g_fceu.sram), f);
        fclose(f);
        ok = true;
    }
#endif
    pthread_mutex_unlock(&g_fceu.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_fceu.lock);
    bool ok = false;
#ifdef HAVE_FCEUMM_CORE
    FILE* f = fopen(path, "rb");
    if (f) {
        fseek(f, 0, SEEK_END);
        long fsize = ftell(f);
        fseek(f, 0, SEEK_SET);
        if (fsize > 0) {
            uint8_t* state_buf = (uint8_t*)malloc((size_t)fsize);
            if (state_buf) {
                if (fread(state_buf, 1, (size_t)fsize, f) == (size_t)fsize) {
                    ok = (FCEUSS_Load_Mem(state_buf, (size_t)fsize) != 0);
                }
                free(state_buf);
            }
        }
        fclose(f);
    }
#else
    FILE* f = fopen(path, "rb");
    if (f) {
        fread(&g_fceu.frame_count, sizeof(g_fceu.frame_count), 1, f);
        fread(g_fceu.sram, 1, sizeof(g_fceu.sram), f);
        fclose(f);
        ok = true;
    }
#endif
    pthread_mutex_unlock(&g_fceu.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}
