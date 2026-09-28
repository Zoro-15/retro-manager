#include "zstd.h"
#include <stdlib.h>
#include <string.h>
#include <zlib.h>

struct ZSTD_CCtx_s {
    int level;
};

struct ZSTD_DCtx_s {
    int placeholder;
};

ZSTD_CCtx* ZSTD_createCCtx(void) {
    return (ZSTD_CCtx*)calloc(1, sizeof(ZSTD_CCtx));
}

void ZSTD_freeCCtx(ZSTD_CCtx* cctx) {
    if (cctx) free(cctx);
}

size_t ZSTD_CCtx_setParameter(ZSTD_CCtx* cctx, ZSTD_cParameter param, int value) {
    if (cctx && param == ZSTD_c_compressionLevel) cctx->level = value;
    return 0;
}

size_t ZSTD_CCtx_setPledgedSrcSize(ZSTD_CCtx* cctx, unsigned long long pledgedSrcSize) {
    (void)cctx; (void)pledgedSrcSize;
    return 0;
}

size_t ZSTD_compress2(ZSTD_CCtx* cctx, void* dst, size_t dstCapacity, const void* src, size_t srcSize) {
    (void)cctx;
    uLongf destLen = (uLongf)dstCapacity;
    if (compress((Bytef*)dst, &destLen, (const Bytef*)src, (uLong)srcSize) == Z_OK) {
        return (size_t)destLen;
    }
    return (size_t)-1;
}

size_t ZSTD_decompress(void* dst, size_t dstCapacity, const void* src, size_t compressedSize) {
    uLongf destLen = (uLongf)dstCapacity;
    if (uncompress((Bytef*)dst, &destLen, (const Bytef*)src, (uLong)compressedSize) == Z_OK) {
        return (size_t)destLen;
    }
    return (size_t)-1;
}
