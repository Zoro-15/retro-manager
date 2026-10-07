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

    /**
     * Canonical Libretro Core Identifiers (matching runtimes/cores.json)
     */
    const val CORE_MGBA = "mgba"
    const val CORE_SNES9X = "snes9x"
    const val CORE_GENESIS_PLUS_GX = "genesis_plus_gx"
    const val CORE_FCEUMM = "fceumm"
    const val CORE_MEDNAFEN_PCE_FAST = "mednafen_pce_fast"

    val CANONICAL_CORE_IDS: Set<String> = setOf(
        CORE_MGBA,
        CORE_SNES9X,
        CORE_GENESIS_PLUS_GX,
        CORE_FCEUMM,
        CORE_MEDNAFEN_PCE_FAST
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
        RUNTIME_MGBA_UNIFIED to "e81d7d95593a7825dbdcef394a282265257c7e896ad8cb1175baeb63a3b2304c",
        RUNTIME_SNES9X_UNIFIED to "897329e9ce49f5712a1efa4a9ef6700bb5ad7c68f95439f8b7d13e7de35452a3",
        RUNTIME_GENESIS_UNIFIED to "9a5f0073cc7ee42236be059fc61d4b82915a1b2bd4d7c581f2f9c6f3b446e0c8",
        RUNTIME_FCEUMM_UNIFIED to "a52d2d2e5a827b71264a1de1986d59ccaa224fee636e05253ed40761e375580e",
        RUNTIME_PCE_UNIFIED to "2abd4fa9602be816e00a37636a45fffe204f3ef718b27c2e55c52edfa3119455"
    )

    /**
     * Protected entries bytecode trust anchors for Step 9 integrity validation.
     */
    val TRUSTED_PROTECTED_ENTRIES: Map<String, Map<String, String>> = mapOf(
        RUNTIME_MGBA_UNIFIED to mapOf(
            "lib/arm64-v8a/libretro_engine.so" to "4dccf8df732336322a40ef84f0337dc99ca762606d43f461c9d41f9c1a8ecaf0",
            "lib/arm64-v8a/libretro_mgba.so" to "b5a6ed40ede735ea7f1d56916f4ceed8c2aabe4d481fe3383ae2b5e5485b6545"
        ),
        RUNTIME_SNES9X_UNIFIED to mapOf(
            "lib/arm64-v8a/libretro_engine.so" to "4dccf8df732336322a40ef84f0337dc99ca762606d43f461c9d41f9c1a8ecaf0",
            "lib/arm64-v8a/libretro_snes9x.so" to "dfd51707769000b08551e61eee4fe1a68d9e6188d62bf849c762339fa6bebcd3"
        ),
        RUNTIME_GENESIS_UNIFIED to mapOf(
            "lib/arm64-v8a/libretro_engine.so" to "4dccf8df732336322a40ef84f0337dc99ca762606d43f461c9d41f9c1a8ecaf0",
            "lib/arm64-v8a/libretro_genesis_plus_gx.so" to "d062c02e8a0697832e308d62a79443c4a45501f61871b3ca912cce8b044d7d2a"
        ),
        RUNTIME_FCEUMM_UNIFIED to mapOf(
            "lib/arm64-v8a/libretro_engine.so" to "4dccf8df732336322a40ef84f0337dc99ca762606d43f461c9d41f9c1a8ecaf0",
            "lib/arm64-v8a/libretro_fceumm.so" to "38659530f800032ed6ed46ea0cdf167a38326e0ecd9afa09a3c08130020d85af"
        ),
        RUNTIME_PCE_UNIFIED to mapOf(
            "lib/arm64-v8a/libretro_engine.so" to "4dccf8df732336322a40ef84f0337dc99ca762606d43f461c9d41f9c1a8ecaf0",
            "lib/arm64-v8a/libretro_mednafen_pce_fast.so" to "00394b7a85b771ca82f926ae61f45a82060e4c66c3ed4c1aab0f5bf67fee9aa3"
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

            // Register canonical descriptors for the 5 Golden 2D cores
            registerDescriptor(RuntimeDescriptor.MGBA_UNIFIED)
            registerDescriptor(RuntimeDescriptor.SNES9X_UNIFIED)
            registerDescriptor(RuntimeDescriptor.GENESIS_UNIFIED)
            registerDescriptor(RuntimeDescriptor.FCEUMM_UNIFIED)
            registerDescriptor(RuntimeDescriptor.PCE_UNIFIED)

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
        CORE_MGBA,
        CORE_SNES9X,
        CORE_GENESIS_PLUS_GX,
        CORE_FCEUMM,
        CORE_MEDNAFEN_PCE_FAST
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
