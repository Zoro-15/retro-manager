package com.retropack.domain.rom

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Parsed Game Boy / Game Boy Color ROM cartridge header data.
 */
data class GbRomHeader(
    val title: String,
    val platform: String, // "gb" or "gbc"
    val cgbFlag: Int,
    val sgbFlag: Int,
    val cartridgeType: Int,
    val mbcType: String,
    val hasBattery: Boolean,
    val romSizeBytes: Long,
    val ramSizeBytes: Long,
    val destinationCode: Int,
    val maskRomVersion: Int,
    val logoValid: Boolean,
    val headerChecksumValid: Boolean,
    val storedChecksum: Int,
    val calculatedChecksum: Int,
    val newLicenseeCode: String? = null
)

/**
 * Parser for Game Boy (`.gb`) and Game Boy Color (`.gbc`) ROM headers.
 * Extracts metadata, validates the 48-byte Nintendo logo, cartridge/MBC type,
 * CGB compatibility flags, and verifies the 8-bit header checksum.
 */
object GbRomParser {
    const val MIN_HEADER_SIZE: Int = 0x0150 // 336 bytes
    const val MAX_ROM_SIZE_BYTES: Int = 64 * 1024 * 1024 // 64 MiB safety ceiling

    /**
     * Official 48-byte Nintendo scrolling logo bitmap stored at 0x0104 - 0x0133.
     * The GB boot ROM checks this exact sequence before booting the cartridge.
     */
    val NINTENDO_LOGO: ByteArray = byteArrayOf(
        0xCE.toByte(), 0xED.toByte(), 0x66.toByte(), 0x66.toByte(),
        0xCC.toByte(), 0x0D.toByte(), 0x00.toByte(), 0x0B.toByte(),
        0x03.toByte(), 0x73.toByte(), 0x00.toByte(), 0x83.toByte(),
        0x00.toByte(), 0x0C.toByte(), 0x00.toByte(), 0x0D.toByte(),
        0x00.toByte(), 0x08.toByte(), 0x11.toByte(), 0x1F.toByte(),
        0x88.toByte(), 0x89.toByte(), 0x00.toByte(), 0x0E.toByte(),
        0xDC.toByte(), 0xCC.toByte(), 0x6E.toByte(), 0xE6.toByte(),
        0xDD.toByte(), 0xDD.toByte(), 0xD9.toByte(), 0x99.toByte(),
        0xBB.toByte(), 0xBB.toByte(), 0x67.toByte(), 0x63.toByte(),
        0x6E.toByte(), 0x0E.toByte(), 0xEC.toByte(), 0xCC.toByte(),
        0xDD.toByte(), 0xDC.toByte(), 0x99.toByte(), 0x9F.toByte(),
        0xBB.toByte(), 0xB9.toByte(), 0x33.toByte(), 0x3E.toByte()
    )

    fun parse(input: InputStream): GbRomHeader = parse(input.readBytesLimited(MAX_ROM_SIZE_BYTES))

    fun parse(bytes: ByteArray): GbRomHeader {
        if (bytes.size < MIN_HEADER_SIZE) {
            throw InvalidRomException("The file is too small (${bytes.size} bytes) to contain a GB/GBC header.")
        }
        if (bytes.size > MAX_ROM_SIZE_BYTES) {
            throw InvalidRomException("The file exceeds the 64 MiB safety ceiling.")
        }

        // Validate 48-byte Nintendo logo at 0x0104 - 0x0133
        val logoValid = validateLogo(bytes)

        // CGB Flag at 0x0143:
        // 0x80 = Game supports CGB functions, but works on monochrome Game Boy
        // 0xC0 = Game works on CGB only
        val cgbFlag = bytes[0x0143].toInt() and 0xFF
        val isCgb = (cgbFlag and 0x80) != 0

        // Title extraction:
        // In older GB games, title is 16 bytes (0x0134 - 0x0143).
        // In CGB games, 0x0143 is CGB flag, and 0x013F - 0x0142 can be manufacturer code,
        // leaving 11-15 bytes for title.
        val titleLength = if (isCgb) 15 else 16
        val title = bytes.ascii(0x0134, titleLength).ifBlank { "Untitled GB Game" }

        // New Licensee Code at 0x0144 - 0x0145
        val oldLicensee = bytes[0x014B].toInt() and 0xFF
        val newLicenseeCode = if (oldLicensee == 0x33) bytes.ascii(0x0144, 2) else null

        // SGB Flag at 0x0146: 0x03 = SGB functions supported, 0x00 = No SGB
        val sgbFlag = bytes[0x0146].toInt() and 0xFF

        // Cartridge / Memory Bank Controller (MBC) type at 0x0147
        val cartridgeType = bytes[0x0147].toInt() and 0xFF
        val mbcType = resolveMbcType(cartridgeType)
        val hasBattery = isCartridgeBatteryBacked(cartridgeType)

        // ROM and RAM sizing
        val romSizeCode = bytes[0x0148].toInt() and 0xFF
        val romSizeBytes = resolveRomSize(romSizeCode)

        val ramSizeCode = bytes[0x0149].toInt() and 0xFF
        val ramSizeBytes = resolveRamSize(ramSizeCode)

        val destinationCode = bytes[0x014A].toInt() and 0xFF
        val maskRomVersion = bytes[0x014C].toInt() and 0xFF

        // Header checksum at 0x014D
        val storedChecksum = bytes[0x014D].toInt() and 0xFF
        val calculatedChecksum = calculateHeaderChecksum(bytes)
        val headerChecksumValid = storedChecksum == calculatedChecksum

        return GbRomHeader(
            title = title,
            platform = if (isCgb) "gbc" else "gb",
            cgbFlag = cgbFlag,
            sgbFlag = sgbFlag,
            cartridgeType = cartridgeType,
            mbcType = mbcType,
            hasBattery = hasBattery,
            romSizeBytes = romSizeBytes,
            ramSizeBytes = ramSizeBytes,
            destinationCode = destinationCode,
            maskRomVersion = maskRomVersion,
            logoValid = logoValid,
            headerChecksumValid = headerChecksumValid,
            storedChecksum = storedChecksum,
            calculatedChecksum = calculatedChecksum,
            newLicenseeCode = newLicenseeCode
        )
    }

    /**
     * Calculates the 8-bit GB/GBC header checksum over bytes 0x0134..0x014C:
     * sum = sum - byte[i] - 1 (mod 256).
     */
    fun calculateHeaderChecksum(bytes: ByteArray): Int {
        if (bytes.size < MIN_HEADER_SIZE) {
            throw InvalidRomException("The file is too small to calculate a GB header checksum.")
        }
        var checksum = 0
        for (i in 0x0134..0x014C) {
            checksum = (checksum - (bytes[i].toInt() and 0xFF) - 1) and 0xFF
        }
        return checksum
    }

    /**
     * Validates the 48-byte Nintendo logo at 0x0104 - 0x0133.
     */
    fun validateLogo(bytes: ByteArray): Boolean {
        if (bytes.size < 0x0134) return false
        for (i in NINTENDO_LOGO.indices) {
            if (bytes[0x0104 + i] != NINTENDO_LOGO[i]) {
                return false
            }
        }
        return true
    }

    fun isCartridgeBatteryBacked(cartridgeType: Int): Boolean = when (cartridgeType) {
        0x03, // MBC1+RAM+BATTERY
        0x06, // MBC2+BATTERY
        0x09, // ROM+RAM+BATTERY
        0x0D, // MMM01+RAM+BATTERY
        0x0F, // MBC3+TIMER+BATTERY
        0x10, // MBC3+TIMER+RAM+BATTERY
        0x13, // MBC3+RAM+BATTERY
        0x1B, // MBC5+RAM+BATTERY
        0x1E, // MBC5+RUMBLE+RAM+BATTERY
        0x22, // MBC7+SENSOR+RUMBLE+RAM+BATTERY
        0xFF  // HuC1+RAM+BATTERY
        -> true
        else -> false
    }

    fun resolveMbcType(cartridgeType: Int): String = when (cartridgeType) {
        0x00 -> "ROM ONLY"
        0x01 -> "MBC1"
        0x02 -> "MBC1+RAM"
        0x03 -> "MBC1+RAM+BATTERY"
        0x05 -> "MBC2"
        0x06 -> "MBC2+BATTERY"
        0x08 -> "ROM+RAM"
        0x09 -> "ROM+RAM+BATTERY"
        0x0B -> "MMM01"
        0x0C -> "MMM01+RAM"
        0x0D -> "MMM01+RAM+BATTERY"
        0x0F -> "MBC3+TIMER+BATTERY"
        0x10 -> "MBC3+TIMER+RAM+BATTERY"
        0x11 -> "MBC3"
        0x12 -> "MBC3+RAM"
        0x13 -> "MBC3+RAM+BATTERY"
        0x19 -> "MBC5"
        0x1A -> "MBC5+RAM"
        0x1B -> "MBC5+RAM+BATTERY"
        0x1C -> "MBC5+RUMBLE"
        0x1D -> "MBC5+RUMBLE+RAM"
        0x1E -> "MBC5+RUMBLE+RAM+BATTERY"
        0x20 -> "MBC6"
        0x22 -> "MBC7+SENSOR+RUMBLE+RAM+BATTERY"
        0xFC -> "POCKET CAMERA"
        0xFD -> "BANDAI TAMA5"
        0xFE -> "HuC3"
        0xFF -> "HuC1+RAM+BATTERY"
        else -> "UNKNOWN (0x%02X)".format(cartridgeType)
    }

    fun resolveRomSize(code: Int): Long = when (code) {
        in 0..8 -> (32L * 1024L) shl code
        0x52 -> 72L * 16L * 1024L // 1.1 MiB
        0x53 -> 80L * 16L * 1024L // 1.2 MiB
        0x54 -> 96L * 16L * 1024L // 1.5 MiB
        else -> 0L
    }

    fun resolveRamSize(code: Int): Long = when (code) {
        0x00 -> 0L
        0x01 -> 2L * 1024L // 2 KiB
        0x02 -> 8L * 1024L // 8 KiB (1 bank)
        0x03 -> 32L * 1024L // 32 KiB (4 banks of 8 KiB)
        0x04 -> 128L * 1024L // 128 KiB (16 banks of 8 KiB)
        0x05 -> 64L * 1024L // 64 KiB (8 banks of 8 KiB)
        else -> 0L
    }

    private fun ByteArray.ascii(offset: Int, length: Int): String {
        return copyOfRange(offset, offset + length)
            .takeWhile { it.toInt() != 0 }
            .map { byte ->
                val value = byte.toInt() and 0xFF
                if (value in 32..126) value.toChar() else ' '
            }
            .joinToString("")
            .trim()
    }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw InvalidRomException("The file exceeds the 64 MiB safety ceiling.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
