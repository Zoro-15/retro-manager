#ifndef ZSTD_H
#define ZSTD_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ZSTD_CCtx_s ZSTD_CCtx;
typedef struct ZSTD_DCtx_s ZSTD_DCtx;

typedef enum {
    ZSTD_c_compressionLevel = 100,
    ZSTD_c_checksumFlag = 201,
} ZSTD_cParameter;

#define ZSTD_CLEVEL_DEFAULT 3

static inline size_t ZSTD_compressBound(size_t srcSize) { return srcSize + 512; }
static inline unsigned ZSTD_isError(size_t code) { return code == (size_t)-1; }

ZSTD_CCtx* ZSTD_createCCtx(void);
void ZSTD_freeCCtx(ZSTD_CCtx* cctx);
size_t ZSTD_CCtx_setParameter(ZSTD_CCtx* cctx, ZSTD_cParameter param, int value);
size_t ZSTD_CCtx_setPledgedSrcSize(ZSTD_CCtx* cctx, unsigned long long pledgedSrcSize);
size_t ZSTD_compress2(ZSTD_CCtx* cctx, void* dst, size_t dstCapacity, const void* src, size_t srcSize);
size_t ZSTD_decompress(void* dst, size_t dstCapacity, const void* src, size_t compressedSize);

#ifdef __cplusplus
}
#endif

#endif // ZSTD_H
