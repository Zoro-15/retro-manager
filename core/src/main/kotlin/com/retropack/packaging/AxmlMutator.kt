package com.retropack.packaging

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Mutates AndroidManifest.xml binary XML (AXML) via REAndroid/ARSCLib (Step 7).
 *
 * Enforces Constitutional Invariant 1 (fully-qualified Activity identifiers)
 * and Invariant 2 (extractNativeLibs="false" for 16 KB page alignment).
 */
object AxmlMutator {

    private const val ANDROID_NS_URI = "http://schemas.android.com/apk/res/android"
    private const val ATTR_NAME = "name"
    private const val ATTR_EXTRACT_NATIVE_LIBS = "extractNativeLibs"

    /**
     * Mutates raw AXML [manifestBytes] using [packageIdentity] and [gameTitle].
     */
    fun mutate(
        manifestBytes: ByteArray,
        packageIdentity: PackageIdentity,
        gameTitle: String
    ): ByteArray {
        val manifest = AndroidManifestBlock()
        ByteArrayInputStream(manifestBytes).use { manifest.readBytes(it) }

        // 1. Mutate root package name
        manifest.packageName = packageIdentity.packageName

        // 2. Inlines literal android:label directly on <application>, bypassing resources.arsc
        manifest.setApplicationLabel(gameTitle)

        // 3. Mutate versionCode and versionName
        manifest.versionCode = packageIdentity.versionCode
        manifest.versionName = packageIdentity.versionName

        // 4. Assert Constitutional Invariant 1: Fully qualified activity names
        validateFullyQualifiedActivities(manifest)

        // 5. Assert Constitutional Invariant 2: extractNativeLibs="false"
        validateExtractNativeLibs(manifest)

        manifest.refresh()
        val out = ByteArrayOutputStream()
        manifest.writeBytes(out)
        return out.toByteArray()
    }

    private fun validateFullyQualifiedActivities(manifest: AndroidManifestBlock) {
        val activities = manifest.listApplicationElementsByTag("activity")
        for (activity in activities) {
            val nameAttr = activity.searchAttributeByName(ATTR_NAME)
                ?: activity.searchAttributeByName("android:$ATTR_NAME")
            val name = nameAttr?.valueAsString
                ?: throw IllegalStateException("Found <activity> element without android:name attribute in manifest")

            if (name.startsWith(".") || !name.contains('.')) {
                throw IllegalStateException(
                    "Violation of Invariant 1: Non-fully-qualified activity identifier '$name' found. " +
                        "Activities must be declared with fully-qualified class names (e.g. 'com.retropack.runtime.GameActivity') " +
                        "to prevent ClassNotFoundException after package rewriting."
                )
            }
        }
    }

    private fun validateExtractNativeLibs(manifest: AndroidManifestBlock) {
        val app = manifest.applicationElement
            ?: throw IllegalStateException("No <application> element found in manifest")

        val attr = app.searchAttributeByName(ATTR_EXTRACT_NATIVE_LIBS)
            ?: app.searchAttributeByName("android:$ATTR_EXTRACT_NATIVE_LIBS")
            ?: throw IllegalStateException(
                "Violation of Invariant 2: android:extractNativeLibs is absent; " +
                    "the platform default behaves as true, which breaks 16 KB page-size compatibility!"
            )

        val isExtract = attr.valueAsBoolean
        if (isExtract) {
            throw IllegalStateException(
                "Violation of Invariant 2: android:extractNativeLibs must be false for 16 KB page-size compatibility!"
            )
        }
    }
}
