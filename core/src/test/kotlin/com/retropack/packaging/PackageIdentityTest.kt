package com.retropack.packaging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class PackageIdentityTest {

    private val sampleRomBytes = "Nintendo GameBoy Sample ROM Payload 2026".toByteArray(StandardCharsets.UTF_8)

    @Test
    fun `creates deterministic package name from game title and rom hash`() {
        val identity = PackageIdentity.create(
            gameTitle = "Pokemon Red",
            romBytes = sampleRomBytes,
            versionCode = 1001,
            versionName = "1.0.1"
        )

        assertEquals("pokemonred", identity.slug)
        assertEquals(10, identity.hash10.length)
        assertTrue(identity.packageName.startsWith("com.retropack.game.pokemonred_"))
        assertEquals(1001, identity.versionCode)
        assertEquals("1.0.1", identity.versionName)
    }

    @Test
    fun `slug sanitization handles punctuation and spaces`() {
        val slug = PackageIdentity.sanitizeSlug("The Legend of Zelda: Link's Awakening DX")
        assertEquals("thelegendofzelda", slug) // truncated to 16 chars
    }

    @Test
    fun `slug sanitization prefixes numeric first character with g_`() {
        val slug = PackageIdentity.sanitizeSlug("1080 Snowboarding")
        assertTrue(slug.startsWith("g_1080"), "Numeric prefix must be prefixed with g_: $slug")
    }

    @Test
    fun `slug sanitization falls back to game when empty`() {
        val slug = PackageIdentity.sanitizeSlug("!@#$%^&*()")
        assertEquals("game", slug)
    }

    @Test
    fun `same rom bytes and title produce identical package identity`() {
        val id1 = PackageIdentity.create("Metroid Fusion", sampleRomBytes)
        val id2 = PackageIdentity.create("Metroid Fusion", sampleRomBytes)

        assertEquals(id1.packageName, id2.packageName)
        assertEquals(id1.hash10, id2.hash10)
    }

    @Test
    fun `different rom bytes produce different hash10 and package names`() {
        val id1 = PackageIdentity.create("Metroid", sampleRomBytes)
        val id2 = PackageIdentity.create("Metroid", "Different ROM Bytes".toByteArray(StandardCharsets.UTF_8))

        assertEquals(id1.slug, id2.slug)
        assertTrue(id1.hash10 != id2.hash10)
        assertTrue(id1.packageName != id2.packageName)
    }

    @Test
    fun `createWithHash reuses precomputed digest without rehashing rom`() {
        val full = PackageIdentity.create("Metroid Fusion", sampleRomBytes)
        val sha = java.security.MessageDigest.getInstance("SHA-256")
            .digest(sampleRomBytes).let { HexUtils.run { it.toHexString() } }

        val viaHash = PackageIdentity.createWithHash("Metroid Fusion", sha, full.versionCode, full.versionName)
        assertEquals(full, viaHash)
    }

    @Test
    fun `validatePackageName accepts valid Android package identifiers`() {
        PackageIdentity.validatePackageName("com.retropack.game.test_1234567890")
        PackageIdentity.validatePackageName("a.b.c")
    }

    @Test
    fun `validatePackageName rejects invalid formats`() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageIdentity.validatePackageName("invalid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PackageIdentity.validatePackageName(".com.starts.with.dot")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PackageIdentity.validatePackageName("com.retropack.123starts_with_number")
        }
    }
}
