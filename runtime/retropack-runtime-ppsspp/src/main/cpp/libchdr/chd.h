#pragma once
#ifndef LIBCHDR_CHD_H
#define LIBCHDR_CHD_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct chd_file chd_file;
typedef int chd_error;

#define CHDERR_NONE 0
#define CHDERR_NO_INTERFACE 1
#define CHDERR_OUT_OF_MEMORY 2
#define CHDERR_INVALID_FILE 3
#define CHDERR_INVALID_PARAMETER 4
#define CHDERR_INVALID_DATA 5
#define CHDERR_FILE_NOT_FOUND 6
#define CHDERR_REQUIRES_PARENT 7
#define CHDERR_FILE_NOT_WRITEABLE 8
#define CHDERR_READ_ERROR 9
#define CHDERR_WRITE_ERROR 10
#define CHDERR_CODEC_ERROR 11
#define CHDERR_INVALID_PARENT 12
#define CHDERR_HUNK_OUT_OF_RANGE 13
#define CHDERR_DECOMPRESSION_ERROR 14
#define CHDERR_COMPRESSION_ERROR 15
#define CHDERR_CANT_CREATE_FILE 16
#define CHDERR_CANT_VERIFY 17
#define CHDERR_NOT_SUPPORTED 18
#define CHDERR_NOT_OPEN 19
#define CHDERR_ALREADY_OPEN 20
#define CHDERR_INVALID_METADATA 21
#define CHDERR_METADATA_NOT_FOUND 22
#define CHDERR_UNSUPPORTED_VERSION 23
#define CHDERR_VERIFY_INCOMPLETE 24
#define CHDERR_INVALID_CHUNK 25
#define CHDERR_DECOMPRESS_ALLOC_FAIL 26

#define CHD_OPEN_READ 1
#define CHD_OPEN_READWRITE 2

typedef struct chd_core_file {
    void *argp;
    uint64_t (*fsize)(struct chd_core_file*);
    int (*fseek)(struct chd_core_file*, int64_t offset, int seekType);
    size_t (*fread)(void *out_data, size_t size, size_t count, struct chd_core_file*);
    int (*fclose)(struct chd_core_file*);
} core_file;

typedef struct chd_header {
    uint32_t length;
    uint32_t version;
    uint32_t flags;
    uint32_t compression[4];
    uint32_t hunkbytes;
    uint32_t totalhunks;
    uint64_t logicalbytes;
    uint64_t metaoffset;
    uint8_t  md5[16];
    uint8_t  parentmd5[16];
    uint8_t  sha1[20];
    uint8_t  parentsha1[20];
    uint32_t unitbytes;
    uint64_t unitcount;
    uint32_t hunkcount;
} chd_header;

struct chd_file {
    chd_header header;
    core_file *core;
};

static inline const char *chd_error_string(chd_error err) {
    switch (err) {
        case CHDERR_NONE: return "no error";
        case CHDERR_NO_INTERFACE: return "no interface";
        case CHDERR_OUT_OF_MEMORY: return "out of memory";
        case CHDERR_INVALID_FILE: return "invalid file";
        case CHDERR_INVALID_PARAMETER: return "invalid parameter";
        case CHDERR_INVALID_DATA: return "invalid data";
        case CHDERR_FILE_NOT_FOUND: return "file not found";
        case CHDERR_REQUIRES_PARENT: return "requires parent";
        case CHDERR_FILE_NOT_WRITEABLE: return "file not writeable";
        case CHDERR_READ_ERROR: return "read error";
        case CHDERR_WRITE_ERROR: return "write error";
        case CHDERR_CODEC_ERROR: return "codec error";
        case CHDERR_INVALID_PARENT: return "invalid parent";
        case CHDERR_HUNK_OUT_OF_RANGE: return "hunk out of range";
        case CHDERR_DECOMPRESSION_ERROR: return "decompression error";
        case CHDERR_COMPRESSION_ERROR: return "compression error";
        case CHDERR_CANT_CREATE_FILE: return "can't create file";
        case CHDERR_CANT_VERIFY: return "can't verify";
        case CHDERR_NOT_SUPPORTED: return "not supported";
        case CHDERR_NOT_OPEN: return "not open";
        case CHDERR_ALREADY_OPEN: return "already open";
        case CHDERR_INVALID_METADATA: return "invalid metadata";
        case CHDERR_METADATA_NOT_FOUND: return "metadata not found";
        case CHDERR_UNSUPPORTED_VERSION: return "unsupported version";
        default: return "unknown error";
    }
}

static inline chd_error chd_open_core_file(core_file *file, int mode, chd_file *parent, chd_file **chd) {
    (void)file;
    (void)mode;
    (void)parent;
    if (chd) {
        *chd = NULL;
    }
    return CHDERR_UNSUPPORTED_VERSION;
}

static inline const chd_header *chd_get_header(chd_file *chd) {
    if (chd) {
        return &chd->header;
    }
    return NULL;
}

static inline void chd_close(chd_file *chd) {
    if (chd) {
        // cleanup if allocated
    }
}

static inline chd_error chd_read(chd_file *chd, uint32_t hunknum, void *buffer) {
    (void)chd;
    (void)hunknum;
    (void)buffer;
    return CHDERR_READ_ERROR;
}

#ifdef __cplusplus
}
#endif

#endif // LIBCHDR_CHD_H
