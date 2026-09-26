package com.retropack.runtime.mgba

import com.retropack.runtime.core.NativeCoreBridge
import java.nio.IntBuffer

/**
 * Low-level JNI bridge to the canonical mGBA 0.10.x C core compiled as `libretropack-runtime.so`.
 */
object MgbaNativeCore : NativeCoreBridge {
    private val CANDIDATE_LIBRARIES = listOf("retropack-runtime", "mgba")

    @Volatile
    private var libraryLoaded: Boolean = false

    @Volatile
    var loadedLibraryName: String? = null
        private set

    @Volatile
    var loadError: String? = null
        private set

    init {
        var loaded = false
        var lastError: Throwable? = null
        for (lib in CANDIDATE_LIBRARIES) {
            try {
                System.loadLibrary(lib)
                libraryLoaded = true
                loadedLibraryName = lib
                loaded = true
                loadError = null
                break
            } catch (e: Throwable) {
                lastError = e
            }
        }
        if (!loaded) {
            libraryLoaded = false
            loadError = lastError?.message ?: "mGBA native library link failure"
        }
    }

    override fun isLoaded(): Boolean = libraryLoaded

    override fun nativeInit(internalStoragePath: String): Boolean = mgbaInit(internalStoragePath)
    override fun nativeLoadRom(romPath: String): Boolean = mgbaLoadRom(romPath)
    override fun nativeUnloadRom() = mgbaUnloadRom()
    override fun nativeDestroy() = mgbaDestroy()
    override fun nativeRunFrame(): Boolean = mgbaRunFrame()
    override fun nativeSetKeys(keyMask: Int) = mgbaSetKeys(keyMask)
    override fun nativeGetVideoBuffer(): IntBuffer? = mgbaGetVideoBuffer()
    override fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int =
        mgbaGetAudioSamples(outSamples, maxSamples)
    override fun nativeGetAudioAvailable(): Int = mgbaGetAudioAvailable()
    override fun nativeGetVideoSize(): IntArray? = mgbaGetVideoSize()
    override fun nativeGetSramSize(): Int = mgbaGetSramSize()
    override fun nativeReadSram(outBuffer: ByteArray): Boolean = mgbaReadSram(outBuffer)
    override fun nativeWriteSram(inBuffer: ByteArray): Boolean = mgbaWriteSram(inBuffer)
    override fun nativeSaveState(slot: Int, filePath: String): Boolean = mgbaSaveState(slot, filePath)
    override fun nativeLoadState(slot: Int, filePath: String): Boolean = mgbaLoadState(slot, filePath)

    private external fun mgbaInit(internalStoragePath: String): Boolean
    private external fun mgbaLoadRom(romPath: String): Boolean
    private external fun mgbaUnloadRom()
    private external fun mgbaDestroy()
    private external fun mgbaRunFrame(): Boolean
    private external fun mgbaSetKeys(keyMask: Int)
    private external fun mgbaGetVideoBuffer(): IntBuffer?
    private external fun mgbaGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int
    private external fun mgbaGetAudioAvailable(): Int
    private external fun mgbaGetVideoSize(): IntArray?
    private external fun mgbaGetSramSize(): Int
    private external fun mgbaReadSram(outBuffer: ByteArray): Boolean
    private external fun mgbaWriteSram(inBuffer: ByteArray): Boolean
    private external fun mgbaSaveState(slot: Int, filePath: String): Boolean
    private external fun mgbaLoadState(slot: Int, filePath: String): Boolean
}
