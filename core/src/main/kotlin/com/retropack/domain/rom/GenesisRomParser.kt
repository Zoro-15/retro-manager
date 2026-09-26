package com.retropack.domain.rom

import java.nio.charset.StandardCharsets

/**
 * Parsed Sega Mega Drive / Genesis / Master System / Game Gear cartridge header.
 */
data class GenesisRomHeader(
    val platform: String, // "genesis", "sms", "gg"
    val systemName: String,
    val domesticTitle: String,
    val overseasTitle: String,
    val productNumber: String,
    val checksum: Int,
    val romStart: Long,
    val romEnd: Long,
    val hasSram: Boolean,
    val sramStart: Long,
    val sramEnd: Long,
    val region: String,
    val magicValid: Boolean
)

/**
 * Low-level binary parser for Sega Genesis / Mega Drive (.md, .gen, .smd, .bin)
 * and Sega Master System / Game Gear (.sms, .gg) ROM images.
 */
object GenesisRomParser {

    const val MIN_HEADER_SIZE = 0x200 // 512 bytes covers Mega Drive 0x100..0x200 header
    private val SEGA_MAGICS = listOf("SEGA MEGA DRIVE", "SEGA GENESIS", "SEGA 32X", "SEGA SS", "SEGA PICO", "SEGA")
    private val SMS_MAGIC = "TMR SEGA".toByteArray(StandardCharsets.US_ASCII)

    /**
     * Inspects a byte buffer and extracts the Sega ROM header.
     */
    fun parse(bytes: ByteArray): GenesisRomHeader {
        // 1. Check Sega Mega Drive / Genesis header at 0x100
        if (bytes.size >= 0x200) {
            val systemNameRaw = String(bytes, 0x100, 16, StandardCharsets.US_ASCII).trim()
            val hasMegaDriveMagic = SEGA_MAGICS.any { systemNameRaw.startsWith(it) }

            if (hasMegaDriveMagic) {
                val domesticTitle = cleanString(String(bytes, 0x120, 48, StandardCharsets.US_ASCII))
                val overseasTitle = cleanString(String(bytes, 0x150, 48, StandardCharsets.US_ASCII))
                val productNumber = cleanString(String(bytes, 0x180, 14, StandardCharsets.US_ASCII))

                val checksum = ((bytes[0x18E].toInt() and 0xFF) shl 8) or (bytes[0x18F].toInt() and 0xFF)
                val romStart = readUint32BE(bytes, 0x1A0)
                val romEnd = readUint32BE(bytes, 0x1A4)

                // SRAM indicator "RA" at 0x1B0
                val hasSram = bytes[0x1B0] == 'R'.code.toByte() && bytes[0x1B1] == 'A'.code.toByte()
                val sramStart = if (hasSram) readUint32BE(bytes, 0x1B4) else 0L
                val sramEnd = if (hasSram) readUint32BE(bytes, 0x1B8) else 0L

                val region = cleanString(String(bytes, 0x1F0, 16, StandardCharsets.US_ASCII))

                val finalTitle = when {
                    overseasTitle.isNotBlank() -> overseasTitle
                    domesticTitle.isNotBlank() -> domesticTitle
                    else -> "Sega Genesis Game"
                }

                return GenesisRomHeader(
                    platform = "genesis",
                    systemName = systemNameRaw,
                    domesticTitle = domesticTitle,
                    overseasTitle = finalTitle,
                    productNumber = productNumber,
                    checksum = checksum,
                    romStart = romStart,
                    romEnd = romEnd,
                    hasSram = hasSram,
                    sramStart = sramStart,
                    sramEnd = sramEnd,
                    region = region,
                    magicValid = true
                )
            }
        }

        // 2. Check Sega Master System / Game Gear "TMR SEGA" header at 0x1FF0, 0x3FF0, or 0x7FF0
        for (offset in listOf(0x7FF0, 0x3FF0, 0x1FF0)) {
            if (bytes.size >= offset + 16 && matchesMagic(bytes, offset, SMS_MAGIC)) {
                val checksum = (bytes[offset + 10].toInt() and 0xFF) or ((bytes[offset + 11].toInt() and 0xFF) shl 8)
                val regionNibble = (bytes[offset + 15].toInt() and 0xF0) ushr 4
                val isGameGear = regionNibble in 5..7

                return GenesisRomHeader(
                    platform = if (isGameGear) "gg" else "sms",
                    systemName = if (isGameGear) "SEGA GAME GEAR" else "SEGA MASTER SYSTEM",
                    domesticTitle = if (isGameGear) "Game Gear Title" else "Master System Title",
                    overseasTitle = if (isGameGear) "Game Gear Title" else "Master System Title",
                    productNumber = "",
                    checksum = checksum,
                    romStart = 0L,
                    romEnd = bytes.size.toLong(),
                    hasSram = false,
                    sramStart = 0L,
                    sramEnd = 0L,
                    region = if (isGameGear) "GameGear" else "SMS",
                    magicValid = true
                )
            }
        }

        throw InvalidRomException("No valid Sega Mega Drive/Genesis or SMS/GG header found in ROM bytes.")
    }

    /**
     * Returns true if bytes contain a recognized Sega console header.
     */
    fun isGenesisRom(bytes: ByteArray): Boolean {
        if (bytes.size >= 0x200) {
            val systemNameRaw = String(bytes, 0x100, 16, StandardCharsets.US_ASCII).trim()
            if (SEGA_MAGICS.any { systemNameRaw.startsWith(it) }) return true
        }
        for (offset in listOf(0x7FF0, 0x3FF0, 0x1FF0)) {
            if (bytes.size >= offset + 16 && matchesMagic(bytes, offset, SMS_MAGIC)) return true
        }
        return false
    }

    private fun matchesMagic(bytes: ByteArray, offset: Int, magic: ByteArray): Boolean {
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

    private fun cleanString(raw: String): String {
        return raw.map { if (it in ' '..'~') it else ' ' }.joinToString("").trim()
    }
}
