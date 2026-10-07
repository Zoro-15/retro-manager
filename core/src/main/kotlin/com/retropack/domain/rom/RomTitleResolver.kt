package com.retropack.domain.rom

/**
 * Intelligent ROM Title Resolver and Sanitizer.
 *
 * Resolves clean, human-readable display titles from ROM filenames, cartridge headers,
 * and well-known serial/game codes. Strips dump tags, region codes, and revision identifiers
 * (e.g. `Golden Sun (USA, Europe).zip` -> `Golden Sun`).
 */
object RomTitleResolver {

    /**
     * Canonical mapping of well-known retro cartridge game codes to clean title names.
     */
    val KNOWN_GAME_CODES: Map<String, String> = mapOf(
        // GBA Classics
        "AGBE" to "Golden Sun",
        "AGBJ" to "Golden Sun",
        "AGBP" to "Golden Sun",
        "AGFE" to "Golden Sun: The Lost Age",
        "AGFJ" to "Golden Sun: The Lost Age",
        "AGFP" to "Golden Sun: The Lost Age",
        "BPEE" to "Pokemon - Emerald Version",
        "BPEJ" to "Pokemon - Emerald Version",
        "BPED" to "Pokemon - Emerald Version",
        "AXVE" to "Pokemon - Ruby Version",
        "AXVJ" to "Pokemon - Ruby Version",
        "AXPE" to "Pokemon - Sapphire Version",
        "AXPJ" to "Pokemon - Sapphire Version",
        "BPRE" to "Pokemon - FireRed Version",
        "BPRJ" to "Pokemon - FireRed Version",
        "BPGE" to "Pokemon - LeafGreen Version",
        "BPGJ" to "Pokemon - LeafGreen Version",
        "BMXE" to "Metroid - Zero Mission",
        "BMXJ" to "Metroid - Zero Mission",
        "BMXP" to "Metroid - Zero Mission",
        "AMFE" to "Metroid Fusion",
        "AMFJ" to "Metroid Fusion",
        "AMFP" to "Metroid Fusion",
        "AMCE" to "The Legend of Zelda: The Minish Cap",
        "AMCJ" to "The Legend of Zelda: The Minish Cap",
        "AMCP" to "The Legend of Zelda: The Minish Cap",
        "A2TE" to "The Legend of Zelda: A Link to the Past",
        "A2TJ" to "The Legend of Zelda: A Link to the Past",
        "A2TP" to "The Legend of Zelda: A Link to the Past",
        "AATE" to "Castlevania: Aria of Sorrow",
        "AATJ" to "Castlevania: Aria of Sorrow",
        "AATP" to "Castlevania: Aria of Sorrow",
        "ACHE" to "Castlevania: Harmony of Dissonance",
        "AANE" to "Castlevania: Circle of the Moon",
        "AMRE" to "Mario Kart: Super Circuit",
        "A2VE" to "Super Mario Advance 2: Super Mario World",
        "A3AE" to "Super Mario Advance 3: Yoshi's Island",
        "A4BE" to "Super Mario Advance 4: Super Mario Bros. 3",
        "AASE" to "Advance Wars",
        "AW2E" to "Advance Wars 2: Black Hole Rising",
        "AGNA" to "Anguna - Warriors of the Demis",

        // GB / GBC Classics
        "AHYE" to "Super Mario Bros. Deluxe",
        "AHYJ" to "Super Mario Bros. Deluxe",
        "AHYP" to "Super Mario Bros. Deluxe",
        "AZLE" to "The Legend of Zelda: Link's Awakening DX",
        "AZLJ" to "The Legend of Zelda: Link's Awakening DX",
        "AZLP" to "The Legend of Zelda: Link's Awakening DX",
        "AZ7E" to "The Legend of Zelda: Oracle of Seasons",
        "AZ8E" to "The Legend of Zelda: Oracle of Ages",
        "BYTE" to "Pokemon - Crystal Version",
        "AAUE" to "Pokemon - Gold Version",
        "AAXE" to "Pokemon - Silver Version",
        "AARE" to "Pokemon - Red Version",
        "AAPE" to "Pokemon - Blue Version",
        "APSE" to "Pokemon - Yellow Version"
    )

    private val GENERIC_FILE_NAMES = setOf(
        "rom", "game", "disc", "disc1", "disc2", "disc3", "disc4",
        "track01", "track02", "track1", "track2", "download", "archive", "content",
        "image", "cartridge", "bios", "boot", "untitled", "test"
    )

    private val ROM_EXTENSIONS = Regex(
        """\.(gba|agb|gbc|cgb|gb|sgb|sfc|smc|snes|fig|swc|nes|fds|unf|unif|fam|md|smd|gen|68k|sms|gg|sg|pce|tg16|sgx|bin|zip|7z|rar|iso|cue|chd|rom|nds|n64|z64|v64|elf|tar|gz|bz2|xz)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Resolves the best available human-readable title.
     * Prioritizes clean source filename if non-generic, then known game codes, then cleaned cartridge headers.
     */
    fun resolveTitle(
        fileName: String? = null,
        gameCode: String? = null,
        headerTitle: String? = null,
        platform: String = ""
    ): String {
        // 1. Clean source filename if available and non-generic
        if (!fileName.isNullOrBlank()) {
            val cleanFromFileName = sanitizeTitle(fileName)
            val baseName = cleanFromFileName.substringBeforeLast('.').lowercase().trim()
            val fullCleanLower = cleanFromFileName.lowercase().trim()
            if (cleanFromFileName.isNotBlank() && baseName !in GENERIC_FILE_NAMES && fullCleanLower !in GENERIC_FILE_NAMES) {
                return cleanFromFileName
            }
        }

        // 2. Direct game code match
        if (!gameCode.isNullOrBlank()) {
            val upperCode = gameCode.trim().uppercase()
            KNOWN_GAME_CODES[upperCode]?.let { return it }
        }

        // 3. Cartridge Header Title fallback
        if (!headerTitle.isNullOrBlank()) {
            var rawHeader = headerTitle.trim()

            // Strip trailing GBC 4-character manufacturer code (e.g. "MARIO DELUXAHYE" -> "MARIO DELUX")
            if (platform.equals("gbc", ignoreCase = true) && rawHeader.length == 15) {
                val potentialTitle = rawHeader.substring(0, 11).trim()
                if (potentialTitle.length >= 4) {
                    rawHeader = potentialTitle
                }
            }

            // Strip single character version suffixes like "_A" in "Golden_Sun_A"
            if (rawHeader.endsWith("_A") || rawHeader.endsWith("_B") || rawHeader.endsWith("_C")) {
                rawHeader = rawHeader.substring(0, rawHeader.length - 2).trim()
            }

            rawHeader = rawHeader.replace('_', ' ').trim()

            return rawHeader
        }

        return "Retro Game"
    }

    /**
     * Sanitizes a title or filename by stripping extensions, region tags `(USA)`,
     * dump tags `[!]`, underscores, and excess whitespace.
     */
    fun sanitizeTitle(raw: String): String {
        var clean = raw

        // Strip file extension
        clean = clean.replace(ROM_EXTENSIONS, "")

        // Strip parenthesized tags e.g. (USA), (Europe, USA), (Rev 1), (v1.1), (Beta)
        clean = clean.replace(Regex("""\s*\([^)]*\)"""), "")

        // Strip bracket tags e.g. [!], [b1], [t1], [h1], [a1]
        clean = clean.replace(Regex("""\s*\[[^\]]*\]"""), "")

        // Replace underscores with spaces
        clean = clean.replace('_', ' ')

        // Normalize hyphen spacing: "Super Mario - World" -> "Super Mario - World"
        clean = clean.replace(Regex("""\s*-\s*"""), " - ")

        // Normalize whitespace
        clean = clean.replace(Regex("""\s+"""), " ").trim()

        return clean
    }
}
