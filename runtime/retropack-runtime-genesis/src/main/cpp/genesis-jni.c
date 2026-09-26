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

#define LOG_TAG "RetroPack-Genesis"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define GENESIS_MAX_WIDTH 320
#define GENESIS_MAX_HEIGHT 240
#define AUDIO_BUFFER_CAPACITY (16 * 1024)

#ifdef HAVE_GENESIS_CORE
// Real upstream Genesis Plus GX C headers
#include <shared.h>
#include <genesis.h>
#include <sound.h>
#include <vdp_ctrl.h>
#include <system.h>
#include <state.h>
#include <loadrom.h>
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    uint32_t video_buffer[GENESIS_MAX_WIDTH * GENESIS_MAX_HEIGHT];
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
} g_genesis = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .video_buffer = {0},
    .video_width = 320,
    .video_height = 224,
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = 0x10000, // 64 KB standard SRAM
    .key_mask = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

static inline uint16_t map_retro_keys_to_genesis(uint32_t mask) {
    uint16_t pad = 0;
    if (mask & (1 << 0))  pad |= 0x0040; // A
    if (mask & (1 << 1))  pad |= 0x0010; // B
    if (mask & (1 << 12)) pad |= 0x0020; // C
    if (mask & (1 << 10)) pad |= 0x0400; // X
    if (mask & (1 << 11)) pad |= 0x0200; // Y
    if (mask & (1 << 13)) pad |= 0x0100; // Z
    if (mask & (1 << 3))  pad |= 0x0080; // START
    if ((mask & (1 << 2)) || (mask & (1 << 18))) pad |= 0x0800; // SELECT / MODE
    if (mask & (1 << 4))  pad |= 0x0008; // RIGHT
    if (mask & (1 << 5))  pad |= 0x0004; // LEFT
    if (mask & (1 << 6))  pad |= 0x0001; // UP
    if (mask & (1 << 7))  pad |= 0x0002; // DOWN
    return pad;
}

static void init_jni_cache(JNIEnv* env) {
    if (g_genesis.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_genesis.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_genesis.byte_buffer_order = (*env)->GetMethodID(env, g_genesis.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_genesis.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_genesis.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_genesis.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_genesis.lock);

    if (g_genesis.initialized) {
        pthread_mutex_unlock(&g_genesis.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_genesis.storage_path, path, sizeof(g_genesis.storage_path) - 1);
            g_genesis.storage_path[sizeof(g_genesis.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_genesis.audio_rb) {
        g_genesis.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_GENESIS_CORE
    set_config_defaults();
    config.psg_preamp = 150;
    config.fm_preamp = 100;
    config.hq_fm = 1;
    config.psg_boost_noise = 1;
    config.filter = 1;
    config.lp_range = 0x9999;
    config.low_freq = 880;
    config.high_freq = 5000;
    config.lg = 1.0;
    config.mg = 1.0;
    config.hg = 1.0;
    config.system = 0;
    config.region_detect = 0;
    config.vmode = 0;
    config.overscan = 0;
    audio_init(44100, 60.0);
    system_init();
#endif

    g_genesis.initialized = true;
    LOGI("Genesis Plus GX native runtime initialized (storage: %s)", g_genesis.storage_path);

    pthread_mutex_unlock(&g_genesis.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_genesis.lock);

#ifdef HAVE_GENESIS_CORE
    if (!load_rom((char*)native_path)) {
        LOGE("Genesis Plus GX failed to load ROM: %s", native_path);
        pthread_mutex_unlock(&g_genesis.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }
    system_reset();
    
    // Set active video dimensions based on detected console system
    g_genesis.video_width = bitmap.viewport.w > 0 ? bitmap.viewport.w : 320;
    g_genesis.video_height = bitmap.viewport.h > 0 ? bitmap.viewport.h : 224;
#else
    g_genesis.video_width = 320;
    g_genesis.video_height = 224;
#endif

    g_genesis.rom_loaded = true;
    LOGI("Genesis Plus GX loaded ROM: %s (mode: %dx%d)", native_path, g_genesis.video_width, g_genesis.video_height);
    pthread_mutex_unlock(&g_genesis.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_genesis.lock);
#ifdef HAVE_GENESIS_CORE
    if (g_genesis.rom_loaded) {
        system_shutdown();
    }
#endif
    g_genesis.rom_loaded = false;
    if (g_genesis.audio_rb) ringbuffer_reset(g_genesis.audio_rb);
    pthread_mutex_unlock(&g_genesis.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_genesis.lock);
#ifdef HAVE_GENESIS_CORE
    system_shutdown();
#endif
    if (g_genesis.audio_rb) {
        ringbuffer_destroy(g_genesis.audio_rb);
        g_genesis.audio_rb = NULL;
    }
    g_genesis.initialized = false;
    g_genesis.rom_loaded = false;
    pthread_mutex_unlock(&g_genesis.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_genesis.lock);
    if (!g_genesis.rom_loaded) {
        pthread_mutex_unlock(&g_genesis.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_GENESIS_CORE
    // 1. Pass Joypad 1 buttons (A, B, C, X, Y, Z, Start, Mode, D-Pad)
    input.pad[0] = map_retro_keys_to_genesis(g_genesis.key_mask);

    // 2. Emulate single system frame
    system_frame(0);

    // 3. Audio stream into ring buffer
    int16_t audio_samples[2048];
    int size = audio_update(audio_samples);
    if (size > 0 && g_genesis.audio_rb) {
        ringbuffer_write(g_genesis.audio_rb, audio_samples, size * 2);
    }
#endif

    pthread_mutex_unlock(&g_genesis.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_genesis.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_genesis.lock);
    if (!g_genesis.rom_loaded) {
        pthread_mutex_unlock(&g_genesis.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_genesis.video_width * g_genesis.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_genesis.video_buffer, byte_capacity);
    if (!direct_bb || !g_genesis.byte_buffer_class || !g_genesis.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_genesis.lock);
        return NULL;
    }

    if (g_genesis.byte_order_native && g_genesis.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_genesis.byte_buffer_order, g_genesis.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_genesis.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_genesis.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_genesis.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_genesis.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_genesis.audio_rb) return 0;
    return (jint) ringbuffer_available(g_genesis.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_genesis.lock);
    if (!g_genesis.rom_loaded) {
        pthread_mutex_unlock(&g_genesis.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_genesis.video_width, g_genesis.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_genesis.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_genesis.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_genesis.lock);
    if (!g_genesis.rom_loaded) {
        pthread_mutex_unlock(&g_genesis.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_GENESIS_CORE
    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
        if (sram.on && sram.sram) {
            size_t copy_len = len < 0x10000 ? len : 0x10000;
            memcpy(dst, sram.sram, copy_len);
        }
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }
#endif

    pthread_mutex_unlock(&g_genesis.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_genesis.lock);
    if (!g_genesis.rom_loaded) {
        pthread_mutex_unlock(&g_genesis.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_GENESIS_CORE
    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
        size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
        if (sram.on && sram.sram) {
            size_t copy_len = len < 0x10000 ? len : 0x10000;
            memcpy(sram.sram, src, copy_len);
        }
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }
#endif

    pthread_mutex_unlock(&g_genesis.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_genesis.lock);
#ifdef HAVE_GENESIS_CORE
    FILE* f = fopen(path, "wb");
    if (f) {
        state_save((unsigned char*)f);
        fclose(f);
    }
#endif
    pthread_mutex_unlock(&g_genesis.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_genesis_GenesisNativeCore_genesisLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_genesis.lock);
#ifdef HAVE_GENESIS_CORE
    FILE* f = fopen(path, "rb");
    if (f) {
        state_load((unsigned char*)f);
        fclose(f);
    }
#endif
    pthread_mutex_unlock(&g_genesis.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}
