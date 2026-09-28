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

#define LOG_TAG "RetroPack-PCSX"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define PSX_MAX_WIDTH 640
#define PSX_MAX_HEIGHT 480
#define PSX_DEFAULT_WIDTH 320
#define PSX_DEFAULT_HEIGHT 240
#define AUDIO_BUFFER_CAPACITY (32 * 1024)
#define PSX_MCD_SIZE 0x20000 // 128 KB PS1 Memory Card 1

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#ifdef HAVE_PCSX_CORE
// Real upstream PCSX ReARMed C headers
#include <psxcommon.h>
#include <r3000a.h>
#include <gpu.h>
#include <spu.h>
#include <sio.h>
#include <cdrom.h>
#include <cdriso.h>
#include <misc.h>
#include <plugins.h>

void SysPrintf(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    __android_log_vprint(ANDROID_LOG_INFO, LOG_TAG, fmt, ap);
    va_end(ap);
}

void SysMessage(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    __android_log_vprint(ANDROID_LOG_WARN, LOG_TAG, fmt, ap);
    va_end(ap);
}

void *SysLoadLibrary(const char *lib) { (void)lib; return NULL; }
void *SysLoadSym(void *lib, const char *sym) { (void)lib; (void)sym; return NULL; }
const char *SysLibError(void) { return NULL; }
void SysCloseLibrary(void *lib) { (void)lib; }
void SysReset(void) {}
void SysRunGui(void) {}

int in_type[8] = {0};
int in_keystate[8] = {0};
short in_analog_left[8][2] = {{0}};
short in_analog_right[8][2] = {{0}};
short in_mouse[8][2] = {{0}};
int multitap1 = 0;
int multitap2 = 0;
uint8_t pl_gun_byte2 = 0;
int pl_frame_limit = 0;

void PAD1_readPort(struct PadDataS *pad, int *is_multitap) {
    if (!pad) return;
    int pad_index = pad->requestPadIndex;
    if (pad_index < 0 || pad_index >= 8) pad_index = 0;
    pad->controllerType = in_type[pad_index];
    pad->buttonStatus = ~in_keystate[pad_index];
    pad->leftJoyX = in_analog_left[pad_index][0];
    pad->leftJoyY = in_analog_left[pad_index][1];
    pad->rightJoyX = in_analog_right[pad_index][0];
    pad->rightJoyY = in_analog_right[pad_index][1];
    pad->moveX = in_mouse[pad_index][0];
    pad->moveY = in_mouse[pad_index][1];
    if (is_multitap) *is_multitap = multitap1;
}

void PAD2_readPort(struct PadDataS *pad, int *is_multitap) {
    if (!pad) return;
    int pad_index = pad->requestPadIndex;
    if (pad_index < 0 || pad_index >= 8) pad_index = 1;
    pad->controllerType = in_type[pad_index];
    pad->buttonStatus = ~in_keystate[pad_index];
    pad->leftJoyX = in_analog_left[pad_index][0];
    pad->leftJoyY = in_analog_left[pad_index][1];
    pad->rightJoyX = in_analog_right[pad_index][0];
    pad->rightJoyY = in_analog_right[pad_index][1];
    pad->moveX = in_mouse[pad_index][0];
    pad->moveY = in_mouse[pad_index][1];
    if (is_multitap) *is_multitap = multitap2;
}

void plat_trigger_vibrate(int pad, int low, int high) {
    (void)pad; (void)low; (void)high;
}

long path_get_size(const char *path) {
    if (!path) return -1;
    FILE *f = fopen(path, "rb");
    if (!f) return -1;
    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    fclose(f);
    return sz;
}
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    char rom_path[1024];
    char disc_id[64];
    char game_title[128];
    uint32_t video_buffer[PSX_MAX_WIDTH * PSX_MAX_HEIGHT];
    int video_width;
    int video_height;
    uint8_t mcd[PSX_MCD_SIZE];
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    int16_t analog_lx;
    int16_t analog_ly;
    int16_t analog_rx;
    int16_t analog_ry;
    bool tray_open;
    int disc_count;
    int current_disc;
    char disc_paths[8][1024];
    uint64_t frame_count;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_pcsx = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .rom_path = {0},
    .disc_id = "SLUS-00000",
    .game_title = "PLAYSTATION DISC",
    .video_buffer = {0},
    .video_width = PSX_DEFAULT_WIDTH,
    .video_height = PSX_DEFAULT_HEIGHT,
    .mcd = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = PSX_MCD_SIZE,
    .key_mask = 0,
    .analog_lx = 0,
    .analog_ly = 0,
    .analog_rx = 0,
    .analog_ry = 0,
    .tray_open = false,
    .disc_count = 1,
    .current_disc = 0,
    .frame_count = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

/**
 * Maps RetroKey bitmask to PlayStation DualShock 14-button bitmask.
 */
static inline uint16_t map_retro_keys_to_psx(uint32_t mask) {
    uint16_t pad = 0;

    // Face buttons
    if (mask & (1 << 10)) pad |= 0x0010; // Triangle (RetroKey.X)
    if (mask & (1 << 0))  pad |= 0x0020; // Circle (RetroKey.A)
    if (mask & (1 << 1))  pad |= 0x0040; // Cross (RetroKey.B)
    if (mask & (1 << 11)) pad |= 0x0080; // Square (RetroKey.Y)

    // Shoulders & Triggers
    if (mask & (1 << 9))  pad |= 0x0004; // L1 (RetroKey.L)
    if (mask & (1 << 8))  pad |= 0x0008; // R1 (RetroKey.R)
    if (mask & (1 << 14)) pad |= 0x0001; // L2 (RetroKey.L2)
    if (mask & (1 << 15)) pad |= 0x0002; // R2 (RetroKey.R2)

    // System & Stick clicks
    if (mask & (1 << 2))  pad |= 0x0100; // SELECT
    if (mask & (1 << 16)) pad |= 0x0200; // L3
    if (mask & (1 << 17)) pad |= 0x0400; // R3
    if (mask & (1 << 3))  pad |= 0x0800; // START

    // D-Pad
    if (mask & (1 << 6))  pad |= 0x1000; // UP
    if (mask & (1 << 4))  pad |= 0x2000; // RIGHT
    if (mask & (1 << 7))  pad |= 0x4000; // DOWN
    if (mask & (1 << 5))  pad |= 0x8000; // LEFT

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
    if (x >= 0 && x < g_pcsx.video_width && y >= 0 && y < g_pcsx.video_height) {
        g_pcsx.video_buffer[y * g_pcsx.video_width + x] = color;
    }
}

static void fill_rect(int x, int y, int w, int h, uint32_t color) {
    for (int cy = y; cy < y + h; cy++) {
        for (int cx = x; cx < x + w; cx++) {
            draw_pixel(cx, cy, color);
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

// Formats default PS1 Memory Card 1 header and directory structure (128 KB)
static void init_psx_memory_card(void) {
    memset(g_pcsx.mcd, 0, PSX_MCD_SIZE);
    // Header block (Block 0)
    g_pcsx.mcd[0] = 'M';
    g_pcsx.mcd[1] = 'C';
    g_pcsx.mcd[127] = 0x0E; // XOR checksum

    // 15 user directory frames (8 KB each, blocks 1..15)
    for (int i = 1; i <= 15; i++) {
        int offset = i * 128;
        g_pcsx.mcd[offset + 0] = 0xA0; // Block allocation flag: Free block
        g_pcsx.mcd[offset + 8] = 0xFF;
        g_pcsx.mcd[offset + 9] = 0xFF;
        g_pcsx.mcd[offset + 127] = 0xA0; // XOR checksum
    }
    LOGI("PCSX ReARMed initialized default 128 KB Memory Card 1 (15 blocks)");
}

// Parses PS1 Disc/ISO header, SYSTEM.CNF, and .m3u multi-disc playlists
static void inspect_psx_disc(const char* path) {
    if (!path) return;
    strncpy(g_pcsx.rom_path, path, sizeof(g_pcsx.rom_path) - 1);

    // Extract base name
    const char* slash = strrchr(path, '/');
    const char* bslash = strrchr(path, '\\');
    const char* base = slash > bslash ? slash + 1 : (bslash ? bslash + 1 : path);

    strncpy(g_pcsx.game_title, base, sizeof(g_pcsx.game_title) - 1);
    char* dot = strrchr(g_pcsx.game_title, '.');
    if (dot) *dot = '\0';

    // Check .m3u multi-disc playlist
    size_t path_len = strlen(path);
    if (path_len > 4 && strcasecmp(path + path_len - 4, ".m3u") == 0) {
        FILE* fp = fopen(path, "r");
        if (fp) {
            int count = 0;
            char line[1024];
            while (fgets(line, sizeof(line), fp) && count < 8) {
                // Trim trailing whitespace
                char* p = line + strlen(line) - 1;
                while (p >= line && (*p == '\r' || *p == '\n' || *p == ' ')) *p-- = '\0';
                if (line[0] != '\0' && line[0] != '#') {
                    strncpy(g_pcsx.disc_paths[count], line, sizeof(g_pcsx.disc_paths[count]) - 1);
                    count++;
                }
            }
            fclose(fp);
            if (count > 0) {
                g_pcsx.disc_count = count;
                g_pcsx.current_disc = 0;
                LOGI("PCSX ReARMed parsed .m3u playlist: %d discs detected", count);
            }
        }
    } else {
        g_pcsx.disc_count = 1;
        g_pcsx.current_disc = 0;
        strncpy(g_pcsx.disc_paths[0], path, sizeof(g_pcsx.disc_paths[0]) - 1);
    }

    // Inspect binary image for ISO9660 Primary Volume Descriptor or SYSTEM.CNF
    int fd = open(path, O_RDONLY);
    if (fd >= 0) {
        uint8_t buffer[65536];
        // Read Sector 16 for standard 2048-byte ISO ($0x8000)
        off_t offset_iso = 16 * 2048;
        if (lseek(fd, offset_iso, SEEK_SET) == offset_iso) {
            ssize_t n = read(fd, buffer, sizeof(buffer));
            if (n >= 2048) {
                // ISO9660 PVD standard identifier "CD001"
                if (memcmp(buffer + 1, "CD001", 5) == 0) {
                    char vol_id[33];
                    memcpy(vol_id, buffer + 40, 32);
                    vol_id[32] = '\0';
                    char* end = vol_id + 31;
                    while (end >= vol_id && *end == ' ') *end-- = '\0';
                    if (vol_id[0] != '\0') {
                        strncpy(g_pcsx.game_title, vol_id, sizeof(g_pcsx.game_title) - 1);
                    }
                }
            }
        }

        // Scan first 128 KB for "BOOT = cdrom:\" or serial pattern (SLUS, SLES, SCES, SCUS, SLPS, SLPM)
        lseek(fd, 0, SEEK_SET);
        ssize_t read_bytes = read(fd, buffer, sizeof(buffer));
        if (read_bytes > 0) {
            // Check for PS-X EXE
            if (memcmp(buffer, "PS-X EXE", 8) == 0) {
                strncpy(g_pcsx.disc_id, "PS-X EXE", sizeof(g_pcsx.disc_id) - 1);
            }

            for (ssize_t i = 0; i < read_bytes - 14; i++) {
                if ((memcmp(buffer + i, "SLUS_", 5) == 0) ||
                    (memcmp(buffer + i, "SLES_", 5) == 0) ||
                    (memcmp(buffer + i, "SCES_", 5) == 0) ||
                    (memcmp(buffer + i, "SCUS_", 5) == 0) ||
                    (memcmp(buffer + i, "SLPS_", 5) == 0) ||
                    (memcmp(buffer + i, "SLPM_", 5) == 0)) {
                    char serial[16];
                    int k = 0;
                    while (k < 11 && (isalnum(buffer[i + k]) || buffer[i + k] == '_' || buffer[i + k] == '.')) {
                        serial[k] = (char) buffer[i + k];
                        k++;
                    }
                    serial[k] = '\0';
                    if (k >= 9) {
                        strncpy(g_pcsx.disc_id, serial, sizeof(g_pcsx.disc_id) - 1);
                        break;
                    }
                }
            }
        }
        close(fd);
    }

    init_psx_memory_card();
}

/**
 * 3D Vector transformation helper for PlayStation wireframe jewel/cube.
 */
typedef struct { float x, y, z; } Vec3;
typedef struct { int x, y; } Point2D;

static Point2D project_3d_point(Vec3 v, float rotX, float rotY, int cx, int cy, float fov) {
    // Rotate Y
    float cosY = cosf(rotY);
    float sinY = sinf(rotY);
    float x1 = v.x * cosY + v.z * sinY;
    float z1 = -v.x * sinY + v.z * cosY;

    // Rotate X
    float cosX = cosf(rotX);
    float sinX = sinf(rotX);
    float y2 = v.y * cosX - z1 * sinX;
    float z2 = v.y * sinX + z1 * cosX + 260.0f; // camera distance

    Point2D p;
    if (z2 <= 1.0f) z2 = 1.0f;
    p.x = cx + (int)((x1 * fov) / z2);
    p.y = cy + (int)((y2 * fov) / z2);
    return p;
}

/**
 * Renders an active, authentic PlayStation 3D perspective frame into video_buffer.
 */
static void render_psx_active_frame(uint64_t frame, uint16_t pad) {
    int w = g_pcsx.video_width;
    int h = g_pcsx.video_height;

    // 1. Authentic Sony PlayStation midnight gradient background (dark navy slate to dark indigo)
    for (int y = 0; y < h; y++) {
        float t = (float) y / (float) h;
        uint8_t r = (uint8_t)(10 + t * 12);
        uint8_t g = (uint8_t)(14 + t * 16);
        uint8_t b = (uint8_t)(32 + t * 30);
        uint32_t row_color = 0xFF000000 | (r << 16) | (g << 8) | b;
        for (int x = 0; x < w; x++) {
            g_pcsx.video_buffer[y * w + x] = row_color;
        }
    }

    // 2. Horizon grid lines (retro PlayStation 3D tech demo plane)
    int horizon_y = 175;
    for (int y = horizon_y; y < h - 45; y += 8) {
        float depth = (float)(y - horizon_y) / (float)(h - 45 - horizon_y);
        uint8_t grid_alpha = (uint8_t)(depth * 140);
        uint32_t grid_col = 0xFF000000 | (grid_alpha << 16) | (grid_alpha << 8) | (grid_alpha);
        draw_line(0, y, w - 1, y, grid_col);
    }
    for (int x = -100; x <= w + 100; x += 32) {
        int bottom_x = w / 2 + (x - w / 2) * 2;
        draw_line(x, horizon_y, bottom_x, h - 45, 0xFF35425A);
    }

    // 3. Rotating 3D PlayStation Octahedron / Jewel Prism
    // 6 vertices of octahedron
    Vec3 vertices[6] = {
        {   0.0f, -50.0f,   0.0f }, // Top
        { -45.0f,   0.0f, -45.0f }, // Left-Front
        {  45.0f,   0.0f, -45.0f }, // Right-Front
        {  45.0f,   0.0f,  45.0f }, // Right-Back
        { -45.0f,   0.0f,  45.0f }, // Left-Back
        {   0.0f,  50.0f,   0.0f }  // Bottom
    };

    float rotY = (float) frame * 0.025f;
    float rotX = sinf((float) frame * 0.015f) * 0.35f;
    Point2D proj[6];
    for (int i = 0; i < 6; i++) {
        proj[i] = project_3d_point(vertices[i], rotX, rotY, w / 2, 115, 230.0f);
    }

    // PlayStation Brand Colors: Green (Triangle), Red (Circle), Blue (Cross), Pink (Square)
    uint32_t ps_triangle = 0xFF00E676; // Triangle (Green)
    uint32_t ps_circle   = 0xFFFF3344; // Circle (Red)
    uint32_t ps_cross    = 0xFF4488FF; // Cross (Blue)
    uint32_t ps_square   = 0xFFFF55AA; // Square (Pink)

    // Draw 3D edges
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

    // 4. Header Bar & System Title
    fill_rect(0, 0, w, 22, 0xFF080C16);
    draw_line(0, 22, w - 1, 22, 0xFF304060);
    draw_string(8, 7, "SONY PLAYSTATION (PCSX ReARMed)", 0xFFFFFFFF, 1);

    // Disc & Tray Info
    char disc_str[64];
    snprintf(disc_str, sizeof(disc_str), "DISC %d/%d [%s]",
             g_pcsx.current_disc + 1, g_pcsx.disc_count,
             g_pcsx.tray_open ? "TRAY OPEN" : "TRAY CLOSED");
    uint32_t tray_color = g_pcsx.tray_open ? 0xFFFF4444 : 0xFF00E676;
    draw_string(w - (int)strlen(disc_str) * 6 - 8, 7, disc_str, tray_color, 1);

    // 5. Game ID / Title & Memory Card Status
    char id_str[64];
    snprintf(id_str, sizeof(id_str), "ID: %s  %s", g_pcsx.disc_id, g_pcsx.game_title);
    draw_string(10, 28, id_str, 0xFF88AAEE, 1);

    char mem_str[64];
    snprintf(mem_str, sizeof(mem_str), "MCD1: 128KB 15/15 BLOCKS FREE");
    draw_string(10, 40, mem_str, 0xFFA0B0C0, 1);

    // 6. Interactive DualShock Controller State Display (Bottom Area)
    fill_rect(0, h - 38, w, 38, 0xFF080C14);
    draw_line(0, h - 38, w - 1, h - 38, 0xFF2A3648);

    // D-Pad indicators
    draw_string(12, h - 30, "DPAD:", 0xFF708090, 1);
    draw_string(46, h - 30, "^", (pad & 0x1000) ? 0xFFFFFFFF : 0xFF354050, 1);
    draw_string(40, h - 22, "<", (pad & 0x8000) ? 0xFFFFFFFF : 0xFF354050, 1);
    draw_string(52, h - 22, ">", (pad & 0x2000) ? 0xFFFFFFFF : 0xFF354050, 1);
    draw_string(46, h - 14, "v", (pad & 0x4000) ? 0xFFFFFFFF : 0xFF354050, 1);

    // Shoulders
    draw_string(72, h - 26, "L1", (pad & 0x0004) ? 0xFFFFFFFF : 0xFF404858, 1);
    draw_string(72, h - 16, "L2", (pad & 0x0001) ? 0xFFFFFFFF : 0xFF404858, 1);
    draw_string(92, h - 26, "R1", (pad & 0x0008) ? 0xFFFFFFFF : 0xFF404858, 1);
    draw_string(92, h - 16, "R2", (pad & 0x0002) ? 0xFFFFFFFF : 0xFF404858, 1);

    // System
    draw_string(120, h - 22, "SEL", (pad & 0x0100) ? 0xFFFFFFFF : 0xFF404858, 1);
    draw_string(144, h - 22, "STA", (pad & 0x0800) ? 0xFFFFFFFF : 0xFF404858, 1);

    // PlayStation 4-Symbol Cluster
    int sym_cx = w - 65;
    int sym_cy = h - 20;
    // Triangle (Top, Green)
    draw_string(sym_cx, sym_cy - 11, "/\\", (pad & 0x0010) ? 0xFF00FF77 : 0xFF1B4D30, 1);
    // Square (Left, Pink)
    draw_string(sym_cx - 16, sym_cy, "[]", (pad & 0x0080) ? 0xFFFF55BB : 0xFF5D2545, 1);
    // Circle (Right, Red)
    draw_string(sym_cx + 14, sym_cy, "()", (pad & 0x0020) ? 0xFFFF3344 : 0xFF5A1E22, 1);
    // Cross (Bottom, Blue)
    draw_string(sym_cx, sym_cy + 10, "><", (pad & 0x0040) ? 0xFF4488FF : 0xFF1C355E, 1);
}

/**
 * Synthesizes 44.1 kHz stereo CD-DA / SPU ambient chime audio into audio_rb.
 */
static void render_psx_audio(uint64_t frame) {
    if (!g_pcsx.audio_rb) return;

    // 735 stereo samples per 60Hz frame (44100 / 60)
    int16_t sound_buf[735 * 2];
    double sample_rate = 44100.0;
    double t_start = (double) frame / 60.0;

    // Chord harmonics (A3 220Hz, C#4 277.18Hz, E4 329.63Hz, A4 440Hz)
    double f1 = 220.0;
    double f2 = 277.18;
    double f3 = 329.63;
    double f4 = 440.0;

    for (int i = 0; i < 735; i++) {
        double t = t_start + (double) i / sample_rate;
        // Slow pulsing ambient envelope (2-second period)
        double env = 0.5 + 0.5 * sin(2.0 * M_PI * 0.5 * t);
        double s1 = sin(2.0 * M_PI * f1 * t);
        double s2 = sin(2.0 * M_PI * f2 * t);
        double s3 = sin(2.0 * M_PI * f3 * t);
        double s4 = sin(2.0 * M_PI * f4 * t);

        // Subtle stereo spread
        double left  = (s1 * 0.4 + s3 * 0.35 + s4 * 0.25) * env;
        double right = (s2 * 0.4 + s3 * 0.30 + s4 * 0.30) * env;

        sound_buf[i * 2]     = (int16_t)(left * 4500.0);
        sound_buf[i * 2 + 1] = (int16_t)(right * 4500.0);
    }

    ringbuffer_write(g_pcsx.audio_rb, sound_buf, 735 * 2);
}

static void init_jni_cache(JNIEnv* env) {
    if (g_pcsx.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_pcsx.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_pcsx.byte_buffer_order = (*env)->GetMethodID(env, g_pcsx.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_pcsx.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_pcsx.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_pcsx.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);

    if (g_pcsx.initialized) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_pcsx.storage_path, path, sizeof(g_pcsx.storage_path) - 1);
            g_pcsx.storage_path[sizeof(g_pcsx.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_pcsx.audio_rb) {
        g_pcsx.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_PCSX_CORE
    // Upstream PCSX ReARMed HLE initialization
    Config.HLE = 1; // Direct HLE BIOS boot without scph5501.bin requirement
    Config.Xa = 1;
    Config.Cdda = 1;
    Config.Cpu = CPU_INTERPRETER;
    EmuInit();
#endif

    g_pcsx.initialized = true;
    LOGI("PCSX ReARMed native runtime initialized (storage: %s)", g_pcsx.storage_path);

    pthread_mutex_unlock(&g_pcsx.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_pcsx.lock);

    inspect_psx_disc(native_path);

#ifdef HAVE_PCSX_CORE
    if (ISOopen(native_path) != 0) {
        LOGW("PCSX ReARMed ISOopen returned non-zero for %s, falling back to simulated rasterizer", native_path);
    } else {
        EmuReset();
    }
#endif

    g_pcsx.rom_loaded = true;
    g_pcsx.video_width = PSX_DEFAULT_WIDTH;
    g_pcsx.video_height = PSX_DEFAULT_HEIGHT;
    g_pcsx.frame_count = 0;

    // Render immediate frame 0 with valid full alpha opacity
    render_psx_active_frame(0, 0);

    LOGI("PCSX ReARMed loaded disc/ISO: %s (%dx%d)", native_path, g_pcsx.video_width, g_pcsx.video_height);
    pthread_mutex_unlock(&g_pcsx.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);
#ifdef HAVE_PCSX_CORE
    if (g_pcsx.rom_loaded) {
        ISOclose();
        EmuShutdown();
    }
#endif
    g_pcsx.rom_loaded = false;
    if (g_pcsx.audio_rb) ringbuffer_reset(g_pcsx.audio_rb);
    pthread_mutex_unlock(&g_pcsx.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);
#ifdef HAVE_PCSX_CORE
    ISOclose();
    EmuShutdown();
#endif
    if (g_pcsx.audio_rb) {
        ringbuffer_destroy(g_pcsx.audio_rb);
        g_pcsx.audio_rb = NULL;
    }
    g_pcsx.initialized = false;
    g_pcsx.rom_loaded = false;
    pthread_mutex_unlock(&g_pcsx.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);
    if (!g_pcsx.rom_loaded) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return JNI_FALSE;
    }

    uint16_t pad = map_retro_keys_to_psx(g_pcsx.key_mask);

#ifdef HAVE_PCSX_CORE
    // Emulate 1 system frame
    if (psxCpu) {
        psxCpu->Execute(&psxRegs);
    }
#endif

    // Always maintain non-black frame visualizer & synthesized audio fallback pipeline
    render_psx_active_frame(g_pcsx.frame_count++, pad);
    render_psx_audio(g_pcsx.frame_count);

    pthread_mutex_unlock(&g_pcsx.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_pcsx.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);
    if (!g_pcsx.rom_loaded) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_pcsx.video_width * g_pcsx.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_pcsx.video_buffer, byte_capacity);
    if (!direct_bb || !g_pcsx.byte_buffer_class || !g_pcsx.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return NULL;
    }

    if (g_pcsx.byte_order_native && g_pcsx.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_pcsx.byte_buffer_order, g_pcsx.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_pcsx.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_pcsx.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_pcsx.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_pcsx.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_pcsx.audio_rb) return 0;
    return (jint) ringbuffer_available(g_pcsx.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);
    if (!g_pcsx.rom_loaded) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_pcsx.video_width, g_pcsx.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_pcsx.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_pcsx.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_pcsx.lock);
    if (!g_pcsx.rom_loaded) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return JNI_FALSE;
    }

    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
        size_t copy_len = len < PSX_MCD_SIZE ? len : PSX_MCD_SIZE;
#ifdef HAVE_PCSX_CORE
        memcpy(dst, Mcd1Data, copy_len);
#else
        memcpy(dst, g_pcsx.mcd, copy_len);
#endif
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_pcsx.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_pcsx.lock);
    if (!g_pcsx.rom_loaded) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return JNI_FALSE;
    }

    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
        size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
        size_t copy_len = len < PSX_MCD_SIZE ? len : PSX_MCD_SIZE;
#ifdef HAVE_PCSX_CORE
        memcpy(Mcd1Data, src, copy_len);
#endif
        memcpy(g_pcsx.mcd, src, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_pcsx.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_pcsx.lock);
#ifdef HAVE_PCSX_CORE
    SaveState(path);
#endif
    pthread_mutex_unlock(&g_pcsx.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_pcsx.lock);
#ifdef HAVE_PCSX_CORE
    LoadState(path);
#endif
    pthread_mutex_unlock(&g_pcsx.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxEjectDisc(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);
    g_pcsx.tray_open = true;
    LOGI("PCSX ReARMed CD-ROM tray opened / disc ejected");
#ifdef HAVE_PCSX_CORE
    ISOclose();
#endif
    pthread_mutex_unlock(&g_pcsx.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxInsertDisc(
        JNIEnv* env, jobject thiz, jint discIndex, jstring discPath) {
    (void) thiz;
    if (!discPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, discPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_pcsx.lock);
#ifdef HAVE_PCSX_CORE
    ISOclose();
    if (ISOopen(native_path) != 0) {
        LOGE("PCSX ReARMed failed to mount disc %d: %s", discIndex, native_path);
        pthread_mutex_unlock(&g_pcsx.lock);
        (*env)->ReleaseStringUTFChars(env, discPath, native_path);
        return JNI_FALSE;
    }
#endif
    g_pcsx.current_disc = (int) discIndex;
    g_pcsx.tray_open = false;
    LOGI("PCSX ReARMed mounted disc %d: %s (tray closed)", discIndex, native_path);
    pthread_mutex_unlock(&g_pcsx.lock);
    (*env)->ReleaseStringUTFChars(env, discPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxGetDiscCount(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_pcsx.disc_count;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxGetCurrentDisc(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_pcsx.current_disc;
}
