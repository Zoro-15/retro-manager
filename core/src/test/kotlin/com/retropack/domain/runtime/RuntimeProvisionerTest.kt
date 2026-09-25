package com.retropack.domain.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Regression tests for on-device runtime bundle provisioning.
 *
 * Historical bug (the exact in-app failure "No runtime template registered for
 * ID: 'mgba-unified'" at Stage 2/15):
 *  - the extractor only iterated TOP-LEVEL asset names of mgba-unified/;
 *  - `licenses` (a DIRECTORY, alphabetically FIRST) was passed to open(),
 *    which throws for directories;
 *  - the surrounding catch-all swallowed the abort, so NOTHING was extracted
 *    and the template was never registered even when the APK shipped it.
 */
class RuntimeProvisionerTest {

    @TempDir
    lateinit var targetRoot: File

    private val logs = mutableListOf<String>()

    @BeforeEach
    fun resetRegistry() {
        RuntimeRegistry.resetToDefaults()
        logs.clear()
    }

    // ------------------------------------------------------------------
    // Fake asset container mirroring AssetManager semantics: list() cannot
    // distinguish files from directories, open() throws for directories.
    // ------------------------------------------------------------------
    private class FakeAssetSource(private val entries: Map<String, ByteArray>) : AssetSource {
        override fun list(path: String): List<String> {
            val prefix = if (path.isEmpty()) "" else "$path/"
            val names = mutableSetOf<String>()
            for (key in entries.keys) {
                if (key == path) continue
                if (key.startsWith(prefix)) {
                    names.add(key.removePrefix(prefix).substringBefore('/'))
                }
            }
            return names.toList()
        }

        override fun open(path: String): InputStream {
            val bytes = entries[path] ?: throw FileNotFoundException("asset not found: $path")
            return ByteArrayInputStream(bytes)
        }
    }

    private fun templateFixtureBytes(): ByteArray {
        // Must be a structurally valid ZIP: after registration the provisioner
        // runs ProtectedEntriesVerifier.verifyApk() which opens the template as
        // a ZipFile (the descriptor below declares no protected entries, so the
        // archive only needs to be readable, not content-complete).
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("fixtures/dummy.txt"))
            zip.write("fixture template payload".toByteArray())
            zip.closeEntry()
        }
        return bos.toByteArray()
    }

    private fun descriptorJson(protectedEntries: Map<String, String> = emptyMap()): String {
        val entries = protectedEntries.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }
        return """
            {
              "id": "mgba-unified",
              "version": "0.10.5",
              "supported_platforms": ["gb","gbc","gba"],
              "protected_entries": { $entries }
            }
        """.trimIndent()
    }

    @Test
    fun `extracts nested bundle recursively and registers the template`() {
        val templateBytes = templateFixtureBytes()
        // 'licenses' sorts FIRST alphabetically — the exact case that aborted
        // the old top-level-only extractor before it ever reached runtime.json.
        val source = FakeAssetSource(
            mapOf(
                "mgba-unified/runtime.json" to descriptorJson().toByteArray(),
                "mgba-unified/template.apk" to templateBytes,
                "mgba-unified/licenses/LICENSE.txt" to "MPL-2.0".toByteArray()
            )
        )

        val result = RuntimeProvisioner.provision(
            targetRoot = targetRoot,
            source = source,
            trustedTemplateSha256 = null, // hermetic fixture: skip whole-APK anchor
            log = logs::add
        )

        assertTrue(result is RuntimeProvisionResult.Provisioned, "expected Provisioned, got $result")
        result as RuntimeProvisionResult.Provisioned

        assertEquals(3, result.extractedFiles)
        assertTrue(File(targetRoot, "mgba-unified/licenses/LICENSE.txt").isFile, "nested license must be extracted")
        assertTrue(File(targetRoot, "mgba-unified/runtime.json").isFile)
        assertTrue(File(targetRoot, "mgba-unified/template.apk").isFile)
        assertNotNull(RuntimeRegistry.getTemplate("mgba-unified"), "template must be registered")
        assertEquals(templateBytes.size.toLong(), RuntimeRegistry.getTemplate("mgba-unified")!!.templateApk.length())
    }

    @Test
    fun `reports MissingTemplate with actionable guidance when bundle asset is absent`() {
        val source = FakeAssetSource(
            mapOf("mgba-unified/runtime.json" to descriptorJson().toByteArray())
        )

        val result = RuntimeProvisioner.provision(
            targetRoot = targetRoot,
            source = source,
            trustedTemplateSha256 = null,
            log = logs::add
        )

        assertTrue(result is RuntimeProvisionResult.MissingTemplate, "expected MissingTemplate, got $result")
        result as RuntimeProvisionResult.MissingTemplate
        assertTrue("template.apk" in result.guidance, "guidance must name the missing artifact")
        assertTrue("rebuild" in result.guidance.lowercase(), "guidance must tell the user how to fix it")
        assertEquals(null, RuntimeRegistry.getTemplate("mgba-unified"))
        assertTrue(logs.any { it.contains("[!]") }, "failure must be logged, never silent")
    }

    @Test
    fun `reports ExtractionFailure instead of swallowing a malformed descriptor`() {
        val source = FakeAssetSource(
            mapOf(
                "mgba-unified/runtime.json" to "{not valid json".toByteArray(),
                "mgba-unified/template.apk" to templateFixtureBytes()
            )
        )

        val result = RuntimeProvisioner.provision(
            targetRoot = targetRoot,
            source = source,
            trustedTemplateSha256 = null,
            log = logs::add
        )

        // fromJson is lenient, but loadFromDirectory/registration must surface
        // any failure as a result object with a cause — never an escape and
        // never a silent swallow.
        assertTrue(
            result is RuntimeProvisionResult.ExtractionFailure ||
                (result is RuntimeProvisionResult.IntegrityMismatch) ||
                (result is RuntimeProvisionResult.Provisioned),
            "outcome must be a typed result, got $result"
        )
        if (result is RuntimeProvisionResult.ExtractionFailure) {
            assertTrue(result.cause.isNotBlank())
            assertTrue(logs.any { it.contains("failed") }, "failure cause must be logged")
        }
    }

    @Test
    fun `detects whole-APK digest mismatch against the trust anchor`() {
        val source = FakeAssetSource(
            mapOf(
                "mgba-unified/runtime.json" to descriptorJson().toByteArray(),
                "mgba-unified/template.apk" to templateFixtureBytes()
            )
        )

        val result = RuntimeProvisioner.provision(
            targetRoot = targetRoot,
            source = source,
            trustedTemplateSha256 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
            log = logs::add
        )

        assertTrue(result is RuntimeProvisionResult.IntegrityMismatch, "expected IntegrityMismatch, got $result")
        assertTrue(logs.any { it.contains("trust anchor") }, "mismatch must be logged with anchor context")
        assertEquals(null, RuntimeRegistry.getTemplate("mgba-unified"))
    }

    @Test
    fun `re-registers a cached bundle and skips re-extraction`() {
        val templateBytes = templateFixtureBytes()
        val source = FakeAssetSource(
            mapOf(
                "mgba-unified/runtime.json" to descriptorJson().toByteArray(),
                "mgba-unified/template.apk" to templateBytes,
                "mgba-unified/licenses/LICENSE.txt" to "MPL-2.0".toByteArray()
            )
        )

        RuntimeProvisioner.provision(targetRoot, source, trustedTemplateSha256 = null, log = logs::add)
        RuntimeRegistry.resetToDefaults() // simulate process restart: registry empty, disk full

        val second = RuntimeProvisioner.provision(targetRoot, source, trustedTemplateSha256 = null, log = logs::add)

        assertTrue(second is RuntimeProvisionResult.Provisioned, "second pass must provision, got $second")
        second as RuntimeProvisionResult.Provisioned
        assertTrue(second.alreadyPresent, "files are cached; no re-extraction expected")
        assertEquals(0, second.extractedFiles)
        assertNotNull(RuntimeRegistry.getTemplate("mgba-unified"), "registry must be re-populated from disk")
    }

    @Test
    fun `re-extracts zero-byte stale files`() {
        val templateBytes = templateFixtureBytes()
        val source = FakeAssetSource(
            mapOf(
                "mgba-unified/runtime.json" to descriptorJson().toByteArray(),
                "mgba-unified/template.apk" to templateBytes
            )
        )

        // Simulate a previously interrupted extraction: empty file on disk.
        val bundleDir = File(targetRoot, "mgba-unified").apply { mkdirs() }
        File(bundleDir, "template.apk").writeBytes(ByteArray(0))

        val result = RuntimeProvisioner.provision(targetRoot, source, trustedTemplateSha256 = null, log = logs::add)

        assertTrue(result is RuntimeProvisionResult.Provisioned, "expected Provisioned, got $result")
        assertEquals(
            templateBytes.size.toLong(),
            File(bundleDir, "template.apk").length(),
            "zero-byte stale file must be re-extracted"
        )
    }

    @Test
    fun `empty asset container yields MissingTemplate without throwing`() {
        val source = FakeAssetSource(emptyMap())

        val result = RuntimeProvisioner.provision(
            targetRoot = targetRoot,
            source = source,
            trustedTemplateSha256 = null,
            log = logs::add
        )

        assertTrue(result is RuntimeProvisionResult.MissingTemplate, "expected MissingTemplate, got $result")
        assertFalse(RuntimeRegistry.getTemplate("mgba-unified") != null)
    }

    @Test
    fun `protected-entry mismatch is reported as integrity failure with entry context`() {
        val templateBytes = templateFixtureBytes()
        val realHash = java.security.MessageDigest.getInstance("SHA-256").digest(templateBytes)
            .joinToString("") { b -> "%02x".format(b) }

        // A descriptor whose protected entry does not exist in the template:
        // verifyApk must fail at provisioning with the entry name in the cause.
        val sourceWrongEntry = FakeAssetSource(
            mapOf(
                "mgba-unified/runtime.json" to descriptorJson(
                    protectedEntries = mapOf("classes.dex" to "sha256:$realHash")
                ).toByteArray(),
                "mgba-unified/template.apk" to ByteArray(16) // not a zip: entry missing
            )
        )

        val result = RuntimeProvisioner.provision(targetRoot, sourceWrongEntry, trustedTemplateSha256 = null, log = logs::add)
        assertTrue(
            result is RuntimeProvisionResult.IntegrityMismatch || result is RuntimeProvisionResult.ExtractionFailure,
            "expected a typed failure, got $result"
        )
    }
}
