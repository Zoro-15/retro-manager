package com.retropack.runtime.host

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

class RomStagerTest {

    @TempDir
    lateinit var tempDir: File

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `stageRom writes stream to destination atomically with fsync`() {
        val testData = "RetroPack GBA ROM binary payload".toByteArray()
        val stream = ByteArrayInputStream(testData)
        val destination = File(tempDir, "game.rom")
        val expectedSha = sha256Hex(testData)

        val success = RomStager.stageRom(stream, destination, expectedSha)

        assertTrue(success)
        assertTrue(destination.exists())
        assertArrayEquals(testData, destination.readBytes())

        // Ensure temporary file was cleanly renamed and does not persist
        val tempFile = File(tempDir, "game.rom.tmp")
        assertFalse(tempFile.exists())
    }

    @Test
    fun `stageRom rejects mismatched SHA-256 and cleans up temp file`() {
        val testData = "Corrupted or untrusted payload".toByteArray()
        val stream = ByteArrayInputStream(testData)
        val destination = File(tempDir, "corrupted.rom")
        val bogusSha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        val success = RomStager.stageRom(stream, destination, bogusSha)

        assertFalse(success)
        assertFalse(destination.exists())

        val tempFile = File(tempDir, "corrupted.rom.tmp")
        assertFalse(tempFile.exists())
    }

    @Test
    fun `isRomStaged validates presence and checksum`() {
        val testData = "Existing Valid ROM".toByteArray()
        val destination = File(tempDir, "existing.rom")
        val validSha = sha256Hex(testData)

        assertFalse(RomStager.isRomStaged(destination, validSha))

        destination.writeBytes(testData)
        assertTrue(RomStager.isRomStaged(destination, validSha))

        val invalidSha = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        assertFalse(RomStager.isRomStaged(destination, invalidSha))
    }
}
