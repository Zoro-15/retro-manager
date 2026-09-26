package com.retropack.packaging

import com.retropack.packaging.HexUtils.toHexString
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Prepares and sanitizes embedded game assets for injection into the APK container (Step 6).
 */
object RomAssetInjector {

    const val ROM_ENTRY = "assets/game.rom"
    const val CONFIG_ENTRY = "assets/retropack.json"
    const val DISCS_PREFIX = "assets/discs/"
    const val M3U_ENTRY = "assets/game.m3u"

    /**
     * Prepares the asset entries map containing `assets/game.rom` and `assets/retropack.json`.
     */
    fun prepareAssetEntries(
        romBytes: ByteArray,
        configJson: String
    ): Map<String, ByteArray> {
        require(romBytes.isNotEmpty()) { "ROM bytes cannot be empty" }
        require(configJson.isNotBlank()) { "Runtime config JSON cannot be blank" }

        sanitizeEntryPath(ROM_ENTRY)
        sanitizeEntryPath(CONFIG_ENTRY)

        return mapOf(
            ROM_ENTRY to romBytes,
            CONFIG_ENTRY to configJson.toByteArray(StandardCharsets.UTF_8)
        )
    }

    /**
     * Prepares the asset entries map with multi-disc images and optional `.m3u` playlist.
     */
    fun prepareMultiDiscAssetEntries(
        discEntries: Map<String, ByteArray>,
        configJson: String,
        m3uContent: String? = null
    ): Map<String, ByteArray> {
        require(discEntries.isNotEmpty()) { "Disc entries cannot be empty" }
        require(configJson.isNotBlank()) { "Runtime config JSON cannot be blank" }

        val map = mutableMapOf<String, ByteArray>()
        map[CONFIG_ENTRY] = configJson.toByteArray(StandardCharsets.UTF_8)
        sanitizeEntryPath(CONFIG_ENTRY)

        for ((entryPath, bytes) in discEntries) {
            sanitizeEntryPath(entryPath)
            map[entryPath] = bytes
        }

        if (!m3uContent.isNullOrBlank()) {
            sanitizeEntryPath(M3U_ENTRY)
            map[M3U_ENTRY] = m3uContent.toByteArray(StandardCharsets.UTF_8)
        }

        return map
    }

    /**
     * Path-traversal sanitization asserting that [path] does not contain traversal sequences
     * and stays within `assets/`.
     */
    fun sanitizeEntryPath(path: String) {
        val normalized = path.replace('\\', '/')
        require(!normalized.contains("..")) {
            "Path traversal attack detected in asset path: $path"
        }
        require(normalized.startsWith("assets/")) {
            "Asset path must reside within assets/: $path"
        }
        require(!normalized.startsWith("/")) {
            "Asset path cannot be absolute: $path"
        }
    }

    fun computeSha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).toHexString()
    }
}
