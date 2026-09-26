package com.retropack.runtime.host

import com.retropack.runtime.core.ScaleMode

/**
 * Immutable domain model representing the injected standalone runtime configuration (`assets/retropack.json`).
 *
 * Implements specifications from:
 * - architechture.md Contract 2: Standalone Injected Config (lines 150-182).
 * - masterplan.md Section 5.1: Architecture & Bootstrap.
 */
data class RuntimeConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val game: GameConfig = GameConfig(),
    val runtime: EngineConfig = EngineConfig(),
    val controls: ControlsConfig = ControlsConfig(),
    val storage: StorageConfig = StorageConfig(),
    val provenance: ProvenanceConfig = ProvenanceConfig()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val ASSET_PATH = "retropack.json"

        val DEFAULT = RuntimeConfig()

        /**
         * Parses a JSON string into a validated [RuntimeConfig] instance.
         * Resilient to missing optional fields, using canonical defaults.
         */
        fun fromJson(jsonStr: String): RuntimeConfig {
            val root = parseJsonObject(jsonStr.trim())

            val schemaVersion = root["schema_version"]?.toIntOrNull() ?: CURRENT_SCHEMA_VERSION

            val gameObject = root["game"]?.let { parseJsonObject(it) } ?: emptyMap()
            val discsJsonArray = gameObject["discs"]
            val discList = mutableListOf<DiscEntry>()
            if (!discsJsonArray.isNullOrBlank() && discsJsonArray.startsWith("[") && discsJsonArray.endsWith("]")) {
                val arrayContent = discsJsonArray.substring(1, discsJsonArray.length - 1).trim()
                if (arrayContent.isNotEmpty()) {
                    val elements = splitJsonArrayElements(arrayContent)
                    for (elem in elements) {
                        val discMap = parseJsonObject(elem)
                        val idx = discMap["index"]?.toIntOrNull() ?: discList.size
                        val label = discMap["label"] ?: "Disc ${idx + 1}"
                        val path = discMap["path"] ?: "discs/disc_$idx.chd"
                        val sha = discMap["sha256"] ?: ""
                        discList.add(DiscEntry(index = idx, label = label, path = path, sha256 = sha))
                    }
                }
            }

            val gameConfig = GameConfig(
                id = gameObject["id"] ?: "game",
                title = gameObject["title"] ?: "Retro Game",
                platform = gameObject["platform"] ?: "gba",
                romSha256 = gameObject["rom_sha256"] ?: "",
                discs = discList,
                m3uPath = gameObject["m3u_path"] ?: ""
            )

            val runtimeObject = root["runtime"]?.let { parseJsonObject(it) } ?: emptyMap()
            val videoScaleModeStr = runtimeObject["video_scale_mode"]
            val scaleMode = ScaleMode.fromConfig(videoScaleModeStr)
            val shaderMode = com.retropack.domain.model.ShaderMode.fromId(runtimeObject["video_shader_mode"])
            val bezelMode = com.retropack.domain.model.BezelMode.fromId(runtimeObject["video_bezel_mode"])
            val engineConfig = EngineConfig(
                core = runtimeObject["core"] ?: "mgba",
                audioSampleRate = runtimeObject["audio_sample_rate"]?.toIntOrNull() ?: 44100,
                audioBufferSize = runtimeObject["audio_buffer_size"]?.toIntOrNull() ?: 2048,
                videoScaleMode = scaleMode,
                videoShaderMode = shaderMode,
                videoBezelMode = bezelMode
            )

            val controlsObject = root["controls"]?.let { parseJsonObject(it) } ?: emptyMap()
            val gamepadObject = controlsObject["gamepad"]?.let { parseJsonObject(it) }
            val gamepadConfig = gamepadObject?.let { gp ->
                val bindingsStr = gp["button_bindings"]?.let { parseJsonObject(it) } ?: emptyMap()
                val bindingsMap = bindingsStr.mapNotNull { (k, v) ->
                    v.toIntOrNull()?.let { k to it }
                }.toMap()
                GamepadConfig(
                    profileName = gp["profile_name"] ?: "Default Profile",
                    deadzonePercent = gp["deadzone_percent"]?.toIntOrNull() ?: 15,
                    triggerThresholdPercent = gp["trigger_threshold_percent"]?.toIntOrNull() ?: 50,
                    buttonBindings = bindingsMap
                )
            }

            val controlsConfig = ControlsConfig(
                touchEnabled = controlsObject["touch_enabled"]?.toBooleanStrictOrNull() ?: true,
                touchOpacity = controlsObject["touch_opacity"]?.toFloatOrNull() ?: 0.65f,
                haptics = controlsObject["haptics"]?.toBooleanStrictOrNull() ?: true,
                gamepad = gamepadConfig
            )

            val storageObject = root["storage"]?.let { parseJsonObject(it) } ?: emptyMap()
            val storageConfig = StorageConfig(
                saveType = storageObject["save_type"] ?: "battery_sram",
                periodicFlushIntervalSec = storageObject["periodic_flush_interval_sec"]?.toIntOrNull() ?: 60
            )

            val provenanceObject = root["provenance"]?.let { parseJsonObject(it) } ?: emptyMap()
            val provenanceConfig = ProvenanceConfig(
                managerVersion = provenanceObject["manager_version"] ?: "0.1.0",
                buildTimestampUtc = provenanceObject["build_timestamp_utc"] ?: ""
            )

            return RuntimeConfig(
                schemaVersion = schemaVersion,
                game = gameConfig,
                runtime = engineConfig,
                controls = controlsConfig,
                storage = storageConfig,
                provenance = provenanceConfig
            )
        }

        private fun splitJsonArrayElements(arrayBody: String): List<String> {
            val list = mutableListOf<String>()
            var depth = 0
            var inString = false
            var escape = false
            val buffer = StringBuilder()

            for (ch in arrayBody) {
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
                    buffer.append(ch)
                    continue
                }
                if (!inString) {
                    if (ch == '{') depth++
                    if (ch == '}') depth--
                    if (ch == ',' && depth == 0) {
                        val item = buffer.toString().trim()
                        if (item.isNotEmpty()) list.add(item)
                        buffer.clear()
                        continue
                    }
                }
                buffer.append(ch)
            }
            val lastItem = buffer.toString().trim()
            if (lastItem.isNotEmpty()) list.add(lastItem)
            return list
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
                        currentKey = buffer.toString().trim()
                        buffer.clear()
                        readingKey = false
                        continue
                    }
                    if (ch == ',') {
                        val value = buffer.toString().trim()
                        if (currentKey.isNotEmpty()) {
                            result[currentKey] = value
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
                result[currentKey] = buffer.toString().trim()
            }

            return result
        }
    }

    /**
     * Serializes this [RuntimeConfig] to a formatted canonical JSON string.
     */
    fun toJson(): String {
        val scaleModeStr = runtime.videoScaleMode.configValue
        val discsJson = if (game.discs.isNotEmpty()) {
            val items = game.discs.joinToString(",\n") { disc ->
                """        { "index": ${disc.index}, "label": "${escapeJson(disc.label)}", "path": "${escapeJson(disc.path)}", "sha256": "${escapeJson(disc.sha256)}" }"""
            }
            """,
            "discs": [
$items
            ],
            "m3u_path": "${escapeJson(game.m3uPath)}""""
        } else ""

        val gamepadJson = if (controls.gamepad != null) {
            val gp = controls.gamepad
            val bindings = gp.buttonBindings.entries.joinToString(", ") { """"${it.key}": ${it.value}""" }
            """,
            "gamepad": {
              "profile_name": "${escapeJson(gp.profileName)}",
              "deadzone_percent": ${gp.deadzonePercent},
              "trigger_threshold_percent": ${gp.triggerThresholdPercent},
              "button_bindings": { $bindings }
            }"""
        } else ""

        return """
        {
          "${'$'}schema": "https://retropack.org/schemas/v1/runtime-config.json",
          "schema_version": $schemaVersion,
          "game": {
            "id": "${escapeJson(game.id)}",
            "title": "${escapeJson(game.title)}",
            "platform": "${escapeJson(game.platform)}",
            "rom_sha256": "${escapeJson(game.romSha256)}"$discsJson
          },
          "runtime": {
            "core": "${escapeJson(runtime.core)}",
            "audio_sample_rate": ${runtime.audioSampleRate},
            "audio_buffer_size": ${runtime.audioBufferSize},
            "video_scale_mode": "$scaleModeStr",
            "video_shader_mode": "${escapeJson(runtime.videoShaderMode.id)}",
            "video_bezel_mode": "${escapeJson(runtime.videoBezelMode.id)}"
          },
          "controls": {
            "touch_enabled": ${controls.touchEnabled},
            "touch_opacity": ${controls.touchOpacity},
            "haptics": ${controls.haptics}$gamepadJson
          },
          "storage": {
            "save_type": "${escapeJson(storage.saveType)}",
            "periodic_flush_interval_sec": ${storage.periodicFlushIntervalSec}
          },
          "provenance": {
            "manager_version": "${escapeJson(provenance.managerVersion)}",
            "build_timestamp_utc": "${escapeJson(provenance.buildTimestampUtc)}"
          }
        }
        """.trimIndent()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

data class DiscEntry(
    val index: Int = 0,
    val label: String = "Disc 1",
    val path: String = "game.rom",
    val sha256: String = ""
)

data class GameConfig(
    val id: String = "game",
    val title: String = "Retro Game",
    val platform: String = "gba",
    val romSha256: String = "",
    val discs: List<DiscEntry> = emptyList(),
    val m3uPath: String = ""
)

data class EngineConfig(
    val core: String = "mgba",
    val audioSampleRate: Int = 44100,
    val audioBufferSize: Int = 2048,
    val videoScaleMode: ScaleMode = ScaleMode.INTEGER_FIT,
    val videoShaderMode: com.retropack.domain.model.ShaderMode = com.retropack.domain.model.ShaderMode.NONE,
    val videoBezelMode: com.retropack.domain.model.BezelMode = com.retropack.domain.model.BezelMode.AUTO
)

data class GamepadConfig(
    val profileName: String = "Default Profile",
    val deadzonePercent: Int = 15,
    val triggerThresholdPercent: Int = 50,
    val buttonBindings: Map<String, Int> = emptyMap()
)

data class ControlsConfig(
    val touchEnabled: Boolean = true,
    val touchOpacity: Float = 0.65f,
    val haptics: Boolean = true,
    val gamepad: GamepadConfig? = null
)

data class StorageConfig(
    val saveType: String = "battery_sram",
    val periodicFlushIntervalSec: Int = 60
)

data class ProvenanceConfig(
    val managerVersion: String = "0.1.0",
    val buildTimestampUtc: String = ""
)
