package com.retropack.packaging

import java.io.File

/**
 * Prepares and validates standalone Libretro core native libraries (.so) for
 * uncompressed, 16 KB page-aligned injection into the game APK container (Step 6 / Step 10).
 *
 * Supported Target ABIs:
 * - `arm64-v8a` (Modern 64-bit ARM / Android 15+)
 * - `x86_64` (64-bit x86 Android Emulators & Chromebooks)
 */
object CoreLibraryInjector {

    val SUPPORTED_ABIS = listOf("arm64-v8a", "x86_64")
    private val ELF_MAGIC = byteArrayOf(0x7F.toByte(), 0x45.toByte(), 0x4C.toByte(), 0x46.toByte()) // \x7fELF

    /**
     * Resolves the canonical core ID from platform identifier and/or core hint.
     */
    fun resolveCoreId(platform: String, coreHint: String? = null): String {
        val normalizedPlatform = platform.lowercase().trim().removePrefix(".")
        val normalizedCore = coreHint?.lowercase()?.trim() ?: ""

        // 1. Check explicit core hint first
        when (normalizedCore) {
            "snes9x", "snes9x-unified", "libretro_snes9x.so" -> return "snes9x"
            "genesis", "genesis-plus-gx", "genesis_plus_gx", "genesis-unified", "libretro_genesis_plus_gx.so" -> return "genesis_plus_gx"
            "fceumm", "fceumm-unified", "libretro_fceumm.so" -> return "fceumm"
            "pce", "beetle-pce-fast", "mednafen_pce_fast", "pce-unified", "libretro_mednafen_pce_fast.so" -> return "mednafen_pce_fast"
            "mgba", "mgba-unified", "libretro_mgba.so" -> return "mgba"
        }

        // 2. Resolve by platform ID
        return when (normalizedPlatform) {
            "snes", "sfc", "smc" -> "snes9x"
            "genesis", "md", "smd", "gen", "sms", "gg" -> "genesis_plus_gx"
            "nes", "fds", "unf" -> "fceumm"
            "pce", "tg16", "sgx" -> "mednafen_pce_fast"
            "gba", "gbc", "gb" -> "mgba"
            else -> if (normalizedCore.isNotBlank()) normalizedCore else "mgba"
        }
    }

    /**
     * Resolves the canonical shared library filename (.so) from platform identifier and/or core hint.
     */
    fun resolveCoreLibName(platform: String, coreHint: String? = null): String {
        val coreId = resolveCoreId(platform, coreHint)
        return "libretro_$coreId.so"
    }

    /**
     * Constructs the target APK entry path for a given ABI and library filename.
     */
    fun getEntryPath(abi: String, libName: String): String {
        require(SUPPORTED_ABIS.contains(abi)) { "Unsupported target ABI: $abi. Expected one of $SUPPORTED_ABIS" }
        require(libName.endsWith(".so")) { "Library name must end with .so: $libName" }
        return "lib/$abi/$libName"
    }

    /**
     * Resolves core library byte entries from a staging directory (`runtimes/<coreId>/lib/<abi>/<libName>`).
     */
    fun prepareCoreEntriesFromDirectory(
        coreId: String,
        runtimesBaseDir: File,
        targetAbis: List<String> = SUPPORTED_ABIS
    ): Map<String, ByteArray> {
        val libName = "libretro_$coreId.so"
        val entries = mutableMapOf<String, ByteArray>()

        for (abi in targetAbis) {
            val candidateFile = File(runtimesBaseDir, "$coreId/lib/$abi/$libName")
            if (candidateFile.exists() && candidateFile.length() > 0L) {
                val bytes = candidateFile.readBytes()
                val entryPath = getEntryPath(abi, libName)
                sanitizeEntryPath(entryPath)
                entries[entryPath] = bytes
            }
        }
        return entries
    }

    /**
     * Validates and sanitizes a map of core library entries prior to APK injection.
     */
    fun sanitizeCoreEntries(entries: Map<String, ByteArray>): Map<String, ByteArray> {
        val sanitized = mutableMapOf<String, ByteArray>()
        for ((entryPath, bytes) in entries) {
            sanitizeEntryPath(entryPath)
            require(bytes.isNotEmpty()) { "Native library bytes for '$entryPath' cannot be empty" }
            require(isValidElfBinary(bytes)) { "Native library '$entryPath' does not begin with ELF magic header (0x7F 'ELF')" }
            sanitized[entryPath] = bytes
        }
        return sanitized
    }

    /**
     * Validates that the entry path stays securely within `lib/<abi>/`.
     */
    fun sanitizeEntryPath(path: String) {
        val normalized = path.replace('\\', '/')
        require(!normalized.contains("..")) {
            "Path traversal attack detected in native library path: $path"
        }
        require(normalized.startsWith("lib/")) {
            "Native library path must reside within lib/: $path"
        }
        require(normalized.endsWith(".so")) {
            "Native library path must end with .so: $path"
        }
        require(!normalized.startsWith("/")) {
            "Native library path cannot be absolute: $path"
        }
    }

    /**
     * Verifies that the raw byte payload starts with standard ELF magic bytes.
     */
    fun isValidElfBinary(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == ELF_MAGIC[0] &&
            bytes[1] == ELF_MAGIC[1] &&
            bytes[2] == ELF_MAGIC[2] &&
            bytes[3] == ELF_MAGIC[3]
    }
}
