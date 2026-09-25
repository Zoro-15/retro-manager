package com.retropack.packaging

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class AxmlMutatorTest {

    private fun createSampleAxml(
        packageName: String = "com.retropack.template",
        activityName: String = "com.retropack.runtime.GameActivity",
        extractNativeLibs: Boolean = false,
        includeExtractAttr: Boolean = true
    ): ByteArray {
        val block = AndroidManifestBlock()
        block.packageName = packageName
        block.versionCode = 1
        block.versionName = "1.0"
        block.setApplicationLabel("Original Template")

        val app = block.getOrCreateApplicationElement()
        if (includeExtractAttr) {
            val extractAttr = app.getOrCreateAndroidAttribute("extractNativeLibs", 0x010104ea)
            extractAttr.setValueAsBoolean(extractNativeLibs)
        }

        val activity = app.createChildElement("activity")
        val nameAttr = activity.getOrCreateAndroidAttribute("name", 0x01010003)
        nameAttr.valueAsString = activityName

        block.refresh()
        val out = ByteArrayOutputStream()
        block.writeBytes(out)
        return out.toByteArray()
    }

    @Test
    fun `mutate updates package name label versionCode and versionName`() {
        val originalAxml = createSampleAxml()
        val identity = PackageIdentity.create(
            gameTitle = "Pokemon Emerald Version",
            romBytes = "ROM Sample Emerald Payload 2026".toByteArray(StandardCharsets.UTF_8),
            versionCode = 2005,
            versionName = "2.0.5"
        )

        val mutatedBytes = AxmlMutator.mutate(
            manifestBytes = originalAxml,
            packageIdentity = identity,
            gameTitle = "Pokemon Emerald Version"
        )

        val mutatedBlock = AndroidManifestBlock()
        ByteArrayInputStream(mutatedBytes).use { mutatedBlock.readBytes(it) }

        assertEquals(identity.packageName, mutatedBlock.packageName)
        assertEquals("Pokemon Emerald Version", mutatedBlock.applicationLabelString)
        assertEquals(2005, mutatedBlock.versionCode)
        assertEquals("2.0.5", mutatedBlock.versionName)

        val activities = mutatedBlock.listApplicationElementsByTag("activity")
        assertTrue(activities.isNotEmpty(), "Template must declare at least one activity")
        for (act in activities) {
            val name = (act.searchAttributeByName("name") ?: act.searchAttributeByName("android:name"))?.valueAsString
            assertTrue(name != null && !name.startsWith("."), "Activity name must be fully qualified: $name")
        }
    }

    @Test
    fun `mutate fails with IllegalStateException when activity uses shorthand relative name`() {
        val shorthandAxml = createSampleAxml(activityName = ".GameActivity")
        val identity = PackageIdentity.create(
            gameTitle = "Metroid Zero Mission",
            romBytes = "ROM Sample Metroid".toByteArray(StandardCharsets.UTF_8)
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            AxmlMutator.mutate(shorthandAxml, identity, "Metroid Zero Mission")
        }
        assertTrue(ex.message!!.contains("Invariant 1"), "Must reference Invariant 1: ${ex.message}")
    }

    @Test
    fun `mutate fails with IllegalStateException when extractNativeLibs is true`() {
        val extractTrueAxml = createSampleAxml(extractNativeLibs = true)
        val identity = PackageIdentity.create(
            gameTitle = "Castlevania Aria of Sorrow",
            romBytes = "ROM Sample Castlevania".toByteArray(StandardCharsets.UTF_8)
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            AxmlMutator.mutate(extractTrueAxml, identity, "Castlevania Aria of Sorrow")
        }
        assertTrue(ex.message!!.contains("Invariant 2"), "Must reference Invariant 2: ${ex.message}")
    }

    @Test
    fun `mutate fails when activity name has no package qualifier`() {
        val bareAxml = createSampleAxml(activityName = "GameActivity")
        val identity = PackageIdentity.create(
            gameTitle = "Kirby Nightmare",
            romBytes = "ROM Sample Kirby".toByteArray(StandardCharsets.UTF_8)
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            AxmlMutator.mutate(bareAxml, identity, "Kirby Nightmare")
        }
        assertTrue(ex.message!!.contains("Invariant 1"), "Must reference Invariant 1: ${ex.message}")
    }

    @Test
    fun `mutate fails when extractNativeLibs attribute is absent`() {
        val noAttrAxml = createSampleAxml(includeExtractAttr = false)
        val identity = PackageIdentity.create(
            gameTitle = "Golden Sun",
            romBytes = "ROM Sample Golden Sun".toByteArray(StandardCharsets.UTF_8)
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            AxmlMutator.mutate(noAttrAxml, identity, "Golden Sun")
        }
        assertTrue(ex.message!!.contains("Invariant 2"), "Must reference Invariant 2: ${ex.message}")
    }
}
