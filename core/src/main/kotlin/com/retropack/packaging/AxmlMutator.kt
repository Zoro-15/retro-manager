package com.retropack.packaging

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.value.ValueType
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Mutates AndroidManifest.xml binary XML (AXML) via REAndroid/ARSCLib (Step 7).
 *
 * Enforces Constitutional Invariant 1 (fully-qualified Activity identifiers)
 * and Invariant 2 (extractNativeLibs="false" for 16 KB page alignment).
 */
object AxmlMutator {

    private const val ANDROID_NS_URI = "http://schemas.android.com/apk/res/android"
    private const val ATTR_NAME = "name"
    private const val ATTR_LABEL = "label"
    private const val ATTR_EXTRACT_NATIVE_LIBS = "extractNativeLibs"
    private const val ATTR_LABEL_RESOURCE_ID = 0x01010001
    private const val ACTION_MAIN = "android.intent.action.MAIN"
    private const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"

    // Typed attribute constants for binary AXML compatibility across all Android versions (API 26-35+)
    private const val ORIENTATION_SENSOR_LANDSCAPE = 6
    private const val CONFIG_CHANGES_BITMASK = 0x000004a0 // orientation | keyboardHidden | screenSize
    private const val WINDOW_SOFT_INPUT_ADJUST_NOTHING = 0x00000030

    /**
     * Mutates raw AXML (or plain-text XML) [manifestBytes] using [packageIdentity] and [gameTitle].
     */
    fun mutate(
        manifestBytes: ByteArray,
        packageIdentity: PackageIdentity,
        gameTitle: String
    ): ByteArray {
        val manifest = loadManifestBlock(manifestBytes)

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

    private fun isPlainTextXml(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val prefix = bytes.take(32).toByteArray().toString(Charsets.UTF_8).trimStart()
        return prefix.startsWith("<")
    }

    private fun loadManifestBlock(manifestBytes: ByteArray): AndroidManifestBlock {
        val block = AndroidManifestBlock()
        if (isPlainTextXml(manifestBytes)) {
            parseTextXmlToBlock(manifestBytes, block)
        } else {
            ByteArrayInputStream(manifestBytes).use { block.readBytes(it) }
        }
        // Ensure compulsory Android 15+ SDK attributes
        if (block.minSdkVersion == null || block.minSdkVersion == 0) {
            block.minSdkVersion = 26
        }
        if (block.targetSdkVersion == null || block.targetSdkVersion == 0) {
            block.targetSdkVersion = 35
        }
        try {
            block.setCompileSdkVersion(35)
        } catch (_: Throwable) {}
        return block
    }

    private fun parseTextXmlToBlock(xmlBytes: ByteArray, block: AndroidManifestBlock) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val manifestElement = doc.documentElement

        val pkg = manifestElement.getAttribute("package").ifEmpty { "com.retropack.template" }
        block.packageName = pkg
        val vcStr = manifestElement.getAttributeNS(ANDROID_NS_URI, "versionCode")
            .ifEmpty { manifestElement.getAttribute("android:versionCode") }
        block.versionCode = vcStr.toIntOrNull() ?: 1

        val vnStr = manifestElement.getAttributeNS(ANDROID_NS_URI, "versionName")
            .ifEmpty { manifestElement.getAttribute("android:versionName") }
        block.versionName = vnStr.ifEmpty { "1.0.0" }

        // SDK constraints
        val sdkNodes = manifestElement.getElementsByTagName("uses-sdk")
        if (sdkNodes.length > 0) {
            val sdkElem = sdkNodes.item(0) as Element
            val minSdk = sdkElem.getAttributeNS(ANDROID_NS_URI, "minSdkVersion")
                .ifEmpty { sdkElem.getAttribute("android:minSdkVersion") }
            val targetSdk = sdkElem.getAttributeNS(ANDROID_NS_URI, "targetSdkVersion")
                .ifEmpty { sdkElem.getAttribute("android:targetSdkVersion") }
            block.minSdkVersion = minSdk.toIntOrNull() ?: 26
            block.targetSdkVersion = targetSdk.toIntOrNull() ?: 35
        } else {
            block.minSdkVersion = 26
            block.targetSdkVersion = 35
        }

        val appNodeList = manifestElement.getElementsByTagName("application")
        if (appNodeList.length > 0) {
            val appElem = appNodeList.item(0) as Element
            val app = block.getOrCreateApplicationElement()

            val label = appElem.getAttributeNS(ANDROID_NS_URI, "label")
                .ifEmpty { appElem.getAttribute("android:label") }
            if (label.isNotEmpty()) {
                block.setApplicationLabel(label)
            }

            val hasCode = appElem.getAttributeNS(ANDROID_NS_URI, "hasCode")
                .ifEmpty { appElem.getAttribute("android:hasCode") }
            if (hasCode.isNotEmpty()) {
                app.getOrCreateAndroidAttribute("hasCode", 0x0101000c).setValueAsBoolean(hasCode.toBoolean())
            } else {
                app.getOrCreateAndroidAttribute("hasCode", 0x0101000c).setValueAsBoolean(false)
            }

            val extractLibs = appElem.getAttributeNS(ANDROID_NS_URI, "extractNativeLibs")
                .ifEmpty { appElem.getAttribute("android:extractNativeLibs") }
            if (extractLibs.isNotEmpty()) {
                app.getOrCreateAndroidAttribute("extractNativeLibs", 0x010104ea).setValueAsBoolean(extractLibs.toBoolean())
            } else {
                app.getOrCreateAndroidAttribute("extractNativeLibs", 0x010104ea).setValueAsBoolean(false)
            }

            app.getOrCreateAndroidAttribute("allowBackup", 0x01010280).setValueAsBoolean(false)

            val activityNodes = appElem.getElementsByTagName("activity")
            for (i in 0 until activityNodes.length) {
                val actElem = activityNodes.item(i) as Element
                val actName = actElem.getAttributeNS(ANDROID_NS_URI, "name")
                    .ifEmpty { actElem.getAttribute("android:name") }
                val activity = app.createChildElement("activity")
                activity.getOrCreateAndroidAttribute("name", 0x01010003).valueAsString = actName

                val exported = actElem.getAttributeNS(ANDROID_NS_URI, "exported")
                    .ifEmpty { actElem.getAttribute("android:exported") }
                if (exported.isNotEmpty()) {
                    activity.getOrCreateAndroidAttribute("exported", 0x01010010).setValueAsBoolean(exported.toBoolean())
                } else {
                    activity.getOrCreateAndroidAttribute("exported", 0x01010010).setValueAsBoolean(true)
                }

                activity.getOrCreateAndroidAttribute("configChanges", 0x0101001f).setTypeAndData(ValueType.HEX, CONFIG_CHANGES_BITMASK)
                activity.getOrCreateAndroidAttribute("windowSoftInputMode", 0x0101022b).setTypeAndData(ValueType.HEX, WINDOW_SOFT_INPUT_ADJUST_NOTHING)
                activity.getOrCreateAndroidAttribute("screenOrientation", 0x0101001e).setTypeAndData(ValueType.DEC, ORIENTATION_SENSOR_LANDSCAPE)

                val theme = actElem.getAttributeNS(ANDROID_NS_URI, "theme")
                    .ifEmpty { actElem.getAttribute("android:theme") }
                if (theme.isNotEmpty()) {
                    val themeResId = if (theme.contains("Theme.Black.NoTitleBar.Fullscreen")) 0x01030008 else 0x01030007
                    activity.getOrCreateAndroidAttribute("theme", 0x01010000).setTypeAndData(ValueType.REFERENCE, themeResId)
                }

                // meta-data
                val metaNodes = actElem.getElementsByTagName("meta-data")
                for (m in 0 until metaNodes.length) {
                    val metaElem = metaNodes.item(m) as Element
                    val mName = metaElem.getAttributeNS(ANDROID_NS_URI, "name")
                        .ifEmpty { metaElem.getAttribute("android:name") }
                    val mVal = metaElem.getAttributeNS(ANDROID_NS_URI, "value")
                        .ifEmpty { metaElem.getAttribute("android:value") }
                    val metaChild = activity.createChildElement("meta-data")
                    metaChild.getOrCreateAndroidAttribute("name", 0x01010003).valueAsString = mName
                    metaChild.getOrCreateAndroidAttribute("value", 0x01010024).valueAsString = mVal
                }

                if (metaNodes.length == 0 && actName.contains("NativeActivity")) {
                    val metaChild = activity.createChildElement("meta-data")
                    metaChild.getOrCreateAndroidAttribute("name", 0x01010003).valueAsString = "android.app.lib_name"
                    metaChild.getOrCreateAndroidAttribute("value", 0x01010024).valueAsString = "retro_engine"
                }

                // intent-filter
                val filterNodes = actElem.getElementsByTagName("intent-filter")
                if (filterNodes.length > 0) {
                    for (f in 0 until filterNodes.length) {
                        val filterElem = filterNodes.item(f) as Element
                        val filter = activity.createChildElement("intent-filter")
                        val actionNodes = filterElem.getElementsByTagName("action")
                        for (a in 0 until actionNodes.length) {
                            val aElem = actionNodes.item(a) as Element
                            val aName = aElem.getAttributeNS(ANDROID_NS_URI, "name")
                                .ifEmpty { aElem.getAttribute("android:name") }
                            val action = filter.createChildElement("action")
                            action.getOrCreateAndroidAttribute("name", 0x01010003).valueAsString = aName
                        }
                        val catNodes = filterElem.getElementsByTagName("category")
                        for (c in 0 until catNodes.length) {
                            val cElem = catNodes.item(c) as Element
                            val cName = cElem.getAttributeNS(ANDROID_NS_URI, "name")
                                .ifEmpty { cElem.getAttribute("android:name") }
                            val category = filter.createChildElement("category")
                            category.getOrCreateAndroidAttribute("name", 0x01010003).valueAsString = cName
                        }
                    }
                } else {
                    val filter = activity.createChildElement("intent-filter")
                    val action = filter.createChildElement("action")
                    action.getOrCreateAndroidAttribute("name", 0x01010003).valueAsString = ACTION_MAIN
                    val category = filter.createChildElement("category")
                    category.getOrCreateAndroidAttribute("name", 0x01010003).valueAsString = CATEGORY_LAUNCHER
                }
            }
        }
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
