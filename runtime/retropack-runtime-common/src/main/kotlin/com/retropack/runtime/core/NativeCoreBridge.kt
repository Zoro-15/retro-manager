package com.retropack.runtime.core

import java.nio.IntBuffer

/**
 * Universal Native Core Contract implemented by all C/C++ retro emulation engines
 * (mGBA, Snes9x, Genesis Plus GX, FCEUmm, Beetle PCE Fast).
 *
 * Implements the universal engine contract described in Section 3 of the architecture plan.
 */
interface NativeCoreBridge {
    fun isLoaded(): Boolean
    fun nativeInit(internalStoragePath: String): Boolean
    fun nativeLoadRom(romPath: String): Boolean
    fun nativeUnloadRom()
    fun nativeDestroy()
    fun nativeRunFrame(): Boolean
    fun nativeSetKeys(keyMask: Int)
    fun nativeGetVideoBuffer(): IntBuffer?
    fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int
    fun nativeGetAudioAvailable(): Int
    fun nativeGetVideoSize(): IntArray?
    fun nativeGetSramSize(): Int
    fun nativeReadSram(outBuffer: ByteArray): Boolean
    fun nativeWriteSram(inBuffer: ByteArray): Boolean
    fun nativeSaveState(slot: Int, filePath: String): Boolean
    fun nativeLoadState(slot: Int, filePath: String): Boolean
    fun nativeEjectDisc(): Boolean = false
    fun nativeInsertDisc(discIndex: Int, discPath: String): Boolean = false
    fun nativeGetDiscCount(): Int = 1
    fun nativeGetCurrentDisc(): Int = 0
    fun nativeSetRumbleCallback(callback: ((motorIndex: Int, strengthPercent: Int, durationMs: Int) -> Unit)?) {}
    fun nativeSetAnalogAxis(axisX: Float, axisY: Float) {}
    fun nativeGetLastError(): String? = null
}
