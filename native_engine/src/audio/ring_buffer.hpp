#ifndef RETROPACK_RING_BUFFER_HPP
#define RETROPACK_RING_BUFFER_HPP

#include <vector>
#include <atomic>
#include <cstdint>
#include <cstddef>
#include <algorithm>
#include <cstring>

namespace retropack {

/**
 * Lock-Free Single-Producer Single-Consumer (SPSC) Circular Ring Buffer.
 * Designed specifically for low-latency PCM audio streaming without mutex contention.
 */
template <typename T>
class SpscRingBuffer {
public:
    explicit SpscRingBuffer(size_t capacity = 32768)
        : m_capacity(nextPowerOfTwo(capacity)),
          m_mask(m_capacity - 1),
          m_buffer(m_capacity),
          m_head(0),
          m_tail(0) {}

    ~SpscRingBuffer() = default;

    // Non-copyable and non-movable for concurrency safety
    SpscRingBuffer(const SpscRingBuffer&) = delete;
    SpscRingBuffer& operator=(const SpscRingBuffer&) = delete;

    /**
     * Write items into the ring buffer (Producer thread).
     * @param data Pointer to input data array.
     * @param count Number of elements to write.
     * @return Number of elements successfully written.
     */
    size_t write(const T* data, size_t count) {
        if (!data || count == 0) return 0;

        const size_t currentTail = m_tail.load(std::memory_order_relaxed);
        const size_t currentHead = m_head.load(std::memory_order_acquire);

        const size_t occupied = currentTail - currentHead;
        const size_t available = m_capacity - occupied;
        const size_t toWrite = std::min(count, available);

        if (toWrite == 0) {
            return 0;
        }

        const size_t writeIndex = currentTail & m_mask;
        const size_t firstChunk = std::min(toWrite, m_capacity - writeIndex);
        const size_t secondChunk = toWrite - firstChunk;

        std::memcpy(&m_buffer[writeIndex], data, firstChunk * sizeof(T));
        if (secondChunk > 0) {
            std::memcpy(&m_buffer[0], data + firstChunk, secondChunk * sizeof(T));
        }

        m_tail.store(currentTail + toWrite, std::memory_order_release);
        return toWrite;
    }

    /**
     * Read items from the ring buffer (Consumer thread).
     * @param destination Pointer to output buffer.
     * @param count Number of elements to read.
     * @return Number of elements actually read.
     */
    size_t read(T* destination, size_t count) {
        if (!destination || count == 0) return 0;

        const size_t currentHead = m_head.load(std::memory_order_relaxed);
        const size_t currentTail = m_tail.load(std::memory_order_acquire);

        const size_t available = currentTail - currentHead;
        const size_t toRead = std::min(count, available);

        if (toRead == 0) {
            return 0;
        }

        const size_t readIndex = currentHead & m_mask;
        const size_t firstChunk = std::min(toRead, m_capacity - readIndex);
        const size_t secondChunk = toRead - firstChunk;

        std::memcpy(destination, &m_buffer[readIndex], firstChunk * sizeof(T));
        if (secondChunk > 0) {
            std::memcpy(destination + firstChunk, &m_buffer[0], secondChunk * sizeof(T));
        }

        m_head.store(currentHead + toRead, std::memory_order_release);
        return toRead;
    }

    /**
     * Get number of elements available to read.
     */
    size_t availableRead() const {
        const size_t head = m_head.load(std::memory_order_relaxed);
        const size_t tail = m_tail.load(std::memory_order_acquire);
        return tail - head;
    }

    /**
     * Get number of elements available to write.
     */
    size_t availableWrite() const {
        const size_t head = m_head.load(std::memory_order_acquire);
        const size_t tail = m_tail.load(std::memory_order_relaxed);
        return m_capacity - (tail - head);
    }

    /**
     * Reset buffer to empty state.
     */
    void clear() {
        m_head.store(0, std::memory_order_relaxed);
        m_tail.store(0, std::memory_order_relaxed);
    }

    size_t capacity() const {
        return m_capacity;
    }

private:
    static size_t nextPowerOfTwo(size_t v) {
        if (v == 0) return 1;
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v |= v >> 32;
        return v + 1;
    }

    const size_t m_capacity;
    const size_t m_mask;
    std::vector<T> m_buffer;

    // Cacheline alignment padding to prevent false sharing between producer and consumer
    alignas(64) std::atomic<size_t> m_head{0};
    alignas(64) std::atomic<size_t> m_tail{0};
};

using AudioRingBuffer = SpscRingBuffer<int16_t>;

} // namespace retropack

#endif // RETROPACK_RING_BUFFER_HPP
