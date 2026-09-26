package com.retropack.domain.rom

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NesRomParserTest {

    private fun createNesRom(
        prg16kUnits: Int = 2, // 32 KB PRG
        chr8kUnits: Int = 1,  // 8 KB CHR
        mapper: Int = 1,       // MMC1
        hasBattery: Boolean = true
    ): ByteArray {
        val bytes = ByteArray(16 + prg16kUnits * 16384 + chr8kUnits * 8192)

        // Magic "NES\x1a"
        bytes[0] = 0x4E
        bytes[1] = 0x45
        bytes[2] = 0x53
        bytes[3] = 0x1A

        bytes[4] = prg16kUnits.toByte()
        bytes[5] = chr8kUnits.toByte()

        // Flags 6: Lower mapper nibble + battery
        val flags6 = ((mapper and 0x0F) shl 4) or (if (hasBattery) 0x02 else 0x00)
        bytes[6] = flags6.toByte()

        // Flags 7: Upper mapper nibble
        val flags7 = mapper and 0xF0
        bytes[7] = flags7.toByte()

        return bytes
    }

    @Test
    fun `test iNES header parsing with MMC1 mapper and battery`() {
        val rom = createNesRom(prg16kUnits = 8, chr8kUnits = 4, mapper = 1, hasBattery = true)
        val header = NesRomParser.parse(rom, fallbackTitle = "THE LEGEND OF ZELDA")

        assertEquals("THE LEGEND OF ZELDA", header.title)
        assertEquals(1, header.mapperNumber)
        assertEquals(8 * 16384L, header.prgRomSizeBytes)
        assertEquals(4 * 8192L, header.chrRomSizeBytes)
        assertTrue(header.hasBattery)
        assertTrue(header.magicValid)
    }
}
