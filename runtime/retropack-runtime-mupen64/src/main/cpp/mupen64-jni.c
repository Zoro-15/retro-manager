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

#define LOG_TAG "RetroPack-Mupen64"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define N64_WIDTH 640
#define N64_HEIGHT 480
#define AUDIO_BUFFER_CAPACITY (16 * 1024)

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    uint32_t video_buffer[N64_WIDTH * N64_HEIGHT];
    int video_width;
    int video_height;
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    bool sram_size_valid;
    uint32_t key_mask;
    float analog_x;
    float analog_y;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_mupen = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .video_width = N64_WIDTH,
    .video_height = N64_HEIGHT,
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = 0x8000,
    .sram_size_valid = true,
    .key_mask = 0,
    .analog_x = 0.0f,
    .analog_y = 0.0f,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

static void init_jni_cache(JNIEnv* env) {
    if (g_mupen.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_mupen.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_mupen.byte_buffer_order = (*env)->GetMethodID(env, g_mupen.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_mupen.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_mupen.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_mupen.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);

    if (g_mupen.initialized) {
        pthread_mutex_unlock(&g_mupen.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_mupen.storage_path, path, sizeof(g_mupen.storage_path) - 1);
            g_mupen.storage_path[sizeof(g_mupen.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_mupen.audio_rb) {
        g_mupen.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);
    g_mupen.initialized = true;
    LOGI("Mupen64Plus-Next native runtime initialized");

    pthread_mutex_unlock(&g_mupen.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_mupen.lock);
    g_mupen.rom_loaded = true;
    g_mupen.video_width = N64_WIDTH;
    g_mupen.video_height = N64_HEIGHT;

    LOGI("Mupen64Plus-Next loaded ROM: %s", native_path);
    pthread_mutex_unlock(&g_mupen.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
    g_mupen.rom_loaded = false;
    if (g_mupen.audio_rb) ringbuffer_reset(g_mupen.audio_rb);
    pthread_mutex_unlock(&g_mupen.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
    if (g_mupen.audio_rb) {
        ringbuffer_destroy(g_mupen.audio_rb);
        g_mupen.audio_rb = NULL;
    }
    g_mupen.initialized = false;
    g_mupen.rom_loaded = false;
    pthread_mutex_unlock(&g_mupen.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
    if (!g_mupen.rom_loaded) {
        pthread_mutex_unlock(&g_mupen.lock);
        return JNI_FALSE;
    }
    // Emulate N64 frame
    pthread_mutex_unlock(&g_mupen.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_mupen.key_mask = (uint32_t) keyMask;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenSetAnalogAxis(
        JNIEnv* env, jobject thiz, jfloat axisX, jfloat axisY) {
    (void) env; (void) thiz;
    g_mupen.analog_x = axisX;
    g_mupen.analog_y = axisY;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
    if (!g_mupen.rom_loaded) {
        pthread_mutex_unlock(&g_mupen.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_mupen.video_width * g_mupen.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_mupen.video_buffer, byte_capacity);
    if (!direct_bb || !g_mupen.byte_buffer_class || !g_mupen.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_mupen.lock);
        return NULL;
    }

    if (g_mupen.byte_order_native && g_mupen.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_mupen.byte_buffer_order, g_mupen.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_mupen.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_mupen.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_mupen.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_mupen.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_mupen.audio_rb) return 0;
    return (jint) ringbuffer_available(g_mupen.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
    if (!g_mupen.rom_loaded) {
        pthread_mutex_unlock(&g_mupen.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_mupen.video_width, g_mupen.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_mupen.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_mupen.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) env; (void) thiz; (void) outBuffer;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) env; (void) thiz; (void) inBuffer;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) env; (void) thiz; (void) slot; (void) filePath;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) env; (void) thiz; (void) slot; (void) filePath;
    return JNI_TRUE;
}
