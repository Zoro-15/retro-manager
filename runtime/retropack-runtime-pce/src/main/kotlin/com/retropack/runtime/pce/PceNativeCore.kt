package com.retropack.runtime.pce

import com.retropack.runtime.core.NativeCoreBridge
import java.nio.IntBuffer

/**
 * JNI bridge to the Beetle PCE Fast PC Engine / TG-16 C core.
 */
object PceNativeCore : NativeCoreBridge {
    private val CANDIDATE_LIBRARIES = listOf("retropack-runtime-pce", "mednafen_pce_fast")

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
            loadError = lastError?.message ?: "Beetle PCE Fast native library link failure"
        }
    }

    override fun isLoaded(): Boolean = libraryLoaded

    override fun nativeInit(internalStoragePath: String): Boolean = pceInit(internalStoragePath)
    override fun nativeLoadRom(romPath: String): Boolean = pceLoadRom(romPath)
    override fun nativeUnloadRom() = pceUnloadRom()
    override fun nativeDestroy() = pceDestroy()
    override fun nativeRunFrame(): Boolean = pceRunFrame()
    override fun nativeSetKeys(keyMask: Int) = pceSetKeys(keyMask)
    override fun nativeGetVideoBuffer(): IntBuffer? = pceGetVideoBuffer()
    override fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int =
        pceGetAudioSamples(outSamples, maxSamples)
    override fun nativeGetAudioAvailable(): Int = pceGetAudioAvailable()
    override fun nativeGetVideoSize(): IntArray? = pceGetVideoSize()
    override fun nativeGetSramSize(): Int = pceGetSramSize()
    override fun nativeReadSram(outBuffer: ByteArray): Boolean = pceReadSram(outBuffer)
    override fun nativeWriteSram(inBuffer: ByteArray): Boolean = pceWriteSram(inBuffer)
    override fun nativeSaveState(slot: Int, filePath: String): Boolean = pceSaveState(slot, filePath)
    override fun nativeLoadState(slot: Int, filePath: String): Boolean = pceLoadState(slot, filePath)

    private external fun pceInit(internalStoragePath: String): Boolean
    private external fun pceLoadRom(romPath: String): Boolean
    private external fun pceUnloadRom()
    private external fun pceDestroy()
    private external fun pceRunFrame(): Boolean
    private external fun pceSetKeys(keyMask: Int)
    private external fun pceGetVideoBuffer(): IntBuffer?
    private external fun pceGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int
    private external fun pceGetAudioAvailable(): Int
    private external fun pceGetVideoSize(): IntArray?
    private external fun pceGetSramSize(): Int
    private external fun pceReadSram(outBuffer: ByteArray): Boolean
    private external fun pceWriteSram(inBuffer: ByteArray): Boolean
    private external fun pceSaveState(slot: Int, filePath: String): Boolean
    private external fun pceLoadState(slot: Int, filePath: String): Boolean
}
