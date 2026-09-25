package com.retropack.runtime.save

import com.retropack.runtime.core.NativeCore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Abstraction for low-level cartridge SRAM reading and writing.
 */
interface SramStorageSource {
    fun getSramSize(): Int
    fun readSram(outBuffer: ByteArray): Boolean
    fun writeSram(inBuffer: ByteArray): Boolean
}

/**
 * Default [SramStorageSource] backed by the JNI [NativeCore].
 */
object DefaultNativeSramSource : SramStorageSource {
    override fun getSramSize(): Int = try {
        NativeCore.nativeGetSramSize()
    } catch (_: UnsatisfiedLinkError) {
        0
    }

    override fun readSram(outBuffer: ByteArray): Boolean = try {
        NativeCore.nativeReadSram(outBuffer)
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    override fun writeSram(inBuffer: ByteArray): Boolean = try {
        NativeCore.nativeWriteSram(inBuffer)
    } catch (_: UnsatisfiedLinkError) {
        false
    }
}

/**
 * Manages crash-consistent, atomic cartridge SRAM persistence to disk.
 *
 * Implements the constitutional durability specifications:
 * - masterplan.md Section 5.5 (lines 195-207)
 * - architechture.md Constitutional Law 7 & Section 7 (lines 289-295)
 *
 * Invariants Enforced:
 * 1. Dirty-Gated Periodic Flush: Flushes only when SRAM content is modified.
 * 2. Synchronous Lifecycle Flush: Guarantees flush on Activity onPause/onStop.
 * 3. Atomic POSIX fsync Sequence:
 *    a. Write to `filesDir/game.sav.tmp`
 *    b. `stream.flush()`
 *    c. `stream.fd.sync()` (POSIX fsync flushes OS page cache to NAND flash)
 *    d. `stream.close()`
 *    e. `tmp.renameTo(targetSave)` (Atomic filesystem directory swap)
 */
class SaveManager(
    val saveFile: File,
    private val sramSource: SramStorageSource = DefaultNativeSramSource,
    val periodicFlushIntervalSec: Long = 60L
) {
    // Sibling files resolved null-safely so CWD-relative saveFiles
    // (File("game.sav")) still place .tmp/.bak alongside the target.
    private fun sibling(name: String): File {
        val dir = saveFile.parentFile ?: saveFile.absoluteFile.parentFile
        return if (dir != null) File(dir, name) else File(name)
    }

    val tmpSaveFile: File = sibling("${saveFile.name}.tmp")
    val bakSaveFile: File = sibling("${saveFile.name}.bak")

    private val lock = Any()
    private val dirty = AtomicBoolean(false)
    // Null = never flushed: must not compare equal to any real hash (issue #3).
    private var lastFlushedHash: Int? = null
    // Monotonic gate so the 60fps loop's periodicFlush is a cheap timestamp
    // check instead of a full SRAM clone+hash per frame (issue #12).
    private var lastFlushAttemptMs: Long = 0L

    val isDirty: Boolean
        get() = dirty.get()

    /**
     * Explicitly marks SRAM state as modified.
     */
    fun markDirty() {
        dirty.set(true)
    }

    /**
     * Restores saved cartridge state from disk into active emulator memory.
     * Attempts to read [saveFile] first, falling back to [bakSaveFile] if corrupt or missing.
     *
     * @return true if save data was successfully restored into the core, false otherwise.
     */
    fun restore(): Boolean = synchronized(lock) {
        // Clean up any stale temporary files left by an abrupt system power loss
        if (tmpSaveFile.exists()) {
            tmpSaveFile.delete()
        }

        val fileToRead = when {
            saveFile.exists() && saveFile.length() > 0 -> saveFile
            bakSaveFile.exists() && bakSaveFile.length() > 0 -> bakSaveFile
            else -> return false
        }

        return try {
            val bytes = fileToRead.readBytes()
            if (bytes.isEmpty()) return false

            val expectedSize = sramSource.getSramSize()
            if (expectedSize > 0 && bytes.size != expectedSize) {
                return false
            }

            val success = sramSource.writeSram(bytes)
            if (success) {
                dirty.set(false)
                lastFlushedHash = Arrays.hashCode(bytes)
            }
            success
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Executes atomic POSIX fsync save flush.
     *
     * @param force If true, skips dirty-gating and flushes immediately if SRAM size > 0.
     * @return true if new save bytes were committed to disk, false if skipped or failed.
     */
    fun flush(force: Boolean = false): Boolean = synchronized(lock) {
        val sramSize = sramSource.getSramSize()
        if (sramSize <= 0) {
            return false
        }

        val buffer = ByteArray(sramSize)
        val readSuccess = sramSource.readSram(buffer)
        if (!readSuccess) {
            return false
        }

        val currentHash = Arrays.hashCode(buffer)

        // Dirty gating check (null = never flushed, must not skip).
        if (!force && !dirty.get() && lastFlushedHash != null && currentHash == lastFlushedHash) {
            return false
        }

        // Ensure parent directory exists
        val parent = saveFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        // Clean stale tmp
        if (tmpSaveFile.exists()) {
            tmpSaveFile.delete()
        }

        try {
            // Step 1: Open FileOutputStream(filesDir/game.sav.tmp)
            FileOutputStream(tmpSaveFile).use { fos ->
                // Step 2: Write buffer to output stream
                fos.write(buffer)
                // Step 3: Flush userland buffer
                fos.flush()
                // Step 4: POSIX fsync flushes OS page cache to NAND flash
                fos.fd.sync()
            }

            // Step 5: If previous save exists, rotate to backup (checked:
            // an unchecked rename failure here used to orphan the backup and
            // then overwrite the primary — silent save loss).
            if (saveFile.exists()) {
                if (bakSaveFile.exists()) {
                    bakSaveFile.delete()
                }
                if (!saveFile.renameTo(bakSaveFile)) {
                    try {
                        saveFile.copyTo(bakSaveFile, overwrite = true)
                        saveFile.delete()
                    } catch (_: IOException) {
                        if (tmpSaveFile.exists()) tmpSaveFile.delete()
                        return false
                    }
                    if (bakSaveFile.length() == 0L) {
                        if (tmpSaveFile.exists()) tmpSaveFile.delete()
                        return false
                    }
                }
            }

            // Step 6: Atomic directory swap
            val renamed = tmpSaveFile.renameTo(saveFile)
            if (!renamed) {
                // Fallback copy if filesystem doesn't support atomic rename across mounts
                try {
                    tmpSaveFile.copyTo(saveFile, overwrite = true)
                    tmpSaveFile.delete()
                } catch (_: IOException) {
                    if (tmpSaveFile.exists()) tmpSaveFile.delete()
                    return false
                }
            }

            dirty.set(false)
            lastFlushedHash = currentHash
            lastFlushAttemptMs = System.currentTimeMillis()
            return true
        } catch (_: IOException) {
            // Clean up temporary file on failure
            if (tmpSaveFile.exists()) {
                tmpSaveFile.delete()
            }
            return false
        }
    }

    /**
     * Checks if active SRAM has drifted from last flushed hash and marks dirty if so.
     * @return true if SRAM was found to be dirty.
     */
    fun checkAndMarkDirty(): Boolean = synchronized(lock) {
        val sramSize = sramSource.getSramSize()
        if (sramSize <= 0) return false

        // Never flushed: treat as dirty so the first periodic flush commits.
        if (lastFlushedHash == null) {
            dirty.set(true)
            return true
        }

        val buffer = ByteArray(sramSize)
        if (sramSource.readSram(buffer)) {
            val hash = Arrays.hashCode(buffer)
            if (hash != lastFlushedHash) {
                dirty.set(true)
                return true
            }
        }
        return dirty.get()
    }

    /**
     * Immediately flushes SRAM to disk, bypassing dirty gating.
     */
    fun flushNow(): Boolean = flush(force = true)

    /**
     * Restores saved cartridge state from disk into active emulator memory.
     */
    fun restoreToSram(): Boolean = restore()

    /**
     * Periodically flushes modified SRAM to disk if dirty or data has drifted.
     * Time-gated: at most one SRAM probe per [periodicFlushIntervalSec], so the
     * 60fps emulation loop pays a timestamp comparison per frame instead of a
     * full native clone+hash (issue #12). Lifecycle paths use [flushNow] and
     * bypass this gate.
     */
    fun periodicFlush(currentTimeMs: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        if (currentTimeMs - lastFlushAttemptMs < periodicFlushIntervalSec * 1000L) {
            return false
        }
        lastFlushAttemptMs = currentTimeMs
        if (checkAndMarkDirty()) {
            return flush(force = false)
        }
        return false
    }
}
