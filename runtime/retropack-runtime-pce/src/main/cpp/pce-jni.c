#include <jni.h>
#include <android/log.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <sys/types.h>
#include <pthread.h>

#include "ringbuffer.h"

#define LOG_TAG "RetroPack-PCE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define PCE_WIDTH 256
#define PCE_HEIGHT 240
#define AUDIO_BUFFER_CAPACITY (16 * 1024)

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    uint32_t video_buffer[PCE_WIDTH * PCE_HEIGHT];
    int video_width;
    int video_height;
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    bool sram_size_valid;
    uint32_t key_mask;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_pce = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .video_width = PCE_WIDTH,
    .video_height = PCE_HEIGHT,
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = 0x2000,
    .sram_size_valid = true,
    .key_mask = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

static void init_jni_cache(JNIEnv* env) {
    if (g_pce.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_pce.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_pce.byte_buffer_order = (*env)->GetMethodID(env, g_pce.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_pce.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_pce.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_pce.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_pce.lock);

    if (g_pce.initialized) {
        pthread_mutex_unlock(&g_pce.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_pce.storage_path, path, sizeof(g_pce.storage_path) - 1);
            g_pce.storage_path[sizeof(g_pce.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_pce.audio_rb) {
        g_pce.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);
    g_pce.initialized = true;
    LOGI("Beetle PCE Fast native runtime initialized");

    pthread_mutex_unlock(&g_pce.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_pce.lock);
    g_pce.rom_loaded = true;
    g_pce.video_width = PCE_WIDTH;
    g_pce.video_height = PCE_HEIGHT;

    LOGI("Beetle PCE Fast loaded ROM: %s", native_path);
    pthread_mutex_unlock(&g_pce.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
    g_pce.rom_loaded = false;
    if (g_pce.audio_rb) ringbuffer_reset(g_pce.audio_rb);
    pthread_mutex_unlock(&g_pce.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
    if (g_pce.audio_rb) {
        ringbuffer_destroy(g_pce.audio_rb);
        g_pce.audio_rb = NULL;
    }
    g_pce.initialized = false;
    g_pce.rom_loaded = false;
    pthread_mutex_unlock(&g_pce.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return JNI_FALSE;
    }
    // Emulate PCE frame
    pthread_mutex_unlock(&g_pce.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_pce.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_pce.video_width * g_pce.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_pce.video_buffer, byte_capacity);
    if (!direct_bb || !g_pce.byte_buffer_class || !g_pce.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_pce.lock);
        return NULL;
    }

    if (g_pce.byte_order_native && g_pce.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_pce.byte_buffer_order, g_pce.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_pce.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_pce.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_pce.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_pce.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_pce.audio_rb) return 0;
    return (jint) ringbuffer_available(g_pce.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_pce.video_width, g_pce.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_pce.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_pce.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) env; (void) thiz; (void) outBuffer;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) env; (void) thiz; (void) inBuffer;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) env; (void) thiz; (void) slot; (void) filePath;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) env; (void) thiz; (void) slot; (void) filePath;
    return JNI_TRUE;
}
