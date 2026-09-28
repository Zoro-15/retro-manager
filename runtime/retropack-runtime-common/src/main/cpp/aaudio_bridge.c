/*
 * aaudio_bridge.c - Direct AAudio realtime pull bridge for RetroPack (issue #43).
 *
 * Implements the JNI surface declared by
 * com.retropack.runtime.audio.OboeNativeBridge using Android's native AAudio
 * API (API 26+, the NDK ships libaaudio; no external Oboe dependency).
 *
 * The AAudio onAudioReady callback runs on a high-priority realtime thread
 * and drains the libretro audio ring buffer DIRECTLY through the lock-free
 * host_pull_audio_samples() accessor — no JNI crossings, no mutexes, and no
 * contention with the emulation thread's host lock. This eliminates the
 * old Kotlin pump path (nativeGetAudioSamples -> ShortArray -> JNI write)
 * that added latency and allocation churn between the core and the DAC.
 *
 * Micro drift compensation (proportional regulation): the Kotlin
 * AudioDriftController publishes a rate in [0.94, 1.06] via
 * nativeOboeSetDriftRate; the callback consumes rate-scaled input samples and
 * linearly interpolates them onto the exact output frame count. At ±2-3%
 * this is inaudible and prevents cumulative buffer drift between the ~59.7275
 * Hz NTSC core clock and the 60.00 Hz display/DAc clock (or VRR).
 */

#include <jni.h>
#include <aaudio/AAudio.h>
#include <android/log.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include "libretro_host.h"

#define LOG_TAG "RetroPack-AAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define AAUDIO_MAX_STREAMS 4
#define DRIFT_RATE_MIN 0.94f
#define DRIFT_RATE_MAX 1.06f
/* Latency bound: keep at most ~3 frames (~50 ms) of backlog in the ring
 * buffer; older samples are skipped from the consumer side (race-free). */
#define BACKLOG_LIMIT_FRAMES 3

typedef struct AAudioSlot {
    _Atomic (AAudioStream*) stream;
    _Atomic bool running;
    _Atomic float drift_rate;      /* playback rate multiplier */
    _Atomic float volume;          /* 0.0 .. 1.0 */
    _Atomic int32_t underruns;
    _Atomic int32_t sample_rate;
    _Atomic int32_t channel_count;
    double fractional_carry;       /* callback-thread-private accumulator */
    int16_t* scratch;              /* rate-scaled input staging */
    size_t scratch_capacity;       /* in samples (all channels) */
} AAudioSlot;

static AAudioSlot g_slots[AAUDIO_MAX_STREAMS];
static pthread_mutex_t g_slots_mutex = PTHREAD_MUTEX_INITIALIZER;

static AAudioSlot* slot_from_handle(jlong handle) {
    if (handle <= 0 || handle > AAUDIO_MAX_STREAMS) {
        return NULL;
    }
    AAudioSlot* slot = &g_slots[handle - 1];
    if (atomic_load_explicit(&slot->stream, memory_order_acquire) == NULL) {
        return NULL;
    }
    return slot;
}

static void slot_release_stream(AAudioSlot* slot) {
    AAudioStream* stream = atomic_exchange_explicit(&slot->stream, NULL, memory_order_acq_rel);
    if (stream) {
        AAudioStream_close(stream);
    }
}

/* Linear interpolation of 'in_count' input samples (interleaved) onto
 * 'out_count' output samples. Rate != 1.0 implements the micro time-stretch:
 * a slightly faster consumption rate (rate > 1) shortens the buffered audio
 * and drains latency; rate < 1 stretches it to bridge a slow core. */
static void resample_linear(const int16_t* in, size_t in_count, int16_t* out, size_t out_count, int channels) {
    if (in_count == 0) {
        memset(out, 0, out_count * channels * sizeof(int16_t));
        return;
    }
    if (in_count == out_count) {
        memcpy(out, in, out_count * channels * sizeof(int16_t));
        return;
    }
    if (in_count < (size_t) channels * 2) {
        /* Too few input frames to interpolate: replicate the last frame. */
        for (size_t f = 0; f < out_count; f++) {
            memcpy(out + f * channels, in, (size_t) channels * sizeof(int16_t));
        }
        return;
    }

    const size_t in_frames = in_count / channels;
    const double step = (double) (in_frames - 1) / (double) (out_count > 0 ? (out_count - 1) : 1);
    double src_pos = 0.0;
    for (size_t f = 0; f < out_count; f++) {
        size_t i0 = (size_t) src_pos;
        if (i0 >= in_frames - 1) i0 = in_frames - 2;
        const size_t i1 = i0 + 1;
        const float t = (float) (src_pos - (double) i0);
        for (int c = 0; c < channels; c++) {
            const float s0 = (float) in[i0 * channels + c];
            const float s1 = (float) in[i1 * channels + c];
            out[f * channels + c] = (int16_t) lrintf(s0 + (s1 - s0) * t);
        }
        src_pos += step;
    }
}

static aaudio_data_callback_result_t aaudio_on_audio_ready(
        AAudioStream* stream,
        void* userData,
        void* audioData,
        int32_t numFrames) {
    AAudioSlot* slot = (AAudioSlot*) userData;
    (void) stream;

    const int32_t channels = atomic_load_explicit(&slot->channel_count, memory_order_relaxed);
    int16_t* out = (int16_t*) audioData;
    const size_t out_samples = (size_t) numFrames * (size_t) channels;

    if (!atomic_load_explicit(&slot->running, memory_order_relaxed)) {
        memset(out, 0, out_samples * sizeof(int16_t));
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    /* Rate-scaled input demand. */
    float rate = atomic_load_explicit(&slot->drift_rate, memory_order_relaxed);
    if (!(rate >= DRIFT_RATE_MIN && rate <= DRIFT_RATE_MAX)) {
        rate = 1.0f;
    }
    slot->fractional_carry += (double) numFrames * (double) rate;
    size_t input_frames = (size_t) slot->fractional_carry;
    if (input_frames < (size_t) numFrames) input_frames = (size_t) numFrames;
    if (input_frames > (size_t) numFrames * 2) input_frames = (size_t) numFrames * 2;
    slot->fractional_carry -= (double) input_frames;
    if (slot->fractional_carry < -1.0) slot->fractional_carry = 0.0;

    const size_t want_samples = input_frames * (size_t) channels;
    size_t got = 0;
    if (want_samples <= slot->scratch_capacity) {
        /* Consumer-side latency bound: skip stale backlog beyond ~3 frames. */
        const size_t backlog_limit = (size_t) BACKLOG_LIMIT_FRAMES * (size_t) numFrames * (size_t) channels;
        got = host_pull_audio_samples(slot->scratch, want_samples, backlog_limit);
    }

    if (got == 0) {
        memset(out, 0, out_samples * sizeof(int16_t));
        atomic_fetch_add_explicit(&slot->underruns, 1, memory_order_relaxed);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    if (input_frames == (size_t) numFrames && got == out_samples) {
        /* Exact rate pass-through: the hot path (rate == 1.0). */
        memcpy(out, slot->scratch, out_samples * sizeof(int16_t));
    } else {
        resample_linear(slot->scratch, got, out, (size_t) numFrames, channels);
    }

    /* Apply volume atomically (mute == 0.0). */
    const float volume = atomic_load_explicit(&slot->volume, memory_order_relaxed);
    if (volume < 0.999f || volume > 1.001f) {
        const int32_t scaled_max = (int32_t) (volume * 32767.0f);
        for (size_t i = 0; i < out_samples; i++) {
            int32_t s = (int32_t) out[i];
            s = (s * scaled_max) / 32767;
            if (s > 32767) s = 32767;
            if (s < -32768) s = -32768;
            out[i] = (int16_t) s;
        }
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static void aaudio_on_error(AAudioStream* stream, void* userData, aaudio_result_t error) {
    AAudioSlot* slot = (AAudioSlot*) userData;
    (void) stream;
    LOGE("AAudio stream error: %s", AAudio_convertResultToText(error));
    atomic_store_explicit(&slot->running, false, memory_order_relaxed);
}

/* --------------------------------------------------------------------- */
/* JNI surface: com.retropack.runtime.audio.OboeNativeBridge              */
/* --------------------------------------------------------------------- */

JNIEXPORT jlong JNICALL
Java_com_retropack_runtime_audio_OboeNativeBridge_nativeOboeOpen(
        JNIEnv* env, jobject thiz,
        jint sample_rate, jint channel_count, jint buffer_size_frames) {
    (void) env;
    (void) thiz;
    if (sample_rate <= 0 || channel_count <= 0) {
        return 0;
    }

    pthread_mutex_lock(&g_slots_mutex);
    jlong handle = 0;
    for (int i = 0; i < AAUDIO_MAX_STREAMS; i++) {
        if (atomic_load_explicit(&g_slots[i].stream, memory_order_acquire) == NULL) {
            handle = (jlong) (i + 1);
            break;
        }
    }
    if (handle == 0) {
        pthread_mutex_unlock(&g_slots_mutex);
        return 0;
    }

    AAudioSlot* slot = &g_slots[handle - 1];
    memset(slot, 0, sizeof(*slot));
    atomic_init(&slot->stream, (AAudioStream*) NULL);
    atomic_init(&slot->running, false);
    atomic_init(&slot->drift_rate, 1.0f);
    atomic_init(&slot->volume, 1.0f);
    atomic_init(&slot->underruns, 0);
    atomic_init(&slot->sample_rate, sample_rate);
    atomic_init(&slot->channel_count, channel_count);
    slot->fractional_carry = 0.0;

    /* Scratch staging: 2x requested input headroom for rate > 1. */
    const size_t max_input_frames = (size_t) (buffer_size_frames > 0 ? buffer_size_frames : 512) * 2;
    slot->scratch_capacity = max_input_frames * (size_t) channel_count;
    slot->scratch = (int16_t*) malloc(slot->scratch_capacity * sizeof(int16_t));
    if (!slot->scratch) {
        pthread_mutex_unlock(&g_slots_mutex);
        return 0;
    }

    AAudioStreamBuilder* builder = NULL;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        LOGE("AAudio_createStreamBuilder failed: %s", AAudio_convertResultToText(result));
        free(slot->scratch);
        slot->scratch = NULL;
        pthread_mutex_unlock(&g_slots_mutex);
        return 0;
    }

    AAudioStreamBuilder_setSampleRate(builder, (int32_t) sample_rate);
    AAudioStreamBuilder_setChannelCount(builder, (int32_t) channel_count);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setBufferCapacityInFrames(builder, (int32_t) (buffer_size_frames > 0 ? buffer_size_frames * 4 : 2048));
    /* 0 = driver-default burst-sized callbacks (lowest stable latency). */
    AAudioStreamBuilder_setFramesPerDataCallback(builder, 0);
    AAudioStreamBuilder_setDataCallback(builder, aaudio_on_audio_ready, slot);
    AAudioStreamBuilder_setErrorCallback(builder, aaudio_on_error, slot);

    AAudioStream* stream = NULL;
    result = AAudioStreamBuilder_openStream(builder, &stream);
    if (result != AAUDIO_OK) {
        /* Exclusive sharing is a hint; retry with the default sharing mode. */
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
        result = AAudioStreamBuilder_openStream(builder, &stream);
    }
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK || !stream) {
        LOGE("AAudio openStream failed: %s", AAudio_convertResultToText(result));
        free(slot->scratch);
        slot->scratch = NULL;
        pthread_mutex_unlock(&g_slots_mutex);
        return 0;
    }

    atomic_store_explicit(&slot->channel_count, AAudioStream_getChannelCount(stream), memory_order_relaxed);
    atomic_store_explicit(&slot->sample_rate, AAudioStream_getSampleRate(stream), memory_order_relaxed);
    atomic_store_explicit(&slot->stream, stream, memory_order_release);
    LOGI("AAudio stream opened: handle=%lld rate=%d ch=%d burst=%d capacity=%d perf=%d",
         (long long) handle,
         AAudioStream_getSampleRate(stream),
         AAudioStream_getChannelCount(stream),
         AAudioStream_getFramesPerBurst(stream),
         AAudioStream_getBufferCapacityInFrames(stream),
         AAudioStream_getPerformanceMode(stream));

    pthread_mutex_unlock(&g_slots_mutex);
    return handle;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_audio_OboeNativeBridge_nativeOboeStart(JNIEnv* env, jobject thiz, jlong handle) {
    (void) env; (void) thiz;
    AAudioSlot* slot = slot_from_handle(handle);
    if (!slot) return JNI_FALSE;
    AAudioStream* stream = atomic_load_explicit(&slot->stream, memory_order_acquire);
    if (!stream) return JNI_FALSE;
    atomic_store_explicit(&slot->running, true, memory_order_relaxed);
    return AAudioStream_requestStart(stream) == AAUDIO_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_audio_OboeNativeBridge_nativeOboePause(JNIEnv* env, jobject thiz, jlong handle) {
    (void) env; (void) thiz;
    AAudioSlot* slot = slot_from_handle(handle);
    if (!slot) return JNI_FALSE;
    AAudioStream* stream = atomic_load_explicit(&slot->stream, memory_order_acquire);
    if (!stream) return JNI_FALSE;
    atomic_store_explicit(&slot->running, false, memory_order_relaxed);
    return AAudioStream_requestPause(stream) == AAUDIO_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_audio_OboeNativeBridge_nativeOboeFlush(JNIEnv* env, jobject thiz, jlong handle) {
    (void) env; (void) thiz;
    AAudioSlot* slot = slot_from_handle(handle);
    if (!slot) return;
    AAudioStream* stream = atomic_load_explicit(&slot->stream, memory_order_acquire);
    if (!stream) return;
    slot->fractional_carry = 0.0;
    AAudioStream_requestFlush(stream);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_audio_OboeNativeBridge_nativeOboeClose(JNIEnv* env, jobject thiz, jlong handle) {
    (void) env; (void) thiz;
    AAudioSlot* slot = slot_from_handle(handle);
    if (!slot) return;
    atomic_store_explicit(&slot->running, false, memory_order_relaxed);
    slot_release_stream(slot);
    free(slot->scratch);
    slot->scratch = NULL;
    slot->scratch_capacity = 0;
}

/*
 * Pull mode: the realtime callback drains the ring buffer directly, so pushed
 * writes are redundant. Report the full batch as accepted so any legacy pump
 * path remains a harmless no-op.
 */
JNIEXPORT jint JNICALL
Java_com_retropack_runtime_audio_OboeNativeBridge_nativeOboeWrite(
        JNIEnv* env, jobject thiz, jlong handle,
        jshortArray samples, jint offset, jint count) {
    (void) env; (void) thiz; (void) samples; (void) offset;
    return slot_from_handle(handle) ? count : 0;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_audio_OboeNativeBridge_nativeOboeSetVolume(
        JNIEnv* env, jobject thiz, jlong handle, jfloat volume) {
    (void) env; (void) thiz;
    AAudioSlot* slot = slot_from_handle(handle);
    if (!slot) return;
    if (volume < 0.0f) volume = 0.0f;
    if (volume > 1.0f) volume = 1.0f;
    atomic_store_explicit(&slot->volume, volume, memory_order_relaxed);
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_audio_OboeNativeBridge_nativeOboeGetUnderruns(JNIEnv* env, jobject thiz, jlong handle) {
    (void) env; (void) thiz;
    AAudioSlot* slot = slot_from_handle(handle);
    return slot ? atomic_load_explicit(&slot->underruns, memory_order_relaxed) : 0;
}

JNIEXPORT jdouble JNICALL
Java_com_retropack_runtime_audio_OboeNativeBridge_nativeOboeGetLatencyMs(JNIEnv* env, jobject thiz, jlong handle) {
    (void) env; (void) thiz;
    AAudioSlot* slot = slot_from_handle(handle);
    if (!slot) return 0.0;
    AAudioStream* stream = atomic_load_explicit(&slot->stream, memory_order_acquire);
    if (!stream) return 0.0;
    const int32_t capacity = AAudioStream_getBufferCapacityInFrames(stream);
    const int32_t rate = AAudioStream_getSampleRate(stream);
    if (rate <= 0) return 0.0;
    return (jdouble) capacity * 1000.0 / (jdouble) rate;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_audio_OboeNativeBridge_nativeOboeIsDirect(JNIEnv* env, jobject thiz, jlong handle) {
    (void) env; (void) thiz;
    return slot_from_handle(handle) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Publishes the AudioDriftController's proportional rate adjustment into the
 * realtime callback via a relaxed atomic float (lock-free, wait-free).
 */
JNIEXPORT void JNICALL
Java_com_retropack_runtime_audio_OboeNativeBridge_nativeOboeSetDriftRate(
        JNIEnv* env, jobject thiz, jlong handle, jfloat rate) {
    (void) env; (void) thiz;
    AAudioSlot* slot = slot_from_handle(handle);
    if (!slot) return;
    if (!(rate >= DRIFT_RATE_MIN && rate <= DRIFT_RATE_MAX)) {
        rate = 1.0f;
    }
    atomic_store_explicit(&slot->drift_rate, rate, memory_order_relaxed);
}
