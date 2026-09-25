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

    // ------------------------------------------------------------------
    // Regression: descriptor files (runtime.json) declare digests with an
    // optional "sha256:" URI prefix while computed digests are bare hex.
    // Before normalization was added, EVERY comparison against a prefixed
    // declaration failed — Step 9 could never pass against a byte-perfect
    // template loaded via RuntimeRegistry.loadFromDirectory().
    // ------------------------------------------------------------------

    @Test
    fun `verifyEntries accepts sha256-prefixed declared digests`() {
        val prefixedEntries = expectedProtectedEntries.entries.associate { (k, v) ->
            k to "sha256:$v"
        }
        val entryMap = mapOf(
            "classes.dex" to dexContent,
            "lib/arm64-v8a/libmgba.so" to soContent
        )

        assertDoesNotThrow {
            ProtectedEntriesVerifier.verifyEntries(entryMap, prefixedEntries)
        }
    }

    @Test
    fun `verifyEntries accepts uppercase SHA256-prefixed and whitespace-padded digests`() {
        val prefixedEntries = expectedProtectedEntries.entries.associate { (k, v) ->
            k to "  SHA256: ${v.uppercase()}  "
        }
        val entryMap = mapOf(
            "classes.dex" to dexContent,
            "lib/arm64-v8a/libmgba.so" to soContent
        )

        assertDoesNotThrow {
            ProtectedEntriesVerifier.verifyEntries(entryMap, prefixedEntries)
        }
    }

    @Test
    fun `matchesDigest still rejects mismatching prefixed digests`() {
        val goodHash = RomAssetInjector.computeSha256(dexContent)

        assert(ProtectedEntriesVerifier.matchesDigest(goodHash, "sha256:$goodHash"))
        assert(ProtectedEntriesVerifier.matchesDigest(goodHash, goodHash.uppercase()))
        assert(!ProtectedEntriesVerifier.matchesDigest(goodHash, "sha256:deadbeef"))
    }

    @Test
    fun `verifyApk validates the real pinned runtime template descriptor`() {
        // End-to-end guard: the committed pinned bundle's descriptor (runtime.json)
        // must verify against the actual template.apk bytes. This is the exact
        // Step 9 code path the manager executes on-device.
        val bundleDir = sequenceOf(
            java.io.File("runtimes/mgba-unified"),
            java.io.File("../runtimes/mgba-unified")
        ).firstOrNull { it.isDirectory } ?: return

        val descriptorFile = java.io.File(bundleDir, "runtime.json")
        val templateApk = java.io.File(bundleDir, "template.apk")
        org.junit.jupiter.api.Assumptions.assumeTrue(
            descriptorFile.isFile && templateApk.isFile,
            "pinned bundle fixture not present in this checkout slice"
        )

        val descriptor = com.retropack.domain.runtime.RuntimeDescriptor
            .fromJson(descriptorFile.readText(StandardCharsets.UTF_8))

        assertDoesNotThrow {
            ProtectedEntriesVerifier.verifyApk(templateApk, descriptor)
        }
    }
}
