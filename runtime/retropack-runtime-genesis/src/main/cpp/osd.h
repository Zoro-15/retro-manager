#ifndef _OSD_H_
#define _OSD_H_

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <zlib.h>

#define MAX_INPUTS 8
#define MAX_KEYS 8
#define MAXPATHLEN 1024

#ifndef TRUE
#define TRUE 1
#endif

#ifndef FALSE
#define FALSE 0
#endif

#define GG_ROM      "./ggenie.bin"
#define AR_ROM      "./areplay.bin"
#define SK_ROM      "./sk.bin"
#define SK_UPMEM    "./sk2chip.bin"
#define CD_BIOS_US  "./bios_CD_U.bin"
#define CD_BIOS_EU  "./bios_CD_E.bin"
#define CD_BIOS_JP  "./bios_CD_J.bin"
#define MD_BIOS     "./bios_MD.bin"
#define MS_BIOS_US  "./bios_U.sms"
#define MS_BIOS_EU  "./bios_E.sms"
#define MS_BIOS_JP  "./bios_J.sms"
#define GG_BIOS     "./bios.gg"

typedef struct
{
  uint8 padtype;
} t_input_config;

typedef struct
{
  uint8 hq_fm;
  uint8 filter;
  uint8 hq_psg;
  uint8 ym2612;
  uint8 ym2413;
  uint8 ym3438;
  uint8 opll;
  uint8 cd_latency;
  int16 psg_preamp;
  int16 fm_preamp;
  int16 cdda_volume;
  int16 pcm_volume;
  uint32 lp_range;
  int16 low_freq;
  int16 high_freq;
  int16 lg;
  int16 mg;
  int16 hg;
  uint8 mono;
  uint8 system;
  uint8 region_detect;
  uint8 vdp_mode;
  uint8 master_clock;
  uint8 force_dtack;
  uint8 addr_error;
  uint8 bios;
  uint8 lock_on;
  uint8 add_on;
  uint8 hot_swap;
  uint8 invert_mouse;
  uint8 gun_cursor[2];
  uint8 overscan;
  uint8 gg_extra;
  uint8 ntsc;
  uint8 lcd;
  uint8 render;
  uint8 enhanced_vscroll;
  uint8 enhanced_vscroll_limit;
  t_input_config input[MAX_INPUTS];
} t_config;

extern t_config config;
void set_config_defaults(void);
void osd_input_update(void);
int load_archive(char *filename, unsigned char *buffer, int maxsize, char *extension);
void error(char *format, ...);

#endif /* _OSD_H_ */
