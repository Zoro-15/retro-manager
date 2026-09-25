package com.retropack.domain.rom

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GbaRomParserTest {

    @Test
    fun `test valid GBA header parsing with known game code and maker code`() {
        val rom = GbaTestRomFactory.create(
            title = "POKEMON EMER",
            gameCode = "BPEE",
            makerCode = "01",
            version = 0
        )

        val header = GbaRomParser.parse(rom)
        assertEquals("POKEMON EMER", header.title)
        assertEquals("BPEE", header.gameCode)
        assertEquals("01", header.makerCode)
        assertEquals(0, header.softwareVersion)
        assertTrue(header.fixedValueValid)
        assertTrue(header.headerChecksumValid)
        assertEquals(header.storedChecksum, header.calculatedChecksum)
    }

    @Test
    fun `test rejection of invalid fixed-value byte`() {
        val rom = GbaTestRomFactory.create()
        rom[0xB2] = 0x00 // Corrupt fixed value 0x96

        assertThrows<InvalidRomException> {
            GbaRomParser.parse(rom)
        }
    }

    @Test
    fun `test header checksum recalculation and mismatch detection`() {
        val rom = GbaTestRomFactory.create(title = "METROID ZERO")
        val header = GbaRomParser.parse(rom)
        assertTrue(header.headerChecksumValid)

        // Modify title in ROM without updating checksum
        rom[0xA0] = 'C'.code.toByte()
        val modifiedHeader = GbaRomParser.parse(rom)
        assertFalse(modifiedHeader.headerChecksumValid)
        assertNotEquals(modifiedHeader.storedChecksum, modifiedHeader.calculatedChecksum)
    }

    @Test
    fun `test file too small throws InvalidRomException`() {
        val truncated = ByteArray(0x80) // 128 bytes, less than MIN_HEADER_SIZE 192 bytes
        assertThrows<InvalidRomException> {
            GbaRomParser.parse(truncated)
        }
    }
}

internal object GbaTestRomFactory {
    fun create(
        title: String = "POKEMON EMER",
        gameCode: String = "BPEE",
        makerCode: String = "01",
        version: Int = 0
    ): ByteArray {
        val bytes = ByteArray(0x0100) // 256 bytes

        // Title at 0xA0 - 0xAB (12 bytes)
        val titleBytes = title.toByteArray(Charsets.US_ASCII)
        titleBytes.copyInto(bytes, 0xA0, endIndex = titleBytes.size.coerceAtMost(12))

        // Game Code at 0xAC - 0xAF (4 bytes)
        val codeBytes = gameCode.toByteArray(Charsets.US_ASCII)
        codeBytes.copyInto(bytes, 0xAC, endIndex = codeBytes.size.coerceAtMost(4))

        // Maker Code at 0xB0 - 0xB1 (2 bytes)
        val makerBytes = makerCode.toByteArray(Charsets.US_ASCII)
        makerBytes.copyInto(bytes, 0xB0, endIndex = makerBytes.size.coerceAtMost(2))

        // Fixed value byte: 0x96 at 0xB2
        bytes[0xB2] = 0x96.toByte()

        // Main unit code at 0xB3: 0x00
        bytes[0xB3] = 0x00

        // Software version at 0xBC
        bytes[0xBC] = version.toByte()

        // Stored header checksum at 0xBD: -(sum(0xA0..0xBC) + 0x19) mod 256
        bytes[0xBD] = GbaRomParser.calculateHeaderChecksum(bytes).toByte()

        return bytes
    }
}
