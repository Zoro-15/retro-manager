package com.retropack.domain.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CoreCatalogTest {

    @Test
    fun `default catalog contains all 10 canonical Libretro cores`() {
        val catalog = CoreCatalog.DEFAULT

        assertEquals(1, catalog.version)
        assertEquals(listOf("arm64-v8a", "x86_64"), catalog.supportedAbis)
        assertEquals(10, catalog.cores.size)

        val expectedIds = listOf(
            "mgba",
            "snes9x",
            "genesis_plus_gx",
            "fceumm",
            "mednafen_pce_fast",
            "pcsx_rearmed",
            "melonds",
            "mupen64plus_next",
            "fbneo",
            "ppsspp"
        )

        for (id in expectedIds) {
            val core = catalog.getCore(id)
            assertNotNull(core, "Core $id must be present in default catalog")
            assertEquals("libretro_$id.so", core!!.libName)
            assertTrue(core.extensions.isNotEmpty(), "Core $id must have registered extensions")
            assertTrue(core.displayName.isNotBlank())
            assertTrue(core.system.isNotBlank())
            assertTrue(core.upstreamSlug.endsWith(".zip") || core.upstreamSlug.endsWith(".so"))
        }
    }

    @Test
    fun `parses runtimes cores_json file correctly`() {
        val coresFile = sequenceOf(
            File("runtimes/cores.json"),
            File("../runtimes/cores.json")
        ).firstOrNull { it.isFile }

        if (coresFile != null) {
            val catalog = CoreCatalog.loadFromDirectory(coresFile.parentFile)
            assertEquals(10, catalog.cores.size)
            assertEquals(listOf("arm64-v8a", "x86_64"), catalog.supportedAbis)

            val mgba = catalog.getCore("mgba")
            assertNotNull(mgba)
            assertEquals("mGBA", mgba!!.displayName)
            assertTrue(mgba.extensions.contains(".gba"))
            assertTrue(mgba.extensions.contains(".gb"))
        }
    }

    @Test
    fun `getCore resolves by core ID, platform, alias, and extension`() {
        val catalog = CoreCatalog.DEFAULT

        // By exact core ID
        assertEquals("mgba", catalog.getCore("mgba")?.id)
        assertEquals("snes9x", catalog.getCore("snes9x")?.id)
        assertEquals("genesis_plus_gx", catalog.getCore("genesis_plus_gx")?.id)

        // By platform identifier
        assertEquals("mgba", catalog.getCore("gba")?.id)
        assertEquals("snes9x", catalog.getCore("snes")?.id)
        assertEquals("genesis_plus_gx", catalog.getCore("genesis")?.id)
        assertEquals("fceumm", catalog.getCore("nes")?.id)
        assertEquals("mednafen_pce_fast", catalog.getCore("pce")?.id)
        assertEquals("mednafen_pce_fast", catalog.getCore("tg16")?.id)
        assertEquals("pcsx_rearmed", catalog.getCore("psx")?.id)
        assertEquals("pcsx_rearmed", catalog.getCore("ps1")?.id)
        assertEquals("mupen64plus_next", catalog.getCore("n64")?.id)
        assertEquals("fbneo", catalog.getCore("arcade")?.id)
        assertEquals("fbneo", catalog.getCore("neogeo")?.id)
        assertEquals("ppsspp", catalog.getCore("psp")?.id)
        assertEquals("melonds", catalog.getCore("nds")?.id)

        // By unified ID alias
        assertEquals("mgba", catalog.getCore("mgba-unified")?.id)
        assertEquals("snes9x", catalog.getCore("snes9x-unified")?.id)
        assertEquals("genesis_plus_gx", catalog.getCore("genesis-unified")?.id)
        assertEquals("mednafen_pce_fast", catalog.getCore("pce-unified")?.id)
    }

    @Test
    fun `toRuntimeDescriptors converts all catalog entries to valid RuntimeDescriptors`() {
        val descriptors = CoreCatalog.DEFAULT.toRuntimeDescriptors()

        assertEquals(10, descriptors.size)
        for (desc in descriptors) {
            assertTrue(desc.id.endsWith("-unified"))
            assertEquals(1, desc.runtimeApi)
            assertEquals(26, desc.minSdk)
            assertEquals(35, desc.targetSdk)
            assertTrue(desc.supportedPlatforms.isNotEmpty())
            assertTrue(desc.supportedAbis.contains("arm64-v8a"))
            assertTrue(desc.supportedAbis.contains("x86_64"))
            assertTrue(desc.romExtensions.isNotEmpty())
            assertEquals("assets/game.rom", desc.romAssetPath)
            assertEquals("assets/retropack.json", desc.configAssetPath)
            assertEquals(1, desc.configSchemaVersion)
            assertTrue(desc.capabilities.saveStates > 0)
        }
    }

    @Test
    fun `converts CoreCatalogEntry to RuntimeDescriptor correctly`() {
        val entry = CoreCatalogEntry(
            id = "mgba",
            displayName = "mGBA",
            system = "Game Boy Advance",
            libName = "libretro_mgba.so",
            upstreamSlug = "mgba_libretro_android.so.zip",
            extensions = listOf(".gba", ".gbc", ".gb", ".bin")
        )

        val desc = entry.toRuntimeDescriptor()
        assertEquals("mgba-unified", desc.id)
        assertEquals(listOf("gb", "gbc", "gba"), desc.supportedPlatforms)
        assertEquals(listOf(".gba", ".gbc", ".gb", ".bin"), desc.romExtensions)
        assertEquals("mgba", desc.coreId)
        assertEquals("libretro_mgba.so", desc.coreLibName)
    }
}
