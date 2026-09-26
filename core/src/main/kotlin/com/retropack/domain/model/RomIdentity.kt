package com.retropack.domain.model

import com.retropack.domain.rom.GbRomHeader
import com.retropack.domain.rom.GbaRomHeader
import com.retropack.domain.rom.GenesisRomHeader
import com.retropack.domain.rom.N64RomHeader
import com.retropack.domain.rom.NdsRomHeader
import com.retropack.domain.rom.NesRomHeader
import com.retropack.domain.rom.PceRomHeader
import com.retropack.domain.rom.PsxRomHeader
import com.retropack.domain.rom.SnesRomHeader
import com.retropack.packaging.PackageIdentity

/**
 * Immutable domain model representing fully inspected and verified ROM content.
 * Serves as the domain anchor for creating declarative BuildRequests.
 */
data class RomIdentity(
    val platform: String, // "gb", "gbc", "gba", "snes", "genesis", "sms", "gg", "nes", "psx", "n64", "nds", "pce"
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
    val gbaHeader: GbaRomHeader? = null,
    val snesHeader: SnesRomHeader? = null,
    val genesisHeader: GenesisRomHeader? = null,
    val nesHeader: NesRomHeader? = null,
    val psxHeader: PsxRomHeader? = null,
    val n64Header: N64RomHeader? = null,
    val ndsHeader: NdsRomHeader? = null,
    val pceHeader: PceRomHeader? = null
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
