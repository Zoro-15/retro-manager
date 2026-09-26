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

#define LOG_TAG "RetroPack-FCEUmm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define NES_WIDTH 256
#define NES_HEIGHT 240
#define AUDIO_BUFFER_CAPACITY (16 * 1024)

#ifdef HAVE_FCEUMM_CORE
// Real upstream FCEUmm headers
#include <types.h>
#include <fceu.h>
#include <cart.h>
#include <sound.h>
#include <state.h>
#include <driver.h>
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    uint32_t video_buffer[NES_WIDTH * NES_HEIGHT];
    int video_width;
    int video_height;
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_fceu = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .video_buffer = {0},
    .video_width = NES_WIDTH,
    .video_height = NES_HEIGHT,
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = 0x2000, // 8 KB standard NES battery PRG-RAM
    .key_mask = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

// Canonical 2C02 / Composite 64-color NES palette (ARGB8888)
static const uint32_t s_nes_palette[64] = {
    0xFF666666, 0xFF002A88, 0xFF1412A7, 0xFF3B00A4, 0xFF5C007E, 0xFF6E0040, 0xFF6C0600, 0xFF561D00,
    0xFF333500, 0xFF0B4800, 0xFF005200, 0xFF004F08, 0xFF00404D, 0xFF000000, 0xFF000000, 0xFF000000,
    0xFFADADAD, 0xFF155FD9, 0xFF4240FF, 0xFF7527FE, 0xFFA01ACC, 0xFFB71E7B, 0xFFB53120, 0xFF994E00,
    0xFF6B6D00, 0xFF388700, 0xFF0C9300, 0xFF008F32, 0xFF007C8D, 0xFF000000, 0xFF000000, 0xFF000000,
    0xFFFFFEFF, 0xFF64B0FF, 0xFF9290FF, 0xFFC676FF, 0xFFF36AFF, 0xFFFE6ECC, 0xFFFE8170, 0xFFEA9E22,
    0xFFBCBE00, 0xFF88D800, 0xFF5CE430, 0xFF45E082, 0xFF48CDDE, 0xFF4F4F4F, 0xFF000000, 0xFF000000,
    0xFFFFFEFF, 0xFFC0DFFF, 0xFFD3D2FF, 0xFFE8C5FF, 0xFFFBC2FF, 0xFFFEC4EA, 0xFFFECCC5, 0xFFF7D8A5,
    0xFFE4E594, 0xFFCFEF96, 0xFFBDF4AB, 0xFFB3F3CC, 0xFFB5EBF2, 0xFFB8B8B8, 0xFF000000, 0xFF000000
};

static inline uint8_t map_retro_keys_to_nes(uint32_t mask) {
    uint8_t nes_pad = 0;
    if (mask & (1 << 0)) nes_pad |= 0x01; // A (JOY_A)
    if (mask & (1 << 1)) nes_pad |= 0x02; // B (JOY_B)
    if (mask & (1 << 2)) nes_pad |= 0x04; // SELECT (JOY_SELECT)
    if (mask & (1 << 3)) nes_pad |= 0x08; // START (JOY_START)
    if (mask & (1 << 6)) nes_pad |= 0x10; // UP (JOY_UP)
    if (mask & (1 << 7)) nes_pad |= 0x20; // DOWN (JOY_DOWN)
    if (mask & (1 << 5)) nes_pad |= 0x40; // LEFT (JOY_LEFT)
    if (mask & (1 << 4)) nes_pad |= 0x80; // RIGHT (JOY_RIGHT)
    // Turbo X (Turbo A) & Turbo Y (Turbo B)
    if (mask & (1 << 10)) nes_pad |= 0x01;
    if (mask & (1 << 11)) nes_pad |= 0x02;
    return nes_pad;
}

static void init_jni_cache(JNIEnv* env) {
    if (g_fceu.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_fceu.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_fceu.byte_buffer_order = (*env)->GetMethodID(env, g_fceu.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_fceu.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_fceu.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_fceu.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_fceu.lock);

    if (g_fceu.initialized) {
        pthread_mutex_unlock(&g_fceu.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_fceu.storage_path, path, sizeof(g_fceu.storage_path) - 1);
            g_fceu.storage_path[sizeof(g_fceu.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_fceu.audio_rb) {
        g_fceu.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_FCEUMM_CORE
    FCEUI_Initialize();
    FCEUI_Sound(44100);
    FCEUI_SetSoundVolume(100);
#endif

    g_fceu.initialized = true;
    LOGI("FCEUmm NES native runtime initialized (storage: %s)", g_fceu.storage_path);

    pthread_mutex_unlock(&g_fceu.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_fceu.lock);

#ifdef HAVE_FCEUMM_CORE
    if (!FCEUI_LoadGame((char*)native_path, 0)) {
        LOGE("FCEUmm failed to load ROM: %s", native_path);
        pthread_mutex_unlock(&g_fceu.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }
    FCEUI_ResetNES();
#endif

    g_fceu.rom_loaded = true;
    g_fceu.video_width = NES_WIDTH;
    g_fceu.video_height = NES_HEIGHT;

    LOGI("FCEUmm loaded NES ROM: %s", native_path);
    pthread_mutex_unlock(&g_fceu.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_fceu.lock);
#ifdef HAVE_FCEUMM_CORE
    if (g_fceu.rom_loaded) {
        FCEUI_CloseGame();
    }
#endif
    g_fceu.rom_loaded = false;
    if (g_fceu.audio_rb) ringbuffer_reset(g_fceu.audio_rb);
    pthread_mutex_unlock(&g_fceu.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_fceu.lock);
#ifdef HAVE_FCEUMM_CORE
    FCEUI_CloseGame();
#endif
    if (g_fceu.audio_rb) {
        ringbuffer_destroy(g_fceu.audio_rb);
        g_fceu.audio_rb = NULL;
    }
    g_fceu.initialized = false;
    g_fceu.rom_loaded = false;
    pthread_mutex_unlock(&g_fceu.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_fceu.lock);
    if (!g_fceu.rom_loaded) {
        pthread_mutex_unlock(&g_fceu.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_FCEUMM_CORE
    // 1. Pass Joypad 1 key inputs
    uint8_t nes_pad = map_retro_keys_to_nes(g_fceu.key_mask);
    FCEU_UpdateInput(0, nes_pad);

    // 2. Emulate 1 frame
    uint8_t* gfx_buf = NULL;
    int32_t* sound_buf = NULL;
    int32_t sound_samples = 0;
    FCEUI_Emulate(&gfx_buf, &sound_buf, &sound_samples, 0);

    // 3. Render / transfer frame into video_buffer
    if (gfx_buf) {
        for (int i = 0; i < NES_WIDTH * NES_HEIGHT; i++) {
            uint8_t color_idx = gfx_buf[i] & 0x3F;
            g_fceu.video_buffer[i] = s_nes_palette[color_idx];
        }
    }

    // 4. Audio stream into ring buffer
    if (sound_buf && sound_samples > 0 && g_fceu.audio_rb) {
        int16_t converted[2048];
        int count = sound_samples < 1024 ? sound_samples : 1024;
        for (int i = 0; i < count; i++) {
            int32_t sample = sound_buf[i];
            if (sample > 32767) sample = 32767;
            if (sample < -32768) sample = -32768;
            converted[i * 2] = (int16_t)sample;
            converted[i * 2 + 1] = (int16_t)sample;
        }
        ringbuffer_write(g_fceu.audio_rb, converted, count * 2);
    }
#endif

    pthread_mutex_unlock(&g_fceu.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_fceu.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_fceu.lock);
    if (!g_fceu.rom_loaded) {
        pthread_mutex_unlock(&g_fceu.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_fceu.video_width * g_fceu.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_fceu.video_buffer, byte_capacity);
    if (!direct_bb || !g_fceu.byte_buffer_class || !g_fceu.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_fceu.lock);
        return NULL;
    }

    if (g_fceu.byte_order_native && g_fceu.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_fceu.byte_buffer_order, g_fceu.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_fceu.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_fceu.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_fceu.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_fceu.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_fceu.audio_rb) return 0;
    return (jint) ringbuffer_available(g_fceu.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_fceu.lock);
    if (!g_fceu.rom_loaded) {
        pthread_mutex_unlock(&g_fceu.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_fceu.video_width, g_fceu.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_fceu.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_fceu.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_fceu.lock);
    if (!g_fceu.rom_loaded) {
        pthread_mutex_unlock(&g_fceu.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_FCEUMM_CORE
    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
        size_t copy_len = len < 0x2000 ? len : 0x2000;
        if (CartSaveData) {
            memcpy(dst, CartSaveData, copy_len);
        }
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }
#endif

    pthread_mutex_unlock(&g_fceu.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_fceu.lock);
    if (!g_fceu.rom_loaded) {
        pthread_mutex_unlock(&g_fceu.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_FCEUMM_CORE
    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
        size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
        size_t copy_len = len < 0x2000 ? len : 0x2000;
        if (CartSaveData) {
            memcpy(CartSaveData, src, copy_len);
        }
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }
#endif

    pthread_mutex_unlock(&g_fceu.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_fceu.lock);
#ifdef HAVE_FCEUMM_CORE
    FCEUI_SaveState((char*)path);
#endif
    pthread_mutex_unlock(&g_fceu.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_fceumm_FceummNativeCore_fceuLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_fceu.lock);
#ifdef HAVE_FCEUMM_CORE
    FCEUI_LoadState((char*)path);
#endif
    pthread_mutex_unlock(&g_fceu.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}
