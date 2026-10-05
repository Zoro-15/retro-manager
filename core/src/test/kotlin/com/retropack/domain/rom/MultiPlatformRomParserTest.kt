package com.retropack.domain.rom

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class MultiPlatformRomParserTest {

    @Test
    fun `test SNES ROM auto-detection via unified RomParser`() {
        val bytes = ByteArray(0x80000)
        val headerOffset = 0x7FC0
        val titleBytes = "SUPER METROID".padEnd(21, ' ').toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(titleBytes, 0, bytes, headerOffset, 21)
        bytes[headerOffset + 0x15] = 0x20 // LoROM
        bytes[headerOffset + 0x16] = 0x02 // Battery
        val checksum = 0x3344
        val complement = 0xCCBB
        bytes[headerOffset + 0x1C] = (complement and 0xFF).toByte()
        bytes[headerOffset + 0x1D] = ((complement ushr 8) and 0xFF).toByte()
        bytes[headerOffset + 0x1E] = (checksum and 0xFF).toByte()
        bytes[headerOffset + 0x1F] = ((checksum ushr 8) and 0xFF).toByte()

        val identity = RomParser.parse(bytes)
        assertEquals("snes", identity.platform)
        assertEquals("SUPER METROID", identity.gameTitle)
        assertTrue(identity.hasBattery)
        val pkg = identity.derivePackageName()
        assertTrue(pkg.startsWith("com.retropack.game.supermetroid_"))
    }

    @Test
    fun `test Genesis ROM auto-detection via unified RomParser`() {
        val bytes = ByteArray(0x4000)
        val sysName = "SEGA MEGA DRIVE ".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(sysName, 0, bytes, 0x100, 16)
        val title = "STREETS OF RAGE 2".padEnd(48, ' ').toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(title, 0, bytes, 0x150, 48)

        val identity = RomParser.parse(bytes)
        assertEquals("genesis", identity.platform)
        assertEquals("STREETS OF RAGE 2", identity.gameTitle)
        val pkg = identity.derivePackageName()
        assertTrue(pkg.startsWith("com.retropack.game.streetsofrage2_"))
    }

    @Test
    fun `test NES ROM auto-detection via unified RomParser`() {
        val bytes = ByteArray(16 + 16384 + 8192)
        bytes[0] = 0x4E
        bytes[1] = 0x45
        bytes[2] = 0x53
        bytes[3] = 0x1A
        bytes[4] = 1 // 16 KB PRG
        bytes[5] = 1 // 8 KB CHR
        bytes[6] = 0x02 // battery flag

        val identity = RomParser.parse(bytes)
        assertEquals("nes", identity.platform)
        assertTrue(identity.hasBattery)
    }

    @Test
    fun `test PC Engine ROM auto-detection via unified RomParser`() {
        val bytes = ByteArray(0x40000)
        // Reset vector at 0x1FFFE (offset 0x3FFFE in 256KB ROM)
        bytes[0x3FFFE] = 0x00
        bytes[0x3FFFF] = 0xE0.toByte()

        val identity = RomParser.parse(bytes, "Bonk_Adventure.pce")
        assertEquals("pce", identity.platform)
        assertEquals("Bonk Adventure", identity.gameTitle)
    }

    @Test
    fun `test extension fallback for unheadered homebrews across 2D consoles`() {
        val dummyBytes = ByteArray(1024) { 0x11 }

        val gbaIdentity = RomParser.parse(dummyBytes, "Celeste_Classic.gba")
        assertEquals("gba", gbaIdentity.platform)
        assertEquals("Celeste Classic", gbaIdentity.gameTitle)

        val nesIdentity = RomParser.parse(dummyBytes, "Micro_Mages.nes")
        assertEquals("nes", nesIdentity.platform)
        assertEquals("Micro Mages", nesIdentity.gameTitle)

        val snesIdentity = RomParser.parse(dummyBytes, "Chrono_Trigger.sfc")
        assertEquals("snes", snesIdentity.platform)
        assertEquals("Chrono Trigger", snesIdentity.gameTitle)

        val pceIdentity = RomParser.parse(dummyBytes, "Castlevania_Rondo.pce")
        assertEquals("pce", pceIdentity.platform)
        assertEquals("Castlevania Rondo", pceIdentity.gameTitle)

        val genIdentity = RomParser.parse(dummyBytes, "Sonic_The_Hedgehog.md")
        assertEquals("genesis", genIdentity.platform)
        assertEquals("Sonic The Hedgehog", genIdentity.gameTitle)
    }

    @Test
    fun `test unsupported 3D platforms throw InvalidRomException`() {
        val dummyBytes = ByteArray(1024) { 0x11 }

        assertThrows(InvalidRomException::class.java) {
            RomParser.parse(dummyBytes, "Crisis_Core_FFVII.iso")
        }
        assertThrows(InvalidRomException::class.java) {
            RomParser.parse(dummyBytes, "Zelda_Ocarina_of_Time.z64")
        }
        assertThrows(InvalidRomException::class.java) {
            RomParser.parse(dummyBytes, "Mario_Kart_DS.nds")
        }
        assertThrows(InvalidRomException::class.java) {
            RomParser.parse(dummyBytes, "Metal_Slug.neo")
        }
    }
}
