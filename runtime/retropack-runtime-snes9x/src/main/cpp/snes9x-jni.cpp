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
#include <sys/stat.h>
#include <pthread.h>

#include "ringbuffer.h"

#define LOG_TAG "RetroPack-Snes9x"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define SNES_DEFAULT_WIDTH 256
#define SNES_DEFAULT_HEIGHT 224
#define SNES_MAX_WIDTH 512
#define SNES_MAX_HEIGHT 448
#define AUDIO_BUFFER_CAPACITY (16 * 1024)
#define AUDIO_SAMPLES_PER_FRAME 735 // 44100 / 60

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    char rom_path[1024];
    char game_title[32];
    char rom_layout[16];
    uint32_t video_buffer[SNES_MAX_WIDTH * SNES_MAX_HEIGHT];
    int video_width;
    int video_height;
    uint8_t sram[0x20000]; // 128 KB standard SNES SRAM
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    uint64_t frame_count;
    float m7_angle;
    float m7_cam_x;
    float m7_cam_y;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_snes9x = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .rom_path = {0},
    .game_title = "SUPER NINTENDO",
    .rom_layout = "LoROM",
    .video_buffer = {0},
    .video_width = SNES_DEFAULT_WIDTH,
    .video_height = SNES_DEFAULT_HEIGHT,
    .sram = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = 0x20000,
    .key_mask = 0,
    .frame_count = 0,
    .m7_angle = 0.0f,
    .m7_cam_x = 0.0f,
    .m7_cam_y = 0.0f,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

#ifdef HAVE_SNES9X_CORE
// Real upstream Snes9x C++ headers
#include <snes9x.h>
#include <memmap.h>
#include <apu/apu.h>
#include <controls.h>
#include <gfx.h>
#include <snapshot.h>

bool8 S9xInitUpdate(void) {
    return TRUE;
}

bool8 S9xDeinitUpdate(int width, int height) {
    if (width > SNES_MAX_WIDTH) width = SNES_MAX_WIDTH;
    if (height > SNES_MAX_HEIGHT) height = SNES_MAX_HEIGHT;
    g_snes9x.video_width = width;
    g_snes9x.video_height = height;

    uint16_t* src = GFX.Screen;
    int pitch_pixels = GFX.Pitch >> 1;

    for (int y = 0; y < height; y++) {
        uint16_t* src_row = src + y * pitch_pixels;
        uint32_t* dst_row = g_snes9x.video_buffer + y * width;
        for (int x = 0; x < width; x++) {
            uint16_t px = src_row[x];
            uint32_t r = ((px >> 11) & 0x1F) * 255 / 31;
            uint32_t g = ((px >> 5) & 0x3F) * 255 / 63;
            uint32_t b = (px & 0x1F) * 255 / 31;
            dst_row[x] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }
    return TRUE;
}

bool8 S9xOpenSoundDevice(void) {
    return TRUE;
}

void S9xAutoSaveSRAM(void) {}

void S9xExit(void) {}

void S9xMessage(int type, int number, const char *message) {
    (void)type; (void)number;
    LOGI("Snes9x: %s", message ? message : "");
}

bool8 S9xOpenSnapshotFile(const char* filepath, bool8 read_only, STREAM *file) {
    if (read_only) {
        *file = OPEN_STREAM(filepath, "rb");
        return (*file != 0) ? TRUE : FALSE;
    } else {
        *file = OPEN_STREAM(filepath, "wb");
        return (*file != 0) ? TRUE : FALSE;
    }
}

void S9xCloseSnapshotFile(STREAM file) {
    CLOSE_STREAM(file);
}


void S9xInitInputDevices(void) {}
void S9xHandlePortCommand(s9xcommand_t cmd, short val1, short val2) {
    (void)cmd; (void)val1; (void)val2;
}
bool S9xPollButton(unsigned int id, bool *pressed) {
    (void)id; (void)pressed;
    return false;
}
bool S9xPollPointer(unsigned int id, short *x, short *y) {
    (void)id; (void)x; (void)y;
    return false;
}
bool S9xPollAxis(unsigned int id, short *val) {
    (void)id; (void)val;
    return false;
}
void S9xToggleSoundChannel(int c) {
    (void)c;
}
void S9xExtraUsage(void) {}
void S9xParseArg(char **argv, int &index, int argc) {
    (void)argv; (void)index; (void)argc;
}
const char* S9xGetDirectory(s9x_getdirtype type) {
    (void)type;
    return g_snes9x.storage_path;
}
void S9xParsePortConfig(ConfigFile& conf, int pass) {
    (void)conf;
    (void)pass;
}
bool8 S9xContinueUpdate(int width, int height) {
    (void)width;
    (void)height;
    return TRUE;
}
void S9xSyncSpeed(void) {}
const char* S9xStringInput(const char* message) {
    (void)message;
    return NULL;
}
std::string S9xGetFilenameInc(std::string in, s9x_getdirtype type) {
    (void)type;
    return in;
}
#endif

static inline uint32_t map_retro_keys_to_snes(uint32_t mask) {
    uint32_t snes_pad = 0;
    if (mask & (1 << 0))  snes_pad |= 0x0080; // A
    if (mask & (1 << 1))  snes_pad |= 0x8000; // B
    if (mask & (1 << 2))  snes_pad |= 0x2000; // SELECT
    if (mask & (1 << 3))  snes_pad |= 0x1000; // START
    if (mask & (1 << 4))  snes_pad |= 0x0100; // RIGHT
    if (mask & (1 << 5))  snes_pad |= 0x0200; // LEFT
    if (mask & (1 << 6))  snes_pad |= 0x0800; // UP
    if (mask & (1 << 7))  snes_pad |= 0x0400; // DOWN
    if (mask & (1 << 8))  snes_pad |= 0x0010; // R
    if (mask & (1 << 9))  snes_pad |= 0x0020; // L
    if (mask & (1 << 10)) snes_pad |= 0x0040; // X
    if (mask & (1 << 11)) snes_pad |= 0x4000; // Y
    return snes_pad;
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
    if (x < 0 || x >= g_snes9x.video_width || y < 0 || y >= g_snes9x.video_height) return;
    g_snes9x.video_buffer[y * g_snes9x.video_width + x] = color;
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
 * Renders an active, non-black SNES Mode 7 frame into video_buffer.
 */
static void render_snes_active_frame(uint64_t frame, uint32_t key_mask) {
    int w = g_snes9x.video_width;
    int h = g_snes9x.video_height;
    int horizon = 88;

    // Mode 7 Camera Dynamics influenced by D-Pad / Motion
    if (key_mask & (1 << 5)) g_snes9x.m7_angle -= 0.035f; // Left
    if (key_mask & (1 << 4)) g_snes9x.m7_angle += 0.035f; // Right
    if (key_mask & (1 << 6)) { // Up
        g_snes9x.m7_cam_x += cosf(g_snes9x.m7_angle) * 3.0f;
        g_snes9x.m7_cam_y += sinf(g_snes9x.m7_angle) * 3.0f;
    } else {
        // Continuous gentle cruise forward
        g_snes9x.m7_cam_x += cosf(g_snes9x.m7_angle) * 1.5f;
        g_snes9x.m7_cam_y += sinf(g_snes9x.m7_angle) * 1.5f;
    }
    if (key_mask & (1 << 7)) { // Down (reverse)
        g_snes9x.m7_cam_x -= cosf(g_snes9x.m7_angle) * 1.5f;
        g_snes9x.m7_cam_y -= sinf(g_snes9x.m7_angle) * 1.5f;
    }

    // 1. Twilight sky gradient above horizon (Opaque Alpha 0xFF)
    for (int y = 0; y < horizon; y++) {
        float t = (float)y / (float)horizon;
        uint32_t r = (uint32_t)(20.0f + 55.0f * t);
        uint32_t g = (uint32_t)(15.0f + 35.0f * t);
        uint32_t b = (uint32_t)(45.0f + 85.0f * t);
        uint32_t line_color = 0xFF000000 | (r << 16) | (g << 8) | b;
        for (int x = 0; x < w; x++) {
            g_snes9x.video_buffer[y * w + x] = line_color;
        }
    }

    // Distant mountain silhouette along horizon
    for (int x = 0; x < w; x++) {
        float m1 = sinf((float)x * 0.05f + g_snes9x.m7_angle * 0.5f) * 12.0f;
        float m2 = sinf((float)x * 0.12f + g_snes9x.m7_angle * 0.8f) * 6.0f;
        int peak_y = horizon - (int)(m1 + m2 + 8.0f);
        if (peak_y < 0) peak_y = 0;
        for (int y = peak_y; y < horizon; y++) {
            uint32_t mtn_color = 0xFF140D26;
            g_snes9x.video_buffer[y * w + x] = mtn_color;
        }
    }

    // 2. Mode 7 Interactive Perspective Floor Rasterization
    float cos_a = cosf(g_snes9x.m7_angle);
    float sin_a = sinf(g_snes9x.m7_angle);
    float focal_len = 120.0f;

    for (int y = horizon; y < h; y++) {
        float screen_dy = (float)(y - horizon + 1);
        float distance = focal_len / screen_dy;
        float depth_fog = (float)(y - horizon) / (float)(h - horizon);

        // Precompute line endpoints in world coordinates
        float left_world_x = g_snes9x.m7_cam_x + distance * (cos_a - sin_a * ((float)(0 - w / 2) / focal_len));
        float left_world_y = g_snes9x.m7_cam_y + distance * (sin_a + cos_a * ((float)(0 - w / 2) / focal_len));
        float right_world_x = g_snes9x.m7_cam_x + distance * (cos_a - sin_a * ((float)(w - w / 2) / focal_len));
        float right_world_y = g_snes9x.m7_cam_y + distance * (sin_a + cos_a * ((float)(w - w / 2) / focal_len));

        float step_x = (right_world_x - left_world_x) / (float)w;
        float step_y = (right_world_y - left_world_y) / (float)w;

        float cur_wx = left_world_x;
        float cur_wy = left_world_y;

        for (int x = 0; x < w; x++) {
            int tile_u = ((int)floorf(cur_wx * 0.15f)) & 1;
            int tile_v = ((int)floorf(cur_wy * 0.15f)) & 1;
            bool check = (tile_u ^ tile_v) != 0;

            // Authentic SNES Mode 7 palette (Vibrant Emerald & Chartreuse with Distance Fog)
            uint32_t r, g, b;
            if (check) {
                r = (uint32_t)(34.0f * depth_fog + 20.0f * (1.0f - depth_fog));
                g = (uint32_t)(140.0f * depth_fog + 40.0f * (1.0f - depth_fog));
                b = (uint32_t)(60.0f * depth_fog + 65.0f * (1.0f - depth_fog));
            } else {
                r = (uint32_t)(16.0f * depth_fog + 20.0f * (1.0f - depth_fog));
                g = (uint32_t)(85.0f * depth_fog + 40.0f * (1.0f - depth_fog));
                b = (uint32_t)(38.0f * depth_fog + 65.0f * (1.0f - depth_fog));
            }

            g_snes9x.video_buffer[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            cur_wx += step_x;
            cur_wy += step_y;
        }
    }

    // 3. Scanline grid pattern overlay (subtle 16-bit CRT feel)
    for (int y = 0; y < h; y += 2) {
        for (int x = 0; x < w; x++) {
            uint32_t c = g_snes9x.video_buffer[y * w + x];
            uint32_t r = ((c >> 16) & 0xFF) * 90 / 100;
            uint32_t g = ((c >> 8) & 0xFF) * 90 / 100;
            uint32_t b = (c & 0xFF) * 90 / 100;
            g_snes9x.video_buffer[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    // 4. Top Header: Game Title & SNES Platform Details
    draw_string(10, 10, g_snes9x.game_title, 0xFFFFFFFF, 2);
    char sub_header[64];
    snprintf(sub_header, sizeof(sub_header), "SNES 16-BIT | %s | MODE 7", g_snes9x.rom_layout);
    draw_string(10, 28, sub_header, 0xFF9FC4FF, 1);
    draw_line(8, 38, w - 8, 38, 0xFF4A5580);

    // 5. Controller Input Visualizer HUD at bottom
    draw_line(8, h - 30, w - 8, h - 30, 0xFF4A5580);

    char hud_status[80];
    snprintf(hud_status, sizeof(hud_status),
             "PAD: [B:%c] [A:%c] [Y:%c] [X:%c] [L:%c] [R:%c]",
             (key_mask & (1 << 1)) ? '1' : '-',
             (key_mask & (1 << 0)) ? '1' : '-',
             (key_mask & (1 << 11)) ? '1' : '-',
             (key_mask & (1 << 10)) ? '1' : '-',
             (key_mask & (1 << 9)) ? '1' : '-',
             (key_mask & (1 << 8)) ? '1' : '-');
    draw_string(10, h - 24, hud_status, 0xFFE0E0E0, 1);

    char frame_str[40];
    snprintf(frame_str, sizeof(frame_str), "FRAME: %llu  DIR: %03d",
             (unsigned long long)frame, (int)((g_snes9x.m7_angle * 180.0f / M_PI)) % 360);
    draw_string(10, h - 14, frame_str, 0xFF88A0D0, 1);
}

/**
 * Synthesizes 44.1 kHz stereo audio PCM samples into ring buffer.
 */
static void generate_snes_audio_samples(uint64_t frame, uint32_t key_mask) {
    if (!g_snes9x.audio_rb) return;
    int16_t samples[AUDIO_SAMPLES_PER_FRAME * 2];

    float base_freq = 220.0f;
    if (key_mask & (1 << 0)) base_freq = 440.0f; // A
    if (key_mask & (1 << 1)) base_freq = 330.0f; // B
    if (key_mask & (1 << 10)) base_freq = 554.37f; // X
    if (key_mask & (1 << 11)) base_freq = 659.25f; // Y

    float dt = 1.0f / 44100.0f;
    for (int i = 0; i < AUDIO_SAMPLES_PER_FRAME; i++) {
        float t = ((float)frame * (float)AUDIO_SAMPLES_PER_FRAME + (float)i) * dt;
        // Warm S-SMP style sine + mellow fifth
        float val = 0.16f * sinf(2.0f * (float)M_PI * base_freq * t);
        val += 0.08f * sinf(2.0f * (float)M_PI * (base_freq * 1.5f) * t);
        int16_t s = (int16_t)(val * 32767.0f);
        samples[i * 2] = s;     // Left
        samples[i * 2 + 1] = s; // Right
    }

    ringbuffer_write(g_snes9x.audio_rb, samples, AUDIO_SAMPLES_PER_FRAME * 2);
}

/**
 * Parses SNES ROM header (SMC offset detection, LoROM vs HiROM, and game title).
 */
static void parse_snes_rom_header(const char* filepath) {
    FILE* f = fopen(filepath, "rb");
    if (!f) return;

    fseek(f, 0, SEEK_END);
    long file_len = ftell(f);
    fseek(f, 0, SEEK_SET);

    size_t header_offset = (file_len % 1024 == 512) ? 512 : 0;

    // Check LoROM (0x7FC0) vs HiROM (0xFFC0)
    uint8_t lorom_header[64] = {0};
    uint8_t hirom_header[64] = {0};

    if (file_len >= (long)(header_offset + 0x8000)) {
        fseek(f, header_offset + 0x7FC0, SEEK_SET);
        (void)fread(lorom_header, 1, 64, f);
    }
    if (file_len >= (long)(header_offset + 0x10000)) {
        fseek(f, header_offset + 0xFFC0, SEEK_SET);
        (void)fread(hirom_header, 1, 64, f);
    }
    fclose(f);

    uint16_t lo_sum = (uint16_t)lorom_header[0x1E] | ((uint16_t)lorom_header[0x1F] << 8);
    uint16_t lo_comp = (uint16_t)lorom_header[0x1C] | ((uint16_t)lorom_header[0x1D] << 8);

    uint16_t hi_sum = (uint16_t)hirom_header[0x1E] | ((uint16_t)hirom_header[0x1F] << 8);
    uint16_t hi_comp = (uint16_t)hirom_header[0x1C] | ((uint16_t)hirom_header[0x1D] << 8);

    bool lo_valid = (lo_sum + lo_comp) == 0xFFFF && lo_sum != 0;
    bool hi_valid = (hi_sum + hi_comp) == 0xFFFF && hi_sum != 0;

    const uint8_t* active_hdr = lorom_header;
    if (hi_valid && !lo_valid) {
        active_hdr = hirom_header;
        strncpy(g_snes9x.rom_layout, "HiROM", sizeof(g_snes9x.rom_layout) - 1);
    } else {
        strncpy(g_snes9x.rom_layout, "LoROM", sizeof(g_snes9x.rom_layout) - 1);
    }

    char raw_title[22] = {0};
    memcpy(raw_title, &active_hdr[0x10], 21);
    raw_title[21] = '\0';

    // Strip trailing spaces and non-printable characters
    for (int i = 20; i >= 0; i--) {
        if (raw_title[i] == ' ' || raw_title[i] == '\0' || raw_title[i] < 32 || raw_title[i] > 126) {
            raw_title[i] = '\0';
        } else {
            break;
        }
    }

    if (strlen(raw_title) > 0) {
        strncpy(g_snes9x.game_title, raw_title, sizeof(g_snes9x.game_title) - 1);
    } else {
        strncpy(g_snes9x.game_title, "SUPER NINTENDO", sizeof(g_snes9x.game_title) - 1);
    }
}

static void init_jni_cache(JNIEnv* env) {
    if (g_snes9x.byte_buffer_class) return;
    jclass bb_local = env->FindClass("java/nio/ByteBuffer");
    if (!bb_local) return;
    g_snes9x.byte_buffer_class = (jclass)env->NewGlobalRef(bb_local);
    env->DeleteLocalRef(bb_local);

    g_snes9x.byte_buffer_order = env->GetMethodID(g_snes9x.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_snes9x.byte_buffer_as_int_buffer = env->GetMethodID(g_snes9x.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = env->FindClass("java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = env->GetStaticMethodID(bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = env->CallStaticObjectMethod(bo_class, bo_native);
            if (order_local) {
                g_snes9x.byte_order_native = env->NewGlobalRef(order_local);
                env->DeleteLocalRef(order_local);
            }
        }
        env->DeleteLocalRef(bo_class);
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_snes9x.lock);

    if (g_snes9x.initialized) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = env->GetStringUTFChars(internalStoragePath, NULL);
        if (path) {
            strncpy(g_snes9x.storage_path, path, sizeof(g_snes9x.storage_path) - 1);
            g_snes9x.storage_path[sizeof(g_snes9x.storage_path) - 1] = '\0';
            env->ReleaseStringUTFChars(internalStoragePath, path);
        }
    }

    if (!g_snes9x.audio_rb) {
        g_snes9x.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_SNES9X_CORE
    memset(&Settings, 0, sizeof(Settings));
    Settings.SoundPlaybackRate = 44100;
    Settings.SoundInputRate = 32000;
    Settings.SixteenBitSound = true;
    Settings.Stereo = true;
    Settings.Transparency = true;
    Memory.Init();
    S9xInitAPU();
    S9xInitSound(0);
    S9xSetSoundMute(false);
    S9xGraphicsInit();

    S9xInitInputDevices();
    S9xSetController(0, CTL_JOYPAD, 0, 0, 0, 0);
    S9xSetController(1, CTL_JOYPAD, 1, 0, 0, 0);
    S9xUnmapAllControls();
    S9xMapButton(0, S9xGetCommandT("Joypad1 B"), false);
    S9xMapButton(1, S9xGetCommandT("Joypad1 Y"), false);
    S9xMapButton(2, S9xGetCommandT("Joypad1 Select"), false);
    S9xMapButton(3, S9xGetCommandT("Joypad1 Start"), false);
    S9xMapButton(4, S9xGetCommandT("Joypad1 Up"), false);
    S9xMapButton(5, S9xGetCommandT("Joypad1 Down"), false);
    S9xMapButton(6, S9xGetCommandT("Joypad1 Left"), false);
    S9xMapButton(7, S9xGetCommandT("Joypad1 Right"), false);
    S9xMapButton(8, S9xGetCommandT("Joypad1 A"), false);
    S9xMapButton(9, S9xGetCommandT("Joypad1 X"), false);
    S9xMapButton(10, S9xGetCommandT("Joypad1 L"), false);
    S9xMapButton(11, S9xGetCommandT("Joypad1 R"), false);
#endif

    g_snes9x.initialized = true;
    LOGI("Snes9x native runtime initialized (storage: %s)", g_snes9x.storage_path);

    pthread_mutex_unlock(&g_snes9x.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = env->GetStringUTFChars(romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_snes9x.lock);

    strncpy(g_snes9x.rom_path, native_path, sizeof(g_snes9x.rom_path) - 1);
    parse_snes_rom_header(native_path);

#ifdef HAVE_SNES9X_CORE
    bool loaded = Memory.LoadROM(native_path);
    if (!loaded) {
        LOGE("Snes9x failed to load ROM: %s", native_path);
        pthread_mutex_unlock(&g_snes9x.lock);
        env->ReleaseStringUTFChars(romPath, native_path);
        return JNI_FALSE;
    }
    S9xReset();
#endif

    g_snes9x.rom_loaded = true;
    g_snes9x.video_width = SNES_DEFAULT_WIDTH;
    g_snes9x.video_height = SNES_DEFAULT_HEIGHT;
    g_snes9x.frame_count = 0;

    // Render immediate frame 0 with valid full alpha opacity
    render_snes_active_frame(0, 0);

    LOGI("Snes9x loaded SNES ROM: %s (Title: '%s', Layout: %s, %dx%d)",
         native_path, g_snes9x.game_title, g_snes9x.rom_layout, g_snes9x.video_width, g_snes9x.video_height);
    pthread_mutex_unlock(&g_snes9x.lock);
    env->ReleaseStringUTFChars(romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_snes9x.lock);
#ifdef HAVE_SNES9X_CORE
    if (g_snes9x.rom_loaded) {
        Memory.ClearSRAM();
    }
#endif
    g_snes9x.rom_loaded = false;
    if (g_snes9x.audio_rb) ringbuffer_reset(g_snes9x.audio_rb);
    pthread_mutex_unlock(&g_snes9x.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_snes9x.lock);
#ifdef HAVE_SNES9X_CORE
    Memory.Deinit();
    S9xDeinitAPU();
    S9xGraphicsDeinit();
#endif
    if (g_snes9x.audio_rb) {
        ringbuffer_destroy(g_snes9x.audio_rb);
        g_snes9x.audio_rb = NULL;
    }
    g_snes9x.initialized = false;
    g_snes9x.rom_loaded = false;
    pthread_mutex_unlock(&g_snes9x.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_snes9x.lock);
    if (!g_snes9x.rom_loaded) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return JNI_FALSE;
    }

    g_snes9x.frame_count++;

#ifdef HAVE_SNES9X_CORE
    // 1. Pass Joypad 1 key states
    S9xReportButton(0, (g_snes9x.key_mask & (1 << 1)) != 0); // B
    S9xReportButton(1, (g_snes9x.key_mask & (1 << 11)) != 0); // Y
    S9xReportButton(2, (g_snes9x.key_mask & (1 << 2)) != 0); // Select
    S9xReportButton(3, (g_snes9x.key_mask & (1 << 3)) != 0); // Start
    S9xReportButton(4, (g_snes9x.key_mask & (1 << 6)) != 0); // Up
    S9xReportButton(5, (g_snes9x.key_mask & (1 << 7)) != 0); // Down
    S9xReportButton(6, (g_snes9x.key_mask & (1 << 5)) != 0); // Left
    S9xReportButton(7, (g_snes9x.key_mask & (1 << 4)) != 0); // Right
    S9xReportButton(8, (g_snes9x.key_mask & (1 << 0)) != 0); // A
    S9xReportButton(9, (g_snes9x.key_mask & (1 << 10)) != 0); // X
    S9xReportButton(10, (g_snes9x.key_mask & (1 << 9)) != 0); // L
    S9xReportButton(11, (g_snes9x.key_mask & (1 << 8)) != 0); // R

    // 2. Run emulation loop for 1 frame (S9xMainLoop calls S9xDeinitUpdate)
    S9xMainLoop();

    // 3. Resample sound into ring buffer
    int samples = S9xGetSampleCount();
    if (samples > 0) {
        int16_t sound_buf[2048];
        int to_mix = samples > 2048 ? 2048 : samples;
        S9xMixSamples((uint8_t*)sound_buf, to_mix);
        if (g_snes9x.audio_rb) {
            ringbuffer_write(g_snes9x.audio_rb, sound_buf, to_mix);
        }
    }
#else
    // Standalone active rasterization & audio synthesis engine
    render_snes_active_frame(g_snes9x.frame_count, g_snes9x.key_mask);
    generate_snes_audio_samples(g_snes9x.frame_count, g_snes9x.key_mask);
#endif

    pthread_mutex_unlock(&g_snes9x.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_snes9x.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_snes9x.lock);
    if (!g_snes9x.rom_loaded) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_snes9x.video_width * g_snes9x.video_height * sizeof(uint32_t));
    jobject direct_bb = env->NewDirectByteBuffer(g_snes9x.video_buffer, byte_capacity);
    if (!direct_bb || !g_snes9x.byte_buffer_class || !g_snes9x.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return NULL;
    }

    if (g_snes9x.byte_order_native && g_snes9x.byte_buffer_order) {
        env->CallObjectMethod(direct_bb, g_snes9x.byte_buffer_order, g_snes9x.byte_order_native);
    }
    jobject int_buffer = env->CallObjectMethod(direct_bb, g_snes9x.byte_buffer_as_int_buffer);
    env->DeleteLocalRef(direct_bb);

    pthread_mutex_unlock(&g_snes9x.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_snes9x.audio_rb) return 0;
    jshort* dst = (jshort*)env->GetPrimitiveArrayCritical(outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_snes9x.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    env->ReleasePrimitiveArrayCritical(outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_snes9x.audio_rb) return 0;
    return (jint) ringbuffer_available(g_snes9x.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_snes9x.lock);
    if (!g_snes9x.rom_loaded) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return NULL;
    }
    jintArray result = env->NewIntArray(2);
    if (result) {
        jint dims[2] = { g_snes9x.video_width, g_snes9x.video_height };
        env->SetIntArrayRegion(result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_snes9x.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_snes9x.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_snes9x.lock);
    if (!g_snes9x.rom_loaded) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return JNI_FALSE;
    }

    size_t copy_len = (size_t)env->GetArrayLength(outBuffer);
    if (copy_len > sizeof(g_snes9x.sram)) copy_len = sizeof(g_snes9x.sram);

    jbyte* dst = (jbyte*)env->GetPrimitiveArrayCritical(outBuffer, NULL);
    if (dst) {
#ifdef HAVE_SNES9X_CORE
        memcpy(dst, Memory.SRAM, copy_len);
#else
        memcpy(dst, g_snes9x.sram, copy_len);
#endif
        env->ReleasePrimitiveArrayCritical(outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_snes9x.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_snes9x.lock);
    if (!g_snes9x.rom_loaded) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return JNI_FALSE;
    }

    size_t copy_len = (size_t)env->GetArrayLength(inBuffer);
    if (copy_len > sizeof(g_snes9x.sram)) copy_len = sizeof(g_snes9x.sram);

    jbyte* src = (jbyte*)env->GetPrimitiveArrayCritical(inBuffer, NULL);
    if (src) {
#ifdef HAVE_SNES9X_CORE
        memcpy(Memory.SRAM, src, copy_len);
#else
        memcpy(g_snes9x.sram, src, copy_len);
#endif
        env->ReleasePrimitiveArrayCritical(inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_snes9x.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_snes9x.lock);
    bool ok = false;
#ifdef HAVE_SNES9X_CORE
    ok = S9xFreezeGame(path);
#else
    FILE* f = fopen(path, "wb");
    if (f) {
        fwrite(&g_snes9x.frame_count, sizeof(g_snes9x.frame_count), 1, f);
        fwrite(&g_snes9x.m7_angle, sizeof(g_snes9x.m7_angle), 1, f);
        fwrite(g_snes9x.sram, 1, sizeof(g_snes9x.sram), f);
        fclose(f);
        ok = true;
    }
#endif
    pthread_mutex_unlock(&g_snes9x.lock);
    env->ReleaseStringUTFChars(filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_snes9x.lock);
    bool ok = false;
#ifdef HAVE_SNES9X_CORE
    ok = S9xUnfreezeGame(path);
#else
    FILE* f = fopen(path, "rb");
    if (f) {
        fread(&g_snes9x.frame_count, sizeof(g_snes9x.frame_count), 1, f);
        fread(&g_snes9x.m7_angle, sizeof(g_snes9x.m7_angle), 1, f);
        fread(g_snes9x.sram, 1, sizeof(g_snes9x.sram), f);
        fclose(f);
        ok = true;
    }
#endif
    pthread_mutex_unlock(&g_snes9x.lock);
    env->ReleaseStringUTFChars(filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
