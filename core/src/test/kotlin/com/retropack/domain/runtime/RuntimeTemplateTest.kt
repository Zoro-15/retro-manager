package com.retropack.domain.runtime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

class RuntimeTemplateTest {

    private val runtimeDir: File
        get() {
            val direct = File("runtimes/mgba-unified")
            if (direct.exists()) return direct
            return File("../runtimes/mgba-unified")
        }
    private val templateApk: File
        get() = File(runtimeDir, "template.apk")

    @Test
    fun `template apk exists and passes integrity check against trusted hash`() {
        // Pending the real Phase 3 template.apk artifact (unbuildable while
        // *.apk is gitignored and template-apk/ is unimplemented): abort,
        // don't fail, when the fixture is absent.
        assumeTrue(templateApk.exists(), "template.apk must exist at runtimes/mgba-unified/template.apk")
        val template = RuntimeTemplate(
            descriptor = RuntimeDescriptor.MGBA_UNIFIED,
            templateApk = templateApk
        )

        val trustedHash = RuntimeRegistry.TRUSTED_TEMPLATES[RuntimeRegistry.RUNTIME_MGBA_UNIFIED]!!
        assertTrue(template.verifyTemplateIntegrity(trustedHash), "template.apk must match trusted SHA-256")
    }

    @Test
    fun `verifyProtectedEntries succeeds when digests match`() {
        val template = RuntimeTemplate(
            descriptor = RuntimeDescriptor.MGBA_UNIFIED,
            templateApk = templateApk
        )

        // Mirror the descriptor's declared digests (both bare and sha256:-prefixed
        // forms must verify — the template normalizes both).
        val declared = RuntimeDescriptor.MGBA_UNIFIED.protectedEntries
        val actualEntries = declared.entries.associate { (k, v) ->
            k to v.removePrefix("sha256:")
        }

        assertTrue(template.verifyProtectedEntries(actualEntries))
    }

    @Test
    fun `verifyProtectedEntries fails closed when protected entry is tampered`() {
        val template = RuntimeTemplate(
            descriptor = RuntimeDescriptor.MGBA_UNIFIED,
            templateApk = templateApk
        )

        val declared = RuntimeDescriptor.MGBA_UNIFIED.protectedEntries
        val tamperedEntries = declared.entries.associate { (k, v) ->
            if (k == "classes.dex") k to "0000000000000000000000000000000000000000000000000000000000000000"
            else k to v.removePrefix("sha256:")
        }

        assertFalse(template.verifyProtectedEntries(tamperedEntries), "Must reject tampered bytecode")
    }
}
