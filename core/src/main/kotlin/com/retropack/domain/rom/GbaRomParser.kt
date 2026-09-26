package com.retropack.domain.rom


/**
 * Parsed Game Boy Advance ROM header data.
 */
data class GbaRomHeader(
    val title: String,
    val gameCode: String,
    val makerCode: String,
    val softwareVersion: Int,
    val fixedValueValid: Boolean,
    val headerChecksumValid: Boolean,
    val storedChecksum: Int,
    val calculatedChecksum: Int
)

/**
 * Low-level parser for Game Boy Advance (`.gba`) cartridge headers.
 * Reads bytes 0x00A0 - 0x00BD, validates the Nintendo fixed-value byte (0x96),
 * extracts title, gameCode, makerCode, and verifies the 8-bit header checksum.
 * Adapted cleanly from Retra's battle-tested GbaRomParser.
 */
object GbaRomParser {
    const val MIN_HEADER_SIZE: Int = 0xC0 // 192 bytes
    const val MAX_ROM_SIZE_BYTES: Int = 64 * 1024 * 1024 // 64 MiB


    fun parse(bytes: ByteArray): GbaRomHeader {
        if (bytes.size < MIN_HEADER_SIZE) {
            throw InvalidRomException("The file is too small (${bytes.size} bytes) to contain a GBA header.")
        }
        if (bytes.size > MAX_ROM_SIZE_BYTES) {
            throw InvalidRomException("The file exceeds the 64 MiB safety ceiling.")
        }

        val fixedValueValid = (bytes[0xB2].toInt() and 0xFF) == 0x96
        if (!fixedValueValid) {
            throw InvalidRomException("The GBA fixed-value byte (0x96 at 0xB2) is invalid: 0x%02X".format(bytes[0xB2]))
        }

        val title = bytes.ascii(0xA0, 12).ifBlank { "Untitled GBA Game" }
        val gameCode = bytes.ascii(0xAC, 4)
        val makerCode = bytes.ascii(0xB0, 2)
        val version = bytes[0xBC].toInt() and 0xFF
        val storedChecksum = bytes[0xBD].toInt() and 0xFF
        val calculatedChecksum = calculateHeaderChecksum(bytes)

        return GbaRomHeader(
            title = title,
            gameCode = gameCode,
            makerCode = makerCode,
            softwareVersion = version,
            fixedValueValid = fixedValueValid,
            headerChecksumValid = storedChecksum == calculatedChecksum,
            storedChecksum = storedChecksum,
            calculatedChecksum = calculatedChecksum
        )
    }

    /**
     * Calculates the GBA 8-bit header checksum:
     * -(sum(0xA0..0xBC) + 0x19) mod 256
     */
    fun calculateHeaderChecksum(bytes: ByteArray): Int {
        if (bytes.size < MIN_HEADER_SIZE) {
            throw InvalidRomException("The file is too small to calculate a GBA header checksum.")
        }
        var sum = 0
        for (index in 0xA0..0xBC) {
            sum += bytes[index].toInt() and 0xFF
        }
        return (-(sum + 0x19)) and 0xFF
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

}