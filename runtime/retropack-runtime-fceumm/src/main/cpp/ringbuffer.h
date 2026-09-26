#ifndef RETROPACK_RINGBUFFER_H
#define RETROPACK_RINGBUFFER_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct RingBuffer RingBuffer;

RingBuffer* ringbuffer_create(size_t capacity);
void ringbuffer_destroy(RingBuffer* rb);
void ringbuffer_reset(RingBuffer* rb);
size_t ringbuffer_write(RingBuffer* rb, const int16_t* data, size_t count);
size_t ringbuffer_read(RingBuffer* rb, int16_t* out_data, size_t max_count);
size_t ringbuffer_available(RingBuffer* rb);
size_t ringbuffer_capacity(const RingBuffer* rb);

#ifdef __cplusplus
}
#endif

#endif /* RETROPACK_RINGBUFFER_H */
