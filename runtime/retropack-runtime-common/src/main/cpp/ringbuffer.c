#include "ringbuffer.h"

#include <stdlib.h>
#include <string.h>
#include <stdatomic.h>

/*
 * Lock-free Single-Producer Single-Consumer audio ring buffer (issue #43).
 *
 * Design:
 *  - capacity is rounded up to a power of two; all indexing uses a bitmask
 *    instead of a modulo division.
 *  - head and tail are free-running monotonic counters stored in C11 atomics.
 *    size = head - tail is always exact (until 2^64 samples wrap, which is
 *    effectively never for a 44.1 kHz stream).
 *  - The producer owns head: it writes payload bytes first, then publishes
 *    them with a release store of head. The consumer owns tail: it reads the
 *    published payload, then releases it with a release store of tail.
 *  - acquire loads on the opposite counter synchronize the release store,
 *    giving the same happens-before edge the old pthread_mutex version
 *    provided, with zero syscall or futex traffic on the fast path.
 *
 * Overflow policy:
 *  - The producer never mutates the consumer-owned tail, so it cannot safely
 *    discard the OLDEST samples; when full, it performs a partial write and
 *    drops the newest overflow instead.
 *  - Latency bounding (the old drop-oldest behaviour) is provided on the
 *    consumer side by ringbuffer_read_with_limit(): the consumer skipping its
 *    own backlog is always race-free.
 */

struct RingBuffer {
    int16_t* data;
    size_t capacity;          /* power of two */
    size_t capacity_mask;     /* capacity - 1 */
    _Atomic size_t head;      /* producer-owned: total samples written */
    _Atomic size_t tail;      /* consumer-owned: total samples consumed */
};

static size_t next_power_of_two(size_t v) {
    if (v == 0) {
        return 1;
    }
    size_t p = 1;
    while (p < v) {
        p <<= 1;
    }
    return p;
}

RingBuffer* ringbuffer_create(size_t capacity) {
    if (capacity == 0) {
        return NULL;
    }
    RingBuffer* rb = (RingBuffer*) calloc(1, sizeof(RingBuffer));
    if (!rb) {
        return NULL;
    }
    rb->capacity = next_power_of_two(capacity);
    rb->capacity_mask = rb->capacity - 1;
    rb->data = (int16_t*) malloc(rb->capacity * sizeof(int16_t));
    if (!rb->data) {
        free(rb);
        return NULL;
    }
    atomic_init(&rb->head, (size_t) 0);
    atomic_init(&rb->tail, (size_t) 0);
    return rb;
}

void ringbuffer_destroy(RingBuffer* rb) {
    if (!rb) {
        return;
    }
    free(rb->data);
    free(rb);
}

void ringbuffer_reset(RingBuffer* rb) {
    if (!rb) {
        return;
    }
    /* Contract: producer and consumer are quiescent (audio stream stopped and
     * emulation loop halted). Plain stores suffice under that guarantee; the
     * release ordering additionally makes any resumed consumer observe the
     * cleared state. */
    atomic_store_explicit(&rb->head, (size_t) 0, memory_order_release);
    atomic_store_explicit(&rb->tail, (size_t) 0, memory_order_release);
}

size_t ringbuffer_write(RingBuffer* rb, const int16_t* data, size_t count) {
    if (!rb || !data || count == 0) {
        return 0;
    }

    const size_t head = atomic_load_explicit(&rb->head, memory_order_relaxed);
    const size_t tail = atomic_load_explicit(&rb->tail, memory_order_acquire);
    const size_t size = head - tail;
    const size_t free_space = rb->capacity - size;

    const size_t to_write = count < free_space ? count : free_space;
    if (to_write == 0) {
        return 0;
    }

    /* Bulk copy with at most one wrap split instead of per-sample modulo
     * (issue #12: 44.1kHz stereo paid a division per sample). */
    const size_t pos = head & rb->capacity_mask;
    const size_t first = (rb->capacity - pos) < to_write ? (rb->capacity - pos) : to_write;
    memcpy(rb->data + pos, data, first * sizeof(int16_t));
    const size_t second = to_write - first;
    if (second > 0) {
        memcpy(rb->data, data + first, second * sizeof(int16_t));
    }

    /* Publish the payload to the consumer. */
    atomic_store_explicit(&rb->head, head + to_write, memory_order_release);
    return to_write;
}

size_t ringbuffer_read(RingBuffer* rb, int16_t* out_data, size_t max_count) {
    if (!rb || !out_data || max_count == 0) {
        return 0;
    }

    const size_t tail = atomic_load_explicit(&rb->tail, memory_order_relaxed);
    const size_t head = atomic_load_explicit(&rb->head, memory_order_acquire);
    const size_t size = head - tail;

    const size_t to_read = max_count < size ? max_count : size;
    if (to_read == 0) {
        return 0;
    }

    const size_t pos = tail & rb->capacity_mask;
    const size_t first = (rb->capacity - pos) < to_read ? (rb->capacity - pos) : to_read;
    memcpy(out_data, rb->data + pos, first * sizeof(int16_t));
    const size_t second = to_read - first;
    if (second > 0) {
        memcpy(out_data + first, rb->data, second * sizeof(int16_t));
    }

    /* Release the payload slots back to the producer. */
    atomic_store_explicit(&rb->tail, tail + to_read, memory_order_release);
    return to_read;
}

size_t ringbuffer_read_with_limit(RingBuffer* rb, int16_t* out_data, size_t max_count, size_t backlog_limit) {
    if (!rb || !out_data || max_count == 0) {
        return 0;
    }

    const size_t tail = atomic_load_explicit(&rb->tail, memory_order_relaxed);
    const size_t head = atomic_load_explicit(&rb->head, memory_order_acquire);
    const size_t size = head - tail;

    /* Consumer-side drop-oldest: skip the stale prefix to bound latency.
     * Only the consumer mutates the tail, so this is always race-free. */
    if (size > backlog_limit) {
        const size_t skip = size - backlog_limit;
        atomic_store_explicit(&rb->tail, tail + skip, memory_order_release);
    }

    return ringbuffer_read(rb, out_data, max_count);
}

size_t ringbuffer_available(RingBuffer* rb) {
    if (!rb) {
        return 0;
    }
    const size_t head = atomic_load_explicit(&rb->head, memory_order_acquire);
    const size_t tail = atomic_load_explicit(&rb->tail, memory_order_acquire);
    return head - tail;
}

size_t ringbuffer_capacity(const RingBuffer* rb) {
    return rb ? rb->capacity : 0;
}
