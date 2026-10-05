package com.retropack.packaging

/**
 * Validates and prepares adaptive icon layer assets for pure-file drawable injection (Step 8).
 */
object IconInjector {

    const val FOREGROUND_ENTRY = "res/drawable-nodpi/ic_launcher_foreground.png"
    const val FOREGROUND_ENTRY_V4 = "res/drawable-nodpi-v4/ic_launcher_foreground.png"
    const val BACKGROUND_ENTRY = "res/drawable-nodpi/ic_launcher_background.png"
    const val BACKGROUND_ENTRY_V4 = "res/drawable-nodpi-v4/ic_launcher_background.png"
    const val MIPMAP_XXHDPI_ENTRY = "res/mipmap-xxhdpi/ic_launcher.png"
    const val MIPMAP_XXHDPI_V4_ENTRY = "res/mipmap-xxhdpi-v4/ic_launcher.png"
    const val MIPMAP_XXHDPI_ROUND_ENTRY = "res/mipmap-xxhdpi/ic_launcher_round.png"
    const val MIPMAP_XXHDPI_ROUND_V4_ENTRY = "res/mipmap-xxhdpi-v4/ic_launcher_round.png"

    private val PNG_HEADER = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
    )

    /**
     * Asserts that [bytes] begins with standard 8-byte PNG file signature.
     */
    fun validatePng(bytes: ByteArray, description: String = "Icon") {
        require(bytes.size >= 8) {
            "$description size (${bytes.size} bytes) is too small to be a valid PNG."
        }
        for (i in PNG_HEADER.indices) {
            require(bytes[i] == PNG_HEADER[i]) {
                "$description does not contain a valid PNG file signature."
            }
        }
    }

    /**
     * Prepares adaptive icon entries to inject into the APK archive.
     * Populates both base drawable and API-qualified (e.g. -v4) paths, as well as fallback
     * mipmap raster icons so system package installers and older launchers display the game boxart.
     */
    fun prepareIconEntries(
        foregroundBytes: ByteArray,
        backgroundBytes: ByteArray? = null,
        fallbackRasterBytes: ByteArray? = null
    ): Map<String, ByteArray> {
        validatePng(foregroundBytes, "Foreground icon")
        val result = mutableMapOf<String, ByteArray>()
        result[FOREGROUND_ENTRY] = foregroundBytes
        result[FOREGROUND_ENTRY_V4] = foregroundBytes

        if (backgroundBytes != null) {
            validatePng(backgroundBytes, "Background icon")
            result[BACKGROUND_ENTRY] = backgroundBytes
            result[BACKGROUND_ENTRY_V4] = backgroundBytes
        }

        val rasterBytes = fallbackRasterBytes ?: foregroundBytes
        validatePng(rasterBytes, "Fallback raster icon")
        result[MIPMAP_XXHDPI_ENTRY] = rasterBytes
        result[MIPMAP_XXHDPI_V4_ENTRY] = rasterBytes
        result[MIPMAP_XXHDPI_ROUND_ENTRY] = rasterBytes
        result[MIPMAP_XXHDPI_ROUND_V4_ENTRY] = rasterBytes

        return result
    }
}
