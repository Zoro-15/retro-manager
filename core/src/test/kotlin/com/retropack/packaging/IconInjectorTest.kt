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

        assertEquals(2, entries.size)
        assertTrue(entries.containsKey(IconInjector.FOREGROUND_ENTRY))
        assertTrue(entries.containsKey(IconInjector.BACKGROUND_ENTRY))
        assertArrayEquals(validPngHeader, entries[IconInjector.FOREGROUND_ENTRY])
        assertArrayEquals(validPngHeader, entries[IconInjector.BACKGROUND_ENTRY])
    }

    @Test
    fun `prepareIconEntries works with foreground only`() {
        val entries = IconInjector.prepareIconEntries(foregroundBytes = validPngHeader)
        assertEquals(1, entries.size)
        assertTrue(entries.containsKey(IconInjector.FOREGROUND_ENTRY))
    }
}
