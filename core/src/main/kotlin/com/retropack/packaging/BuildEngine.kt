package com.retropack.packaging

import com.retropack.domain.model.BuildRequest
import com.retropack.domain.model.BuildResult
import com.retropack.domain.model.BuildStageRecord
import com.retropack.domain.rom.GbRomParser
import com.retropack.domain.rom.GbaRomParser
import com.retropack.domain.rom.StreamChecksum
import com.retropack.domain.rom.StreamChecksumResult
import com.retropack.domain.runtime.RuntimeRegistry
import com.retropack.security.SigningIdentity
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Orchestrates the 15-step on-device APK transformation pipeline.
 *
 * Implements specifications from:
 * - masterplan.md Section 6: The 15-Step Verified Transformation Pipeline.
 * - architechture.md Section 5: Packaging Boundaries & Allowlist Policy.
 * - architechture.md Section 7: Low-Level Android Invariants & Physics.
 */
object BuildEngine {

    const val MANAGER_VERSION = "0.1.0"

    /**
     * Executes the complete 15-step transformation pipeline.
     */
    fun build(
        request: BuildRequest,
        romBytes: ByteArray,
        signingIdentity: SigningIdentity,
        outputDir: File,
        iconForegroundBytes: ByteArray? = null,
        iconBackgroundBytes: ByteArray? = null,
        templateOverride: File? = null,
        stageListener: ((BuildStageRecord) -> Unit)? = null
    ): BuildResult {
        val totalStartTime = System.currentTimeMillis()
        val stageRecords = mutableListOf<BuildStageRecord>()

        var scratchApk: File? = null
        var signedScratchApk: File? = null

        fun recordStage(
            stageNum: Int,
            name: String,
            desc: String,
            startTime: Long,
            action: () -> Unit
        ) {
            try {
                action()
                val duration = System.currentTimeMillis() - startTime
                val record = BuildStageRecord(
                    stageNumber = stageNum,
                    stageName = name,
                    description = desc,
                    durationMs = duration,
                    passed = true
                )
                stageRecords.add(record)
                stageListener?.invoke(record)
            } catch (t: Throwable) {
                val duration = System.currentTimeMillis() - startTime
                val record = BuildStageRecord(
                    stageNumber = stageNum,
                    stageName = name,
                    description = "$desc (Failed: ${t.message})",
                    durationMs = duration,
                    passed = false
                )
                stageRecords.add(record)
                stageListener?.invoke(record)
                throw t
            }
        }

        try {
            // STEP 1: Streamed Checksums & Header Analysis
            lateinit var checksumResult: StreamChecksumResult
            recordStage(1, "Checksums & Header Analysis", "Calculating streamed checksums and validating ROM headers", System.currentTimeMillis()) {
                require(romBytes.isNotEmpty()) { "ROM bytes cannot be empty" }
                checksumResult = StreamChecksum.calculate(ByteArrayInputStream(romBytes))

                when (request.content.platform.lowercase(Locale.ROOT)) {
                    "gb", "gbc" -> {
                        GbRomParser.parse(romBytes)
                    }
                    "gba" -> {
                        GbaRomParser.parse(romBytes)
                    }
                }
            }

            // STEP 2: Template Integrity Verification
            lateinit var templateFile: File
            recordStage(2, "Template Integrity Verification", "Verifying precompiled generic template trust anchor", System.currentTimeMillis()) {
                val templateId = request.runtime.templateId
                if (templateOverride != null) {
                    require(templateOverride.exists()) { "Template override file does not exist: ${templateOverride.absolutePath}" }
                    templateFile = templateOverride
                } else {
                    val template = RuntimeRegistry.getTemplate(templateId)
                        ?: throw IllegalStateException(
                            "No runtime template registered for ID: '$templateId'. " +
                                "The runtime bundle was never provisioned on this device: the manager APK " +
                                "is missing assets/runtimes/$templateId/template.apk (the pinned bundle was " +
                                "not committed / not embedded at build time), or on-device extraction " +
                                "failed before registration. Rebuild the manager APK from a checkout " +
                                "containing runtimes/mgba-unified/template.apk and try again."
                        )
                    templateFile = template.templateApk
                    require(templateFile.exists()) { "Runtime template APK not found at: ${templateFile.absolutePath}" }

                    // Assert bytecode-compiled trust anchor
                    val expectedHash = RuntimeRegistry.TRUSTED_TEMPLATES[templateId]
                    if (expectedHash != null) {
                        val actualHash = computeFileSha256(templateFile)
                        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                            throw SecurityException(
                                "Runtime template integrity failure for '$templateId'! " +
                                    "Expected: $expectedHash, Actual: $actualHash"
                            )
                        }
                    }
                }
            }

            // STEP 3: Pre-Flight Package & Signer Check
            lateinit var packageIdentity: PackageIdentity
            recordStage(3, "Pre-Flight Package & Signer Check", "Deriving deterministic package ID and validating signing identity", System.currentTimeMillis()) {
                packageIdentity = PackageIdentity.create(
                    gameTitle = request.identity.gameTitle,
                    romBytes = romBytes,
                    versionCode = request.identity.versionCode,
                    versionName = request.identity.versionName
                )
                signingIdentity.certificate.checkValidity()
            }

            // STEP 4: Atomic Scratch File Preparation
            lateinit var targetApkFile: File
            recordStage(4, "Atomic Scratch File Preparation", "Allocating temporary scratch APK in target directory", System.currentTimeMillis()) {
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }
                val apkName = "${packageIdentity.packageName}-v${packageIdentity.versionCode}.apk"
                targetApkFile = File(outputDir, apkName)
                scratchApk = ScratchAllocator.allocateScratchFile(targetApkFile)
            }

            // STEP 5: Signature Residue Stripping (Pre-cleared in ZipArchiveTransformer)
            recordStage(5, "Signature Residue Stripping", "Configuring filter to strip stale META-INF signatures and signing blocks", System.currentTimeMillis()) {
                // Handled during archive transformation via ScratchAllocator.isSignatureResidue
            }

            // STEP 6: Streamed Asset Injection
            lateinit var assetEntries: Map<String, ByteArray>
            recordStage(6, "Streamed Asset Injection", "Generating retropack.json config and preparing game.rom asset", System.currentTimeMillis()) {
                val configJson = generateRuntimeConfigJson(request, checksumResult.checksums.sha256)
                assetEntries = RomAssetInjector.prepareAssetEntries(romBytes, configJson)
            }

            // STEP 7: Structured AXML Mutation
            lateinit var mutatedManifestBytes: ByteArray
            recordStage(7, "Structured AXML Mutation", "Mutating AndroidManifest.xml binary XML via ARSCLib", System.currentTimeMillis()) {
                val rawManifestBytes = ZipFile(templateFile).use { zip ->
                    val entry = zip.getEntry("AndroidManifest.xml")
                        ?: throw IllegalStateException("AndroidManifest.xml missing from template APK")
                    zip.getInputStream(entry).use { it.readBytes() }
                }

                mutatedManifestBytes = AxmlMutator.mutate(
                    manifestBytes = rawManifestBytes,
                    packageIdentity = packageIdentity,
                    gameTitle = request.identity.gameTitle
                )
            }

            // STEP 8: Adaptive Icon Replacement
            var iconEntries: Map<String, ByteArray>? = null
            recordStage(8, "Adaptive Icon Replacement", "Validating and preparing adaptive launcher icon drawables", System.currentTimeMillis()) {
                if (iconForegroundBytes != null) {
                    iconEntries = IconInjector.prepareIconEntries(iconForegroundBytes, iconBackgroundBytes)
                }
            }

            // STEP 9: Protected Entries Invariant Verification
            recordStage(9, "Protected Entries Invariant Verification", "Cryptographically validating classes.dex and native libraries", System.currentTimeMillis()) {
                if (templateOverride == null) {
                    val descriptor = RuntimeRegistry.getDescriptor(request.runtime.templateId)
                    if (descriptor != null && descriptor.protectedEntries.isNotEmpty()) {
                        ProtectedEntriesVerifier.verifyApk(templateFile, descriptor)
                    }
                }
            }

            // STEP 10: 16 KB Page Zipalign (com.android:zipflinger)
            recordStage(10, "16 KB Page Zipalign", "Assembling archive and aligning uncompressed .so libraries to 16,384 bytes", System.currentTimeMillis()) {
                val allInjected = mutableMapOf<String, ByteArray>()
                allInjected["AndroidManifest.xml"] = mutatedManifestBytes
                allInjected.putAll(assetEntries)
                if (iconEntries != null) {
                    allInjected.putAll(iconEntries!!)
                }

                ZipArchiveTransformer.transform(
                    templateApk = templateFile,
                    outputApk = scratchApk!!,
                    injectedEntries = allInjected
                )
            }

            // STEP 11: Alignment Verification (Assertion)
            recordStage(11, "Alignment Verification", "Programmatically asserting (local_header_payload_offset % 16384 == 0)", System.currentTimeMillis()) {
                AlignmentVerifier.assertCompliant(scratchApk!!)
            }

            // STEP 12: Cryptographic Signing (apksig v1 + v2 + v3)
            recordStage(12, "Cryptographic Signing", "Applying APK Signature Schemes v1, v2, and v3 simultaneously", System.currentTimeMillis()) {
                signedScratchApk = ScratchAllocator.allocateScratchFile(targetApkFile)
                ApkSignerService.signApk(
                    inputApk = scratchApk!!,
                    outputApk = signedScratchApk!!,
                    privateKey = signingIdentity.privateKey,
                    certificateChain = listOf(signingIdentity.certificate),
                    minSdkVersion = 24,
                    v1SigningEnabled = true,
                    v2SigningEnabled = true,
                    v3SigningEnabled = true
                )
                // Clean up un-signed scratch archive
                scratchApk?.delete()
            }

            // STEP 13: Post-Signing Integrity Verification (ApkVerifier)
            recordStage(13, "Post-Signing Integrity Verification", "Programmatically verifying signatures, chunk hashes, and manifest sanity", System.currentTimeMillis()) {
                ApkVerificationService.assertVerified(signedScratchApk!!)
            }

            // STEP 14: Atomic Finalization
            recordStage(14, "Atomic Finalization", "Atomically swapping signed scratch APK to final destination", System.currentTimeMillis()) {
                ScratchAllocator.atomicFinalize(signedScratchApk!!, targetApkFile)
            }

            // STEP 15: Emit Verified BuildResult
            val totalDuration = System.currentTimeMillis() - totalStartTime
            val certFingerprint = computeCertificateFingerprint(signingIdentity.certificate)

            val finalRecord = BuildStageRecord(
                stageNumber = 15,
                stageName = "Emit Verified BuildResult",
                description = "Emitted verified BuildResult with complete stage provenance",
                durationMs = 1,
                passed = true
            )
            stageRecords.add(finalRecord)
            stageListener?.invoke(finalRecord)

            return BuildResult(
                success = true,
                artifactFile = targetApkFile,
                packageName = packageIdentity.packageName,
                versionCode = packageIdentity.versionCode,
                certificateSha256Fingerprint = certFingerprint,
                stageProvenance = stageRecords,
                durationMs = totalDuration,
                errorMessage = null
            )
        } catch (e: Exception) {
            // Clean up any remaining temporary scratch files
            scratchApk?.delete()
            signedScratchApk?.delete()

            val totalDuration = System.currentTimeMillis() - totalStartTime
            return BuildResult(
                success = false,
                artifactFile = null,
                packageName = request.identity.packageName,
                versionCode = request.identity.versionCode,
                certificateSha256Fingerprint = "",
                stageProvenance = stageRecords,
                durationMs = totalDuration,
                errorMessage = e.message ?: e.javaClass.simpleName
            )
        }
    }

    private fun generateRuntimeConfigJson(request: BuildRequest, romSha256: String): String {
        val game = request.identity
        val runtime = request.runtime
        val controls = request.controls
        val storage = request.storage

        return """
        {
          "${'$'}schema": "https://retropack.org/schemas/v1/runtime-config.json",
          "schema_version": 1,
          "game": {
            "id": "${escapeJson(game.gameId)}",
            "title": "${escapeJson(game.gameTitle)}",
            "platform": "${escapeJson(request.content.platform)}",
            "rom_sha256": "$romSha256"
          },
          "runtime": {
            "core": "mgba",
            "audio_sample_rate": ${runtime.audio.sampleRate},
            "audio_buffer_size": ${runtime.audio.bufferSize},
            "video_scale_mode": "${escapeJson(runtime.video.scaleMode)}"
          },
          "controls": {
            "touch_enabled": ${controls.touch.enabled},
            "touch_opacity": ${controls.touch.opacity},
            "haptics": ${controls.touch.haptics}
          },
          "storage": {
            "save_type": "${escapeJson(storage.saveType)}",
            "periodic_flush_interval_sec": ${storage.periodicFlushIntervalSec}
          },
          "provenance": {
            "manager_version": "$MANAGER_VERSION",
            "build_timestamp_utc": "${Instant.now()}"
          }
        }
        """.trimIndent()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun computeFileSha256(file: File): String {
        return StreamChecksum.calculate(file).checksums.sha256
    }

    fun computeCertificateFingerprint(cert: X509Certificate): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(cert.encoded).joinToString("") { "%02x".format(it).lowercase(Locale.ROOT) }
    }
}
