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
    fun `test N64 ROM auto-detection via unified RomParser`() {
        val bytes = ByteArray(0x1000)
        // Magic Z64
        bytes[0] = 0x80.toByte()
        bytes[1] = 0x37
        bytes[2] = 0x12
        bytes[3] = 0x40

        val title = "SUPER MARIO 64".padEnd(20, ' ').toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(title, 0, bytes, 0x20, 20)
        val code = "NSME".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(code, 0, bytes, 0x3B, 4)

        val identity = RomParser.parse(bytes)
        assertEquals("n64", identity.platform)
        assertEquals("SUPER MARIO 64", identity.gameTitle)
        assertEquals("NSME", identity.gameCode)
    }

    @Test
    fun `test NDS ROM auto-detection via unified RomParser`() {
        val bytes = ByteArray(0x1000)
        val title = "MARIO KART".padEnd(12, ' ').toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(title, 0, bytes, 0x00, 12)
        val code = "AMCE".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(code, 0, bytes, 0x0C, 4)
        val maker = "01".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(maker, 0, bytes, 0x10, 2)

        // arm9 offset = 0x4000
        bytes[0x20] = 0x00
        bytes[0x21] = 0x40
        // arm7 offset = 0x8000
        bytes[0x28] = 0x00
        bytes[0x29] = 0x80.toByte()

        val identity = RomParser.parse(bytes)
        assertEquals("nds", identity.platform)
        assertEquals("MARIO KART", identity.gameTitle)
        assertEquals("AMCE", identity.gameCode)
    }

    @Test
    fun `test extension fallback for unheadered homebrews and disc formats`() {
        val dummyBytes = ByteArray(1024) { 0x11 }

        val pspIdentity = RomParser.parse(dummyBytes, "Crisis_Core_FFVII.iso")
        assertEquals("psp", pspIdentity.platform)
        assertEquals("Crisis Core FFVII", pspIdentity.gameTitle)

        val arcadeIdentity = RomParser.parse(dummyBytes, "kof98.zip")
        assertEquals("arcade", arcadeIdentity.platform)
        assertEquals("kof98", arcadeIdentity.gameTitle)

        val snesIdentity = RomParser.parse(dummyBytes, "Chrono_Trigger.sfc")
        assertEquals("snes", snesIdentity.platform)
        assertEquals("Chrono Trigger", snesIdentity.gameTitle)

        val n64Identity = RomParser.parse(dummyBytes, "Zelda_Ocarina_of_Time.z64")
        assertEquals("n64", n64Identity.platform)
        assertEquals("Zelda Ocarina of Time", n64Identity.gameTitle)
    }
}
