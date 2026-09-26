package com.retropack.manager.service

import android.graphics.drawable.Drawable
import java.io.File

/**
 * Model representing a standalone packaged RetroPack standalone APK installed on the Android device.
 *
 * Implements Feature 3 specifications:
 * - Application package name and display title.
 * - Platform architecture identifier (GBA, PS1, SNES, Genesis, NES, N64, PCE, NDS, Arcade).
 * - App size, install timestamp, and version metadata.
 * - Accessible save file metadata (.sav, .srm, .mcd, .eep, .nvram).
 */
data class InstalledGame(
    val packageName: String,
    val gameTitle: String,
    val platform: String,
    val iconDrawable: Drawable? = null,
    val appSizeBytes: Long = 0L,
    val installDate: Long = 0L,
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val saveFile: File? = null,
    val saveSizeBytes: Long = 0L,
    val hasSave: Boolean = false,
    val saveFormat: String = ".sav"
)
