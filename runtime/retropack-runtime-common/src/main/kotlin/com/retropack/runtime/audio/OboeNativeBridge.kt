package com.retropack.runtime.audio

/**
 * Low-level JNI bridge for Google's native C++ Oboe Audio library (AAudio / OpenSL ES).
 *
 * Implements Feature 1 specifications:
 * - Native ultra-low latency audio stream initialization (< 15ms latency).
 * - Direct lock-free hardware audio output.
 * - Dynamic resampler and underrun tracking.
 */
object OboeNativeBridge {

    /**
     * Candidate libraries probed in order. `retropack-host` is the universal
     * libretro host .so that now carries the native AAudio pull bridge
     * (issue #43); the legacy names are kept for older artifact layouts.
     */
    private val CANDIDATE_LIBRARIES = listOf("retropack-host", "retropack-runtime", "oboe", "mgba")

    @Volatile
    private var isNativeAvailable: Boolean = false

    init {
        for (lib in CANDIDATE_LIBRARIES) {
            try {
                System.loadLibrary(lib)
                isNativeAvailable = true
                break
            } catch (_: Throwable) {
            }
        }
    }

    fun isAvailable(): Boolean = isNativeAvailable

    external fun nativeOboeOpen(sampleRate: Int, channelCount: Int, bufferSizeFrames: Int): Long
    external fun nativeOboeStart(handle: Long): Boolean
    external fun nativeOboePause(handle: Long): Boolean
    external fun nativeOboeFlush(handle: Long)
    external fun nativeOboeClose(handle: Long)
    external fun nativeOboeWrite(handle: Long, samples: ShortArray, offset: Int, count: Int): Int
    external fun nativeOboeSetVolume(handle: Long, volume: Float)
    external fun nativeOboeGetUnderruns(handle: Long): Int
    external fun nativeOboeGetLatencyMs(handle: Long): Double

    /**
     * True when the native stream runs in direct-pull mode: the realtime
     * AAudio onAudioReady callback drains the libretro ring buffer itself and
     * JVM-side pumping is unnecessary (issue #43).
     */
    external fun nativeOboeIsDirect(handle: Long): Boolean

    /**
     * Publishes the AudioDriftController's proportional rate adjustment
     * (0.94..1.06) into the realtime callback via a lock-free atomic float.
     */
    external fun nativeOboeSetDriftRate(handle: Long, rate: Float)
}
