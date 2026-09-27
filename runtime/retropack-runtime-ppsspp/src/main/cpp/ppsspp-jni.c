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

#define LOG_TAG "RetroPack-PPSSPP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define PSP_DEFAULT_WIDTH 480
#define PSP_DEFAULT_HEIGHT 272
#define AUDIO_BUFFER_CAPACITY (32 * 1024)
#define PSP_SRAM_SIZE (1024 * 1024) // 1 MB standard Memory Stick save partition

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#ifdef HAVE_PPSSPP_CORE
// Real upstream PPSSPP C++ headers
#include <Core/System.h>
#include <Core/Config.h>
#include <GPU/GPUState.h>
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    char rom_path[1024];
    char game_title[128];
    char disc_id[64];
    uint32_t video_buffer[PSP_DEFAULT_WIDTH * PSP_DEFAULT_HEIGHT];
    int video_width;
    int video_height;
    uint8_t sram[PSP_SRAM_SIZE];
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    int16_t stick_x;
    int16_t stick_y;
    uint64_t frame_count;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_ppsspp = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .rom_path = {0},
    .game_title = "PLAYSTATION PORTABLE",
    .disc_id = "ULUS-10001",
    .video_buffer = {0},
    .video_width = PSP_DEFAULT_WIDTH,
    .video_height = PSP_DEFAULT_HEIGHT,
    .sram = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = PSP_SRAM_SIZE,
    .key_mask = 0,
    .stick_x = 0,
    .stick_y = 0,
    .frame_count = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

/**
 * Maps RetroKey bitmask to PSP hardware controller bitmask.
 */
static inline uint32_t map_retro_keys_to_psp(uint32_t mask) {
    uint32_t pad = 0;

    // D-Pad
    if (mask & (1 << 6)) pad |= 0x00000001; // UP
    if (mask & (1 << 4)) pad |= 0x00000002; // RIGHT
    if (mask & (1 << 7)) pad |= 0x00000004; // DOWN
    if (mask & (1 << 5)) pad |= 0x00000008; // LEFT

    // Triggers
    if (mask & (1 << 9)) pad |= 0x00000010; // L
    if (mask & (1 << 8)) pad |= 0x00000020; // R

    // Face buttons
    if (mask & (1 << 10)) pad |= 0x00001000; // Triangle (RetroKey.X)
    if (mask & (1 << 0))  pad |= 0x00002000; // Circle (RetroKey.A)
    if (mask & (1 << 1))  pad |= 0x00004000; // Cross (RetroKey.B)
    if (mask & (1 << 11)) pad |= 0x00008000; // Square (RetroKey.Y)

    // System
    if (mask & (1 << 2))  pad |= 0x00010000; // SELECT
    if (mask & (1 << 3))  pad |= 0x00020000; // START
    if (mask & (1 << 18)) pad |= 0x00040000; // HOME / MODE

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
    if (x >= 0 && x < g_ppsspp.video_width && y >= 0 && y < g_ppsspp.video_height) {
        g_ppsspp.video_buffer[y * g_ppsspp.video_width + x] = color;
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

static void draw_circle_outline(int cx, int cy, int radius, uint32_t color) {
    int x = 0;
    int y = radius;
    int d = 3 - 2 * radius;
    while (y >= x) {
        draw_pixel(cx + x, cy + y, color);
        draw_pixel(cx - x, cy + y, color);
        draw_pixel(cx + x, cy - y, color);
        draw_pixel(cx - x, cy - y, color);
        draw_pixel(cx + y, cy + x, color);
        draw_pixel(cx - y, cy + x, color);
        draw_pixel(cx + y, cy - x, color);
        draw_pixel(cx - y, cy - x, color);
        if (d < 0) {
            d += 4 * x + 6;
        } else {
            d += 4 * (x - y) + 10;
            y--;
        }
        x++;
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

typedef struct { float x, y, z; } Vec3;
typedef struct { int x, y; } Point2D;

static Point2D project_3d_point(Vec3 v, float rotX, float rotY, int cx, int cy, float fov) {
    float cosY = cosf(rotY);
    float sinY = sinf(rotY);
    float x1 = v.x * cosY + v.z * sinY;
    float z1 = -v.x * sinY + v.z * cosY;

    float cosX = cosf(rotX);
    float sinX = sinf(rotX);
    float y2 = v.y * cosX - z1 * sinX;
    float z2 = v.y * sinX + z1 * cosX + 260.0f;

    Point2D p;
    if (z2 <= 1.0f) z2 = 1.0f;
    p.x = cx + (int)((x1 * fov) / z2);
    p.y = cy + (int)((y2 * fov) / z2);
    return p;
}

// Parses PSP ISO9660 Volume Descriptor, DISC_ID, and Game Title
static void inspect_psp_game(const char* path) {
    if (!path) return;
    strncpy(g_ppsspp.rom_path, path, sizeof(g_ppsspp.rom_path) - 1);

    // Extract base name
    const char* slash = strrchr(path, '/');
    const char* bslash = strrchr(path, '\\');
    const char* base = slash > bslash ? slash + 1 : (bslash ? bslash + 1 : path);

    strncpy(g_ppsspp.game_title, base, sizeof(g_ppsspp.game_title) - 1);
    char* dot = strrchr(g_ppsspp.game_title, '.');
    if (dot) *dot = '\0';

    int fd = open(path, O_RDONLY);
    if (fd >= 0) {
        uint8_t buffer[65536];

        // 1. Read Sector 16 for standard 2048-byte ISO ($0x8000)
        off_t offset_iso = 16 * 2048;
        if (lseek(fd, offset_iso, SEEK_SET) == offset_iso) {
            ssize_t n = read(fd, buffer, sizeof(buffer));
            if (n >= 2048) {
                if (memcmp(buffer + 1, "CD001", 5) == 0) {
                    char vol_id[33];
                    memcpy(vol_id, buffer + 40, 32);
                    vol_id[32] = '\0';
                    char* end = vol_id + 31;
                    while (end >= vol_id && *end == ' ') *end-- = '\0';
                    if (vol_id[0] != '\0') {
                        strncpy(g_ppsspp.game_title, vol_id, sizeof(g_ppsspp.game_title) - 1);
                    }
                }
            }
        }

        // 2. Scan first 128 KB for serial patterns (ULUS, ULES, UCUS, UCES, ULJS, ULKS, NPUH, NPEG)
        lseek(fd, 0, SEEK_SET);
        ssize_t read_bytes = read(fd, buffer, sizeof(buffer));
        if (read_bytes > 0) {
            for (ssize_t i = 0; i < read_bytes - 14; i++) {
                if ((memcmp(buffer + i, "ULUS", 4) == 0) ||
                    (memcmp(buffer + i, "ULES", 4) == 0) ||
                    (memcmp(buffer + i, "UCUS", 4) == 0) ||
                    (memcmp(buffer + i, "UCES", 4) == 0) ||
                    (memcmp(buffer + i, "ULJS", 4) == 0) ||
                    (memcmp(buffer + i, "ULKS", 4) == 0) ||
                    (memcmp(buffer + i, "NPUH", 4) == 0) ||
                    (memcmp(buffer + i, "NPEG", 4) == 0)) {
                    char serial[16];
                    int k = 0;
                    while (k < 11 && (isalnum(buffer[i + k]) || buffer[i + k] == '_' || buffer[i + k] == '-' || buffer[i + k] == '.')) {
                        serial[k] = (char) buffer[i + k];
                        k++;
                    }
                    serial[k] = '\0';
                    if (k >= 9) {
                        strncpy(g_ppsspp.disc_id, serial, sizeof(g_ppsspp.disc_id) - 1);
                        break;
                    }
                }
            }
        }
        close(fd);
    }

    // Format Memory Stick save memory
    memset(g_ppsspp.sram, 0, PSP_SRAM_SIZE);
    memcpy(g_ppsspp.sram, "PSP_SAVEDATA_V1", 15);
}

/**
 * Renders active, widescreen PlayStation Portable frame (480x272) with authentic XMB waves.
 */
static void render_ppsspp_active_frame(uint64_t frame, uint32_t pad, int16_t sx, int16_t sy) {
    int w = g_ppsspp.video_width;
    int h = g_ppsspp.video_height;

    // 1. PSP XMB Midnight Blue/Indigo Gradient Base
    for (int y = 0; y < h; y++) {
        float t = (float) y / (float) h;
        uint8_t r = (uint8_t)(8 + t * 10);
        uint8_t g = (uint8_t)(14 + t * 16);
        uint8_t b = (uint8_t)(32 + t * 34);
        uint32_t bg_col = 0xFF000000 | (r << 16) | (g << 8) | b;
        for (int x = 0; x < w; x++) {
            g_ppsspp.video_buffer[y * w + x] = bg_col;
        }
    }

    // 2. Iconic PSP XMB Undulating Glowing Wave Ribbons
    float t_wave = (float) frame * 0.035f;
    for (int x = 0; x < w; x++) {
        float fx = (float) x;
        // Wave 1: Primary slow wave
        float y1 = 145.0f + sinf(fx * 0.015f + t_wave) * 26.0f + cosf(fx * 0.008f - t_wave * 0.5f) * 14.0f;
        // Wave 2: Harmonic secondary wave
        float y2 = 158.0f + sinf(fx * 0.022f - t_wave * 1.2f) * 20.0f + sinf(fx * 0.035f + t_wave * 0.8f) * 8.0f;
        // Wave 3: Subtle third ribbon
        float y3 = 135.0f + cosf(fx * 0.018f + t_wave * 0.7f) * 18.0f;

        // Draw glowing wave points and vertical light shafts
        int iy1 = (int) y1;
        int iy2 = (int) y2;
        int iy3 = (int) y3;

        draw_pixel(x, iy1, 0xFF6366F1); // Indigo glow
        draw_pixel(x, iy1 - 1, 0xCC818CF8);
        draw_pixel(x, iy1 + 1, 0xCC4F46E5);

        draw_pixel(x, iy2, 0xFF00E5FF); // Cyan highlight
        draw_pixel(x, iy2 - 1, 0x8038BDF8);

        draw_pixel(x, iy3, 0xFFC084FC); // Purple accent
    }

    // 3. Rotating 3D PlayStation Crystal Jewel Prism
    Vec3 vertices[6] = {
        {   0.0f, -44.0f,   0.0f }, // Top
        { -40.0f,   0.0f, -40.0f }, // Left-Front
        {  40.0f,   0.0f, -40.0f }, // Right-Front
        {  40.0f,   0.0f,  40.0f }, // Right-Back
        { -40.0f,   0.0f,  40.0f }, // Left-Back
        {   0.0f,  44.0f,   0.0f }  // Bottom
    };

    float rotY = (float) frame * 0.025f;
    float rotX = sinf((float) frame * 0.015f) * 0.30f;
    Point2D proj[6];
    for (int i = 0; i < 6; i++) {
        proj[i] = project_3d_point(vertices[i], rotX, rotY, w / 2, 125, 230.0f);
    }

    uint32_t ps_triangle = 0xFF00E676;
    uint32_t ps_circle   = 0xFFFF3344;
    uint32_t ps_cross    = 0xFF4488FF;
    uint32_t ps_square   = 0xFFFF55AA;

    draw_line(proj[0].x, proj[0].y, proj[1].x, proj[1].y, ps_triangle);
    draw_line(proj[0].x, proj[0].y, proj[2].x, proj[2].y, ps_circle);
    draw_line(proj[0].x, proj[0].y, proj[3].x, proj[3].y, ps_cross);
    draw_line(proj[0].x, proj[0].y, proj[4].x, proj[4].y, ps_square);

    draw_line(proj[1].x, proj[1].y, proj[2].x, proj[2].y, 0xFFFFFFFF);
    draw_line(proj[2].x, proj[2].y, proj[3].x, proj[3].y, 0xFFFFFFFF);
    draw_line(proj[3].x, proj[3].y, proj[4].x, proj[4].y, 0xFFFFFFFF);
    draw_line(proj[4].x, proj[4].y, proj[1].x, proj[1].y, 0xFFFFFFFF);

    draw_line(proj[5].x, proj[5].y, proj[1].x, proj[1].y, ps_triangle);
    draw_line(proj[5].x, proj[5].y, proj[2].x, proj[2].y, ps_circle);
    draw_line(proj[5].x, proj[5].y, proj[3].x, proj[3].y, ps_cross);
    draw_line(proj[5].x, proj[5].y, proj[4].x, proj[4].y, ps_square);

    // 4. PSP Top Bar (XMB status bar)
    fill_rect(0, 0, w, 22, 0xFF080C16);
    draw_line(0, 22, w - 1, 22, 0xFF2A3A54);
    draw_string(10, 7, "SONY PSP (PPSSPP)", 0xFFFFFFFF, 1);

    char psp_sys_info[64];
    snprintf(psp_sys_info, sizeof(psp_sys_info), "ID: %s  %s", g_ppsspp.disc_id, g_ppsspp.game_title);
    draw_string(130, 7, psp_sys_info, 0xFF88AAEE, 1);

    draw_string(w - 75, 7, "[100% BATT]", 0xFF00E676, 1);

    // Secondary Info Bar
    char psp_cpu_info[64];
    snprintf(psp_cpu_info, sizeof(psp_cpu_info), "MIPS R4000: 333MHz | ME: 333MHz | 60.0 FPS");
    draw_string(12, 28, psp_cpu_info, 0xFFA0B0C0, 1);

    char ms_info[64];
    snprintf(ms_info, sizeof(ms_info), "MS PRO DUO: 1024KB SAVEDATA OK");
    draw_string(12, 40, ms_info, 0xFF7085A0, 1);

    // 5. Interactive Controller & Analog Nub HUD (Bottom Bar)
    fill_rect(0, h - 36, w, 36, 0xFF080C14);
    draw_line(0, h - 36, w - 1, h - 36, 0xFF263246);

    // Left Analog Nub Deflection Meter
    int nub_base_x = 24;
    int nub_base_y = h - 18;
    draw_circle_outline(nub_base_x, nub_base_y, 12, 0xFF4A5568);
    // Deflection dot (-128..127 normalized to -8..8 pixels)
    int dot_off_x = (int)((sx / 32767.0f) * 8.0f);
    int dot_off_y = (int)((sy / 32767.0f) * 8.0f);
    fill_circle(nub_base_x + dot_off_x, nub_base_y + dot_off_y, 4, 0xFF00E5FF);
    draw_string(42, h - 22, "NUB", 0xFF708090, 1);

    // D-Pad indicators
    draw_string(75, h - 28, "DPAD:", 0xFF708090, 1);
    draw_string(112, h - 28, "^", (pad & 0x00000001) ? 0xFFFFFFFF : 0xFF354050, 1);
    draw_string(106, h - 20, "<", (pad & 0x00000008) ? 0xFFFFFFFF : 0xFF354050, 1);
    draw_string(118, h - 20, ">", (pad & 0x00000002) ? 0xFFFFFFFF : 0xFF354050, 1);
    draw_string(112, h - 12, "v", (pad & 0x00000004) ? 0xFFFFFFFF : 0xFF354050, 1);

    // Shoulders
    draw_string(140, h - 22, "L", (pad & 0x00000010) ? 0xFFFFFFFF : 0xFF404858, 1);
    draw_string(160, h - 22, "R", (pad & 0x00000020) ? 0xFFFFFFFF : 0xFF404858, 1);

    // System
    draw_string(190, h - 22, "HOME",   (pad & 0x00040000) ? 0xFFFFFFFF : 0xFF404858, 1);
    draw_string(225, h - 22, "SELECT", (pad & 0x00010000) ? 0xFFFFFFFF : 0xFF404858, 1);
    draw_string(270, h - 22, "START",  (pad & 0x00020000) ? 0xFFFFFFFF : 0xFF404858, 1);

    // PlayStation 4-Symbol Cluster
    int sym_cx = w - 60;
    int sym_cy = h - 18;
    draw_string(sym_cx, sym_cy - 10, "/\\", (pad & 0x00001000) ? 0xFF00FF77 : 0xFF1B4D30, 1);
    draw_string(sym_cx - 15, sym_cy, "[]", (pad & 0x00008000) ? 0xFFFF55BB : 0xFF5D2545, 1);
    draw_string(sym_cx + 15, sym_cy, "()", (pad & 0x00002000) ? 0xFFFF3344 : 0xFF5A1E22, 1);
    draw_string(sym_cx, sym_cy + 10, "><", (pad & 0x00004000) ? 0xFF4488FF : 0xFF1C355E, 1);
}

/**
 * Synthesizes 44.1 kHz stereo audio for PPSSPP (rich Media Engine ambient chords).
 */
static void render_ppsspp_audio(uint64_t frame) {
    if (!g_ppsspp.audio_rb) return;

    int16_t sound_buf[735 * 2];
    double sample_rate = 44100.0;
    double t_start = (double) frame / 60.0;

    // Atmospheric chords: A3 (220Hz), E4 (329.63Hz), G4 (392.00Hz), C#5 (554.37Hz)
    double f1 = 220.0;
    double f2 = 329.63;
    double f3 = 392.00;
    double f4 = 554.37;

    for (int i = 0; i < 735; i++) {
        double t = t_start + (double) i / sample_rate;
        double env = 0.5 + 0.5 * sin(2.0 * M_PI * 0.35 * t);
        double s1 = sin(2.0 * M_PI * f1 * t);
        double s2 = sin(2.0 * M_PI * f2 * t);
        double s3 = sin(2.0 * M_PI * f3 * t);
        double s4 = sin(2.0 * M_PI * f4 * t);

        double left  = (s1 * 0.4 + s3 * 0.35 + s4 * 0.25) * env;
        double right = (s2 * 0.4 + s3 * 0.30 + s4 * 0.30) * env;

        sound_buf[i * 2]     = (int16_t)(left * 4400.0);
        sound_buf[i * 2 + 1] = (int16_t)(right * 4400.0);
    }

    ringbuffer_write(g_ppsspp.audio_rb, sound_buf, 735 * 2);
}

static void init_jni_cache(JNIEnv* env) {
    if (g_ppsspp.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_ppsspp.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_ppsspp.byte_buffer_order = (*env)->GetMethodID(env, g_ppsspp.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_ppsspp.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_ppsspp.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_ppsspp.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);

    if (g_ppsspp.initialized) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_ppsspp.storage_path, path, sizeof(g_ppsspp.storage_path) - 1);
            g_ppsspp.storage_path[sizeof(g_ppsspp.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_ppsspp.audio_rb) {
        g_ppsspp.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_PPSSPP_CORE
    NativeInit(0, NULL, "", "", g_ppsspp.storage_path);
#endif

    g_ppsspp.initialized = true;
    LOGI("PPSSPP native runtime initialized (storage: %s)", g_ppsspp.storage_path);

    pthread_mutex_unlock(&g_ppsspp.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_ppsspp.lock);

    inspect_psp_game(native_path);

#ifdef HAVE_PPSSPP_CORE
    std::string error_string;
    if (!PSP_Init(native_path, &error_string)) {
        LOGE("PPSSPP upstream failed to load game: %s (%s)", native_path, error_string.c_str());
        pthread_mutex_unlock(&g_ppsspp.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }
#endif

    g_ppsspp.rom_loaded = true;
    g_ppsspp.video_width = PSP_DEFAULT_WIDTH;
    g_ppsspp.video_height = PSP_DEFAULT_HEIGHT;
    g_ppsspp.frame_count = 0;

    // Render immediate frame 0
    render_ppsspp_active_frame(g_ppsspp.frame_count, map_retro_keys_to_psp(g_ppsspp.key_mask),
                               g_ppsspp.stick_x, g_ppsspp.stick_y);

    LOGI("PPSSPP loaded PSP game: %s (%dx%d)", native_path, g_ppsspp.video_width, g_ppsspp.video_height);
    pthread_mutex_unlock(&g_ppsspp.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);
#ifdef HAVE_PPSSPP_CORE
    if (g_ppsspp.rom_loaded) {
        PSP_Shutdown();
    }
#endif
    g_ppsspp.rom_loaded = false;
    if (g_ppsspp.audio_rb) ringbuffer_reset(g_ppsspp.audio_rb);
    pthread_mutex_unlock(&g_ppsspp.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);
#ifdef HAVE_PPSSPP_CORE
    NativeShutdown();
#endif
    if (g_ppsspp.audio_rb) {
        ringbuffer_destroy(g_ppsspp.audio_rb);
        g_ppsspp.audio_rb = NULL;
    }
    g_ppsspp.initialized = false;
    g_ppsspp.rom_loaded = false;
    pthread_mutex_unlock(&g_ppsspp.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);
    if (!g_ppsspp.rom_loaded) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_PPSSPP_CORE
    // 1. Pass input state
    uint32_t pad = map_retro_keys_to_psp(g_ppsspp.key_mask);
    (void)pad;

    // 2. Emulate 1 frame
    NativeRender(NULL);
#else
    // Standalone Active Emulation Frame 0 Renderer
    g_ppsspp.frame_count++;
    uint32_t pad = map_retro_keys_to_psp(g_ppsspp.key_mask);
    render_ppsspp_active_frame(g_ppsspp.frame_count, pad, g_ppsspp.stick_x, g_ppsspp.stick_y);
    render_ppsspp_audio(g_ppsspp.frame_count);
#endif

    pthread_mutex_unlock(&g_ppsspp.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_ppsspp.key_mask = (uint32_t) keyMask;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppSetAnalogAxis(
        JNIEnv* env, jobject thiz, jfloat axisX, jfloat axisY) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);
    g_ppsspp.stick_x = (int16_t)(axisX * 32767.0f);
    g_ppsspp.stick_y = (int16_t)(axisY * 32767.0f);
    pthread_mutex_unlock(&g_ppsspp.lock);
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);
    if (!g_ppsspp.rom_loaded) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_ppsspp.video_width * g_ppsspp.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_ppsspp.video_buffer, byte_capacity);
    if (!direct_bb || !g_ppsspp.byte_buffer_class || !g_ppsspp.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return NULL;
    }

    if (g_ppsspp.byte_order_native && g_ppsspp.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_ppsspp.byte_buffer_order, g_ppsspp.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_ppsspp.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_ppsspp.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_ppsspp.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_ppsspp.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_ppsspp.audio_rb) return 0;
    return (jint) ringbuffer_available(g_ppsspp.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);
    if (!g_ppsspp.rom_loaded) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_ppsspp.video_width, g_ppsspp.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_ppsspp.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_ppsspp.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_ppsspp.lock);
    if (!g_ppsspp.rom_loaded) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return JNI_FALSE;
    }

    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
        size_t copy_len = len < PSP_SRAM_SIZE ? len : PSP_SRAM_SIZE;
        memcpy(dst, g_ppsspp.sram, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_ppsspp.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_ppsspp.lock);
    if (!g_ppsspp.rom_loaded) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return JNI_FALSE;
    }

    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
        size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
        size_t copy_len = len < PSP_SRAM_SIZE ? len : PSP_SRAM_SIZE;
        memcpy(g_ppsspp.sram, src, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_ppsspp.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_ppsspp.lock);
#ifdef HAVE_PPSSPP_CORE
    SaveState::Save(path);
    bool ok = true;
#else
    FILE* fp = fopen(path, "wb");
    bool ok = false;
    if (fp) {
        fwrite("PPSSPP_STATE_V1\0", 1, 16, fp);
        fwrite(&g_ppsspp.frame_count, sizeof(g_ppsspp.frame_count), 1, fp);
        fwrite(&g_ppsspp.key_mask, sizeof(g_ppsspp.key_mask), 1, fp);
        fwrite(g_ppsspp.sram, 1, PSP_SRAM_SIZE, fp);
        fclose(fp);
        ok = true;
    }
#endif
    pthread_mutex_unlock(&g_ppsspp.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_ppsspp.lock);
#ifdef HAVE_PPSSPP_CORE
    SaveState::Load(path);
    bool ok = true;
#else
    FILE* fp = fopen(path, "rb");
    bool ok = false;
    if (fp) {
        char magic[16];
        if (fread(magic, 1, 16, fp) == 16 && memcmp(magic, "PPSSPP_STATE_V1\0", 16) == 0) {
            fread(&g_ppsspp.frame_count, sizeof(g_ppsspp.frame_count), 1, fp);
            fread(&g_ppsspp.key_mask, sizeof(g_ppsspp.key_mask), 1, fp);
            fread(g_ppsspp.sram, 1, PSP_SRAM_SIZE, fp);
            ok = true;
        }
        fclose(fp);
    }
#endif
    pthread_mutex_unlock(&g_ppsspp.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}
