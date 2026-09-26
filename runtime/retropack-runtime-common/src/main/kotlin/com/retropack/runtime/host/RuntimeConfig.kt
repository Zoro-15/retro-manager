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
            val gameConfig = GameConfig(
                id = gameObject["id"] ?: "game",
                title = gameObject["title"] ?: "Retro Game",
                platform = gameObject["platform"] ?: "gba",
                romSha256 = gameObject["rom_sha256"] ?: ""
            )

            val runtimeObject = root["runtime"]?.let { parseJsonObject(it) } ?: emptyMap()
            val videoScaleModeStr = runtimeObject["video_scale_mode"]
            val scaleMode = ScaleMode.fromConfig(videoScaleModeStr)
            val engineConfig = EngineConfig(
                core = runtimeObject["core"] ?: "mgba",
                audioSampleRate = runtimeObject["audio_sample_rate"]?.toIntOrNull() ?: 44100,
                audioBufferSize = runtimeObject["audio_buffer_size"]?.toIntOrNull() ?: 2048,
                videoScaleMode = scaleMode
            )

            val controlsObject = root["controls"]?.let { parseJsonObject(it) } ?: emptyMap()
            val controlsConfig = ControlsConfig(
                touchEnabled = controlsObject["touch_enabled"]?.toBooleanStrictOrNull() ?: true,
                touchOpacity = controlsObject["touch_opacity"]?.toFloatOrNull() ?: 0.65f,
                haptics = controlsObject["haptics"]?.toBooleanStrictOrNull() ?: true
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

        return """
        {
          "${'$'}schema": "https://retropack.org/schemas/v1/runtime-config.json",
          "schema_version": $schemaVersion,
          "game": {
            "id": "${escapeJson(game.id)}",
            "title": "${escapeJson(game.title)}",
            "platform": "${escapeJson(game.platform)}",
            "rom_sha256": "${escapeJson(game.romSha256)}"
          },
          "runtime": {
            "core": "${escapeJson(runtime.core)}",
            "audio_sample_rate": ${runtime.audioSampleRate},
            "audio_buffer_size": ${runtime.audioBufferSize},
            "video_scale_mode": "$scaleModeStr"
          },
          "controls": {
            "touch_enabled": ${controls.touchEnabled},
            "touch_opacity": ${controls.touchOpacity},
            "haptics": ${controls.haptics}
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

data class GameConfig(
    val id: String = "game",
    val title: String = "Retro Game",
    val platform: String = "gba",
    val romSha256: String = ""
)

data class EngineConfig(
    val core: String = "mgba",
    val audioSampleRate: Int = 44100,
    val audioBufferSize: Int = 2048,
    val videoScaleMode: ScaleMode = ScaleMode.INTEGER_FIT
)

data class ControlsConfig(
    val touchEnabled: Boolean = true,
    val touchOpacity: Float = 0.65f,
    val haptics: Boolean = true
)

data class StorageConfig(
    val saveType: String = "battery_sram",
    val periodicFlushIntervalSec: Int = 60
)

data class ProvenanceConfig(
    val managerVersion: String = "0.1.0",
    val buildTimestampUtc: String = ""
)
