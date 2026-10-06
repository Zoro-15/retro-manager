#include "aaudio_player.hpp"

#include <android/log.h>
#include <cstring>

#define LOG_TAG "RetroEngine-Audio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace retropack {

AAudioPlayer::AAudioPlayer() = default;

AAudioPlayer::~AAudioPlayer() {
    destroy();
}

bool AAudioPlayer::init(int sampleRate, int channelCount, size_t ringBufferCapacity) {
    destroy();

    m_sampleRate = (sampleRate > 0) ? sampleRate : 44100;
    m_channelCount = (channelCount > 0) ? channelCount : 2;
    m_ringBufferCapacity = ringBufferCapacity;

    m_ringBuffer = std::make_unique<AudioRingBuffer>(m_ringBufferCapacity);

    LOGI("Initializing AAudio stream: %d Hz, %d channels, buffer capacity: %zu samples",
         m_sampleRate, m_channelCount, m_ringBuffer->capacity());

    return openStream();
}

void AAudioPlayer::destroy() {
    stop();
    closeStream();
    m_ringBuffer.reset();
}

bool AAudioPlayer::openStream() {
    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        LOGE("Failed to create AAudioStreamBuilder: %s", AAudio_convertResultToText(result));
        return false;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, m_channelCount);
    AAudioStreamBuilder_setSampleRate(builder, m_sampleRate);
    AAudioStreamBuilder_setDataCallback(builder, dataCallback, this);
    AAudioStreamBuilder_setErrorCallback(builder, errorCallback, this);

    result = AAudioStreamBuilder_openStream(builder, &m_stream);
    if (result != AAUDIO_OK) {
        LOGW("Failed to open EXCLUSIVE AAudio stream (%s), attempting SHARED mode fallback",
             AAudio_convertResultToText(result));
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        result = AAudioStreamBuilder_openStream(builder, &m_stream);
    }

    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK) {
        LOGE("Failed to open AAudio stream: %s", AAudio_convertResultToText(result));
        m_stream = nullptr;
        return false;
    }

    int32_t burstSize = AAudioStream_getFramesPerBurst(m_stream);
    int32_t actualSampleRate = AAudioStream_getSampleRate(m_stream);
    aaudio_sharing_mode_t sharingMode = AAudioStream_getSharingMode(m_stream);

    // Set hardware buffer size to 2 bursts for optimal low latency
    AAudioStream_setBufferSizeInFrames(m_stream, burstSize * 2);

    LOGI("AAudio stream opened successfully: actual sample rate %d Hz, burst size %d frames, sharing mode: %s",
         actualSampleRate, burstSize,
         (sharingMode == AAUDIO_SHARING_MODE_EXCLUSIVE) ? "EXCLUSIVE" : "SHARED");

    return true;
}

void AAudioPlayer::closeStream() {
    if (m_stream) {
        AAudioStream_close(m_stream);
        m_stream = nullptr;
    }
}

bool AAudioPlayer::start() {
    if (!m_stream) return false;

    aaudio_stream_state_t state = AAudioStream_getState(m_stream);
    if (state == AAUDIO_STREAM_STATE_RUNNING || state == AAUDIO_STREAM_STATE_STARTING) {
        return true;
    }

    aaudio_result_t result = AAudioStream_requestStart(m_stream);
    if (result != AAUDIO_OK) {
        LOGE("AAudioStream_requestStart failed: %s", AAudio_convertResultToText(result));
        return false;
    }

    m_running.store(true, std::memory_order_release);
    LOGI("AAudio stream started");
    return true;
}

bool AAudioPlayer::pause() {
    if (!m_stream) return false;

    m_running.store(false, std::memory_order_release);

    aaudio_stream_state_t state = AAudioStream_getState(m_stream);
    if (state == AAUDIO_STREAM_STATE_PAUSED || state == AAUDIO_STREAM_STATE_PAUSING) {
        return true;
    }

    aaudio_result_t result = AAudioStream_requestPause(m_stream);
    if (result != AAUDIO_OK) {
        LOGW("AAudioStream_requestPause failed: %s", AAudio_convertResultToText(result));
        return false;
    }

    LOGI("AAudio stream paused");
    return true;
}

bool AAudioPlayer::stop() {
    if (!m_stream) return false;

    m_running.store(false, std::memory_order_release);

    aaudio_result_t result = AAudioStream_requestStop(m_stream);
    if (result != AAUDIO_OK) {
        LOGW("AAudioStream_requestStop failed: %s", AAudio_convertResultToText(result));
        return false;
    }

    if (m_ringBuffer) {
        m_ringBuffer->clear();
    }

    LOGI("AAudio stream stopped");
    return true;
}

size_t AAudioPlayer::writeSamples(const int16_t* data, size_t frames) {
    if (!m_ringBuffer || !data || frames == 0) return 0;

    size_t samples = frames * static_cast<size_t>(m_channelCount);
    size_t written = m_ringBuffer->write(data, samples);
    return written / static_cast<size_t>(m_channelCount);
}

aaudio_data_callback_result_t AAudioPlayer::dataCallback(
    AAudioStream* /*stream*/,
    void* userData,
    void* audioData,
    int32_t numFrames
) {
    auto* player = static_cast<AAudioPlayer*>(userData);
    if (!player || !player->m_ringBuffer || numFrames <= 0) {
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    auto* outBuffer = static_cast<int16_t*>(audioData);
    size_t samplesNeeded = static_cast<size_t>(numFrames * player->m_channelCount);

    size_t samplesRead = player->m_ringBuffer->read(outBuffer, samplesNeeded);

    // In case of ring buffer underrun, fill remaining buffer with silence
    if (samplesRead < samplesNeeded) {
        std::memset(outBuffer + samplesRead, 0, (samplesNeeded - samplesRead) * sizeof(int16_t));
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AAudioPlayer::errorCallback(
    AAudioStream* /*stream*/,
    void* userData,
    aaudio_result_t error
) {
    LOGE("AAudio error callback received: %s (%d)", AAudio_convertResultToText(error), error);
    auto* player = static_cast<AAudioPlayer*>(userData);
    if (player && error == AAUDIO_ERROR_DISCONNECTED) {
        LOGW("Audio device disconnected, stream will need recreation");
        player->m_running.store(false, std::memory_order_release);
    }
}

} // namespace retropack
