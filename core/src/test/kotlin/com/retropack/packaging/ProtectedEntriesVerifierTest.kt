package com.retropack.packaging

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class ProtectedEntriesVerifierTest {

    private val dexContent = "dex\n035\u0000authentic dex content".toByteArray(StandardCharsets.UTF_8)
    private val soContent = "\u007fELFauthentic shared object".toByteArray(StandardCharsets.UTF_8)

    private val dexHash = RomAssetInjector.computeSha256(dexContent)
    private val soHash = RomAssetInjector.computeSha256(soContent)

    private val expectedProtectedEntries = mapOf(
        "classes.dex" to dexHash,
        "lib/arm64-v8a/libmgba.so" to soHash
    )

    @Test
    fun `verifyEntries succeeds when all digests match`() {
        val entryMap = mapOf(
            "classes.dex" to dexContent,
            "lib/arm64-v8a/libmgba.so" to soContent,
            "assets/game.rom" to "rom content".toByteArray(StandardCharsets.UTF_8)
        )

        assertDoesNotThrow {
            ProtectedEntriesVerifier.verifyEntries(entryMap, expectedProtectedEntries)
        }
    }

    @Test
    fun `verifyEntries throws SecurityException when protected entry is missing`() {
        val entryMap = mapOf(
            "classes.dex" to dexContent
            // lib/arm64-v8a/libmgba.so is missing
        )

        assertThrows(SecurityException::class.java) {
            ProtectedEntriesVerifier.verifyEntries(entryMap, expectedProtectedEntries)
        }
    }

    @Test
    fun `verifyEntries throws SecurityException when protected entry is tampered`() {
        val tamperedDex = "dex\n035\u0000tampered dex content".toByteArray(StandardCharsets.UTF_8)
        val entryMap = mapOf(
            "classes.dex" to tamperedDex,
            "lib/arm64-v8a/libmgba.so" to soContent
        )

        assertThrows(SecurityException::class.java) {
            ProtectedEntriesVerifier.verifyEntries(entryMap, expectedProtectedEntries)
        }
    }
}
