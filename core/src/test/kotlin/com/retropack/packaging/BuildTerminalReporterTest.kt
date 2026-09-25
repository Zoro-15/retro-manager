package com.retropack.packaging

import com.retropack.domain.model.BuildResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Regression tests against the false-positive build-success reporting.
 *
 * Historical symptom (in-app terminal, 2026-09-25 23:14):
 *   [✗] Stage 2/15: Template Integrity Verification (FAILED: ...)
 *   ==================================================
 *   [✓] BUILD PIPELINE COMPLETE IN 4 ms
 *   --> Target APK: null
 *   --> 16 KB Page Alignment: VERIFIED COMPLIANT
 *   --> Signature Schemes: v1 + v2 + v3 PASSED
 *
 * A failed pipeline was rendered as COMPLETE with verification banners that
 * never ran. These tests pin the invariant: success lines are only produced
 * for successful results backed by an existing artifact file.
 */
class BuildTerminalReporterTest {

    @TempDir
    lateinit var tempDir: File

    private fun failedResult(errorMessage: String? = "No runtime template registered for ID: 'mgba-unified'") =
        BuildResult(
            success = false,
            artifactFile = null,
            packageName = "com.retropack.game.pokemongldaaue_fb0016d27b",
            versionCode = 1,
            certificateSha256Fingerprint = "",
            stageProvenance = emptyList(),
            durationMs = 10,
            errorMessage = errorMessage
        )

    @Test
    fun `failed result never renders success banner or verification claims`() {
        val lines = BuildTerminalReporter.failureSummary(failedResult().errorMessage)

        val rendered = lines.joinToString("\n")
        assertTrue(rendered.contains("BUILD PIPELINE FAILED"), "must carry the failure banner")
        assertFalse(rendered.contains("PIPELINE COMPLETE"), "must never claim completion")
        assertFalse(rendered.contains("PASSED"), "must never claim verification PASSED")
        assertFalse(rendered.contains("VERIFIED COMPLIANT"), "must never claim compliance")
        assertFalse(rendered.contains("Target APK"), "must not render a null artifact line")
        assertNull(failedResult().artifactFile)
    }

    @Test
    fun `success marked result without artifact file is rendered as failure`() {
        val inconsistent = BuildResult(
            success = true,
            artifactFile = null,
            packageName = "com.retropack.game.x",
            versionCode = 1,
            certificateSha256Fingerprint = "aa",
            stageProvenance = emptyList(),
            durationMs = 5,
            errorMessage = null
        )

        val lines = BuildTerminalReporter.successSummary(inconsistent)

        val rendered = lines.joinToString("\n")
        assertFalse(rendered.contains("PIPELINE COMPLETE"), "no artifact -> no success banner")
        assertTrue(rendered.contains("BUILD PIPELINE FAILED"), "inconsistency must render as failure")
        assertTrue(rendered.contains("Internal inconsistency"), "must explain the inconsistency")
    }

    @Test
    fun `success marked result pointing at missing file is rendered as failure`() {
        val ghostArtifact = BuildResult(
            success = true,
            artifactFile = File(tempDir, "never-written.apk"),
            packageName = "com.retropack.game.x",
            versionCode = 1,
            certificateSha256Fingerprint = "aa",
            stageProvenance = emptyList(),
            durationMs = 5,
            errorMessage = null
        )

        val lines = BuildTerminalReporter.successSummary(ghostArtifact)

        assertTrue(lines.joinToString("\n").contains("BUILD PIPELINE FAILED"))
    }

    @Test
    fun `genuine success renders the complete banner with real artifact path`() {
        val artifact = File(tempDir, "com.retropack.game.z-v1.apk").apply { writeBytes(byteArrayOf(0x50, 0x4b)) }
        val good = BuildResult(
            success = true,
            artifactFile = artifact,
            packageName = "com.retropack.game.z",
            versionCode = 1,
            certificateSha256Fingerprint = "deadbeef",
            stageProvenance = emptyList(),
            durationMs = 1234,
            errorMessage = null
        )

        val lines = BuildTerminalReporter.successSummary(good)

        val rendered = lines.joinToString("\n")
        assertTrue(rendered.contains("[✓] BUILD PIPELINE COMPLETE IN 1234 ms"))
        assertTrue(rendered.contains(artifact.absolutePath))
        assertTrue(rendered.contains("com.retropack.game.z (v1)"))
        assertTrue(rendered.contains("Signature Schemes: v1 + v2 + v3 PASSED"))
    }

    @Test
    fun `abort summary never claims completion`() {
        val rendered = BuildTerminalReporter.abortSummary("boom").joinToString("\n")
        assertTrue(rendered.contains("BUILD PIPELINE ABORTED"))
        assertFalse(rendered.contains("PIPELINE COMPLETE"))
    }

    @Test
    fun `failure summary defaults null error to explicit message`() {
        val rendered = BuildTerminalReporter.failureSummary(null).joinToString("\n")
        assertTrue(rendered.contains("intermediate stage"))
    }
}
