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

#define LOG_TAG "RetroPack-Mupen64"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define N64_MAX_WIDTH 640
#define N64_MAX_HEIGHT 480
#define N64_DEFAULT_WIDTH 640
#define N64_DEFAULT_HEIGHT 480
#define AUDIO_BUFFER_CAPACITY (32 * 1024)
#define N64_SRAM_SIZE 0x20000 // 128 KB FlashRAM / Controller Pak

#ifdef HAVE_MUPEN64_CORE
// Real upstream Mupen64Plus C headers
#include <m64p_types.h>
#include <m64p_frontend.h>
#include <m64p_core.h>
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    uint32_t video_buffer[N64_MAX_WIDTH * N64_MAX_HEIGHT];
    int video_width;
    int video_height;
    uint8_t sram[N64_SRAM_SIZE];
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
} g_mupen = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .video_buffer = {0},
    .video_width = N64_DEFAULT_WIDTH,
    .video_height = N64_DEFAULT_HEIGHT,
    .sram = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = N64_SRAM_SIZE,
    .key_mask = 0,
    .stick_x = 0,
    .stick_y = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

/**
 * Maps RetroKey bitmask to Nintendo 64 Controller 16-bit bitmask.
 *
 * N64 Standard Button Bitmask:
 * - 0x8000 : A
 * - 0x4000 : B
 * - 0x2000 : Z
 * - 0x1000 : START
 * - 0x0800 : D-Pad UP
 * - 0x0400 : D-Pad DOWN
 * - 0x0200 : D-Pad LEFT
 * - 0x0100 : D-Pad RIGHT
 * - 0x0020 : L trigger
 * - 0x0010 : R trigger
 * - 0x0008 : C-Up
 * - 0x0004 : C-Down
 * - 0x0002 : C-Left
 * - 0x0001 : C-Right
 */
static inline uint16_t map_retro_keys_to_n64(uint32_t mask) {
    uint16_t pad = 0;

    if (mask & (1 << 0))  pad |= 0x8000; // A (RetroKey.A)
    if (mask & (1 << 1))  pad |= 0x4000; // B (RetroKey.B)
    if ((mask & (1 << 13)) || (mask & (1 << 14))) pad |= 0x2000; // Z trigger (RetroKey.Z / RetroKey.L2)
    if (mask & (1 << 3))  pad |= 0x1000; // START
    if (mask & (1 << 6))  pad |= 0x0800; // D-Pad UP
    if (mask & (1 << 7))  pad |= 0x0400; // D-Pad DOWN
    if (mask & (1 << 5))  pad |= 0x0200; // D-Pad LEFT
    if (mask & (1 << 4))  pad |= 0x0100; // D-Pad RIGHT
    if (mask & (1 << 9))  pad |= 0x0020; // L trigger
    if (mask & (1 << 8))  pad |= 0x0010; // R trigger

    // C-Buttons
    if (mask & (1 << 19)) pad |= 0x0008; // C-Up
    if (mask & (1 << 20)) pad |= 0x0004; // C-Down
    if (mask & (1 << 21)) pad |= 0x0002; // C-Left
    if (mask & (1 << 22)) pad |= 0x0001; // C-Right
    if (mask & (1 << 10)) pad |= 0x0008; // X fallback -> C-Up
    if (mask & (1 << 11)) pad |= 0x0002; // Y fallback -> C-Left

    return pad;
}

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

#ifdef HAVE_MUPEN64_CORE
    // Upstream Mupen64Plus Core initialization
    CoreStartup(CORE_API_VERSION, g_mupen.storage_path, NULL, NULL, NULL, NULL, NULL);
#endif

    g_mupen.initialized = true;
    LOGI("Mupen64Plus native runtime initialized (storage: %s)", g_mupen.storage_path);

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

#ifdef HAVE_MUPEN64_CORE
    if (CoreDoCommand(M64CMD_ROM_OPEN, (int)strlen(native_path), (void*)native_path) != M64ERR_SUCCESS) {
        LOGE("Mupen64Plus failed to load ROM: %s", native_path);
        pthread_mutex_unlock(&g_mupen.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }
#endif

    g_mupen.rom_loaded = true;
    g_mupen.video_width = N64_DEFAULT_WIDTH;
    g_mupen.video_height = N64_DEFAULT_HEIGHT;

    LOGI("Mupen64Plus loaded N64 ROM: %s (%dx%d)", native_path, g_mupen.video_width, g_mupen.video_height);
    pthread_mutex_unlock(&g_mupen.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
#ifdef HAVE_MUPEN64_CORE
    if (g_mupen.rom_loaded) {
        CoreDoCommand(M64CMD_ROM_CLOSE, 0, NULL);
    }
#endif
    g_mupen.rom_loaded = false;
    if (g_mupen.audio_rb) ringbuffer_reset(g_mupen.audio_rb);
    pthread_mutex_unlock(&g_mupen.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_mupen.lock);
#ifdef HAVE_MUPEN64_CORE
    CoreShutdown();
#endif
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

#ifdef HAVE_MUPEN64_CORE
    // 1. Pass input state
    uint16_t pad = map_retro_keys_to_n64(g_mupen.key_mask);
    (void)pad;

    // 2. Advance 1 frame
    CoreDoCommand(M64CMD_ADVANCE_FRAME, 0, NULL);
#endif

    pthread_mutex_unlock(&g_mupen.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_mupen.key_mask = (uint32_t) keyMask;
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
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_mupen.lock);
    if (!g_mupen.rom_loaded) {
        pthread_mutex_unlock(&g_mupen.lock);
        return JNI_FALSE;
    }

    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
        size_t copy_len = len < N64_SRAM_SIZE ? len : N64_SRAM_SIZE;
        memcpy(dst, g_mupen.sram, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_mupen.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_mupen.lock);
    if (!g_mupen.rom_loaded) {
        pthread_mutex_unlock(&g_mupen.lock);
        return JNI_FALSE;
    }

    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
        size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
        size_t copy_len = len < N64_SRAM_SIZE ? len : N64_SRAM_SIZE;
        memcpy(g_mupen.sram, src, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_mupen.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_mupen.lock);
#ifdef HAVE_MUPEN64_CORE
    CoreDoCommand(M64CMD_STATE_SAVE, 0, (void*)path);
#endif
    pthread_mutex_unlock(&g_mupen.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_mupen64_Mupen64NativeCore_mupenLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_mupen.lock);
#ifdef HAVE_MUPEN64_CORE
    CoreDoCommand(M64CMD_STATE_LOAD, 0, (void*)path);
#endif
    pthread_mutex_unlock(&g_mupen.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}
