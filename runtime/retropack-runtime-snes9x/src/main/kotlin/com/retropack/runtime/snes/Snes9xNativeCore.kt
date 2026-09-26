package com.retropack.runtime.snes

import com.retropack.runtime.core.NativeCoreBridge
import java.nio.IntBuffer

/**
 * JNI bridge to the Snes9x Super Nintendo C/C++ core compiled as `libretropack-runtime-snes9x.so`.
 */
object Snes9xNativeCore : NativeCoreBridge {
    private val CANDIDATE_LIBRARIES = listOf("retropack-runtime-snes9x", "snes9x")

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
            loadError = lastError?.message ?: "Snes9x native library link failure"
        }
    }

    override fun isLoaded(): Boolean = libraryLoaded

    override fun nativeInit(internalStoragePath: String): Boolean = snesInit(internalStoragePath)
    override fun nativeLoadRom(romPath: String): Boolean = snesLoadRom(romPath)
    override fun nativeUnloadRom() = snesUnloadRom()
    override fun nativeDestroy() = snesDestroy()
    override fun nativeRunFrame(): Boolean = snesRunFrame()
    override fun nativeSetKeys(keyMask: Int) = snesSetKeys(keyMask)
    override fun nativeGetVideoBuffer(): IntBuffer? = snesGetVideoBuffer()
    override fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int =
        snesGetAudioSamples(outSamples, maxSamples)
    override fun nativeGetAudioAvailable(): Int = snesGetAudioAvailable()
    override fun nativeGetVideoSize(): IntArray? = snesGetVideoSize()
    override fun nativeGetSramSize(): Int = snesGetSramSize()
    override fun nativeReadSram(outBuffer: ByteArray): Boolean = snesReadSram(outBuffer)
    override fun nativeWriteSram(inBuffer: ByteArray): Boolean = snesWriteSram(inBuffer)
    override fun nativeSaveState(slot: Int, filePath: String): Boolean = snesSaveState(slot, filePath)
    override fun nativeLoadState(slot: Int, filePath: String): Boolean = snesLoadState(slot, filePath)

    private external fun snesInit(internalStoragePath: String): Boolean
    private external fun snesLoadRom(romPath: String): Boolean
    private external fun snesUnloadRom()
    private external fun snesDestroy()
    private external fun snesRunFrame(): Boolean
    private external fun snesSetKeys(keyMask: Int)
    private external fun snesGetVideoBuffer(): IntBuffer?
    private external fun snesGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int
    private external fun snesGetAudioAvailable(): Int
    private external fun snesGetVideoSize(): IntArray?
    private external fun snesGetSramSize(): Int
    private external fun snesReadSram(outBuffer: ByteArray): Boolean
    private external fun snesWriteSram(inBuffer: ByteArray): Boolean
    private external fun snesSaveState(slot: Int, filePath: String): Boolean
    private external fun snesLoadState(slot: Int, filePath: String): Boolean
}
