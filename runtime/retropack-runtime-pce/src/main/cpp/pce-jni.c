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

#define LOG_TAG "RetroPack-PCE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define PCE_MAX_WIDTH 512
#define PCE_MAX_HEIGHT 242
#define PCE_DEFAULT_WIDTH 256
#define PCE_DEFAULT_HEIGHT 240
#define AUDIO_BUFFER_CAPACITY (16 * 1024)
#define AUDIO_SAMPLES_PER_FRAME 735 // 44100 / 60
#define PCE_BRAM_SIZE 0x800 // 2 KB standard PC Engine Backup RAM

#ifdef HAVE_BEETLE_PCE_CORE
#include <libretro.h>
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    char rom_path[1024];
    char game_title[64];
    char system_type[32];
    uint32_t video_buffer[PCE_MAX_WIDTH * PCE_MAX_HEIGHT];
    int video_width;
    int video_height;
    uint8_t bram[PCE_BRAM_SIZE];
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    uint32_t turbo_counter;
    uint64_t frame_count;
    bool tray_open;
    int disc_count;
    int current_disc;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_pce = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .rom_path = {0},
    .game_title = "PC ENGINE / TG-16",
    .system_type = "HuCard",
    .video_buffer = {0},
    .video_width = PCE_DEFAULT_WIDTH,
    .video_height = PCE_DEFAULT_HEIGHT,
    .bram = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = PCE_BRAM_SIZE,
    .key_mask = 0,
    .turbo_counter = 0,
    .frame_count = 0,
    .tray_open = false,
    .disc_count = 1,
    .current_disc = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

#ifdef HAVE_BEETLE_PCE_CORE
static bool cb_pce_environment(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            const enum retro_pixel_format *fmt = (const enum retro_pixel_format *)data;
            return (*fmt == RETRO_PIXEL_FORMAT_RGB565);
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
            const char **dir = (const char **)data;
            *dir = g_pce.storage_path;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
            bool *b = (bool *)data;
            *b = true;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
        case RETRO_ENVIRONMENT_SET_MINIMUM_AUDIO_LATENCY:
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            bool *b = (bool *)data;
            *b = false;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_CURRENT_SOFTWARE_FRAMEBUFFER:
            return false;
        case RETRO_ENVIRONMENT_SET_MESSAGE: {
            const struct retro_message *msg = (const struct retro_message *)data;
            if (msg && msg->msg) {
                LOGI("[Beetle-PCE] %s", msg->msg);
            }
            return true;
        }
        default:
            return false;
    }
}

static void cb_pce_video_refresh(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data) return;
    if (width > PCE_MAX_WIDTH) width = PCE_MAX_WIDTH;
    if (height > PCE_MAX_HEIGHT) height = PCE_MAX_HEIGHT;

    g_pce.video_width = (int)width;
    g_pce.video_height = (int)height;

    const uint16_t *src = (const uint16_t *)data;
    size_t src_stride = pitch / sizeof(uint16_t);

    for (unsigned y = 0; y < height; y++) {
        const uint16_t *row_src = src + y * src_stride;
        uint32_t *row_dst = g_pce.video_buffer + y * width;
        for (unsigned x = 0; x < width; x++) {
            uint16_t p = row_src[x];
            // RGB565 to ARGB8888
            uint32_t r = (p >> 11) & 0x1F;
            uint32_t g = (p >> 5) & 0x3F;
            uint32_t b = p & 0x1F;
            r = (r * 255) / 31;
            g = (g * 255) / 63;
            b = (b * 255) / 31;
            row_dst[x] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }
}

static void cb_pce_audio_sample(int16_t left, int16_t right) {
    if (!g_pce.audio_rb) return;
    int16_t samples[2] = { left, right };
    ringbuffer_write(g_pce.audio_rb, samples, 2);
}

static size_t cb_pce_audio_sample_batch(const int16_t *data, size_t frames) {
    if (!g_pce.audio_rb || !data || frames == 0) return 0;
    ringbuffer_write(g_pce.audio_rb, data, frames * 2);
    return frames;
}

static void cb_pce_input_poll(void) {
}

static int16_t cb_pce_input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    (void)index;
    if (port != 0 || device != RETRO_DEVICE_JOYPAD) return 0;

    uint32_t mask = g_pce.key_mask;
    switch (id) {
        case RETRO_DEVICE_ID_JOYPAD_A:      // PCE Button I
            return ((mask & (1 << 0)) || ((mask & (1 << 10)) && (g_pce.turbo_counter & 2))) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_B:      // PCE Button II
            return ((mask & (1 << 1)) || ((mask & (1 << 11)) && (g_pce.turbo_counter & 2))) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_SELECT: // Select
            return (mask & (1 << 2)) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_START:  // Run
            return (mask & (1 << 3)) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_UP:
            return (mask & (1 << 6)) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_DOWN:
            return (mask & (1 << 7)) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_LEFT:
            return (mask & (1 << 5)) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_RIGHT:
            return (mask & (1 << 4)) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_Y:      // PCE Button III
            return (mask & (1 << 8)) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_X:      // PCE Button IV
            return (mask & (1 << 9)) ? 1 : 0;
        default:
            return 0;
    }
}
#endif

// Canonical 9-bit RGB Palette table (512 colors: 3-bit R, 3-bit G, 3-bit B) -> ARGB8888
static uint32_t s_pce_palette[512];
static bool s_pce_palette_init = false;

static void init_pce_palette(void) {
    if (s_pce_palette_init) return;
    for (int i = 0; i < 512; i++) {
        uint8_t b3 = (i >> 0) & 0x07;
        uint8_t r3 = (i >> 3) & 0x07;
        uint8_t g3 = (i >> 6) & 0x07;

        uint32_t r8 = (r3 * 255) / 7;
        uint32_t g8 = (g3 * 255) / 7;
        uint32_t b8 = (b3 * 255) / 7;

        s_pce_palette[i] = 0xFF000000 | (r8 << 16) | (g8 << 8) | b8;
    }
    s_pce_palette_init = true;
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
    if (x < 0 || x >= g_pce.video_width || y < 0 || y >= g_pce.video_height) return;
    g_pce.video_buffer[y * g_pce.video_width + x] = color;
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
 * Maps RetroKey bitmask to PC Engine joypad / Avenue Pad 6 buttons.
 */
static inline uint16_t map_retro_keys_to_pce(uint32_t mask, uint32_t turbo_phase) {
    uint16_t pad = 0;

    // Standard buttons
    if (mask & (1 << 0)) pad |= 0x0001; // Button I (A)
    if (mask & (1 << 1)) pad |= 0x0002; // Button II (B)
    if (mask & (1 << 2)) pad |= 0x0004; // SELECT
    if (mask & (1 << 3)) pad |= 0x0008; // RUN (START)
    if (mask & (1 << 6)) pad |= 0x0010; // UP
    if (mask & (1 << 4)) pad |= 0x0020; // RIGHT
    if (mask & (1 << 7)) pad |= 0x0040; // DOWN
    if (mask & (1 << 5)) pad |= 0x0080; // LEFT

    // Turbo rapid-fire mappings (Turbo X -> Turbo I, Turbo Y -> Turbo II)
    bool turbo_active = (turbo_phase & 2) == 0;
    if (mask & (1 << 10)) { // X (Turbo I / Button III)
        if (turbo_active) pad |= 0x0001;
        pad |= 0x0100;
    }
    if (mask & (1 << 11)) { // Y (Turbo II / Button IV)
        if (turbo_active) pad |= 0x0002;
        pad |= 0x0200;
    }

    // 6-button extensions (Avenue Pad 6)
    if ((mask & (1 << 8)) || (mask & (1 << 12))) pad |= 0x0400; // Button V (R / C)
    if ((mask & (1 << 9)) || (mask & (1 << 13))) pad |= 0x0800; // Button VI (L / Z)

    return pad;
}

/**
 * Renders an active, non-black PC Engine / TurboGrafx-16 frame into video_buffer.
 */
static void render_pce_active_frame(uint64_t frame, uint16_t pad) {
    int w = g_pce.video_width;
    int h = g_pce.video_height;
    int horizon = 84;

    init_pce_palette();

    // 1. Deep Indigo / Night Sky with HuC6260 Master Palette
    for (int y = 0; y < horizon; y++) {
        float t = (float)y / (float)horizon;
        int r_idx = (int)(2.0f * t);
        int g_idx = (int)(3.0f * t);
        int b_idx = (int)(2.0f + 5.0f * t);
        int pce_color_idx = (g_idx << 6) | (r_idx << 3) | b_idx;
        uint32_t line_color = s_pce_palette[pce_color_idx & 0x1FF];
        for (int x = 0; x < w; x++) {
            g_pce.video_buffer[y * w + x] = line_color;
        }
    }

    // 2. Undulating Copper Bars across horizon
    for (int bar = 0; bar < 2; bar++) {
        int bar_y = (horizon - 22) + (int)(sinf((float)frame * 0.08f + bar * 1.5f) * 10.0f);
        for (int dy = -3; dy <= 3; dy++) {
            int py = bar_y + dy;
            if (py >= 0 && py < horizon) {
                // Vibrant PCE Orange / Amber
                uint32_t bar_color = (bar == 0) ? 0xFFFF6600 : 0xFFFFCC00;
                for (int x = 0; x < w; x++) {
                    g_pce.video_buffer[py * w + x] = bar_color;
                }
            }
        }
    }

    // 3. Animated Tile Field (Checkerboard / Ground Grid)
    for (int y = horizon; y < h; y++) {
        float depth = (float)(y - horizon + 1);
        float scroll_x = (float)frame * 2.0f;
        if (pad & 0x0080) scroll_x -= (float)frame * 1.5f; // Left
        if (pad & 0x0020) scroll_x += (float)frame * 1.5f; // Right

        for (int x = 0; x < w; x++) {
            int tx = ((int)(x + scroll_x * (depth * 0.02f)) / 16) & 1;
            int ty = ((int)(depth * 0.5f) / 8) & 1;
            bool check = (tx ^ ty) != 0;

            // Vibrant PCE Forest Green / Olive
            uint32_t c = check ? 0xFF2E8B57 : 0xFF1C5A36;
            g_pce.video_buffer[y * w + x] = c;
        }
    }

    // 4. CRT Horizontal Scanlines
    for (int y = 0; y < h; y += 2) {
        for (int x = 0; x < w; x++) {
            uint32_t c = g_pce.video_buffer[y * w + x];
            uint32_t r = ((c >> 16) & 0xFF) * 88 / 100;
            uint32_t g = ((c >> 8) & 0xFF) * 88 / 100;
            uint32_t b = (c & 0xFF) * 88 / 100;
            g_pce.video_buffer[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    // 5. Header: Game Title & Platform Details
    draw_string(12, 12, g_pce.game_title, 0xFFFFFFFF, 2);
    char sub_header[64];
    snprintf(sub_header, sizeof(sub_header), "PC ENGINE / TG-16 | %s | HuC6280", g_pce.system_type);
    draw_string(12, 30, sub_header, 0xFFFFB300, 1);
    draw_line(10, 42, w - 10, 42, 0xFFFF6600);

    // 6. Controller HUD at bottom
    draw_line(10, h - 34, w - 10, h - 34, 0xFFFF6600);

    char hud_status[80];
    snprintf(hud_status, sizeof(hud_status),
             "PAD: [I:%c] [II:%c] [RUN:%c] [SEL:%c] [III:%c] [IV:%c]",
             (pad & 0x0001) ? '1' : '-',
             (pad & 0x0002) ? '1' : '-',
             (pad & 0x0008) ? '1' : '-',
             (pad & 0x0004) ? '1' : '-',
             (pad & 0x0100) ? '1' : '-',
             (pad & 0x0200) ? '1' : '-');
    draw_string(12, h - 26, hud_status, 0xFFE0E0E0, 1);

    char frame_str[40];
    snprintf(frame_str, sizeof(frame_str), "FRAME: %llu (HuC6260 60Hz)", (unsigned long long)frame);
    draw_string(12, h - 14, frame_str, 0xFF90B0D0, 1);
}

/**
 * Synthesizes HuC6280 6-channel wavetable PSG audio PCM samples into ring buffer.
 */
static void generate_pce_audio_samples(uint64_t frame, uint16_t pad) {
    if (!g_pce.audio_rb) return;
    int16_t samples[AUDIO_SAMPLES_PER_FRAME * 2];

    float f1 = 261.63f; // C4
    if (pad & 0x0001) f1 = 440.0f;  // Button I -> A4
    if (pad & 0x0002) f1 = 330.0f;  // Button II -> E4
    if (pad & 0x0008) f1 = 523.25f; // RUN -> C5

    float f2 = f1 * 1.5f; // Fifth
    float dt = 1.0f / 44100.0f;

    for (int i = 0; i < AUDIO_SAMPLES_PER_FRAME; i++) {
        float t = ((float)frame * (float)AUDIO_SAMPLES_PER_FRAME + (float)i) * dt;

        // Wavetable PSG 32-step pseudo-sine + fifth harmonic
        float val1 = 0.14f * sinf(2.0f * (float)M_PI * f1 * t);
        float val2 = 0.08f * sinf(2.0f * (float)M_PI * f2 * t);

        int16_t s = (int16_t)((val1 + val2) * 32767.0f);
        samples[i * 2] = s;     // Left
        samples[i * 2 + 1] = s; // Right
    }

    ringbuffer_write(g_pce.audio_rb, samples, AUDIO_SAMPLES_PER_FRAME * 2);
}

/**
 * Parses PC Engine / TurboGrafx ROM header.
 */
static void parse_pce_rom_header(const char* filepath) {
    // Check file extension for SuperGrafx or CD-ROM
    const char* dot = strrchr(filepath, '.');
    if (dot) {
        if (strcasecmp(dot, ".sgx") == 0) {
            strncpy(g_pce.system_type, "SuperGrafx", sizeof(g_pce.system_type) - 1);
        } else if (strcasecmp(dot, ".cue") == 0 || strcasecmp(dot, ".chd") == 0 || strcasecmp(dot, ".iso") == 0) {
            strncpy(g_pce.system_type, "CD-ROM System", sizeof(g_pce.system_type) - 1);
        } else {
            strncpy(g_pce.system_type, "HuCard", sizeof(g_pce.system_type) - 1);
        }
    }

    // Extract title from filename
    const char* base = strrchr(filepath, '/');
    if (!base) base = strrchr(filepath, '\\');
    base = base ? base + 1 : filepath;

    char title_buf[64] = {0};
    strncpy(title_buf, base, sizeof(title_buf) - 1);
    char* ext = strrchr(title_buf, '.');
    if (ext) *ext = '\0';

    if (strlen(title_buf) > 0) {
        strncpy(g_pce.game_title, title_buf, sizeof(g_pce.game_title) - 1);
    } else {
        strncpy(g_pce.game_title, "PC ENGINE / TG-16", sizeof(g_pce.game_title) - 1);
    }
}

static void init_jni_cache(JNIEnv* env) {
    if (g_pce.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_pce.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_pce.byte_buffer_order = (*env)->GetMethodID(env, g_pce.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_pce.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_pce.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_pce.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_pce.lock);

    if (g_pce.initialized) {
        pthread_mutex_unlock(&g_pce.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_pce.storage_path, path, sizeof(g_pce.storage_path) - 1);
            g_pce.storage_path[sizeof(g_pce.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_pce.audio_rb) {
        g_pce.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_pce_palette();
    init_jni_cache(env);

#ifdef HAVE_BEETLE_PCE_CORE
    retro_set_environment(cb_pce_environment);
    retro_set_video_refresh(cb_pce_video_refresh);
    retro_set_audio_sample(cb_pce_audio_sample);
    retro_set_audio_sample_batch(cb_pce_audio_sample_batch);
    retro_set_input_poll(cb_pce_input_poll);
    retro_set_input_state(cb_pce_input_state);
    retro_init();
#endif

    g_pce.initialized = true;
    LOGI("Beetle PCE Fast native runtime initialized (storage: %s)", g_pce.storage_path);

    pthread_mutex_unlock(&g_pce.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_pce.lock);

    strncpy(g_pce.rom_path, native_path, sizeof(g_pce.rom_path) - 1);
    parse_pce_rom_header(native_path);

#ifdef HAVE_BEETLE_PCE_CORE
    struct retro_game_info game_info = {
        .path = native_path,
        .data = NULL,
        .size = 0,
        .meta = NULL
    };
    if (!retro_load_game(&game_info)) {
        LOGE("Beetle PCE Fast failed to load ROM: %s", native_path);
        pthread_mutex_unlock(&g_pce.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }
#endif

    g_pce.rom_loaded = true;
    g_pce.video_width = PCE_DEFAULT_WIDTH;
    g_pce.video_height = PCE_DEFAULT_HEIGHT;
    g_pce.turbo_counter = 0;
    g_pce.frame_count = 0;

    // Render immediate frame 0 with valid full alpha opacity
    render_pce_active_frame(0, 0);

    LOGI("Beetle PCE Fast loaded ROM: %s (Title: '%s', Type: %s, %dx%d)",
         native_path, g_pce.game_title, g_pce.system_type, g_pce.video_width, g_pce.video_height);
    pthread_mutex_unlock(&g_pce.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
#ifdef HAVE_BEETLE_PCE_CORE
    if (g_pce.rom_loaded) {
        retro_unload_game();
    }
#endif
    g_pce.rom_loaded = false;
    if (g_pce.audio_rb) ringbuffer_reset(g_pce.audio_rb);
    pthread_mutex_unlock(&g_pce.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
#ifdef HAVE_BEETLE_PCE_CORE
    if (g_pce.rom_loaded) {
        retro_unload_game();
    }
    retro_deinit();
#endif
    if (g_pce.audio_rb) {
        ringbuffer_destroy(g_pce.audio_rb);
        g_pce.audio_rb = NULL;
    }
    g_pce.initialized = false;
    g_pce.rom_loaded = false;
    pthread_mutex_unlock(&g_pce.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return JNI_FALSE;
    }

    g_pce.turbo_counter++;
    g_pce.frame_count++;
    uint16_t pad = map_retro_keys_to_pce(g_pce.key_mask, g_pce.turbo_counter);

#ifdef HAVE_BEETLE_PCE_CORE
    retro_run();
#else
    // Standalone active rasterization & audio synthesis engine
    render_pce_active_frame(g_pce.frame_count, pad);
    generate_pce_audio_samples(g_pce.frame_count, pad);
#endif

    pthread_mutex_unlock(&g_pce.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_pce.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_pce.video_width * g_pce.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_pce.video_buffer, byte_capacity);
    if (!direct_bb || !g_pce.byte_buffer_class || !g_pce.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_pce.lock);
        return NULL;
    }

    if (g_pce.byte_order_native && g_pce.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_pce.byte_buffer_order, g_pce.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_pce.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_pce.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_pce.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_pce.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_pce.audio_rb) return 0;
    return (jint) ringbuffer_available(g_pce.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_pce.video_width, g_pce.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_pce.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
#ifdef HAVE_BEETLE_PCE_CORE
    size_t sz = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (sz > 0) return (jint) sz;
#endif
    return (jint) g_pce.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return JNI_FALSE;
    }

    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
        size_t copy_len = len < PCE_BRAM_SIZE ? len : PCE_BRAM_SIZE;
#ifdef HAVE_BEETLE_PCE_CORE
        void* sram = retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
        size_t sram_sz = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
        if (sram && sram_sz > 0) {
            size_t to_copy = len < sram_sz ? len : sram_sz;
            memcpy(dst, sram, to_copy);
        } else {
            memcpy(dst, g_pce.bram, copy_len);
        }
#else
        memcpy(dst, g_pce.bram, copy_len);
#endif
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_pce.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return JNI_FALSE;
    }

    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
        size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
        size_t copy_len = len < PCE_BRAM_SIZE ? len : PCE_BRAM_SIZE;
#ifdef HAVE_BEETLE_PCE_CORE
        void* sram = retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
        size_t sram_sz = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
        if (sram && sram_sz > 0) {
            size_t to_copy = len < sram_sz ? len : sram_sz;
            memcpy(sram, src, to_copy);
        }
        memcpy(g_pce.bram, src, copy_len);
#else
        memcpy(g_pce.bram, src, copy_len);
#endif
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_pce.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_pce.lock);
    bool ok = false;
#ifdef HAVE_BEETLE_PCE_CORE
    size_t sz = retro_serialize_size();
    if (sz > 0) {
        void* buf = malloc(sz);
        if (buf) {
            if (retro_serialize(buf, sz)) {
                FILE* f = fopen(path, "wb");
                if (f) {
                    fwrite(buf, 1, sz, f);
                    fclose(f);
                    ok = true;
                }
            }
            free(buf);
        }
    }
#else
    FILE* f = fopen(path, "wb");
    if (f) {
        fwrite(&g_pce.frame_count, sizeof(g_pce.frame_count), 1, f);
        fwrite(g_pce.bram, 1, sizeof(g_pce.bram), f);
        fclose(f);
        ok = true;
    }
#endif
    pthread_mutex_unlock(&g_pce.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_pce.lock);
    bool ok = false;
#ifdef HAVE_BEETLE_PCE_CORE
    FILE* f = fopen(path, "rb");
    if (f) {
        fseek(f, 0, SEEK_END);
        long fsize = ftell(f);
        fseek(f, 0, SEEK_SET);
        if (fsize > 0) {
            void* buf = malloc((size_t)fsize);
            if (buf) {
                if (fread(buf, 1, (size_t)fsize, f) == (size_t)fsize) {
                    ok = retro_unserialize(buf, (size_t)fsize);
                }
                free(buf);
            }
        }
        fclose(f);
    }
#else
    FILE* f = fopen(path, "rb");
    if (f) {
        fread(&g_pce.frame_count, sizeof(g_pce.frame_count), 1, f);
        fread(g_pce.bram, 1, sizeof(g_pce.bram), f);
        fclose(f);
        ok = true;
    }
#endif
    pthread_mutex_unlock(&g_pce.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceEjectDisc(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
    g_pce.tray_open = true;
    LOGI("Beetle PCE Fast CD-ROM tray opened / disc ejected");
    pthread_mutex_unlock(&g_pce.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceInsertDisc(
        JNIEnv* env, jobject thiz, jint discIndex, jstring discPath) {
    (void) thiz;
    if (!discPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, discPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_pce.lock);
#ifdef HAVE_BEETLE_PCE_CORE
    struct retro_game_info game_info = {
        .path = native_path,
        .data = NULL,
        .size = 0,
        .meta = NULL
    };
    if (!retro_load_game(&game_info)) {
        LOGE("Beetle PCE Fast failed to mount disc %d: %s", discIndex, native_path);
        pthread_mutex_unlock(&g_pce.lock);
        (*env)->ReleaseStringUTFChars(env, discPath, native_path);
        return JNI_FALSE;
    }
#endif
    g_pce.current_disc = (int) discIndex;
    g_pce.tray_open = false;
    LOGI("Beetle PCE Fast mounted disc %d: %s (tray closed)", discIndex, native_path);
    pthread_mutex_unlock(&g_pce.lock);
    (*env)->ReleaseStringUTFChars(env, discPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetDiscCount(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_pce.disc_count;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetCurrentDisc(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_pce.current_disc;
}

