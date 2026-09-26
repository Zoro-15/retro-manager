package com.retropack.manager.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.StandardCharsets

class GameIconScraperTest {

    @field:TempDir
    lateinit var tempDir: File

    @Test
    fun testSanitizeTitle() {
        assertEquals(
            "Pokemon - Emerald Version",
            GameIconScraper.sanitizeTitle("Pokemon - Emerald Version (USA, Europe).gba")
        )
        assertEquals(
            "Metroid Zero Mission",
            GameIconScraper.sanitizeTitle("Metroid_Zero_Mission_[!].zip")
        )
        assertEquals(
            "Castlevania Aria of Sorrow",
            GameIconScraper.sanitizeTitle("Castlevania - Aria of Sorrow (USA) (Rev 1).gba".replace("-", ""))
        )
        assertEquals(
            "Super Mario Advance 2",
            GameIconScraper.sanitizeTitle("Super_Mario_Advance_2_(U)_[b1].gbc")
        )
    }

    @Test
    fun testKnownGameCodeMappings() {
        assertEquals("Pokemon - Emerald Version (USA, Europe)", GameIconScraper.KNOWN_GAME_CODES["BPEE"])
        assertEquals("Metroid - Zero Mission (USA)", GameIconScraper.KNOWN_GAME_CODES["BMXE"])
        assertEquals("The Legend of Zelda - The Minish Cap (USA)", GameIconScraper.KNOWN_GAME_CODES["AMCE"])
        assertEquals("Castlevania - Aria of Sorrow (USA)", GameIconScraper.KNOWN_GAME_CODES["AATE"])
        assertEquals("Anguna - Warriors of the Demis (USA)", GameIconScraper.KNOWN_GAME_CODES["AGNA"])
    }

    @Test
    fun testBuildCandidateTitlesWithGameCode() {
        val candidates = GameIconScraper.buildCandidateTitles(
            gameTitle = "POKEMON EMER",
            rawFileName = "Pokemon - Emerald.gba",
            gameCode = "BPEE"
        )

        assertTrue(candidates.isNotEmpty())
        assertEquals("Pokemon - Emerald Version (USA, Europe)", candidates.first())
        assertTrue(candidates.contains("Pokemon - Emerald"))
        assertTrue(candidates.contains("POKEMON EMER"))
    }

    @Test
    fun testBuildCandidateTitlesWithoutGameCode() {
        val candidates = GameIconScraper.buildCandidateTitles(
            gameTitle = "Metroid Zero Mission",
            rawFileName = "Metroid - Zero Mission (USA).gba",
            gameCode = null
        )

        assertTrue(candidates.contains("Metroid - Zero Mission (USA)"))
        assertTrue(candidates.contains("Metroid - Zero Mission"))
    }

    @Test
    fun testResolveSystems() {
        assertEquals(listOf("Nintendo_-_Game_Boy_Advance"), GameIconScraper.resolveSystems("gba"))
        assertEquals(listOf("Nintendo_-_Game_Boy_Color", "Nintendo_-_Game_Boy"), GameIconScraper.resolveSystems("gbc"))
        assertEquals(listOf("Nintendo_-_Game_Boy", "Nintendo_-_Game_Boy_Color"), GameIconScraper.resolveSystems("gb"))
    }

    @Test
    fun testFetchBoxArtSyncWithMockNetworkAndCache() {
        val cacheDir = File(tempDir, "art_cache")
        val dummyPngPayload = "FAKE_PNG_BYTES_89504E470D0A1A0A".toByteArray(StandardCharsets.UTF_8)
        var networkCalls = 0

        val mockFetcher: (String) -> ByteArray? = { url ->
            networkCalls++
            if (url.contains("Emerald")) {
                dummyPngPayload
            } else {
                null
            }
        }

        // 1. Initial lookup -> Network fetch -> Cache write
        val result1 = GameIconScraper.fetchBoxArtSync(
            cacheDir = cacheDir,
            platform = "gba",
            gameTitle = "POKEMON EMER",
            rawFileName = "Pokemon - Emerald Version (USA).gba",
            gameCode = "BPEE",
            networkFetcher = mockFetcher
        )

        assertNotNull(result1)
        assertEquals(String(dummyPngPayload), String(result1!!))
        assertEquals(1, networkCalls)

        // Verify cache file created on disk
        val files = cacheDir.listFiles()
        assertNotNull(files)
        assertTrue(files!!.isNotEmpty())
        assertEquals(dummyPngPayload.size.toLong(), files.first().length())

        // 2. Second lookup -> Cache hit -> Zero network calls!
        val result2 = GameIconScraper.fetchBoxArtSync(
            cacheDir = cacheDir,
            platform = "gba",
            gameTitle = "POKEMON EMER",
            rawFileName = "Pokemon - Emerald Version (USA).gba",
            gameCode = "BPEE",
            networkFetcher = { throw IllegalStateException("Network should NOT be queried on cache hit!") }
        )

        assertNotNull(result2)
        assertEquals(String(dummyPngPayload), String(result2!!))
        assertEquals(1, networkCalls)
    }

    @Test
    fun testFetchBoxArtSyncOfflineFallback() {
        val cacheDir = File(tempDir, "art_cache_empty")

        // Network returns null (offline / 404)
        val result = GameIconScraper.fetchBoxArtSync(
            cacheDir = cacheDir,
            platform = "gba",
            gameTitle = "Unknown Homebrew Demo",
            rawFileName = "demo.gba",
            gameCode = null,
            networkFetcher = { null }
        )

        // Should gracefully return null without throwing
        assertNull(result)
    }
}
