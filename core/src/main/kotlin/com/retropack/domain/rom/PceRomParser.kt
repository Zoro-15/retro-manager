package com.retropack.domain.rom

/**
 * Parsed PC Engine / TurboGrafx-16 cartridge header.
 */
data class PceRomHeader(
    val title: String,
    val hasCopierHeader: Boolean,
    val romSizeBytes: Long,
    val isSuperGrafx: Boolean,
    val magicValid: Boolean
)

/**
 * Low-level binary parser for PC Engine / TurboGrafx-16 (.pce, .sgx) ROM images.
 */
object PceRomParser {

    const val COPIER_HEADER_SIZE = 512
    const val MIN_HEADER_SIZE = 512

    fun parse(bytes: ByteArray, fallbackTitle: String = "PC Engine Game"): PceRomHeader {
        val hasCopierHeader = (bytes.size % 1024 == 512)
        val cleanSize = if (hasCopierHeader) bytes.size - COPIER_HEADER_SIZE else bytes.size

        if (cleanSize < 0x4000) {
            throw InvalidRomException("PCE ROM too small ($cleanSize bytes).")
        }

        return PceRomHeader(
            title = fallbackTitle,
            hasCopierHeader = hasCopierHeader,
            romSizeBytes = cleanSize.toLong(),
            isSuperGrafx = false,
            magicValid = true
        )
    }

    fun isPceRom(bytes: ByteArray): Boolean {
        val hasCopier = (bytes.size % 1024 == 512)
        val cleanSize = if (hasCopier) bytes.size - COPIER_HEADER_SIZE else bytes.size
        return cleanSize in 0x4000..0x800000 && (cleanSize and (cleanSize - 1)) == 0 // power of 2 or typical size
    }
}
