package com.retropack.domain.rom

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GbRomParserTest {

    @Test
    fun `test valid Game Boy monochrome header parsing`() {
        val rom = GbTestRomFactory.create(
            title = "TETRIS",
            isCgb = false,
            cartridgeType = 0x01, // MBC1
            romSizeCode = 0x00,   // 32 KB
            ramSizeCode = 0x00    // None
        )

        val header = GbRomParser.parse(rom)
        assertEquals("TETRIS", header.title)
        assertEquals("gb", header.platform)
        assertEquals("MBC1", header.mbcType)
        assertFalse(header.hasBattery)
        assertEquals(32 * 1024L, header.romSizeBytes)
        assertEquals(0L, header.ramSizeBytes)
        assertTrue(header.logoValid)
        assertTrue(header.headerChecksumValid)
        assertEquals(header.storedChecksum, header.calculatedChecksum)
    }

    @Test
    fun `test valid Game Boy Color header parsing with battery SRAM`() {
        val rom = GbTestRomFactory.create(
            title = "POKEMON CRYS",
            isCgb = true,
            cgbFlag = 0xC0,        // CGB only
            cartridgeType = 0x10,  // MBC3+TIMER+RAM+BATTERY
            romSizeCode = 0x05,    // 1 MiB
            ramSizeCode = 0x03     // 32 KiB
        )

        val header = GbRomParser.parse(rom)
        assertEquals("POKEMON CRYS", header.title)
        assertEquals("gbc", header.platform)
        assertEquals("MBC3+TIMER+RAM+BATTERY", header.mbcType)
        assertTrue(header.hasBattery)
        assertEquals(1024 * 1024L, header.romSizeBytes)
        assertEquals(32 * 1024L, header.ramSizeBytes)
        assertTrue(header.logoValid)
        assertTrue(header.headerChecksumValid)
    }

    @Test
    fun `test corrupted Nintendo logo detection`() {
        val rom = GbTestRomFactory.create()
        rom[0x0104] = 0x00 // Alter first byte of logo

        val header = GbRomParser.parse(rom)
        assertFalse(header.logoValid)
        assertTrue(header.headerChecksumValid) // Checksum is over 0x0134..0x014C so remains valid
    }

    @Test
    fun `test corrupted header checksum detection`() {
        val rom = GbTestRomFactory.create()
        rom[0x014D] = (rom[0x014D].toInt() xor 0xFF).toByte() // Invert checksum

        val header = GbRomParser.parse(rom)
        assertFalse(header.headerChecksumValid)
        assertNotEquals(header.storedChecksum, header.calculatedChecksum)
    }

    @Test
    fun `test file too small throws InvalidRomException`() {
        val truncated = ByteArray(100)
        assertThrows<InvalidRomException> {
            GbRomParser.parse(truncated)
        }
    }
}

internal object GbTestRomFactory {
    fun create(
        title: String = "TESTROM",
        isCgb: Boolean = false,
        cgbFlag: Int = if (isCgb) 0x80 else 0x00,
        cartridgeType: Int = 0x00,
        romSizeCode: Int = 0x00,
        ramSizeCode: Int = 0x00
    ): ByteArray {
        val bytes = ByteArray(0x8000) // 32 KB standard cartridge size

        // Entry point 0x0100 - 0x0103: NOP; JP 0x0150
        bytes[0x0100] = 0x00 // NOP
        bytes[0x0101] = 0xC3.toByte() // JP
        bytes[0x0102] = 0x50.toByte()
        bytes[0x0103] = 0x01.toByte()

        // 48-byte Nintendo logo at 0x0104 - 0x0133
        GbRomParser.NINTENDO_LOGO.copyInto(bytes, 0x0104)

        // Title at 0x0134 - 0x0143
        val titleBytes = title.toByteArray(Charsets.US_ASCII)
        val maxTitleLen = if (isCgb) 15 else 16
        titleBytes.copyInto(bytes, 0x0134, endIndex = titleBytes.size.coerceAtMost(maxTitleLen))

        // CGB Flag at 0x0143
        bytes[0x0143] = cgbFlag.toByte()

        // New licensee code at 0x0144 - 0x0145
        bytes[0x0144] = '0'.code.toByte()
        bytes[0x0145] = '1'.code.toByte()

        // SGB Flag at 0x0146
        bytes[0x0146] = 0x00

        // Cartridge type at 0x0147
        bytes[0x0147] = cartridgeType.toByte()

        // ROM size at 0x0148
        bytes[0x0148] = romSizeCode.toByte()

        // RAM size at 0x0149
        bytes[0x0149] = ramSizeCode.toByte()

        // Destination code at 0x014A
        bytes[0x014A] = 0x01 // Non-Japan

        // Old licensee code at 0x014B
        bytes[0x014B] = 0x33 // Use new licensee code

        // Mask ROM version at 0x014C
        bytes[0x014C] = 0x00

        // Header checksum at 0x014D: -(sum(0x0134..0x014C) + 1) mod 256
        bytes[0x014D] = GbRomParser.calculateHeaderChecksum(bytes).toByte()

        return bytes
    }
}
