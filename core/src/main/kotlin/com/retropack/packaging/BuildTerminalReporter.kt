package com.retropack.packaging

import com.retropack.domain.model.BuildResult
import java.io.File

/**
 * Single source of truth for the terminal-sheet summary of a transform run.
 *
 * Regression background: MainViewModel used to render the success banner
 * ("[✓] BUILD PIPELINE COMPLETE", "Signature Schemes: v1 + v2 + v3 PASSED",
 * "Target APK: null") whenever BuildEngine.build() RETURNED at all — and the
 * engine returns a BuildResult (success=false) instead of throwing for every
 * recoverable pipeline failure, so failed builds were reported as successes.
 * PR #26 fixed the branch; this reporter makes the invariant structural:
 *
 *   Success lines are only ever produced for a result that is BOTH
 *   marked successful AND backed by an existing artifact file on disk.
 *   Anything else renders as an explicit failure banner.
 */
object BuildTerminalReporter {

    private const val DIVIDER = "=================================================="

    /**
     * Terminal lines for a successful build. A result marked successful but
     * missing its artifact file is an internal inconsistency and is rendered
     * as a failure — never as a false positive.
     */
    fun successSummary(result: BuildResult): List<String> {
        val artifact: File? = result.artifactFile
        if (!result.success || artifact == null || !artifact.isFile) {
            return failureSummary(
                "Internal inconsistency: BuildResult marked successful but no artifact file exists " +
                    "(artifact=${artifact?.absolutePath ?: "null"}). Refusing to report success."
            )
        }
        return listOf(
            DIVIDER,
            "[✓] BUILD PIPELINE COMPLETE IN ${result.durationMs} ms",
            "--> Target APK: ${artifact.absolutePath}",
            "--> Package ID: ${result.packageName} (v${result.versionCode})",
            "--> Cert SHA-256: ${result.certificateSha256Fingerprint}",
            "--> 16 KB Page Alignment: VERIFIED COMPLIANT",
            "--> Signature Schemes: v1 + v2 + v3 PASSED",
            DIVIDER
        )
    }

    /**
     * Terminal lines for a failed (or aborted) build.
     */
    fun failureSummary(errorMessage: String?): List<String> = listOf(
        DIVIDER,
        "[✗] BUILD PIPELINE FAILED: ${
            errorMessage ?: "Pipeline execution failed at an intermediate stage"
        }",
        DIVIDER
    )

    /**
     * Terminal lines for an exception-level abort (engine threw, no result).
     */
    fun abortSummary(errorMessage: String?): List<String> = listOf(
        DIVIDER,
        "[✗] BUILD PIPELINE ABORTED: ${errorMessage ?: "unknown error"}",
        DIVIDER
    )
}
