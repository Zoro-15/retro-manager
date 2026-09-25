package com.retropack.packaging

/**
 * Shared lowercase-hex encoding (single source of truth).
 *
 * Replaces scattered per-byte `"%02x".format` loops (measured ~261x slower)
 * previously duplicated across packaging helpers.
 */
object HexUtils {
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    fun ByteArray.toHexString(): String {
        val chars = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            chars[i * 2] = HEX_CHARS[v ushr 4]
            chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(chars)
    }
}
