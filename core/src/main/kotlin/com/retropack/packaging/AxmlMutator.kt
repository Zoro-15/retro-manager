package com.retropack.packaging

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlElement
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
        block.versionName = vnStr.ifEmpty { "1.0" }

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
            }

            val extractLibs = appElem.getAttributeNS(ANDROID_NS_URI, "extractNativeLibs")
                .ifEmpty { appElem.getAttribute("android:extractNativeLibs") }
            if (extractLibs.isNotEmpty()) {
                app.getOrCreateAndroidAttribute("extractNativeLibs", 0x010104ea).setValueAsBoolean(extractLibs.toBoolean())
            } else {
                app.getOrCreateAndroidAttribute("extractNativeLibs", 0x010104ea).setValueAsBoolean(false)
            }

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
                }

                val screenOrientation = actElem.getAttributeNS(ANDROID_NS_URI, "screenOrientation")
                    .ifEmpty { actElem.getAttribute("android:screenOrientation") }
                if (screenOrientation.isNotEmpty()) {
                    activity.getOrCreateAndroidAttribute("screenOrientation", 0x0101001e).valueAsString = screenOrientation
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

                // intent-filter
                val filterNodes = actElem.getElementsByTagName("intent-filter")
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
