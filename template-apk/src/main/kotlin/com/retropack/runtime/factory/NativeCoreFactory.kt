package com.retropack.runtime.factory

import com.retropack.runtime.core.NativeCore
import com.retropack.runtime.core.NativeCoreBridge
import com.retropack.runtime.core.UniversalLibretroCore
import com.retropack.runtime.host.RuntimeConfig

/**
 * Universal dynamic core factory for RetroPack standalone runtime applications.
 *
 * Resolves platform identifiers and runtime configurations to canonical Libretro core
 * shared libraries and dispatches execution dynamically to [UniversalLibretroCore].
 *
 * Supported Cores & Consoles:
 * - mGBA (`libretro_mgba.so`): GB / GBC / GBA
 * - Snes9x (`libretro_snes9x.so`): SNES / Super Famicom
 * - Genesis Plus GX (`libretro_genesis_plus_gx.so`): Genesis / Mega Drive / Master System / Game Gear
 * - FCEUmm (`libretro_fceumm.so`): NES / Famicom
 * - Beetle PCE Fast (`libretro_mednafen_pce_fast.so`): PC Engine / TurboGrafx-16
 * - FinalBurn Neo (`libretro_fbneo.so`): Arcade / Neo Geo / CPS 1-3
 * - PCSX ReARMed (`libretro_pcsx_rearmed.so`): PlayStation 1
 * - Mupen64Plus-Next (`libretro_mupen64plus_next.so`): Nintendo 64
 * - PPSSPP (`libretro_ppsspp.so`): PlayStation Portable
 * - melonDS (`libretro_melonds.so`): Nintendo DS
 */
object NativeCoreFactory {

    /**
     * Resolves and prepares the appropriate [NativeCoreBridge] from injected [RuntimeConfig].
     */
    fun createCore(config: RuntimeConfig, context: android.content.Context? = null): NativeCoreBridge {
        val platform = config.game.platform
        val coreHint = config.runtime.core
        return resolveCore(platform = platform, coreHint = coreHint, context = context)
    }

    /**
     * Resolves the canonical core ID from platform identifier and/or core hint.
     */
    fun resolveCoreId(platform: String?, coreHint: String? = null): String {
        val normalizedPlatform = platform?.lowercase()?.trim()?.removePrefix(".") ?: ""
        val normalizedCore = coreHint?.lowercase()?.trim() ?: ""

        // 1. Check explicit core hint first
        when (normalizedCore) {
            "snes9x", "snes9x-unified", "libretro_snes9x.so" -> return "snes9x"
            "genesis", "genesis-plus-gx", "genesis_plus_gx", "genesis-unified", "libretro_genesis_plus_gx.so" -> return "genesis_plus_gx"
            "fceumm", "fceumm-unified", "libretro_fceumm.so" -> return "fceumm"
            "pce", "beetle-pce-fast", "mednafen_pce_fast", "pce-unified", "libretro_mednafen_pce_fast.so" -> return "mednafen_pce_fast"
            "fbneo", "finalburn-neo", "fbneo-unified", "libretro_fbneo.so" -> return "fbneo"
            "pcsx", "pcsx-rearmed", "pcsx_rearmed", "pcsx-unified", "libretro_pcsx_rearmed.so" -> return "pcsx_rearmed"
            "mupen64", "mupen64plus-next", "mupen64plus_next", "mupen64-unified", "libretro_mupen64plus_next.so" -> return "mupen64plus_next"
            "ppsspp", "ppsspp-unified", "libretro_ppsspp.so" -> return "ppsspp"
            "melonds", "melonds-unified", "libretro_melonds.so" -> return "melonds"
            "mgba", "mgba-unified", "libretro_mgba.so" -> return "mgba"
        }

        // 2. Resolve by platform ID
        return when (normalizedPlatform) {
            "snes", "sfc", "smc" -> "snes9x"
            "genesis", "md", "smd", "gen", "sms", "gg" -> "genesis_plus_gx"
            "nes", "fds", "unf" -> "fceumm"
            "pce", "tg16", "sgx" -> "mednafen_pce_fast"
            "arcade", "neogeo", "cps1", "cps2", "cps3", "fbneo" -> "fbneo"
            "psx", "ps1", "ps" -> "pcsx_rearmed"
            "n64", "z64", "v64" -> "mupen64plus_next"
            "psp" -> "ppsspp"
            "nds", "dsi" -> "melonds"
            "gba", "gbc", "gb" -> "mgba"
            else -> if (normalizedCore.isNotBlank()) normalizedCore else "mgba"
        }
    }

    /**
     * Resolves the canonical shared library filename (.so) from platform identifier and/or core hint.
     */
    fun resolveCoreLibName(platform: String?, coreHint: String? = null): String {
        val coreId = resolveCoreId(platform, coreHint)
        return "libretro_$coreId.so"
    }

    /**
     * Resolves and binds the target [NativeCoreBridge] for execution.
     * Loads the target core dynamically into [UniversalLibretroCore] if available.
     */
    fun resolveCore(platform: String?, coreHint: String? = null, context: android.content.Context? = null): NativeCoreBridge {
        val coreId = resolveCoreId(platform, coreHint)
        val libName = resolveCoreLibName(platform, coreHint)

        if (UniversalLibretroCore.isLoaded()) {
            // 1. Attempt registering via ClassLoader System.loadLibrary
            val baseName = libName.removePrefix("lib").removeSuffix(".so")
            runCatching { System.loadLibrary(baseName) }
            runCatching { System.loadLibrary(coreId) }
            runCatching { System.loadLibrary("retro_$coreId") }

            // 2. Attempt loading from absolute nativeLibraryDir if available
            val nativeDir = context?.applicationInfo?.nativeLibraryDir
            if (!nativeDir.isNullOrBlank()) {
                val candidateFile = java.io.File(nativeDir, libName)
                if (candidateFile.exists()) {
                    UniversalLibretroCore.loadCore(candidateFile.absolutePath)
                }
            }

            // 3. Direct loadCore call
            UniversalLibretroCore.loadCore(libName)
            return UniversalLibretroCore
        }

        // Fallback to NativeCore bridge if running in legacy host mode or testing
        return if (UniversalLibretroCore.isLoaded()) UniversalLibretroCore else NativeCore
    }
}
