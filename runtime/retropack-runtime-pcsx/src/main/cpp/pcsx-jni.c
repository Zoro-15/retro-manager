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

#define LOG_TAG "RetroPack-PCSX"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define PSX_MAX_WIDTH 640
#define PSX_MAX_HEIGHT 480
#define PSX_DEFAULT_WIDTH 320
#define PSX_DEFAULT_HEIGHT 240
#define AUDIO_BUFFER_CAPACITY (32 * 1024)
#define PSX_MCD_SIZE 0x20000 // 128 KB PS1 Memory Card 1

#ifdef HAVE_PCSX_CORE
// Real upstream PCSX ReARMed C headers
#include <psxcommon.h>
#include <r3000a.h>
#include <gpureg.h>
#include <spu.h>
#include <sio.h>
#include <cdrom.h>
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    uint32_t video_buffer[PSX_MAX_WIDTH * PSX_MAX_HEIGHT];
    int video_width;
    int video_height;
    uint8_t mcd[PSX_MCD_SIZE];
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    int16_t analog_lx;
    int16_t analog_ly;
    int16_t analog_rx;
    int16_t analog_ry;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_pcsx = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .video_buffer = {0},
    .video_width = PSX_DEFAULT_WIDTH,
    .video_height = PSX_DEFAULT_HEIGHT,
    .mcd = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = PSX_MCD_SIZE,
    .key_mask = 0,
    .analog_lx = 0,
    .analog_ly = 0,
    .analog_rx = 0,
    .analog_ry = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

/**
 * Maps RetroKey bitmask to PlayStation DualShock 14-button bitmask.
 *
 * PSX Standard Joypad Active-Low / Active-High Mask:
 * - 0x0001 : L2
 * - 0x0002 : R2
 * - 0x0004 : L1
 * - 0x0008 : R1
 * - 0x0010 : Triangle (X)
 * - 0x0020 : Circle (A)
 * - 0x0040 : Cross (B)
 * - 0x0080 : Square (Y)
 * - 0x0100 : SELECT
 * - 0x0200 : L3
 * - 0x0300 : R3
 * - 0x0800 : START
 * - 0x1000 : UP
 * - 0x2000 : RIGHT
 * - 0x4000 : DOWN
 * - 0x8000 : LEFT
 */
static inline uint16_t map_retro_keys_to_psx(uint32_t mask) {
    uint16_t pad = 0;

    // Face buttons
    if (mask & (1 << 10)) pad |= 0x0010; // Triangle (RetroKey.X)
    if (mask & (1 << 0))  pad |= 0x0020; // Circle (RetroKey.A)
    if (mask & (1 << 1))  pad |= 0x0040; // Cross (RetroKey.B)
    if (mask & (1 << 11)) pad |= 0x0080; // Square (RetroKey.Y)

    // Shoulders & Triggers
    if (mask & (1 << 9))  pad |= 0x0004; // L1 (RetroKey.L)
    if (mask & (1 << 8))  pad |= 0x0008; // R1 (RetroKey.R)
    if (mask & (1 << 14)) pad |= 0x0001; // L2 (RetroKey.L2)
    if (mask & (1 << 15)) pad |= 0x0002; // R2 (RetroKey.R2)

    // System & Stick clicks
    if (mask & (1 << 2))  pad |= 0x0100; // SELECT
    if (mask & (1 << 16)) pad |= 0x0200; // L3
    if (mask & (1 << 17)) pad |= 0x0400; // R3
    if (mask & (1 << 3))  pad |= 0x0800; // START

    // D-Pad
    if (mask & (1 << 6))  pad |= 0x1000; // UP
    if (mask & (1 << 4))  pad |= 0x2000; // RIGHT
    if (mask & (1 << 7))  pad |= 0x4000; // DOWN
    if (mask & (1 << 5))  pad |= 0x8000; // LEFT

    return pad;
}

static void init_jni_cache(JNIEnv* env) {
    if (g_pcsx.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_pcsx.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_pcsx.byte_buffer_order = (*env)->GetMethodID(env, g_pcsx.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_pcsx.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_pcsx.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_pcsx.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);

    if (g_pcsx.initialized) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_pcsx.storage_path, path, sizeof(g_pcsx.storage_path) - 1);
            g_pcsx.storage_path[sizeof(g_pcsx.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_pcsx.audio_rb) {
        g_pcsx.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_PCSX_CORE
    // Upstream PCSX ReARMed HLE initialization
    Config.Hle = 1; // Direct HLE BIOS boot without scph5501.bin requirement
    Config.Xa = 1;
    Config.Cdda = 1;
    Config.RCntFix = 0;
    Config.Cpu = 0; // Dynamic recompiler
    psxInit();
#endif

    g_pcsx.initialized = true;
    LOGI("PCSX ReARMed native runtime initialized (storage: %s)", g_pcsx.storage_path);

    pthread_mutex_unlock(&g_pcsx.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_pcsx.lock);

#ifdef HAVE_PCSX_CORE
    if (psxLoad(native_path) != 0) {
        LOGE("PCSX ReARMed failed to load disc/ISO: %s", native_path);
        pthread_mutex_unlock(&g_pcsx.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }
    psxReset();
#endif

    g_pcsx.rom_loaded = true;
    g_pcsx.video_width = PSX_DEFAULT_WIDTH;
    g_pcsx.video_height = PSX_DEFAULT_HEIGHT;

    LOGI("PCSX ReARMed loaded disc/ISO: %s (%dx%d)", native_path, g_pcsx.video_width, g_pcsx.video_height);
    pthread_mutex_unlock(&g_pcsx.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);
#ifdef HAVE_PCSX_CORE
    if (g_pcsx.rom_loaded) {
        psxShutdown();
    }
#endif
    g_pcsx.rom_loaded = false;
    if (g_pcsx.audio_rb) ringbuffer_reset(g_pcsx.audio_rb);
    pthread_mutex_unlock(&g_pcsx.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);
#ifdef HAVE_PCSX_CORE
    psxShutdown();
#endif
    if (g_pcsx.audio_rb) {
        ringbuffer_destroy(g_pcsx.audio_rb);
        g_pcsx.audio_rb = NULL;
    }
    g_pcsx.initialized = false;
    g_pcsx.rom_loaded = false;
    pthread_mutex_unlock(&g_pcsx.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);
    if (!g_pcsx.rom_loaded) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_PCSX_CORE
    // 1. Pass Joypad 1 buttons and analog sticks
    uint16_t pad = map_retro_keys_to_psx(g_pcsx.key_mask);
    PAD1_Buttons = pad;

    // 2. Emulate 1 system frame
    psxCpu->Execute();

    // 3. Audio stream into ring buffer
    int16_t sound_buf[2048];
    int samples = SPU_GetSamples(sound_buf, 1024);
    if (samples > 0 && g_pcsx.audio_rb) {
        ringbuffer_write(g_pcsx.audio_rb, sound_buf, samples * 2);
    }
#endif

    pthread_mutex_unlock(&g_pcsx.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_pcsx.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);
    if (!g_pcsx.rom_loaded) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_pcsx.video_width * g_pcsx.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_pcsx.video_buffer, byte_capacity);
    if (!direct_bb || !g_pcsx.byte_buffer_class || !g_pcsx.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return NULL;
    }

    if (g_pcsx.byte_order_native && g_pcsx.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_pcsx.byte_buffer_order, g_pcsx.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_pcsx.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_pcsx.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_pcsx.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_pcsx.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_pcsx.audio_rb) return 0;
    return (jint) ringbuffer_available(g_pcsx.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_pcsx.lock);
    if (!g_pcsx.rom_loaded) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_pcsx.video_width, g_pcsx.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_pcsx.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_pcsx.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_pcsx.lock);
    if (!g_pcsx.rom_loaded) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return JNI_FALSE;
    }

    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
        size_t copy_len = len < PSX_MCD_SIZE ? len : PSX_MCD_SIZE;
#ifdef HAVE_PCSX_CORE
        if (Mcd1Data) {
            memcpy(dst, Mcd1Data, copy_len);
        } else {
            memcpy(dst, g_pcsx.mcd, copy_len);
        }
#else
        memcpy(dst, g_pcsx.mcd, copy_len);
#endif
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_pcsx.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_pcsx.lock);
    if (!g_pcsx.rom_loaded) {
        pthread_mutex_unlock(&g_pcsx.lock);
        return JNI_FALSE;
    }

    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
        size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
        size_t copy_len = len < PSX_MCD_SIZE ? len : PSX_MCD_SIZE;
#ifdef HAVE_PCSX_CORE
        if (Mcd1Data) {
            memcpy(Mcd1Data, src, copy_len);
        }
#endif
        memcpy(g_pcsx.mcd, src, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_pcsx.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_pcsx.lock);
#ifdef HAVE_PCSX_CORE
    SaveState(path);
#endif
    pthread_mutex_unlock(&g_pcsx.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pcsx_PcsxNativeCore_pcsxLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_pcsx.lock);
#ifdef HAVE_PCSX_CORE
    LoadState(path);
#endif
    pthread_mutex_unlock(&g_pcsx.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}
