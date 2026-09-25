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

            // Register default mgba-unified descriptor
            registerDescriptor(RuntimeDescriptor.MGBA_UNIFIED)

            // Register canonical platform associations
            registerPlatformMapping("gb", RUNTIME_MGBA_UNIFIED)
            registerPlatformMapping("gbc", RUNTIME_MGBA_UNIFIED)
            registerPlatformMapping("gba", RUNTIME_MGBA_UNIFIED)
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
        return registeredDescriptors[runtimeId]
    }

    /**
     * Finds all compatible runtime descriptors for the specified [platform].
     */
    fun findRuntimesForPlatform(platform: String): List<RuntimeDescriptor> {
        val normalized = platform.lowercase().trim().removePrefix(".")
        val runtimeIds = platformMappings[normalized] ?: return emptyList()
        return runtimeIds.mapNotNull { registeredDescriptors[it] }
    }

    /**
     * Retrieves a registered descriptor by [runtimeId].
     */
    fun getDescriptor(runtimeId: String): RuntimeDescriptor? {
        return registeredDescriptors[runtimeId]
    }

    /**
     * Retrieves a registered template by [runtimeId].
     */
    fun getTemplate(runtimeId: String): RuntimeTemplate? {
        return registeredTemplates[runtimeId]
    }

    /**
     * Retrieves the compiled bytecode trust anchor for [runtimeId].
     */
    fun getTrustedFingerprint(runtimeId: String): String? {
        return TRUSTED_TEMPLATES[runtimeId]
    }

    /**
     * Validates whether [actualSha256] matches the bytecode-anchored trusted fingerprint for [runtimeId].
     */
    fun isTrustedTemplate(runtimeId: String, actualSha256: String): Boolean {
        val trusted = TRUSTED_TEMPLATES[runtimeId] ?: return false
        val normalizedActual = actualSha256.removePrefix("sha256:").trim()
        val normalizedTrusted = trusted.removePrefix("sha256:").trim()
        return normalizedActual.equals(normalizedTrusted, ignoreCase = true)
    }

    /**
     * Cryptographically validates actual entry digests against compiled bytecode trust anchors.
     * Enforces BuildEngine Step 9 (Allowlist Policy).
     */
    fun verifyProtectedEntries(runtimeId: String, actualEntries: Map<String, String>): Boolean {
        val trustedMap = TRUSTED_PROTECTED_ENTRIES[runtimeId] ?: return false
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

        val licensesDir = File(runtimeDir, "licenses").takeIf { it.exists() && it.isDirectory }

        val template = RuntimeTemplate(
            descriptor = descriptor,
            templateApk = apkFile,
            licensesDir = licensesDir
        )

        registerTemplate(template)
        return template
    }
}
