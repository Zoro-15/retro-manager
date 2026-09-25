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
     */
    fun derivePackageName(customSlug: String? = null): String {
        val slug = (customSlug ?: gameTitle)
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(20)
            .ifBlank { "game" }
        val hash10 = checksums.sha256.take(10)
        return "com.retropack.game.${slug}_${hash10}"
    }
}
