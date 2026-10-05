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

    @Test
    fun `all 5 canonical 2D runtime descriptors have valid configurations`() {
        val descriptors = listOf(
            RuntimeDescriptor.MGBA_UNIFIED,
            RuntimeDescriptor.SNES9X_UNIFIED,
            RuntimeDescriptor.GENESIS_UNIFIED,
            RuntimeDescriptor.FCEUMM_UNIFIED,
            RuntimeDescriptor.PCE_UNIFIED
        )

        assertEquals(5, descriptors.size)
        for (desc in descriptors) {
            assertTrue(desc.id.isNotBlank())
            assertTrue(desc.version.isNotBlank())
            assertTrue(desc.supportedPlatforms.isNotEmpty())
            assertTrue(desc.supportedAbis.isNotEmpty())
            assertTrue(desc.romExtensions.isNotEmpty())
            assertEquals(1, desc.runtimeApi)
            assertEquals(26, desc.minSdk)
            assertEquals(35, desc.targetSdk)

            // Test toJson -> fromJson roundtrip for each descriptor
            val json = desc.toJson()
            val reparsed = RuntimeDescriptor.fromJson(json)
            assertEquals(desc.id, reparsed.id)
            assertEquals(desc.supportedPlatforms, reparsed.supportedPlatforms)

            // Test canonical coreId and coreLibName resolution
            assertTrue(desc.coreId.isNotBlank())
            assertTrue(desc.coreLibName.startsWith("libretro_") && desc.coreLibName.endsWith(".so"))
        }
    }

    @Test
    fun `canonical core IDs and library names map correctly for all descriptors`() {
        assertEquals("mgba", RuntimeDescriptor.MGBA_UNIFIED.coreId)
        assertEquals("libretro_mgba.so", RuntimeDescriptor.MGBA_UNIFIED.coreLibName)

        assertEquals("snes9x", RuntimeDescriptor.SNES9X_UNIFIED.coreId)
        assertEquals("libretro_snes9x.so", RuntimeDescriptor.SNES9X_UNIFIED.coreLibName)

        assertEquals("genesis_plus_gx", RuntimeDescriptor.GENESIS_UNIFIED.coreId)
        assertEquals("libretro_genesis_plus_gx.so", RuntimeDescriptor.GENESIS_UNIFIED.coreLibName)

        assertEquals("fceumm", RuntimeDescriptor.FCEUMM_UNIFIED.coreId)
        assertEquals("libretro_fceumm.so", RuntimeDescriptor.FCEUMM_UNIFIED.coreLibName)

        assertEquals("mednafen_pce_fast", RuntimeDescriptor.PCE_UNIFIED.coreId)
        assertEquals("libretro_mednafen_pce_fast.so", RuntimeDescriptor.PCE_UNIFIED.coreLibName)
    }

    @Test
    fun `fromCatalog creates valid descriptors from CoreCatalog`() {
        val descriptors = RuntimeDescriptor.fromCatalog(CoreCatalog.DEFAULT)
        assertEquals(5, descriptors.size)

        val mgba = descriptors.find { it.id == "mgba-unified" }
        assertEquals(listOf("gb", "gbc", "gba"), mgba?.supportedPlatforms)
        assertEquals("mgba", mgba?.coreId)
        assertEquals("libretro_mgba.so", mgba?.coreLibName)
    }
}
