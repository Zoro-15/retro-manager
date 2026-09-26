package com.retropack.domain.runtime

/**
 * Immutable domain model representing a standalone runtime descriptor (`runtime.json`).
 *
 * Implements specifications from:
 * - architechture.md Section 3: "The Runtime Abstraction: RuntimeTemplate"
 * - architechture.md Contract 3: Runtime Descriptor (lines 184-212)
 */
data class RuntimeDescriptor(
    val id: String = "mgba-unified",
    val version: String = "0.10.5",
    val runtimeApi: Int = 1,
    val supportedPlatforms: List<String> = listOf("gb", "gbc", "gba"),
    val supportedAbis: List<String> = listOf("arm64-v8a", "x86_64"),
    val minSdk: Int = 26,
    val targetSdk: Int = 35,
    val romExtensions: List<String> = listOf(".gb", ".gbc", ".gba"),
    val romAssetPath: String = "assets/game.rom",
    val configAssetPath: String = "assets/retropack.json",
    val configSchemaVersion: Int = 1,
    val capabilities: RuntimeCapabilities = RuntimeCapabilities(),
    val protectedEntries: Map<String, String> = emptyMap()
) {
    companion object {
        const val DESCRIPTOR_FILENAME = "runtime.json"

        val MGBA_UNIFIED = RuntimeDescriptor(
            id = "mgba-unified",
            version = "0.10.5",
            runtimeApi = 1,
            supportedPlatforms = listOf("gb", "gbc", "gba"),
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            minSdk = 26,
            targetSdk = 35,
            romExtensions = listOf(".gb", ".gbc", ".gba"),
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
            ),
            protectedEntries = mapOf(
                "classes.dex" to "sha256:b2533f8585723081e9d2bda0038eb8b0d550a7dbc9c4a52a0a66a2bf3901010f",
                "lib/arm64-v8a/libretropack-runtime.so" to "sha256:2253df2006ed765a84492382325b09e2ee2dfad72e943ab9d50fa3a31f09754b"
            )
        )

        val SNES9X_UNIFIED = RuntimeDescriptor(
            id = "snes9x-unified",
            version = "1.62.3",
            runtimeApi = 1,
            supportedPlatforms = listOf("snes", "sfc", "smc"),
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            minSdk = 26,
            targetSdk = 35,
            romExtensions = listOf(".sfc", ".smc", ".snes"),
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

        val GENESIS_UNIFIED = RuntimeDescriptor(
            id = "genesis-unified",
            version = "1.7.5",
            runtimeApi = 1,
            supportedPlatforms = listOf("genesis", "md", "smd", "gen", "sms", "gg"),
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            minSdk = 26,
            targetSdk = 35,
            romExtensions = listOf(".md", ".smd", ".gen", ".sms", ".gg", ".bin"),
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

        val FCEUMM_UNIFIED = RuntimeDescriptor(
            id = "fceumm-unified",
            version = "2.6.5",
            runtimeApi = 1,
            supportedPlatforms = listOf("nes", "fds", "unf"),
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            minSdk = 26,
            targetSdk = 35,
            romExtensions = listOf(".nes", ".fds", ".unf"),
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

        val PCE_UNIFIED = RuntimeDescriptor(
            id = "pce-unified",
            version = "1.31.0",
            runtimeApi = 1,
            supportedPlatforms = listOf("pce", "tg16", "sgx"),
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            minSdk = 26,
            targetSdk = 35,
            romExtensions = listOf(".pce", ".sgx", ".cue", ".iso"),
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

        val FBNEO_UNIFIED = RuntimeDescriptor(
            id = "fbneo-unified",
            version = "1.0.0.3",
            runtimeApi = 1,
            supportedPlatforms = listOf("arcade", "neogeo", "cps1", "cps2", "cps3", "fbneo"),
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            minSdk = 26,
            targetSdk = 35,
            romExtensions = listOf(".zip", ".7z"),
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

        val PCSX_UNIFIED = RuntimeDescriptor(
            id = "pcsx-unified",
            version = "1.0.0",
            runtimeApi = 1,
            supportedPlatforms = listOf("psx", "ps1", "ps"),
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            minSdk = 26,
            targetSdk = 35,
            romExtensions = listOf(".cue", ".iso", ".chd", ".pbp", ".bin"),
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

        val MUPEN64_UNIFIED = RuntimeDescriptor(
            id = "mupen64-unified",
            version = "2.5.0",
            runtimeApi = 1,
            supportedPlatforms = listOf("n64", "z64", "v64"),
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            minSdk = 26,
            targetSdk = 35,
            romExtensions = listOf(".z64", ".n64", ".v64"),
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

        val PPSSPP_UNIFIED = RuntimeDescriptor(
            id = "ppsspp-unified",
            version = "1.17.1",
            runtimeApi = 1,
            supportedPlatforms = listOf("psp"),
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            minSdk = 26,
            targetSdk = 35,
            romExtensions = listOf(".iso", ".cso", ".pbp"),
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

        val MELONDS_UNIFIED = RuntimeDescriptor(
            id = "melonds-unified",
            version = "0.9.5",
            runtimeApi = 1,
            supportedPlatforms = listOf("nds", "dsi"),
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            minSdk = 26,
            targetSdk = 35,
            romExtensions = listOf(".nds", ".srl", ".dsi"),
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

        /**
         * Parses a JSON string into a validated [RuntimeDescriptor].
         */
        fun fromJson(jsonStr: String): RuntimeDescriptor {
            val root = parseJsonObject(jsonStr.trim())

            val id = root["id"] ?: "mgba-unified"
            val version = root["version"] ?: "0.10.5"
            val runtimeApi = root["runtime_api"]?.toIntOrNull() ?: 1
            val supportedPlatforms = root["supported_platforms"]?.let { parseJsonArray(it) } ?: listOf("gb", "gbc", "gba")
            val supportedAbis = root["supported_abis"]?.let { parseJsonArray(it) } ?: listOf("arm64-v8a", "x86_64")
            val minSdk = root["min_sdk"]?.toIntOrNull() ?: 26
            val targetSdk = root["target_sdk"]?.toIntOrNull() ?: 35
            val romExtensions = root["rom_extensions"]?.let { parseJsonArray(it) } ?: listOf(".gb", ".gbc", ".gba")
            val romAssetPath = root["rom_asset_path"] ?: "assets/game.rom"
            val configAssetPath = root["config_asset_path"] ?: "assets/retropack.json"
            val configSchemaVersion = root["config_schema_version"]?.toIntOrNull() ?: 1

            val capsObj = root["capabilities"]?.let { parseJsonObject(it) } ?: emptyMap()
            val capabilities = RuntimeCapabilities(
                saveStates = capsObj["save_states"]?.toIntOrNull() ?: 4,
                rewind = capsObj["rewind"]?.toBooleanStrictOrNull() ?: true,
                fastForward = capsObj["fast_forward"]?.toBooleanStrictOrNull() ?: true,
                touchControls = capsObj["touch_controls"]?.toBooleanStrictOrNull() ?: true,
                physicalGamepad = capsObj["physical_gamepad"]?.toBooleanStrictOrNull() ?: true,
                biosOptional = capsObj["bios_optional"]?.toBooleanStrictOrNull() ?: true
            )

            val protectedEntries = root["protected_entries"]?.let { parseJsonObject(it) } ?: emptyMap()

            return RuntimeDescriptor(
                id = id,
                version = version,
                runtimeApi = runtimeApi,
                supportedPlatforms = supportedPlatforms,
                supportedAbis = supportedAbis,
                minSdk = minSdk,
                targetSdk = targetSdk,
                romExtensions = romExtensions,
                romAssetPath = romAssetPath,
                configAssetPath = configAssetPath,
                configSchemaVersion = configSchemaVersion,
                capabilities = capabilities,
                protectedEntries = protectedEntries
            )
        }

        private fun parseJsonObject(input: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            val trimmed = input.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return result
            }

            val body = trimmed.substring(1, trimmed.length - 1).trim()
            if (body.isEmpty()) return result

            var inString = false
            var escape = false
            var braceDepth = 0
            var bracketDepth = 0
            var currentKey = ""
            var readingKey = true
            val buffer = StringBuilder()

            for (ch in body) {
                if (escape) {
                    buffer.append(ch)
                    escape = false
                    continue
                }
                if (ch == '\\' && inString) {
                    escape = true
                    continue
                }
                if (ch == '"') {
                    inString = !inString
                    continue
                }
                if (inString) {
                    buffer.append(ch)
                    continue
                }

                if (ch == '{') braceDepth++
                if (ch == '}') braceDepth--
                if (ch == '[') bracketDepth++
                if (ch == ']') bracketDepth--

                if (braceDepth == 0 && bracketDepth == 0) {
                    if (ch == ':' && readingKey) {
                        currentKey = buffer.toString().trim().removeSurrounding("\"")
                        buffer.clear()
                        readingKey = false
                        continue
                    }
                    if (ch == ',') {
                        val value = buffer.toString().trim()
                        if (currentKey.isNotEmpty()) {
                            result[currentKey] = value.removeSurrounding("\"")
                        }
                        buffer.clear()
                        currentKey = ""
                        readingKey = true
                        continue
                    }
                }
                buffer.append(ch)
            }

            if (currentKey.isNotEmpty()) {
                val value = buffer.toString().trim()
                result[currentKey] = value.removeSurrounding("\"")
            }

            return result
        }

        private fun parseJsonArray(input: String): List<String> {
            val trimmed = input.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
                return emptyList()
            }
            val body = trimmed.substring(1, trimmed.length - 1).trim()
            if (body.isEmpty()) return emptyList()

            return body.split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        }
    }

    /**
     * Serializes this descriptor into a canonical JSON representation.
     */
    fun toJson(): String {
        val platformsJson = supportedPlatforms.joinToString(", ") { "\"$it\"" }
        val abisJson = supportedAbis.joinToString(", ") { "\"$it\"" }
        val extsJson = romExtensions.joinToString(", ") { "\"$it\"" }

        val protectedJson = protectedEntries.entries.joinToString(",\n    ") {
            "\"${it.key}\": \"${it.value}\""
        }

        return """
        {
          "id": "$id",
          "version": "$version",
          "runtime_api": $runtimeApi,
          "supported_platforms": [$platformsJson],
          "supported_abis": [$abisJson],
          "min_sdk": $minSdk,
          "target_sdk": $targetSdk,
          "rom_extensions": [$extsJson],
          "rom_asset_path": "$romAssetPath",
          "config_asset_path": "$configAssetPath",
          "config_schema_version": $configSchemaVersion,
          "capabilities": {
            "save_states": ${capabilities.saveStates},
            "rewind": ${capabilities.rewind},
            "fast_forward": ${capabilities.fastForward},
            "touch_controls": ${capabilities.touchControls},
            "physical_gamepad": ${capabilities.physicalGamepad},
            "bios_optional": ${capabilities.biosOptional}
          },
          "protected_entries": {
            $protectedJson
          }
        }
        """.trimIndent()
    }
}

data class RuntimeCapabilities(
    val saveStates: Int = 4,
    val rewind: Boolean = true,
    val fastForward: Boolean = true,
    val touchControls: Boolean = true,
    val physicalGamepad: Boolean = true,
    val biosOptional: Boolean = true
)
