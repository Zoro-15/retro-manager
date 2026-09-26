package com.retropack.domain.rom

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class SnesRomParserTest {

    private fun createSnesRom(
        title: String = "SUPER MARIO WORLD",
        isHiRom: Boolean = false,
        hasCopierHeader: Boolean = false,
        cartridgeType: Int = 0x02 // ROM + RAM + Battery
    ): ByteArray {
        val baseSize = 0x80000 // 512 KB
        val totalSize = if (hasCopierHeader) baseSize + 512 else baseSize
        val bytes = ByteArray(totalSize)

        val headerOffset = (if (hasCopierHeader) 512 else 0) + (if (isHiRom) 0xFFC0 else 0x7FC0)

        // Title 21 bytes
        val titleBytes = title.padEnd(21, ' ').toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(titleBytes, 0, bytes, headerOffset, 21)

        // Map mode (0x20 LoROM, 0x21 HiROM)
        bytes[headerOffset + 0x15] = (if (isHiRom) 0x21 else 0x20).toByte()
        // Cart type
        bytes[headerOffset + 0x16] = cartridgeType.toByte()
        // ROM size (0x09 = 4Mbit / 512KB)
        bytes[headerOffset + 0x17] = 0x09
        // RAM size (0x01 = 2KB)
        bytes[headerOffset + 0x18] = 0x01
        // Country (0x01 = USA)
        bytes[headerOffset + 0x19] = 0x01
        // Maker
        bytes[headerOffset + 0x1A] = 0x01
        // Version
        bytes[headerOffset + 0x1B] = 0x00

        // Checksum & Complement
        val checksum = 0x1234
        val complement = 0xEDCB // 0xFFFF - 0x1234
        bytes[headerOffset + 0x1C] = (complement and 0xFF).toByte()
        bytes[headerOffset + 0x1D] = ((complement ushr 8) and 0xFF).toByte()
        bytes[headerOffset + 0x1E] = (checksum and 0xFF).toByte()
        bytes[headerOffset + 0x1F] = ((checksum ushr 8) and 0xFF).toByte()

        return bytes
    }

    @Test
    fun `test LoROM parsing without copier header`() {
        val rom = createSnesRom(title = "SUPER MARIO WORLD", isHiRom = false, hasCopierHeader = false)
        val header = SnesRomParser.parse(rom)

        assertEquals("SUPER MARIO WORLD", header.title)
        assertFalse(header.isHiRom)
        assertTrue(header.checksumValid)
        assertTrue(header.hasBattery)
        assertFalse(header.copiersHeaderPresent)
    }

    @Test
    fun `test HiROM parsing with 512-byte copier header`() {
        val rom = createSnesRom(title = "CHRONO TRIGGER", isHiRom = true, hasCopierHeader = true)
        val header = SnesRomParser.parse(rom)

        assertEquals("CHRONO TRIGGER", header.title)
        assertTrue(header.isHiRom)
        assertTrue(header.checksumValid)
        assertTrue(header.hasBattery)
        assertTrue(header.copiersHeaderPresent)
    }
}
