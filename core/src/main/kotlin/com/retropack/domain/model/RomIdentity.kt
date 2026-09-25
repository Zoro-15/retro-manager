package com.retropack.domain.model

import com.retropack.domain.rom.GbRomHeader
import com.retropack.domain.rom.GbaRomHeader

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
    val hasBattery: Boolean = false,
    val gbHeader: GbRomHeader? = null,
    val gbaHeader: GbaRomHeader? = null
) {
    /**
     * Converts this RomIdentity into a ContentPayload suitable for BuildRequest.
     */
    fun toContentPayload(sourceRomName: String, appliedPatch: String? = null): ContentPayload {
        return ContentPayload(
            sourceRom = sourceRomName,
            platform = platform,
            fileSize = fileSize,
            checksums = checksums,
            header = RomHeaderData(
                gameCode = gameCode,
                makerCode = makerCode,
                romVersion = softwareVersion,
                cgbFlag = cgbFlag,
                cartridgeType = cartridgeType
            ),
            appliedPatch = appliedPatch
        )
    }

    /**
     * Generates a deterministic package name according to architechture.md:
     * com.retropack.game.<slug>_<hash10>
     * slug = lowercase ASCII [a-z0-9], max 16 chars, prefixed with g_ if it
     * would otherwise start with a digit (Android package segments must start
     * with a letter).
     */
    fun derivePackageName(customSlug: String? = null): String {
        var slug = (customSlug ?: gameTitle)
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(16)
            .ifBlank { "game" }
        if (slug.first().isDigit()) {
            slug = "g_$slug"
        }
        val hash10 = checksums.sha256.take(10)
        require(hash10.length == 10) { "SHA-256 checksum must provide 10 hex chars for package name" }
        return "com.retropack.game.${slug}_${hash10}"
    }
}
