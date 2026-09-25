package com.retropack.domain.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeDescriptorTest {

    private val canonicalJson = """
    {
      "id": "mgba-unified",
      "version": "0.10.5",
      "runtime_api": 1,
      "supported_platforms": ["gb", "gbc", "gba"],
      "supported_abis": ["arm64-v8a", "x86_64"],
      "min_sdk": 26,
      "target_sdk": 35,
      "rom_extensions": [".gb", ".gbc", ".gba"],
      "rom_asset_path": "assets/game.rom",
      "config_asset_path": "assets/retropack.json",
      "config_schema_version": 1,
      "capabilities": {
        "save_states": 4,
        "rewind": true,
        "fast_forward": true,
        "touch_controls": true,
        "physical_gamepad": true,
        "bios_optional": true
      },
      "protected_entries": {
        "classes.dex": "sha256:798a89a984f3e83964c19e681336ce6dacf4d948bd4253dfb895c55b82ef243d",
        "lib/arm64-v8a/libmgba.so": "sha256:79eca5e1ea4df26b67ea6c23839173de1ba465baf0713c3198c1d6f61a2d1bf1"
      }
    }
    """.trimIndent()

    @Test
    fun `parses canonical runtime descriptor correctly`() {
        val descriptor = RuntimeDescriptor.fromJson(canonicalJson)

        assertEquals("mgba-unified", descriptor.id)
        assertEquals("0.10.5", descriptor.version)
        assertEquals(1, descriptor.runtimeApi)
        assertEquals(listOf("gb", "gbc", "gba"), descriptor.supportedPlatforms)
        assertEquals(listOf("arm64-v8a", "x86_64"), descriptor.supportedAbis)
        assertEquals(26, descriptor.minSdk)
        assertEquals(35, descriptor.targetSdk)
        assertEquals(listOf(".gb", ".gbc", ".gba"), descriptor.romExtensions)
        assertEquals("assets/game.rom", descriptor.romAssetPath)
        assertEquals("assets/retropack.json", descriptor.configAssetPath)
        assertEquals(1, descriptor.configSchemaVersion)

        assertEquals(4, descriptor.capabilities.saveStates)
        assertTrue(descriptor.capabilities.rewind)
        assertTrue(descriptor.capabilities.fastForward)
        assertTrue(descriptor.capabilities.touchControls)
        assertTrue(descriptor.capabilities.physicalGamepad)
        assertTrue(descriptor.capabilities.biosOptional)

        assertEquals(
            "sha256:798a89a984f3e83964c19e681336ce6dacf4d948bd4253dfb895c55b82ef243d",
            descriptor.protectedEntries["classes.dex"]
        )
        assertEquals(
            "sha256:79eca5e1ea4df26b67ea6c23839173de1ba465baf0713c3198c1d6f61a2d1bf1",
            descriptor.protectedEntries["lib/arm64-v8a/libmgba.so"]
        )
    }

    @Test
    fun `roundtrip serialization preserves descriptor structure`() {
        val original = RuntimeDescriptor.fromJson(canonicalJson)
        val serialized = original.toJson()
        val reparsed = RuntimeDescriptor.fromJson(serialized)

        assertEquals(original.id, reparsed.id)
        assertEquals(original.version, reparsed.version)
        assertEquals(original.runtimeApi, reparsed.runtimeApi)
        assertEquals(original.supportedPlatforms, reparsed.supportedPlatforms)
        assertEquals(original.supportedAbis, reparsed.supportedAbis)
        assertEquals(original.minSdk, reparsed.minSdk)
        assertEquals(original.targetSdk, reparsed.targetSdk)
        assertEquals(original.romExtensions, reparsed.romExtensions)
        assertEquals(original.capabilities, reparsed.capabilities)
        assertEquals(original.protectedEntries, reparsed.protectedEntries)
    }
}
