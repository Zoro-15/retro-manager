package com.retropack.domain.runtime

import com.retropack.packaging.CoreLibraryInjector
import java.io.File

/**
 * Domain model representing the universal Libretro core catalog (`runtimes/cores.json`).
 *
 * Implements catalog specifications from Phase 2 / Phase 3 architecture:
 * - Single source of truth for all 10 supported Libretro core binaries, display names, and extensions.
 * - Bridges core catalog entries to runtime descriptors ([RuntimeDescriptor]).
 */
data class CoreCatalog(
    val version: Int = 1,
    val description: String = "Universal Libretro Core Catalog for RetroPack",
    val supportedAbis: List<String> = listOf("arm64-v8a", "x86_64"),
    val cores: List<CoreCatalogEntry> = emptyList()
) {

    /**
     * Resolves a [CoreCatalogEntry] by core ID, platform identifier, or core hint.
     */
    fun getCore(idOrPlatform: String): CoreCatalogEntry? {
        val normalized = idOrPlatform.lowercase().trim().removePrefix(".")
        val direct = cores.firstOrNull { it.id.equals(normalized, ignoreCase = true) }
        if (direct != null) return direct

        val resolvedCoreId = CoreLibraryInjector.resolveCoreId(normalized, normalized)
        val resolved = cores.firstOrNull { it.id.equals(resolvedCoreId, ignoreCase = true) }
        if (resolved != null) return resolved

        // Search by extension match
        val ext = if (normalized.startsWith(".")) normalized else ".$normalized"
        return cores.firstOrNull { it.extensions.any { e -> e.equals(ext, ignoreCase = true) } }
    }

    /**
     * Converts all catalog entries to [RuntimeDescriptor] instances.
     */
    fun toRuntimeDescriptors(): List<RuntimeDescriptor> {
        return cores.map { it.toRuntimeDescriptor() }
    }

    companion object {
        const val CATALOG_FILENAME = "cores.json"

        /**
         * Pinned default core catalog containing all 10 supported Libretro cores.
         */
        val DEFAULT = CoreCatalog(
            version = 1,
            description = "Universal Libretro Core Catalog for RetroPack (2D Golden Suite)",
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            cores = listOf(
                CoreCatalogEntry(
                    id = "mgba",
                    displayName = "mGBA",
                    system = "Game Boy Advance / Color / Classic",
                    libName = "libretro_mgba.so",
                    upstreamSlug = "mgba_libretro_android.so.zip",
                    extensions = listOf(".gba", ".gbc", ".gb", ".bin")
                ),
                CoreCatalogEntry(
                    id = "snes9x",
                    displayName = "Snes9x",
                    system = "Super Nintendo Entertainment System",
                    libName = "libretro_snes9x.so",
                    upstreamSlug = "snes9x_libretro_android.so.zip",
                    extensions = listOf(".sfc", ".smc")
                ),
                CoreCatalogEntry(
                    id = "genesis_plus_gx",
                    displayName = "Genesis Plus GX",
                    system = "Sega Genesis / Mega Drive / Master System / Game Gear",
                    libName = "libretro_genesis_plus_gx.so",
                    upstreamSlug = "genesis_plus_gx_libretro_android.so.zip",
                    extensions = listOf(".md", ".gen", ".smd", ".sms", ".gg", ".bin")
                ),
                CoreCatalogEntry(
                    id = "fceumm",
                    displayName = "FCEUmm",
                    system = "Nintendo Entertainment System / Famicom",
                    libName = "libretro_fceumm.so",
                    upstreamSlug = "fceumm_libretro_android.so.zip",
                    extensions = listOf(".nes", ".fds", ".unf")
                ),
                CoreCatalogEntry(
                    id = "mednafen_pce_fast",
                    displayName = "Beetle PCE Fast",
                    system = "PC Engine / TurboGrafx-16",
                    libName = "libretro_mednafen_pce_fast.so",
                    upstreamSlug = "mednafen_pce_fast_libretro_android.so.zip",
                    extensions = listOf(".pce", ".sgx", ".cue", ".ccd", ".chd")
                )
            )
        )

        /**
         * Loads and parses [CoreCatalog] from a given runtimes directory.
         */
        fun loadFromDirectory(runtimesDir: File): CoreCatalog {
            val candidate = if (runtimesDir.name == CATALOG_FILENAME) {
                runtimesDir
            } else {
                File(runtimesDir, CATALOG_FILENAME)
            }
            if (candidate.exists() && candidate.isFile) {
                return fromJson(candidate.readText(Charsets.UTF_8))
            }
            return DEFAULT
        }

        /**
         * Parses a JSON string representation of `cores.json` into a [CoreCatalog].
         */
        fun fromJson(jsonStr: String): CoreCatalog {
            val trimmed = jsonStr.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return DEFAULT
            }

            var version = 1
            var description = "Universal Libretro Core Catalog for RetroPack"
            var supportedAbis = listOf("arm64-v8a", "x86_64")
            val coresList = mutableListOf<CoreCatalogEntry>()

            // Extract top-level version
            val versionRegex = """"version"\s*:\s*(\d+)""".toRegex()
            versionRegex.find(trimmed)?.let {
                version = it.groupValues[1].toIntOrNull() ?: 1
            }

            // Extract description
            val descRegex = """"description"\s*:\s*"([^"]+)"""".toRegex()
            descRegex.find(trimmed)?.let {
                description = it.groupValues[1]
            }

            // Extract supported_abis
            val abisRegex = """"supported_abis"\s*:\s*\[([^\]]*)\]""".toRegex()
            abisRegex.find(trimmed)?.let { match ->
                val abisRaw = match.groupValues[1]
                val abis = abisRaw.split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotEmpty() }
                if (abis.isNotEmpty()) {
                    supportedAbis = abis
                }
            }

            // Extract core objects from "cores" array
            val coresArrayRegex = """"cores"\s*:\s*\[([\s\S]*?)\]\s*\}""".toRegex()
            val coresArrayContent = coresArrayRegex.find(trimmed)?.groupValues?.get(1) ?: ""

            if (coresArrayContent.isNotEmpty()) {
                val objectRegex = """\{[\s\S]*?\}""".toRegex()
                for (match in objectRegex.findAll(coresArrayContent)) {
                    val coreJson = match.value
                    val id = extractJsonField(coreJson, "id") ?: continue
                    val displayName = extractJsonField(coreJson, "display_name") ?: id
                    val system = extractJsonField(coreJson, "system") ?: ""
                    val libName = extractJsonField(coreJson, "lib_name") ?: "libretro_$id.so"
                    val upstreamSlug = extractJsonField(coreJson, "upstream_slug") ?: ""

                    val extRegex = """"extensions"\s*:\s*\[([^\]]*)\]""".toRegex()
                    val extensions = extRegex.find(coreJson)?.let { extMatch ->
                        extMatch.groupValues[1].split(",")
                            .map { it.trim().removeSurrounding("\"") }
                            .filter { it.isNotEmpty() }
                    } ?: emptyList()

                    coresList.add(
                        CoreCatalogEntry(
                            id = id,
                            displayName = displayName,
                            system = system,
                            libName = libName,
                            upstreamSlug = upstreamSlug,
                            extensions = extensions
                        )
                    )
                }
            }

            return if (coresList.isEmpty()) {
                DEFAULT
            } else {
                CoreCatalog(
                    version = version,
                    description = description,
                    supportedAbis = supportedAbis,
                    cores = coresList
                )
            }
        }

        private fun extractJsonField(json: String, fieldName: String): String? {
            val regex = """"$fieldName"\s*:\s*"([^"]+)"""".toRegex()
            return regex.find(json)?.groupValues?.get(1)
        }
    }
}

/**
 * Entry item representing a single Libretro core binary in [CoreCatalog].
 */
data class CoreCatalogEntry(
    val id: String,
    val displayName: String,
    val system: String,
    val libName: String,
    val upstreamSlug: String,
    val extensions: List<String>
) {
    /**
     * Converts this core catalog entry to a [RuntimeDescriptor] suitable for runtime registration.
     */
    fun toRuntimeDescriptor(): RuntimeDescriptor {
        val platforms = when (id) {
            "mgba" -> listOf("gb", "gbc", "gba")
            "snes9x" -> listOf("snes", "sfc", "smc")
            "genesis_plus_gx" -> listOf("genesis", "md", "smd", "gen", "sms", "gg")
            "fceumm" -> listOf("nes", "fds", "unf")
            "mednafen_pce_fast" -> listOf("pce", "tg16", "sgx")
            else -> listOf(id)
        }

        val runtimeId = when (id) {
            "mgba" -> "mgba-unified"
            "snes9x" -> "snes9x-unified"
            "genesis_plus_gx" -> "genesis-unified"
            "fceumm" -> "fceumm-unified"
            "mednafen_pce_fast" -> "pce-unified"
            else -> "$id-unified"
        }

        return RuntimeDescriptor(
            id = runtimeId,
            version = "1.0.0",
            runtimeApi = 1,
            supportedPlatforms = platforms,
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            minSdk = 26,
            targetSdk = 35,
            romExtensions = extensions,
            romAssetPath = "assets/game.rom",
            configAssetPath = "assets/retropack.json",
            configSchemaVersion = 1,
            capabilities = RuntimeCapabilities(
                saveStates = 4,
                rewind = true,
                fastForward = true,
                touchControls = true,
                physicalGamepad = true,
                biosOptional = true
            )
        )
    }
}
