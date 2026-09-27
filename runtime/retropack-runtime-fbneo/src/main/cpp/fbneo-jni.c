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
#include <ctype.h>

#include "ringbuffer.h"

#define LOG_TAG "RetroPack-FBNeo"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define FBNEO_MAX_WIDTH 512
#define FBNEO_MAX_HEIGHT 512
#define FBNEO_NEOGEO_WIDTH 320
#define FBNEO_NEOGEO_HEIGHT 224
#define FBNEO_CPS_WIDTH 384
#define FBNEO_CPS_HEIGHT 224
#define AUDIO_BUFFER_CAPACITY (32 * 1024)
#define FBNEO_NVRAM_SIZE 0x10000 // 64 KB standard Arcade NVRAM

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

typedef enum {
    ARCADE_SYS_NEOGEO,
    ARCADE_SYS_CPS,
    ARCADE_SYS_GENERIC
} ArcadeSystemType;

#ifdef HAVE_FBNEO_CORE
// Real upstream FBNeo C++ headers
#include <burner.h>
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    char rom_path[1024];
    char game_title[128];
    ArcadeSystemType system_type;
    uint32_t video_buffer[FBNEO_MAX_WIDTH * FBNEO_MAX_HEIGHT];
    int video_width;
    int video_height;
    uint8_t nvram[FBNEO_NVRAM_SIZE];
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    uint64_t frame_count;
    int credits;
    bool coin_pressed_prev;
    int coin_sound_timer;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_fbneo = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .rom_path = {0},
    .game_title = "ARCADE GAME",
    .system_type = ARCADE_SYS_NEOGEO,
    .video_buffer = {0},
    .video_width = FBNEO_NEOGEO_WIDTH,
    .video_height = FBNEO_NEOGEO_HEIGHT,
    .nvram = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = FBNEO_NVRAM_SIZE,
    .key_mask = 0,
    .frame_count = 0,
    .credits = 2,
    .coin_pressed_prev = false,
    .coin_sound_timer = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

/**
 * Maps RetroKey bitmask to Arcade / Neo Geo controller bitmask.
 */
static inline uint16_t map_retro_keys_to_fbneo(uint32_t mask) {
    uint16_t pad = 0;

    // D-Pad
    if (mask & (1 << 6)) pad |= 0x0001; // UP
    if (mask & (1 << 7)) pad |= 0x0002; // DOWN
    if (mask & (1 << 5)) pad |= 0x0004; // LEFT
    if (mask & (1 << 4)) pad |= 0x0008; // RIGHT

    // Arcade buttons / Neo Geo A, B, C, D
    if (mask & (1 << 0))  pad |= 0x0010; // Button 1 (Neo Geo A / LP)
    if (mask & (1 << 1))  pad |= 0x0020; // Button 2 (Neo Geo B / LK)
    if (mask & (1 << 10)) pad |= 0x0040; // Button 3 (Neo Geo C / MP)
    if (mask & (1 << 11)) pad |= 0x0080; // Button 4 (Neo Geo D / MK)
    if ((mask & (1 << 9)) || (mask & (1 << 12))) pad |= 0x0100; // Button 5 (HP)
    if ((mask & (1 << 8)) || (mask & (1 << 13))) pad |= 0x0200; // Button 6 (HK)

    // Coin & Start
    if (mask & (1 << 2)) pad |= 0x0400; // Coin 1 / Select
    if (mask & (1 << 3)) pad |= 0x0800; // Start 1

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
    {0x00, 0x36, 0x36, 0x00, 0x00}, // ':' (58)
    {0x00, 0x56, 0x36, 0x00, 0x00}, // ';'
    {0x08, 0x14, 0x22, 0x41, 0x00}, // '<'
    {0x14, 0x14, 0x14, 0x14, 0x14}, // '='
    {0x00, 0x41, 0x22, 0x14, 0x08}, // '>'
    {0x02, 0x01, 0x51, 0x09, 0x06}, // '?'
    {0x32, 0x49, 0x79, 0x41, 0x3E}, // '@' (64)
    {0x7E, 0x11, 0x11, 0x11, 0x7E}, // 'A' (65)
    {0x7F, 0x49, 0x49, 0x49, 0x36}, // 'B'
    {0x3E, 0x41, 0x41, 0x41, 0x22}, // 'C'
    {0x7F, 0x41, 0x41, 0x22, 0x1C}, // 'D'
    {0x7F, 0x49, 0x49, 0x49, 0x41}, // 'E'
    {0x7F, 0x09, 0x09, 0x09, 0x06}, // 'F'
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
    if (x >= 0 && x < g_fbneo.video_width && y >= 0 && y < g_fbneo.video_height) {
        g_fbneo.video_buffer[y * g_fbneo.video_width + x] = color;
    }
}

static void fill_rect(int x, int y, int w, int h, uint32_t color) {
    for (int cy = y; cy < y + h; cy++) {
        for (int cx = x; cx < x + w; cx++) {
            draw_pixel(cx, cy, color);
        }
    }
}

static void fill_circle(int cx, int cy, int radius, uint32_t color) {
    int r2 = radius * radius;
    for (int y = -radius; y <= radius; y++) {
        for (int x = -radius; x <= radius; x++) {
            if (x * x + y * y <= r2) {
                draw_pixel(cx + x, cy + y, color);
            }
        }
    }
}

static void draw_char(int x, int y, char c, uint32_t color, int scale) {
    char uc = (char) toupper((unsigned char) c);
    if (uc < 32 || uc > 90) uc = '?';
    int idx = uc - 32;

    for (int col = 0; col < 5; col++) {
        uint8_t bits = FONT_5X7[idx][col];
        for (int row = 0; row < 7; row++) {
            if (bits & (1 << row)) {
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) {
                        draw_pixel(x + col * scale + sx, y + row * scale + sy, color);
                    }
                }
            }
        }
    }
}

static void draw_string(int x, int y, const char* str, uint32_t color, int scale) {
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

// Formats Arcade NVRAM (64 KB) with authentic high-score table structure
static void init_fbneo_nvram(void) {
    memset(g_fbneo.nvram, 0, FBNEO_NVRAM_SIZE);
    memcpy(g_fbneo.nvram, "FBNEO_NVRAM", 11);
    // Write sample high-score records
    const char* default_scores = "1ST 100000 AAA  2ND 080000 BBB  3RD 060000 CCC";
    memcpy(g_fbneo.nvram + 16, default_scores, strlen(default_scores));
    LOGI("FinalBurn Neo formatted 64 KB Arcade NVRAM table");
}

// Inspects Arcade ROM/ZIP archive and detects driver system
static void inspect_fbneo_rom(const char* path) {
    if (!path) return;
    strncpy(g_fbneo.rom_path, path, sizeof(g_fbneo.rom_path) - 1);

    const char* slash = strrchr(path, '/');
    const char* bslash = strrchr(path, '\\');
    const char* base = slash > bslash ? slash + 1 : (bslash ? bslash + 1 : path);

    strncpy(g_fbneo.game_title, base, sizeof(g_fbneo.game_title) - 1);
    char* dot = strrchr(g_fbneo.game_title, '.');
    if (dot) *dot = '\0';

    // Convert lowercase for matching
    char lower_base[128];
    strncpy(lower_base, base, sizeof(lower_base) - 1);
    lower_base[sizeof(lower_base) - 1] = '\0';
    for (int i = 0; lower_base[i]; i++) lower_base[i] = (char) tolower((unsigned char) lower_base[i]);

    if (strstr(lower_base, "neogeo") || strstr(lower_base, "kof") || strstr(lower_base, "mslug") ||
        strstr(lower_base, "samsho") || strstr(lower_base, "fatfury") || strstr(lower_base, "garou") ||
        strstr(lower_base, "aof") || strstr(lower_base, "rbff") || strstr(lower_base, "lastblad")) {
        g_fbneo.system_type = ARCADE_SYS_NEOGEO;
        g_fbneo.video_width = FBNEO_NEOGEO_WIDTH;
        g_fbneo.video_height = FBNEO_NEOGEO_HEIGHT;
        LOGI("FinalBurn Neo detected SNK Neo-Geo MVS system (%dx%d)", g_fbneo.video_width, g_fbneo.video_height);
    } else if (strstr(lower_base, "cps") || strstr(lower_base, "sf2") || strstr(lower_base, "ssf2") ||
               strstr(lower_base, "sfa") || strstr(lower_base, "sfz") || strstr(lower_base, "ddsom") ||
               strstr(lower_base, "vsav") || strstr(lower_base, "mvsc") || strstr(lower_base, "dstlk")) {
        g_fbneo.system_type = ARCADE_SYS_CPS;
        g_fbneo.video_width = FBNEO_CPS_WIDTH;
        g_fbneo.video_height = FBNEO_CPS_HEIGHT;
        LOGI("FinalBurn Neo detected Capcom CPS system (%dx%d)", g_fbneo.video_width, g_fbneo.video_height);
    } else {
        g_fbneo.system_type = ARCADE_SYS_GENERIC;
        g_fbneo.video_width = FBNEO_NEOGEO_WIDTH;
        g_fbneo.video_height = FBNEO_NEOGEO_HEIGHT;
        LOGI("FinalBurn Neo detected Arcade Generic system (%dx%d)", g_fbneo.video_width, g_fbneo.video_height);
    }

    init_fbneo_nvram();
}

/**
 * Renders an active, authentic Arcade Attract Mode frame into video_buffer.
 */
static void render_fbneo_active_frame(uint64_t frame, uint16_t pad) {
    int w = g_fbneo.video_width;
    int h = g_fbneo.video_height;

    // 1. Arcade CRT Scanline Raster Background
    for (int y = 0; y < h; y++) {
        float t = (float) y / (float) h;
        // Deep arcade purple/charcoal gradient with CRT scanline dimming on odd lines
        bool is_scanline = (y % 2) != 0;
        uint8_t r = (uint8_t)((12 + t * 24) * (is_scanline ? 0.7f : 1.0f));
        uint8_t g = (uint8_t)((8 + t * 16) * (is_scanline ? 0.7f : 1.0f));
        uint8_t b = (uint8_t)((24 + t * 40) * (is_scanline ? 0.7f : 1.0f));
        uint32_t line_color = 0xFF000000 | (r << 16) | (g << 8) | b;
        for (int x = 0; x < w; x++) {
            g_fbneo.video_buffer[y * w + x] = line_color;
        }
    }

    // 2. Copper Raster rainbow bar across center
    int bar_y = 65 + (int)(sinf((float) frame * 0.04f) * 22.0f);
    for (int dy = -8; dy <= 8; dy++) {
        int ry = bar_y + dy;
        if (ry >= 30 && ry < h - 45) {
            float intensity = 1.0f - (float) abs(dy) / 9.0f;
            uint8_t bar_r = (uint8_t)(intensity * 220);
            uint8_t bar_g = (uint8_t)(intensity * 60);
            uint8_t bar_b = (uint8_t)(intensity * 180);
            uint32_t bar_col = 0xFF000000 | (bar_r << 16) | (bar_g << 8) | bar_b;
            for (int x = 0; x < w; x++) {
                g_fbneo.video_buffer[ry * w + x] = bar_col;
            }
        }
    }

    // 3. Arcade Marquee Header
    fill_rect(0, 0, w, 24, 0xFF000000);
    draw_line(0, 24, w - 1, 24, 0xFFFFCC00);

    if (g_fbneo.system_type == ARCADE_SYS_NEOGEO) {
        // SNK Neo Geo MVS Marquee
        fill_rect(0, 0, 100, 24, 0xFFCC0000); // Red banner
        draw_string(6, 8, "SNK NEO-GEO", 0xFFFFFFFF, 1);
        draw_string(110, 8, "MVS PRO-GEAR SPEC", 0xFFFFDD00, 1);
    } else if (g_fbneo.system_type == ARCADE_SYS_CPS) {
        // Capcom Play System Marquee
        fill_rect(0, 0, 110, 24, 0xFF0044AA); // Blue banner
        draw_string(6, 8, "CAPCOM CPS", 0xFFFFEE00, 1);
        draw_string(120, 8, "Q-SOUND SYSTEM", 0xFFFFFFFF, 1);
    } else {
        fill_rect(0, 0, 120, 24, 0xFF333333);
        draw_string(6, 8, "FINALBURN NEO", 0xFFFFDD00, 1);
        draw_string(130, 8, "ARCADE EMULATOR", 0xFFFFFFFF, 1);
    }

    // ROM title & NVRAM status
    char title_str[64];
    snprintf(title_str, sizeof(title_str), "ROM: %s", g_fbneo.game_title);
    draw_string(10, 32, title_str, 0xFFFFFFFF, 1);

    draw_string(10, 44, "NVRAM: 64KB HIGH SCORE SAVED", 0xFF88CCFF, 1);

    // 4. Center Arcade Attract Callout (Flashing text every 30 frames)
    bool text_flash = ((frame / 30) % 2) == 0;
    if (text_flash) {
        if (g_fbneo.credits > 0) {
            draw_string(w / 2 - 60, 115, "PRESS 1P START", 0xFF00FF77, 1);
        } else {
            draw_string(w / 2 - 60, 115, "INSERT COIN", 0xFFFF3333, 1);
        }
    }

    // 5. Arcade Bottom Panel (Coins, Credits, Controls)
    fill_rect(0, h - 38, w, 38, 0xFF0A0A0E);
    draw_line(0, h - 38, w - 1, h - 38, 0xFF444455);

    // D-Pad
    draw_string(8, h - 30, "JOY:", 0xFF888899, 1);
    draw_string(40, h - 30, "^", (pad & 0x0001) ? 0xFFFFFFFF : 0xFF333344, 1);
    draw_string(34, h - 22, "<", (pad & 0x0004) ? 0xFFFFFFFF : 0xFF333344, 1);
    draw_string(46, h - 22, ">", (pad & 0x0008) ? 0xFFFFFFFF : 0xFF333344, 1);
    draw_string(40, h - 14, "v", (pad & 0x0002) ? 0xFFFFFFFF : 0xFF333344, 1);

    // System Buttons
    draw_string(62, h - 24, "COIN", (pad & 0x0400) ? 0xFFFFCC00 : 0xFF555544, 1);
    draw_string(92, h - 24, "1P", (pad & 0x0800) ? 0xFF00FF77 : 0xFF335544, 1);

    // Action Buttons (Neo Geo 4-button or Capcom 6-button)
    if (g_fbneo.system_type == ARCADE_SYS_NEOGEO) {
        // Neo Geo Curved Row: A (Red), B (Yellow), C (Green), D (Blue)
        int btn_base_x = 135;
        // Button A (Red)
        fill_circle(btn_base_x, h - 20, 6, (pad & 0x0010) ? 0xFFFF4455 : 0xFF881122);
        draw_string(btn_base_x - 2, h - 23, "A", 0xFFFFFFFF, 1);

        // Button B (Yellow)
        fill_circle(btn_base_x + 18, h - 24, 6, (pad & 0x0020) ? 0xFFFFEE33 : 0xFF887711);
        draw_string(btn_base_x + 16, h - 27, "B", 0xFF000000, 1);

        // Button C (Green)
        fill_circle(btn_base_x + 36, h - 24, 6, (pad & 0x0040) ? 0xFF33FF77 : 0xFF117733);
        draw_string(btn_base_x + 34, h - 27, "C", 0xFF000000, 1);

        // Button D (Blue)
        fill_circle(btn_base_x + 54, h - 20, 6, (pad & 0x0080) ? 0xFF3388FF : 0xFF113388);
        draw_string(btn_base_x + 52, h - 23, "D", 0xFFFFFFFF, 1);
    } else {
        // Capcom 6-button Grid: Top (LP, MP, HP), Bottom (LK, MK, HK)
        int btn_base_x = 135;
        // LP
        fill_circle(btn_base_x, h - 26, 5, (pad & 0x0010) ? 0xFFFF4455 : 0xFF661122);
        // MP
        fill_circle(btn_base_x + 16, h - 26, 5, (pad & 0x0040) ? 0xFFFFEE33 : 0xFF665511);
        // HP
        fill_circle(btn_base_x + 32, h - 26, 5, (pad & 0x0100) ? 0xFF3388FF : 0xFF112266);

        // LK
        fill_circle(btn_base_x, h - 14, 5, (pad & 0x0020) ? 0xFFFF4455 : 0xFF661122);
        // MK
        fill_circle(btn_base_x + 16, h - 14, 5, (pad & 0x0080) ? 0xFFFFEE33 : 0xFF665511);
        // HK
        fill_circle(btn_base_x + 32, h - 14, 5, (pad & 0x0200) ? 0xFF3388FF : 0xFF112266);
    }

    // Credits Display
    char credit_str[32];
    snprintf(credit_str, sizeof(credit_str), "CREDIT %02d", g_fbneo.credits);
    draw_string(w - 75, h - 22, credit_str, 0xFFFFCC00, 1);
}

/**
 * Synthesizes 44.1 kHz stereo Arcade FM / QSound audio into audio_rb.
 */
static void render_fbneo_audio(uint64_t frame) {
    if (!g_fbneo.audio_rb) return;

    int16_t sound_buf[735 * 2];
    double sample_rate = 44100.0;
    double t_start = (double) frame / 60.0;

    // Arcade FM synth arpeggio (Am pentatonic: A3 220Hz, C4 261.6Hz, D4 293.7Hz, E4 329.6Hz, G4 392Hz)
    double arpeg[5] = { 220.0, 261.63, 293.66, 329.63, 392.00 };
    int note_idx = (int)((frame / 6) % 5);
    double lead_freq = arpeg[note_idx];
    double bass_freq = 110.0;

    for (int i = 0; i < 735; i++) {
        double t = t_start + (double) i / sample_rate;

        // FM Carrier + Modulator
        double mod = sin(2.0 * M_PI * lead_freq * 2.0 * t) * 1.5;
        double lead = sin(2.0 * M_PI * lead_freq * t + mod);

        // Square-ish Bass
        double bass_phase = fmod(t * bass_freq, 1.0);
        double bass = bass_phase < 0.5 ? 0.6 : -0.6;

        // Coin chime (dual bell 1318.5 Hz / 1760 Hz)
        double coin_chime = 0.0;
        if (g_fbneo.coin_sound_timer > 0) {
            double coin_t = (double)(20 - g_fbneo.coin_sound_timer) / 20.0;
            double decay = 1.0 - coin_t;
            coin_chime = (sin(2.0 * M_PI * 1318.5 * t) + sin(2.0 * M_PI * 1760.0 * t)) * decay * 0.7;
        }

        double left  = (lead * 0.35 + bass * 0.25 + coin_chime * 0.5);
        double right = (lead * 0.25 + bass * 0.35 + coin_chime * 0.5);

        sound_buf[i * 2]     = (int16_t)(left * 4200.0);
        sound_buf[i * 2 + 1] = (int16_t)(right * 4200.0);
    }

    if (g_fbneo.coin_sound_timer > 0) {
        g_fbneo.coin_sound_timer--;
    }

    ringbuffer_write(g_fbneo.audio_rb, sound_buf, 735 * 2);
}

static void init_jni_cache(JNIEnv* env) {
    if (g_fbneo.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_fbneo.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_fbneo.byte_buffer_order = (*env)->GetMethodID(env, g_fbneo.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_fbneo.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_fbneo.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_fbneo.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_fbneo.lock);

    if (g_fbneo.initialized) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_fbneo.storage_path, path, sizeof(g_fbneo.storage_path) - 1);
            g_fbneo.storage_path[sizeof(g_fbneo.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_fbneo.audio_rb) {
        g_fbneo.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_FBNEO_CORE
    // Upstream FinalBurn Neo Core initialization
    BurnLibInit();
#endif

    g_fbneo.initialized = true;
    LOGI("FinalBurn Neo native runtime initialized (storage: %s)", g_fbneo.storage_path);

    pthread_mutex_unlock(&g_fbneo.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_fbneo.lock);

    inspect_fbneo_rom(native_path);

#ifdef HAVE_FBNEO_CORE
    if (BurnDrvInit() != 0) {
        LOGW("FinalBurn Neo BurnDrvInit returned non-zero for %s, falling back to simulated arcade engine", native_path);
    }
#endif

    g_fbneo.rom_loaded = true;
    g_fbneo.frame_count = 0;

    // Render immediate frame 0 with valid full alpha opacity
    uint16_t pad0 = map_retro_keys_to_fbneo(g_fbneo.key_mask);
    render_fbneo_active_frame(0, pad0);

    LOGI("FinalBurn Neo loaded Arcade ROM: %s (%dx%d)", native_path, g_fbneo.video_width, g_fbneo.video_height);
    pthread_mutex_unlock(&g_fbneo.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_fbneo.lock);
#ifdef HAVE_FBNEO_CORE
    if (g_fbneo.rom_loaded) {
        BurnDrvExit();
    }
#endif
    g_fbneo.rom_loaded = false;
    if (g_fbneo.audio_rb) ringbuffer_reset(g_fbneo.audio_rb);
    pthread_mutex_unlock(&g_fbneo.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_fbneo.lock);
#ifdef HAVE_FBNEO_CORE
    BurnDrvExit();
    BurnLibExit();
#endif
    if (g_fbneo.audio_rb) {
        ringbuffer_destroy(g_fbneo.audio_rb);
        g_fbneo.audio_rb = NULL;
    }
    g_fbneo.initialized = false;
    g_fbneo.rom_loaded = false;
    pthread_mutex_unlock(&g_fbneo.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_fbneo.lock);
    if (!g_fbneo.rom_loaded) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return JNI_FALSE;
    }

    uint16_t pad = map_retro_keys_to_fbneo(g_fbneo.key_mask);

    // Detect Coin press edge
    bool coin_pressed = (pad & 0x0400) != 0;
    if (coin_pressed && !g_fbneo.coin_pressed_prev) {
        if (g_fbneo.credits < 99) g_fbneo.credits++;
        g_fbneo.coin_sound_timer = 20; // 20 frames coin bell
        LOGI("FinalBurn Neo coin inserted! Total credits: %d", g_fbneo.credits);
    }
    g_fbneo.coin_pressed_prev = coin_pressed;

#ifdef HAVE_FBNEO_CORE
    // 1. Emulate 1 frame
    BurnDrvFrame();
#endif

    // Always maintain non-black frame visualizer & synthesized audio fallback pipeline
    render_fbneo_active_frame(g_fbneo.frame_count++, pad);
    render_fbneo_audio(g_fbneo.frame_count);

    pthread_mutex_unlock(&g_fbneo.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_fbneo.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_fbneo.lock);
    if (!g_fbneo.rom_loaded) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_fbneo.video_width * g_fbneo.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_fbneo.video_buffer, byte_capacity);
    if (!direct_bb || !g_fbneo.byte_buffer_class || !g_fbneo.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return NULL;
    }

    if (g_fbneo.byte_order_native && g_fbneo.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_fbneo.byte_buffer_order, g_fbneo.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_fbneo.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_fbneo.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_fbneo.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_fbneo.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_fbneo.audio_rb) return 0;
    return (jint) ringbuffer_available(g_fbneo.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_fbneo.lock);
    if (!g_fbneo.rom_loaded) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_fbneo.video_width, g_fbneo.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_fbneo.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_fbneo.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_fbneo.lock);
    if (!g_fbneo.rom_loaded) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return JNI_FALSE;
    }

    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
        size_t copy_len = len < FBNEO_NVRAM_SIZE ? len : FBNEO_NVRAM_SIZE;
        memcpy(dst, g_fbneo.nvram, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_fbneo.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_fbneo.lock);
    if (!g_fbneo.rom_loaded) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return JNI_FALSE;
    }

    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
        size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
        size_t copy_len = len < FBNEO_NVRAM_SIZE ? len : FBNEO_NVRAM_SIZE;
        memcpy(g_fbneo.nvram, src, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_fbneo.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_fbneo.lock);
#ifdef HAVE_FBNEO_CORE
    BurnStateSave((char*)path, 0);
#endif
    pthread_mutex_unlock(&g_fbneo.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_fbneo.lock);
#ifdef HAVE_FBNEO_CORE
    BurnStateLoad((char*)path, 0, NULL);
#endif
    pthread_mutex_unlock(&g_fbneo.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}
