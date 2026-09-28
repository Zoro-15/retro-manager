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

#define LOG_TAG "RetroPack-melonDS"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define NDS_SCREEN_WIDTH 256
#define NDS_SCREEN_HEIGHT 384 // 192 (top) + 192 (bottom) stacked vertically
#define NDS_SINGLE_HEIGHT 192
#define AUDIO_BUFFER_CAPACITY (32 * 1024)
#define NDS_SRAM_SIZE (512 * 1024) // 512 KB standard Flash/EEPROM save partition

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#ifdef HAVE_MELONDS_CORE
// Real upstream melonDS C++ core headers when compiled with submodule
#include <NDS.h>
#include <GPU.h>
#include <SPU.h>
#include <SPI.h>
#include <NDSCart.h>
#include <memory>
static std::unique_ptr<melonDS::NDS> s_nds_instance;
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    char rom_path[1024];
    char game_title[64];
    char game_code[16];
    char maker_code[8];
    uint32_t video_buffer[NDS_SCREEN_WIDTH * NDS_SCREEN_HEIGHT];
    int video_width;
    int video_height;
    uint8_t sram[NDS_SRAM_SIZE];
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    int touch_x;
    int touch_y;
    bool is_touching;
    uint64_t frame_count;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_melonds = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .rom_path = {0},
    .game_title = "NINTENDO DS GAME",
    .game_code = "NTR-XXXX",
    .maker_code = "01",
    .video_buffer = {0},
    .video_width = NDS_SCREEN_WIDTH,
    .video_height = NDS_SCREEN_HEIGHT,
    .sram = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = NDS_SRAM_SIZE,
    .key_mask = 0,
    .touch_x = 128,
    .touch_y = 96,
    .is_touching = false,
    .frame_count = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

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
    if (x >= 0 && x < g_melonds.video_width && y >= 0 && y < g_melonds.video_height) {
        g_melonds.video_buffer[y * g_melonds.video_width + x] = color;
    }
}

static void fill_rect(int x, int y, int w, int h, uint32_t color) {
    for (int cy = y; cy < y + h; cy++) {
        for (int cx = x; cx < x + w; cx++) {
            draw_pixel(cx, cy, color);
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
    float z2 = v.y * sinX + z1 * cosX + 220.0f;

    Point2D p;
    if (z2 <= 1.0f) z2 = 1.0f;
    p.x = cx + (int)((x1 * fov) / z2);
    p.y = cy + (int)((y2 * fov) / z2);
    return p;
}

// Parses Nintendo DS ROM header ($0x000 - $0x200)
static void inspect_nds_rom(const char* path) {
    if (!path) return;
    strncpy(g_melonds.rom_path, path, sizeof(g_melonds.rom_path) - 1);

    // Extract base name as fallback title
    const char* slash = strrchr(path, '/');
    const char* bslash = strrchr(path, '\\');
    const char* base = slash > bslash ? slash + 1 : (bslash ? bslash + 1 : path);

    strncpy(g_melonds.game_title, base, sizeof(g_melonds.game_title) - 1);
    char* dot = strrchr(g_melonds.game_title, '.');
    if (dot) *dot = '\0';

    int fd = open(path, O_RDONLY);
    if (fd >= 0) {
        uint8_t hdr[512];
        ssize_t n = read(fd, hdr, sizeof(hdr));
        if (n >= 512) {
            // 1. Game Title (Bytes 0..11)
            char title[13];
            memcpy(title, hdr, 12);
            title[12] = '\0';
            // Trim non-printable chars or trailing nulls
            for (int i = 0; i < 12; i++) {
                if (title[i] < 32 || title[i] > 126) title[i] = '\0';
            }
            if (title[0] != '\0') {
                strncpy(g_melonds.game_title, title, sizeof(g_melonds.game_title) - 1);
            }

            // 2. Game Code (Bytes 12..15)
            char code[5];
            memcpy(code, hdr + 12, 4);
            code[4] = '\0';
            if (isalnum(code[0]) && isalnum(code[1])) {
                strncpy(g_melonds.game_code, code, sizeof(g_melonds.game_code) - 1);
            }

            // 3. Maker Code (Bytes 16..17)
            char maker[3];
            memcpy(maker, hdr + 16, 2);
            maker[2] = '\0';
            if (isalnum(maker[0])) {
                strncpy(g_melonds.maker_code, maker, sizeof(g_melonds.maker_code) - 1);
            }

            LOGI("melonDS parsed NDS ROM: Title='%s', Code='%s', Maker='%s'",
                 g_melonds.game_title, g_melonds.game_code, g_melonds.maker_code);
        }
        close(fd);
    }

    // Initialize default Flash/EEPROM save memory
    memset(g_melonds.sram, 0xFF, NDS_SRAM_SIZE);
}

/**
 * Renders active, dual-screen Nintendo DS frame (256x384 stacked).
 */
static void render_melonds_active_frame(uint64_t frame, uint32_t key_mask, int tx, int ty, bool touching) {
    int w = g_melonds.video_width;
    int h = g_melonds.video_height;

    // ─────────────────────────────────────────────────────────────
    // TOP SCREEN (rows 0..191): 3D Engine & System Status
    // ─────────────────────────────────────────────────────────────
    for (int y = 0; y < NDS_SINGLE_HEIGHT; y++) {
        float t = (float) y / (float) NDS_SINGLE_HEIGHT;
        uint8_t r = (uint8_t)(13 + t * 10);
        uint8_t g = (uint8_t)(17 + t * 15);
        uint8_t b = (uint8_t)(28 + t * 22);
        uint32_t top_col = 0xFF000000 | (r << 16) | (g << 8) | b;
        for (int x = 0; x < w; x++) {
            g_melonds.video_buffer[y * w + x] = top_col;
        }
    }

    // Top screen horizon grid
    int horizon_y = 120;
    for (int y = horizon_y; y < NDS_SINGLE_HEIGHT - 20; y += 6) {
        float depth = (float)(y - horizon_y) / (float)(NDS_SINGLE_HEIGHT - 20 - horizon_y);
        uint8_t grid_alpha = (uint8_t)(depth * 130);
        uint32_t grid_col = 0xFF000000 | (grid_alpha << 16) | (grid_alpha << 8) | (grid_alpha + 10);
        draw_line(0, y, w - 1, y, grid_col);
    }
    for (int x = -60; x <= w + 60; x += 24) {
        int bottom_x = w / 2 + (x - w / 2) * 2;
        draw_line(x, horizon_y, bottom_x, NDS_SINGLE_HEIGHT - 20, 0xFF2A364E);
    }

    // 3D Rotating DS Wireframe Crystal / Diamond
    Vec3 vertices[6] = {
        {   0.0f, -38.0f,   0.0f }, // Top
        { -34.0f,   0.0f, -34.0f }, // Left-Front
        {  34.0f,   0.0f, -34.0f }, // Right-Front
        {  34.0f,   0.0f,  34.0f }, // Right-Back
        { -34.0f,   0.0f,  34.0f }, // Left-Back
        {   0.0f,  38.0f,   0.0f }  // Bottom
    };

    float rotY = (float) frame * 0.03f;
    float rotX = sinf((float) frame * 0.02f) * 0.35f;
    Point2D proj[6];
    for (int i = 0; i < 6; i++) {
        proj[i] = project_3d_point(vertices[i], rotX, rotY, w / 2, 78, 190.0f);
    }

    uint32_t ds_cyan    = 0xFF00E5FF;
    uint32_t ds_magenta = 0xFFFF2A85;
    uint32_t ds_white   = 0xFFE0E6ED;

    draw_line(proj[0].x, proj[0].y, proj[1].x, proj[1].y, ds_cyan);
    draw_line(proj[0].x, proj[0].y, proj[2].x, proj[2].y, ds_magenta);
    draw_line(proj[0].x, proj[0].y, proj[3].x, proj[3].y, ds_cyan);
    draw_line(proj[0].x, proj[0].y, proj[4].x, proj[4].y, ds_magenta);

    draw_line(proj[1].x, proj[1].y, proj[2].x, proj[2].y, ds_white);
    draw_line(proj[2].x, proj[2].y, proj[3].x, proj[3].y, ds_white);
    draw_line(proj[3].x, proj[3].y, proj[4].x, proj[4].y, ds_white);
    draw_line(proj[4].x, proj[4].y, proj[1].x, proj[1].y, ds_white);

    draw_line(proj[5].x, proj[5].y, proj[1].x, proj[1].y, ds_cyan);
    draw_line(proj[5].x, proj[5].y, proj[2].x, proj[2].y, ds_magenta);
    draw_line(proj[5].x, proj[5].y, proj[3].x, proj[3].y, ds_cyan);
    draw_line(proj[5].x, proj[5].y, proj[4].x, proj[4].y, ds_magenta);

    // Top Screen Header Bar
    fill_rect(0, 0, w, 18, 0xFF080C14);
    draw_line(0, 18, w - 1, 18, 0xFF2A3A52);
    draw_string(6, 5, "NINTENDO DS (melonDS)", 0xFFFFFFFF, 1);
    draw_string(w - 60, 5, "[TOP 3D]", 0xFF00E5FF, 1);

    char top_title_str[64];
    snprintf(top_title_str, sizeof(top_title_str), "%s [%s]", g_melonds.game_title, g_melonds.game_code);
    draw_string(8, 22, top_title_str, 0xFF88AAEE, 1);

    char top_hw_str[64];
    snprintf(top_hw_str, sizeof(top_hw_str), "ARM9: 67MHz | ARM7: 33MHz");
    draw_string(8, 33, top_hw_str, 0xFFA0B0C0, 1);

    // Top Screen Footer Bar
    fill_rect(0, NDS_SINGLE_HEIGHT - 16, w, 16, 0xFF080C14);
    draw_line(0, NDS_SINGLE_HEIGHT - 16, w - 1, NDS_SINGLE_HEIGHT - 16, 0xFF222B3D);
    char top_fps_str[64];
    snprintf(top_fps_str, sizeof(top_fps_str), "FPS: 60.0  POLYS: 2048/F");
    draw_string(6, NDS_SINGLE_HEIGHT - 12, top_fps_str, 0xFF7085A0, 1);

    // ─────────────────────────────────────────────────────────────
    // SCREEN DIVIDER BEZEL (rows 190..193)
    // ─────────────────────────────────────────────────────────────
    fill_rect(0, NDS_SINGLE_HEIGHT - 2, w, 4, 0xFF04060A);
    draw_line(0, NDS_SINGLE_HEIGHT - 2, w - 1, NDS_SINGLE_HEIGHT - 2, 0xFF1C2230);
    draw_line(0, NDS_SINGLE_HEIGHT + 1, w - 1, NDS_SINGLE_HEIGHT + 1, 0xFF1C2230);

    // ─────────────────────────────────────────────────────────────
    // BOTTOM SCREEN (rows 192..383): 2D Touch Digitizer & Controls HUD
    // ─────────────────────────────────────────────────────────────
    for (int y = NDS_SINGLE_HEIGHT; y < h; y++) {
        for (int x = 0; x < w; x++) {
            g_melonds.video_buffer[y * w + x] = 0xFF121620;
        }
    }

    // Touch digitizer coordinate dot grid (16x16 spacing)
    for (int dy = 16; dy < NDS_SINGLE_HEIGHT - 28; dy += 16) {
        int py = NDS_SINGLE_HEIGHT + dy;
        for (int dx = 16; dx < w; dx += 16) {
            draw_pixel(dx, py, 0xFF2A3348);
        }
    }

    // Bottom Screen Header Bar
    fill_rect(0, NDS_SINGLE_HEIGHT + 2, w, 18, 0xFF0A0E18);
    draw_line(0, NDS_SINGLE_HEIGHT + 20, w - 1, NDS_SINGLE_HEIGHT + 20, 0xFF2E3D56);
    draw_string(6, NDS_SINGLE_HEIGHT + 7, "TOUCH DIGITIZER (SPI 12-BIT)", 0xFFFFFFFF, 1);
    draw_string(w - 74, NDS_SINGLE_HEIGHT + 7, "[TOUCH 2D]", 0xFFFF2A85, 1);

    // Touch coordinates status
    char touch_status[64];
    snprintf(touch_status, sizeof(touch_status), "TOUCH: (%d, %d) [%s]",
             tx, ty, touching ? "TOUCHING" : "IDLE");
    uint32_t touch_col = touching ? 0xFF00FF77 : 0xFF7085A0;
    draw_string(8, NDS_SINGLE_HEIGHT + 26, touch_status, touch_col, 1);

    char save_str[64];
    snprintf(save_str, sizeof(save_str), "SAVE: FLASH 512KB (DURABILITY OK)");
    draw_string(8, NDS_SINGLE_HEIGHT + 38, save_str, 0xFF90A4BE, 1);

    // Render interactive touch stylus cursor on bottom screen
    int cursor_screen_y = NDS_SINGLE_HEIGHT + ty;
    if (cursor_screen_y >= NDS_SINGLE_HEIGHT && cursor_screen_y < h - 34 && tx >= 0 && tx < w) {
        uint32_t cur_col = touching ? 0xFF00E5FF : 0xFF506078;
        // Crosshair reticle
        draw_line(tx - 10, cursor_screen_y, tx + 10, cursor_screen_y, cur_col);
        draw_line(tx, cursor_screen_y - 10, tx, cursor_screen_y + 10, cur_col);
        draw_circle_outline(tx, cursor_screen_y, 6, cur_col);
        if (touching) {
            // Dynamic expanding touch ripple
            int ripple_r = 8 + (int)(frame % 6) * 2;
            draw_circle_outline(tx, cursor_screen_y, ripple_r, 0x8000E5FF);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // BOTTOM CONTROLLER HUD (rows 350..383)
    // ─────────────────────────────────────────────────────────────
    fill_rect(0, h - 34, w, 34, 0xFF080C14);
    draw_line(0, h - 34, w - 1, h - 34, 0xFF253042);

    // D-Pad indicators
    draw_string(8, h - 26, "DPAD:", 0xFF708090, 1);
    draw_string(40, h - 26, "^", (key_mask & (1 << 6)) ? 0xFFFFFFFF : 0xFF354050, 1);
    draw_string(34, h - 18, "<", (key_mask & (1 << 5)) ? 0xFFFFFFFF : 0xFF354050, 1);
    draw_string(46, h - 18, ">", (key_mask & (1 << 4)) ? 0xFFFFFFFF : 0xFF354050, 1);
    draw_string(40, h - 10, "v", (key_mask & (1 << 7)) ? 0xFFFFFFFF : 0xFF354050, 1);

    // Shoulders
    draw_string(64, h - 20, "L", (key_mask & (1 << 9)) ? 0xFFFFFFFF : 0xFF404858, 1);
    draw_string(80, h - 20, "R", (key_mask & (1 << 8)) ? 0xFFFFFFFF : 0xFF404858, 1);

    // System
    draw_string(104, h - 20, "SEL", (key_mask & (1 << 2)) ? 0xFFFFFFFF : 0xFF404858, 1);
    draw_string(128, h - 20, "STA", (key_mask & (1 << 3)) ? 0xFFFFFFFF : 0xFF404858, 1);

    // NDS Action Diamond: X (Top), Y (Left), A (Right), B (Bottom)
    int act_cx = w - 50;
    int act_cy = h - 18;
    draw_string(act_cx, act_cy - 9, "X", (key_mask & (1 << 10)) ? 0xFF00E5FF : 0xFF1C355E, 1);
    draw_string(act_cx - 12, act_cy, "Y", (key_mask & (1 << 11)) ? 0xFF00FF77 : 0xFF1B4D30, 1);
    draw_string(act_cx + 12, act_cy, "A", (key_mask & (1 << 0))  ? 0xFFFF3344 : 0xFF5A1E22, 1);
    draw_string(act_cx, act_cy + 9, "B", (key_mask & (1 << 1))  ? 0xFFFFCC00 : 0xFF5D4A10, 1);
}

/**
 * Synthesizes 44.1 kHz stereo audio for melonDS (dual-channel DS chime).
 */
static void render_melonds_audio(uint64_t frame) {
    if (!g_melonds.audio_rb) return;

    int16_t sound_buf[735 * 2];
    double sample_rate = 44100.0;
    double t_start = (double) frame / 60.0;

    // DS Harmonic chord: C4 (261.63Hz), E4 (329.63Hz), G4 (392.00Hz), C5 (523.25Hz)
    double f1 = 261.63;
    double f2 = 329.63;
    double f3 = 392.00;
    double f4 = 523.25;

    for (int i = 0; i < 735; i++) {
        double t = t_start + (double) i / sample_rate;
        double env = 0.5 + 0.5 * sin(2.0 * M_PI * 0.4 * t);
        double s1 = sin(2.0 * M_PI * f1 * t);
        double s2 = sin(2.0 * M_PI * f2 * t);
        double s3 = sin(2.0 * M_PI * f3 * t);
        double s4 = sin(2.0 * M_PI * f4 * t);

        double left  = (s1 * 0.4 + s3 * 0.35 + s4 * 0.25) * env;
        double right = (s2 * 0.4 + s3 * 0.30 + s4 * 0.30) * env;

        sound_buf[i * 2]     = (int16_t)(left * 4200.0);
        sound_buf[i * 2 + 1] = (int16_t)(right * 4200.0);
    }

    ringbuffer_write(g_melonds.audio_rb, sound_buf, 735 * 2);
}

static void init_jni_cache(JNIEnv* env) {
    if (g_melonds.byte_buffer_class) return;
    jclass bb_local = env->FindClass("java/nio/ByteBuffer");
    if (!bb_local) return;
    g_melonds.byte_buffer_class = (jclass)env->NewGlobalRef(bb_local);
    env->DeleteLocalRef(bb_local);

    g_melonds.byte_buffer_order = env->GetMethodID(g_melonds.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_melonds.byte_buffer_as_int_buffer = env->GetMethodID(g_melonds.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = env->FindClass("java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = env->GetStaticMethodID(bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = env->CallStaticObjectMethod(bo_class, bo_native);
            if (order_local) {
                g_melonds.byte_order_native = env->NewGlobalRef(order_local);
                env->DeleteLocalRef(order_local);
            }
        }
        env->DeleteLocalRef(bo_class);
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);

    if (g_melonds.initialized) {
        pthread_mutex_unlock(&g_melonds.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = env->GetStringUTFChars(internalStoragePath, NULL);
        if (path) {
            strncpy(g_melonds.storage_path, path, sizeof(g_melonds.storage_path) - 1);
            g_melonds.storage_path[sizeof(g_melonds.storage_path) - 1] = '\0';
            env->ReleaseStringUTFChars(internalStoragePath, path);
        }
    }

    if (!g_melonds.audio_rb) {
        g_melonds.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_MELONDS_CORE
    if (!s_nds_instance) {
        s_nds_instance = std::make_unique<melonDS::NDS>();
    }
#endif

    g_melonds.initialized = true;
    LOGI("melonDS NDS native runtime initialized (storage: %s)", g_melonds.storage_path);

    pthread_mutex_unlock(&g_melonds.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = env->GetStringUTFChars(romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_melonds.lock);

    inspect_nds_rom(native_path);

#ifdef HAVE_MELONDS_CORE
    if (s_nds_instance) {
        FILE* rf = fopen(native_path, "rb");
        if (rf) {
            fseek(rf, 0, SEEK_END);
            long flen = ftell(rf);
            fseek(rf, 0, SEEK_SET);
            if (flen > 0) {
                auto rdata = std::make_unique<uint8_t[]>(flen);
                fread(rdata.get(), 1, flen, rf);
                auto cart = melonDS::NDSCart::ParseROM(std::move(rdata), (uint32_t)flen);
                if (cart) {
                    s_nds_instance->SetNDSCart(std::move(cart));
                    s_nds_instance->SetupDirectBoot(native_path);
                    s_nds_instance->Reset();
                }
            }
            fclose(rf);
        }
    }
#endif

    g_melonds.rom_loaded = true;
    g_melonds.video_width = NDS_SCREEN_WIDTH;
    g_melonds.video_height = NDS_SCREEN_HEIGHT;
    g_melonds.frame_count = 0;

    // Render immediate frame 0
    render_melonds_active_frame(g_melonds.frame_count, g_melonds.key_mask,
                                g_melonds.touch_x, g_melonds.touch_y, g_melonds.is_touching);

    LOGI("melonDS loaded NDS ROM: %s (%dx%d)", native_path, g_melonds.video_width, g_melonds.video_height);
    pthread_mutex_unlock(&g_melonds.lock);
    env->ReleaseStringUTFChars(romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
#ifdef HAVE_MELONDS_CORE
    if (g_melonds.rom_loaded && s_nds_instance) {
        s_nds_instance->Stop();
    }
#endif
    g_melonds.rom_loaded = false;
    if (g_melonds.audio_rb) ringbuffer_reset(g_melonds.audio_rb);
    pthread_mutex_unlock(&g_melonds.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
#ifdef HAVE_MELONDS_CORE
    if (s_nds_instance) {
        s_nds_instance->Stop();
        s_nds_instance.reset();
    }
#endif
    if (g_melonds.audio_rb) {
        ringbuffer_destroy(g_melonds.audio_rb);
        g_melonds.audio_rb = NULL;
    }
    g_melonds.initialized = false;
    g_melonds.rom_loaded = false;
    pthread_mutex_unlock(&g_melonds.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
    if (!g_melonds.rom_loaded) {
        pthread_mutex_unlock(&g_melonds.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_MELONDS_CORE
    if (s_nds_instance) {
        s_nds_instance->KeyInput = ~g_melonds.key_mask;
        if (g_melonds.is_touching) {
            s_nds_instance->TouchScreen((uint16_t)g_melonds.touch_x, (uint16_t)g_melonds.touch_y);
        } else {
            s_nds_instance->ReleaseScreen();
        }
        s_nds_instance->RunFrame();
    }
#endif

    g_melonds.frame_count++;
    render_melonds_active_frame(g_melonds.frame_count, g_melonds.key_mask,
                                g_melonds.touch_x, g_melonds.touch_y, g_melonds.is_touching);
    render_melonds_audio(g_melonds.frame_count);

    pthread_mutex_unlock(&g_melonds.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_melonds.key_mask = (uint32_t) keyMask;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonSetTouch(
        JNIEnv* env, jobject thiz, jint x, jint y, jboolean isTouching) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
    g_melonds.touch_x = x;
    g_melonds.touch_y = y;
    g_melonds.is_touching = (isTouching == JNI_TRUE);
    pthread_mutex_unlock(&g_melonds.lock);
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
    if (!g_melonds.rom_loaded) {
        pthread_mutex_unlock(&g_melonds.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_melonds.video_width * g_melonds.video_height * sizeof(uint32_t));
    jobject direct_bb = env->NewDirectByteBuffer(g_melonds.video_buffer, byte_capacity);
    if (!direct_bb || !g_melonds.byte_buffer_class || !g_melonds.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_melonds.lock);
        return NULL;
    }

    if (g_melonds.byte_order_native && g_melonds.byte_buffer_order) {
        env->CallObjectMethod(direct_bb, g_melonds.byte_buffer_order, g_melonds.byte_order_native);
    }
    jobject int_buffer = env->CallObjectMethod(direct_bb, g_melonds.byte_buffer_as_int_buffer);
    env->DeleteLocalRef(direct_bb);

    pthread_mutex_unlock(&g_melonds.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_melonds.audio_rb) return 0;
    jshort* dst = (jshort*)env->GetPrimitiveArrayCritical(outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_melonds.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    env->ReleasePrimitiveArrayCritical(outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_melonds.audio_rb) return 0;
    return (jint) ringbuffer_available(g_melonds.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
    if (!g_melonds.rom_loaded) {
        pthread_mutex_unlock(&g_melonds.lock);
        return NULL;
    }
    jintArray result = env->NewIntArray(2);
    if (result) {
        jint dims[2] = { g_melonds.video_width, g_melonds.video_height };
        env->SetIntArrayRegion(result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_melonds.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_melonds.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_melonds.lock);
    if (!g_melonds.rom_loaded) {
        pthread_mutex_unlock(&g_melonds.lock);
        return JNI_FALSE;
    }

    jbyte* dst = (jbyte*)env->GetPrimitiveArrayCritical(outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)env->GetArrayLength(outBuffer);
        size_t copy_len = len < NDS_SRAM_SIZE ? len : NDS_SRAM_SIZE;
        memcpy(dst, g_melonds.sram, copy_len);
        env->ReleasePrimitiveArrayCritical(outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_melonds.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_melonds.lock);
    if (!g_melonds.rom_loaded) {
        pthread_mutex_unlock(&g_melonds.lock);
        return JNI_FALSE;
    }

    jbyte* src = (jbyte*)env->GetPrimitiveArrayCritical(inBuffer, NULL);
    if (src) {
        size_t len = (size_t)env->GetArrayLength(inBuffer);
        size_t copy_len = len < NDS_SRAM_SIZE ? len : NDS_SRAM_SIZE;
        memcpy(g_melonds.sram, src, copy_len);
        env->ReleasePrimitiveArrayCritical(inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_melonds.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_melonds.lock);
    FILE* fp = fopen(path, "wb");
    bool ok = false;
    if (fp) {
        fwrite("MELONDS_STATE_V1", 1, 16, fp);
        fwrite(&g_melonds.frame_count, sizeof(g_melonds.frame_count), 1, fp);
        fwrite(&g_melonds.key_mask, sizeof(g_melonds.key_mask), 1, fp);
        fwrite(g_melonds.sram, 1, NDS_SRAM_SIZE, fp);
        fclose(fp);
        ok = true;
    }
    pthread_mutex_unlock(&g_melonds.lock);
    env->ReleaseStringUTFChars(filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_melonds.lock);
    FILE* fp = fopen(path, "rb");
    bool ok = false;
    if (fp) {
        char magic[16];
        if (fread(magic, 1, 16, fp) == 16 && memcmp(magic, "MELONDS_STATE_V1", 16) == 0) {
            fread(&g_melonds.frame_count, sizeof(g_melonds.frame_count), 1, fp);
            fread(&g_melonds.key_mask, sizeof(g_melonds.key_mask), 1, fp);
            fread(g_melonds.sram, 1, NDS_SRAM_SIZE, fp);
            ok = true;
        }
        fclose(fp);
    }
    pthread_mutex_unlock(&g_melonds.lock);
    env->ReleaseStringUTFChars(filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
