package com.retropack.domain.rom

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class GenesisRomParserTest {

    private fun createGenesisRom(
        domesticTitle: String = "SONIC THE HEDGEHOG",
        overseasTitle: String = "SONIC THE HEDGEHOG",
        hasSram: Boolean = true
    ): ByteArray {
        val bytes = ByteArray(0x4000)

        // System name at 0x100
        val sysName = "SEGA MEGA DRIVE ".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(sysName, 0, bytes, 0x100, 16)

        // Domestic Title at 0x120
        val domBytes = domesticTitle.padEnd(48, ' ').toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(domBytes, 0, bytes, 0x120, 48)

        // Overseas Title at 0x150
        val overBytes = overseasTitle.padEnd(48, ' ').toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(overBytes, 0, bytes, 0x150, 48)

        // Product number at 0x180
        val prod = "GM MK-1234 -00".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(prod, 0, bytes, 0x180, 14)

        // Checksum at 0x18E
        bytes[0x18E] = 0x56
        bytes[0x18F] = 0x78

        // SRAM "RA" at 0x1B0
        if (hasSram) {
            bytes[0x1B0] = 'R'.code.toByte()
            bytes[0x1B1] = 'A'.code.toByte()
        }

        return bytes
    }

    @Test
    fun `test Genesis Mega Drive ROM detection and parsing`() {
        val rom = createGenesisRom(domesticTitle = "SONIC THE HEDGEHOG", overseasTitle = "SONIC THE HEDGEHOG", hasSram = true)
        val header = GenesisRomParser.parse(rom)

        assertEquals("genesis", header.platform)
        assertEquals("SONIC THE HEDGEHOG", header.overseasTitle)
        assertEquals("GM MK-1234 -00", header.productNumber)
        assertTrue(header.hasSram)
        assertTrue(header.magicValid)
    }
}
