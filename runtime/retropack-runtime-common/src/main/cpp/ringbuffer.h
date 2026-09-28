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
 * Creates a thread-safe circular ring buffer with the given capacity in 16-bit audio samples.
 *
 * @param capacity Maximum number of 16-bit audio samples the buffer can hold.
 * @return Pointer to the allocated RingBuffer instance, or NULL on allocation failure.
 */
RingBuffer* ringbuffer_create(size_t capacity);

/**
 * Destroys the ring buffer, releasing its allocated data and synchronizing mutex.
 *
 * @param rb RingBuffer instance to destroy (safe to pass NULL).
 */
void ringbuffer_destroy(RingBuffer* rb);

/**
 * Clears all buffered samples and resets read and write pointers to zero.
 *
 * @param rb RingBuffer instance to reset.
 */
void ringbuffer_reset(RingBuffer* rb);

/**
 * Writes up to 'count' int16_t samples to the ring buffer.
 * If incoming samples would overflow remaining capacity, the oldest unread samples
 * are discarded (drift compensation) to maintain low audio latency and eliminate backlog lag.
 *
 * @param rb RingBuffer instance.
 * @param data Source buffer of 16-bit samples.
 * @param count Number of samples to write.
 * @return Number of samples written.
 */
size_t ringbuffer_write(RingBuffer* rb, const int16_t* data, size_t count);

/**
 * Reads up to 'max_count' int16_t samples from the ring buffer into 'out_data'.
 *
 * @param rb RingBuffer instance.
 * @param out_data Destination buffer for audio samples.
 * @param max_count Maximum number of samples to read.
 * @return Actual number of samples read into out_data.
 */
size_t ringbuffer_read(RingBuffer* rb, int16_t* out_data, size_t max_count);

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
 * @return Total capacity in samples.
 */
size_t ringbuffer_capacity(const RingBuffer* rb);

#ifdef __cplusplus
}
#endif

#endif /* RETROPACK_RINGBUFFER_H */
