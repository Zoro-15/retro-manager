package com.retropack.runtime.factory

import com.retropack.runtime.core.NativeCore
import com.retropack.runtime.fceumm.FceummNativeCore
import com.retropack.runtime.fbneo.FbNeoNativeCore
import com.retropack.runtime.genesis.GenesisNativeCore
import com.retropack.runtime.host.EngineConfig
import com.retropack.runtime.host.GameConfig
import com.retropack.runtime.host.RuntimeConfig
import com.retropack.runtime.melonds.MelondsNativeCore
import com.retropack.runtime.mgba.MgbaNativeCore
import com.retropack.runtime.mupen64.Mupen64NativeCore
import com.retropack.runtime.pce.PceNativeCore
import com.retropack.runtime.pcsx.PcsxNativeCore
import com.retropack.runtime.ppsspp.PpssppNativeCore
import com.retropack.runtime.snes.Snes9xNativeCore
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class NativeCoreFactoryTest {

    @Test
    fun `resolves core by platform identifier across all 10 consoles`() {
        assertSame(MgbaNativeCore, NativeCoreFactory.resolveCore("gba"))
        assertSame(MgbaNativeCore, NativeCoreFactory.resolveCore("gbc"))
        assertSame(MgbaNativeCore, NativeCoreFactory.resolveCore("gb"))

        assertSame(Snes9xNativeCore, NativeCoreFactory.resolveCore("snes"))
        assertSame(Snes9xNativeCore, NativeCoreFactory.resolveCore("sfc"))
        assertSame(Snes9xNativeCore, NativeCoreFactory.resolveCore("smc"))

        assertSame(GenesisNativeCore, NativeCoreFactory.resolveCore("genesis"))
        assertSame(GenesisNativeCore, NativeCoreFactory.resolveCore("md"))
        assertSame(GenesisNativeCore, NativeCoreFactory.resolveCore("smd"))
        assertSame(GenesisNativeCore, NativeCoreFactory.resolveCore("gen"))
        assertSame(GenesisNativeCore, NativeCoreFactory.resolveCore("sms"))
        assertSame(GenesisNativeCore, NativeCoreFactory.resolveCore("gg"))

        assertSame(FceummNativeCore, NativeCoreFactory.resolveCore("nes"))
        assertSame(FceummNativeCore, NativeCoreFactory.resolveCore("fds"))
        assertSame(FceummNativeCore, NativeCoreFactory.resolveCore("unf"))

        assertSame(PceNativeCore, NativeCoreFactory.resolveCore("pce"))
        assertSame(PceNativeCore, NativeCoreFactory.resolveCore("tg16"))
        assertSame(PceNativeCore, NativeCoreFactory.resolveCore("sgx"))

        assertSame(FbNeoNativeCore, NativeCoreFactory.resolveCore("arcade"))
        assertSame(FbNeoNativeCore, NativeCoreFactory.resolveCore("neogeo"))
        assertSame(FbNeoNativeCore, NativeCoreFactory.resolveCore("cps1"))
        assertSame(FbNeoNativeCore, NativeCoreFactory.resolveCore("cps2"))
        assertSame(FbNeoNativeCore, NativeCoreFactory.resolveCore("cps3"))
        assertSame(FbNeoNativeCore, NativeCoreFactory.resolveCore("fbneo"))

        assertSame(PcsxNativeCore, NativeCoreFactory.resolveCore("psx"))
        assertSame(PcsxNativeCore, NativeCoreFactory.resolveCore("ps1"))
        assertSame(PcsxNativeCore, NativeCoreFactory.resolveCore("ps"))

        assertSame(Mupen64NativeCore, NativeCoreFactory.resolveCore("n64"))
        assertSame(Mupen64NativeCore, NativeCoreFactory.resolveCore("z64"))
        assertSame(Mupen64NativeCore, NativeCoreFactory.resolveCore("v64"))

        assertSame(PpssppNativeCore, NativeCoreFactory.resolveCore("psp"))

        assertSame(MelondsNativeCore, NativeCoreFactory.resolveCore("nds"))
        assertSame(MelondsNativeCore, NativeCoreFactory.resolveCore("dsi"))
    }

    @Test
    fun `resolves core by explicit core hint`() {
        assertSame(Snes9xNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "snes9x"))
        assertSame(Snes9xNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "snes9x-unified"))

        assertSame(GenesisNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "genesis"))
        assertSame(GenesisNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "genesis-plus-gx"))
        assertSame(GenesisNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "genesis-unified"))

        assertSame(FceummNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "fceumm"))
        assertSame(FceummNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "fceumm-unified"))

        assertSame(PceNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "pce"))
        assertSame(PceNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "beetle-pce-fast"))
        assertSame(PceNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "pce-unified"))

        assertSame(FbNeoNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "fbneo"))
        assertSame(FbNeoNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "finalburn-neo"))
        assertSame(FbNeoNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "fbneo-unified"))

        assertSame(PcsxNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "pcsx"))
        assertSame(PcsxNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "pcsx-rearmed"))
        assertSame(PcsxNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "pcsx-unified"))

        assertSame(Mupen64NativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "mupen64"))
        assertSame(Mupen64NativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "mupen64plus-next"))
        assertSame(Mupen64NativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "mupen64-unified"))

        assertSame(PpssppNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "ppsspp"))
        assertSame(PpssppNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "ppsspp-unified"))

        assertSame(MelondsNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "melonds"))
        assertSame(MelondsNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "melonds-unified"))

        assertSame(MgbaNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "mgba"))
        assertSame(MgbaNativeCore, NativeCoreFactory.resolveCore(platform = "", coreHint = "mgba-unified"))
    }

    @Test
    fun `resolves core from RuntimeConfig object`() {
        val snesConfig = RuntimeConfig(
            game = GameConfig(platform = "snes"),
            runtime = EngineConfig(core = "snes9x")
        )
        assertSame(Snes9xNativeCore, NativeCoreFactory.createCore(snesConfig))

        val psxConfig = RuntimeConfig(
            game = GameConfig(platform = "psx"),
            runtime = EngineConfig(core = "pcsx")
        )
        assertSame(PcsxNativeCore, NativeCoreFactory.createCore(psxConfig))

        val n64Config = RuntimeConfig(
            game = GameConfig(platform = "n64"),
            runtime = EngineConfig(core = "mupen64")
        )
        assertSame(Mupen64NativeCore, NativeCoreFactory.createCore(n64Config))
    }

    @Test
    fun `falls back safely to default NativeCore on unknown platform or null`() {
        assertSame(NativeCore, NativeCoreFactory.resolveCore(null, null))
        assertSame(NativeCore, NativeCoreFactory.resolveCore("unknown_console", "unknown_core"))
    }
}
