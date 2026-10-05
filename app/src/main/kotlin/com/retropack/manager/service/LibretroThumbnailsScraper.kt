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
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000
    private const val MAX_IMAGE_SIZE_BYTES = 8 * 1024 * 1024 // 8 MB max
    private const val MAX_CACHE_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB LRU limit

    private const val LIBRETRO_CDN_BASE = "https://thumbnails.libretro.com"

    /**
     * Golden 5 2D Console Architecture System Directory Mapping Table
     */
    val SYSTEM_DIR_MAP = mapOf(
        "gba" to "Nintendo%20-%20Game%20Boy%20Advance",
        "gbc" to "Nintendo%20-%20Game%20Boy%20Color",
        "gb" to "Nintendo%20-%20Game%20Boy",
        "snes" to "Nintendo%20-%20Super%20Nintendo%20Entertainment%20System",
        "sfc" to "Nintendo%20-%20Super%20Nintendo%20Entertainment%20System",
        "nes" to "Nintendo%20-%20Nintendo%20Entertainment%20System",
        "fds" to "Nintendo%20-%20Family%20Computer%20Disk%20System",
        "genesis" to "Sega%20-%20Mega%20Drive%20-%20Genesis",
        "md" to "Sega%20-%20Mega%20Drive%20-%20Genesis",
        "gen" to "Sega%20-%20Mega%20Drive%20-%20Genesis",
        "sms" to "Sega%20-%20Master%20System%20-%20Mark%20III",
        "gg" to "Sega%20-%20Game%20Gear",
        "pce" to "NEC%20-%20PC%20Engine%20-%20TurboGrafx%2016",
        "tg16" to "NEC%20-%20PC%20Engine%20-%20TurboGrafx%2016",
        "sgx" to "NEC%20-%20PC%20Engine%20SuperGrafx"
    )

    val CATEGORIES = listOf("Named_Boxarts", "Named_Titles", "Named_Snaps")

    private val ROMAN_NUMERALS = setOf("II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV")

    private val KNOWN_GAMING_WORDS = listOf(
        "CASTLEVANIA", "BANDICOOT", "BOMBERMAN", "DONKEY", "DRAGON", "FANTASY",
        "FIGHTER", "GOLDEN", "HARVEST", "KAZOOIE", "KOMBAT", "LEGEND",
        "METROID", "MONSTER", "PACMAN", "POKEMON", "RAIDER", "RESIDENT",
        "SILENT", "STREET", "TRIGGER", "TURISMO", "TWISTED", "WARRIOR",
        "ADVANCE", "CONTRA", "CRASH", "EARTH", "FINAL", "KART", "KIRBY",
        "KONG", "MARIO", "METAL", "NINJA", "RIDGE", "RACER", "SONIC",
        "SPEED", "SPYRO", "SUPER", "TOOIE", "WARIO", "WORLD", "YOSHI",
        "ZELDA", "BROS", "CHOP", "EVIL", "FIRE", "FUSION", "GEAR", "HERO",
        "HILL", "LAND", "MEGA", "MOON", "RUSH", "SOLID", "STAR", "TOMB",
        "WARS", "WAVE", "WILD", "AUTO", "BALL", "BOMB", "DARK", "DEAD",
        "DUAL", "EYE", "FOX", "FZERO", "MAN", "RACE", "SOUL", "ZERO", "EMER"
    ).sortedByDescending { it.length }

    /**
     * Splits compressed titles like MARIOKART64 -> Mario Kart 64, SUPERMARIO -> Super Mario.
     */
    fun splitCompressedTitle(raw: String): String {
        if (raw.isBlank()) return raw

        // 1. Separate digits and letters (e.g. MARIOKART64 -> MARIOKART 64, 1080SNOW -> 1080 SNOW)
        var s = raw.replace(Regex("([a-zA-Z])([0-9])"), "$1 $2")
            .replace(Regex("([0-9])([a-zA-Z])"), "$1 $2")

        // 2. Separate CamelCase (e.g. MarioKart -> Mario Kart)
        s = s.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")

        // 3. Segment all-caps words by known gaming vocabulary
        val tokens = s.split(Regex("\\s+")).filter { it.isNotBlank() }
        val segmentedTokens = mutableListOf<String>()

        for (token in tokens) {
            if (token.all { it.isUpperCase() } && token.length > 5 && !ROMAN_NUMERALS.contains(token)) {
                val words = segmentAllCapsWord(token)
                segmentedTokens.addAll(words)
            } else {
                segmentedTokens.add(token)
            }
        }

        // 4. Convert all-caps tokens to title case (e.g. MARIO -> Mario, preserves roman numerals)
        return segmentedTokens.joinToString(" ") { word ->
            if (ROMAN_NUMERALS.contains(word)) {
                word
            } else if (word.all { it.isUpperCase() } && word.length > 1) {
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } else {
                word
            }
        }.trim()
    }

    private fun segmentAllCapsWord(word: String): List<String> {
        val result = mutableListOf<String>()
        var remaining = word
        while (remaining.isNotEmpty()) {
            val match = KNOWN_GAMING_WORDS.firstOrNull { remaining.startsWith(it) }
            if (match != null && (remaining.length == match.length || remaining.length - match.length >= 2)) {
                result.add(match)
                remaining = remaining.substring(match.length)
            } else {
                result.add(remaining)
                break
            }
        }
        return if (result.isEmpty()) listOf(word) else result
    }

    /**
     * Sanitizes title string according to Libretro repository naming conventions:
     * Characters illegal on cross-platform filesystems are replaced with '_'.
     */
    fun sanitizeLibretroTitle(raw: String): String {
        var s = raw
        // Strip common extension
        s = s.replace(Regex("\\.(gba|gbc|gb|zip|7z|bin|sfc|smc|snes|fig|nes|fds|unf|md|smd|gen|sms|gg|pce|tg16|sgx)$", RegexOption.IGNORE_CASE), "")
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
     * Prioritizes clean file name first, then smart-tokenized header titles.
     */
    fun generateCandidateTitles(
        gameTitle: String,
        rawFileName: String? = null,
        region: String = "USA"
    ): List<String> {
        val candidates = mutableListOf<String>()

        fun addTierVariants(base: String) {
            val clean = sanitizeLibretroTitle(base)
            if (clean.isBlank()) return
            val reordered = normalizeArticleReordering(clean)
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

        // Priority 1: Clean Raw File Name
        if (rawFileName != null && rawFileName.isNotBlank()) {
            val stripped = rawFileName.replace(Regex("\\.(gba|gbc|gb|zip|7z|bin|sfc|smc|snes|fig|nes|fds|unf|md|smd|gen|sms|gg|pce|tg16|sgx)$", RegexOption.IGNORE_CASE), "")
            val cleanStripped = sanitizeLibretroTitle(stripped)
            // Top priority: literal stripped file name if it already has region / brackets
            candidates.add(cleanStripped)
            val baseFileName = stripped.replace(Regex("\\s*\\([^)]*\\)"), "").replace(Regex("\\s*\\[[^\\]]*\\]"), "").trim()
            addTierVariants(baseFileName)
        }

        // Priority 2: Smart-tokenized ROM header title (e.g. "MARIOKART64" -> "Mario Kart 64")
        if (gameTitle.isNotBlank()) {
            val cleanHeader = gameTitle.replace(Regex("\\s*\\([^)]*\\)"), "").replace(Regex("\\s*\\[[^\\]]*\\]"), "").trim()
            val tokenized = splitCompressedTitle(cleanHeader)
            if (tokenized.isNotBlank() && !tokenized.equals(cleanHeader, ignoreCase = false)) {
                addTierVariants(tokenized)
            }
            addTierVariants(cleanHeader)
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
                val encodedCandidate = URLEncoder.encode(candidate, "UTF-8")
                    .replace("+", "%20")
                    .replace("%28", "(")
                    .replace("%29", ")")
                    .replace("%2C", ",")
                    .replace("%27", "'")
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
