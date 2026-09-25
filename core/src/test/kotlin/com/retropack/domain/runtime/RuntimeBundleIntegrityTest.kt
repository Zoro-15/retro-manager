package com.retropack.domain.runtime

import com.retropack.packaging.ProtectedEntriesVerifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * HARD gate for the pinned runtime bundle — no skip-assumptions.
 *
 * Why this test exists: the legacy fixture tests (RuntimeRegistryTest /
 * RuntimeTemplateTest) used `assumeTrue(...)` when template.apk was absent,
 * so CI stayed green for weeks while the manager APK shipped WITHOUT the
 * runtime bundle and every in-app 'retropack transform' failed at Stage 2
 * ("No runtime template registered for ID: 'mgba-unified'"). A skip is a
 * silent pass; this class makes bundle absence a FAILURE.
 *
 * The bundle (runtimes/mgba-unified/{runtime.json, template.apk,
 * licenses/}) is committed on purpose (see the .gitignore whitelist): the
 * manager embeds it as APK assets and the compiled-in trust anchors pin its
 * bytes. This test pins ALL of them together so they can never drift again.
 */
class RuntimeBundleIntegrityTest {

    private val bundleDir: File
        get() = sequenceOf(
            File("runtimes/mgba-unified"),
            File("../runtimes/mgba-unified")
        ).firstOrNull { it.isDirectory }
            ?: throw AssertionError(
                "Pinned runtime bundle runtimes/mgba-unified/ is MISSING from this checkout. " +
                    "The manager APK cannot function without it (in-app transform fails at " +
                    "Stage 2: 'No runtime template registered'). The bundle must be committed — " +
                    "restore runtimes/mgba-unified/template.apk (CI artifact or " +
                    ":template-apk:assembleDebug output) and run scripts/rotate-trust-anchors.sh."
            )

    private val templateApk: File get() = File(bundleDir, "template.apk")
    private val descriptorFile: File get() = File(bundleDir, "runtime.json")

    @Test
    fun `pinned bundle files exist`() {
        assertTrue(templateApk.isFile && templateApk.length() > 0) {
            "runtimes/mgba-unified/template.apk must be committed (gitignore whitelist: !runtimes/**/*.apk)"
        }
        assertTrue(descriptorFile.isFile && descriptorFile.length() > 0) {
            "runtimes/mgba-unified/runtime.json must be committed"
        }
        assertTrue(File(bundleDir, "licenses").isDirectory) {
            "runtimes/mgba-unified/licenses/ must be committed"
        }
    }

    @Test
    fun `template apk digest matches compiled-in TRUSTED_TEMPLATES anchor`() {
        val expected = RuntimeRegistry.TRUSTED_TEMPLATES[RuntimeRegistry.RUNTIME_MGBA_UNIFIED]
        assertNotNull(expected) { "TRUSTED_TEMPLATES must pin mgba-unified" }

        val actual = RuntimeTemplate.computeSha256(templateApk)
        assertEquals(
            expected!!.removePrefix("sha256:").lowercase(),
            actual.lowercase(),
            "template.apk bytes drifted from the TRUSTED_TEMPLATES anchor. " +
                "Rotate anchors in lockstep: run scripts/rotate-trust-anchors.sh"
        )
    }

    @Test
    fun `descriptor file protected entries match actual template apk entries`() {
        val descriptor = RuntimeDescriptor.fromJson(descriptorFile.readText(Charsets.UTF_8))
        assertTrue(descriptor.protectedEntries.isNotEmpty()) {
            "runtime.json must declare protected_entries (classes.dex + native lib)"
        }
        // Exact Step 9 code path: verifyApk reads the APK entries and compares
        // digests (with sha256: prefix normalization).
        ProtectedEntriesVerifier.verifyApk(templateApk, descriptor)
    }

    @Test
    fun `descriptor entry names match the real native library produced by the NDK build`() {
        val descriptor = RuntimeDescriptor.fromJson(descriptorFile.readText(Charsets.UTF_8))
        val nativeEntries = descriptor.protectedEntries.keys.filter { it.startsWith("lib/") }

        assertTrue(nativeEntries.isNotEmpty()) { "at least one lib/ entry must be pinned" }
        // The NDK CMake target is 'retropack-runtime' (System.loadLibrary
        // (\"retropack-runtime\")); pinning any other .so name (the old
        // 'libmgba.so' placeholder) makes Step 9 unpassable.
        nativeEntries.forEach { entry ->
            assertTrue(entry.endsWith("libretropack-runtime.so")) {
                "protected entry '$entry' does not exist in built templates; the NDK library " +
                    "is libretropack-runtime.so — update runtime.json and the trust anchors"
            }
        }
    }

    @Test
    fun `loadFromDirectory registers the template retrievable via getTemplate`() {
        // The exact production registration path exercised by the on-device
        // provisioner; the user-visible bug was getTemplate() returning null.
        RuntimeRegistry.resetToDefaults()
        val template = RuntimeRegistry.loadFromDirectory(bundleDir)

        assertEquals(RuntimeRegistry.RUNTIME_MGBA_UNIFIED, template.descriptor.id)
        assertNotNull(RuntimeRegistry.getTemplate(RuntimeRegistry.RUNTIME_MGBA_UNIFIED)) {
            "template must be registered and retrievable — this is the exact regression " +
                "behind 'No runtime template registered for ID: mgba-unified'"
        }
        RuntimeRegistry.resetToDefaults()
    }

    @Test
    fun `compiled-in descriptor and descriptor file agree in lockstep`() {
        val fromFile = RuntimeDescriptor.fromJson(descriptorFile.readText(Charsets.UTF_8))
        val compiled = RuntimeDescriptor.MGBA_UNIFIED

        assertEquals(compiled.id, fromFile.id, "descriptor id drift")
        assertEquals(compiled.version, fromFile.version, "descriptor version drift")
        assertEquals(compiled.supportedPlatforms, fromFile.supportedPlatforms, "platforms drift")
        assertEquals(compiled.supportedAbis, fromFile.supportedAbis, "abis drift")
        assertEquals(
            compiled.protectedEntries.entries.associate { (k, v) -> k to v.removePrefix("sha256:").lowercase() },
            fromFile.protectedEntries.entries.associate { (k, v) -> k to v.removePrefix("sha256:").lowercase() },
            "compiled-in anchor map and runtime.json protected_entries drifted apart — " +
                "rotate both in lockstep via scripts/rotate-trust-anchors.sh"
        )
    }

    @Test
    fun `tampered template fails verification (guard against weakening the gate)`() {
        // Sanity-check that the verifier actually bites: a descriptor with a
        // bogus digest must throw, proving the green tests above are meaningful.
        val descriptor = RuntimeDescriptor.fromJson(descriptorFile.readText(Charsets.UTF_8))
        val tampered = descriptor.copy(
            protectedEntries = descriptor.protectedEntries.entries.associate { (k, v) ->
                k to v.replaceRange(0..7, "deadbeef")
            }
        )
        val threw = try {
            ProtectedEntriesVerifier.verifyApk(templateApk, tampered)
            false
        } catch (_: SecurityException) {
            true
        }
        assertTrue(threw) { "tampered digests must be rejected" }
    }
}
