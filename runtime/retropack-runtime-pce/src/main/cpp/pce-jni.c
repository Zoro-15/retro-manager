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

#define LOG_TAG "RetroPack-PCE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define PCE_MAX_WIDTH 512
#define PCE_MAX_HEIGHT 242
#define PCE_DEFAULT_WIDTH 256
#define PCE_DEFAULT_HEIGHT 240
#define AUDIO_BUFFER_CAPACITY (16 * 1024)
#define PCE_BRAM_SIZE 0x800 // 2 KB standard PC Engine Backup RAM

#ifdef HAVE_BEETLE_PCE_CORE
// Real upstream Beetle PCE Fast / Mednafen C/C++ headers
#include <mednafen/mednafen.h>
#include <mednafen/pce_fast/pce.h>
#include <mednafen/pce_fast/vdc.h>
#include <mednafen/pce_fast/psg.h>
#include <mednafen/pce_fast/huc.h>
#include <mednafen/pce_fast/input.h>
#endif

static struct {
    bool initialized;
    bool rom_loaded;
    char storage_path[1024];
    uint32_t video_buffer[PCE_MAX_WIDTH * PCE_MAX_HEIGHT];
    int video_width;
    int video_height;
    uint8_t bram[PCE_BRAM_SIZE];
    RingBuffer* audio_rb;
    pthread_mutex_t lock;
    size_t sram_size;
    uint32_t key_mask;
    uint32_t turbo_counter;
    jclass byte_buffer_class;
    jmethodID byte_buffer_order;
    jmethodID byte_buffer_as_int_buffer;
    jobject byte_order_native;
} g_pce = {
    .initialized = false,
    .rom_loaded = false,
    .storage_path = {0},
    .video_buffer = {0},
    .video_width = PCE_DEFAULT_WIDTH,
    .video_height = PCE_DEFAULT_HEIGHT,
    .bram = {0},
    .audio_rb = NULL,
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .sram_size = PCE_BRAM_SIZE,
    .key_mask = 0,
    .turbo_counter = 0,
    .byte_buffer_class = NULL,
    .byte_buffer_order = NULL,
    .byte_buffer_as_int_buffer = NULL,
    .byte_order_native = NULL,
};

// Canonical 9-bit RGB Palette table (512 colors: 3-bit R, 3-bit G, 3-bit B) -> ARGB8888
static uint32_t s_pce_palette[512];
static bool s_pce_palette_init = false;

static void init_pce_palette(void) {
    if (s_pce_palette_init) return;
    for (int i = 0; i < 512; i++) {
        // 9-bit RGB: bits 0..2 = Blue (0..7), bits 3..5 = Red (0..7), bits 6..8 = Green (0..7)
        uint8_t b3 = (i >> 0) & 0x07;
        uint8_t r3 = (i >> 3) & 0x07;
        uint8_t g3 = (i >> 6) & 0x07;

        uint32_t r8 = (r3 * 255) / 7;
        uint32_t g8 = (g3 * 255) / 7;
        uint32_t b8 = (b3 * 255) / 7;

        s_pce_palette[i] = 0xFF000000 | (r8 << 16) | (g8 << 8) | b8;
    }
    s_pce_palette_init = true;
}

/**
 * Maps RetroKey bitmask to PC Engine joypad / Avenue Pad 6 buttons.
 *
 * PCE Bitmask:
 * - 0x0001 : Button I
 * - 0x0002 : Button II
 * - 0x0004 : SELECT
 * - 0x0008 : RUN
 * - 0x0010 : UP
 * - 0x0020 : RIGHT
 * - 0x0040 : DOWN
 * - 0x0080 : LEFT
 * - 0x0100 : Button III (6-button)
 * - 0x0200 : Button IV (6-button)
 * - 0x0300 : Button V (6-button)
 * - 0x0800 : Button VI (6-button)
 */
static inline uint16_t map_retro_keys_to_pce(uint32_t mask, uint32_t turbo_phase) {
    uint16_t pad = 0;

    // Standard buttons
    if (mask & (1 << 0)) pad |= 0x0001; // Button I (A)
    if (mask & (1 << 1)) pad |= 0x0002; // Button II (B)
    if (mask & (1 << 2)) pad |= 0x0004; // SELECT
    if (mask & (1 << 3)) pad |= 0x0008; // RUN (START)
    if (mask & (1 << 6)) pad |= 0x0010; // UP
    if (mask & (1 << 4)) pad |= 0x0020; // RIGHT
    if (mask & (1 << 7)) pad |= 0x0040; // DOWN
    if (mask & (1 << 5)) pad |= 0x0080; // LEFT

    // Turbo rapid-fire mappings (Turbo X -> Turbo I, Turbo Y -> Turbo II)
    bool turbo_active = (turbo_phase & 2) == 0;
    if (mask & (1 << 10)) { // X (Turbo I / Button III)
        if (turbo_active) pad |= 0x0001;
        pad |= 0x0100;
    }
    if (mask & (1 << 11)) { // Y (Turbo II / Button IV)
        if (turbo_active) pad |= 0x0002;
        pad |= 0x0200;
    }

    // 6-button extensions (Avenue Pad 6)
    if ((mask & (1 << 8)) || (mask & (1 << 12))) pad |= 0x0400; // Button V (R / C)
    if ((mask & (1 << 9)) || (mask & (1 << 13))) pad |= 0x0800; // Button VI (L / Z)

    return pad;
}

static void init_jni_cache(JNIEnv* env) {
    if (g_pce.byte_buffer_class) return;
    jclass bb_local = (*env)->FindClass(env, "java/nio/ByteBuffer");
    if (!bb_local) return;
    g_pce.byte_buffer_class = (jclass)(*env)->NewGlobalRef(env, bb_local);
    (*env)->DeleteLocalRef(env, bb_local);

    g_pce.byte_buffer_order = (*env)->GetMethodID(env, g_pce.byte_buffer_class,
        "order", "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;");
    g_pce.byte_buffer_as_int_buffer = (*env)->GetMethodID(env, g_pce.byte_buffer_class,
        "asIntBuffer", "()Ljava/nio/IntBuffer;");

    jclass bo_class = (*env)->FindClass(env, "java/nio/ByteOrder");
    if (bo_class) {
        jmethodID bo_native = (*env)->GetStaticMethodID(env, bo_class,
            "nativeOrder", "()Ljava/nio/ByteOrder;");
        if (bo_native) {
            jobject order_local = (*env)->CallStaticObjectMethod(env, bo_class, bo_native);
            if (order_local) {
                g_pce.byte_order_native = (*env)->NewGlobalRef(env, order_local);
                (*env)->DeleteLocalRef(env, order_local);
            }
        }
        (*env)->DeleteLocalRef(env, bo_class);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceInit(
        JNIEnv* env, jobject thiz, jstring internalStoragePath) {
    (void) thiz;
    pthread_mutex_lock(&g_pce.lock);

    if (g_pce.initialized) {
        pthread_mutex_unlock(&g_pce.lock);
        return JNI_TRUE;
    }

    if (internalStoragePath) {
        const char* path = (*env)->GetStringUTFChars(env, internalStoragePath, NULL);
        if (path) {
            strncpy(g_pce.storage_path, path, sizeof(g_pce.storage_path) - 1);
            g_pce.storage_path[sizeof(g_pce.storage_path) - 1] = '\0';
            (*env)->ReleaseStringUTFChars(env, internalStoragePath, path);
        }
    }

    if (!g_pce.audio_rb) {
        g_pce.audio_rb = ringbuffer_create(AUDIO_BUFFER_CAPACITY);
    }

    init_pce_palette();
    init_jni_cache(env);

#ifdef HAVE_BEETLE_PCE_CORE
    // Upstream Beetle PCE Fast initialization
    MDFNI_Initialize(g_pce.storage_path);
    PCE_Init();
    PSG_Init();
#endif

    g_pce.initialized = true;
    LOGI("Beetle PCE Fast native runtime initialized (storage: %s)", g_pce.storage_path);

    pthread_mutex_unlock(&g_pce.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceLoadRom(
        JNIEnv* env, jobject thiz, jstring romPath) {
    (void) thiz;
    if (!romPath) return JNI_FALSE;
    const char* native_path = (*env)->GetStringUTFChars(env, romPath, NULL);
    if (!native_path) return JNI_FALSE;

    pthread_mutex_lock(&g_pce.lock);

#ifdef HAVE_BEETLE_PCE_CORE
    if (!PCE_Load(native_path)) {
        LOGE("Beetle PCE Fast failed to load ROM: %s", native_path);
        pthread_mutex_unlock(&g_pce.lock);
        (*env)->ReleaseStringUTFChars(env, romPath, native_path);
        return JNI_FALSE;
    }
    PCE_Reset();
#endif

    g_pce.rom_loaded = true;
    g_pce.video_width = PCE_DEFAULT_WIDTH;
    g_pce.video_height = PCE_DEFAULT_HEIGHT;
    g_pce.turbo_counter = 0;

    LOGI("Beetle PCE Fast loaded ROM: %s (%dx%d)", native_path, g_pce.video_width, g_pce.video_height);
    pthread_mutex_unlock(&g_pce.lock);
    (*env)->ReleaseStringUTFChars(env, romPath, native_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceUnloadRom(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
#ifdef HAVE_BEETLE_PCE_CORE
    if (g_pce.rom_loaded) {
        PCE_Close();
    }
#endif
    g_pce.rom_loaded = false;
    if (g_pce.audio_rb) ringbuffer_reset(g_pce.audio_rb);
    pthread_mutex_unlock(&g_pce.lock);
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceDestroy(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
#ifdef HAVE_BEETLE_PCE_CORE
    PCE_Close();
    MDFNI_Kill();
#endif
    if (g_pce.audio_rb) {
        ringbuffer_destroy(g_pce.audio_rb);
        g_pce.audio_rb = NULL;
    }
    g_pce.initialized = false;
    g_pce.rom_loaded = false;
    pthread_mutex_unlock(&g_pce.lock);
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceRunFrame(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return JNI_FALSE;
    }

    g_pce.turbo_counter++;

#ifdef HAVE_BEETLE_PCE_CORE
    // 1. Pass Joypad inputs
    uint16_t pad = map_retro_keys_to_pce(g_pce.key_mask, g_pce.turbo_counter);
    PCE_SetInput(0, pad);

    // 2. Emulate 1 system frame
    PCE_EmulateFrame();

    // 3. Audio stream into ring buffer
    int16_t sound_buf[2048];
    int samples = PSG_GetAudioSamples(sound_buf, 1024);
    if (samples > 0 && g_pce.audio_rb) {
        ringbuffer_write(g_pce.audio_rb, sound_buf, samples * 2);
    }
#endif

    pthread_mutex_unlock(&g_pce.lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceSetKeys(
        JNIEnv* env, jobject thiz, jint keyMask) {
    (void) env; (void) thiz;
    g_pce.key_mask = (uint32_t) keyMask;
}

JNIEXPORT jobject JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetVideoBuffer(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return NULL;
    }

    jlong byte_capacity = (jlong)(g_pce.video_width * g_pce.video_height * sizeof(uint32_t));
    jobject direct_bb = (*env)->NewDirectByteBuffer(env, g_pce.video_buffer, byte_capacity);
    if (!direct_bb || !g_pce.byte_buffer_class || !g_pce.byte_buffer_as_int_buffer) {
        pthread_mutex_unlock(&g_pce.lock);
        return NULL;
    }

    if (g_pce.byte_order_native && g_pce.byte_buffer_order) {
        (*env)->CallObjectMethod(env, direct_bb, g_pce.byte_buffer_order, g_pce.byte_order_native);
    }
    jobject int_buffer = (*env)->CallObjectMethod(env, direct_bb, g_pce.byte_buffer_as_int_buffer);
    (*env)->DeleteLocalRef(env, direct_bb);

    pthread_mutex_unlock(&g_pce.lock);
    return int_buffer;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outSamples, jint maxSamples) {
    (void) thiz;
    if (!outSamples || maxSamples <= 0 || !g_pce.audio_rb) return 0;
    jshort* dst = (*env)->GetPrimitiveArrayCritical(env, outSamples, NULL);
    if (!dst) return 0;
    size_t read = ringbuffer_read(g_pce.audio_rb, (int16_t*) dst, (size_t) maxSamples);
    (*env)->ReleasePrimitiveArrayCritical(env, outSamples, dst, 0);
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetAudioAvailable(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!g_pce.audio_rb) return 0;
    return (jint) ringbuffer_available(g_pce.audio_rb);
}

JNIEXPORT jintArray JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetVideoSize(JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return NULL;
    }
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) {
        jint dims[2] = { g_pce.video_width, g_pce.video_height };
        (*env)->SetIntArrayRegion(env, result, 0, 2, dims);
    }
    pthread_mutex_unlock(&g_pce.lock);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceGetSramSize(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    return (jint) g_pce.sram_size;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceReadSram(
        JNIEnv* env, jobject thiz, jbyteArray outBuffer) {
    (void) thiz;
    if (!outBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return JNI_FALSE;
    }

    jbyte* dst = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, outBuffer, NULL);
    if (dst) {
        size_t len = (size_t)(*env)->GetArrayLength(env, outBuffer);
        size_t copy_len = len < PCE_BRAM_SIZE ? len : PCE_BRAM_SIZE;
#ifdef HAVE_BEETLE_PCE_CORE
        if (PCE_GetBRAM()) {
            memcpy(dst, PCE_GetBRAM(), copy_len);
        } else {
            memcpy(dst, g_pce.bram, copy_len);
        }
#else
        memcpy(dst, g_pce.bram, copy_len);
#endif
        (*env)->ReleasePrimitiveArrayCritical(env, outBuffer, dst, 0);
    }

    pthread_mutex_unlock(&g_pce.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceWriteSram(
        JNIEnv* env, jobject thiz, jbyteArray inBuffer) {
    (void) thiz;
    if (!inBuffer) return JNI_FALSE;
    pthread_mutex_lock(&g_pce.lock);
    if (!g_pce.rom_loaded) {
        pthread_mutex_unlock(&g_pce.lock);
        return JNI_FALSE;
    }

    jbyte* src = (jbyte*)(*env)->GetPrimitiveArrayCritical(env, inBuffer, NULL);
    if (src) {
        size_t len = (size_t)(*env)->GetArrayLength(env, inBuffer);
        size_t copy_len = len < PCE_BRAM_SIZE ? len : PCE_BRAM_SIZE;
#ifdef HAVE_BEETLE_PCE_CORE
        if (PCE_GetBRAM()) {
            memcpy(PCE_GetBRAM(), src, copy_len);
        }
#endif
        memcpy(g_pce.bram, src, copy_len);
        (*env)->ReleasePrimitiveArrayCritical(env, inBuffer, src, 0);
    }

    pthread_mutex_unlock(&g_pce.lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceSaveState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_pce.lock);
#ifdef HAVE_BEETLE_PCE_CORE
    PCE_SaveState(path);
#endif
    pthread_mutex_unlock(&g_pce.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retropack_runtime_pce_PceNativeCore_pceLoadState(
        JNIEnv* env, jobject thiz, jint slot, jstring filePath) {
    (void) thiz; (void) slot;
    if (!filePath) return JNI_FALSE;
    const char* path = (*env)->GetStringUTFChars(env, filePath, NULL);
    if (!path) return JNI_FALSE;

    pthread_mutex_lock(&g_pce.lock);
#ifdef HAVE_BEETLE_PCE_CORE
    PCE_LoadState(path);
#endif
    pthread_mutex_unlock(&g_pce.lock);
    (*env)->ReleaseStringUTFChars(env, filePath, path);
    return JNI_TRUE;
}
