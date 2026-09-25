package com.retropack.runtime

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Validates adaptive icon drawables and PNG assets:
 * - Adaptive icon XML pointing to pure-file drawable layers.
 * - Valid PNG file bytes in res/drawable-nodpi/ for file-level replacement without touching resources.arsc.
 */
class AdaptiveIconStructureTest {

    private val resDir: File
        get() {
            val direct = File("src/main/res")
            if (direct.exists()) return direct
            return File("template-apk/src/main/res")
        }
    private val pngSignature = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
    )

    @Test
    fun `adaptive icon xml points to nodpi drawable foreground and background`() {
        val launcherXml = File(resDir, "mipmap-anydpi-v26/ic_launcher.xml")
        assertTrue(launcherXml.exists(), "ic_launcher.xml must exist in mipmap-anydpi-v26")

        val content = launcherXml.readText()
        assertTrue(content.contains("@drawable/ic_launcher_background"), "Must reference @drawable/ic_launcher_background")
        assertTrue(content.contains("@drawable/ic_launcher_foreground"), "Must reference @drawable/ic_launcher_foreground")
    }

    @Test
    fun `adaptive round icon xml points to nodpi drawable foreground and background`() {
        val roundXml = File(resDir, "mipmap-anydpi-v26/ic_launcher_round.xml")
        assertTrue(roundXml.exists(), "ic_launcher_round.xml must exist in mipmap-anydpi-v26")

        val content = roundXml.readText()
        assertTrue(content.contains("@drawable/ic_launcher_background"), "Must reference @drawable/ic_launcher_background")
        assertTrue(content.contains("@drawable/ic_launcher_foreground"), "Must reference @drawable/ic_launcher_foreground")
    }

    @Test
    fun `nodpi background and foreground pngs exist and have valid PNG signatures`() {
        val bgFile = File(resDir, "drawable-nodpi/ic_launcher_background.png")
        val fgFile = File(resDir, "drawable-nodpi/ic_launcher_foreground.png")

        assertTrue(bgFile.exists() && bgFile.length() > 0, "ic_launcher_background.png must exist in drawable-nodpi")
        assertTrue(fgFile.exists() && fgFile.length() > 0, "ic_launcher_foreground.png must exist in drawable-nodpi")

        val bgHeader = bgFile.inputStream().use { it.readNBytes(8) }
        val fgHeader = fgFile.inputStream().use { it.readNBytes(8) }

        assertArrayEquals(pngSignature, bgHeader, "Background must be a valid PNG binary")
        assertArrayEquals(pngSignature, fgHeader, "Foreground must be a valid PNG binary")
    }
}
