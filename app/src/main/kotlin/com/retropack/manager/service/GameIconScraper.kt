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
 * Rapid Online Game Icon & Box-Art Scraper Service (Step 2 Enhancement).
 *
 * Automatically resolves and fetches retro box art from public CDN repositories
 * (e.g. Libretro Thumbnails) with strict 2.5s timeouts, disk LRU caching,
 * header game-code heuristics, and silent offline fallback.
 */
object GameIconScraper {

    private const val CACHE_DIR_NAME = "retro_art_cache"
    private const val CONNECT_TIMEOUT_MS = 2500
    private const val READ_TIMEOUT_MS = 2500
    private const val MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB max per icon
    private const val MAX_CACHE_SIZE_BYTES = 50L * 1024 * 1024 // 50 MB LRU limit

    private const val CDN_BASE_URL = "https://raw.githubusercontent.com/libretro-thumbnails"

    /**
     * Well-known GBA header game codes to canonical Libretro boxart filenames.
     */
    val KNOWN_GAME_CODES = mapOf(
        "BPEE" to "Pokemon - Emerald Version (USA, Europe)",
        "BPEJ" to "Pocket Monsters - Emerald (Japan)",
        "BPED" to "Pokemon - Smaragd-Edition (Germany)",
        "AXVE" to "Pokemon - Ruby Version (USA, Europe)",
        "AXPE" to "Pokemon - Sapphire Version (USA, Europe)",
        "BPRE" to "Pokemon - FireRed Version (USA, Europe)",
        "BPGE" to "Pokemon - LeafGreen Version (USA, Europe)",
        "BMXE" to "Metroid - Zero Mission (USA)",
        "BMXJ" to "Metroid - Zero Mission (Japan)",
        "BMXP" to "Metroid - Zero Mission (Europe)",
        "AMFE" to "Metroid Fusion (USA)",
        "AMFJ" to "Metroid Fusion (Japan)",
        "AMFP" to "Metroid Fusion (Europe)",
        "AMCE" to "The Legend of Zelda - The Minish Cap (USA)",
        "A2TE" to "The Legend of Zelda - A Link to the Past with Four Swords (USA)",
        "AATE" to "Castlevania - Aria of Sorrow (USA)",
        "ACHE" to "Castlevania - Harmony of Dissonance (USA)",
        "AANE" to "Castlevania - Circle of the Moon (USA)",
        "AGBE" to "Golden Sun (USA)",
        "AGFE" to "Golden Sun - The Lost Age (USA)",
        "AMRE" to "Mario Kart - Super Circuit (USA)",
        "A2VE" to "Super Mario Advance 2 - Super Mario World (USA)",
        "A3AE" to "Super Mario Advance 3 - Yoshi's Island (USA)",
        "A4BE" to "Super Mario Advance 4 - Super Mario Bros. 3 (USA)",
        "AGNA" to "Anguna - Warriors of the Demis (USA)",
        "AASE" to "Advance Wars (USA, Europe)",
        "AW2E" to "Advance Wars 2 - Black Hole Rising (USA, Europe)"
    )

    /**
     * Asynchronously fetches box art for a loaded ROM.
     * Checks disk cache first, queries CDN if cache miss, and caches result.
     */
    suspend fun fetchBoxArt(
        context: Context,
        platform: String,
        gameTitle: String,
        rawFileName: String? = null,
        gameCode: String? = null
    ): ByteArray? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { if (!exists()) mkdirs() }
        fetchBoxArtSync(
            cacheDir = cacheDir,
            platform = platform,
            gameTitle = gameTitle,
            rawFileName = rawFileName,
            gameCode = gameCode
        )
    }

    /**
     * Synchronous lookup logic for execution and unit tests.
     */
    fun fetchBoxArtSync(
        cacheDir: File,
        platform: String,
        gameTitle: String,
        rawFileName: String? = null,
        gameCode: String? = null,
        networkFetcher: ((String) -> ByteArray?)? = null
    ): ByteArray? {
        val cacheKey = computeCacheKey(platform, gameTitle, rawFileName, gameCode)
        val cachedFile = File(cacheDir, "$cacheKey.png")

        if (cachedFile.exists() && cachedFile.length() > 0) {
            try {
                return cachedFile.readBytes()
            } catch (_: Exception) {
                // Ignore read errors, proceed to fetch
            }
        }

        val candidates = buildCandidateTitles(gameTitle, rawFileName, gameCode)
        val systems = resolveSystems(platform)
        val fetcher = networkFetcher ?: { url -> fetchHttpBytes(url) }

        for (system in systems) {
            for (candidate in candidates) {
                val urlsToTry = listOf(
                    buildCdnUrl(system, "Named_Boxarts", candidate),
                    buildCdnUrl(system, "Named_Titles", candidate)
                )

                for (url in urlsToTry) {
                    val bytes = fetcher(url)
                    if (bytes != null && bytes.isNotEmpty()) {
                        saveToDiskCache(cacheDir, cachedFile, bytes)
                        return bytes
                    }
                }
            }
        }

        return null
    }

    /**
     * Sanitizes a ROM title or filename by stripping file extensions,
     * tags (e.g. `(USA)`, `[!]`, `(Europe)`), and normalizing whitespace.
     */
    fun sanitizeTitle(raw: String): String {
        var clean = raw
        // Strip common extensions
        clean = clean.replace(Regex("\\.(gba|gbc|gb|zip|7z|bin)$", RegexOption.IGNORE_CASE), "")
        // Strip parentheses tags e.g. (USA), (Europe, USA), (Rev 1), (v1.1)
        clean = clean.replace(Regex("\\s*\\([^)]*\\)"), "")
        // Strip bracket tags e.g. [!], [b1], [t1]
        clean = clean.replace(Regex("\\s*\\[[^\\]]*\\]"), "")
        // Replace underscores and excess spaces
        clean = clean.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
        return clean
    }

    /**
     * Builds prioritized list of candidate title strings to query in CDN.
     */
    fun buildCandidateTitles(
        gameTitle: String,
        rawFileName: String?,
        gameCode: String?
    ): List<String> {
        val candidates = mutableListOf<String>()

        // 1. Direct Game Code Mapping
        if (!gameCode.isNullOrBlank()) {
            KNOWN_GAME_CODES[gameCode.trim().uppercase()]?.let { candidates.add(it) }
        }

        // 2. Exact stripped filename (No-Intro style with regions)
        if (!rawFileName.isNullOrBlank()) {
            val fileWithoutExt = rawFileName.replace(Regex("\\.(gba|gbc|gb|zip|7z|bin)$", RegexOption.IGNORE_CASE), "")
            if (fileWithoutExt.isNotBlank()) {
                candidates.add(fileWithoutExt.trim())
            }
            val sanitizedFile = sanitizeTitle(rawFileName)
            if (sanitizedFile.isNotBlank()) {
                candidates.add(sanitizedFile)
                // Also add with region suffixes commonly found
                candidates.add("$sanitizedFile (USA)")
                candidates.add("$sanitizedFile (USA, Europe)")
                candidates.add("$sanitizedFile (Europe)")
            }
        }

        // 3. Header Game Title
        if (gameTitle.isNotBlank()) {
            val sanitizedTitle = sanitizeTitle(gameTitle)
            if (sanitizedTitle.isNotBlank()) {
                candidates.add(sanitizedTitle)
                candidates.add("$sanitizedTitle (USA)")
                candidates.add("$sanitizedTitle (USA, Europe)")
            }
        }

        return candidates.distinct()
    }

    fun resolveSystems(platform: String): List<String> {
        return when (platform.lowercase().trim()) {
            "gba" -> listOf("Nintendo_-_Game_Boy_Advance")
            "gbc" -> listOf("Nintendo_-_Game_Boy_Color", "Nintendo_-_Game_Boy")
            "gb" -> listOf("Nintendo_-_Game_Boy", "Nintendo_-_Game_Boy_Color")
            else -> listOf("Nintendo_-_Game_Boy_Advance", "Nintendo_-_Game_Boy_Color", "Nintendo_-_Game_Boy")
        }
    }

    fun buildCdnUrl(system: String, category: String, title: String): String {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
            .replace("+", "%20")
            .replace("%26", "&")
        return "$CDN_BASE_URL/$system/master/$category/$encodedTitle.png"
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
                setRequestProperty("User-Agent", "RetroPack-Manager/1.0")
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
                if (totalRead > MAX_IMAGE_SIZE_BYTES) {
                    return null // Image too large
                }
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
            // Non-critical cache write failure
        }
    }

    private fun enforceLruCache(cacheDir: File) {
        val files = cacheDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= MAX_CACHE_SIZE_BYTES) return

        // Sort oldest modified first
        val sorted = files.sortedBy { it.lastModified() }
        for (file in sorted) {
            val len = file.length()
            if (file.delete()) {
                totalSize -= len
                if (totalSize <= MAX_CACHE_SIZE_BYTES * 0.8) { // prune to 80% limit
                    break
                }
            }
        }
    }

    fun computeCacheKey(
        platform: String,
        gameTitle: String,
        rawFileName: String?,
        gameCode: String?
    ): String {
        val raw = "$platform:${gameCode ?: ""}:${rawFileName ?: ""}:${sanitizeTitle(gameTitle)}"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun clearCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (_: Exception) {
            // Ignore
        }
    }
}
