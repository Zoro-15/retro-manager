package com.retropack.runtime.core

import java.nio.IntBuffer

/**
 * Clean Kotlin engine interface abstracting the native JNI mGBA layer
 * for GameActivity, audio playback thread, and surface renderers.
 */
interface EmulationEngine : AutoCloseable {
    /** Current lifecycle state of the emulation engine. */
    val state: EmulationState

    /**
     * Initializes the native core with an internal storage directory.
     * Must be called before loading any ROM.
     */
    fun initialize(internalStoragePath: String): Boolean

    /**
     * Loads a verified ROM from [romPath].
     */
    fun loadRom(romPath: String): Boolean

    /**
     * Unloads the active ROM and releases ROM-specific allocations.
     */
    fun unloadRom()

    /**
     * Steps the emulator forward by one frame.
     * @return true if the frame ran successfully, false if an error occurred.
     */
    fun stepFrame(): Boolean

    /**
     * Sets the active hardware keymask for upcoming frames.
     */
    fun setKeyMask(mask: Int)

    /**
     * Convenience method to set keys from a variable list of [RetroKey]s.
     */
    fun setKeys(vararg keys: RetroKey)

    /**
     * Returns a direct [IntBuffer] containing native ARGB pixels for display rendering.
     */
    fun getVideoBuffer(): IntBuffer?

    /**
     * Reads interleaved 16-bit stereo audio samples into [outSamples].
     * @return Number of samples written.
     */
    fun getAudioSamples(outSamples: ShortArray, maxSamples: Int): Int

    /**
     * Returns the size in bytes of cartridge SRAM/Flash memory.
     */
    fun getSramSize(): Int

    /**
     * Reads current cartridge SRAM bytes into [outBuffer].
     */
    fun readSram(outBuffer: ByteArray): Boolean

    /**
     * Restores cartridge SRAM bytes from [inBuffer].
     */
    fun writeSram(inBuffer: ByteArray): Boolean

    /**
     * Saves a snapshot of complete emulator state to [targetPath].
     */
    fun saveState(slot: Int, targetPath: String): Boolean

    /**
     * Restores complete emulator state from snapshot at [sourcePath].
     */
    fun loadState(slot: Int, sourcePath: String): Boolean

    /**
     * Suspends active frame execution.
     */
    fun pause()

    /**
     * Resumes active frame execution.
     */
    fun resume()

    /**
     * Returns the active frame dimensions as int[width, height], or null if uninitialized/unsupported.
     */
    fun getVideoSize(): IntArray? = null

    /**
     * Completely shuts down the engine and releases all native resources.
     */
    override fun close()
}

/**
 * Standard production implementation of [EmulationEngine] driving [NativeCoreBridge]
 * with thread synchronization and state machine validation.
 */
class NativeEmulationEngine(
    val core: NativeCoreBridge = NativeCore
) : EmulationEngine {

    private val lock = Any()

    @Volatile
    private var _state: EmulationState = EmulationState.UNINITIALIZED

    override val state: EmulationState
        get() = _state

    override fun initialize(internalStoragePath: String): Boolean = synchronized(lock) {
        if (_state != EmulationState.UNINITIALIZED && _state != EmulationState.STOPPED) {
            return false
        }
        val success = try {
            core.nativeInit(internalStoragePath)
        } catch (e: UnsatisfiedLinkError) {
            false
        }
        _state = if (success) EmulationState.INITIALIZED else EmulationState.ERROR
        return success
    }

    override fun loadRom(romPath: String): Boolean = synchronized(lock) {
        check(_state == EmulationState.INITIALIZED || _state == EmulationState.STOPPED) {
            "Cannot load ROM in state $_state"
        }
        val success = try {
            core.nativeLoadRom(romPath)
        } catch (e: UnsatisfiedLinkError) {
            false
        }
        _state = if (success) EmulationState.INITIALIZED else EmulationState.ERROR
        return success
    }

    override fun unloadRom() = synchronized(lock) {
        if (_state.isEmulating || _state == EmulationState.INITIALIZED) {
            try {
                core.nativeUnloadRom()
            } catch (_: UnsatisfiedLinkError) {}
            _state = EmulationState.STOPPED
        }
    }

    override fun stepFrame(): Boolean = synchronized(lock) {
        if (_state == EmulationState.INITIALIZED) {
            _state = EmulationState.RUNNING
        }
        if (_state != EmulationState.RUNNING) {
            return false
        }
        val success = try {
            core.nativeRunFrame()
        } catch (e: UnsatisfiedLinkError) {
            false
        }
        if (!success) {
            _state = EmulationState.ERROR
        }
        return success
    }

    override fun setKeyMask(mask: Int) = synchronized(lock) {
        try {
            core.nativeSetKeys(mask and RetroKey.ALL_KEYS_MASK)
        } catch (_: UnsatisfiedLinkError) {}
    }

    override fun setKeys(vararg keys: RetroKey) {
        setKeyMask(RetroKey.maskOf(*keys))
    }

    override fun getVideoBuffer(): IntBuffer? = synchronized(lock) {
        return try {
            core.nativeGetVideoBuffer()
        } catch (e: UnsatisfiedLinkError) {
            null
        }
    }

    override fun getVideoSize(): IntArray? = synchronized(lock) {
        return try {
            core.nativeGetVideoSize()
        } catch (e: UnsatisfiedLinkError) {
            null
        }
    }

    override fun getAudioSamples(outSamples: ShortArray, maxSamples: Int): Int = synchronized(lock) {
        return try {
            core.nativeGetAudioSamples(outSamples, maxSamples)
        } catch (e: UnsatisfiedLinkError) {
            0
        }
    }

    override fun getSramSize(): Int = synchronized(lock) {
        return try {
            core.nativeGetSramSize()
        } catch (e: UnsatisfiedLinkError) {
            0
        }
    }

    override fun readSram(outBuffer: ByteArray): Boolean = synchronized(lock) {
        return try {
            core.nativeReadSram(outBuffer)
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    override fun writeSram(inBuffer: ByteArray): Boolean = synchronized(lock) {
        return try {
            core.nativeWriteSram(inBuffer)
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    override fun saveState(slot: Int, targetPath: String): Boolean = synchronized(lock) {
        require(slot in 0..9) { "State slot must be between 0 and 9" }
        return try {
            core.nativeSaveState(slot, targetPath)
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    override fun loadState(slot: Int, sourcePath: String): Boolean = synchronized(lock) {
        require(slot in 0..9) { "State slot must be between 0 and 9" }
        return try {
            core.nativeLoadState(slot, sourcePath)
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    override fun pause() = synchronized(lock) {
        if (_state == EmulationState.RUNNING) {
            _state = EmulationState.PAUSED
        }
    }

    override fun resume() = synchronized(lock) {
        if (_state == EmulationState.PAUSED) {
            _state = EmulationState.RUNNING
        }
    }

    override fun close() = synchronized(lock) {
        if (_state != EmulationState.UNINITIALIZED) {
            try {
                if (_state.isEmulating || _state == EmulationState.INITIALIZED) {
                    core.nativeUnloadRom()
                }
                core.nativeDestroy()
            } catch (_: UnsatisfiedLinkError) {}
            _state = EmulationState.UNINITIALIZED
        }
    }
}
