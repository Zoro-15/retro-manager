package com.retropack.runtime.factory

import com.retropack.runtime.core.NativeCore
import com.retropack.runtime.core.NativeCoreBridge
import com.retropack.runtime.core.UniversalLibretroCore
import com.retropack.runtime.host.RuntimeConfig
import java.io.File

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
            "mgba", "mgba-unified", "libretro_mgba.so" -> return "mgba"
        }

        // 2. Resolve by platform ID
        return when (normalizedPlatform) {
            "snes", "sfc", "smc" -> "snes9x"
            "genesis", "md", "smd", "gen", "sms", "gg" -> "genesis_plus_gx"
            "nes", "fds", "unf" -> "fceumm"
            "pce", "tg16", "sgx" -> "mednafen_pce_fast"
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

            var loaded = false
            // 2. Attempt loading from absolute nativeLibraryDir if available
            val nativeDir = context?.applicationInfo?.nativeLibraryDir
            if (!nativeDir.isNullOrBlank()) {
                val candidateFile = java.io.File(nativeDir, libName)
                if (candidateFile.exists()) {
                    loaded = UniversalLibretroCore.loadCore(candidateFile.absolutePath)
                }
            }

            // 3. Direct loadCore call if not already loaded from nativeDir
            if (!loaded) {
                UniversalLibretroCore.loadCore(libName)
            }
            return UniversalLibretroCore
        }

        // Fallback to NativeCore bridge if running in legacy host mode or testing
        return if (UniversalLibretroCore.isLoaded()) UniversalLibretroCore else NativeCore
    }
}
