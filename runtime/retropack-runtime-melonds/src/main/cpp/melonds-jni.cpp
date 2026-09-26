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
#define NDS_SCREEN_HEIGHT 384 // 192 (top) + 192 (bottom) stacked vertically
#define AUDIO_BUFFER_CAPACITY (16 * 1024)

#ifdef HAVE_MELONDS_CORE
// Real upstream melonDS C++ core headers when compiled with submodule
#include <NDS.h>
#include <GPU.h>
#include <SPU.h>
#include <SPI.h>
#include <SaveMemory.h>
#endif

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
    .video_buffer = {0},
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
    jclass bb_local = env->FindClass("java/nio/ByteBuffer");
    if (!bb_local) return;
    g_melonds.byte_buffer_class = (jclass)env->NewGlobalRef(bb_local);
    env->DeleteLocalRef(bb_local);

    g_melonds.byte_buffer_order = env->GetMethodID(g_melonds.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_melonds.byte_buffer_as_int_buffer = env->GetMethodID(g_melonds.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = env->FindClass("java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = env->GetStaticMethodID(bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = env->CallStaticObjectMethod(bo_class, bo_native);
            if (order_local) {
                g_melonds.byte_order_native = env->NewGlobalRef(order_local);
                env->DeleteLocalRef(order_local);
            }
        }
        env->DeleteLocalRef(bo_class);
    }
}

extern "C" {

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
        const char* path = env->GetStringUTFChars(internalStoragePath, NULL);
        if (path) {
            strncpy(g_melonds.storage_path, path, sizeof(g_melonds.storage_path) - 1);
            g_melonds.storage_path[sizeof(g_melonds.storage_path) - 1] = '\0';
            env->ReleaseStringUTFChars(internalStoragePath, path);
        }
    }

    if (!g_melonds.audio_rb) {
        g_melonds.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_jni_cache(env);

#ifdef HAVE_MELONDS_CORE
    NDS::Init();
    NDS::SetDirectBoot(true);
#endif

    g_melonds.initialized = true;
    LOGI("melonDS NDS native runtime initialized (storage: %s)", g_melonds.storage_path);

    pthread_mutex_unlock(&g_melonds.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = env->GetStringUTFChars(romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_melonds.lock);

#ifdef HAVE_MELONDS_CORE
    bool loaded = NDS::LoadROM(native_path, false);
    if (!loaded) {
        LOGE("melonDS failed to load ROM: %s", native_path);
        pthread_mutex_unlock(&g_melonds.lock);
        env->ReleaseStringUTFChars(romPath, native_path);
        return JNI_FALSE;
    }
#endif

    g_melonds.rom_loaded = true;
    g_melonds.video_width = NDS_SCREEN_WIDTH;
    g_melonds.video_height = NDS_SCREEN_HEIGHT;

    LOGI("melonDS loaded NDS ROM: %s", native_path);
    pthread_mutex_unlock(&g_melonds.lock);
    env->ReleaseStringUTFChars(romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
#ifdef HAVE_MELONDS_CORE
    if (g_melonds.rom_loaded) {
        NDS::DeInit();
    }
#endif
    g_melonds.rom_loaded = false;
    if (g_melonds.audio_rb) ringbuffer_reset(g_melonds.audio_rb);
    pthread_mutex_unlock(&g_melonds.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_melonds.lock);
#ifdef HAVE_MELONDS_CORE
    NDS::DeInit();
#endif
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

#ifdef HAVE_MELONDS_CORE
    // 1. Pass keypad bits (A, B, Select, Start, Right, Left, Up, Down, R, L, X, Y)
    NDS::SetKeyMask(~g_melonds.key_mask);

    // 2. Pass touch stylus state
    if (g_melonds.is_touching) {
        NDS::SetTouchPos(g_melonds.touch_x, g_melonds.touch_y);
    } else {
        NDS::ReleaseTouch();
    }

    // 3. Emulate NDS frame (~560,190 ARM9/ARM7 cycles)
    NDS::RunFrame();

    // 4. Copy dual screens into stacked video buffer (256x384)
    // Top screen: rows 0..191
    memcpy(&g_melonds.video_buffer[0], GPU::Framebuffer[0], 256 * 192 * sizeof(uint32_t));
    // Bottom screen: rows 192..383
    memcpy(&g_melonds.video_buffer[256 * 192], GPU::Framebuffer[1], 256 * 192 * sizeof(uint32_t));

    // 5. Stream SPU audio samples into ring buffer
    int16_t audio_temp[2048];
    int samples = SPU::ReadOutput(audio_temp, 1024);
    if (samples > 0 && g_melonds.audio_rb) {
        ringbuffer_write(g_melonds.audio_rb, audio_temp, samples * 2);
    }
#endif

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
    jobject direct_bb = env->NewDirectByteBuffer(g_melonds.video_buffer, byte_capacity);
    if (!direct_bb || !g_melonds.byte_buffer_class || !g_melonds.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_melonds.lock);
        return NULL;
    }

    if (g_melonds.byte_order_native && g_melonds.byte_buffer_order) {
        env->CallObjectMethod(direct_bb, g_melonds.byte_buffer_order, g_melonds.byte_order_native);
    }
    jobject int_buffer = env->CallObjectMethod(direct_bb, g_melonds.byte_buffer_as_int_buffer);
    env->DeleteLocalRef(direct_bb);

    pthread_mutex_unlock(&g_melonds.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_melonds.audio_rb) return 0;
    jshort* dst = (jshort*)env->GetPrimitiveArrayCritical(outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_melonds.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    env->ReleasePrimitiveArrayCritical(outSamples, dst, 0);
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
    jintArray result = env->NewIntArray(2);
    if (result) {
        jint dims[2] = { g_melonds.video_width, g_melonds.video_height };
        env->SetIntArrayRegion(result, 0, 2, dims);
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
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_melonds.lock);
    if (!g_melonds.rom_loaded) {
        pthread_mutex_unlock(&g_melonds.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_MELONDS_CORE
    jbyte* dst = (jbyte*)env->GetPrimitiveArrayCritical(outBuffer, NULL);
    if (dst) {
        NDS::GetSaveData((uint8_t*)dst, (size_t)env->GetArrayLength(outBuffer));
        env->ReleasePrimitiveArrayCritical(outBuffer, dst, 0);
    }
#endif

    pthread_mutex_unlock(&g_melonds.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_melonds.lock);
    if (!g_melonds.rom_loaded) {
        pthread_mutex_unlock(&g_melonds.lock);
        return JNI_FALSE;
    }

#ifdef HAVE_MELONDS_CORE
    jbyte* src = (jbyte*)env->GetPrimitiveArrayCritical(inBuffer, NULL);
    if (src) {
        NDS::SetSaveData((const uint8_t*)src, (size_t)env->GetArrayLength(inBuffer));
        env->ReleasePrimitiveArrayCritical(inBuffer, src, 0);
    }
#endif

    pthread_mutex_unlock(&g_melonds.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_melonds.lock);
#ifdef HAVE_MELONDS_CORE
    bool ok = NDS::SaveState(path);
#else
    bool ok = true;
#endif
    pthread_mutex_unlock(&g_melonds.lock);
    env->ReleaseStringUTFChars(filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_melonds_MelondsNativeCore_melonLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_melonds.lock);
#ifdef HAVE_MELONDS_CORE
    bool ok = NDS::LoadState(path);
#else
    bool ok = true;
#endif
    pthread_mutex_unlock(&g_melonds.lock);
    env->ReleaseStringUTFChars(filePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
