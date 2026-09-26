package com.retropack.domain.model

/**
 * Immutable declarative build payload consumed by the BuildEngine.
 * Corresponds directly to https://retropack.org/schemas/v1/build-request.json
 * defined in architechture.md.
 */
data class BuildRequest(
    val identity: GameIdentity,
    val content: ContentPayload,
    val runtime: RuntimeConfigPayload = RuntimeConfigPayload(),
    val controls: ControlsPayload = ControlsPayload(),
    val storage: StoragePayload = StoragePayload()
)

data class GameIdentity(
    val gameId: String,
    val gameTitle: String,
    val packageName: String,
    val versionCode: Int = 1,
    val versionName: String = "1.0.0"
)

data class ContentPayload(
    val sourceRom: String,
    val platform: String, // "gb", "gbc", "gba"
    val header: RomHeaderData? = null
)

data class ChecksumRecords(
    val crc32: String,
    val md5: String,
    val sha1: String,
    val sha256: String
)

data class RomHeaderData(
    val gameCode: String? = null,
    val makerCode: String? = null,
    val cgbFlag: Int? = null,
    val cartridgeType: Int? = null
)

data class RuntimeConfigPayload(
    val templateId: String = "mgba-unified",
    val audio: AudioSettings = AudioSettings(),
    val video: VideoSettings = VideoSettings()
)

data class AudioSettings(
    val sampleRate: Int = 44100,
    val bufferSize: Int = 2048
)

data class VideoSettings(
    val scaleMode: String = "integer_fit" // "integer_fit", "aspect_fit"
)

data class ControlsPayload(
    val touch: TouchControlsSettings = TouchControlsSettings()
)

data class TouchControlsSettings(
    val enabled: Boolean = true,
    val opacity: Float = 0.65f,
    val haptics: Boolean = true
)

data class StoragePayload(
    val saveType: String = "battery_sram",
    val periodicFlushIntervalSec: Int = 60
)
