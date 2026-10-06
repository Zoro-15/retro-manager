package com.retropack.runtime.core

import java.nio.IntBuffer

/**
 * Universal Libretro C-Host JNI Bridge.
 *
 * Implements [NativeCoreBridge] by dynamically loading the unified `libretropack-host.so`
 * native host, which in turn dynamically binds and executes any standard Libretro
 * core (.so) via dlopen/dlsym at runtime.
 */
object UniversalLibretroCore : NativeCoreBridge {
    private val HOST_LIBRARIES = listOf(
        "retropack-runtime",
        "retropack-host",
        "libretro_host",
        "retro_host",
        "retro_engine",
        "retropack",
        "mgba"
    )

    @Volatile
    private var hostLibraryLoaded: Boolean = false

    @Volatile
    var loadedHostLibraryName: String? = null
        private set

    @Volatile
    var hostLoadError: String? = null
        private set

    @Volatile
    var loadedCorePath: String? = null
        private set

    init {
        tryLoadHostLibrary()
    }

    private fun tryLoadHostLibrary(): Boolean {
        if (hostLibraryLoaded) return true
        var loaded = false
        var lastError: Throwable? = null
        for (lib in HOST_LIBRARIES) {
            try {
                System.loadLibrary(lib)
                hostLibraryLoaded = true
                loadedHostLibraryName = lib
                loaded = true
                hostLoadError = null
                break
            } catch (e: Throwable) {
                lastError = e
            }
        }
        if (!loaded) {
            hostLibraryLoaded = false
            hostLoadError = lastError?.message ?: "Universal Libretro host library not found"
        }
        return loaded
    }

    /**
     * Returns true if the universal host native shared library is loaded.
     */
    override fun isLoaded(): Boolean {
        if (!hostLibraryLoaded) {
            tryLoadHostLibrary()
        }
        return hostLibraryLoaded
    }

    /**
     * Loads a standalone Libretro core .so file dynamically into the universal host.
     *
     * @param corePath Absolute path or filename of the target libretro_<core>.so.
     * @return true if core symbols were resolved and retro_init succeeded, false otherwise.
     */
    fun loadCore(corePath: String): Boolean {
        if (!isLoaded()) return false
        val candidates = mutableListOf(corePath)
        val normalized = corePath.substringAfterLast('/').substringAfterLast('\\')
        if (normalized != corePath) {
            candidates.add(normalized)
        }
        if (!normalized.startsWith("lib")) {
            candidates.add("lib$normalized")
            candidates.add("libretro_$normalized.so")
        }
        if (!normalized.endsWith(".so")) {
            candidates.add("$normalized.so")
        }

        var success = false
        var lastAttemptedPath = corePath
        for (cand in candidates.distinct()) {
            success = try {
                nativeLoadCore(cand)
            } catch (e: UnsatisfiedLinkError) {
                false
            }
            if (success) {
                lastAttemptedPath = cand
                break
            }
        }

        if (success) {
            loadedCorePath = lastAttemptedPath
        }
        return success
    }

    /**
     * Unloads the active Libretro core from the universal host.
     */
    fun unloadCore() {
        if (!hostLibraryLoaded) return
        try {
            nativeUnloadCore()
            loadedCorePath = null
        } catch (_: UnsatisfiedLinkError) {}
    }

    /**
     * Resets the active emulation core.
     */
    fun reset() {
        if (!hostLibraryLoaded) return
        try {
            nativeReset()
        } catch (_: UnsatisfiedLinkError) {}
    }

    /* --------------------------------------------------------------------- */
    /* NativeCoreBridge External JNI Methods                                 */
    /* --------------------------------------------------------------------- */

    override external fun nativeInit(internalStoragePath: String): Boolean

    external fun nativeLoadCore(corePath: String): Boolean

    external fun nativeUnloadCore()

    override external fun nativeLoadRom(romPath: String): Boolean

    override external fun nativeUnloadRom()

    external fun nativeReset()

    override external fun nativeDestroy()

    override external fun nativeRunFrame(): Boolean

    override external fun nativeSetKeys(keyMask: Int)

    override external fun nativeSetAnalogAxis(axisX: Float, axisY: Float)

    external fun nativeSetTouch(x: Int, y: Int, isTouching: Boolean)

    override external fun nativeGetVideoBuffer(): IntBuffer?

    override external fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int

    override external fun nativeGetAudioAvailable(): Int

    override external fun nativeGetVideoSize(): IntArray?

    override external fun nativeGetSramSize(): Int

    override external fun nativeReadSram(outBuffer: ByteArray): Boolean

    override external fun nativeWriteSram(inBuffer: ByteArray): Boolean

    override external fun nativeSaveState(slot: Int, filePath: String): Boolean

    override external fun nativeLoadState(slot: Int, filePath: String): Boolean

    override external fun nativeGetLastError(): String?
}
