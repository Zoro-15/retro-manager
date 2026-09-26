package com.retropack.domain.rom

/**
 * Parsed NES / Famicom cartridge header (iNES / NES 2.0).
 */
data class NesRomHeader(
    val title: String,
    val mapperNumber: Int,
    val submapperNumber: Int,
    val prgRomSizeBytes: Long,
    val chrRomSizeBytes: Long,
    val prgRamSizeBytes: Long,
    val isNes20: Boolean,
    val isPal: Boolean,
    val hasBattery: Boolean,
    val hasTrainer: Boolean,
    val mirroring: String,
    val magicValid: Boolean
)

/**
 * Low-level binary parser for NES / Famicom (.nes, .fds) ROM images.
 */
object NesRomParser {

    const val MIN_HEADER_SIZE = 16
    private val INES_MAGIC = byteArrayOf(0x4E, 0x45, 0x53, 0x1A) // "NES\x1a"
    private val FDS_MAGIC = byteArrayOf(0x46, 0x44, 0x53, 0x1A) // "FDS\x1a"

    /**
     * Inspects a byte buffer and extracts the NES ROM header.
     */
    fun parse(bytes: ByteArray, fallbackTitle: String = "NES Game"): NesRomHeader {
        if (bytes.size < MIN_HEADER_SIZE) {
            throw InvalidRomException("NES ROM too small to contain valid iNES header ($bytes.size bytes).")
        }

        if (matchesMagic(bytes, 0, INES_MAGIC)) {
            val prgUnits = bytes[4].toInt() and 0xFF
            val chrUnits = bytes[5].toInt() and 0xFF
            val flags6 = bytes[6].toInt() and 0xFF
            val flags7 = bytes[7].toInt() and 0xFF
            val flags8 = bytes[8].toInt() and 0xFF
            val flags9 = bytes[9].toInt() and 0xFF

            val isNes20 = (flags7 and 0x0C) == 0x08
            val isPal = (flags9 and 0x01) != 0

            val lowerMapper = (flags6 ushr 4) and 0x0F
            val upperMapper = flags7 and 0xF0
            var mapperNumber = upperMapper or lowerMapper
            var submapperNumber = 0

            var prgRomSize = prgUnits.toLong() * 16384L
            var chrRomSize = chrUnits.toLong() * 8192L

            if (isNes20) {
                val extendedMapper = (flags8 and 0x0F) shl 8
                mapperNumber = mapperNumber or extendedMapper
                submapperNumber = (flags8 ushr 4) and 0x0F

                val prgMsb = (bytes[9].toInt() and 0x0F)
                val chrMsb = (bytes[9].toInt() ushr 4) and 0x0F
                if (prgMsb == 0x0F) {
                    val exp = (prgUnits ushr 2) and 0x3F
                    val mul = (prgUnits and 0x03) * 2 + 1
                    prgRomSize = (1L shl exp) * mul
                } else {
                    prgRomSize = ((prgMsb.toLong() shl 8) or prgUnits.toLong()) * 16384L
                }

                if (chrMsb == 0x0F) {
                    val exp = (chrUnits ushr 2) and 0x3F
                    val mul = (chrUnits and 0x03) * 2 + 1
                    chrRomSize = (1L shl exp) * mul
                } else {
                    chrRomSize = ((chrMsb.toLong() shl 8) or chrUnits.toLong()) * 8192L
                }
            }

            val hasBattery = (flags6 and 0x02) != 0
            val hasTrainer = (flags6 and 0x04) != 0
            val isFourScreen = (flags6 and 0x08) != 0
            val isVerticalMirroring = (flags6 and 0x01) != 0

            val mirroring = when {
                isFourScreen -> "Four-Screen"
                isVerticalMirroring -> "Vertical"
                else -> "Horizontal"
            }

            return NesRomHeader(
                title = fallbackTitle,
                mapperNumber = mapperNumber,
                submapperNumber = submapperNumber,
                prgRomSizeBytes = prgRomSize,
                chrRomSizeBytes = chrRomSize,
                prgRamSizeBytes = if (hasBattery) 8192L else 0L,
                isNes20 = isNes20,
                isPal = isPal,
                hasBattery = hasBattery,
                hasTrainer = hasTrainer,
                mirroring = mirroring,
                magicValid = true
            )
        }

        if (matchesMagic(bytes, 0, FDS_MAGIC)) {
            return NesRomHeader(
                title = fallbackTitle,
                mapperNumber = 20, // FDS mapper
                submapperNumber = 0,
                prgRomSizeBytes = (bytes.size - 16).toLong(),
                chrRomSizeBytes = 0L,
                prgRamSizeBytes = 32768L,
                isNes20 = false,
                isPal = false,
                hasBattery = true,
                hasTrainer = false,
                mirroring = "FDS",
                magicValid = true
            )
        }

        throw InvalidRomException("Invalid NES ROM header: magic bytes do not match 'NES\\x1a' or 'FDS\\x1a'.")
    }

    /**
     * Returns true if bytes start with iNES or FDS magic.
     */
    fun isNesRom(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_HEADER_SIZE) return false
        return matchesMagic(bytes, 0, INES_MAGIC) || matchesMagic(bytes, 0, FDS_MAGIC)
    }

    private fun matchesMagic(bytes: ByteArray, offset: Int, magic: ByteArray): Boolean {
        if (bytes.size < offset + magic.size) return false
        for (i in magic.indices) {
            if (bytes[offset + i] != magic[i]) return false
        }
        return true
    }
}
