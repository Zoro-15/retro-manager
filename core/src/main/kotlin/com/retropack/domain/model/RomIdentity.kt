package com.retropack.domain.model

import com.retropack.packaging.PackageIdentity

/**
 * Immutable domain model representing fully inspected and verified ROM content.
 * Serves as the domain anchor for creating declarative BuildRequests.
 */
data class RomIdentity(
    val platform: String, // "gb", "gbc", "gba"
    val gameTitle: String,
    val gameCode: String? = null,
    val makerCode: String? = null,
    val softwareVersion: Int = 0,
    val fileSize: Long,
    val checksums: ChecksumRecords,
    val headerChecksumValid: Boolean,
    val logoOrFixedValid: Boolean,
    val cgbFlag: Int? = null,
    val cartridgeType: Int? = null,
    val mbcType: String? = null,
    val hasBattery: Boolean = false
) {
    /**
     * Converts this RomIdentity into a ContentPayload suitable for BuildRequest.
     */
    fun toContentPayload(sourceRomName: String): ContentPayload {
        return ContentPayload(
            sourceRom = sourceRomName,
            platform = platform,
            header = RomHeaderData(
                gameCode = gameCode,
                makerCode = makerCode,
                cgbFlag = cgbFlag,
                cartridgeType = cartridgeType
            )
        )
    }

    /**
     * Generates a deterministic package name according to architechture.md:
     * com.retropack.game.<slug>_<hash10>
     * Slug rules (max 16, g_ prefix on leading digit) are owned by
     * [PackageIdentity.sanitizeSlug] — the single source of truth (issue #18).
     */
    fun derivePackageName(customSlug: String? = null): String {
        val slug = PackageIdentity.sanitizeSlug(customSlug ?: gameTitle)
        val hash10 = checksums.sha256.take(10)
        require(hash10.length == 10) { "SHA-256 checksum must provide 10 hex chars for package name" }
        return "com.retropack.game.${slug}_${hash10}"
    }
}
