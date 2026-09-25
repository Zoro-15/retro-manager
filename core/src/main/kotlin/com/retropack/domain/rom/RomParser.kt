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
        return parseWithChecksums(bytes, checksumResult)
    }

    /**
     * Inspects a ROM file and resolves its complete [RomIdentity].
     */
    fun parse(file: File): RomIdentity {
        val bytes = file.readBytes()
        val checksumResult = StreamChecksum.calculate(file)
        return parseWithChecksums(bytes, checksumResult)
    }

    private fun parseWithChecksums(bytes: ByteArray, checksumResult: StreamChecksumResult): RomIdentity {
        // Attempt GBA detection first
        if (bytes.size >= GbaRomParser.MIN_HEADER_SIZE) {
            val isGbaFixedByte = (bytes[0xB2].toInt() and 0xFF) == 0x96
            val gbaChecksumCalculated = GbaRomParser.calculateHeaderChecksum(bytes)
            val gbaChecksumStored = bytes[0xBD].toInt() and 0xFF
            val isGbaChecksumMatch = gbaChecksumCalculated == gbaChecksumStored

            if (isGbaFixedByte || isGbaChecksumMatch) {
                val gbaHeader = GbaRomParser.parse(bytes)
                return RomIdentity(
                    platform = "gba",
                    gameTitle = gbaHeader.title,
                    gameCode = gbaHeader.gameCode,
                    makerCode = gbaHeader.makerCode,
                    softwareVersion = gbaHeader.softwareVersion,
                    fileSize = bytes.size.toLong(),
                    checksums = checksumResult.checksums,
                    headerChecksumValid = gbaHeader.headerChecksumValid,
                    logoOrFixedValid = gbaHeader.fixedValueValid,
                    gbaHeader = gbaHeader
                )
            }
        }

        // Attempt GB/GBC detection
        if (bytes.size >= GbRomParser.MIN_HEADER_SIZE) {
            val gbHeader = GbRomParser.parse(bytes)
            if (gbHeader.logoValid || gbHeader.headerChecksumValid) {
                return RomIdentity(
                    platform = gbHeader.platform,
                    gameTitle = gbHeader.title,
                    softwareVersion = gbHeader.maskRomVersion,
                    fileSize = bytes.size.toLong(),
                    checksums = checksumResult.checksums,
                    headerChecksumValid = gbHeader.headerChecksumValid,
                    logoOrFixedValid = gbHeader.logoValid,
                    cgbFlag = gbHeader.cgbFlag,
                    cartridgeType = gbHeader.cartridgeType,
                    mbcType = gbHeader.mbcType,
                    hasBattery = gbHeader.hasBattery,
                    gbHeader = gbHeader
                )
            }
        }

        throw InvalidRomException("Unrecognized ROM format: file size ${bytes.size} bytes does not match GB/GBC or GBA header specifications.")
    }
}
