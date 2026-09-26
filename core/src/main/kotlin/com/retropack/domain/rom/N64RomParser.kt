package com.retropack.domain.rom

import java.nio.charset.StandardCharsets

/**
 * Parsed Nintendo 64 cartridge header.
 */
data class N64RomHeader(
    val title: String,
    val gameCode: String,
    val countryCode: Char,
    val version: Int,
    val byteOrder: String, // "Z64 (Big-Endian)", "V64 (Byte-Swapped)", "N64 (Little-Endian)"
    val crc1: Long,
    val crc2: Long,
    val magicValid: Boolean
)

/**
 * Low-level binary parser for Nintendo 64 ROM images (.z64, .v64, .n64).
 */
object N64RomParser {

    const val MIN_HEADER_SIZE = 0x40 // 64 bytes

    // Magic signatures
    val MAGIC_Z64 = byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40)
    val MAGIC_V64 = byteArrayOf(0x37, 0x80.toByte(), 0x40, 0x12)
    val MAGIC_N64 = byteArrayOf(0x40, 0x12, 0x37, 0x80.toByte())

    fun parse(bytes: ByteArray): N64RomHeader {
        if (bytes.size < MIN_HEADER_SIZE) {
            throw InvalidRomException("N64 ROM too small to contain valid header ($bytes.size bytes).")
        }

        val isZ64 = matchesMagic(bytes, 0, MAGIC_Z64)
        val isV64 = matchesMagic(bytes, 0, MAGIC_V64)
        val isN64 = matchesMagic(bytes, 0, MAGIC_N64)

        if (!isZ64 && !isV64 && !isN64) {
            throw InvalidRomException("Invalid N64 ROM header magic.")
        }

        val normalized = when {
            isZ64 -> bytes.copyOfRange(0, MIN_HEADER_SIZE)
            isV64 -> {
                val b = ByteArray(MIN_HEADER_SIZE)
                for (i in 0 until MIN_HEADER_SIZE step 2) {
                    b[i] = bytes[i + 1]
                    b[i + 1] = bytes[i]
                }
                b
            }
            else -> { // Little-endian
                val b = ByteArray(MIN_HEADER_SIZE)
                for (i in 0 until MIN_HEADER_SIZE step 4) {
                    b[i] = bytes[i + 3]
                    b[i + 1] = bytes[i + 2]
                    b[i + 2] = bytes[i + 1]
                    b[i + 3] = bytes[i]
                }
                b
            }
        }

        val titleRaw = String(normalized, 0x20, 20, StandardCharsets.US_ASCII)
        val title = titleRaw.map { if (it in ' '..'~') it else ' ' }.joinToString("").trim()
        val gameCode = String(normalized, 0x3B, 4, StandardCharsets.US_ASCII).trim()
        val country = (normalized[0x3E].toInt() and 0xFF).toChar()
        val version = normalized[0x3F].toInt() and 0xFF

        val crc1 = readUint32BE(normalized, 0x10)
        val crc2 = readUint32BE(normalized, 0x14)

        val byteOrderName = when {
            isZ64 -> "Z64 (Big-Endian)"
            isV64 -> "V64 (Byte-Swapped)"
            else -> "N64 (Little-Endian)"
        }

        return N64RomHeader(
            title = if (title.isBlank()) "N64 Game" else title,
            gameCode = gameCode,
            countryCode = country,
            version = version,
            byteOrder = byteOrderName,
            crc1 = crc1,
            crc2 = crc2,
            magicValid = true
        )
    }

    fun isN64Rom(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return matchesMagic(bytes, 0, MAGIC_Z64) ||
            matchesMagic(bytes, 0, MAGIC_V64) ||
            matchesMagic(bytes, 0, MAGIC_N64)
    }

    private fun matchesMagic(bytes: ByteArray, offset: Int, magic: ByteArray): Boolean {
        if (bytes.size < offset + magic.size) return false
        for (i in magic.indices) {
            if (bytes[offset + i] != magic[i]) return false
        }
        return true
    }

    private fun readUint32BE(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }
}
