#include <jni.h>
#include <android/log.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <sys/types.h>
#include <pthread.h>

#include <mgba/core/core.h>
#include <mgba/core/config.h>
#include <mgba/core/blip_buf.h>
#include <mgba/core/serialize.h>
#include <mgba/core/version.h>
#include <mgba-util/vfs.h>

#include "ringbuffer.h"

#define LOG_TAG "RetroPack-mGBA"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define VIDEO_MAX_WIDTH 256
#define VIDEO_MAX_HEIGHT 160
#define AUDIO_SAMPLE_RATE 44100
#define AUDIO_BUFFER_CAPACITY (16 * 1024)
#define AUDIO_TEMP_CAPACITY 4096

_Static_assert(sizeof(color_t) == 4, "RetroPack requires mGBA's 32-bit color format");

static struct {
    struct mCore* core;
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    color_t video_buffer[VIDEO_MAX_WIDTH * VIDEO_MAX_HEIGHT];
    int video_width;
    int video_height;
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
} g_runtime = {
    .core = NULL,
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .video_width = 0,
    .video_height = 0,
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
};

static void release_core_locked(void) {
    if (g_runtime.core) {
        mCoreConfigDeinit(&g_runtime.core->config);
        g_runtime.core->deinit(g_runtime.core);
        g_runtime.core = NULL;
    }
    g_runtime.rom_loaded = false;
    g_runtime.video_width = 0;
    g_runtime.video_height = 0;
    memset(g_runtime.video_buffer, 0, sizeof(g_runtime.video_buffer));
    if (g_runtime.audio_rb) {
        ringbuffer_reset(g_runtime.audio_rb);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_runtime.lock);

    if (g_runtime.initialized) {
        LOGI("NativeCore already initialized");
        pthread_mutex_unlock(&g_runtime.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_runtime.storage_path, path, sizeof(g_runtime.storage_path) - 1);
            g_runtime.storage_path[sizeof(g_runtime.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_runtime.audio_rb) {
        g_runtime.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
        if (!g_runtime.audio_rb) {
            LOGE("Failed to allocate audio ring buffer");
            pthread_mutex_unlock(&g_runtime.lock);
            return JNI_FALSE;
        }
    }

    g_runtime.initialized = true;
    LOGI("NativeCore initialized successfully (storage: %s)", g_runtime.storage_path);

    pthread_mutex_unlock(&g_runtime.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) {
        LOGE("nativeLoadRom: romPath is null");
        return JNI_FALSE;
    }

    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) {
        LOGE("nativeLoadRom: failed to resolve UTF-8 string");
        return JNI_FALSE;
    }

    pthread_mutex_lock(&g_runtime.lock);

    release_core_locked();

    struct mCore* core = mCoreFind(native_path);
    if (!core) {
        LOGE("nativeLoadRom: mCoreFind failed for '%s'", native_path);
        pthread_mutex_unlock(&g_runtime.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }

    if (!core->init(core)) {
        LOGE("nativeLoadRom: core->init failed");
        free(core);
        pthread_mutex_unlock(&g_runtime.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }

    mCoreInitConfig(core, NULL);
    struct mCoreOptions options = {
        .useBios = true,
        .fpsTarget = 60.0f,
        .sampleRate = AUDIO_SAMPLE_RATE,
        .volume = 0x100,
    };
    mCoreConfigLoadDefaults(&core->config, &options);
    mCoreConfigSetIntValue(&core->config, "sgb.borders", 0);
    mCoreLoadConfig(core);

    if (!mCoreLoadFile(core, native_path)) {
        LOGE("nativeLoadRom: mCoreLoadFile failed for '%s'", native_path);
        mCoreConfigDeinit(&core->config);
        core->deinit(core);
        pthread_mutex_unlock(&g_runtime.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }

    (*env)->ReleaseStringUTFChars(env, romPath, native_path);

    unsigned width = 240;
    unsigned height = 160;
    core->desiredVideoDimensions(core, &width, &height);
    if (!width || !height || width > VIDEO_MAX_WIDTH || height > VIDEO_MAX_HEIGHT) {
        LOGE("nativeLoadRom: invalid dimensions %ux%u", width, height);
        mCoreConfigDeinit(&core->config);
        core->deinit(core);
        pthread_mutex_unlock(&g_runtime.lock);
        return JNI_FALSE;
    }

    g_runtime.video_width = (int) width;
    g_runtime.video_height = (int) height;
    core->setVideoBuffer(core, g_runtime.video_buffer, width);

    core->setAudioBufferSize(core, 2048);
    blip_set_rates(core->getAudioChannel(core, 0), core->frequency(core), AUDIO_SAMPLE_RATE);
    blip_set_rates(core->getAudioChannel(core, 1), core->frequency(core), AUDIO_SAMPLE_RATE);

    core->reset(core);
    if (g_runtime.audio_rb) {
        ringbuffer_reset(g_runtime.audio_rb);
    }

    g_runtime.core = core;
    g_runtime.rom_loaded = true;

    LOGI("nativeLoadRom: loaded ROM successfully (%dx%d)", g_runtime.video_width, g_runtime.video_height);

    pthread_mutex_unlock(&g_runtime.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeUnloadRom(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_runtime.lock);
    release_core_locked();
    LOGI("nativeUnloadRom: ROM unloaded");
    pthread_mutex_unlock(&g_runtime.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeDestroy(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_runtime.lock);
    release_core_locked();
    if (g_runtime.audio_rb) {
        ringbuffer_destroy(g_runtime.audio_rb);
        g_runtime.audio_rb = NULL;
    }
    g_runtime.initialized = false;
    g_runtime.storage_path[0] = '\0';
    LOGI("nativeDestroy: native subsystem destroyed");
    pthread_mutex_unlock(&g_runtime.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeRunFrame(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_runtime.lock);

    if (!g_runtime.core || !g_runtime.rom_loaded) {
        pthread_mutex_unlock(&g_runtime.lock);
        return JNI_FALSE;
    }

    struct mCore* core = g_runtime.core;
    core->runFrame(core);

    size_t total_pixels = (size_t) g_runtime.video_width * (size_t) g_runtime.video_height;
    for (size_t i = 0; i < total_pixels; ++i) {
        g_runtime.video_buffer[i] |= 0xFF000000U;
    }

    blip_t* left = core->getAudioChannel(core, 0);
    blip_t* right = core->getAudioChannel(core, 1);
    if (left && right && g_runtime.audio_rb) {
        int available = blip_samples_avail(left);
        if (available > 0) {
            int16_t temp_samples[AUDIO_TEMP_CAPACITY];
            size_t frame_capacity = AUDIO_TEMP_CAPACITY / 2;
            int frames_to_read = available;
            if ((size_t) frames_to_read > frame_capacity) {
                frames_to_read = (int) frame_capacity;
            }
            int left_frames = blip_read_samples(left, temp_samples, frames_to_read, true);
            int right_frames = blip_read_samples(right, temp_samples + 1, frames_to_read, true);
            int valid_frames = left_frames < right_frames ? left_frames : right_frames;
            if (valid_frames > 0) {
                ringbuffer_write(g_runtime.audio_rb, temp_samples, (size_t) (valid_frames * 2));
            }
        }
    }

    pthread_mutex_unlock(&g_runtime.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_runtime.lock);
    if (g_runtime.core && g_runtime.rom_loaded) {
        g_runtime.core->setKeys(g_runtime.core, (uint32_t) (keyMask & 0x3FF));
    }
    pthread_mutex_unlock(&g_runtime.lock);
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeGetVideoBuffer(
        JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_runtime.lock);

    if (!g_runtime.core || !g_runtime.rom_loaded || g_runtime.video_width <= 0 || g_runtime.video_height <= 0) {
        pthread_mutex_unlock(&g_runtime.lock);
        return NULL;
    }

    size_t byte_count = (size_t) g_runtime.video_width * (size_t) g_runtime.video_height * sizeof(color_t);
    jobject byteBuffer = (*env)->NewDirectByteBuffer(env, (void*) g_runtime.video_buffer, (jlong) byte_count);
    if (!byteBuffer) {
        pthread_mutex_unlock(&g_runtime.lock);
        return NULL;
    }

    jclass byteBufferClass = (*env)->GetObjectClass(env, byteBuffer);
    jclass byteOrderClass = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (!byteBufferClass || !byteOrderClass) {
        pthread_mutex_unlock(&g_runtime.lock);
        return NULL;
    }

    jmethodID nativeOrderMethod = (*env)->GetStaticMethodID(env, byteOrderClass, "nativeOrder", "()Ljava/nio/ByteOrder;");
    jobject nativeOrder = (*env)->CallStaticObjectMethod(env, byteOrderClass, nativeOrderMethod);
    jmethodID orderMethod = (*env)->GetMethodID(env, byteBufferClass, "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    jobject orderedByteBuffer = (*env)->CallObjectMethod(env, byteBuffer, orderMethod, nativeOrder);
    jmethodID asIntBufferMethod = (*env)->GetMethodID(env, byteBufferClass, "asIntBuffer", "()Ljava/nio/IntBuffer;");
    jobject intBuffer = (*env)->CallObjectMethod(env, orderedByteBuffer, asIntBufferMethod);

    pthread_mutex_unlock(&g_runtime.lock);
    return intBuffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_runtime.audio_rb) {
        return 0;
    }

    jsize arrayLen = (*env)->GetArrayLength(env, outSamples);
    size_t to_read = (size_t) (maxSamples < arrayLen ? maxSamples : arrayLen);
    if (to_read == 0) {
        return 0;
    }

    int16_t temp_buf[1024];
    size_t total_read = 0;
    while (to_read > 0) {
        size_t chunk = to_read > 1024 ? 1024 : to_read;
        size_t read_count = ringbuffer_read(g_runtime.audio_rb, temp_buf, chunk);
        if (read_count == 0) {
            break;
        }
        (*env)->SetShortArrayRegion(env, outSamples, (jsize) total_read, (jsize) read_count, (const jshort*) temp_buf);
        total_read += read_count;
        to_read -= read_count;
        if (read_count < chunk) {
            break;
        }
    }

    return (jint) total_read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeGetSramSize(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_runtime.lock);

    if (!g_runtime.core || !g_runtime.rom_loaded) {
        pthread_mutex_unlock(&g_runtime.lock);
        return 0;
    }

    void* sram = NULL;
    size_t size = g_runtime.core->savedataClone(g_runtime.core, &sram);
    if (sram) {
        free(sram);
    }

    pthread_mutex_unlock(&g_runtime.lock);
    return (jint) size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) {
        return JNI_FALSE;
    }

    pthread_mutex_lock(&g_runtime.lock);

    if (!g_runtime.core || !g_runtime.rom_loaded) {
        pthread_mutex_unlock(&g_runtime.lock);
        return JNI_FALSE;
    }

    void* sram = NULL;
    size_t size = g_runtime.core->savedataClone(g_runtime.core, &sram);
    if (!sram || size == 0) {
        pthread_mutex_unlock(&g_runtime.lock);
        return JNI_FALSE;
    }

    jsize buffer_capacity = (*env)->GetArrayLength(env, outBuffer);
    if ((size_t) buffer_capacity < size) {
        LOGE("nativeReadSram: buffer capacity (%d) < SRAM size (%zu)", buffer_capacity, size);
        free(sram);
        pthread_mutex_unlock(&g_runtime.lock);
        return JNI_FALSE;
    }

    (*env)->SetByteArrayRegion(env, outBuffer, 0, (jsize) size, (const jbyte*) sram);
    free(sram);

    pthread_mutex_unlock(&g_runtime.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) {
        return JNI_FALSE;
    }

    jsize size = (*env)->GetArrayLength(env, inBuffer);
    if (size <= 0) {
        return JNI_FALSE;
    }

    void* data = malloc((size_t) size);
    if (!data) {
        return JNI_FALSE;
    }

    (*env)->GetByteArrayRegion(env, inBuffer, 0, size, (jbyte*) data);
    if ((*env)->ExceptionCheck(env)) {
        free(data);
        return JNI_FALSE;
    }

    pthread_mutex_lock(&g_runtime.lock);

    if (!g_runtime.core || !g_runtime.rom_loaded) {
        free(data);
        pthread_mutex_unlock(&g_runtime.lock);
        return JNI_FALSE;
    }

    struct VFile* save = VFileMemChunk(data, (size_t) size);
    if (!save) {
        free(data);
        pthread_mutex_unlock(&g_runtime.lock);
        return JNI_FALSE;
    }

    bool success = g_runtime.core->loadSave(g_runtime.core, save);
    if (!success) {
        save->close(save);
    }
    free(data);

    pthread_mutex_unlock(&g_runtime.lock);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz;
    pthread_mutex_lock(&g_runtime.lock);

    if (!g_runtime.core || !g_runtime.rom_loaded) {
        pthread_mutex_unlock(&g_runtime.lock);
        return JNI_FALSE;
    }

    bool success = false;
    if (filePath) {
        const char* native_path = (*env)->GetStringUTFChars(env, filePath, NULL);
        if (native_path) {
            struct VFile* vf = VFileOpen(native_path, O_CREAT | O_TRUNC | O_RDWR);
            if (vf) {
                success = mCoreSaveStateNamed(g_runtime.core, vf, SAVESTATE_ALL);
                vf->close(vf);
            }
            (*env)->ReleaseStringUTFChars(env, filePath, native_path);
        }
    } else {
        success = mCoreSaveState(g_runtime.core, (int) slot, SAVESTATE_ALL);
    }

    pthread_mutex_unlock(&g_runtime.lock);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_core_NativeCore_nativeLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz;
    pthread_mutex_lock(&g_runtime.lock);

    if (!g_runtime.core || !g_runtime.rom_loaded) {
        pthread_mutex_unlock(&g_runtime.lock);
        return JNI_FALSE;
    }

    bool success = false;
    if (filePath) {
        const char* native_path = (*env)->GetStringUTFChars(env, filePath, NULL);
        if (native_path) {
            struct VFile* vf = VFileOpen(native_path, O_RDONLY);
            if (vf) {
                success = mCoreLoadStateNamed(g_runtime.core, vf, SAVESTATE_ALL);
                vf->close(vf);
            }
            (*env)->ReleaseStringUTFChars(env, filePath, native_path);
        }
    } else {
        success = mCoreLoadState(g_runtime.core, (int) slot, SAVESTATE_ALL);
    }

    pthread_mutex_unlock(&g_runtime.lock);
    return success ? JNI_TRUE : JNI_FALSE;
}
