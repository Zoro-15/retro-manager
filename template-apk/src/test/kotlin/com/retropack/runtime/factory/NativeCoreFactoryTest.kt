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

        assertEquals("fbneo", NativeCoreFactory.resolveCoreId("arcade"))
        assertEquals("fbneo", NativeCoreFactory.resolveCoreId("neogeo"))
        assertEquals("fbneo", NativeCoreFactory.resolveCoreId("cps1"))
        assertEquals("fbneo", NativeCoreFactory.resolveCoreId("cps2"))
        assertEquals("fbneo", NativeCoreFactory.resolveCoreId("cps3"))
        assertEquals("fbneo", NativeCoreFactory.resolveCoreId("fbneo"))

        assertEquals("pcsx_rearmed", NativeCoreFactory.resolveCoreId("psx"))
        assertEquals("pcsx_rearmed", NativeCoreFactory.resolveCoreId("ps1"))
        assertEquals("pcsx_rearmed", NativeCoreFactory.resolveCoreId("ps"))

        assertEquals("mupen64plus_next", NativeCoreFactory.resolveCoreId("n64"))
        assertEquals("mupen64plus_next", NativeCoreFactory.resolveCoreId("z64"))
        assertEquals("mupen64plus_next", NativeCoreFactory.resolveCoreId("v64"))

        assertEquals("ppsspp", NativeCoreFactory.resolveCoreId("psp"))

        assertEquals("melonds", NativeCoreFactory.resolveCoreId("nds"))
        assertEquals("melonds", NativeCoreFactory.resolveCoreId("dsi"))
    }

    @Test
    fun `resolves canonical library names across all 10 consoles`() {
        assertEquals("libretro_mgba.so", NativeCoreFactory.resolveCoreLibName("gba"))
        assertEquals("libretro_snes9x.so", NativeCoreFactory.resolveCoreLibName("snes"))
        assertEquals("libretro_genesis_plus_gx.so", NativeCoreFactory.resolveCoreLibName("genesis"))
        assertEquals("libretro_fceumm.so", NativeCoreFactory.resolveCoreLibName("nes"))
        assertEquals("libretro_mednafen_pce_fast.so", NativeCoreFactory.resolveCoreLibName("pce"))
        assertEquals("libretro_fbneo.so", NativeCoreFactory.resolveCoreLibName("arcade"))
        assertEquals("libretro_pcsx_rearmed.so", NativeCoreFactory.resolveCoreLibName("ps1"))
        assertEquals("libretro_mupen64plus_next.so", NativeCoreFactory.resolveCoreLibName("n64"))
        assertEquals("libretro_ppsspp.so", NativeCoreFactory.resolveCoreLibName("psp"))
        assertEquals("libretro_melonds.so", NativeCoreFactory.resolveCoreLibName("nds"))
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

        assertEquals("fbneo", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "fbneo"))
        assertEquals("fbneo", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "finalburn-neo"))

        assertEquals("pcsx_rearmed", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "pcsx"))
        assertEquals("pcsx_rearmed", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "pcsx-rearmed"))
        assertEquals("pcsx_rearmed", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "pcsx_rearmed"))

        assertEquals("mupen64plus_next", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "mupen64"))
        assertEquals("mupen64plus_next", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "mupen64plus-next"))
        assertEquals("mupen64plus_next", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "mupen64plus_next"))

        assertEquals("ppsspp", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "ppsspp"))
        assertEquals("ppsspp", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "ppsspp-unified"))

        assertEquals("melonds", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "melonds"))
        assertEquals("melonds", NativeCoreFactory.resolveCoreId(platform = "", coreHint = "melonds-unified"))

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

        val psxConfig = RuntimeConfig(
            game = GameConfig(platform = "psx"),
            runtime = EngineConfig(core = "pcsx")
        )
        val psxBridge: NativeCoreBridge = NativeCoreFactory.createCore(psxConfig)
        assertNotNull(psxBridge)
    }

    @Test
    fun `falls back safely to default core on unknown platform or null`() {
        assertEquals("mgba", NativeCoreFactory.resolveCoreId(null, null))
        assertEquals("libretro_mgba.so", NativeCoreFactory.resolveCoreLibName(null, null))
        assertNotNull(NativeCoreFactory.resolveCore(null, null))
    }
}
