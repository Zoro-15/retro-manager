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
     */
    fun parse(bytes: ByteArray): RomIdentity {
        val checksumResult = StreamChecksum.calculate(bytes)
        return parseWithChecksums(bytes, checksumResult, bytes.size.toLong())
    }

    /**
     * Inspects a ROM file in a single streaming pass: checksums plus a bounded
     * header prefix up to 64 KB.
     */
    fun parse(file: File): RomIdentity {
        val (checksumResult, prefix) =
            StreamChecksum.calculateWithPrefix(file, MAX_HEADER_PROBE_SIZE)
        return parseWithChecksums(prefix, checksumResult, checksumResult.totalBytes)
    }

    private fun parseWithChecksums(
        headerBytes: ByteArray,
        checksumResult: StreamChecksumResult,
        fileSize: Long
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

        throw InvalidRomException("Unrecognized ROM format: file size $fileSize bytes does not match any known retro console header specification.")
    }
}
