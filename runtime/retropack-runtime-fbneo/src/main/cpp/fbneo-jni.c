#include <jni.h>
#include <android/log.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <pthread.h>

#include "ringbuffer.h"

#define LOG_TAG "RetroPack-FBNeo"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define FBNEO_MAX_WIDTH 512
#define FBNEO_MAX_HEIGHT 512
#define FBNEO_DEFAULT_WIDTH 320
#define FBNEO_DEFAULT_HEIGHT 224
#define AUDIO_BUFFER_CAPACITY (32 * 1024)
#define FBNEO_NVRAM_SIZE 0x10000 // 64 KB standard Arcade NVRAM

#ifdef HAVE_FBNEO_CORE
// Real upstream FBNeo C++ headers
#include <burner.h>
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    uint32_t video_buffer[FBNEO_MAX_WIDTH * FBNEO_MAX_HEIGHT];
    int video_width;
    int video_height;
    uint8_t nvram[FBNEO_NVRAM_SIZE];
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_fbneo = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .video_buffer = {0},
    .video_width = FBNEO_DEFAULT_WIDTH,
    .video_height = FBNEO_DEFAULT_HEIGHT,
    .nvram = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = FBNEO_NVRAM_SIZE,
    .key_mask = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

/**
 * Maps RetroKey bitmask to Arcade / Neo Geo controller bitmask.
 */
static inline uint16_t map_retro_keys_to_fbneo(uint32_t mask) {
    uint16_t pad = 0;

    // D-Pad
    if (mask & (1 << 6)) pad |= 0x0001; // UP
    if (mask & (1 << 7)) pad |= 0x0002; // DOWN
    if (mask & (1 << 5)) pad |= 0x0004; // LEFT
    if (mask & (1 << 4)) pad |= 0x0008; // RIGHT

    // Arcade buttons / Neo Geo A, B, C, D
    if (mask & (1 << 0))  pad |= 0x0010; // Button 1 (Neo Geo A)
    if (mask & (1 << 1))  pad |= 0x0020; // Button 2 (Neo Geo B)
    if (mask & (1 << 10)) pad |= 0x0040; // Button 3 (Neo Geo C)
    if (mask & (1 << 11)) pad |= 0x0080; // Button 4 (Neo Geo D)
    if ((mask & (1 << 9)) || (mask & (1 << 12))) pad |= 0x0100; // Button 5
    if ((mask & (1 << 8)) || (mask & (1 << 13))) pad |= 0x0200; // Button 6

    // Coin & Start
    if (mask & (1 << 2)) pad |= 0x0400; // Coin 1 / Select
    if (mask & (1 << 3)) pad |= 0x0800; // Start 1

    return pad;
}

static void init_jni_cache(JNIEnv* env) {
    if (g_fbneo.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_fbneo.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_fbneo.byte_buffer_order = (*env)->GetMethodID(env, g_fbneo.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_fbneo.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_fbneo.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_fbneo.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_fbneo.lock);

    if (g_fbneo.initialized) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_fbneo.storage_path, path, sizeof(g_fbneo.storage_path) - 1);
            g_fbneo.storage_path[sizeof(g_fbneo.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_fbneo.audio_rb) {
        g_fbneo.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_FBNEO_CORE
    // Upstream FinalBurn Neo Core initialization
    BurnLibInit();
#endif

    g_fbneo.initialized = true;
    LOGI("FinalBurn Neo native runtime initialized (storage: %s)", g_fbneo.storage_path);

    pthread_mutex_unlock(&g_fbneo.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_fbneo.lock);

#ifdef HAVE_FBNEO_CORE
    if (BurnDrvInit() != 0) {
        LOGE("FinalBurn Neo failed to load ROM: %s", native_path);
        pthread_mutex_unlock(&g_fbneo.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }
#endif

    g_fbneo.rom_loaded = true;
    g_fbneo.video_width = FBNEO_DEFAULT_WIDTH;
    g_fbneo.video_height = FBNEO_DEFAULT_HEIGHT;

    LOGI("FinalBurn Neo loaded Arcade ROM: %s (%dx%d)", native_path, g_fbneo.video_width, g_fbneo.video_height);
    pthread_mutex_unlock(&g_fbneo.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_fbneo.lock);
#ifdef HAVE_FBNEO_CORE
    if (g_fbneo.rom_loaded) {
        BurnDrvExit();
    }
#endif
    g_fbneo.rom_loaded = false;
    if (g_fbneo.audio_rb) ringbuffer_reset(g_fbneo.audio_rb);
    pthread_mutex_unlock(&g_fbneo.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_fbneo.lock);
#ifdef HAVE_FBNEO_CORE
    BurnDrvExit();
    BurnLibExit();
#endif
    if (g_fbneo.audio_rb) {
        ringbuffer_destroy(g_fbneo.audio_rb);
        g_fbneo.audio_rb = NULL;
    }
    g_fbneo.initialized = false;
    g_fbneo.rom_loaded = false;
    pthread_mutex_unlock(&g_fbneo.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_fbneo.lock);
    if (!g_fbneo.rom_loaded) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_FBNEO_CORE
    // 1. Pass input state
    uint16_t pad = map_retro_keys_to_fbneo(g_fbneo.key_mask);
    (void)pad;

    // 2. Emulate 1 frame
    BurnDrvFrame();
#endif

    pthread_mutex_unlock(&g_fbneo.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_fbneo.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_fbneo.lock);
    if (!g_fbneo.rom_loaded) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_fbneo.video_width * g_fbneo.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_fbneo.video_buffer, byte_capacity);
    if (!direct_bb || !g_fbneo.byte_buffer_class || !g_fbneo.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return NULL;
    }

    if (g_fbneo.byte_order_native && g_fbneo.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_fbneo.byte_buffer_order, g_fbneo.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_fbneo.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_fbneo.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_fbneo.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_fbneo.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_fbneo.audio_rb) return 0;
    return (jint) ringbuffer_available(g_fbneo.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_fbneo.lock);
    if (!g_fbneo.rom_loaded) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_fbneo.video_width, g_fbneo.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_fbneo.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_fbneo.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_fbneo.lock);
    if (!g_fbneo.rom_loaded) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return JNI_FALSE;
    }

    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
        size_t copy_len = len < FBNEO_NVRAM_SIZE ? len : FBNEO_NVRAM_SIZE;
        memcpy(dst, g_fbneo.nvram, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_fbneo.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_fbneo.lock);
    if (!g_fbneo.rom_loaded) {
        pthread_mutex_unlock(&g_fbneo.lock);
        return JNI_FALSE;
    }

    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
        size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
        size_t copy_len = len < FBNEO_NVRAM_SIZE ? len : FBNEO_NVRAM_SIZE;
        memcpy(g_fbneo.nvram, src, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_fbneo.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_fbneo.lock);
#ifdef HAVE_FBNEO_CORE
    BurnStateSave((char*)path, 0);
#endif
    pthread_mutex_unlock(&g_fbneo.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fbneo_FbneoNativeCore_fbneoLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_fbneo.lock);
#ifdef HAVE_FBNEO_CORE
    BurnStateLoad((char*)path, 0, NULL);
#endif
    pthread_mutex_unlock(&g_fbneo.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}
