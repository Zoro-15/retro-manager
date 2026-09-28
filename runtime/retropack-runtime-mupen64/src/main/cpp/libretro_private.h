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

#ifdef __cplusplus
}
#endif

#endif /* _LIBRETRO_PRIVATE_H_ */
