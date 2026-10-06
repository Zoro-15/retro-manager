#ifndef RETROPACK_AAUDIO_PLAYER_HPP
#define RETROPACK_AAUDIO_PLAYER_HPP

#include <aaudio/AAudio.h>
#include <memory>
#include <atomic>
#include <cstdint>
#include <cstddef>

#include "ring_buffer.hpp"

namespace retropack {

/**
 * AAudioPlayer provides an ultra-low latency, hardware-exclusive AAudio stream
 * fed continuously by a lock-free SPSC circular ring buffer.
 */
class AAudioPlayer {
public:
    AAudioPlayer();
    ~AAudioPlayer();

    // Non-copyable and non-movable
    AAudioPlayer(const AAudioPlayer&) = delete;
    AAudioPlayer& operator=(const AAudioPlayer&) = delete;

    /**
     * Initialize the audio subsystem and ring buffer.
     * @param sampleRate Core sample rate (e.g. 44100 or 48000 Hz).
     * @param channelCount Number of audio channels (default: 2 for stereo).
     * @param ringBufferCapacity Sample capacity for SPSC ring buffer.
     * @return true if stream builder succeeded.
     */
    bool init(int sampleRate, int channelCount = 2, size_t ringBufferCapacity = 32768);

    /**
     * Terminate the audio stream and free ring buffer.
     */
    void destroy();

    /**
     * Start/Resume audio stream playback.
     */
    bool start();

    /**
     * Pause audio stream playback.
     */
    bool pause();

    /**
     * Stop audio stream playback.
     */
    bool stop();

    /**
     * Write interleaved stereo PCM audio samples into the ring buffer.
     * Called directly from the Libretro core's retro_audio_sample_batch callback.
     * @param data Pointer to interleaved 16-bit PCM samples.
     * @param frames Number of stereo frames (total samples = frames * channelCount).
     * @return Number of frames accepted.
     */
    size_t writeSamples(const int16_t* data, size_t frames);

    /**
     * Status queries.
     */
    bool isRunning() const { return m_running.load(std::memory_order_relaxed); }
    int getSampleRate() const { return m_sampleRate; }
    int getChannelCount() const { return m_channelCount; }

private:
    bool openStream();
    void closeStream();

    static aaudio_data_callback_result_t dataCallback(
        AAudioStream *stream,
        void *userData,
        void *audioData,
        int32_t numFrames
    );

    static void errorCallback(
        AAudioStream *stream,
        void *userData,
        aaudio_result_t error
    );

    AAudioStream* m_stream{nullptr};
    std::unique_ptr<AudioRingBuffer> m_ringBuffer;

    std::atomic<bool> m_running{false};
    int m_sampleRate{44100};
    int m_channelCount{2};
    size_t m_ringBufferCapacity{32768};
};

} // namespace retropack

#endif // RETROPACK_AAUDIO_PLAYER_HPP
