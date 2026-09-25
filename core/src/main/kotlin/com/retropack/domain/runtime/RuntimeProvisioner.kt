package com.retropack.domain.runtime

import com.retropack.packaging.ProtectedEntriesVerifier
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Abstraction over a read-only hierarchical asset container (e.g. Android
 * [android.content.res.AssetManager] on-device, or a fake in tests).
 *
 * Contract mirrors AssetManager:
 *  - [list] returns the immediate children names of [path] (files and
 *    directories are indistinguishable, an empty list means "no children"),
 *    or `null` when the container cannot answer (I/O failure).
 *  - [open] returns the byte stream of a FILE asset; opening a directory or a
 *    missing asset throws [java.io.FileNotFoundException].
 */
interface AssetSource {
    fun list(path: String): List<String>?
    fun open(path: String): InputStream
}

/**
 * Outcome of a runtime-bundle provisioning pass. Every failure mode carries a
 * human-readable, actionable message: provisioning problems must NEVER be
 * silently swallowed (the original in-app bug reported only as
 * "No runtime template registered for ID: 'mgba-unified'" at Stage 2/15 with
 * zero hints about the missing asset).
 */
sealed class RuntimeProvisionResult {

    /** Bundle extracted (or already cached) and registered in [RuntimeRegistry]. */
    data class Provisioned(
        val runtimeId: String,
        val template: RuntimeTemplate,
        val extractedFiles: Int,
        val alreadyPresent: Boolean
    ) : RuntimeProvisionResult()

    /** The template APK is not present in the asset container at all. */
    data class MissingTemplate(
        val runtimeId: String,
        val guidance: String
    ) : RuntimeProvisionResult()

    /** The template exists but does not match the compiled-in trust anchors. */
    data class IntegrityMismatch(
        val runtimeId: String,
        val details: String
    ) : RuntimeProvisionResult()

    /** Extraction or registration failed (e.g. malformed descriptor). */
    data class ExtractionFailure(
        val runtimeId: String,
        val cause: String
    ) : RuntimeProvisionResult()
}

/**
 * Extracts a pinned runtime bundle from an [AssetSource] into a writable
 * directory and registers it in [RuntimeRegistry].
 *
 * Motivation (regression): the previous in-app extractor iterated only the
 * TOP-LEVEL asset names of `mgba-unified/`. The bundle contains a `licenses/`
 * subdirectory, which sorts alphabetically BEFORE `runtime.json`; calling
 * `AssetManager.open("mgba-unified/licenses")` throws because directories
 * cannot be opened, and the surrounding `catch (_: Exception)` swallowed the
 * abort — so NOTHING was ever extracted and the template was never
 * registered, even when the manager APK actually contained it.
 *
 * This implementation:
 *  1. Recursively walks the bundle asset tree (directories recursed, files
 *     copied; zero-byte targets are re-extracted).
 *  2. Registers the on-disk bundle via [RuntimeRegistry.loadFromDirectory]
 *     even when files were previously cached (the in-memory registry does not
 *     survive process restarts, disk does).
 *  3. Pre-verifies the whole-APK digest and protected entries against the
 *     compiled-in trust anchors so misbuilt manager APKs fail FAST with a
 *     clear diagnosis instead of an opaque Stage 2 SecurityException.
 *  4. Emits every step through [log] — provisioning is never silent.
 */
object RuntimeProvisioner {

    const val BUNDLE_ROOT_DIRNAME = "runtimes"

    /**
     * Provisions the bundle for [runtimeId].
     *
     * @param targetRoot writable directory under which `<runtimeId>/` is
     *        created (e.g. `File(context.filesDir, "runtimes")`).
     * @param source read-only asset container (APK assets on-device).
     * @param runtimeId canonical runtime identifier.
     * @param trustedTemplateSha256 whole-APK trust anchor consulted for the
     *        fail-fast pre-check; defaults to the compiled-in anchor. Pass a
     *        fixture digest (or `null` to skip the whole-APK pre-check) in
     *        hermetic tests.
     * @param log diagnostic sink (terminal sheet on-device).
     */
    fun provision(
        targetRoot: File,
        source: AssetSource,
        runtimeId: String = RuntimeRegistry.RUNTIME_MGBA_UNIFIED,
        trustedTemplateSha256: String? = RuntimeRegistry.getTrustedFingerprint(runtimeId),
        log: (String) -> Unit = {}
    ): RuntimeProvisionResult {
        val bundleRoot = File(targetRoot, runtimeId)
        if (!bundleRoot.exists() && !bundleRoot.mkdirs()) {
            return RuntimeProvisionResult.ExtractionFailure(
                runtimeId,
                "Cannot create runtime bundle directory: ${bundleRoot.absolutePath}"
            )
        }

        val before = countFiles(bundleRoot)
        try {
            extractRecursively(source, runtimeId, bundleRoot, log)
        } catch (t: Throwable) {
            val cause = t.message ?: t.javaClass.simpleName
            log("[!] Runtime bundle extraction failed: $cause")
            return RuntimeProvisionResult.ExtractionFailure(runtimeId, cause)
        }
        val extractedCount = countFiles(bundleRoot) - before
        val alreadyPresent = extractedCount == 0

        val descriptorFile = File(bundleRoot, RuntimeDescriptor.DESCRIPTOR_FILENAME)
        val templateApk = File(bundleRoot, RuntimeTemplate.TEMPLATE_APK_FILENAME)

        if (!templateApk.isFile || templateApk.length() == 0L) {
            val guidance =
                "Runtime template '$runtimeId/template.apk' was not found. The manager APK " +
                    "was built WITHOUT the pinned runtime bundle embedded in its assets " +
                    "(assets/$runtimeId/template.apk missing at packaging time). Rebuild the " +
                    "manager from a checkout that contains runtimes/mgba-unified/template.apk " +
                    "(it is committed and hash-pinned) and reinstall."
            log("[!] $guidance")
            return RuntimeProvisionResult.MissingTemplate(runtimeId, guidance)
        }
        if (!descriptorFile.isFile || descriptorFile.length() == 0L) {
            val guidance =
                "Runtime descriptor '$runtimeId/runtime.json' was not extracted. The manager " +
                    "APK assets are incomplete or corrupted; reinstall the manager APK."
            log("[!] $guidance")
            return RuntimeProvisionResult.ExtractionFailure(runtimeId, guidance)
        }

        // Fail-fast whole-APK trust check (Stage 2 remains the authoritative gate).
        if (trustedTemplateSha256 != null) {
            val actual = RuntimeTemplate.computeSha256(templateApk)
            val expected = trustedTemplateSha256.removePrefix("sha256:").trim()
            if (!actual.equals(expected, ignoreCase = true)) {
                val details =
                    "template.apk digest does not match the compiled trust anchor " +
                        "(expected $expected, actual $actual). The bundle is stale or tampered; " +
                        "clear app data or reinstall the manager APK built with the matching " +
                        "pinned bundle."
                log("[!] $details")
                return RuntimeProvisionResult.IntegrityMismatch(runtimeId, details)
            }
        }

        // Register (also re-registers cached bundles after process restarts).
        val template = try {
            RuntimeRegistry.loadFromDirectory(bundleRoot)
        } catch (t: Throwable) {
            val cause = t.message ?: t.javaClass.simpleName
            log("[!] Runtime bundle registration failed: $cause")
            return RuntimeProvisionResult.ExtractionFailure(runtimeId, cause)
        }

        // Fail-fast protected-entry check (Stage 9 remains the authoritative gate).
        try {
            ProtectedEntriesVerifier.verifyApk(templateApk, template.descriptor)
        } catch (t: Throwable) {
            val cause = t.message ?: t.javaClass.simpleName
            log("[!] $cause")
            return RuntimeProvisionResult.IntegrityMismatch(runtimeId, cause)
        }

        log(
            "[i] Runtime bundle '$runtimeId' " +
                (if (alreadyPresent) "verified from cache" else "extracted ($extractedCount files)") +
                " and registered (template ${templateApk.length()} bytes)"
        )
        return RuntimeProvisionResult.Provisioned(runtimeId, template, extractedCount, alreadyPresent)
    }

    /**
     * Depth-first walk of `assets/<assetPath>` into [targetDir].
     *
     * A name that has children (or cannot be opened as a stream) is treated
     * as a directory and recursed; a name that opens cleanly is a file and is
     * copied unless a same-or-larger copy already exists on disk.
     */
    private fun extractRecursively(
        source: AssetSource,
        assetPath: String,
        targetDir: File,
        log: (String) -> Unit
    ) {
        val children = source.list(assetPath)
        if (children == null) {
            log("[i] Asset container reported no entries for '$assetPath'")
            return
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw java.io.IOException("Cannot create directory: ${targetDir.absolutePath}")
        }
        for (name in children.sorted()) {
            val childAssetPath = if (assetPath.isEmpty()) name else "$assetPath/$name"
            val childTarget = File(targetDir, name)
            val grandchildren = try {
                source.list(childAssetPath)
            } catch (_: Throwable) {
                null
            }

            // AssetManager-style semantics: list() cannot distinguish files from
            // directories, but open() succeeds ONLY for files. Treat anything
            // with children — or anything that cannot be opened — as a directory.
            val isDirectory = if (grandchildren != null && grandchildren.isNotEmpty()) {
                true
            } else {
                !opensAsFile(source, childAssetPath)
            }

            if (isDirectory) {
                extractRecursively(source, childAssetPath, childTarget, log)
            } else {
                if (childTarget.isFile && childTarget.length() > 0L) {
                    continue // cached
                }
                source.open(childAssetPath).use { input ->
                    FileOutputStream(childTarget).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun opensAsFile(source: AssetSource, path: String): Boolean = try {
        source.open(path).close()
        true
    } catch (_: Throwable) {
        false
    }

    private fun countFiles(dir: File): Int {
        if (!dir.isDirectory) return 0
        return dir.walkTopDown().count { it.isFile }
    }
}
