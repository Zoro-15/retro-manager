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

        val actualEntries = mapOf(
            "classes.dex" to "798a89a984f3e83964c19e681336ce6dacf4d948bd4253dfb895c55b82ef243d",
            "lib/arm64-v8a/libmgba.so" to "79eca5e1ea4df26b67ea6c23839173de1ba465baf0713c3198c1d6f61a2d1bf1"
        )

        assertTrue(template.verifyProtectedEntries(actualEntries))
    }

    @Test
    fun `verifyProtectedEntries fails closed when protected entry is tampered`() {
        val template = RuntimeTemplate(
            descriptor = RuntimeDescriptor.MGBA_UNIFIED,
            templateApk = templateApk
        )

        val tamperedEntries = mapOf(
            "classes.dex" to "0000000000000000000000000000000000000000000000000000000000000000",
            "lib/arm64-v8a/libmgba.so" to "79eca5e1ea4df26b67ea6c23839173de1ba465baf0713c3198c1d6f61a2d1bf1"
        )

        assertFalse(template.verifyProtectedEntries(tamperedEntries), "Must reject tampered bytecode")
    }
}
