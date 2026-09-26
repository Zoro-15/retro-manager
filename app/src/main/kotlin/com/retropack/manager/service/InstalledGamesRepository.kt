package com.retropack.manager.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.retropack.manager.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Scans and discovers standalone packaged RetroPack games installed on the Android OS.
 *
 * Implements Feature 3 specifications:
 * - Queries PackageManager for packages matching `com.retropack.game.*` or possessing RetroPack metadata.
 * - Extracts application icon, display title, platform architecture, install date, and app size.
 * - Detects battery save files in storage.
 */
class InstalledGamesRepository {

    suspend fun scanInstalledGames(context: Context): List<InstalledGame> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedApps = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            }
        } catch (e: Exception) {
            AppLogger.e("InstalledGamesRepository", "Failed to query PackageManager: ${e.message}", e)
            emptyList()
        }

        val discoveredGames = mutableListOf<InstalledGame>()

        for (app in installedApps) {
            val pkg = app.packageName
            val isRetroPackApp = pkg.startsWith("com.retropack.game") ||
                    (app.metaData?.containsKey("com.retropack.runtime") == true) ||
                    (app.metaData?.containsKey("com.retropack.platform") == true)

            if (!isRetroPackApp) continue

            try {
                val label = pm.getApplicationLabel(app).toString()
                val icon = pm.getApplicationIcon(app)
                val pkgInfo = pm.getPackageInfo(pkg, 0)
                val installTime = pkgInfo.firstInstallTime
                val versionName = pkgInfo.versionName ?: "1.0.0"
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode
                }

                val sourceApk = File(app.sourceDir)
                val sizeBytes = if (sourceApk.exists()) sourceApk.length() else 0L

                // Derive platform from metadata or package name
                val platform = app.metaData?.getString("com.retropack.platform")
                    ?: derivePlatformFromPackage(pkg, label)

                // Check for save files
                val (saveFile, saveSize, hasSave, saveFormat) = locateSaveFile(context, pkg, platform)

                discoveredGames.add(
                    InstalledGame(
                        packageName = pkg,
                        gameTitle = label,
                        platform = platform,
                        iconDrawable = icon,
                        appSizeBytes = sizeBytes,
                        installDate = installTime,
                        versionName = versionName,
                        versionCode = versionCode,
                        saveFile = saveFile,
                        saveSizeBytes = saveSize,
                        hasSave = hasSave,
                        saveFormat = saveFormat
                    )
                )
            } catch (e: Exception) {
                AppLogger.w("InstalledGamesRepository", "Error inspecting installed game $pkg: ${e.message}")
            }
        }

        discoveredGames.sortedByDescending { it.installDate }
    }

    private fun derivePlatformFromPackage(packageName: String, title: String): String {
        val lower = (packageName + " " + title).lowercase()
        return when {
            lower.contains("gba") || lower.contains("advance") -> "gba"
            lower.contains("gbc") || lower.contains("color") -> "gbc"
            lower.contains("gb") || lower.contains("gameboy") -> "gb"
            lower.contains("snes") || lower.contains("sfc") || lower.contains("super") -> "snes"
            lower.contains("genesis") || lower.contains("megadrive") || lower.contains("sega") -> "genesis"
            lower.contains("nes") || lower.contains("famicom") -> "nes"
            lower.contains("psx") || lower.contains("ps1") || lower.contains("playstation") -> "psx"
            lower.contains("n64") || lower.contains("nintendo64") -> "n64"
            lower.contains("pce") || lower.contains("turbografx") -> "pce"
            lower.contains("nds") || lower.contains("ds") -> "nds"
            lower.contains("psp") -> "psp"
            else -> "gba"
        }
    }

    private fun locateSaveFile(
        context: Context,
        packageName: String,
        platform: String
    ): Quad<File?, Long, Boolean, String> {
        val format = when (platform.lowercase()) {
            "psx", "ps1" -> ".mcd"
            "snes" -> ".srm"
            "n64" -> ".eep"
            "pce" -> ".sav"
            else -> ".sav"
        }

        // 1. Check shared external files directory: Android/data/<packageName>/files/game.sav
        val extDataDir = File(context.getExternalFilesDir(null)?.parentFile?.parentFile, "$packageName/files")
        val candidateNames = listOf("game$format", "game.sav", "game.srm", "game.mcd", "game.nvram")

        for (cand in candidateNames) {
            val f = File(extDataDir, cand)
            if (f.exists() && f.length() > 0) {
                return Quad(f, f.length(), true, format)
            }
        }

        // 2. Check RetroPack exported saves directory: Downloads/RetroPack/Saves/<packageName>/
        val downloadsSaves = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "RetroPack/Saves/$packageName"
        )
        for (cand in candidateNames) {
            val f = File(downloadsSaves, cand)
            if (f.exists() && f.length() > 0) {
                return Quad(f, f.length(), true, format)
            }
        }

        return Quad(null, 0L, false, format)
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
