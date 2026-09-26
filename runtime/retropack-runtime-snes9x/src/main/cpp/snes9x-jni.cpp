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

#define LOG_TAG "RetroPack-Snes9x"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define SNES_MAX_WIDTH 512
#define SNES_MAX_HEIGHT 448
#define AUDIO_BUFFER_CAPACITY (16 * 1024)

#ifdef HAVE_SNES9X_CORE
// Real upstream Snes9x C++ headers
#include <snes9x.h>
#include <memmap.h>
#include <apu.h>
#include <controls.h>
#include <gfx.h>
#include <snapshot.h>
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    uint32_t video_buffer[SNES_MAX_WIDTH * SNES_MAX_HEIGHT];
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
} g_snes9x = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .video_buffer = {0},
    .video_width = 256,
    .video_height = 224,
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = 0x20000, // 128 KB standard SNES SRAM
    .key_mask = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

static inline uint32_t map_retro_keys_to_snes(uint32_t mask) {
    uint32_t snes_pad = 0;
    if (mask & (1 << 0))  snes_pad |= 0x0080; // A
    if (mask & (1 << 1))  snes_pad |= 0x8000; // B
    if (mask & (1 << 2))  snes_pad |= 0x2000; // SELECT
    if (mask & (1 << 3))  snes_pad |= 0x1000; // START
    if (mask & (1 << 4))  snes_pad |= 0x0100; // RIGHT
    if (mask & (1 << 5))  snes_pad |= 0x0200; // LEFT
    if (mask & (1 << 6))  snes_pad |= 0x0800; // UP
    if (mask & (1 << 7))  snes_pad |= 0x0400; // DOWN
    if (mask & (1 << 8))  snes_pad |= 0x0010; // R
    if (mask & (1 << 9))  snes_pad |= 0x0020; // L
    if (mask & (1 << 10)) snes_pad |= 0x0040; // X
    if (mask & (1 << 11)) snes_pad |= 0x4000; // Y
    return snes_pad;
}

static void init_jni_cache(JNIEnv* env) {
    if (g_snes9x.byte_buffer_class) return;
    jclass bb_local = env->FindClass("java/nio/ByteBuffer");
    if (!bb_local) return;
    g_snes9x.byte_buffer_class = (jclass)env->NewGlobalRef(bb_local);
    env->DeleteLocalRef(bb_local);

    g_snes9x.byte_buffer_order = env->GetMethodID(g_snes9x.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_snes9x.byte_buffer_as_int_buffer = env->GetMethodID(g_snes9x.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = env->FindClass("java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = env->GetStaticMethodID(bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = env->CallStaticObjectMethod(bo_class, bo_native);
            if (order_local) {
                g_snes9x.byte_order_native = env->NewGlobalRef(order_local);
                env->DeleteLocalRef(order_local);
            }
        }
        env->DeleteLocalRef(bo_class);
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_snes9x.lock);

    if (g_snes9x.initialized) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = env->GetStringUTFChars(internalStoragePath, NULL);
        if (path) {
            strncpy(g_snes9x.storage_path, path, sizeof(g_snes9x.storage_path) - 1);
            g_snes9x.storage_path[sizeof(g_snes9x.storage_path) - 1] = '\0';
            env->ReleaseStringUTFChars(internalStoragePath, path);
        }
    }

    if (!g_snes9x.audio_rb) {
        g_snes9x.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_SNES9X_CORE
    memset(&Settings, 0, sizeof(Settings));
    Settings.CyclesPercentage = 100;
    Settings.DisableSound = false;
    Settings.SoundPlaybackRate = 44100;
    Settings.SoundInputRate = 32000;
    Settings.SixteenBitSound = true;
    Settings.Stereo = true;
    Settings.Transparency = true;
    Settings.SupportHiRes = true;
    Memory.Init();
    S9xInitAPU();
    S9xInitSound(44100, 0);
    S9xSetSoundMute(false);
    S9xGraphicsInit();
#endif

    g_snes9x.initialized = true;
    LOGI("Snes9x native runtime initialized (storage: %s)", g_snes9x.storage_path);

    pthread_mutex_unlock(&g_snes9x.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = env->GetStringUTFChars(romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_snes9x.lock);

#ifdef HAVE_SNES9X_CORE
    bool loaded = Memory.LoadROM(native_path);
    if (!loaded) {
        LOGE("Snes9x failed to load ROM: %s", native_path);
        pthread_mutex_unlock(&g_snes9x.lock);
        env->ReleaseStringUTFChars(romPath, native_path);
        return JNI_FALSE;
    }
    S9xReset();
#endif

    g_snes9x.rom_loaded = true;
    g_snes9x.video_width = 256;
    g_snes9x.video_height = 224;

    LOGI("Snes9x loaded SNES ROM: %s", native_path);
    pthread_mutex_unlock(&g_snes9x.lock);
    env->ReleaseStringUTFChars(romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_snes9x.lock);
#ifdef HAVE_SNES9X_CORE
    if (g_snes9x.rom_loaded) {
        Memory.ClearSRAM();
    }
#endif
    g_snes9x.rom_loaded = false;
    if (g_snes9x.audio_rb) ringbuffer_reset(g_snes9x.audio_rb);
    pthread_mutex_unlock(&g_snes9x.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_snes9x.lock);
#ifdef HAVE_SNES9X_CORE
    Memory.Deinit();
    S9xDeinitAPU();
    S9xGraphicsDeinit();
#endif
    if (g_snes9x.audio_rb) {
        ringbuffer_destroy(g_snes9x.audio_rb);
        g_snes9x.audio_rb = NULL;
    }
    g_snes9x.initialized = false;
    g_snes9x.rom_loaded = false;
    pthread_mutex_unlock(&g_snes9x.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_snes9x.lock);
    if (!g_snes9x.rom_loaded) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_SNES9X_CORE
    // 1. Pass Joypad 1 key states
    uint32_t snes_keys = map_retro_keys_to_snes(g_snes9x.key_mask);
    Movie.Pad[0] = snes_keys;

    // 2. Run emulation loop for 1 frame
    S9xMainLoop();

    // 3. Resample sound into ring buffer
    int16_t sound_buf[2048];
    int samples = S9xMixSamples(sound_buf, 1024);
    if (samples > 0 && g_snes9x.audio_rb) {
        ringbuffer_write(g_snes9x.audio_rb, sound_buf, samples * 2);
    }
#endif

    pthread_mutex_unlock(&g_snes9x.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_snes9x.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_snes9x.lock);
    if (!g_snes9x.rom_loaded) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_snes9x.video_width * g_snes9x.video_height * sizeof(uint32_t));
    jobject direct_bb = env->NewDirectByteBuffer(g_snes9x.video_buffer, byte_capacity);
    if (!direct_bb || !g_snes9x.byte_buffer_class || !g_snes9x.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return NULL;
    }

    if (g_snes9x.byte_order_native && g_snes9x.byte_buffer_order) {
        env->CallObjectMethod(direct_bb, g_snes9x.byte_buffer_order, g_snes9x.byte_order_native);
    }
    jobject int_buffer = env->CallObjectMethod(direct_bb, g_snes9x.byte_buffer_as_int_buffer);
    env->DeleteLocalRef(direct_bb);

    pthread_mutex_unlock(&g_snes9x.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_snes9x.audio_rb) return 0;
    jshort* dst = (jshort*)env->GetPrimitiveArrayCritical(outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_snes9x.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    env->ReleasePrimitiveArrayCritical(outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_snes9x.audio_rb) return 0;
    return (jint) ringbuffer_available(g_snes9x.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_snes9x.lock);
    if (!g_snes9x.rom_loaded) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return NULL;
    }
    jintArray result = env->NewIntArray(2);
    if (result) {
        jint dims[2] = { g_snes9x.video_width, g_snes9x.video_height };
        env->SetIntArrayRegion(result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_snes9x.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_snes9x.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_snes9x.lock);
    if (!g_snes9x.rom_loaded) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_SNES9X_CORE
    jbyte* dst = (jbyte*)env->GetPrimitiveArrayCritical(outBuffer, NULL);
    if (dst) {
        size_t copy_len = (size_t)env->GetArrayLength(outBuffer);
        if (copy_len > 0x20000) copy_len = 0x20000;
        memcpy(dst, Memory.SRAM, copy_len);
        env->ReleasePrimitiveArrayCritical(outBuffer, dst, 0);
    }
#endif

    pthread_mutex_unlock(&g_snes9x.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_snes9x.lock);
    if (!g_snes9x.rom_loaded) {
        pthread_mutex_unlock(&g_snes9x.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_SNES9X_CORE
    jbyte* src = (jbyte*)env->GetPrimitiveArrayCritical(inBuffer, NULL);
    if (src) {
        size_t copy_len = (size_t)env->GetArrayLength(inBuffer);
        if (copy_len > 0x20000) copy_len = 0x20000;
        memcpy(Memory.SRAM, src, copy_len);
        env->ReleasePrimitiveArrayCritical(inBuffer, src, 0);
    }
#endif

    pthread_mutex_unlock(&g_snes9x.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_snes9x.lock);
#ifdef HAVE_SNES9X_CORE
    bool ok = S9xFreezeGame(path);
#else
    bool ok = true;
#endif
    pthread_mutex_unlock(&g_snes9x.lock);
    env->ReleaseStringUTFChars(filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_snes_Snes9xNativeCore_snesLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_snes9x.lock);
#ifdef HAVE_SNES9X_CORE
    bool ok = S9xUnfreezeGame(path);
#else
    bool ok = true;
#endif
    pthread_mutex_unlock(&g_snes9x.lock);
    env->ReleaseStringUTFChars(filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
