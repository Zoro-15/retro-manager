package com.retropack.packaging

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlElement
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Mutates AndroidManifest.xml binary XML (AXML) via REAndroid/ARSCLib (Step 7).
 *
 * Enforces Constitutional Invariant 1 (fully-qualified Activity identifiers)
 * and Invariant 2 (extractNativeLibs="false" for 16 KB page alignment).
 *
 * Issue #45 adds a single-pass in-place fast path (see [AxmlInPlace]): when
 * every required mutation fits the in-place model (equal-or-shorter string
 * pool rewrites plus integer typed-value patches), the pool bytes are patched
 * directly and the full ARSCLib DOM round-trip is skipped. Invariant
 * validations run identically in both paths; anything the fast path cannot
 * express (string growth, missing attributes, styled pools) transparently
 * falls back to the structured mutation, so behaviour is identical.
 */
object AxmlMutator {

    private const val ANDROID_NS_URI = "http://schemas.android.com/apk/res/android"
    private const val ATTR_NAME = "name"
    private const val ATTR_LABEL = "label"
    private const val ATTR_EXTRACT_NATIVE_LIBS = "extractNativeLibs"
    private const val ATTR_LABEL_RESOURCE_ID = 0x01010001
    private const val ACTION_MAIN = "android.intent.action.MAIN"
    private const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"

    /**
     * Mutates raw AXML [manifestBytes] using [packageIdentity] and [gameTitle].
     */
    fun mutate(
        manifestBytes: ByteArray,
        packageIdentity: PackageIdentity,
        gameTitle: String
    ): ByteArray {
        mutateInPlaceIfPossible(manifestBytes, packageIdentity, gameTitle)?.let { return it }
        return mutateStructured(manifestBytes, packageIdentity, gameTitle)
    }

    /**
     * Issue #45 fast path: single-pass in-place string pool mutation.
     *
     * Returns the mutated [manifestBytes] when every required change fits the
     * in-place model, or `null` to signal the caller to fall back to the
     * structured ARSCLib path. Invariant violations throw with identical
     * messages regardless of which path detects them.
     */
    internal fun mutateInPlaceIfPossible(
        manifestBytes: ByteArray,
        packageIdentity: PackageIdentity,
        gameTitle: String
    ): ByteArray? {
        val doc = try {
            AxmlInPlace.parse(manifestBytes)
        } catch (_: Exception) {
            return null
        } ?: return null

        val pool = doc.pool

        // -- Invariant validations (identical semantics to the structured path) --
        for (activity in doc.activities) {
            val name = activity.attrStringAndroid(ATTR_NAME, pool)
                ?: throw IllegalStateException(
                    "Found <activity> element without android:name attribute in manifest"
                )
            if (name.startsWith(".") || !name.contains('.')) {
                throw IllegalStateException(
                    "Violation of Invariant 1: Non-fully-qualified activity identifier '$name' found. " +
                        "Activities must be declared with fully-qualified class names (e.g. 'com.retropack.runtime.GameActivity') " +
                        "to prevent ClassNotFoundException after package rewriting."
                )
            }
        }
        val extract = doc.application.attrAndroid(ATTR_EXTRACT_NATIVE_LIBS)
        when {
            extract == null || extract.dataType != AxmlInPlace.TYPE_BOOLEAN -> throw IllegalStateException(
                "Violation of Invariant 2: android:extractNativeLibs is absent; " +
                    "the platform default behaves as true, which breaks 16 KB page-size compatibility!"
            )
            extract.data == 0 -> Unit // correct: extractNativeLibs="false"
            else -> throw IllegalStateException(
                "Violation of Invariant 2: android:extractNativeLibs must be false for 16 KB page-size compatibility!"
            )
        }

        // -- Collect the in-place patch plan --
        data class StringPatch(val slot: Int, val newValue: String)

        val stringPatches = mutableListOf<StringPatch>()
        val intPatches = mutableListOf<Pair<Int, Int>>() // absolute file offset -> new u32

        // package="..." on <manifest> (no namespace).
        val packageAttr = doc.manifest.attrGlobal("package")
        if (packageAttr == null || packageAttr.dataType != AxmlInPlace.TYPE_STRING) return null
        stringPatches.add(StringPatch(packageAttr.data, packageIdentity.packageName))

        // versionCode / versionName are injected by aapt from the build config;
        // both may legitimately be absent in hand-written manifests.
        val versionCodeAttr = doc.manifest.attrGlobal("versionCode")
            ?: doc.manifest.attrAndroid("versionCode")
        if (versionCodeAttr != null) {
            if (versionCodeAttr.dataType != AxmlInPlace.TYPE_INT) return null
            intPatches.add(versionCodeAttr.dataOffset to packageIdentity.versionCode)
        }
        val versionNameAttr = doc.manifest.attrGlobal("versionName")
            ?: doc.manifest.attrAndroid("versionName")
        if (versionNameAttr != null) {
            if (versionNameAttr.dataType != AxmlInPlace.TYPE_STRING) return null
            stringPatches.add(StringPatch(versionNameAttr.data, packageIdentity.versionName))
        }

        // Application label: patchable only when already a string literal.
        val appLabel = doc.application.attrAndroid(ATTR_LABEL)
        val patchAppLabel = appLabel != null && appLabel.dataType == AxmlInPlace.TYPE_STRING

        // Launcher activity labels: patchable only where the attribute already
        // exists as a literal; attributes that would need to be created require
        // pool growth -> structured fallback (getOrCreateAndroidAttribute).
        val launcherLabelSlots = mutableListOf<Int>()
        for (activity in doc.activities) {
            if (!isLauncherActivity(activity, pool)) continue
            val labelAttr = activity.attrAndroid(ATTR_LABEL) ?: return null
            if (labelAttr.dataType != AxmlInPlace.TYPE_STRING) return null
            launcherLabelSlots.add(labelAttr.data)
        }

        if (patchAppLabel) {
            stringPatches.add(StringPatch(appLabel!!.data, gameTitle))
        }
        for (slot in launcherLabelSlots) {
            stringPatches.add(StringPatch(slot, gameTitle))
        }

        if (stringPatches.isEmpty() && intPatches.isEmpty()) return null

        // -- Feasibility: every string rewrite must fit its existing slot --
        for (patch in stringPatches) {
            if (!AxmlInPlace.canPatchInPlace(pool, patch.slot, patch.newValue)) return null
        }

        // -- Apply in place (no re-serialization, no DOM) --
        for (patch in stringPatches) {
            AxmlInPlace.patchString(pool, patch.slot, patch.newValue)
        }
        for ((offset, value) in intPatches) {
            manifestBytes[offset] = (value and 0xFF).toByte()
            manifestBytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
            manifestBytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
            manifestBytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        return manifestBytes
    }

    private fun isLauncherActivity(activity: AxmlInPlace.Element, pool: AxmlInPlace.StringPool): Boolean {
        val filters = activity.children.filter { it.name == "intent-filter" }
        if (filters.isEmpty()) return false
        val hasMain = filters.any { filter ->
            filter.children.any { it.name == "action" && it.attrStringAndroid(ATTR_NAME, pool) == ACTION_MAIN }
        }
        val hasLauncher = filters.any { filter ->
            filter.children.any { it.name == "category" && it.attrStringAndroid(ATTR_NAME, pool) == CATEGORY_LAUNCHER }
        }
        return hasMain && hasLauncher
    }

    /**
     * Structured ARSCLib mutation (single-pass parse → mutate → serialize).
     */
    private fun mutateStructured(
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

        // 6. Explicitly set android:label on all launcher activities
        mutateLauncherActivityLabels(manifest, gameTitle)

        manifest.refresh()
        val out = ByteArrayOutputStream()
        manifest.writeBytes(out)
        return out.toByteArray()
    }

    private fun mutateLauncherActivityLabels(manifest: AndroidManifestBlock, gameTitle: String) {
        val activities = manifest.listApplicationElementsByTag("activity") ?: return
        for (activity in activities) {
            if (isLauncherActivity(activity)) {
                val labelAttr = activity.getOrCreateAndroidAttribute(ATTR_LABEL, ATTR_LABEL_RESOURCE_ID)
                labelAttr.valueAsString = gameTitle
            }
        }
    }

    private fun isLauncherActivity(activity: ResXmlElement): Boolean {
        val intentFilters = activity.listElements("intent-filter") ?: return false
        for (filter in intentFilters) {
            val actions = filter.listElements("action") ?: emptyList()
            val categories = filter.listElements("category") ?: emptyList()

            val hasMain = actions.any { action ->
                val name = (action.searchAttributeByName(ATTR_NAME) ?: action.searchAttributeByName("android:$ATTR_NAME"))?.valueAsString
                name == ACTION_MAIN
            }
            val hasLauncher = categories.any { category ->
                val name = (category.searchAttributeByName(ATTR_NAME) ?: category.searchAttributeByName("android:$ATTR_NAME"))?.valueAsString
                name == CATEGORY_LAUNCHER
            }

            if (hasMain && hasLauncher) {
                return true
            }
        }
        return false
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
