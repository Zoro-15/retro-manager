#ifndef RETROPACK_RINGBUFFER_H
#define RETROPACK_RINGBUFFER_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct RingBuffer RingBuffer;

/**
 * Creates a lock-free Single-Producer Single-Consumer (SPSC) circular ring buffer
 * with the given capacity in 16-bit audio samples.
 *
 * The requested capacity is rounded up to the next power of two so that the
 * hot paths can index with a bitwise mask instead of an integer division.
 * The effective capacity (>= requested) is returned by ringbuffer_capacity().
 *
 * Thread-safety contract (issue #43):
 *  - Exactly ONE thread may call ringbuffer_write() (the producer, e.g. the
 *    emulation thread's libretro audio callbacks).
 *  - Exactly ONE thread may call ringbuffer_read*() (the consumer, e.g. the
 *    AAudio onAudioReady callback thread).
 *  - ringbuffer_available() and ringbuffer_capacity() are safe from either side.
 *  - ringbuffer_reset() must only be called while BOTH sides are quiescent
 *    (e.g. while the audio stream is stopped and the emulation loop halted).
 *
 * @param capacity Maximum number of 16-bit audio samples the buffer can hold.
 * @return Pointer to the allocated RingBuffer instance, or NULL on allocation failure.
 */
RingBuffer* ringbuffer_create(size_t capacity);

/**
 * Destroys the ring buffer, releasing its allocated data.
 * Both producer and consumer must be quiescent when this is called.
 *
 * @param rb RingBuffer instance to destroy (safe to pass NULL).
 */
void ringbuffer_destroy(RingBuffer* rb);

/**
 * Clears all buffered samples and resets read and write counters to zero.
 * Must only be called while both producer and consumer are quiescent.
 *
 * @param rb RingBuffer instance to reset.
 */
void ringbuffer_reset(RingBuffer* rb);

/**
 * Writes up to 'count' int16_t samples to the ring buffer (lock-free).
 *
 * If the remaining capacity cannot hold the whole chunk, only the prefix that
 * fits is written and the newest overflow is dropped. Latency bounding is the
 * consumer's responsibility: use ringbuffer_read_with_limit() to skip a
 * growing backlog from the consumer side, which is always race-free because
 * the consumer owns the tail counter.
 *
 * @param rb RingBuffer instance.
 * @param data Source buffer of 16-bit samples.
 * @param count Number of samples to write.
 * @return Number of samples actually written (may be a partial write).
 */
size_t ringbuffer_write(RingBuffer* rb, const int16_t* data, size_t count);

/**
 * Reads up to 'max_count' int16_t samples from the ring buffer into 'out_data'
 * (lock-free).
 *
 * @param rb RingBuffer instance.
 * @param out_data Destination buffer for audio samples.
 * @param max_count Maximum number of samples to read.
 * @return Actual number of samples read into out_data.
 */
size_t ringbuffer_read(RingBuffer* rb, int16_t* out_data, size_t max_count);

/**
 * Reads up to 'max_count' samples while bounding the retained backlog
 * (lock-free, consumer-side drift compensation).
 *
 * If the buffer holds more than 'backlog_limit' readable samples, the oldest
 * (size - backlog_limit) samples are skipped first, then up to 'max_count'
 * samples are read. Skipping only ever advances the consumer-owned tail, so
 * this path introduces no data race with the producer.
 *
 * @param rb RingBuffer instance.
 * @param out_data Destination buffer for audio samples.
 * @param max_count Maximum number of samples to read.
 * @param backlog_limit Maximum backlog (in samples) tolerated before the
 *                      oldest samples are discarded to bound latency.
 * @return Actual number of samples read into out_data.
 */
size_t ringbuffer_read_with_limit(RingBuffer* rb, int16_t* out_data, size_t max_count, size_t backlog_limit);

/**
 * Returns the current number of readable samples in the ring buffer.
 *
 * @param rb RingBuffer instance.
 * @return Number of samples available to read.
 */
size_t ringbuffer_available(RingBuffer* rb);

/**
 * Returns the total sample capacity of the ring buffer.
 *
 * @param rb RingBuffer instance.
 * @return Total capacity in samples (rounded up to a power of two).
 */
size_t ringbuffer_capacity(const RingBuffer* rb);

#ifdef __cplusplus
}
#endif

#endif /* RETROPACK_RINGBUFFER_H */
