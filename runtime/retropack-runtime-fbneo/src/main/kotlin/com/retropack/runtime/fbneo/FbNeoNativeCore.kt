package com.retropack.runtime.fbneo

import com.retropack.runtime.core.NativeCoreBridge
import java.nio.IntBuffer

/**
 * JNI bridge to the FBNeo (FinalBurn Neo) Arcade C core.
 */
object FbNeoNativeCore : NativeCoreBridge {
    private val CANDIDATE_LIBRARIES = listOf("retropack-runtime-fbneo", "fbneo")

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
            loadError = lastError?.message ?: "FBNeo native library link failure"
        }
    }

    override fun isLoaded(): Boolean = libraryLoaded

    override fun nativeInit(internalStoragePath: String): Boolean = fbneoInit(internalStoragePath)
    override fun nativeLoadRom(romPath: String): Boolean = fbneoLoadRom(romPath)
    override fun nativeUnloadRom() = fbneoUnloadRom()
    override fun nativeDestroy() = fbneoDestroy()
    override fun nativeRunFrame(): Boolean = fbneoRunFrame()
    override fun nativeSetKeys(keyMask: Int) = fbneoSetKeys(keyMask)
    override fun nativeGetVideoBuffer(): IntBuffer? = fbneoGetVideoBuffer()
    override fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int =
        fbneoGetAudioSamples(outSamples, maxSamples)
    override fun nativeGetAudioAvailable(): Int = fbneoGetAudioAvailable()
    override fun nativeGetVideoSize(): IntArray? = fbneoGetVideoSize()
    override fun nativeGetSramSize(): Int = fbneoGetSramSize()
    override fun nativeReadSram(outBuffer: ByteArray): Boolean = fbneoReadSram(outBuffer)
    override fun nativeWriteSram(inBuffer: ByteArray): Boolean = fbneoWriteSram(inBuffer)
    override fun nativeSaveState(slot: Int, filePath: String): Boolean = fbneoSaveState(slot, filePath)
    override fun nativeLoadState(slot: Int, filePath: String): Boolean = fbneoLoadState(slot, filePath)

    private external fun fbneoInit(internalStoragePath: String): Boolean
    private external fun fbneoLoadRom(romPath: String): Boolean
    private external fun fbneoUnloadRom()
    private external fun fbneoDestroy()
    private external fun fbneoRunFrame(): Boolean
    private external fun fbneoSetKeys(keyMask: Int)
    private external fun fbneoGetVideoBuffer(): IntBuffer?
    private external fun fbneoGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int
    private external fun fbneoGetAudioAvailable(): Int
    private external fun fbneoGetVideoSize(): IntArray?
    private external fun fbneoGetSramSize(): Int
    private external fun fbneoReadSram(outBuffer: ByteArray): Boolean
    private external fun fbneoWriteSram(inBuffer: ByteArray): Boolean
    private external fun fbneoSaveState(slot: Int, filePath: String): Boolean
    private external fun fbneoLoadState(slot: Int, filePath: String): Boolean
}
