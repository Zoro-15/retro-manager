package com.retropack.domain.rom

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RomParserTest {

    @Test
    fun `test auto-detection and identity generation for GB ROM`() {
        val bytes = GbTestRomFactory.create(title = "SUPER MARIOLAND", isCgb = false)
        val identity = RomParser.parse(bytes)

        assertEquals("gb", identity.platform)
        assertEquals("SUPER MARIOLAND", identity.gameTitle)
        assertTrue(identity.headerChecksumValid)
        assertTrue(identity.logoOrFixedValid)
        assertEquals(bytes.size.toLong(), identity.fileSize)
        assertNotNull(identity.checksums.sha256)
        assertNotNull(identity.checksums.crc32)

        val pkgName = identity.derivePackageName()
        assertTrue(pkgName.startsWith("com.retropack.game.supermarioland_"))
        assertEquals(34 + 10, pkgName.length) // "com.retropack.game." (19) + "supermarioland" (14) + "_" (1) + 10 hex
    }

    @Test
    fun `test auto-detection and identity generation for GBC ROM`() {
        val bytes = GbTestRomFactory.create(
            title = "ZELDA DX",
            isCgb = true,
            cgbFlag = 0xC0,
            cartridgeType = 0x1B // MBC5+RAM+BATTERY
        )
        val identity = RomParser.parse(bytes)

        assertEquals("gbc", identity.platform)
        assertEquals("ZELDA DX", identity.gameTitle)
        assertTrue(identity.hasBattery)
        assertEquals("MBC5+RAM+BATTERY", identity.mbcType)
        assertTrue(identity.headerChecksumValid)

        val contentPayload = identity.toContentPayload("zelda_dx.gbc")
        assertEquals("gbc", contentPayload.platform)
        assertEquals("zelda_dx.gbc", contentPayload.sourceRom)
        assertEquals(0xC0, contentPayload.header?.cgbFlag)
        assertEquals(0x1B, contentPayload.header?.cartridgeType)
    }

    @Test
    fun `test auto-detection and identity generation for GBA ROM`() {
        val bytes = GbaTestRomFactory.create(
            title = "POKEMON EMER",
            gameCode = "BPEE",
            makerCode = "01",
            version = 0
        )
        val identity = RomParser.parse(bytes)

        assertEquals("gba", identity.platform)
        assertEquals("POKEMON EMER", identity.gameTitle)
        assertEquals("BPEE", identity.gameCode)
        assertEquals("01", identity.makerCode)
        assertTrue(identity.headerChecksumValid)
        assertTrue(identity.logoOrFixedValid)

        val pkgName = identity.derivePackageName()
        assertTrue(pkgName.startsWith("com.retropack.game.pokemonemer_"))

        val contentPayload = identity.toContentPayload("emerald.gba")
        assertEquals("BPEE", contentPayload.header?.gameCode)
        assertEquals("01", contentPayload.header?.makerCode)
    }

    @Test
    fun `test unidentifiable data throws InvalidRomException`() {
        val randomBytes = ByteArray(500) { 0x55 }
        assertThrows<InvalidRomException> {
            RomParser.parse(randomBytes)
        }
    }

    @Test
    fun `test GBA checksum match with bad fixed byte falls through to GB`() {
        // Valid GB ROM whose GBA-header region happens to checksum-match while
        // the fixed byte stays invalid: must parse as GB, not throw (issue #18).
        val bytes = GbTestRomFactory.create(title = "FALLBACK", isCgb = false)
        bytes[0xBD] = GbaRomParser.calculateHeaderChecksum(bytes).toByte()
        assertFalse((bytes[0xB2].toInt() and 0xFF) == 0x96)

        val identity = RomParser.parse(bytes)
        assertEquals("gb", identity.platform)
        assertEquals("FALLBACK", identity.gameTitle)
    }

    @Test
    fun `test digit-leading title gets g_ prefixed package segment`() {
        val bytes = GbTestRomFactory.create(title = "123 GAME", isCgb = false)
        val identity = RomParser.parse(bytes)

        val pkgName = identity.derivePackageName()
        assertTrue(pkgName.startsWith("com.retropack.game.g_123game_"))
        val lastSegment = pkgName.substringAfterLast('.')
        assertTrue(lastSegment.first().isLetter())
    }
}
