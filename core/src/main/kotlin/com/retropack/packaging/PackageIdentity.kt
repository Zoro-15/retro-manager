package com.retropack.packaging

import com.retropack.packaging.HexUtils.toHexString
import java.security.MessageDigest
import java.util.Locale

/**
 * Deterministic Package Identity conforming to RetroPack Constitutional Invariant 4.
 *
 * Package name formula:
 *   PackageName = "com.retropack.game." + slug + "_" + hash10
 *
 * Where:
 * - slug: Lowercase ASCII alphanumeric [a-z0-9], max 16 chars (prefixed with `g_` if starting with a digit).
 * - hash10: First 10 hex characters of SHA-256(sourceRomBytes).
 */
data class PackageIdentity(
    val packageName: String,
    val slug: String,
    val hash10: String,
    val versionCode: Int,
    val versionName: String
) {
    companion object {
        const val PACKAGE_PREFIX = "com.retropack.game."
        private const val MAX_SLUG_LEN = 16
        private val ANDROID_PACKAGE_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
        private val SLUG_SANITIZE_REGEX = Regex("[^a-z0-9]")

        /**
         * Creates a deterministic [PackageIdentity] for the specified [gameTitle] and [romBytes].
         */
        fun create(
            gameTitle: String,
            romBytes: ByteArray,
            versionCode: Int = 1000,
            versionName: String = "1.0.0"
        ): PackageIdentity {
            val hash10 = sha256Hex(romBytes).substring(0, 10).lowercase(Locale.ROOT)
            return createWithHash(gameTitle, hash10, versionCode, versionName)
        }

        /**
         * Creates a deterministic [PackageIdentity] from a precomputed ROM
         * SHA-256 hex digest, avoiding a second full hash pass when the caller
         * (Step 1 checksums, `RomParser`) already hashed the ROM.
         */
        fun createWithHash(
            gameTitle: String,
            romSha256Hex: String,
            versionCode: Int = 1000,
            versionName: String = "1.0.0"
        ): PackageIdentity {
            val hash10 = romSha256Hex.lowercase(Locale.ROOT).take(10)
            require(hash10.length == 10 && hash10.all { it in '0'..'9' || it in 'a'..'f' }) {
                "ROM SHA-256 must provide at least 10 hex chars"
            }
            val slug = sanitizeSlug(gameTitle)
            val packageName = "$PACKAGE_PREFIX${slug}_$hash10"
            validatePackageName(packageName)
            return PackageIdentity(
                packageName = packageName,
                slug = slug,
                hash10 = hash10,
                versionCode = versionCode,
                versionName = versionName
            )
        }

        /**
         * Sanitizes a title into a valid Android package component slug.
         */
        fun sanitizeSlug(title: String): String {
            var cleaned = title.lowercase(Locale.ROOT)
                .replace(SLUG_SANITIZE_REGEX, "")
            if (cleaned.isEmpty()) {
                cleaned = "game"
            }
            if (cleaned.length > MAX_SLUG_LEN) {
                cleaned = cleaned.substring(0, MAX_SLUG_LEN)
            }
            if (cleaned.first().isDigit()) {
                cleaned = "g_$cleaned"
                if (cleaned.length > MAX_SLUG_LEN + 2) {
                    cleaned = cleaned.substring(0, MAX_SLUG_LEN + 2)
                }
            }
            return cleaned
        }

        /**
         * Verifies that the package name conforms to Android package naming requirements.
         */
        fun validatePackageName(packageName: String) {
            require(packageName.length in 5..128) {
                "Package name length (${packageName.length}) must be between 5 and 128 characters: $packageName"
            }
            require(ANDROID_PACKAGE_REGEX.matches(packageName)) {
                "Package name is invalid for Android: $packageName"
            }
        }

        private fun sha256Hex(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(bytes).toHexString()
        }
    }
}
