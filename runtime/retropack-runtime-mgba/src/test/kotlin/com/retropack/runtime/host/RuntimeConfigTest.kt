package com.retropack.runtime.host

import com.retropack.runtime.core.ScaleMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeConfigTest {

    private val canonicalJson = """
    {
      "${'$'}schema": "https://retropack.org/schemas/v1/runtime-config.json",
      "schema_version": 1,
      "game": {
        "id": "emerald-01H",
        "title": "Pokemon Emerald",
        "platform": "gba",
        "rom_sha256": "a91c37e4b2d1847f9e0a2b4c6d8e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e"
      },
      "runtime": {
        "core": "mgba",
        "audio_sample_rate": 44100,
        "audio_buffer_size": 2048,
        "video_scale_mode": "integer_fit"
      },
      "controls": {
        "touch_enabled": true,
        "touch_opacity": 0.65,
        "haptics": true
      },
      "storage": {
        "save_type": "battery_sram",
        "periodic_flush_interval_sec": 60
      },
      "provenance": {
        "manager_version": "0.1.0",
        "build_timestamp_utc": "2026-09-25T13:30:00Z"
      }
    }
    """.trimIndent()

    @Test
    fun `parses canonical retropack json correctly`() {
        val config = RuntimeConfig.fromJson(canonicalJson)

        assertEquals(1, config.schemaVersion)
        assertEquals("emerald-01H", config.game.id)
        assertEquals("Pokemon Emerald", config.game.title)
        assertEquals("gba", config.game.platform)
        assertEquals("a91c37e4b2d1847f9e0a2b4c6d8e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e", config.game.romSha256)

        assertEquals("mgba", config.runtime.core)
        assertEquals(44100, config.runtime.audioSampleRate)
        assertEquals(2048, config.runtime.audioBufferSize)
        assertEquals(ScaleMode.INTEGER_FIT, config.runtime.videoScaleMode)

        assertTrue(config.controls.touchEnabled)
        assertEquals(0.65f, config.controls.touchOpacity, 0.001f)
        assertTrue(config.controls.haptics)

        assertEquals("battery_sram", config.storage.saveType)
        assertEquals(60, config.storage.periodicFlushIntervalSec)

        assertEquals("0.1.0", config.provenance.managerVersion)
        assertEquals("2026-09-25T13:30:00Z", config.provenance.buildTimestampUtc)
    }

    @Test
    fun `parses empty json with safe default values`() {
        val config = RuntimeConfig.fromJson("{}")

        assertEquals(1, config.schemaVersion)
        assertEquals("game", config.game.id)
        assertEquals("Retro Game", config.game.title)
        assertEquals("gba", config.game.platform)
        assertEquals("", config.game.romSha256)
        assertEquals(ScaleMode.INTEGER_FIT, config.runtime.videoScaleMode)
        assertTrue(config.controls.touchEnabled)
        assertEquals(0.65f, config.controls.touchOpacity, 0.001f)
        assertTrue(config.controls.haptics)
        assertEquals("battery_sram", config.storage.saveType)
        assertEquals(60, config.storage.periodicFlushIntervalSec)
    }

    @Test
    fun `parses aspect fit scale mode`() {
        val json = """
        {
          "runtime": {
            "video_scale_mode": "aspect_fit"
          },
          "controls": {
            "touch_enabled": false,
            "touch_opacity": 0.4,
            "haptics": false
          }
        }
        """.trimIndent()

        val config = RuntimeConfig.fromJson(json)
        assertEquals(ScaleMode.ASPECT_FIT, config.runtime.videoScaleMode)
        assertFalse(config.controls.touchEnabled)
        assertEquals(0.4f, config.controls.touchOpacity, 0.001f)
        assertFalse(config.controls.haptics)
    }

    @Test
    fun `roundtrip serialization preserves configuration`() {
        val original = RuntimeConfig.fromJson(canonicalJson)
        val serialized = original.toJson()
        val reparsed = RuntimeConfig.fromJson(serialized)

        assertEquals(original.schemaVersion, reparsed.schemaVersion)
        assertEquals(original.game.id, reparsed.game.id)
        assertEquals(original.game.title, reparsed.game.title)
        assertEquals(original.game.platform, reparsed.game.platform)
        assertEquals(original.game.romSha256, reparsed.game.romSha256)
        assertEquals(original.runtime.videoScaleMode, reparsed.runtime.videoScaleMode)
        assertEquals(original.runtime.audioSampleRate, reparsed.runtime.audioSampleRate)
        assertEquals(original.controls.touchEnabled, reparsed.controls.touchEnabled)
        assertEquals(original.controls.touchOpacity, reparsed.controls.touchOpacity, 0.001f)
        assertEquals(original.controls.haptics, reparsed.controls.haptics)
        assertEquals(original.storage.saveType, reparsed.storage.saveType)
        assertEquals(original.storage.periodicFlushIntervalSec, reparsed.storage.periodicFlushIntervalSec)
    }
}
