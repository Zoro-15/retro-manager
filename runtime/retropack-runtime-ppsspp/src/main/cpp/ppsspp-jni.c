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

#define LOG_TAG "RetroPack-PPSSPP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define PSP_MAX_WIDTH 1920
#define PSP_MAX_HEIGHT 1088
#define PSP_DEFAULT_WIDTH 480
#define PSP_DEFAULT_HEIGHT 272
#define AUDIO_BUFFER_CAPACITY (32 * 1024)
#define PSP_SRAM_SIZE 0x100000 // 1 MB standard Memory Stick save partition

#ifdef HAVE_PPSSPP_CORE
// Real upstream PPSSPP C++ headers
#include <Core/System.h>
#include <Core/Config.h>
#include <GPU/GPUState.h>
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    uint32_t video_buffer[PSP_MAX_WIDTH * PSP_MAX_HEIGHT];
    int video_width;
    int video_height;
    uint8_t sram[PSP_SRAM_SIZE];
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    int16_t stick_x;
    int16_t stick_y;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_ppsspp = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .video_buffer = {0},
    .video_width = PSP_DEFAULT_WIDTH,
    .video_height = PSP_DEFAULT_HEIGHT,
    .sram = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = PSP_SRAM_SIZE,
    .key_mask = 0,
    .stick_x = 0,
    .stick_y = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

/**
 * Maps RetroKey bitmask to PSP hardware controller bitmask.
 */
static inline uint32_t map_retro_keys_to_psp(uint32_t mask) {
    uint32_t pad = 0;

    // D-Pad
    if (mask & (1 << 6)) pad |= 0x00000001; // UP
    if (mask & (1 << 4)) pad |= 0x00000002; // RIGHT
    if (mask & (1 << 7)) pad |= 0x00000004; // DOWN
    if (mask & (1 << 5)) pad |= 0x00000008; // LEFT

    // Triggers
    if (mask & (1 << 9)) pad |= 0x00000010; // L
    if (mask & (1 << 8)) pad |= 0x00000020; // R

    // Face buttons
    if (mask & (1 << 10)) pad |= 0x00001000; // Triangle (RetroKey.X)
    if (mask & (1 << 0))  pad |= 0x00002000; // Circle (RetroKey.A)
    if (mask & (1 << 1))  pad |= 0x00004000; // Cross (RetroKey.B)
    if (mask & (1 << 11)) pad |= 0x00008000; // Square (RetroKey.Y)

    // System
    if (mask & (1 << 2)) pad |= 0x00010000; // SELECT
    if (mask & (1 << 3)) pad |= 0x00020000; // START

    return pad;
}

static void init_jni_cache(JNIEnv* env) {
    if (g_ppsspp.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_ppsspp.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_ppsspp.byte_buffer_order = (*env)->GetMethodID(env, g_ppsspp.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_ppsspp.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_ppsspp.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_ppsspp.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);

    if (g_ppsspp.initialized) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_ppsspp.storage_path, path, sizeof(g_ppsspp.storage_path) - 1);
            g_ppsspp.storage_path[sizeof(g_ppsspp.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_ppsspp.audio_rb) {
        g_ppsspp.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_PPSSPP_CORE
    // Upstream PPSSPP Core initialization
    NativeInit(0, NULL, "", "", g_ppsspp.storage_path);
#endif

    g_ppsspp.initialized = true;
    LOGI("PPSSPP native runtime initialized (storage: %s)", g_ppsspp.storage_path);

    pthread_mutex_unlock(&g_ppsspp.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_ppsspp.lock);

#ifdef HAVE_PPSSPP_CORE
    std::string error_string;
    if (!PSP_Init(native_path, &error_string)) {
        LOGE("PPSSPP failed to load game: %s (%s)", native_path, error_string.c_str());
        pthread_mutex_unlock(&g_ppsspp.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }
#endif

    g_ppsspp.rom_loaded = true;
    g_ppsspp.video_width = PSP_DEFAULT_WIDTH;
    g_ppsspp.video_height = PSP_DEFAULT_HEIGHT;

    LOGI("PPSSPP loaded PSP game: %s (%dx%d)", native_path, g_ppsspp.video_width, g_ppsspp.video_height);
    pthread_mutex_unlock(&g_ppsspp.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);
#ifdef HAVE_PPSSPP_CORE
    if (g_ppsspp.rom_loaded) {
        PSP_Shutdown();
    }
#endif
    g_ppsspp.rom_loaded = false;
    if (g_ppsspp.audio_rb) ringbuffer_reset(g_ppsspp.audio_rb);
    pthread_mutex_unlock(&g_ppsspp.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);
#ifdef HAVE_PPSSPP_CORE
    NativeShutdown();
#endif
    if (g_ppsspp.audio_rb) {
        ringbuffer_destroy(g_ppsspp.audio_rb);
        g_ppsspp.audio_rb = NULL;
    }
    g_ppsspp.initialized = false;
    g_ppsspp.rom_loaded = false;
    pthread_mutex_unlock(&g_ppsspp.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);
    if (!g_ppsspp.rom_loaded) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_PPSSPP_CORE
    // 1. Pass input state
    uint32_t pad = map_retro_keys_to_psp(g_ppsspp.key_mask);
    (void)pad;

    // 2. Emulate 1 frame
    NativeRender(NULL);
#endif

    pthread_mutex_unlock(&g_ppsspp.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_ppsspp.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);
    if (!g_ppsspp.rom_loaded) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_ppsspp.video_width * g_ppsspp.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_ppsspp.video_buffer, byte_capacity);
    if (!direct_bb || !g_ppsspp.byte_buffer_class || !g_ppsspp.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return NULL;
    }

    if (g_ppsspp.byte_order_native && g_ppsspp.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_ppsspp.byte_buffer_order, g_ppsspp.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_ppsspp.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_ppsspp.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_ppsspp.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_ppsspp.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_ppsspp.audio_rb) return 0;
    return (jint) ringbuffer_available(g_ppsspp.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_ppsspp.lock);
    if (!g_ppsspp.rom_loaded) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_ppsspp.video_width, g_ppsspp.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_ppsspp.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_ppsspp.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_ppsspp.lock);
    if (!g_ppsspp.rom_loaded) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return JNI_FALSE;
    }

    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
        size_t copy_len = len < PSP_SRAM_SIZE ? len : PSP_SRAM_SIZE;
        memcpy(dst, g_ppsspp.sram, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_ppsspp.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_ppsspp.lock);
    if (!g_ppsspp.rom_loaded) {
        pthread_mutex_unlock(&g_ppsspp.lock);
        return JNI_FALSE;
    }

    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
        size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
        size_t copy_len = len < PSP_SRAM_SIZE ? len : PSP_SRAM_SIZE;
        memcpy(g_ppsspp.sram, src, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_ppsspp.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_ppsspp.lock);
#ifdef HAVE_PPSSPP_CORE
    SaveState::Save(path);
#endif
    pthread_mutex_unlock(&g_ppsspp.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_ppsspp_PpssppNativeCore_ppssppLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_ppsspp.lock);
#ifdef HAVE_PPSSPP_CORE
    SaveState::Load(path);
#endif
    pthread_mutex_unlock(&g_ppsspp.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}
