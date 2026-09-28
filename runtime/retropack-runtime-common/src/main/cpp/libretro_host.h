#ifndef RETROPACK_LIBRETRO_HOST_H
#define RETROPACK_LIBRETRO_HOST_H

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#include <pthread.h>
#include "libretro.h"
#include "ringbuffer.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Libretro core dynamic dispatch table resolved via dlopen/dlsym.
 */
struct LibretroCore {
    void* handle;
    void (*retro_init)(void);
    void (*retro_deinit)(void);
    unsigned (*retro_api_version)(void);
    void (*retro_get_system_info)(struct retro_system_info *info);
    void (*retro_get_system_av_info)(struct retro_system_av_info *info);
    void (*retro_set_environment)(retro_environment_t);
    void (*retro_set_video_refresh)(retro_video_refresh_t);
    void (*retro_set_audio_sample)(retro_audio_sample_t);
    void (*retro_set_audio_sample_batch)(retro_audio_sample_batch_t);
    void (*retro_set_input_poll)(retro_input_poll_t);
    void (*retro_set_input_state)(retro_input_state_t);
    void (*retro_set_controller_port_device)(unsigned port, unsigned device);
    void (*retro_reset)(void);
    void (*retro_run)(void);
    size_t (*retro_serialize_size)(void);
    bool (*retro_serialize)(void *data, size_t size);
    bool (*retro_unserialize)(const void *data, size_t size);
    bool (*retro_load_game)(const struct retro_game_info *game);
    void (*retro_unload_game)(void);
    unsigned (*retro_get_region)(void);
    void *(*retro_get_memory_data)(unsigned id);
    size_t (*retro_get_memory_size)(unsigned id);
};

/**
 * Libretro host state holding runtime context, buffers, and active core dispatch.
 */
typedef struct LibretroHostState {
    struct LibretroCore core;
    struct retro_system_info system_info;
    struct retro_system_av_info av_info;

    bool core_loaded;
    bool game_loaded;
    bool initialized;

    char system_dir[1024];
    char save_dir[1024];
    char loaded_core_path[1024];

    // Video pipeline
    enum retro_pixel_format pixel_format;
    uint32_t* video_buffer;
    size_t video_buffer_capacity;
    unsigned video_width;
    unsigned video_height;
    unsigned base_width;
    unsigned base_height;
    unsigned max_width;
    unsigned max_height;
    float aspect_ratio;
    double target_fps;
    double sample_rate;

    // Audio pipeline
    RingBuffer* audio_rb;

    // Input state
    uint32_t input_mask;
    int16_t analog_left_x;
    int16_t analog_left_y;
    int16_t analog_right_x;
    int16_t analog_right_y;
    int16_t pointer_x;
    int16_t pointer_y;
    bool pointer_pressed;

    // Concurrency control
    pthread_mutex_t lock;

    // Loaded ROM memory buffer (if core does not use fullpath)
    void* rom_data;
    size_t rom_size;
} LibretroHostState;

/**
 * Global host instance access.
 */
LibretroHostState* host_get_instance(void);

/**
 * Core lifecycle & host initialization API.
 */
bool host_init(const char* system_dir, const char* save_dir);
void host_destroy(void);

bool host_load_core(const char* core_path);
void host_unload_core(void);

bool host_load_game(const char* rom_path);
void host_unload_game(void);

void host_reset(void);
bool host_run_frame(void);

/**
 * Input pipeline API.
 */
void host_set_keys(uint32_t key_mask);
void host_set_analog(float left_x, float left_y, float right_x, float right_y);
void host_set_pointer(int16_t x, int16_t y, bool pressed);

/**
 * Video & Audio pipeline API.
 */
uint32_t* host_get_video_buffer(unsigned* out_width, unsigned* out_height);
size_t host_get_audio_samples(int16_t* out_samples, size_t max_samples);
size_t host_get_audio_available(void);

/**
 * Durability & Saves API.
 */
size_t host_get_sram_size(void);
bool host_read_sram(uint8_t* out_buffer, size_t max_size);
bool host_write_sram(const uint8_t* in_buffer, size_t size);

bool host_save_state(const char* file_path);
bool host_load_state(const char* file_path);

#ifdef __cplusplus
}
#endif

#endif /* RETROPACK_LIBRETRO_HOST_H */
