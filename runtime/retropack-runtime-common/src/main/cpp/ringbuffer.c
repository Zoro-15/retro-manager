#include "ringbuffer.h"

#include <stdlib.h>
#include <string.h>
#include <pthread.h>

struct RingBuffer {
    int16_t* data;
    size_t capacity;
    size_t head;
    size_t tail;
    size_t size;
    pthread_mutex_t lock;
};

RingBuffer* ringbuffer_create(size_t capacity) {
    if (capacity == 0) {
        return NULL;
    }
    RingBuffer* rb = (RingBuffer*) calloc(1, sizeof(RingBuffer));
    if (!rb) {
        return NULL;
    }
    rb->data = (int16_t*) malloc(capacity * sizeof(int16_t));
    if (!rb->data) {
        free(rb);
        return NULL;
    }
    rb->capacity = capacity;
    rb->head = 0;
    rb->tail = 0;
    rb->size = 0;
    pthread_mutex_init(&rb->lock, NULL);
    return rb;
}

void ringbuffer_destroy(RingBuffer* rb) {
    if (!rb) {
        return;
    }
    pthread_mutex_destroy(&rb->lock);
    free(rb->data);
    free(rb);
}

void ringbuffer_reset(RingBuffer* rb) {
    if (!rb) {
        return;
    }
    pthread_mutex_lock(&rb->lock);
    rb->head = 0;
    rb->tail = 0;
    rb->size = 0;
    pthread_mutex_unlock(&rb->lock);
}

size_t ringbuffer_write(RingBuffer* rb, const int16_t* data, size_t count) {
    if (!rb || !data || count == 0) {
        return 0;
    }
    pthread_mutex_lock(&rb->lock);

    if (count > rb->capacity) {
        data += (count - rb->capacity);
        count = rb->capacity;
    }

    size_t overflow = (rb->size + count > rb->capacity) ? (rb->size + count - rb->capacity) : 0;
    if (overflow > 0) {
        rb->tail = (rb->tail + overflow) % rb->capacity;
        rb->size -= overflow;
    }

    // Bulk copy with at most one wrap split instead of per-sample modulo
    // (issue #12: 44.1kHz stereo paid a division per sample).
    size_t first = rb->capacity - rb->head;
    if (first > count) {
        first = count;
    }
    memcpy(rb->data + rb->head, data, first * sizeof(int16_t));
    size_t second = count - first;
    if (second > 0) {
        memcpy(rb->data, data + first, second * sizeof(int16_t));
    }
    rb->head = (rb->head + count) % rb->capacity;
    rb->size += count;

    pthread_mutex_unlock(&rb->lock);
    return count;
}

size_t ringbuffer_read(RingBuffer* rb, int16_t* out_data, size_t max_count) {
    if (!rb || !out_data || max_count == 0) {
        return 0;
    }
    pthread_mutex_lock(&rb->lock);

    size_t to_read = (max_count < rb->size) ? max_count : rb->size;
    size_t first = rb->capacity - rb->tail;
    if (first > to_read) {
        first = to_read;
    }
    memcpy(out_data, rb->data + rb->tail, first * sizeof(int16_t));
    size_t second = to_read - first;
    if (second > 0) {
        memcpy(out_data + first, rb->data, second * sizeof(int16_t));
    }
    rb->tail = (rb->tail + to_read) % rb->capacity;
    rb->size -= to_read;

    pthread_mutex_unlock(&rb->lock);
    return to_read;
}

size_t ringbuffer_available(RingBuffer* rb) {
    if (!rb) {
        return 0;
    }
    pthread_mutex_lock(&rb->lock);
    size_t avail = rb->size;
    pthread_mutex_unlock(&rb->lock);
    return avail;
}

size_t ringbuffer_capacity(const RingBuffer* rb) {
    return rb ? rb->capacity : 0;
}
