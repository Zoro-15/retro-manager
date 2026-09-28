package com.retropack.domain.runtime

import java.io.File

/**
 * Central registry, discovery engine, and cryptographic trust anchor for RetroPack runtime templates.
 *
 * Implements specifications from:
 * - architechture.md Section 3: Runtime Discovery & Trust Anchor (lines 80-84)
 * - roadmap.md Phase 3 Task 6: "Hardcode trusted SHA-256 fingerprints directly into RuntimeRegistry.kt"
 * - Constitutional Law 22: Verification precedes release.
 */
object RuntimeRegistry {

    /**
     * Canonical Runtime Identifiers
     */
    const val RUNTIME_MGBA_UNIFIED = "mgba-unified"
    const val RUNTIME_SNES9X_UNIFIED = "snes9x-unified"
    const val RUNTIME_GENESIS_UNIFIED = "genesis-unified"
    const val RUNTIME_FCEUMM_UNIFIED = "fceumm-unified"
    const val RUNTIME_PCE_UNIFIED = "pce-unified"
    const val RUNTIME_FBNEO_UNIFIED = "fbneo-unified"
    const val RUNTIME_PCSX_UNIFIED = "pcsx-unified"
    const val RUNTIME_MUPEN64_UNIFIED = "mupen64-unified"
    const val RUNTIME_PPSSPP_UNIFIED = "ppsspp-unified"
    const val RUNTIME_MELONDS_UNIFIED = "melonds-unified"

    /**
     * Canonical Libretro Core Identifiers (matching runtimes/cores.json)
     */
    const val CORE_MGBA = "mgba"
    const val CORE_SNES9X = "snes9x"
    const val CORE_GENESIS_PLUS_GX = "genesis_plus_gx"
    const val CORE_FCEUMM = "fceumm"
    const val CORE_MEDNAFEN_PCE_FAST = "mednafen_pce_fast"
    const val CORE_FBNEO = "fbneo"
    const val CORE_PCSX_REARMED = "pcsx_rearmed"
    const val CORE_MUPEN64PLUS_NEXT = "mupen64plus_next"
    const val CORE_PPSSPP = "ppsspp"
    const val CORE_MELONDS = "melonds"

    val CANONICAL_CORE_IDS: Set<String> = setOf(
        CORE_MGBA,
        CORE_SNES9X,
        CORE_GENESIS_PLUS_GX,
        CORE_FCEUMM,
        CORE_MEDNAFEN_PCE_FAST,
        CORE_FBNEO,
        CORE_PCSX_REARMED,
        CORE_MUPEN64PLUS_NEXT,
        CORE_PPSSPP,
        CORE_MELONDS
    )

    /**
     * TRUST ANCHORS: Hardcoded SHA-256 fingerprints compiled directly into Kotlin bytecode.
     * Invariant: Never loaded from mutable disk or `.sha256` text files.
     *
     * Pinned to runtimes/mgba-unified/template.apk (whole-APK digest). Rotate in
     * lockstep with TRUSTED_PROTECTED_ENTRIES, RuntimeDescriptor.MGBA_UNIFIED and
     * runtimes/mgba-unified/runtime.json via scripts/rotate-trust-anchors.sh.
     * RuntimeBundleIntegrityTest fails the build when any of them drift.
     */
    val TRUSTED_TEMPLATES: Map<String, String> = mapOf(
        RUNTIME_MGBA_UNIFIED to "391b8bc1cb323f4a4d07784af3778cebe1ecd366f702fb0605bb63a1b6d9d8e0"
    )

    /**
     * Protected entries bytecode trust anchors for Step 9 integrity validation.
     * Entry names must match the REAL template contents: the NDK build produces
     * libretropack-runtime.so (System.loadLibrary("retropack-runtime")), NOT
     * libmgba.so.
     */
    val TRUSTED_PROTECTED_ENTRIES: Map<String, Map<String, String>> = mapOf(
        RUNTIME_MGBA_UNIFIED to mapOf(
            "classes.dex" to "b2533f8585723081e9d2bda0038eb8b0d550a7dbc9c4a52a0a66a2bf3901010f",
            "lib/arm64-v8a/libretropack-runtime.so" to "2253df2006ed765a84492382325b09e2ee2dfad72e943ab9d50fa3a31f09754b"
        )
    )

    private val registeredDescriptors = java.util.concurrent.ConcurrentHashMap<String, RuntimeDescriptor>()
    private val registeredTemplates = java.util.concurrent.ConcurrentHashMap<String, RuntimeTemplate>()
    private val platformMappings = java.util.concurrent.ConcurrentHashMap<String, MutableList<String>>()

    /**
     * Guards compound multi-map updates. Single-map reads/writes are already
     * safe via [ConcurrentHashMap]; registration/reset must be atomic across maps.
     * NOTE: batch builds must still serialize register/load cycles at the call
     * site — the registry does not version concurrent template generations.
     */
    private val registryLock = Any()

    init {
        resetToDefaults()
    }

    /**
     * Resets registry state to default built-in configurations.
     */
    fun resetToDefaults() {
        synchronized(registryLock) {
            registeredDescriptors.clear()
            registeredTemplates.clear()
            platformMappings.clear()

            // Register canonical descriptors for all 10 cores
            registerDescriptor(RuntimeDescriptor.MGBA_UNIFIED)
            registerDescriptor(RuntimeDescriptor.SNES9X_UNIFIED)
            registerDescriptor(RuntimeDescriptor.GENESIS_UNIFIED)
            registerDescriptor(RuntimeDescriptor.FCEUMM_UNIFIED)
            registerDescriptor(RuntimeDescriptor.PCE_UNIFIED)
            registerDescriptor(RuntimeDescriptor.FBNEO_UNIFIED)
            registerDescriptor(RuntimeDescriptor.PCSX_UNIFIED)
            registerDescriptor(RuntimeDescriptor.MUPEN64_UNIFIED)
            registerDescriptor(RuntimeDescriptor.PPSSPP_UNIFIED)
            registerDescriptor(RuntimeDescriptor.MELONDS_UNIFIED)

            // Register canonical platform associations
            registerPlatformMapping("gb", RUNTIME_MGBA_UNIFIED)
            registerPlatformMapping("gbc", RUNTIME_MGBA_UNIFIED)
            registerPlatformMapping("gba", RUNTIME_MGBA_UNIFIED)
            registerPlatformMapping("mgba", RUNTIME_MGBA_UNIFIED)

            registerPlatformMapping("snes", RUNTIME_SNES9X_UNIFIED)
            registerPlatformMapping("sfc", RUNTIME_SNES9X_UNIFIED)
            registerPlatformMapping("smc", RUNTIME_SNES9X_UNIFIED)
            registerPlatformMapping("snes9x", RUNTIME_SNES9X_UNIFIED)

            registerPlatformMapping("genesis", RUNTIME_GENESIS_UNIFIED)
            registerPlatformMapping("md", RUNTIME_GENESIS_UNIFIED)
            registerPlatformMapping("smd", RUNTIME_GENESIS_UNIFIED)
            registerPlatformMapping("gen", RUNTIME_GENESIS_UNIFIED)
            registerPlatformMapping("sms", RUNTIME_GENESIS_UNIFIED)
            registerPlatformMapping("gg", RUNTIME_GENESIS_UNIFIED)
            registerPlatformMapping("genesis_plus_gx", RUNTIME_GENESIS_UNIFIED)
            registerPlatformMapping("genesis-plus-gx", RUNTIME_GENESIS_UNIFIED)

            registerPlatformMapping("nes", RUNTIME_FCEUMM_UNIFIED)
            registerPlatformMapping("fds", RUNTIME_FCEUMM_UNIFIED)
            registerPlatformMapping("unf", RUNTIME_FCEUMM_UNIFIED)
            registerPlatformMapping("fceumm", RUNTIME_FCEUMM_UNIFIED)

            registerPlatformMapping("pce", RUNTIME_PCE_UNIFIED)
            registerPlatformMapping("tg16", RUNTIME_PCE_UNIFIED)
            registerPlatformMapping("sgx", RUNTIME_PCE_UNIFIED)
            registerPlatformMapping("mednafen_pce_fast", RUNTIME_PCE_UNIFIED)
            registerPlatformMapping("beetle-pce-fast", RUNTIME_PCE_UNIFIED)

            registerPlatformMapping("arcade", RUNTIME_FBNEO_UNIFIED)
            registerPlatformMapping("neogeo", RUNTIME_FBNEO_UNIFIED)
            registerPlatformMapping("cps1", RUNTIME_FBNEO_UNIFIED)
            registerPlatformMapping("cps2", RUNTIME_FBNEO_UNIFIED)
            registerPlatformMapping("cps3", RUNTIME_FBNEO_UNIFIED)
            registerPlatformMapping("fbneo", RUNTIME_FBNEO_UNIFIED)

            registerPlatformMapping("psx", RUNTIME_PCSX_UNIFIED)
            registerPlatformMapping("ps1", RUNTIME_PCSX_UNIFIED)
            registerPlatformMapping("ps", RUNTIME_PCSX_UNIFIED)
            registerPlatformMapping("pcsx", RUNTIME_PCSX_UNIFIED)
            registerPlatformMapping("pcsx_rearmed", RUNTIME_PCSX_UNIFIED)
            registerPlatformMapping("pcsx-rearmed", RUNTIME_PCSX_UNIFIED)

            registerPlatformMapping("n64", RUNTIME_MUPEN64_UNIFIED)
            registerPlatformMapping("z64", RUNTIME_MUPEN64_UNIFIED)
            registerPlatformMapping("v64", RUNTIME_MUPEN64_UNIFIED)
            registerPlatformMapping("mupen64", RUNTIME_MUPEN64_UNIFIED)
            registerPlatformMapping("mupen64plus_next", RUNTIME_MUPEN64_UNIFIED)
            registerPlatformMapping("mupen64plus-next", RUNTIME_MUPEN64_UNIFIED)

            registerPlatformMapping("psp", RUNTIME_PPSSPP_UNIFIED)
            registerPlatformMapping("ppsspp", RUNTIME_PPSSPP_UNIFIED)

            registerPlatformMapping("nds", RUNTIME_MELONDS_UNIFIED)
            registerPlatformMapping("dsi", RUNTIME_MELONDS_UNIFIED)
            registerPlatformMapping("melonds", RUNTIME_MELONDS_UNIFIED)
        }
    }

    /**
     * Registers a runtime descriptor into the registry.
     */
    fun registerDescriptor(descriptor: RuntimeDescriptor) {
        synchronized(registryLock) {
            registeredDescriptors[descriptor.id] = descriptor
            for (platform in descriptor.supportedPlatforms) {
                registerPlatformMapping(platform, descriptor.id)
            }
        }
    }

    /**
     * Registers a complete runtime template bundle into the registry.
     */
    fun registerTemplate(template: RuntimeTemplate) {
        synchronized(registryLock) {
            // Inline descriptor registration to stay under one lock acquisition.
            registeredDescriptors[template.descriptor.id] = template.descriptor
            for (platform in template.descriptor.supportedPlatforms) {
                val normalized = platform.lowercase().trim().removePrefix(".")
                val list = platformMappings.getOrPut(normalized) { mutableListOf() }
                if (!list.contains(template.descriptor.id)) {
                    list.add(template.descriptor.id)
                }
            }
            registeredTemplates[template.descriptor.id] = template
        }
    }

    /**
     * Loads and registers all runtime descriptors defined in a [CoreCatalog].
     */
    fun loadCatalog(catalog: CoreCatalog) {
        synchronized(registryLock) {
            for (descriptor in catalog.toRuntimeDescriptors()) {
                registerDescriptor(descriptor)
            }
        }
    }

    /**
     * Loads and registers all runtime descriptors from `cores.json` at [coresJsonFile].
     */
    fun loadCatalog(coresJsonFile: File) {
        val catalog = CoreCatalog.loadFromDirectory(coresJsonFile)
        loadCatalog(catalog)
    }

    /**
     * Maps a console platform identifier (e.g. "gba") to a compatible runtime ID.
     */
    fun registerPlatformMapping(platform: String, runtimeId: String) {
        val normalized = platform.lowercase().trim().removePrefix(".")
        // List mutation is guarded by the caller's registryLock where compound;
        // standalone calls synchronize here for the get-or-create + add pair.
        synchronized(registryLock) {
            val list = platformMappings.getOrPut(normalized) { mutableListOf() }
            if (!list.contains(runtimeId)) {
                list.add(runtimeId)
            }
        }
    }

    /**
     * Finds the default runtime descriptor for the specified [platform].
     */
    fun findRuntimeForPlatform(platform: String): RuntimeDescriptor? {
        val normalized = platform.lowercase().trim().removePrefix(".")
        val runtimeId = platformMappings[normalized]?.firstOrNull() ?: return null
        return getDescriptor(runtimeId)
    }

    /**
     * Finds all compatible runtime descriptors for the specified [platform].
     */
    fun findRuntimesForPlatform(platform: String): List<RuntimeDescriptor> {
        val normalized = platform.lowercase().trim().removePrefix(".")
        val runtimeIds = platformMappings[normalized] ?: return emptyList()
        return runtimeIds.mapNotNull { getDescriptor(it) }
    }

    val CANONICAL_RUNTIME_IDS: Set<String> = setOf(
        RUNTIME_MGBA_UNIFIED,
        RUNTIME_SNES9X_UNIFIED,
        RUNTIME_GENESIS_UNIFIED,
        RUNTIME_FCEUMM_UNIFIED,
        RUNTIME_PCE_UNIFIED,
        RUNTIME_FBNEO_UNIFIED,
        RUNTIME_PCSX_UNIFIED,
        RUNTIME_MUPEN64_UNIFIED,
        RUNTIME_PPSSPP_UNIFIED,
        RUNTIME_MELONDS_UNIFIED,
        CORE_MGBA,
        CORE_SNES9X,
        CORE_GENESIS_PLUS_GX,
        CORE_FCEUMM,
        CORE_MEDNAFEN_PCE_FAST,
        CORE_FBNEO,
        CORE_PCSX_REARMED,
        CORE_MUPEN64PLUS_NEXT,
        CORE_PPSSPP,
        CORE_MELONDS
    )

    /**
     * Maps a core ID, platform slug, or alias to its canonical unified runtime descriptor ID.
     */
    fun toUnifiedRuntimeId(identifier: String): String {
        val normalized = identifier.lowercase().trim().replace("-", "_")
        return when (normalized) {
            "mgba", "gba", "gbc", "gb" -> RUNTIME_MGBA_UNIFIED
            "snes9x", "snes", "sfc", "smc" -> RUNTIME_SNES9X_UNIFIED
            "genesis_plus_gx", "genesis", "md", "smd", "gen", "sms", "gg" -> RUNTIME_GENESIS_UNIFIED
            "fceumm", "nes", "fds", "unf" -> RUNTIME_FCEUMM_UNIFIED
            "mednafen_pce_fast", "pce", "tg16", "sgx", "beetle_pce_fast" -> RUNTIME_PCE_UNIFIED
            "fbneo", "arcade", "neogeo", "cps1", "cps2", "cps3" -> RUNTIME_FBNEO_UNIFIED
            "pcsx_rearmed", "pcsx", "psx", "ps1", "ps" -> RUNTIME_PCSX_UNIFIED
            "mupen64plus_next", "mupen64", "n64", "z64", "v64" -> RUNTIME_MUPEN64_UNIFIED
            "ppsspp", "psp" -> RUNTIME_PPSSPP_UNIFIED
            "melonds", "nds", "dsi" -> RUNTIME_MELONDS_UNIFIED
            else -> {
                if (normalized.endsWith("_unified")) normalized.replace("_", "-")
                else "$normalized-unified"
            }
        }
    }

    /**
     * Retrieves a registered descriptor by [runtimeId] or canonical core ID.
     */
    fun getDescriptor(runtimeId: String): RuntimeDescriptor? {
        val normalized = runtimeId.lowercase().trim()
        val direct = registeredDescriptors[normalized]
        if (direct != null) return direct

        val mappedId = platformMappings[normalized]?.firstOrNull()
        if (mappedId != null) {
            registeredDescriptors[mappedId]?.let { return it }
        }

        val unifiedId = toUnifiedRuntimeId(normalized)
        val mapped = registeredDescriptors[unifiedId] ?: registeredDescriptors[unifiedId.replace("-", "_")]
        if (mapped != null) return mapped

        val coreId = com.retropack.packaging.CoreLibraryInjector.resolveCoreId(normalized, normalized)
        val coreUnifiedId = toUnifiedRuntimeId(coreId)
        val coreMapped = registeredDescriptors[coreUnifiedId] ?: registeredDescriptors[coreId]
        if (coreMapped != null) return coreMapped

        if (normalized in CANONICAL_RUNTIME_IDS || coreId in CANONICAL_CORE_IDS) {
            return registeredDescriptors[RUNTIME_MGBA_UNIFIED]
        }
        return null
    }

    /**
     * Retrieves a registered template by [runtimeId] or canonical core ID.
     */
    fun getTemplate(runtimeId: String): RuntimeTemplate? {
        val normalized = runtimeId.lowercase().trim()
        val direct = registeredTemplates[normalized]
        if (direct != null) return direct

        val mappedId = platformMappings[normalized]?.firstOrNull()
        if (mappedId != null) {
            registeredTemplates[mappedId]?.let { return it }
        }

        val unifiedId = toUnifiedRuntimeId(normalized)
        val mapped = registeredTemplates[unifiedId] ?: registeredTemplates[unifiedId.replace("-", "_")]
        if (mapped != null) return mapped

        val coreId = com.retropack.packaging.CoreLibraryInjector.resolveCoreId(normalized, normalized)
        val coreUnifiedId = toUnifiedRuntimeId(coreId)
        val coreMapped = registeredTemplates[coreUnifiedId] ?: registeredTemplates[coreId]
        if (coreMapped != null) return coreMapped

        if (normalized in CANONICAL_RUNTIME_IDS || coreId in CANONICAL_CORE_IDS) {
            return registeredTemplates[RUNTIME_MGBA_UNIFIED]
        }
        return null
    }

    /**
     * Retrieves the compiled bytecode trust anchor for [runtimeId].
     */
    fun getTrustedFingerprint(runtimeId: String): String? {
        val normalized = runtimeId.lowercase().trim()
        val direct = TRUSTED_TEMPLATES[normalized]
        if (direct != null) return direct

        val mappedId = platformMappings[normalized]?.firstOrNull()
        if (mappedId != null) {
            TRUSTED_TEMPLATES[mappedId]?.let { return it }
        }

        val unifiedId = toUnifiedRuntimeId(normalized)
        val mapped = TRUSTED_TEMPLATES[unifiedId] ?: TRUSTED_TEMPLATES[unifiedId.replace("-", "_")]
        if (mapped != null) return mapped

        val coreId = com.retropack.packaging.CoreLibraryInjector.resolveCoreId(normalized, normalized)
        val coreUnifiedId = toUnifiedRuntimeId(coreId)
        val coreMapped = TRUSTED_TEMPLATES[coreUnifiedId] ?: TRUSTED_TEMPLATES[coreId]
        if (coreMapped != null) return coreMapped

        if (normalized in CANONICAL_RUNTIME_IDS || coreId in CANONICAL_CORE_IDS) {
            return TRUSTED_TEMPLATES[RUNTIME_MGBA_UNIFIED]
        }
        return null
    }

    /**
     * Validates whether [actualSha256] matches the bytecode-anchored trusted fingerprint for [runtimeId].
     */
    fun isTrustedTemplate(runtimeId: String, actualSha256: String): Boolean {
        val trusted = getTrustedFingerprint(runtimeId) ?: return false
        val normalizedActual = actualSha256.removePrefix("sha256:").trim()
        val normalizedTrusted = trusted.removePrefix("sha256:").trim()
        return normalizedActual.equals(normalizedTrusted, ignoreCase = true)
    }

    /**
     * Cryptographically validates actual entry digests against compiled bytecode trust anchors.
     * Enforces BuildEngine Step 9 (Allowlist Policy).
     */
    fun verifyProtectedEntries(runtimeId: String, actualEntries: Map<String, String>): Boolean {
        val normalized = runtimeId.lowercase().trim()
        val mappedId = platformMappings[normalized]?.firstOrNull()
        val unifiedId = toUnifiedRuntimeId(normalized)
        val coreId = com.retropack.packaging.CoreLibraryInjector.resolveCoreId(normalized, normalized)
        val coreUnifiedId = toUnifiedRuntimeId(coreId)

        val trustedMap = TRUSTED_PROTECTED_ENTRIES[normalized]
            ?: (if (mappedId != null) TRUSTED_PROTECTED_ENTRIES[mappedId] else null)
            ?: TRUSTED_PROTECTED_ENTRIES[unifiedId]
            ?: TRUSTED_PROTECTED_ENTRIES[coreUnifiedId]
            ?: TRUSTED_PROTECTED_ENTRIES[coreId]
            ?: (if (normalized in CANONICAL_RUNTIME_IDS || coreId in CANONICAL_CORE_IDS) TRUSTED_PROTECTED_ENTRIES[RUNTIME_MGBA_UNIFIED] else null)
            ?: return false

        for ((entryName, trustedHash) in trustedMap) {
            val actualHash = actualEntries[entryName] ?: return false
            val normalizedActual = actualHash.removePrefix("sha256:").trim()
            val normalizedTrusted = trustedHash.removePrefix("sha256:").trim()
            if (!normalizedActual.equals(normalizedTrusted, ignoreCase = true)) {
                return false
            }
        }
        return true
    }

    /**
     * Loads a [RuntimeTemplate] from a standard on-disk layout and registers it:
     * ```
     * runtimeDir/
     * ├── runtime.json
     * ├── template.apk
     * └── licenses/ (optional)
     * ```
     * NOTE: registration is a global side effect by design (single-template
     * host); concurrent loaders overwrite each other — serialize at call site.
     */
    fun loadFromDirectory(runtimeDir: File): RuntimeTemplate {
        require(runtimeDir.exists() && runtimeDir.isDirectory) {
            "Runtime directory does not exist: ${runtimeDir.absolutePath}"
        }

        val descriptorFile = File(runtimeDir, RuntimeDescriptor.DESCRIPTOR_FILENAME)
        require(descriptorFile.exists() && descriptorFile.isFile) {
            "Missing runtime descriptor: ${descriptorFile.absolutePath}"
        }

        val apkFile = File(runtimeDir, RuntimeTemplate.TEMPLATE_APK_FILENAME)
        require(apkFile.exists() && apkFile.isFile) {
            "Missing template APK: ${apkFile.absolutePath}"
        }

        val descriptorJson = descriptorFile.readText(Charsets.UTF_8)
        val descriptor = RuntimeDescriptor.fromJson(descriptorJson)

        val template = RuntimeTemplate(
            descriptor = descriptor,
            templateApk = apkFile
        )

        registerTemplate(template)
        return template
    }
}
