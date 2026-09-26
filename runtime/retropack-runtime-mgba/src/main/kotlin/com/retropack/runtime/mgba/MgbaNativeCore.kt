package com.retropack.runtime.mgba

import com.retropack.runtime.core.NativeCore
import com.retropack.runtime.core.NativeCoreBridge
import java.nio.IntBuffer

object MgbaNativeCore : NativeCoreBridge {
    override fun isLoaded(): Boolean = NativeCore.isLoaded()

    val loadedLibraryName: String?
        get() = NativeCore.loadedLibraryName

    val loadError: String?
        get() = NativeCore.loadError

    override fun nativeInit(internalStoragePath: String): Boolean = NativeCore.nativeInit(internalStoragePath)
    override fun nativeLoadRom(romPath: String): Boolean = NativeCore.nativeLoadRom(romPath)
    override fun nativeUnloadRom() = NativeCore.nativeUnloadRom()
    override fun nativeDestroy() = NativeCore.nativeDestroy()
    override fun nativeRunFrame(): Boolean = NativeCore.nativeRunFrame()
    override fun nativeSetKeys(keyMask: Int) = NativeCore.nativeSetKeys(keyMask)
    override fun nativeGetVideoBuffer(): IntBuffer? = NativeCore.nativeGetVideoBuffer()
    override fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int =
        NativeCore.nativeGetAudioSamples(outSamples, maxSamples)
    override fun nativeGetAudioAvailable(): Int = NativeCore.nativeGetAudioAvailable()
    override fun nativeGetVideoSize(): IntArray? = NativeCore.nativeGetVideoSize()
    override fun nativeGetSramSize(): Int = NativeCore.nativeGetSramSize()
    override fun nativeReadSram(outBuffer: ByteArray): Boolean = NativeCore.nativeReadSram(outBuffer)
    override fun nativeWriteSram(inBuffer: ByteArray): Boolean = NativeCore.nativeWriteSram(inBuffer)
    override fun nativeSaveState(slot: Int, filePath: String): Boolean = NativeCore.nativeSaveState(slot, filePath)
    override fun nativeLoadState(slot: Int, filePath: String): Boolean = NativeCore.nativeLoadState(slot, filePath)
}

