package com.retropack.runtime.pcsx

import com.retropack.runtime.core.NativeCoreBridge
import java.nio.IntBuffer

/**
 * JNI bridge to the PCSX ReARMed PlayStation 1 C core.
 */
object PcsxNativeCore : NativeCoreBridge {
    private val CANDIDATE_LIBRARIES = listOf("retropack-runtime-pcsx", "pcsx_rearmed")

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
            loadError = lastError?.message ?: "PCSX native library link failure"
        }
    }

    override fun isLoaded(): Boolean = libraryLoaded

    override fun nativeInit(internalStoragePath: String): Boolean = pcsxInit(internalStoragePath)
    override fun nativeLoadRom(romPath: String): Boolean = pcsxLoadRom(romPath)
    override fun nativeUnloadRom() = pcsxUnloadRom()
    override fun nativeDestroy() = pcsxDestroy()
    override fun nativeRunFrame(): Boolean = pcsxRunFrame()
    override fun nativeSetKeys(keyMask: Int) = pcsxSetKeys(keyMask)
    override fun nativeGetVideoBuffer(): IntBuffer? = pcsxGetVideoBuffer()
    override fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int =
        pcsxGetAudioSamples(outSamples, maxSamples)
    override fun nativeGetAudioAvailable(): Int = pcsxGetAudioAvailable()
    override fun nativeGetVideoSize(): IntArray? = pcsxGetVideoSize()
    override fun nativeGetSramSize(): Int = pcsxGetSramSize()
    override fun nativeReadSram(outBuffer: ByteArray): Boolean = pcsxReadSram(outBuffer)
    override fun nativeWriteSram(inBuffer: ByteArray): Boolean = pcsxWriteSram(inBuffer)
    override fun nativeSaveState(slot: Int, filePath: String): Boolean = pcsxSaveState(slot, filePath)
    override fun nativeLoadState(slot: Int, filePath: String): Boolean = pcsxLoadState(slot, filePath)

    private external fun pcsxInit(internalStoragePath: String): Boolean
    private external fun pcsxLoadRom(romPath: String): Boolean
    private external fun pcsxUnloadRom()
    private external fun pcsxDestroy()
    private external fun pcsxRunFrame(): Boolean
    private external fun pcsxSetKeys(keyMask: Int)
    private external fun pcsxGetVideoBuffer(): IntBuffer?
    private external fun pcsxGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int
    private external fun pcsxGetAudioAvailable(): Int
    private external fun pcsxGetVideoSize(): IntArray?
    private external fun pcsxGetSramSize(): Int
    private external fun pcsxReadSram(outBuffer: ByteArray): Boolean
    private external fun pcsxWriteSram(inBuffer: ByteArray): Boolean
    private external fun pcsxSaveState(slot: Int, filePath: String): Boolean
    private external fun pcsxLoadState(slot: Int, filePath: String): Boolean
}
