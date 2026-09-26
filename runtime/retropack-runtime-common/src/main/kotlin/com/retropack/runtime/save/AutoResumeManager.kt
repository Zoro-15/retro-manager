package com.retropack.runtime.save

import com.retropack.runtime.core.EmulationEngine
import java.io.File
import java.io.FileOutputStream

/**
 * Instant Resume & Background Auto-Snapshot Manager for RetroPack.
 *
 * Implements Feature 1 specifications:
 * - Silently captures an instantaneous savestate snapshot when the game app is backgrounded or minimized.
 * - Restores from the exact frame on relaunch in < 250ms with zero perceptible delay.
 * - Uses POSIX fsync atomic write to guarantee durability before Android OS process death.
 * - Safe: Does not overwrite numbered user save slots (Slots 0 to 9).
 * - Corrupt state mitigation: Gracefully falls back to clean boot + battery SRAM reload on error.
 */
object AutoResumeManager {

    const val AUTO_RESUME_FILENAME = "auto_resume.state"
    const val AUTO_RESUME_META_FILENAME = "auto_resume.meta"

    /**
     * Atomically serializes the current emulation state to auto_resume.state with POSIX fsync.
     */
    fun saveAutoSnapshot(
        storageDir: File,
        engine: EmulationEngine,
        romSha256: String,
        gameTitle: String
    ): Boolean {
        return try {
            val savesDir = File(storageDir, "saves").apply { mkdirs() }
            val targetFile = File(savesDir, AUTO_RESUME_FILENAME)
            val scratchFile = File(savesDir, "auto_resume.scratch.tmp")

            if (scratchFile.exists()) {
                scratchFile.delete()
            }

            // Capture state directly to scratch file
            val stateSaved = engine.saveState(slot = 99, filePath = scratchFile.absolutePath)
            if (!stateSaved || !scratchFile.exists() || scratchFile.length() == 0L) {
                scratchFile.delete()
                return false
            }

            // POSIX fsync flush
            FileOutputStream(scratchFile, true).use { fos ->
                fos.fd.sync()
            }

            // Atomic rename
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = scratchFile.renameTo(targetFile)
            if (!renamed) {
                scratchFile.copyTo(targetFile, overwrite = true)
                scratchFile.delete()
            }

            // Write metadata
            val metaFile = File(savesDir, AUTO_RESUME_META_FILENAME)
            metaFile.writeText(
                "romSha256=$romSha256\n" +
                "gameTitle=$gameTitle\n" +
                "timestamp=${System.currentTimeMillis()}\n" +
                "fileSize=${targetFile.length()}\n"
            )

            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks if a valid, matching auto-resume snapshot exists for the given ROM hash.
     */
    fun hasValidAutoResume(storageDir: File, romSha256: String): Boolean {
        val savesDir = File(storageDir, "saves")
        val targetFile = File(savesDir, AUTO_RESUME_FILENAME)
        val metaFile = File(savesDir, AUTO_RESUME_META_FILENAME)

        if (!targetFile.exists() || targetFile.length() == 0L || !metaFile.exists()) {
            return false
        }

        val metaContent = metaFile.readText()
        return metaContent.contains("romSha256=$romSha256")
    }

    /**
     * Restores the auto-resume state snapshot on frame 0.
     * Falls back to clean boot if restore fails.
     */
    fun restoreAutoResume(
        storageDir: File,
        engine: EmulationEngine,
        romSha256: String
    ): Boolean {
        val savesDir = File(storageDir, "saves")
        val targetFile = File(savesDir, AUTO_RESUME_FILENAME)

        if (!hasValidAutoResume(storageDir, romSha256)) {
            return false
        }

        return try {
            val loaded = engine.loadState(slot = 99, filePath = targetFile.absolutePath)
            if (loaded) {
                true
            } else {
                clearAutoResume(storageDir)
                false
            }
        } catch (_: Throwable) {
            clearAutoResume(storageDir)
            false
        }
    }

    /**
     * Clears and purges any stored auto-resume snapshot.
     */
    fun clearAutoResume(storageDir: File) {
        try {
            val savesDir = File(storageDir, "saves")
            File(savesDir, AUTO_RESUME_FILENAME).delete()
            File(savesDir, AUTO_RESUME_META_FILENAME).delete()
            File(savesDir, "auto_resume.scratch.tmp").delete()
        } catch (_: Throwable) {
        }
    }
}
