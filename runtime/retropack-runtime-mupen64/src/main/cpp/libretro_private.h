#ifndef _LIBRETRO_PRIVATE_H_
#define _LIBRETRO_PRIVATE_H_

#include <stdio.h>
#include <string.h>
#include <stdbool.h>
#include "libretro.h"

#ifdef __cplusplus
extern "C" {
#endif

extern retro_environment_t environ_cb;
extern unsigned int FAKE_SDL_TICKS;
extern retro_log_printf_t log_cb;
extern retro_video_refresh_t video_cb;
extern retro_audio_sample_t audio_cb;
extern retro_audio_sample_batch_t audio_batch_cb;
extern retro_input_poll_t input_poll_cb;
extern retro_input_state_t input_state_cb;
extern retro_perf_get_counter_t perf_get_counter_cb;
extern retro_get_cpu_features_t perf_get_cpu_features_cb;
extern retro_perf_log_t perf_log_cb;
extern retro_perf_register_t perf_register_cb;
extern retro_perf_start_t perf_start_cb;
extern retro_perf_stop_t perf_stop_cb;

void retro_return(void);
uint32_t get_retro_screen_width(void);

#ifdef __cplusplus
}
#endif

#endif /* _LIBRETRO_PRIVATE_H_ */
