package com.retropack.domain.rom

import java.nio.charset.StandardCharsets

/**
 * Parsed PlayStation 1 disc or executable image header.
 */
data class PsxRomHeader(
    val title: String,
    val discId: String?,
    val format: String, // "ISO", "CHD", "PBP", "PS-X EXE", "BIN/CUE"
    val isCompressed: Boolean,
    val sectorSize: Int,
    val magicValid: Boolean
)

/**
 * Low-level binary parser for PlayStation 1 disc images (.chd, .iso, .cue, .bin, .pbp)
 * and executable (.exe) files.
 */
object PsxRomParser {

    const val MIN_HEADER_SIZE = 0x9400 // Cover Sector 16 in 2048 or 2352 mode
    private val PSX_EXE_MAGIC = "PS-X EXE".toByteArray(StandardCharsets.US_ASCII)
    private val CHD_MAGIC = "MComprHD".toByteArray(StandardCharsets.US_ASCII)
    private val PBP_MAGIC = byteArrayOf(0x00, 0x50, 0x42, 0x50) // "\0PBP"
    private val ISO_CD001_MAGIC = "CD001".toByteArray(StandardCharsets.US_ASCII)

    /**
     * Inspects a byte buffer and extracts the PS1 ROM / Disc header.
     */
    fun parse(bytes: ByteArray, fallbackTitle: String = "PlayStation Game"): PsxRomHeader {
        // 1. PS-X Executable
        if (bytes.size >= 8 && matchesMagic(bytes, 0, PSX_EXE_MAGIC)) {
            val title = cleanString(String(bytes, 8, Math.min(32, bytes.size - 8), StandardCharsets.US_ASCII))
            return PsxRomHeader(
                title = if (title.isNotBlank()) title else fallbackTitle,
                discId = null,
                format = "PS-X EXE",
                isCompressed = false,
                sectorSize = 0,
                magicValid = true
            )
        }

        // 2. CHD (Compressed Hunks of Data)
        if (bytes.size >= 8 && matchesMagic(bytes, 0, CHD_MAGIC)) {
            return PsxRomHeader(
                title = fallbackTitle,
                discId = null,
                format = "CHD",
                isCompressed = true,
                sectorSize = 2352,
                magicValid = true
            )
        }

        // 3. Sony PBP Container
        if (bytes.size >= 4 && matchesMagic(bytes, 0, PBP_MAGIC)) {
            return PsxRomHeader(
                title = fallbackTitle,
                discId = null,
                format = "PBP",
                isCompressed = true,
                sectorSize = 2352,
                magicValid = true
            )
        }

        // 4. ISO 9660 Sector 16 PVD (Check 2048 byte sectors at 0x8000 and 2352 raw sectors at 0x9300 or 0x9318)
        val isoOffsets = listOf(
            0x8000 to 2048,
            0x9300 to 2352,
            0x9318 to 2352
        )

        for ((pvdOffset, sectorSize) in isoOffsets) {
            if (bytes.size >= pvdOffset + 128 && matchesMagic(bytes, pvdOffset + 1, ISO_CD001_MAGIC)) {
                val sysId = cleanString(String(bytes, pvdOffset + 8, 32, StandardCharsets.US_ASCII))
                val volId = cleanString(String(bytes, pvdOffset + 40, 32, StandardCharsets.US_ASCII))

                val isPlaystation = sysId.contains("PLAYSTATION", ignoreCase = true) ||
                    volId.startsWith("SLUS", ignoreCase = true) ||
                    volId.startsWith("SLES", ignoreCase = true) ||
                    volId.startsWith("SCUS", ignoreCase = true) ||
                    volId.startsWith("SCES", ignoreCase = true) ||
                    volId.startsWith("SLPS", ignoreCase = true) ||
                    volId.startsWith("SLPM", ignoreCase = true)

                val title = if (volId.isNotBlank()) volId else fallbackTitle
                return PsxRomHeader(
                    title = title,
                    discId = if (volId.length in 8..15) volId else null,
                    format = if (sectorSize == 2048) "ISO" else "BIN/CUE",
                    isCompressed = false,
                    sectorSize = sectorSize,
                    magicValid = isPlaystation || sysId.isNotBlank()
                )
            }
        }

        throw InvalidRomException("No valid PS1 disc image (ISO/BIN/CHD/PBP) or PS-X EXE header found.")
    }

    /**
     * Returns true if bytes match any PS1 magic signature.
     */
    fun isPsxRom(bytes: ByteArray): Boolean {
        if (bytes.size >= 8 && matchesMagic(bytes, 0, PSX_EXE_MAGIC)) return true
        if (bytes.size >= 8 && matchesMagic(bytes, 0, CHD_MAGIC)) return true
        if (bytes.size >= 4 && matchesMagic(bytes, 0, PBP_MAGIC)) return true

        val isoOffsets = listOf(0x8000, 0x9300, 0x9318)
        for (offset in isoOffsets) {
            if (bytes.size >= offset + 6 && matchesMagic(bytes, offset + 1, ISO_CD001_MAGIC)) {
                return true
            }
        }
        return false
    }

    private fun matchesMagic(bytes: ByteArray, offset: Int, magic: ByteArray): Boolean {
        if (bytes.size < offset + magic.size) return false
        for (i in magic.indices) {
            if (bytes[offset + i] != magic[i]) return false
        }
        return true
    }

    private fun cleanString(raw: String): String {
        return raw.map { if (it in ' '..'~') it else ' ' }.joinToString("").trim()
    }
}
