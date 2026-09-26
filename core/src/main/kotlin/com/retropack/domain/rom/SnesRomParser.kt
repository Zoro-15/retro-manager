package com.retropack.domain.rom

import java.nio.charset.StandardCharsets

/**
 * Parsed Super Nintendo Entertainment System (SNES / Super Famicom) cartridge header.
 */
data class SnesRomHeader(
    val title: String,
    val mapMode: Int,
    val isHiRom: Boolean,
    val isFastRom: Boolean,
    val cartridgeType: Int,
    val romSizeCode: Int,
    val romSizeBytes: Long,
    val ramSizeCode: Int,
    val ramSizeBytes: Long,
    val countryCode: Int,
    val makerCode: Int,
    val version: Int,
    val checksum: Int,
    val checksumComplement: Int,
    val checksumValid: Boolean,
    val hasBattery: Boolean,
    val copiersHeaderPresent: Boolean,
    val headerOffset: Int
)

/**
 * Low-level binary parser for SNES ROM images (.sfc, .smc, .fig).
 *
 * Handles 512-byte copier headers and verifies LoROM (0x7FC0) vs HiROM (0xFFC0) internal headers.
 */
object SnesRomParser {

    const val COPIER_HEADER_SIZE = 512
    const val LOROM_OFFSET = 0x7FC0
    const val HIROM_OFFSET = 0xFFC0
    const val MIN_HEADER_SIZE = 0x10000 // 64 KB to test both LoROM and HiROM offsets

    /**
     * Inspects a byte buffer and extracts the SNES ROM header.
     */
    fun parse(bytes: ByteArray): SnesRomHeader {
        val hasCopierHeader = (bytes.size % 1024 == 512)
        val baseOffset = if (hasCopierHeader) COPIER_HEADER_SIZE else 0

        // Score LoROM and HiROM candidate offsets
        val loOffset = baseOffset + LOROM_OFFSET
        val hiOffset = baseOffset + HIROM_OFFSET

        val loHeader = if (bytes.size >= loOffset + 32) parseAtOffset(bytes, loOffset, hasCopierHeader, isHiRom = false) else null
        val hiHeader = if (bytes.size >= hiOffset + 32) parseAtOffset(bytes, hiOffset, hasCopierHeader, isHiRom = true) else null

        return when {
            loHeader != null && loHeader.checksumValid && (hiHeader == null || !hiHeader.checksumValid) -> loHeader
            hiHeader != null && hiHeader.checksumValid && (loHeader == null || !loHeader.checksumValid) -> hiHeader
            hiHeader != null && (loHeader == null || isLikelyHiRom(bytes, baseOffset)) -> hiHeader
            loHeader != null -> loHeader
            else -> throw InvalidRomException("SNES ROM header could not be verified at LoROM or HiROM offsets.")
        }
    }

    /**
     * Probes if bytes look like a valid SNES ROM header.
     */
    fun isSnesRom(bytes: ByteArray): Boolean {
        if (bytes.size < 0x8000) return false
        val hasCopierHeader = (bytes.size % 1024 == 512)
        val baseOffset = if (hasCopierHeader) COPIER_HEADER_SIZE else 0

        val loOffset = baseOffset + LOROM_OFFSET
        if (bytes.size >= loOffset + 32) {
            val c = (bytes[loOffset + 0x1E].toInt() and 0xFF) or ((bytes[loOffset + 0x1F].toInt() and 0xFF) shl 8)
            val comp = (bytes[loOffset + 0x1C].toInt() and 0xFF) or ((bytes[loOffset + 0x1D].toInt() and 0xFF) shl 8)
            if (c + comp == 0xFFFF && c != 0) return true
        }

        val hiOffset = baseOffset + HIROM_OFFSET
        if (bytes.size >= hiOffset + 32) {
            val c = (bytes[hiOffset + 0x1E].toInt() and 0xFF) or ((bytes[hiOffset + 0x1F].toInt() and 0xFF) shl 8)
            val comp = (bytes[hiOffset + 0x1C].toInt() and 0xFF) or ((bytes[hiOffset + 0x1D].toInt() and 0xFF) shl 8)
            if (c + comp == 0xFFFF && c != 0) return true
        }

        return false
    }

    private fun isLikelyHiRom(bytes: ByteArray, baseOffset: Int): Boolean {
        val loMap = if (bytes.size >= baseOffset + LOROM_OFFSET + 0x15) bytes[baseOffset + LOROM_OFFSET + 0x15].toInt() and 0xFF else 0
        val hiMap = if (bytes.size >= baseOffset + HIROM_OFFSET + 0x15) bytes[baseOffset + HIROM_OFFSET + 0x15].toInt() and 0xFF else 0
        return (hiMap and 0x01) != 0 || (loMap and 0x01) != 0
    }

    private fun parseAtOffset(bytes: ByteArray, offset: Int, hasCopier: Boolean, isHiRom: Boolean): SnesRomHeader {
        // Read 21-byte title
        val titleRaw = String(bytes, offset, 21, StandardCharsets.US_ASCII)
        val title = titleRaw.map { if (it in ' '..'~') it else ' ' }.joinToString("").trim()

        val mapMode = bytes[offset + 0x15].toInt() and 0xFF
        val isFast = (mapMode and 0x10) != 0
        val cartType = bytes[offset + 0x16].toInt() and 0xFF
        val romCode = bytes[offset + 0x17].toInt() and 0xFF
        val ramCode = bytes[offset + 0x18].toInt() and 0xFF
        val country = bytes[offset + 0x19].toInt() and 0xFF
        val maker = bytes[offset + 0x1A].toInt() and 0xFF
        val version = bytes[offset + 0x1B].toInt() and 0xFF

        val comp = (bytes[offset + 0x1C].toInt() and 0xFF) or ((bytes[offset + 0x1D].toInt() and 0xFF) shl 8)
        val checksum = (bytes[offset + 0x1E].toInt() and 0xFF) or ((bytes[offset + 0x1F].toInt() and 0xFF) shl 8)
        val checksumValid = (comp + checksum) == 0xFFFF && checksum != 0

        // Cartridge types with battery: 0x02, 0x05, 0x06, 0x15, 0x16, 0x25, 0x35, 0x45, 0x55, 0xE5, 0xF5
        val hasBattery = cartType in setOf(0x02, 0x05, 0x06, 0x15, 0x16, 0x25, 0x35, 0x45, 0x55, 0xE5, 0xF5)

        val romSizeBytes = if (romCode in 0x08..0x10) (1L shl (romCode + 10)) else 0L
        val ramSizeBytes = if (ramCode in 0x01..0x08) (1L shl (ramCode + 10)) else 0L

        return SnesRomHeader(
            title = if (title.isBlank()) "SNES Game" else title,
            mapMode = mapMode,
            isHiRom = isHiRom,
            isFastRom = isFast,
            cartridgeType = cartType,
            romSizeCode = romCode,
            romSizeBytes = romSizeBytes,
            ramSizeCode = ramCode,
            ramSizeBytes = ramSizeBytes,
            countryCode = country,
            makerCode = maker,
            version = version,
            checksum = checksum,
            checksumComplement = comp,
            checksumValid = checksumValid,
            hasBattery = hasBattery,
            copiersHeaderPresent = hasCopier,
            headerOffset = offset
        )
    }
}
