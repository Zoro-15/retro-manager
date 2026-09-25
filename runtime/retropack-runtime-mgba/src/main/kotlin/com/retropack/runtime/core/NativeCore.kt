package com.retropack.runtime.core

import java.nio.IntBuffer

/**
 * Low-level JNI bridge to the canonical mGBA 0.10.x C core compiled as `libretropack-runtime.so`.
 *
 * Implements the native core contract defined in masterplan.md Section 5.3 (lines 167-189).
 * All native methods execute synchronously on the caller's emulation thread.
 */
object NativeCore {
    private const val LIBRARY_NAME = "retropack-runtime"

    @Volatile
    private var libraryLoaded: Boolean = false

    init {
        try {
            System.loadLibrary(LIBRARY_NAME)
            libraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            // Permitted on host JVM during unit testing where native .so binaries are not compiled/loaded.
            libraryLoaded = false
        }
    }

    /**
     * Returns true if the native runtime library was successfully loaded into this process.
     */
    fun isLoaded(): Boolean = libraryLoaded

    /**
     * Initializes the native mGBA core environment with internal storage directories for BIOS and config.
     *
     * @param internalStoragePath Absolute private directory path (e.g. context.filesDir.absolutePath).
     * @return true if initialization succeeded, false otherwise.
     */
    external fun nativeInit(internalStoragePath: String): Boolean

    /**
     * Loads a ROM from a private, seekable file into the native mGBA core.
     *
     * @param romPath Absolute path to the verified ROM file on disk.
     * @return true if mGBA accepted and parsed the ROM, false otherwise.
     */
    external fun nativeLoadRom(romPath: String): Boolean

    /**
     * Unloads the current ROM and releases native core emulation allocations.
     */
    external fun nativeUnloadRom()

    /**
     * Completely destroys the native emulation subsystem and deallocates global core state.
     */
    external fun nativeDestroy()

    /**
     * Advances emulation by exactly one frame (e.g. 1/60th second).
     *
     * @return true if frame completed normally, false if execution halted or errored.
     */
    external fun nativeRunFrame(): Boolean

    /**
     * Updates the hardware button bitmask state for the next frame.
     *
     * @param keyMask 10-bit hardware keymask matching GBA hardware register (see [RetroKey]).
     */
    external fun nativeSetKeys(keyMask: Int)

    /**
     * Retrieves the direct native ARGB pixel buffer (240x160 for GBA, 160x144 for GB/GBC).
     *
     * @return Direct [IntBuffer] mapped to native frame memory, or null if no ROM is loaded.
     */
    external fun nativeGetVideoBuffer(): IntBuffer?

    /**
     * Pulls interleaved 16-bit signed stereo audio samples generated during recent frames.
     *
     * @param outSamples Buffer to fill with audio samples (L, R, L, R...).
     * @param maxSamples Maximum number of 16-bit samples to read.
     * @return Actual number of samples written to [outSamples].
     */
    external fun nativeGetAudioSamples(outSamples: ShortArray, maxSamples: Int): Int

    /**
     * Returns the number of samples currently buffered for playback without
     * draining. Lets the drift controller evaluate true ringbuffer occupancy
     * instead of the drained batch size (issue #13). Returns 0 when native
     * is unavailable; callers must fall back to the drained count.
     */
    external fun nativeGetAudioAvailable(): Int

    /**
     * Returns the active frame dimensions as int[width, height], or null when
     * no ROM is loaded, so the GL renderer syncs instead of assuming 240x160
     * for GB/GBC titles (issue #13).
     */
    external fun nativeGetVideoSize(): IntArray?

    /**
     * Returns the size in bytes of the cartridge battery SRAM/Flash/EEPROM save area.
     *
     * @return Non-negative byte count, or 0 if the cartridge has no battery backup.
     */
    external fun nativeGetSramSize(): Int

    /**
     * Reads current SRAM/Flash bytes into [outBuffer] for durable persistence.
     *
     * @param outBuffer Destination byte array of size at least [nativeGetSramSize].
     * @return true if SRAM bytes were copied successfully, false otherwise.
     */
    external fun nativeReadSram(outBuffer: ByteArray): Boolean

    /**
     * Restores cartridge battery SRAM/Flash bytes into active emulator memory from disk.
     *
     * @param inBuffer Source byte array containing valid savedata.
     * @return true if SRAM was successfully restored, false otherwise.
     */
    external fun nativeWriteSram(inBuffer: ByteArray): Boolean

    /**
     * Serializes complete emulator CPU/RAM state into a quick-save file snapshot.
     *
     * @param slot Quick-save slot index (0..3).
     * @param filePath Absolute path where save state snapshot should be written.
     * @return true if state was written successfully, false otherwise.
     */
    external fun nativeSaveState(slot: Int, filePath: String): Boolean

    /**
     * Deserializes and restores complete emulator state from a quick-save file snapshot.
     *
     * @param slot Quick-save slot index (0..3).
     * @param filePath Absolute path of save state snapshot to restore.
     * @return true if state was loaded and applied successfully, false otherwise.
     */
    external fun nativeLoadState(slot: Int, filePath: String): Boolean
}
