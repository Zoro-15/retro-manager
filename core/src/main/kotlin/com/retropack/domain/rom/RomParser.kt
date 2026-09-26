package com.retropack.domain.rom

import com.retropack.domain.model.RomIdentity
import java.io.File
import java.io.InputStream

/**
 * Unified ROM inspection facade. Automatically identifies platform ("gb", "gbc", "gba"),
 * parses system-specific headers, and runs streaming checksum calculation.
 */
object RomParser {

    /**
     * Inspects a ROM byte array and resolves its complete [RomIdentity].
     */
    fun parse(bytes: ByteArray): RomIdentity {
        val checksumResult = StreamChecksum.calculate(bytes)
        return parseWithChecksums(bytes, checksumResult, bytes.size.toLong())
    }

    /**
     * Inspects a ROM file in a single streaming pass: checksums plus a bounded
     * header prefix (GB headers need 0x150 bytes; GBA needs 0xC0). The old
     * implementation read the whole file into heap and then hashed it again.
     */
    fun parse(file: File): RomIdentity {
        val (checksumResult, prefix) =
            StreamChecksum.calculateWithPrefix(file, GbRomParser.MIN_HEADER_SIZE)
        return parseWithChecksums(prefix, checksumResult, checksumResult.totalBytes)
    }

    private fun parseWithChecksums(
        headerBytes: ByteArray,
        checksumResult: StreamChecksumResult,
        fileSize: Long
    ): RomIdentity {
        // Attempt GBA detection first
        if (headerBytes.size >= GbaRomParser.MIN_HEADER_SIZE) {
            val isGbaFixedByte = (headerBytes[0xB2].toInt() and 0xFF) == 0x96
            val gbaChecksumCalculated = GbaRomParser.calculateHeaderChecksum(headerBytes)
            val gbaChecksumStored = headerBytes[0xBD].toInt() and 0xFF
            val isGbaChecksumMatch = gbaChecksumCalculated == gbaChecksumStored

            if (isGbaFixedByte || isGbaChecksumMatch) {
                // Fixed byte alone is not sufficient proof (issue #18): a bad
                // fixed byte with matching checksum must fall through to GB
                // instead of throwing out of the strict GBA parser.
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

        // Attempt GB/GBC detection
        if (headerBytes.size >= GbRomParser.MIN_HEADER_SIZE) {
            val gbHeader = GbRomParser.parse(headerBytes)
            if (gbHeader.logoValid || gbHeader.headerChecksumValid) {
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

        throw InvalidRomException("Unrecognized ROM format: file size $fileSize bytes does not match GB/GBC or GBA header specifications.")
    }
}
