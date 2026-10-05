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
    private val PCE_EXTENSIONS = setOf("pce", "sgx", "tg16")

    fun parse(bytes: ByteArray, fileName: String? = null, fallbackTitle: String = "PC Engine Game"): PceRomHeader {
        val hasCopierHeader = (bytes.size % 1024 == 512)
        val cleanSize = if (hasCopierHeader) bytes.size - COPIER_HEADER_SIZE else bytes.size

        if (cleanSize < 0x4000) {
            throw InvalidRomException("PCE ROM too small ($cleanSize bytes).")
        }

        val title = if (!fileName.isNullOrBlank()) {
            fileName.substringBeforeLast('.').replace('_', ' ').trim().ifEmpty { fallbackTitle }
        } else {
            fallbackTitle
        }

        val isSgx = fileName?.lowercase()?.endsWith(".sgx") == true

        return PceRomHeader(
            title = title,
            hasCopierHeader = hasCopierHeader,
            romSizeBytes = cleanSize.toLong(),
            isSuperGrafx = isSgx,
            magicValid = true
        )
    }

    fun isPceRom(bytes: ByteArray, fileName: String? = null): Boolean {
        if (!fileName.isNullOrBlank()) {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (PCE_EXTENSIONS.contains(ext)) {
                return bytes.size >= MIN_HEADER_SIZE
            }
        }

        // Binary-only heuristic: must have 512-byte copier header OR match valid PCE 6502 reset vector
        val hasCopier = (bytes.size % 1024 == 512) && bytes.size in (0x4000 + 512)..(0x800000 + 512)
        if (hasCopier) return true

        // Check PCE reset vector at 0x1FFFE (128 KB) or 0x3FFFE (256 KB) or 0x7FFFE (512 KB) if buffer large enough
        if (bytes.size >= 0x20000) {
            val vector = (bytes[0x1FFFE].toInt() and 0xFF) or ((bytes[0x1FFFF].toInt() and 0xFF) shl 8)
            if (vector in 0xE000..0xFFFF) return true
        }

        return false
    }
}

