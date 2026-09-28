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
    private val HOST_LIBRARIES = listOf("retropack-host", "libretro_host", "retro_host")

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
    }

    /**
     * Returns true if the universal host native shared library is loaded.
     */
    override fun isLoaded(): Boolean = hostLibraryLoaded

    /**
     * Loads a standalone Libretro core .so file dynamically into the universal host.
     *
     * @param corePath Absolute path or filename of the target libretro_<core>.so.
     * @return true if core symbols were resolved and retro_init succeeded, false otherwise.
     */
    fun loadCore(corePath: String): Boolean {
        if (!hostLibraryLoaded) return false
        val success = try {
            nativeLoadCore(corePath)
        } catch (e: UnsatisfiedLinkError) {
            false
        }
        if (success) {
            loadedCorePath = corePath
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
}
