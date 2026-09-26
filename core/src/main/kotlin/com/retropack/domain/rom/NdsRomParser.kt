package com.retropack.domain.rom

import java.nio.charset.StandardCharsets

/**
 * Parsed Nintendo DS cartridge header.
 */
data class NdsRomHeader(
    val title: String,
    val gameCode: String,
    val makerCode: String,
    val unitCode: Int,
    val isDsiEnhanced: Boolean,
    val version: Int,
    val arm9Offset: Long,
    val arm7Offset: Long,
    val headerCrc16: Int,
    val magicValid: Boolean
)

/**
 * Low-level binary parser for Nintendo DS ROM images (.nds).
 */
object NdsRomParser {

    const val MIN_HEADER_SIZE = 0x200 // 512 bytes

    fun parse(bytes: ByteArray): NdsRomHeader {
        if (bytes.size < MIN_HEADER_SIZE) {
            throw InvalidRomException("NDS ROM too small to contain valid header ($bytes.size bytes).")
        }

        val titleRaw = String(bytes, 0x00, 12, StandardCharsets.US_ASCII)
        val title = titleRaw.map { if (it in ' '..'~') it else ' ' }.joinToString("").trim()

        val gameCode = String(bytes, 0x0C, 4, StandardCharsets.US_ASCII).trim()
        val makerCode = String(bytes, 0x10, 2, StandardCharsets.US_ASCII).trim()
        val unitCode = bytes[0x12].toInt() and 0xFF
        val version = bytes[0x1E].toInt() and 0xFF

        val arm9Offset = readUint32LE(bytes, 0x20)
        val arm7Offset = readUint32LE(bytes, 0x28)
        val headerCrc = (bytes[0x15C].toInt() and 0xFF) or ((bytes[0x15D].toInt() and 0xFF) shl 8)

        // Valid NDS header heuristics:
        // 1. gameCode is 4 ASCII alphanumeric chars (e.g. A-Z, 0-9)
        // 2. arm9Offset is non-zero and aligned (typically 0x4000 or 0x8000)
        // 3. arm7Offset is non-zero
        val isValidGameCode = gameCode.length == 4 && gameCode.all { it in 'A'..'Z' || it in '0'..'9' }
        val isValidOffsets = arm9Offset in 0x200..0x100000 && arm7Offset in 0x200..0x200000

        if (!isValidGameCode && !isValidOffsets) {
            throw InvalidRomException("Not a valid Nintendo DS ROM header.")
        }

        return NdsRomHeader(
            title = if (title.isBlank()) "NDS Game" else title,
            gameCode = gameCode,
            makerCode = makerCode,
            unitCode = unitCode,
            isDsiEnhanced = (unitCode and 0x02) != 0,
            version = version,
            arm9Offset = arm9Offset,
            arm7Offset = arm7Offset,
            headerCrc16 = headerCrc,
            magicValid = true
        )
    }

    fun isNdsRom(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_HEADER_SIZE) return false
        val gameCode = String(bytes, 0x0C, 4, StandardCharsets.US_ASCII).trim()
        val arm9Offset = readUint32LE(bytes, 0x20)
        val arm7Offset = readUint32LE(bytes, 0x28)
        return gameCode.length == 4 && gameCode.all { it in 'A'..'Z' || it in '0'..'9' } &&
            arm9Offset in 0x200..0x100000 && arm7Offset in 0x200..0x200000
    }

    private fun readUint32LE(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }
}
