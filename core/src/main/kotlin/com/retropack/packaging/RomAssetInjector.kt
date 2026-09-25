package com.retropack.packaging

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Prepares and sanitizes embedded game assets for injection into the APK container (Step 6).
 */
object RomAssetInjector {

    const val ROM_ENTRY = "assets/game.rom"
    const val CONFIG_ENTRY = "assets/retropack.json"

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
        return md.digest(bytes).joinToString("") { "%02x".format(it).lowercase(Locale.ROOT) }
    }
}
