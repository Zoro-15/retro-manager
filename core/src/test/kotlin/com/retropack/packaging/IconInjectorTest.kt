package com.retropack.packaging

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IconInjectorTest {

    private val validPngHeader = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        0x00, 0x00, 0x00, 0x0D // IHDR chunk length
    )

    @Test
    fun `validatePng succeeds on valid png header`() {
        IconInjector.validatePng(validPngHeader)
    }

    @Test
    fun `validatePng rejects invalid headers or short byte arrays`() {
        assertThrows(IllegalArgumentException::class.java) {
            IconInjector.validatePng(byteArrayOf(0x01, 0x02, 0x03))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IconInjector.validatePng(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x00, 0x00)) // GIF89a
        }
    }

    @Test
    fun `prepareIconEntries maps foreground and optional background correctly`() {
        val entries = IconInjector.prepareIconEntries(
            foregroundBytes = validPngHeader,
            backgroundBytes = validPngHeader
        )

        // 4 drawable entries + 10 mipmap directories * 2 (ic_launcher + ic_launcher_round) = 24 entries
        assertEquals(24, entries.size)
        assertTrue(entries.containsKey(IconInjector.FOREGROUND_ENTRY))
        assertTrue(entries.containsKey(IconInjector.FOREGROUND_ENTRY_V4))
        assertTrue(entries.containsKey(IconInjector.BACKGROUND_ENTRY))
        assertTrue(entries.containsKey(IconInjector.BACKGROUND_ENTRY_V4))
        assertTrue(entries.containsKey(IconInjector.MIPMAP_XXHDPI_ENTRY))
        assertTrue(entries.containsKey(IconInjector.MIPMAP_XXHDPI_V4_ENTRY))
        assertTrue(entries.containsKey(IconInjector.MIPMAP_XXHDPI_ROUND_ENTRY))
        assertTrue(entries.containsKey(IconInjector.MIPMAP_XXHDPI_ROUND_V4_ENTRY))
        assertTrue(entries.containsKey("res/mipmap-xxxhdpi/ic_launcher.png"))
        assertTrue(entries.containsKey("res/mipmap-xxxhdpi-v4/ic_launcher.png"))
        assertArrayEquals(validPngHeader, entries[IconInjector.FOREGROUND_ENTRY])
        assertArrayEquals(validPngHeader, entries[IconInjector.FOREGROUND_ENTRY_V4])
        assertArrayEquals(validPngHeader, entries[IconInjector.BACKGROUND_ENTRY])
        assertArrayEquals(validPngHeader, entries[IconInjector.BACKGROUND_ENTRY_V4])
    }

    @Test
    fun `prepareIconEntries works with foreground only`() {
        val entries = IconInjector.prepareIconEntries(foregroundBytes = validPngHeader)
        // 2 drawable entries + 10 mipmap directories * 2 = 22 entries
        assertEquals(22, entries.size)
        assertTrue(entries.containsKey(IconInjector.FOREGROUND_ENTRY))
        assertTrue(entries.containsKey(IconInjector.FOREGROUND_ENTRY_V4))
        assertTrue(entries.containsKey(IconInjector.MIPMAP_XXHDPI_ENTRY))
        assertTrue(entries.containsKey(IconInjector.MIPMAP_XXHDPI_V4_ENTRY))
        assertTrue(entries.containsKey(IconInjector.MIPMAP_XXHDPI_ROUND_ENTRY))
        assertTrue(entries.containsKey(IconInjector.MIPMAP_XXHDPI_ROUND_V4_ENTRY))
    }
}
