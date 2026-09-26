package com.retropack.runtime.genesis

import com.retropack.runtime.core.NativeCoreBridge
import java.nio.IntBuffer

/**
 * JNI bridge to the Genesis Plus GX Sega Mega Drive / Master System / Game Gear C core.
 */
object GenesisNativeCore : NativeCoreBridge {
    private val CANDIDATE_LIBRARIES = listOf("retropack-runtime-genesis", "genesis_plus_gx")

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
            loadError = lastError?.message ?: "Genesis native library link failure"
        }
    }

    override fun isLoaded(): Boolean = libraryLoaded

    override fun nativeInit(internalStoragePath: String): Boolean = genesisInit(internalStoragePath)
    override fun nativeLoadRom(romPath: String): Boolean = genesisLoadRom(romPath)
    override fun nativeUnloadRom() = genesisUnloadRom()
    override fun nativeDestroy() = genesisDestroy()
    override fun nativeRunFrame(): Boolean = genesisRunFrame()
    override fun nativeSetKeys(keyMask: Int) = genesisSetKeys(keyMask)
    override fun nativeGetVideoBuffer(): IntBuffer? = genesisGetVideoBuffer()
    override fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int =
        genesisGetAudioSamples(outSamples, maxSamples)
    override fun nativeGetAudioAvailable(): Int = genesisGetAudioAvailable()
    override fun nativeGetVideoSize(): IntArray? = genesisGetVideoSize()
    override fun nativeGetSramSize(): Int = genesisGetSramSize()
    override fun nativeReadSram(outBuffer: ByteArray): Boolean = genesisReadSram(outBuffer)
    override fun nativeWriteSram(inBuffer: ByteArray): Boolean = genesisWriteSram(inBuffer)
    override fun nativeSaveState(slot: Int, filePath: String): Boolean = genesisSaveState(slot, filePath)
    override fun nativeLoadState(slot: Int, filePath: String): Boolean = genesisLoadState(slot, filePath)

    private external fun genesisInit(internalStoragePath: String): Boolean
    private external fun genesisLoadRom(romPath: String): Boolean
    private external fun genesisUnloadRom()
    private external fun genesisDestroy()
    private external fun genesisRunFrame(): Boolean
    private external fun genesisSetKeys(keyMask: Int)
    private external fun genesisGetVideoBuffer(): IntBuffer?
    private external fun genesisGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int
    private external fun genesisGetAudioAvailable(): Int
    private external fun genesisGetVideoSize(): IntArray?
    private external fun genesisGetSramSize(): Int
    private external fun genesisReadSram(outBuffer: ByteArray): Boolean
    private external fun genesisWriteSram(inBuffer: ByteArray): Boolean
    private external fun genesisSaveState(slot: Int, filePath: String): Boolean
    private external fun genesisLoadState(slot: Int, filePath: String): Boolean
}
