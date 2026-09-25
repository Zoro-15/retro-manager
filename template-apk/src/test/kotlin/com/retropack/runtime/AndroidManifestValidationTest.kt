package com.retropack.runtime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Validates AndroidManifest invariants from architechture.md and masterplan.md:
 * - Invariant 1: Fully qualified activity name (`com.retropack.runtime.GameActivity`)
 * - Invariant 2: `android:extractNativeLibs="false"`
 * - Invariant 3: Fullscreen theme declaration
 */
class AndroidManifestValidationTest {

    private val manifestFile: File
        get() {
            val direct = File("src/main/AndroidManifest.xml")
            if (direct.exists()) return direct
            return File("template-apk/src/main/AndroidManifest.xml")
        }

    @Test
    fun `manifest file exists and is readable`() {
        assertTrue(manifestFile.exists(), "AndroidManifest.xml must exist at template-apk/src/main/AndroidManifest.xml")
        assertTrue(manifestFile.length() > 0, "AndroidManifest.xml must not be empty")
    }

    @Test
    fun `manifest declares fully qualified GameActivity to prevent ClassNotFoundException`() {
        val content = manifestFile.readText()
        assertTrue(
            content.contains("""android:name="com.retropack.runtime.GameActivity""""),
            "Manifest must explicitly declare fully qualified com.retropack.runtime.GameActivity (Invariant 1)"
        )
        assertFalse(
            content.contains("""android:name=".GameActivity""""),
            "Manifest must NOT use shorthand .GameActivity as AXML package renaming breaks it"
        )
    }

    @Test
    fun `manifest declares extractNativeLibs false for 16 KB page-size compliance`() {
        val content = manifestFile.readText()
        assertTrue(
            content.contains("""android:extractNativeLibs="false""""),
            "Manifest must explicitly declare android:extractNativeLibs=\"false\" (Invariant 2)"
        )
    }

    @Test
    fun `manifest declares fullscreen no titlebar theme`() {
        val content = manifestFile.readText()
        assertTrue(
            content.contains("""android:theme="@android:style/Theme.NoTitleBar.Fullscreen""""),
            "Manifest must explicitly declare android:theme=\"@android:style/Theme.NoTitleBar.Fullscreen\""
        )
    }

    @Test
    fun `manifest declares standard launcher intent filter`() {
        val content = manifestFile.readText()
        assertTrue(content.contains("android.intent.action.MAIN"))
        assertTrue(content.contains("android.intent.category.LAUNCHER"))
    }
}
