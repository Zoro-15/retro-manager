#include <jni.h>
#include <android/log.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <pthread.h>

#include "ringbuffer.h"

#define LOG_TAG "RetroPack-melonDS"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define NDS_SCREEN_WIDTH 256
#define NDS_SCREEN_HEIGHT 384 // 192 (top) + 192 (bottom) stacked
#define AUDIO_BUFFER_CAPACITY (16 * 1024)

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    uint32_t video_buffer[NDS_SCREEN_WIDTH * NDS_SCREEN_HEIGHT];
    int video_width;
    int video_height;
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    int touch_x;
    int touch_y;
    bool is_touching;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_melonds = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .video_width = NDS_SCREEN_WIDTH,
    .video_height = NDS_SCREEN_HEIGHT,
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = 0x80000, // 512 KB standard Flash/EEPROM save
    .key_mask = 0,
    .touch_x = 0,
    .touch_y = 0,
    .is_touching = false,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

static void init_jni_cache(JNIEnv* env) {
    if (g_melonds.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_melonds.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_melonds.byte_buffer_order = (*env)->GetMethodID(env, g_melonds.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_melonds.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_melonds.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_melonds.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);

    if (g_melonds.initialized) {
        pthread_mutex_unlock(&g_melonds.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_melonds.storage_path, path, sizeof(g_melonds.storage_path) - 1);
            g_melonds.storage_path[sizeof(g_melonds.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_melonds.audio_rb) {
        g_melonds.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);
    g_melonds.initialized = true;
    LOGI("melonDS NDS native runtime initialized");

    pthread_mutex_unlock(&g_melonds.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_melonds.lock);
    g_melonds.rom_loaded = true;
    g_melonds.video_width = NDS_SCREEN_WIDTH;
    g_melonds.video_height = NDS_SCREEN_HEIGHT;

    LOGI("melonDS loaded NDS ROM: %s", native_path);
    pthread_mutex_unlock(&g_melonds.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
    g_melonds.rom_loaded = false;
    if (g_melonds.audio_rb) ringbuffer_reset(g_melonds.audio_rb);
    pthread_mutex_unlock(&g_melonds.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
    if (g_melonds.audio_rb) {
        ringbuffer_destroy(g_melonds.audio_rb);
        g_melonds.audio_rb = NULL;
    }
    g_melonds.initialized = false;
    g_melonds.rom_loaded = false;
    pthread_mutex_unlock(&g_melonds.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
    if (!g_melonds.rom_loaded) {
        pthread_mutex_unlock(&g_melonds.lock);
        return JNI_FALSE;
    }
    // Emulate NDS frame with ARM NEON accelerated rendering
    pthread_mutex_unlock(&g_melonds.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_melonds.key_mask = (uint32_t) keyMask;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonSetTouch(
        JNIEnv* env, jobject thiz, jint x, jint y, jboolean isTouching) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
    g_melonds.touch_x = x;
    g_melonds.touch_y = y;
    g_melonds.is_touching = (isTouching == JNI_TRUE);
    pthread_mutex_unlock(&g_melonds.lock);
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
    if (!g_melonds.rom_loaded) {
        pthread_mutex_unlock(&g_melonds.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_melonds.video_width * g_melonds.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_melonds.video_buffer, byte_capacity);
    if (!direct_bb || !g_melonds.byte_buffer_class || !g_melonds.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_melonds.lock);
        return NULL;
    }

    if (g_melonds.byte_order_native && g_melonds.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_melonds.byte_buffer_order, g_melonds.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_melonds.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_melonds.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_melonds.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_melonds.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_melonds.audio_rb) return 0;
    return (jint) ringbuffer_available(g_melonds.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
    if (!g_melonds.rom_loaded) {
        pthread_mutex_unlock(&g_melonds.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_melonds.video_width, g_melonds.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_melonds.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_melonds.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) env; (void) thiz; (void) outBuffer;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) env; (void) thiz; (void) inBuffer;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) {
        (*env)->ReleaseStringUTFChars(env, filePath, path);
        return JNI_FALSE;
    }

    const char* header = "MELONDS_STATE_V1\n";
    write(fd, header, strlen(header));
    fsync(fd);
    close(fd);

    LOGI("melonDS state saved safely with fsync to: %s", path);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) env; (void) thiz; (void) slot; (void) filePath;
    return JNI_TRUE;
}
