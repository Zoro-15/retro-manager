package com.retropack.manager.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * High-Performance Libretro Thumbnails Boxart Scraping Engine.
 *
 * Implements Feature 5 specifications:
 * - Queries official Libretro Thumbnails CDN: `https://thumbnails.libretro.com/<System_Name>/<Category>/<Title>.png`
 * - Full 14-Console Architecture URL mapping table.
 * - Libretro special character sanitization rules (&, *, :, /, <, >, ?, \, |, " -> _).
 * - Multi-tier candidate resolution & category fallbacks (Named_Boxarts -> Named_Titles -> Named_Snaps).
 * - On-disk LRU caching in `<cacheDir>/boxarts/<hash>.png`.
 */
object LibretroThumbnailsScraper {

    private const val CACHE_DIR_NAME = "boxarts"
    private const val CONNECT_TIMEOUT_MS = 2500
    private const val READ_TIMEOUT_MS = 2500
    private const val MAX_IMAGE_SIZE_BYTES = 8 * 1024 * 1024 // 8 MB max
    private const val MAX_CACHE_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB LRU limit

    private const val LIBRETRO_CDN_BASE = "https://thumbnails.libretro.com"

    /**
     * Complete 14-Console Architecture System Directory Mapping Table
     */
    val SYSTEM_DIR_MAP = mapOf(
        "gba" to "Nintendo%20-%20Game%20Boy%20Advance",
        "gbc" to "Nintendo%20-%20Game%20Boy%20Color",
        "gb" to "Nintendo%20-%20Game%20Boy",
        "snes" to "Nintendo%20-%20Super%20Nintendo%20Entertainment%20System",
        "sfc" to "Nintendo%20-%20Super%20Nintendo%20Entertainment%20System",
        "nes" to "Nintendo%20-%20Nintendo%20Entertainment%20System",
        "fds" to "Nintendo%20-%20Family%20Computer%20Disk%20System",
        "n64" to "Nintendo%20-%20Nintendo%2064",
        "nds" to "Nintendo%20-%20Nintendo%20DS",
        "genesis" to "Sega%20-%20Mega%20Drive%20-%20Genesis",
        "md" to "Sega%20-%20Mega%20Drive%20-%20Genesis",
        "gen" to "Sega%20-%20Mega%20Drive%20-%20Genesis",
        "sms" to "Sega%20-%20Master%20System%20-%20Mark%20III",
        "gg" to "Sega%20-%20Game%20Gear",
        "pce" to "NEC%20-%20PC%20Engine%20-%20TurboGrafx%2016",
        "tg16" to "NEC%20-%20PC%20Engine%20-%20TurboGrafx%2016",
        "sgx" to "NEC%20-%20PC%20Engine%20SuperGrafx",
        "psx" to "Sony%20-%20PlayStation",
        "ps1" to "Sony%20-%20PlayStation",
        "ps" to "Sony%20-%20PlayStation",
        "psp" to "Sony%20-%20PlayStation%20Portable",
        "arcade" to "FBNeo%20-%20Arcade%20Games",
        "neogeo" to "SNK%20-%20Neo%20Geo",
        "fbneo" to "FBNeo%20-%20Arcade%20Games"
    )

    val CATEGORIES = listOf("Named_Boxarts", "Named_Titles", "Named_Snaps")

    /**
     * Sanitizes title string according to Libretro repository naming conventions:
     * Characters illegal on cross-platform filesystems are replaced with '_'.
     */
    fun sanitizeLibretroTitle(raw: String): String {
        var s = raw
        // Strip common extension
        s = s.replace(Regex("\\.(gba|gbc|gb|zip|7z|bin|sfc|smc|snes|fig|nes|fds|unf|md|smd|gen|sms|gg|pce|tg16|sgx|n64|z64|v64|nds|srl|dsi|iso|cso|pbp|cue|chd)$", RegexOption.IGNORE_CASE), "")
        // Libretro illegal characters: & * : / < > ? \ | " -> _
        val illegalChars = charArrayOf('&', '*', ':', '/', '<', '>', '?', '\\', '|', '"')
        for (c in illegalChars) {
            s = s.replace(c, '_')
        }
        // Normalize whitespace
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }

    /**
     * Normalizes article prefix: "The Legend of Zelda" <-> "Legend of Zelda, The"
     */
    fun normalizeArticleReordering(title: String): List<String> {
        val result = mutableListOf(title)
        if (title.startsWith("The ", ignoreCase = true)) {
            val withoutThe = title.substring(4).trim()
            result.add("$withoutThe, The")
        } else if (title.endsWith(", The", ignoreCase = true)) {
            val withoutThe = title.substring(0, title.length - 5).trim()
            result.add("The $withoutThe")
        }
        return result
    }

    /**
     * Generates prioritized multi-tier resolution candidates.
     */
    fun generateCandidateTitles(
        gameTitle: String,
        rawFileName: String? = null,
        region: String = "USA"
    ): List<String> {
        val candidates = mutableListOf<String>()

        val baseTitles = mutableListOf<String>()
        if (rawFileName != null && rawFileName.isNotBlank()) {
            val stripped = rawFileName.replace(Regex("\\.(gba|gbc|gb|zip|7z|bin|sfc|smc|snes|fig|nes|fds|unf|md|smd|gen|sms|gg|pce|tg16|sgx|n64|z64|v64|nds|srl|dsi|iso|cso|pbp|cue|chd)$", RegexOption.IGNORE_CASE), "")
            candidates.add(sanitizeLibretroTitle(stripped))
            baseTitles.add(stripped.replace(Regex("\\s*\\([^)]*\\)"), "").replace(Regex("\\s*\\[[^\\]]*\\]"), "").trim())
        }

        if (gameTitle.isNotBlank()) {
            val cleanHeader = gameTitle.replace(Regex("\\s*\\([^)]*\\)"), "").replace(Regex("\\s*\\[[^\\]]*\\]"), "").trim()
            baseTitles.add(cleanHeader)
        }

        for (base in baseTitles) {
            val cleanBase = sanitizeLibretroTitle(base)
            if (cleanBase.isBlank()) continue

            // 1. Article reorderings
            val reordered = normalizeArticleReordering(cleanBase)
            for (t in reordered) {
                // Tier 1: Exact region candidates
                candidates.add("$t ($region)")
                candidates.add("$t (USA, Europe)")
                candidates.add("$t (USA)")
                candidates.add("$t (Europe)")
                candidates.add("$t (Japan)")
                candidates.add("$t (World)")
                // Tier 2: Clean title only
                candidates.add(t)
            }
        }

        return candidates.distinct()
    }

    /**
     * Asynchronously scrapes boxart for a game ROM.
     */
    suspend fun scrapeBoxart(
        context: Context,
        platform: String,
        gameTitle: String,
        rawFileName: String? = null,
        region: String = "USA"
    ): ByteArray? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { if (!exists()) mkdirs() }
        scrapeBoxartSync(
            cacheDir = cacheDir,
            platform = platform,
            gameTitle = gameTitle,
            rawFileName = rawFileName,
            region = region
        )
    }

    /**
     * Synchronous lookup logic for execution and unit tests.
     */
    fun scrapeBoxartSync(
        cacheDir: File,
        platform: String,
        gameTitle: String,
        rawFileName: String? = null,
        region: String = "USA",
        networkFetcher: ((String) -> ByteArray?)? = null
    ): ByteArray? {
        val cacheKey = computeCacheKey(platform, gameTitle, rawFileName)
        val cachedFile = File(cacheDir, "$cacheKey.png")

        if (cachedFile.exists() && cachedFile.length() > 0) {
            try {
                return cachedFile.readBytes()
            } catch (_: Exception) {
            }
        }

        val systemDir = SYSTEM_DIR_MAP[platform.lowercase().trim()] ?: "Nintendo%20-%20Game%20Boy%20Advance"
        val candidates = generateCandidateTitles(gameTitle, rawFileName, region)
        val fetcher = networkFetcher ?: { url -> fetchHttpBytes(url) }

        for (category in CATEGORIES) {
            for (candidate in candidates) {
                val encodedCandidate = URLEncoder.encode(candidate, "UTF-8").replace("+", "%20")
                val url = "$LIBRETRO_CDN_BASE/$systemDir/$category/$encodedCandidate.png"
                val bytes = fetcher(url)
                if (bytes != null && bytes.isNotEmpty()) {
                    saveToDiskCache(cacheDir, cachedFile, bytes)
                    return bytes
                }
            }
        }

        return null
    }

    private fun fetchHttpBytes(urlString: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "RetroPack-Manager/2.0")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            val inputStream: InputStream = connection.inputStream
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var totalRead = 0
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                totalRead += read
                if (totalRead > MAX_IMAGE_SIZE_BYTES) return null
                outputStream.write(buffer, 0, read)
            }

            outputStream.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun saveToDiskCache(cacheDir: File, targetFile: File, bytes: ByteArray) {
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            FileOutputStream(targetFile).use { it.write(bytes) }
            enforceLruCache(cacheDir)
        } catch (_: Exception) {
        }
    }

    private fun enforceLruCache(cacheDir: File) {
        val files = cacheDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= MAX_CACHE_SIZE_BYTES) return

        val sorted = files.sortedBy { it.lastModified() }
        for (file in sorted) {
            val len = file.length()
            if (file.delete()) {
                totalSize -= len
                if (totalSize <= MAX_CACHE_SIZE_BYTES * 0.8) break
            }
        }
    }

    fun computeCacheKey(platform: String, gameTitle: String, rawFileName: String?): String {
        val raw = "$platform:${rawFileName ?: ""}:${sanitizeLibretroTitle(gameTitle)}"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
