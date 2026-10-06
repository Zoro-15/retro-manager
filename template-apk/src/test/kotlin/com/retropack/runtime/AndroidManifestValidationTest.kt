package com.retropack.runtime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Validates AndroidManifest invariants for pure NativeActivity (plan2):
 * - Invariant 1: android:hasCode="false" application declaration
 * - Invariant 2: android.app.NativeActivity with retro_engine lib_name metadata
 * - Invariant 3: android:extractNativeLibs="false" for 16 KB page alignment
 * - Invariant 4: Fullscreen no titlebar theme declaration
 * - Invariant 5: Standard MAIN / LAUNCHER intent filter
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
    fun `manifest declares android hasCode false for pure native execution`() {
        val content = manifestFile.readText()
        assertTrue(
            content.contains("""android:hasCode="false""""),
            "Manifest must explicitly declare android:hasCode=\"false\""
        )
    }

    @Test
    fun `manifest declares android app NativeActivity with retro_engine library`() {
        val content = manifestFile.readText()
        assertTrue(
            content.contains("""android:name="android.app.NativeActivity""""),
            "Manifest must explicitly declare android:name=\"android.app.NativeActivity\""
        )
        assertTrue(
            content.contains("""android:name="android.app.lib_name"""") && content.contains("""android:value="retro_engine""""),
            "Manifest must declare retro_engine lib_name meta-data"
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
            content.contains("""android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen"""") ||
                content.contains("""android:theme="@android:style/Theme.NoTitleBar.Fullscreen""""),
            "Manifest must explicitly declare black fullscreen theme"
        )
    }

    @Test
    fun `manifest declares standard launcher intent filter`() {
        val content = manifestFile.readText()
        assertTrue(content.contains("android.intent.action.MAIN"))
        assertTrue(content.contains("android.intent.category.LAUNCHER"))
    }
}
