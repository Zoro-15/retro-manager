package com.retropack.runtime.save

import com.retropack.runtime.core.EmulationEngine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.IntBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Metadata descriptor stored alongside save state binary snapshots.
 */
data class SaveSlotMetadata(
    val slot: Int,
    val timestamp: Long,
    val gameTitle: String = "",
    val frameCount: Long = 0L
) {
    fun toJson(): String {
        return """{"slot":$slot,"timestamp":$timestamp,"gameTitle":"$gameTitle","frameCount":$frameCount}"""
    }

    companion object {
        fun fromJson(json: String): SaveSlotMetadata? {
            return try {
                var slot = 0
                var timestamp = 0L
                var gameTitle = ""
                var frameCount = 0L

                val clean = json.trim().removeSurrounding("{", "}")
                val pairs = clean.split(",")
                for (pair in pairs) {
                    val kv = pair.split(":", limit = 2)
                    if (kv.size == 2) {
                        val key = kv[0].trim().removeSurrounding("\"")
                        val value = kv[1].trim().removeSurrounding("\"")
                        when (key) {
                            "slot" -> slot = value.toIntOrNull() ?: 0
                            "timestamp" -> timestamp = value.toLongOrNull() ?: 0L
                            "gameTitle" -> gameTitle = value
                            "frameCount" -> frameCount = value.toLongOrNull() ?: 0L
                        }
                    }
                }
                SaveSlotMetadata(slot, timestamp, gameTitle, frameCount)
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Information descriptor for an individual visual save state slot.
 */
data class SaveSlotInfo(
    val slot: Int,
    val isAutoSave: Boolean,
    val exists: Boolean,
    val timestamp: Long,
    val relativeTime: String,
    val metadata: SaveSlotMetadata?,
    val stateFile: File,
    val thumbnailFile: File?
)

/**
 * Visual Save State Manager for RetroPack.
 *
 * Manages 5 dedicated manual save state slots (Slot 1 to Slot 5) plus an Auto-Save Slot 0.
 * For each slot, stores:
 *  1. State payload binary: `slot_X.state`
 *  2. Live 120x80 screenshot thumbnail: `slot_X.png`
 *  3. Metadata JSON descriptor: `slot_X.meta`
 */
class SaveStateManager(
    val storageDir: File
) {
    companion object {
        const val AUTO_SAVE_SLOT = 0
        const val MIN_USER_SLOT = 1
        const val MAX_USER_SLOT = 5
        const val THUMBNAIL_WIDTH = 120
        const val THUMBNAIL_HEIGHT = 80

        /**
         * Computes a human-readable relative time string from an epoch timestamp.
         */
        fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
            if (timestamp <= 0L) return "Empty"
            val diffMs = now - timestamp
            if (diffMs < 0L) return "Just now"

            val diffSec = diffMs / 1000L
            val diffMin = diffSec / 60L
            val diffHours = diffMin / 60L
            val diffDays = diffHours / 24L

            return when {
                diffSec < 45 -> "Just now"
                diffMin < 2 -> "1 min ago"
                diffMin < 60 -> "$diffMin mins ago"
                diffHours < 2 -> "1 hour ago"
                diffHours < 24 -> "$diffHours hours ago"
                diffDays < 2 -> "Yesterday"
                diffDays < 30 -> "$diffDays days ago"
                else -> "${diffDays / 30} months ago"
            }
        }

        /**
         * Downsamples a native ARGB8888 frame buffer into a 120x80 RGBA thumbnail image.
         */
        fun downsampleFrame(
            videoBuffer: IntBuffer,
            srcW: Int,
            srcH: Int,
            dstW: Int = THUMBNAIL_WIDTH,
            dstH: Int = THUMBNAIL_HEIGHT
        ): IntArray {
            val result = IntArray(dstW * dstH)
            val srcPixels = IntArray(srcW * srcH)
            val oldPos = videoBuffer.position()
            videoBuffer.position(0)
            videoBuffer.get(srcPixels)
            videoBuffer.position(oldPos)

            for (y in 0 until dstH) {
                val srcY = (y * srcH) / dstH
                for (x in 0 until dstW) {
                    val srcX = (x * srcW) / dstW
                    result[y * dstW + x] = srcPixels[srcY * srcW + srcX]
                }
            }
            return result
        }

        /**
         * Encodes raw ARGB pixels into a valid uncompressed PNG file byte array.
         */
        fun encodeToPng(pixels: IntArray, width: Int, height: Int): ByteArray {
            val baos = ByteArrayOutputStream()

            // 1. PNG Signature
            baos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

            // 2. IHDR Chunk (13 bytes)
            val ihdr = ByteArrayOutputStream()
            ihdr.write(intToBytes(width))
            ihdr.write(intToBytes(height))
            ihdr.write(8) // Bit depth: 8
            ihdr.write(6) // Color type: 6 (RGBA)
            ihdr.write(0) // Compression method
            ihdr.write(0) // Filter method
            ihdr.write(0) // Interlace method
            writeChunk(baos, "IHDR", ihdr.toByteArray())

            // 3. IDAT Chunk (Raw scanlines with filter byte 0)
            val rawScanlines = ByteArrayOutputStream()
            for (y in 0 until height) {
                rawScanlines.write(0) // Filter byte (0 = None)
                for (x in 0 until width) {
                    val argb = pixels[y * width + x]
                    val a = (argb ushr 24) and 0xFF
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    rawScanlines.write(r)
                    rawScanlines.write(g)
                    rawScanlines.write(b)
                    rawScanlines.write(if (a == 0) 255 else a) // Ensure opaque if alpha 0
                }
            }

            val compressedIdat = ByteArrayOutputStream()
            DeflaterOutputStream(compressedIdat, Deflater(Deflater.BEST_SPEED)).use { dos ->
                dos.write(rawScanlines.toByteArray())
            }
            writeChunk(baos, "IDAT", compressedIdat.toByteArray())

            // 4. IEND Chunk
            writeChunk(baos, "IEND", ByteArray(0))

            return baos.toByteArray()
        }

        private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
            out.write(intToBytes(data.size))
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            out.write(typeBytes)
            out.write(data)

            val crc = CRC32()
            crc.update(typeBytes)
            crc.update(data)
            out.write(intToBytes(crc.value.toInt()))
        }

        private fun intToBytes(value: Int): ByteArray {
            return byteArrayOf(
                ((value ushr 24) and 0xFF).toByte(),
                ((value ushr 16) and 0xFF).toByte(),
                ((value ushr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte()
            )
        }
    }

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    fun getStateFile(slot: Int): File = File(storageDir, "slot_$slot.state")
    fun getThumbnailFile(slot: Int): File = File(storageDir, "slot_$slot.png")
    fun getMetaFile(slot: Int): File = File(storageDir, "slot_$slot.meta")

    /**
     * Saves complete state binary, live thumbnail, and metadata for [slot].
     */
    fun saveState(
        slot: Int,
        engine: EmulationEngine,
        videoBuffer: IntBuffer? = null,
        nativeWidth: Int = 240,
        nativeHeight: Int = 160,
        gameTitle: String = ""
    ): Boolean {
        require(slot in AUTO_SAVE_SLOT..MAX_USER_SLOT) { "Slot must be between $AUTO_SAVE_SLOT and $MAX_USER_SLOT" }

        val stateFile = getStateFile(slot)
        val tempStateFile = File(storageDir, "slot_$slot.state.tmp")

        // Step 1: Save state snapshot via engine
        val stateSaved = engine.saveState(slot, tempStateFile.absolutePath)
        if (!stateSaved || !tempStateFile.exists() || tempStateFile.length() == 0L) {
            tempStateFile.delete()
            return false
        }

        // Atomic swap state file
        if (stateFile.exists()) {
            stateFile.delete()
        }
        if (!tempStateFile.renameTo(stateFile)) {
            tempStateFile.copyTo(stateFile, overwrite = true)
            tempStateFile.delete()
        }

        // Step 2: Save Screenshot Thumbnail
        if (videoBuffer != null && nativeWidth > 0 && nativeHeight > 0) {
            try {
                val thumbPixels = downsampleFrame(videoBuffer, nativeWidth, nativeHeight, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                val pngBytes = encodeToPng(thumbPixels, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                val thumbFile = getThumbnailFile(slot)
                FileOutputStream(thumbFile).use { fos ->
                    fos.write(pngBytes)
                    fos.flush()
                }
            } catch (_: Exception) {}
        }

        // Step 3: Write metadata JSON
        val meta = SaveSlotMetadata(
            slot = slot,
            timestamp = System.currentTimeMillis(),
            gameTitle = gameTitle
        )
        try {
            getMetaFile(slot).writeText(meta.toJson(), Charsets.UTF_8)
        } catch (_: IOException) {}

        return true
    }

    /**
     * Restores complete emulator state from [slot].
     */
    fun loadState(slot: Int, engine: EmulationEngine): Boolean {
        require(slot in AUTO_SAVE_SLOT..MAX_USER_SLOT) { "Slot must be between $AUTO_SAVE_SLOT and $MAX_USER_SLOT" }
        val stateFile = getStateFile(slot)
        if (!stateFile.exists() || stateFile.length() == 0L) {
            return false
        }
        return engine.loadState(slot, stateFile.absolutePath)
    }

    /**
     * Returns descriptor info for [slot].
     */
    fun getSlotInfo(slot: Int, now: Long = System.currentTimeMillis()): SaveSlotInfo {
        val stateFile = getStateFile(slot)
        val thumbFile = getThumbnailFile(slot)
        val metaFile = getMetaFile(slot)

        val exists = stateFile.exists() && stateFile.length() > 0L
        val metadata = if (metaFile.exists()) {
            try { SaveSlotMetadata.fromJson(metaFile.readText(Charsets.UTF_8)) } catch (_: Exception) { null }
        } else null

        val timestamp = metadata?.timestamp ?: if (exists) stateFile.lastModified() else 0L
        val relative = if (exists) formatRelativeTime(timestamp, now) else "Empty"

        return SaveSlotInfo(
            slot = slot,
            isAutoSave = (slot == AUTO_SAVE_SLOT),
            exists = exists,
            timestamp = timestamp,
            relativeTime = relative,
            metadata = metadata,
            stateFile = stateFile,
            thumbnailFile = if (thumbFile.exists() && thumbFile.length() > 0L) thumbFile else null
        )
    }

    /**
     * Returns descriptor info for all slots (Slot 0 Auto + Slots 1..5).
     */
    fun getAllSlots(now: Long = System.currentTimeMillis()): List<SaveSlotInfo> {
        val list = mutableListOf<SaveSlotInfo>()
        for (slot in AUTO_SAVE_SLOT..MAX_USER_SLOT) {
            list.add(getSlotInfo(slot, now))
        }
        return list
    }

    /**
     * Deletes saved state, thumbnail, and metadata for [slot].
     */
    fun deleteSlot(slot: Int): Boolean {
        var deleted = false
        val s = getStateFile(slot)
        val t = getThumbnailFile(slot)
        val m = getMetaFile(slot)
        if (s.exists()) deleted = s.delete() || deleted
        if (t.exists()) t.delete()
        if (m.exists()) m.delete()
        return deleted
    }
}
