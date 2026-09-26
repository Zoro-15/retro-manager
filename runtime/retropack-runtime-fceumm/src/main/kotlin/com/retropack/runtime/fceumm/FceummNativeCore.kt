package com.retropack.runtime.fceumm

import com.retropack.runtime.core.NativeCoreBridge
import java.nio.IntBuffer

/**
 * JNI bridge to the FCEUmm NES / Famicom C core.
 */
object FceummNativeCore : NativeCoreBridge {
    private val CANDIDATE_LIBRARIES = listOf("retropack-runtime-fceumm", "fceumm")

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
            loadError = lastError?.message ?: "FCEUmm native library link failure"
        }
    }

    override fun isLoaded(): Boolean = libraryLoaded

    override fun nativeInit(internalStoragePath: String): Boolean = fceuInit(internalStoragePath)
    override fun nativeLoadRom(romPath: String): Boolean = fceuLoadRom(romPath)
    override fun nativeUnloadRom() = fceuUnloadRom()
    override fun nativeDestroy() = fceuDestroy()
    override fun nativeRunFrame(): Boolean = fceuRunFrame()
    override fun nativeSetKeys(keyMask: Int) = fceuSetKeys(keyMask)
    override fun nativeGetVideoBuffer(): IntBuffer? = fceuGetVideoBuffer()
    override fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int =
        fceuGetAudioSamples(outSamples, maxSamples)
    override fun nativeGetAudioAvailable(): Int = fceuGetAudioAvailable()
    override fun nativeGetVideoSize(): IntArray? = fceuGetVideoSize()
    override fun nativeGetSramSize(): Int = fceuGetSramSize()
    override fun nativeReadSram(outBuffer: ByteArray): Boolean = fceuReadSram(outBuffer)
    override fun nativeWriteSram(inBuffer: ByteArray): Boolean = fceuWriteSram(inBuffer)
    override fun nativeSaveState(slot: Int, filePath: String): Boolean = fceuSaveState(slot, filePath)
    override fun nativeLoadState(slot: Int, filePath: String): Boolean = fceuLoadState(slot, filePath)

    private external fun fceuInit(internalStoragePath: String): Boolean
    private external fun fceuLoadRom(romPath: String): Boolean
    private external fun fceuUnloadRom()
    private external fun fceuDestroy()
    private external fun fceuRunFrame(): Boolean
    private external fun fceuSetKeys(keyMask: Int)
    private external fun fceuGetVideoBuffer(): IntBuffer?
    private external fun fceuGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int
    private external fun fceuGetAudioAvailable(): Int
    private external fun fceuGetVideoSize(): IntArray?
    private external fun fceuGetSramSize(): Int
    private external fun fceuReadSram(outBuffer: ByteArray): Boolean
    private external fun fceuWriteSram(inBuffer: ByteArray): Boolean
    private external fun fceuSaveState(slot: Int, filePath: String): Boolean
    private external fun fceuLoadState(slot: Int, filePath: String): Boolean
}
