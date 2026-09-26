package com.retropack.runtime.ppsspp

import com.retropack.runtime.core.NativeCoreBridge
import java.nio.IntBuffer

/**
 * JNI bridge to the PPSSPP PlayStation Portable C/C++ core compiled as `libretropack-runtime-ppsspp.so`.
 */
object PpssppNativeCore : NativeCoreBridge {
    private val CANDIDATE_LIBRARIES = listOf("retropack-runtime-ppsspp", "ppsspp")

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
            loadError = lastError?.message ?: "PPSSPP native library link failure"
        }
    }

    override fun isLoaded(): Boolean = libraryLoaded

    override fun nativeInit(internalStoragePath: String): Boolean = ppssppInit(internalStoragePath)
    override fun nativeLoadRom(romPath: String): Boolean = ppssppLoadRom(romPath)
    override fun nativeUnloadRom() = ppssppUnloadRom()
    override fun nativeDestroy() = ppssppDestroy()
    override fun nativeRunFrame(): Boolean = ppssppRunFrame()
    override fun nativeSetKeys(keyMask: Int) = ppssppSetKeys(keyMask)
    override fun nativeGetVideoBuffer(): IntBuffer? = ppssppGetVideoBuffer()
    override fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int =
        ppssppGetAudioSamples(outSamples, maxSamples)
    override fun nativeGetAudioAvailable(): Int = ppssppGetAudioAvailable()
    override fun nativeGetVideoSize(): IntArray? = ppssppGetVideoSize()
    override fun nativeGetSramSize(): Int = ppssppGetSramSize()
    override fun nativeReadSram(outBuffer: ByteArray): Boolean = ppssppReadSram(outBuffer)
    override fun nativeWriteSram(inBuffer: ByteArray): Boolean = ppssppWriteSram(inBuffer)
    override fun nativeSaveState(slot: Int, filePath: String): Boolean = ppssppSaveState(slot, filePath)
    override fun nativeLoadState(slot: Int, filePath: String): Boolean = ppssppLoadState(slot, filePath)

    private external fun ppssppInit(internalStoragePath: String): Boolean
    private external fun ppssppLoadRom(romPath: String): Boolean
    private external fun ppssppUnloadRom()
    private external fun ppssppDestroy()
    private external fun ppssppRunFrame(): Boolean
    private external fun ppssppSetKeys(keyMask: Int)
    private external fun ppssppGetVideoBuffer(): IntBuffer?
    private external fun ppssppGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int
    private external fun ppssppGetAudioAvailable(): Int
    private external fun ppssppGetVideoSize(): IntArray?
    private external fun ppssppGetSramSize(): Int
    private external fun ppssppReadSram(outBuffer: ByteArray): Boolean
    private external fun ppssppWriteSram(inBuffer: ByteArray): Boolean
    private external fun ppssppSaveState(slot: Int, filePath: String): Boolean
    private external fun ppssppLoadState(slot: Int, filePath: String): Boolean
}
