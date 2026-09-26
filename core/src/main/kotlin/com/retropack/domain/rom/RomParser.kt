package com.retropack.domain.rom

import com.retropack.domain.model.RomIdentity
import java.io.File

/**
 * Unified multi-platform ROM inspection facade.
 *
 * Automatically identifies platforms across Tier 1 and Tier 2 retro cores:
 * - GBA / GBC / GB (mGBA)
 * - SNES / Super Famicom (Snes9x)
 * - Sega Genesis / Mega Drive / Master System / Game Gear (Genesis Plus GX)
 * - NES / Famicom (FCEUmm)
 * - PlayStation 1 (PCSX ReARMed)
 * - Nintendo 64 (Mupen64Plus-Next)
 * - Nintendo DS (melonDS)
 * - PC Engine / TG-16 (Beetle PCE Fast)
 */
object RomParser {

    const val MAX_HEADER_PROBE_SIZE = 0x10000 // 64 KB covers SNES Lo/HiROM, PSX PVD, etc.

    /**
     * Inspects a ROM byte array and resolves its complete [RomIdentity].
     * Transparently unpacks ZIP and RAR archives and re-validates the inner candidate ROM.
     */
    fun parse(bytes: ByteArray, fileName: String? = null): RomIdentity {
        val candidate = if (ArchiveExtractor.isArchive(bytes, fileName)) {
            val extracted = ArchiveExtractor.extractCandidateRom(bytes, fileName ?: "archive.zip")
            if (extracted.isExtractedFromArchive) extracted else null
        } else {
            null
        }

        val targetBytes = candidate?.bytes ?: bytes
        val targetFileName = candidate?.candidateFileName ?: fileName
        val checksumResult = StreamChecksum.calculate(targetBytes)
        return parseWithChecksums(targetBytes, checksumResult, targetBytes.size.toLong(), targetFileName)
    }

    /**
     * Inspects a ROM file in a single streaming pass: checksums plus a bounded
     * header prefix up to 64 KB. Transparently extracts ZIP and RAR archives.
     */
    fun parse(file: File): RomIdentity {
        if (ArchiveExtractor.isArchive(file.readBytes().take(16).toByteArray(), file.name)) {
            val extracted = ArchiveExtractor.extractCandidateRom(file)
            if (extracted.isExtractedFromArchive) {
                return parse(extracted.bytes, extracted.candidateFileName)
            }
        }
        val (checksumResult, prefix) =
            StreamChecksum.calculateWithPrefix(file, MAX_HEADER_PROBE_SIZE)
        return parseWithChecksums(prefix, checksumResult, checksumResult.totalBytes, file.name)
    }

    private fun parseWithChecksums(
        headerBytes: ByteArray,
        checksumResult: StreamChecksumResult,
        fileSize: Long,
        fileName: String? = null
    ): RomIdentity {
        // 1. Attempt GBA detection
        if (headerBytes.size >= GbaRomParser.MIN_HEADER_SIZE) {
            val isGbaFixedByte = (headerBytes[0xB2].toInt() and 0xFF) == 0x96
            val gbaChecksumCalculated = GbaRomParser.calculateHeaderChecksum(headerBytes)
            val gbaChecksumStored = headerBytes[0xBD].toInt() and 0xFF
            val isGbaChecksumMatch = gbaChecksumCalculated == gbaChecksumStored

            if (isGbaFixedByte || isGbaChecksumMatch) {
                val gbaHeader = runCatching { GbaRomParser.parse(headerBytes) }.getOrNull()
                if (gbaHeader != null) {
                    return RomIdentity(
                        platform = "gba",
                        gameTitle = gbaHeader.title,
                        gameCode = gbaHeader.gameCode,
                        makerCode = gbaHeader.makerCode,
                        softwareVersion = gbaHeader.softwareVersion,
                        fileSize = fileSize,
                        checksums = checksumResult.checksums,
                        headerChecksumValid = gbaHeader.headerChecksumValid,
                        logoOrFixedValid = gbaHeader.fixedValueValid
                    )
                }
            }
        }

        // 2. Attempt GB/GBC detection
        if (headerBytes.size >= GbRomParser.MIN_HEADER_SIZE) {
            val gbHeader = runCatching { GbRomParser.parse(headerBytes) }.getOrNull()
            if (gbHeader != null && (gbHeader.logoValid || gbHeader.headerChecksumValid)) {
                return RomIdentity(
                    platform = gbHeader.platform,
                    gameTitle = gbHeader.title,
                    softwareVersion = gbHeader.maskRomVersion,
                    fileSize = fileSize,
                    checksums = checksumResult.checksums,
                    headerChecksumValid = gbHeader.headerChecksumValid,
                    logoOrFixedValid = gbHeader.logoValid,
                    cgbFlag = gbHeader.cgbFlag,
                    cartridgeType = gbHeader.cartridgeType,
                    mbcType = gbHeader.mbcType,
                    hasBattery = gbHeader.hasBattery
                )
            }
        }

        // 3. Attempt NES / Famicom detection
        if (NesRomParser.isNesRom(headerBytes)) {
            val nesHeader = runCatching { NesRomParser.parse(headerBytes) }.getOrNull()
            if (nesHeader != null) {
                return RomIdentity(
                    platform = "nes",
                    gameTitle = nesHeader.title,
                    fileSize = fileSize,
                    checksums = checksumResult.checksums,
                    headerChecksumValid = true,
                    logoOrFixedValid = true,
                    hasBattery = nesHeader.hasBattery,
                    nesHeader = nesHeader
                )
            }
        }

        // 4. Attempt Sega Mega Drive / Genesis / SMS / GG detection
        if (GenesisRomParser.isGenesisRom(headerBytes)) {
            val genesisHeader = runCatching { GenesisRomParser.parse(headerBytes) }.getOrNull()
            if (genesisHeader != null) {
                return RomIdentity(
                    platform = genesisHeader.platform,
                    gameTitle = genesisHeader.overseasTitle,
                    gameCode = genesisHeader.productNumber.takeIf { it.isNotBlank() },
                    fileSize = fileSize,
                    checksums = checksumResult.checksums,
                    headerChecksumValid = true,
                    logoOrFixedValid = genesisHeader.magicValid,
                    hasBattery = genesisHeader.hasSram,
                    genesisHeader = genesisHeader
                )
            }
        }

        // 5. Attempt Super Nintendo (SNES) detection
        if (SnesRomParser.isSnesRom(headerBytes)) {
            val snesHeader = runCatching { SnesRomParser.parse(headerBytes) }.getOrNull()
            if (snesHeader != null) {
                return RomIdentity(
                    platform = "snes",
                    gameTitle = snesHeader.title,
                    softwareVersion = snesHeader.version,
                    fileSize = fileSize,
                    checksums = checksumResult.checksums,
                    headerChecksumValid = snesHeader.checksumValid,
                    logoOrFixedValid = snesHeader.checksumValid,
                    cartridgeType = snesHeader.cartridgeType,
                    hasBattery = snesHeader.hasBattery,
                    snesHeader = snesHeader
                )
            }
        }

        // 6. Attempt Nintendo 64 (N64) detection
        if (N64RomParser.isN64Rom(headerBytes)) {
            val n64Header = runCatching { N64RomParser.parse(headerBytes) }.getOrNull()
            if (n64Header != null) {
                return RomIdentity(
                    platform = "n64",
                    gameTitle = n64Header.title,
                    gameCode = n64Header.gameCode,
                    softwareVersion = n64Header.version,
                    fileSize = fileSize,
                    checksums = checksumResult.checksums,
                    headerChecksumValid = true,
                    logoOrFixedValid = true,
                    hasBattery = true,
                    n64Header = n64Header
                )
            }
        }

        // 7. Attempt Nintendo DS (NDS) detection
        if (NdsRomParser.isNdsRom(headerBytes)) {
            val ndsHeader = runCatching { NdsRomParser.parse(headerBytes) }.getOrNull()
            if (ndsHeader != null) {
                return RomIdentity(
                    platform = "nds",
                    gameTitle = ndsHeader.title,
                    gameCode = ndsHeader.gameCode,
                    makerCode = ndsHeader.makerCode,
                    softwareVersion = ndsHeader.version,
                    fileSize = fileSize,
                    checksums = checksumResult.checksums,
                    headerChecksumValid = true,
                    logoOrFixedValid = true,
                    hasBattery = true,
                    ndsHeader = ndsHeader
                )
            }
        }

        // 8. Attempt PlayStation 1 (PS1) detection
        if (PsxRomParser.isPsxRom(headerBytes)) {
            val psxHeader = runCatching { PsxRomParser.parse(headerBytes) }.getOrNull()
            if (psxHeader != null) {
                return RomIdentity(
                    platform = "psx",
                    gameTitle = psxHeader.title,
                    gameCode = psxHeader.discId,
                    fileSize = fileSize,
                    checksums = checksumResult.checksums,
                    headerChecksumValid = true,
                    logoOrFixedValid = true,
                    hasBattery = true,
                    psxHeader = psxHeader
                )
            }
        }

        // 9. Attempt PC Engine (PCE) detection
        if (PceRomParser.isPceRom(headerBytes)) {
            val pceHeader = runCatching { PceRomParser.parse(headerBytes) }.getOrNull()
            if (pceHeader != null) {
                return RomIdentity(
                    platform = "pce",
                    gameTitle = pceHeader.title,
                    fileSize = fileSize,
                    checksums = checksumResult.checksums,
                    headerChecksumValid = true,
                    logoOrFixedValid = true,
                    hasBattery = false,
                    pceHeader = pceHeader
                )
            }
        }

        // 10. Extension-based fallback for unheadered homebrews, disc images, and arcade archives
        if (!fileName.isNullOrBlank()) {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val fallbackPlatform = when (ext) {
                // Game Boy / Color / Advance
                "gba", "agb" -> "gba"
                "gbc", "cgb" -> "gbc"
                "gb", "sgb" -> "gb"
                // Super Nintendo
                "sfc", "smc", "snes", "fig", "swc", "bs", "gd3", "gd7", "dx2" -> "snes"
                // NES / Famicom
                "nes", "fds", "unf", "unif", "fam" -> "nes"
                // Sega Genesis / Master System / Game Gear / SG-1000
                "md", "smd", "gen", "68k", "sgd" -> "genesis"
                "sms" -> "sms"
                "gg" -> "gg"
                "sg", "sc" -> "genesis"
                // PC Engine / TG-16 / SuperGrafx / PCE-CD
                "pce", "tg16", "sgx", "ccd", "toc" -> "pce"
                // Nintendo 64
                "n64", "z64", "v64", "u64", "ndd" -> "n64"
                // Nintendo DS / DSi
                "nds", "srl", "dsi", "ids" -> "nds"
                // PlayStation Portable / PS1 / Disc Images
                "cso", "prx", "elf" -> "psp"
                "pbp" -> "psp"
                "cue", "chd", "img", "mdf", "ecm" -> "psx"
                "iso" -> "psp"
                // Arcade / Neo Geo
                "neo" -> "arcade"
                "zip", "7z", "rar" -> "arcade"
                "bin" -> "genesis"
                else -> null
            }
            if (fallbackPlatform != null) {
                val cleanTitle = fileName.substringBeforeLast('.').replace('_', ' ').trim()
                return RomIdentity(
                    platform = fallbackPlatform,
                    gameTitle = cleanTitle.ifBlank { "Unknown Game" },
                    fileSize = fileSize,
                    checksums = checksumResult.checksums,
                    headerChecksumValid = true,
                    logoOrFixedValid = true,
                    hasBattery = true
                )
            }
        }

        throw InvalidRomException("Unrecognized ROM format: file size $fileSize bytes does not match any known retro console header specification.")
    }
}
