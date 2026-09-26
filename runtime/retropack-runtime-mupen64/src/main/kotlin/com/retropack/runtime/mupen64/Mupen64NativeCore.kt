package com.retropack.runtime.mupen64

import com.retropack.runtime.core.NativeCoreBridge
import java.nio.IntBuffer

/**
 * JNI bridge to the Mupen64Plus-Next Nintendo 64 C core with GLideN64 GLES3 renderer.
 */
object Mupen64NativeCore : NativeCoreBridge {
    private val CANDIDATE_LIBRARIES = listOf("retropack-runtime-mupen64", "mupen64plus_next")

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
            loadError = lastError?.message ?: "Mupen64Plus-Next native library link failure"
        }
    }

    override fun isLoaded(): Boolean = libraryLoaded

    override fun nativeInit(internalStoragePath: String): Boolean = mupenInit(internalStoragePath)
    override fun nativeLoadRom(romPath: String): Boolean = mupenLoadRom(romPath)
    override fun nativeUnloadRom() = mupenUnloadRom()
    override fun nativeDestroy() = mupenDestroy()
    override fun nativeRunFrame(): Boolean = mupenRunFrame()
    override fun nativeSetKeys(keyMask: Int) = mupenSetKeys(keyMask)
    override fun nativeGetVideoBuffer(): IntBuffer? = mupenGetVideoBuffer()
    override fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int =
        mupenGetAudioSamples(outSamples, maxSamples)
    override fun nativeGetAudioAvailable(): Int = mupenGetAudioAvailable()
    override fun nativeGetVideoSize(): IntArray? = mupenGetVideoSize()
    override fun nativeGetSramSize(): Int = mupenGetSramSize()
    override fun nativeReadSram(outBuffer: ByteArray): Boolean = mupenReadSram(outBuffer)
    override fun nativeWriteSram(inBuffer: ByteArray): Boolean = mupenWriteSram(inBuffer)
    override fun nativeSaveState(slot: Int, filePath: String): Boolean = mupenSaveState(slot, filePath)
    override fun nativeLoadState(slot: Int, filePath: String): Boolean = mupenLoadState(slot, filePath)

    fun nativeSetAnalogAxis(axisX: Float, axisY: Float) = mupenSetAnalogAxis(axisX, axisY)

    private external fun mupenInit(internalStoragePath: String): Boolean
    private external fun mupenLoadRom(romPath: String): Boolean
    private external fun mupenUnloadRom()
    private external fun mupenDestroy()
    private external fun mupenRunFrame(): Boolean
    private external fun mupenSetKeys(keyMask: Int)
    private external fun mupenSetAnalogAxis(axisX: Float, axisY: Float)
    private external fun mupenGetVideoBuffer(): IntBuffer?
    private external fun mupenGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int
    private external fun mupenGetAudioAvailable(): Int
    private external fun mupenGetVideoSize(): IntArray?
    private external fun mupenGetSramSize(): Int
    private external fun mupenReadSram(outBuffer: ByteArray): Boolean
    private external fun mupenWriteSram(inBuffer: ByteArray): Boolean
    private external fun mupenSaveState(slot: Int, filePath: String): Boolean
    private external fun mupenLoadState(slot: Int, filePath: String): Boolean
}
