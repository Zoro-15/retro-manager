package com.retropack.runtime.factory

import com.retropack.runtime.core.NativeCore
import com.retropack.runtime.core.NativeCoreBridge
import com.retropack.runtime.fceumm.FceummNativeCore
import com.retropack.runtime.fbneo.FbNeoNativeCore
import com.retropack.runtime.genesis.GenesisNativeCore
import com.retropack.runtime.host.RuntimeConfig
import com.retropack.runtime.melonds.MelondsNativeCore
import com.retropack.runtime.mgba.MgbaNativeCore
import com.retropack.runtime.mupen64.Mupen64NativeCore
import com.retropack.runtime.pce.PceNativeCore
import com.retropack.runtime.pcsx.PcsxNativeCore
import com.retropack.runtime.ppsspp.PpssppNativeCore
import com.retropack.runtime.snes.Snes9xNativeCore

/**
 * Universal dynamic core factory for RetroPack standalone runtime applications.
 *
 * Resolves and instantiates the appropriate [NativeCoreBridge] implementation
 * across all 10 supported retro emulation engines:
 * - mGBA (GB / GBC / GBA)
 * - Snes9x (SNES / Super Famicom)
 * - Genesis Plus GX (Genesis / Mega Drive / Master System / Game Gear)
 * - FCEUmm (NES / Famicom)
 * - Beetle PCE Fast (PC Engine / TurboGrafx-16)
 * - FinalBurn Neo (Arcade / Neo Geo / CPS 1-3)
 * - PCSX ReARMed (PlayStation 1)
 * - Mupen64Plus-Next (Nintendo 64)
 * - PPSSPP (PlayStation Portable)
 * - melonDS (Nintendo DS)
 */
object NativeCoreFactory {

    /**
     * Resolves the appropriate [NativeCoreBridge] from injected [RuntimeConfig].
     */
    fun createCore(config: RuntimeConfig): NativeCoreBridge {
        val platform = config.game.platform
        val coreHint = config.runtime.core
        return resolveCore(platform = platform, coreHint = coreHint)
    }

    /**
     * Resolves the appropriate [NativeCoreBridge] by platform identifier and/or core hint.
     */
    fun resolveCore(platform: String?, coreHint: String? = null): NativeCoreBridge {
        val normalizedPlatform = platform?.lowercase()?.trim()?.removePrefix(".") ?: ""
        val normalizedCore = coreHint?.lowercase()?.trim() ?: ""

        // 1. Check explicit core hint if specified
        when (normalizedCore) {
            "snes9x", "snes9x-unified" -> return Snes9xNativeCore
            "genesis", "genesis-plus-gx", "genesis-unified" -> return GenesisNativeCore
            "fceumm", "fceumm-unified" -> return FceummNativeCore
            "pce", "beetle-pce-fast", "pce-unified" -> return PceNativeCore
            "fbneo", "finalburn-neo", "fbneo-unified" -> return FbNeoNativeCore
            "pcsx", "pcsx-rearmed", "pcsx-unified" -> return PcsxNativeCore
            "mupen64", "mupen64plus-next", "mupen64-unified" -> return Mupen64NativeCore
            "ppsspp", "ppsspp-unified" -> return PpssppNativeCore
            "melonds", "melonds-unified" -> return MelondsNativeCore
            "mgba", "mgba-unified" -> return MgbaNativeCore
        }

        // 2. Resolve by platform ID
        return when (normalizedPlatform) {
            "snes", "sfc", "smc" -> Snes9xNativeCore
            "genesis", "md", "smd", "gen", "sms", "gg" -> GenesisNativeCore
            "nes", "fds", "unf" -> FceummNativeCore
            "pce", "tg16", "sgx" -> PceNativeCore
            "arcade", "neogeo", "cps1", "cps2", "cps3", "fbneo" -> FbNeoNativeCore
            "psx", "ps1", "ps" -> PcsxNativeCore
            "n64", "z64", "v64" -> Mupen64NativeCore
            "psp" -> PpssppNativeCore
            "nds", "dsi" -> MelondsNativeCore
            "gba", "gbc", "gb" -> MgbaNativeCore
            else -> NativeCore
        }
    }
}
