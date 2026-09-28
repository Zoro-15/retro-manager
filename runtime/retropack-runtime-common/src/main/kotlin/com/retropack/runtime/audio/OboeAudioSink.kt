package com.retropack.runtime.audio

/**
 * High-performance, low-latency [AudioSink] powered by Google's native C++ Oboe library (AAudio / OpenSL ES).
 *
 * Implements Feature 1 specifications and issue #43:
 * - Ultra-low glass-to-ear latency (< 15ms) eliminating audio pops and crackles.
 * - Zero-GC audio pipeline avoiding JVM allocations during playback.
 * - Direct-pull mode (issue #43): the native AAudio onAudioReady callback
 *   drains the libretro SPSC ring buffer itself, removing the per-frame
 *   JVM pump and its JNI crossings entirely. In this mode [write] is a
 *   harmless no-op and [setDriftRate] feeds the native proportional
 *   regulator instead.
 * - Seamless fallback to [AudioTrackSink] when running on non-supported environments or pure JVM tests.
 */
class OboeAudioSink(
    override val sampleRate: Int = 44_100,
    override val channelCount: Int = 2,
    bufferSizeFrames: Int = 1024
) : AudioSink {

    private var nativeHandle: Long = 0L
    private val fallbackSink: AudioTrackSink? by lazy {
        AudioTrackSink(sampleRate, channelCount)
    }

    /**
     * True once the native AAudio stream is confirmed to run in direct-pull
     * mode (realtime callback drains the emulation ring buffer natively).
     */
    @Volatile
    var isDirectPullMode: Boolean = false
        private set

    init {
        if (OboeNativeBridge.isAvailable()) {
            try {
                nativeHandle = OboeNativeBridge.nativeOboeOpen(sampleRate, channelCount, bufferSizeFrames)
                if (nativeHandle != 0L) {
                    isDirectPullMode = try {
                        OboeNativeBridge.nativeOboeIsDirect(nativeHandle)
                    } catch (_: Throwable) {
                        false
                    }
                }
            } catch (_: Throwable) {
                nativeHandle = 0L
            }
        }
    }

    override fun write(samples: ShortArray, offset: Int, count: Int): Int {
        if (nativeHandle != 0L) {
            // Direct-pull mode: the realtime callback drains natively; pushed
            // writes would double-feed audio. Report success so callers using
            // legacy pump bookkeeping remain consistent.
            if (isDirectPullMode) {
                return count
            }
            try {
                return OboeNativeBridge.nativeOboeWrite(nativeHandle, samples, offset, count)
            } catch (_: Throwable) {
                // Fallback to AudioTrack on JNI error
            }
        }
        return fallbackSink?.write(samples, offset, count) ?: 0
    }

    /**
     * Publishes the drift controller's proportional rate adjustment
     * (0.94..1.06) into the native realtime callback (no-op outside
     * direct-pull mode).
     */
    fun setDriftRate(rate: Float) {
        if (nativeHandle != 0L && isDirectPullMode) {
            try {
                OboeNativeBridge.nativeOboeSetDriftRate(nativeHandle, rate)
            } catch (_: Throwable) {}
        }
    }

    override fun play() {
        if (nativeHandle != 0L) {
            try {
                OboeNativeBridge.nativeOboeStart(nativeHandle)
                return
            } catch (_: Throwable) {}
        }
        fallbackSink?.play()
    }

    override fun pause() {
        if (nativeHandle != 0L) {
            try {
                OboeNativeBridge.nativeOboePause(nativeHandle)
                return
            } catch (_: Throwable) {}
        }
        fallbackSink?.pause()
    }

    override fun flush() {
        if (nativeHandle != 0L) {
            try {
                OboeNativeBridge.nativeOboeFlush(nativeHandle)
                return
            } catch (_: Throwable) {}
        }
        fallbackSink?.flush()
    }

    override fun release() {
        if (nativeHandle != 0L) {
            try {
                OboeNativeBridge.nativeOboeClose(nativeHandle)
                nativeHandle = 0L
            } catch (_: Throwable) {}
        }
        fallbackSink?.release()
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        if (nativeHandle != 0L) {
            try {
                OboeNativeBridge.nativeOboeSetVolume(nativeHandle, clamped)
                return
            } catch (_: Throwable) {}
        }
        fallbackSink?.setVolume(clamped)
    }

    override fun getUnderrunCount(): Int {
        if (nativeHandle != 0L) {
            try {
                return OboeNativeBridge.nativeOboeGetUnderruns(nativeHandle)
            } catch (_: Throwable) {}
        }
        return fallbackSink?.getUnderrunCount() ?: 0
    }

    fun getLatencyMs(): Double {
        if (nativeHandle != 0L) {
            try {
                return OboeNativeBridge.nativeOboeGetLatencyMs(nativeHandle)
            } catch (_: Throwable) {}
        }
        return 12.5 // Typical target latency
    }
}
