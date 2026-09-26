package com.retropack.domain.rom

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveExtractorTest {

    @Test
    fun `isArchive correctly detects ZIP and GZIP magic bytes and extensions`() {
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)
        assertTrue(ArchiveExtractor.isArchive(zipHeader, "game.zip"))
        assertTrue(ArchiveExtractor.isArchive(byteArrayOf(1, 2, 3), "game.rar"))
        assertTrue(ArchiveExtractor.isArchive(byteArrayOf(1, 2, 3), "game.7z"))
        assertTrue(ArchiveExtractor.isArchive(byteArrayOf(0x1F.toByte(), 0x8B.toByte()), "game.nes.gz"))

        assertFalse(ArchiveExtractor.isArchive(byteArrayOf(0, 0, 0, 0), "game.gba"))
    }

    @Test
    fun `extractCandidateRom unpacks ZIP containing GBA ROM and ignores metadata`() {
        val gbaHeader = ByteArray(0xC0).apply {
            this[0xB2] = 0x96.toByte() // GBA fixed value
            val title = "POKEMON EMER"
            System.arraycopy(title.toByteArray(), 0, this, 0xA0, title.length)
            this[0xBD] = GbaRomParser.calculateHeaderChecksum(this).toByte()
        }

        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                // Add ignored noise entries first
                zos.putNextEntry(ZipEntry("readme.txt"))
                zos.write("Pokemon Emerald instructions".toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("__MACOSX/._game.gba"))
                zos.write(byteArrayOf(1, 2, 3))
                zos.closeEntry()

                // Add real ROM entry
                zos.putNextEntry(ZipEntry("roms/pokemon_emerald.gba"))
                zos.write(gbaHeader)
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val result = ArchiveExtractor.extractCandidateRom(zipBytes, "pokemon_emerald.zip")
        assertTrue(result.isExtractedFromArchive)
        assertEquals("pokemon_emerald.gba", result.candidateFileName)
        assertArrayEquals(gbaHeader, result.bytes)
        assertEquals("ZIP", result.archiveType)

        // Verify RomParser can parse the extracted result transparently
        val identity = RomParser.parse(zipBytes, "pokemon_emerald.zip")
        assertEquals("gba", identity.platform)
        assertEquals("POKEMON EMER", identity.gameTitle)
    }

    @Test
    fun `extractCandidateRom handles nested ZIP archives recursively`() {
        val snesRom = ByteArray(0x8000).apply {
            // Setup SNES header at 0x7FC0
            val title = "SUPER MARIO WORLD   "
            System.arraycopy(title.toByteArray(), 0, this, 0x7FC0, title.length)
            this[0x7FD5] = 0x20.toByte() // LoROM FastROM
            this[0x7FD6] = 0x00.toByte() // ROM only
            this[0x7FD7] = 0x09.toByte() // 4 Mbit
            this[0x7FD8] = 0x00.toByte()
            this[0x7FD9] = 0x01.toByte() // USA
            this[0x7FDA] = 0x01.toByte()
            this[0x7FDC] = 0x55.toByte() // Complement
            this[0x7FDD] = 0xAA.toByte()
            this[0x7FDE] = 0xAA.toByte() // Checksum
            this[0x7FDF] = 0x55.toByte()
        }

        // Create inner ZIP containing snesRom
        val innerZipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("smw.sfc"))
                zos.write(snesRom)
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        // Create outer ZIP containing inner ZIP
        val outerZipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("inner.zip"))
                zos.write(innerZipBytes)
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val result = ArchiveExtractor.extractCandidateRom(outerZipBytes, "nested_games.zip")
        assertTrue(result.isExtractedFromArchive)
        assertEquals("smw.sfc", result.candidateFileName)
        assertArrayEquals(snesRom, result.bytes)

        val identity = RomParser.parse(outerZipBytes, "nested_games.zip")
        assertEquals("snes", identity.platform)
        assertEquals("SUPER MARIO WORLD", identity.gameTitle)
    }

    @Test
    fun `extractCandidateRom decompresses GZIP stream`() {
        val nesData = ByteArray(0x4010).apply {
            this[0] = 'N'.code.toByte()
            this[1] = 'E'.code.toByte()
            this[2] = 'S'.code.toByte()
            this[3] = 0x1A.toByte()
            this[4] = 2 // 32 KB PRG
            this[5] = 1 // 8 KB CHR
            this[6] = 0x00 // Mapper 0
        }

        val gzipBytes = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzos ->
                gzos.write(nesData)
            }
            baos.toByteArray()
        }

        val result = ArchiveExtractor.extractCandidateRom(gzipBytes, "SuperMarioBros.nes.gz")
        assertTrue(result.isExtractedFromArchive)
        assertEquals("SuperMarioBros.nes", result.candidateFileName)
        assertArrayEquals(nesData, result.bytes)

        val identity = RomParser.parse(gzipBytes, "SuperMarioBros.nes.gz")
        assertEquals("nes", identity.platform)
    }

    @Test
    fun `selectBestEntry prioritizes explicit console extensions over generic bin`() {
        val entries = listOf("manual.pdf", "game.bin", "game.cue", "sonic_advance.gba", "info.nfo")
        val selected = ArchiveExtractor.selectBestEntry(entries)
        assertEquals("sonic_advance.gba", selected)
    }

    @Test
    fun `SUPPORTED_ROM_EXTENSIONS contains all 10 console architectures`() {
        val expected = listOf(
            ".gba", ".gbc", ".gb",
            ".sfc", ".smc", ".snes",
            ".nes", ".fds",
            ".md", ".gen", ".smd", ".sms", ".gg",
            ".pce", ".sgx", ".tg16",
            ".iso", ".cue", ".chd", ".pbp",
            ".z64", ".n64", ".v64",
            ".nds", ".srl", ".dsi",
            ".cso", ".neo"
        )
        for (ext in expected) {
            assertTrue(ArchiveExtractor.SUPPORTED_ROM_EXTENSIONS.contains(ext), "Expected $ext in SUPPORTED_ROM_EXTENSIONS")
        }
    }
}
