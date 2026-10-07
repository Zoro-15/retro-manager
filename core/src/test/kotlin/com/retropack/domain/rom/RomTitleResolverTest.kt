package com.retropack.domain.rom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RomTitleResolverTest {

    @Test
    fun testResolveTitleFromKnownGameCode() {
        assertEquals("Golden Sun", RomTitleResolver.resolveTitle(gameCode = "AGBE"))
        assertEquals("Pokemon - Emerald Version", RomTitleResolver.resolveTitle(gameCode = "BPEE"))
        assertEquals("Metroid - Zero Mission", RomTitleResolver.resolveTitle(gameCode = "BMXE"))
        assertEquals("The Legend of Zelda: The Minish Cap", RomTitleResolver.resolveTitle(gameCode = "AMCE"))
        assertEquals("Super Mario Bros. Deluxe", RomTitleResolver.resolveTitle(gameCode = "AHYE"))
    }

    @Test
    fun testResolveTitleFromCleanFilenames() {
        assertEquals(
            "Golden Sun",
            RomTitleResolver.resolveTitle(fileName = "Golden Sun (USA, Europe).zip", headerTitle = "Golden_Sun_A", platform = "gba")
        )
        assertEquals(
            "Super Mario Bros. Deluxe",
            RomTitleResolver.resolveTitle(fileName = "Super Mario Bros. Deluxe (USA, Europe) (Rev 1).gbc", headerTitle = "MARIO DELUXAHYE", platform = "gbc")
        )
        assertEquals(
            "Teenage Mutant Ninja Turtles - Return of the Shredder",
            RomTitleResolver.resolveTitle(fileName = "Teenage Mutant Ninja Turtles - Return of the Shredder (Japan).zip", headerTitle = "TEENAGE MUTANT NINJA TURTLES", platform = "genesis")
        )
        assertEquals(
            "Castlevania - Aria of Sorrow",
            RomTitleResolver.resolveTitle(fileName = "Castlevania - Aria of Sorrow (USA) (Rev 1).gba", headerTitle = "CASTLEVANIA", platform = "gba")
        )
        assertEquals(
            "Metroid - Zero Mission",
            RomTitleResolver.resolveTitle(fileName = "Metroid_Zero_Mission_[!].zip", headerTitle = "METROID ZERO", platform = "gba")
        )
    }

    @Test
    fun testResolveTitleFallbackToHeaderWhenFilenameIsGenericOrNull() {
        assertEquals(
            "Super Mario Bros. Deluxe",
            RomTitleResolver.resolveTitle(fileName = "game.rom", gameCode = "AHYE", headerTitle = "MARIO DELUXAHYE", platform = "gbc")
        )
        assertEquals(
            "Golden_Sun",
            RomTitleResolver.resolveTitle(fileName = "rom.bin", headerTitle = "Golden_Sun_A", platform = "gba")
        )
        assertEquals(
            "POKEMON EMER",
            RomTitleResolver.resolveTitle(fileName = null, headerTitle = "POKEMON EMER", platform = "gba")
        )
        assertEquals(
            "SUPER MARIOLAND",
            RomTitleResolver.resolveTitle(fileName = null, headerTitle = "SUPER MARIOLAND", platform = "gb")
        )
    }

    @Test
    fun testSanitizeTitleTagsAndExtensions() {
        assertEquals("Super Mario World", RomTitleResolver.sanitizeTitle("Super Mario World (USA) [!].sfc"))
        assertEquals("Pokemon Emerald", RomTitleResolver.sanitizeTitle("Pokemon_Emerald_(U)_[b1].gba"))
        assertEquals("Sonic The Hedgehog", RomTitleResolver.sanitizeTitle("Sonic_The_Hedgehog_(W)_(Rev 01).md"))
    }
}
