package com.retropack.runtime.factory

import com.retropack.runtime.core.NativeCoreBridge
import com.retropack.runtime.host.EngineConfig
import com.retropack.runtime.host.GameConfig
import com.retropack.runtime.host.RuntimeConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class NativeCoreFactoryTest {

    @Test
    fun `resolves core ID by platform identifier across all 10 consoles`() {
        assertEquals("mgba", NativeCoreFactory.resolveCoreId("gba"))
        assertEquals("mgba", NativeCoreFactory.resolveCoreId("gbc"))
        assertEquals("mgba", NativeCoreFactory.resolveCoreId("gb"))

        assertEquals("snes9x", NativeCoreFactory.resolveCoreId("snes"))
        assertEquals("snes9x", NativeCoreFactory.resolveCoreId("sfc"))
        assertEquals("snes9x", NativeCoreFactory.resolveCoreId("smc"))

        assertEquals("genesis_plus_gx", NativeCoreFactory.resolveCoreId("genesis"))
        assertEquals("genesis_plus_gx", NativeCoreFactory.resolveCoreId("md"))
        assertEquals("genesis_plus_gx", NativeCoreFactory.resolveCoreId("smd"))
        assertEquals("genesis_plus_gx", NativeCoreFactory.resolveCoreId("gen"))
        assertEquals("genesis_plus_gx", NativeCoreFactory.resolveCoreId("sms"))
        assertEquals("genesis_plus_gx", NativeCoreFactory.resolveCoreId("gg"))

        assertEquals("fceumm", NativeCoreFactory.resolveCoreId("nes"))
        assertEquals("fceumm", NativeCoreFactory.resolveCoreId("fds"))
        assertEquals("fceumm", NativeCoreFactory.resolveCoreId("unf"))

        assertEquals("mednafen_pce_fast", NativeCoreFactory.resolveCoreId("pce"))
        assertEquals("mednafen_pce_fast", NativeCoreFactory.resolveCoreId("tg16"))
        assertEquals("mednafen_pce_fast", NativeCoreFactory.resolveCoreId("sgx"))
    }

    @Test
    fun `resolves canonical library names across all 5 consoles`() {
        assertEquals("libretro_mgba.so", NativeCoreFactory.resolveCoreLibName("gba"))
        assertEquals("libretro_snes9x.so", NativeCoreFactory.resolveCoreLibName("snes"))
        assertEquals("libretro_genesis_plus_gx.so", NativeCoreFactory.resolveCoreLibName("genesis"))
        assertEquals("libretro_fceumm.so", NativeCoreFactory.resolveCoreLibName("nes"))
        assertEquals("libretro_mednafen_pce_fast.so", NativeCoreFactory.resolveCoreLibName("pce"))
    }

    @Test
    fun `resolves core by explicit core hint`() {
        assertEquals("snes9x", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "snes9x"))
        assertEquals("snes9x", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "snes9x-unified"))

        assertEquals("genesis_plus_gx", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "genesis"))
        assertEquals("genesis_plus_gx", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "genesis-plus-gx"))
        assertEquals("genesis_plus_gx", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "genesis_plus_gx"))

        assertEquals("fceumm", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "fceumm"))
        assertEquals("fceumm", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "fceumm-unified"))

        assertEquals("mednafen_pce_fast", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "pce"))
        assertEquals("mednafen_pce_fast", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "beetle-pce-fast"))
        assertEquals("mednafen_pce_fast", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "mednafen_pce_fast"))

        assertEquals("mgba", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "mgba"))
        assertEquals("mgba", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "mgba-unified"))
    }

    @Test
    fun `resolves and returns valid NativeCoreBridge instance`() {
        val snesConfig = RuntimeConfig(
            game = GameConfig(platform = "snes"),
            runtime = EngineConfig(core = "snes9x")
        )
        val snesBridge: NativeCoreBridge = NativeCoreFactory.createCore(snesConfig)
        assertNotNull(snesBridge)

        val nesConfig = RuntimeConfig(
            game = GameConfig(platform = "nes"),
            runtime = EngineConfig(core = "fceumm")
        )
        val nesBridge: NativeCoreBridge = NativeCoreFactory.createCore(nesConfig)
        assertNotNull(nesBridge)
    }

    @Test
    fun `falls back safely to default core on unknown platform or null`() {
        assertEquals("mgba", NativeCoreFactory.resolveCoreId(null, null))
        assertEquals("libretro_mgba.so", NativeCoreFactory.resolveCoreLibName(null, null))
        assertNotNull(NativeCoreFactory.resolveCore(null, null))
    }
}
