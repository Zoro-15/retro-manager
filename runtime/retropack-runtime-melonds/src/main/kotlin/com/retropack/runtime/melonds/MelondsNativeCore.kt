package com.retropack.runtime.melonds

import com.retropack.runtime.core.NativeCoreBridge
import java.nio.IntBuffer

/**
 * JNI bridge to the melonDS Nintendo DS C/C++ core compiled as `libretropack-runtime-melonds.so`.
 */
object MelondsNativeCore : NativeCoreBridge {
    private val CANDIDATE_LIBRARIES = listOf("retropack-runtime-melonds", "melonds")

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
            loadError = lastError?.message ?: "melonDS native library link failure"
        }
    }

    override fun isLoaded(): Boolean = libraryLoaded

    override fun nativeInit(internalStoragePath: String): Boolean = melonInit(internalStoragePath)
    override fun nativeLoadRom(romPath: String): Boolean = melonLoadRom(romPath)
    override fun nativeUnloadRom() = melonUnloadRom()
    override fun nativeDestroy() = melonDestroy()
    override fun nativeRunFrame(): Boolean = melonRunFrame()
    override fun nativeSetKeys(keyMask: Int) = melonSetKeys(keyMask)
    override fun nativeGetVideoBuffer(): IntBuffer? = melonGetVideoBuffer()
    override fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int =
        melonGetAudioSamples(outSamples, maxSamples)
    override fun nativeGetAudioAvailable(): Int = melonGetAudioAvailable()
    override fun nativeGetVideoSize(): IntArray? = melonGetVideoSize()
    override fun nativeGetSramSize(): Int = melonGetSramSize()
    override fun nativeReadSram(outBuffer: ByteArray): Boolean = melonReadSram(outBuffer)
    override fun nativeWriteSram(inBuffer: ByteArray): Boolean = melonWriteSram(inBuffer)
    override fun nativeSaveState(slot: Int, filePath: String): Boolean = melonSaveState(slot, filePath)
    override fun nativeLoadState(slot: Int, filePath: String): Boolean = melonLoadState(slot, filePath)

    /**
     * Touch screen / stylus coordinates passthrough.
     * @param x X coordinate (0..255)
     * @param y Y coordinate (0..191 on bottom screen)
     * @param isTouching True if stylus/finger is pressed down
     */
    fun nativeSetTouch(x: Int, y: Int, isTouching: Boolean) = melonSetTouch(x, y, isTouching)

    private external fun melonInit(internalStoragePath: String): Boolean
    private external fun melonLoadRom(romPath: String): Boolean
    private external fun melonUnloadRom()
    private external fun melonDestroy()
    private external fun melonRunFrame(): Boolean
    private external fun melonSetKeys(keyMask: Int)
    private external fun melonSetTouch(x: Int, y: Int, isTouching: Boolean)
    private external fun melonGetVideoBuffer(): IntBuffer?
    private external fun melonGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int
    private external fun melonGetAudioAvailable(): Int
    private external fun melonGetVideoSize(): IntArray?
    private external fun melonGetSramSize(): Int
    private external fun melonReadSram(outBuffer: ByteArray): Boolean
    private external fun melonWriteSram(inBuffer: ByteArray): Boolean
    private external fun melonSaveState(slot: Int, filePath: String): Boolean
    private external fun melonLoadState(slot: Int, filePath: String): Boolean
}
