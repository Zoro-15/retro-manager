package com.retropack.packaging

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class RomAssetInjectorTest {

    private val sampleRom = "Test ROM Binary Payload".toByteArray(StandardCharsets.UTF_8)
    private val sampleJson = """{"schema_version":1,"game_title":"Test"}"""

    @Test
    fun `prepares correct asset entries for rom and config`() {
        val entries = RomAssetInjector.prepareAssetEntries(sampleRom, sampleJson)

        assertEquals(2, entries.size)
        assertTrue(entries.containsKey(RomAssetInjector.ROM_ENTRY))
        assertTrue(entries.containsKey(RomAssetInjector.CONFIG_ENTRY))

        assertArrayEquals(sampleRom, entries[RomAssetInjector.ROM_ENTRY])
        assertEquals(sampleJson, String(entries[RomAssetInjector.CONFIG_ENTRY]!!, StandardCharsets.UTF_8))
    }

    @Test
    fun `sanitizeEntryPath rejects path traversal sequences`() {
        assertThrows(IllegalArgumentException::class.java) {
            RomAssetInjector.sanitizeEntryPath("assets/../secret.txt")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RomAssetInjector.sanitizeEntryPath("assets/subdir/../../etc/passwd")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RomAssetInjector.sanitizeEntryPath("/assets/game.rom")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RomAssetInjector.sanitizeEntryPath("res/game.rom")
        }
    }

    @Test
    fun `empty rom bytes or blank json throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            RomAssetInjector.prepareAssetEntries(ByteArray(0), sampleJson)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RomAssetInjector.prepareAssetEntries(sampleRom, "   ")
        }
    }
}
