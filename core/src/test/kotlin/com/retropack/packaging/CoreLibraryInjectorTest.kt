package com.retropack.packaging

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CoreLibraryInjectorTest {

    @field:TempDir
    lateinit var tempDir: File

    private val mockElfBytes = byteArrayOf(
        0x7F.toByte(), 0x45.toByte(), 0x4C.toByte(), 0x46.toByte(), // \x7fELF
        0x02, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    @Test
    fun `resolves canonical core IDs for all supported platforms`() {
        assertEquals("mgba", CoreLibraryInjector.resolveCoreId("gba"))
        assertEquals("mgba", CoreLibraryInjector.resolveCoreId("gbc"))
        assertEquals("mgba", CoreLibraryInjector.resolveCoreId("gb"))

        assertEquals("snes9x", CoreLibraryInjector.resolveCoreId("snes"))
        assertEquals("snes9x", CoreLibraryInjector.resolveCoreId("sfc"))
        assertEquals("snes9x", CoreLibraryInjector.resolveCoreId("smc"))

        assertEquals("genesis_plus_gx", CoreLibraryInjector.resolveCoreId("genesis"))
        assertEquals("genesis_plus_gx", CoreLibraryInjector.resolveCoreId("md"))
        assertEquals("genesis_plus_gx", CoreLibraryInjector.resolveCoreId("smd"))

        assertEquals("fceumm", CoreLibraryInjector.resolveCoreId("nes"))
        assertEquals("fceumm", CoreLibraryInjector.resolveCoreId("fds"))

        assertEquals("mednafen_pce_fast", CoreLibraryInjector.resolveCoreId("pce"))
        assertEquals("mednafen_pce_fast", CoreLibraryInjector.resolveCoreId("tg16"))
    }

    @Test
    fun `resolves canonical core library names`() {
        assertEquals("libretro_mgba.so", CoreLibraryInjector.resolveCoreLibName("gba"))
        assertEquals("libretro_snes9x.so", CoreLibraryInjector.resolveCoreLibName("snes"))
        assertEquals("libretro_genesis_plus_gx.so", CoreLibraryInjector.resolveCoreLibName("genesis"))
        assertEquals("libretro_fceumm.so", CoreLibraryInjector.resolveCoreLibName("nes"))
        assertEquals("libretro_mednafen_pce_fast.so", CoreLibraryInjector.resolveCoreLibName("pce"))
    }

    @Test
    fun `constructs valid APK entry paths`() {
        assertEquals("lib/arm64-v8a/libretro_mgba.so", CoreLibraryInjector.getEntryPath("arm64-v8a", "libretro_mgba.so"))
        assertEquals("lib/x86_64/libretro_snes9x.so", CoreLibraryInjector.getEntryPath("x86_64", "libretro_snes9x.so"))
    }

    @Test
    fun `rejects invalid ABI or non-so library names`() {
        assertThrows(IllegalArgumentException::class.java) {
            CoreLibraryInjector.getEntryPath("armeabi-v7a-invalid", "libretro_mgba.so")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CoreLibraryInjector.getEntryPath("arm64-v8a", "libretro_mgba.dll")
        }
    }

    @Test
    fun `sanitizes entry paths preventing directory traversal`() {
        assertDoesNotThrow {
            CoreLibraryInjector.sanitizeEntryPath("lib/arm64-v8a/libretro_mgba.so")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CoreLibraryInjector.sanitizeEntryPath("lib/arm64-v8a/../../../etc/passwd.so")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CoreLibraryInjector.sanitizeEntryPath("assets/libretro_mgba.so")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CoreLibraryInjector.sanitizeEntryPath("/lib/arm64-v8a/libretro_mgba.so")
        }
    }

    @Test
    fun `validates ELF magic header bytes`() {
        assertTrue(CoreLibraryInjector.isValidElfBinary(mockElfBytes))
        assertFalse(CoreLibraryInjector.isValidElfBinary(byteArrayOf(0x00, 0x01, 0x02, 0x03)))
        assertFalse(CoreLibraryInjector.isValidElfBinary(byteArrayOf(0x7F, 0x45))) // too short
    }

    @Test
    fun `prepares core entries from staged directory structure`() {
        val runtimesDir = File(tempDir, "runtimes")
        val arm64Dir = File(runtimesDir, "snes9x/lib/arm64-v8a").apply { mkdirs() }
        val x86Dir = File(runtimesDir, "snes9x/lib/x86_64").apply { mkdirs() }

        File(arm64Dir, "libretro_snes9x.so").writeBytes(mockElfBytes)
        File(x86Dir, "libretro_snes9x.so").writeBytes(mockElfBytes)

        val entries = CoreLibraryInjector.prepareCoreEntriesFromDirectory("snes9x", runtimesDir)
        assertEquals(2, entries.size)
        assertTrue(entries.containsKey("lib/arm64-v8a/libretro_snes9x.so"))
        assertTrue(entries.containsKey("lib/x86_64/libretro_snes9x.so"))
        assertArrayEquals(mockElfBytes, entries["lib/arm64-v8a/libretro_snes9x.so"])
    }

    @Test
    fun `sanitizes valid core entries map`() {
        val input = mapOf(
            "lib/arm64-v8a/libretro_mgba.so" to mockElfBytes,
            "lib/x86_64/libretro_mgba.so" to mockElfBytes
        )
        val sanitized = CoreLibraryInjector.sanitizeCoreEntries(input)
        assertEquals(2, sanitized.size)
    }

    @Test
    fun `rejects core entries with invalid non-ELF binaries`() {
        val invalidInput = mapOf(
            "lib/arm64-v8a/libretro_mgba.so" to "corrupt-non-elf-binary-bytes".toByteArray()
        )
        assertThrows(IllegalArgumentException::class.java) {
            CoreLibraryInjector.sanitizeCoreEntries(invalidInput)
        }
    }
}
