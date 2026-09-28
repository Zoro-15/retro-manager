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
    private val NDS_EXTENSIONS = setOf("nds", "srl", "dsi", "ids")

    fun parse(bytes: ByteArray, fileName: String? = null): NdsRomHeader {
        if (bytes.size < MIN_HEADER_SIZE) {
            throw InvalidRomException("NDS ROM too small to contain valid header (${bytes.size} bytes).")
        }

        val titleRaw = String(bytes, 0x00, 12, StandardCharsets.US_ASCII)
        val cleanedTitle = titleRaw.map { if (it in ' '..'~') it else ' ' }.joinToString("").trim()

        val gameCode = String(bytes, 0x0C, 4, StandardCharsets.US_ASCII).trim()
        val makerCode = String(bytes, 0x10, 2, StandardCharsets.US_ASCII).trim()
        val unitCode = bytes[0x12].toInt() and 0xFF
        val version = bytes[0x1E].toInt() and 0xFF

        val arm9Offset = readUint32LE(bytes, 0x20)
        val arm7Offset = readUint32LE(bytes, 0x28)
        val headerCrc = (bytes[0x15C].toInt() and 0xFF) or ((bytes[0x15D].toInt() and 0xFF) shl 8)

        val ext = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
        val isNdsExtension = NDS_EXTENSIONS.contains(ext)

        val isValidGameCode = gameCode.length in 3..4 && gameCode.all { it.isLetterOrDigit() }
        val isValidOffsets = arm9Offset in 0x200..0x2000000 && arm7Offset in 0x200..0x2000000

        if (!isValidGameCode && !isValidOffsets && !isNdsExtension) {
            throw InvalidRomException("Not a valid Nintendo DS ROM header.")
        }

        val fallbackName = fileName?.substringBeforeLast('.')?.trim()?.ifEmpty { "NDS Game" } ?: "NDS Game"
        val resolvedTitle = if (cleanedTitle.isBlank()) fallbackName else cleanedTitle

        return NdsRomHeader(
            title = resolvedTitle,
            gameCode = gameCode.ifEmpty { "NDS" },
            makerCode = makerCode.ifEmpty { "01" },
            unitCode = unitCode,
            isDsiEnhanced = (unitCode and 0x02) != 0,
            version = version,
            arm9Offset = arm9Offset,
            arm7Offset = arm7Offset,
            headerCrc16 = headerCrc,
            magicValid = true
        )
    }

    fun isNdsRom(bytes: ByteArray, fileName: String? = null): Boolean {
        if (bytes.size < MIN_HEADER_SIZE) return false
        if (!fileName.isNullOrBlank()) {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (NDS_EXTENSIONS.contains(ext)) {
                return true
            }
        }
        val gameCode = String(bytes, 0x0C, 4, StandardCharsets.US_ASCII).trim()
        val arm9Offset = readUint32LE(bytes, 0x20)
        val arm7Offset = readUint32LE(bytes, 0x28)
        return (gameCode.length in 3..4 && gameCode.all { it.isLetterOrDigit() }) &&
            arm9Offset in 0x200..0x2000000 && arm7Offset in 0x200..0x2000000
    }

    private fun readUint32LE(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }
}
