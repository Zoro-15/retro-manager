package com.retropack.domain.rom

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Robust, unified archive inspector and extractor for RetroPack.
 *
 * Supports transparent unpacking of:
 * - ZIP archives (.zip)
 * - RAR archives (.rar, RAR4 and RAR5)
 * - GZIP streams (.gz, .nes.gz, etc.)
 *
 * Automatically filters metadata/noise files and recursively unpacks nested archives
 * to locate and extract the candidate retro console ROM.
 */
object ArchiveExtractor {

    private const val MAX_RECURSION_DEPTH = 3

    /**
     * Comprehensive catalog of all supported retro console ROM file extensions across all 10 architectures.
     */
    val SUPPORTED_ROM_EXTENSIONS = setOf(
        // Nintendo Game Boy / Color / Advance
        ".gba", ".gbc", ".gb", ".sgb", ".cgb", ".agb",
        // Super Nintendo / Super Famicom
        ".sfc", ".smc", ".snes", ".fig", ".swc", ".bs", ".gd3", ".gd7", ".dx2",
        // Nintendo Entertainment System / Famicom / FDS
        ".nes", ".fds", ".unf", ".unif", ".fam",
        // Sega Genesis / Mega Drive / Master System / Game Gear / SG-1000 / Sega CD
        ".md", ".smd", ".gen", ".sms", ".gg", ".sg", ".sc", ".68k", ".sgd",
        // PC Engine / TurboGrafx-16 / SuperGrafx / PCE-CD
        ".pce", ".tg16", ".sgx", ".ccd", ".toc",
        // Sony PlayStation 1 (PSX)
        ".iso", ".cue", ".chd", ".pbp", ".img", ".mdf", ".ecm",
        // Nintendo 64
        ".z64", ".n64", ".v64", ".u64", ".ndd",
        // Nintendo DS / DSi
        ".nds", ".srl", ".dsi", ".ids",
        // Sony PSP
        ".cso", ".prx", ".elf",
        // Arcade / Neo Geo / Generic binary
        ".neo", ".bin"
    )

    val ARCHIVE_EXTENSIONS = setOf(
        ".zip", ".rar", ".7z", ".tar", ".gz"
    )

    private val IGNORED_EXTENSIONS = setOf(
        ".txt", ".nfo", ".diz", ".doc", ".pdf", ".rtf",
        ".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp",
        ".xml", ".html", ".htm", ".json", ".ini", ".cfg",
        ".url", ".lnk", ".exe", ".bat", ".sh", ".py",
        ".db", ".ds_store", ".git", ".svn"
    )

    /**
     * Result of an archive extraction or inspection.
     */
    data class ExtractedRomResult(
        val bytes: ByteArray,
        val candidateFileName: String,
        val originalFileName: String,
        val isExtractedFromArchive: Boolean,
        val archiveType: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ExtractedRomResult
            return bytes.contentEquals(other.bytes) &&
                candidateFileName == other.candidateFileName &&
                originalFileName == other.originalFileName &&
                isExtractedFromArchive == other.isExtractedFromArchive &&
                archiveType == other.archiveType
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + candidateFileName.hashCode()
            result = 31 * result + originalFileName.hashCode()
            result = 31 * result + isExtractedFromArchive.hashCode()
            result = 31 * result + (archiveType?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * Determines whether the given byte prefix or file name indicates an archive format.
     */
    fun isArchive(bytes: ByteArray, fileName: String? = null): Boolean {
        if (!fileName.isNullOrBlank()) {
            val lower = fileName.lowercase(Locale.US)
            if (ARCHIVE_EXTENSIONS.any { lower.endsWith(it) }) return true
        }

        if (bytes.size >= 4) {
            // ZIP magic: PK\x03\x04 or PK\x05\x06 or PK\x07\x08
            if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) return true
            // RAR magic: Rar!\x1A\x07
            if (bytes.size >= 7 &&
                bytes[0] == 0x52.toByte() && bytes[1] == 0x61.toByte() &&
                bytes[2] == 0x72.toByte() && bytes[3] == 0x21.toByte() &&
                bytes[4] == 0x1A.toByte() && bytes[5] == 0x07.toByte()
            ) return true
            // 7Z magic: 7z\xBC\xAF\x27\x1C
            if (bytes.size >= 6 &&
                bytes[0] == 0x37.toByte() && bytes[1] == 0x7A.toByte() &&
                bytes[2] == 0xBC.toByte() && bytes[3] == 0xAF.toByte()
            ) return true
            // GZIP magic: \x1F\x8B
            if (bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) return true
        }

        return false
    }

    /**
     * Inspects incoming file from disk. If it is a ZIP or RAR archive, extracts the primary ROM file.
     */
    fun extractCandidateRom(file: File): ExtractedRomResult {
        val rawBytes = file.readBytes()
        return extractCandidateRom(rawBytes, file.name)
    }

    /**
     * Inspects incoming byte array and file name. If it is a ZIP or RAR (or GZIP) archive,
     * extracts the inner ROM bytes and inner candidate file name. Recursively unpacks nested archives.
     */
    fun extractCandidateRom(
        rawBytes: ByteArray,
        rawFileName: String,
        currentDepth: Int = 0
    ): ExtractedRomResult {
        if (currentDepth >= MAX_RECURSION_DEPTH || !isArchive(rawBytes, rawFileName)) {
            return ExtractedRomResult(
                bytes = rawBytes,
                candidateFileName = rawFileName,
                originalFileName = rawFileName,
                isExtractedFromArchive = currentDepth > 0
            )
        }

        val lowerName = rawFileName.lowercase(Locale.US)

        // 1. Try ZIP extraction
        if (isZip(rawBytes, lowerName)) {
            val zipResult = runCatching { extractFromZip(rawBytes, rawFileName) }.getOrNull()
            if (zipResult != null) {
                if (isArchive(zipResult.bytes, zipResult.candidateFileName) && currentDepth < MAX_RECURSION_DEPTH) {
                    return extractCandidateRom(zipResult.bytes, zipResult.candidateFileName, currentDepth + 1)
                }
                return zipResult
            }
        }

        // 2. Try RAR extraction
        if (isRar(rawBytes, lowerName)) {
            val rarResult = runCatching { extractFromRar(rawBytes, rawFileName) }.getOrNull()
            if (rarResult != null) {
                if (isArchive(rarResult.bytes, rarResult.candidateFileName) && currentDepth < MAX_RECURSION_DEPTH) {
                    return extractCandidateRom(rarResult.bytes, rarResult.candidateFileName, currentDepth + 1)
                }
                return rarResult
            }
        }

        // 3. Try GZIP decompression (e.g. game.nes.gz)
        if (isGzip(rawBytes, lowerName)) {
            val gzResult = runCatching { extractFromGzip(rawBytes, rawFileName) }.getOrNull()
            if (gzResult != null) {
                if (isArchive(gzResult.bytes, gzResult.candidateFileName) && currentDepth < MAX_RECURSION_DEPTH) {
                    return extractCandidateRom(gzResult.bytes, gzResult.candidateFileName, currentDepth + 1)
                }
                return gzResult
            }
        }

        return ExtractedRomResult(
            bytes = rawBytes,
            candidateFileName = rawFileName,
            originalFileName = rawFileName,
            isExtractedFromArchive = currentDepth > 0
        )
    }

    private fun isZip(bytes: ByteArray, lowerName: String): Boolean {
        return lowerName.endsWith(".zip") ||
            (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())
    }

    private fun isRar(bytes: ByteArray, lowerName: String): Boolean {
        return lowerName.endsWith(".rar") ||
            (bytes.size >= 7 &&
                bytes[0] == 0x52.toByte() && bytes[1] == 0x61.toByte() &&
                bytes[2] == 0x72.toByte() && bytes[3] == 0x21.toByte() &&
                bytes[4] == 0x1A.toByte() && bytes[5] == 0x07.toByte())
    }

    private fun isGzip(bytes: ByteArray, lowerName: String): Boolean {
        return lowerName.endsWith(".gz") ||
            (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte())
    }

    /**
     * Extracts the best candidate ROM entry from a ZIP archive.
     */
    private fun extractFromZip(rawBytes: ByteArray, rawFileName: String): ExtractedRomResult? {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(rawBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val cleanName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    if (!isIgnoredFile(cleanName)) {
                        val content = zis.readBytes()
                        entries.add(Pair(cleanName, content))
                    }
                }
                entry = zis.nextEntry
            }
        }

        if (entries.isEmpty()) return null

        val best = selectBestEntry(entries.map { it.first }) ?: entries.first().first
        val matched = entries.firstOrNull { it.first == best } ?: entries.first()

        return ExtractedRomResult(
            bytes = matched.second,
            candidateFileName = matched.first,
            originalFileName = rawFileName,
            isExtractedFromArchive = true,
            archiveType = "ZIP"
        )
    }

    /**
     * Extracts the best candidate ROM entry from a RAR archive using Junrar.
     */
    private fun extractFromRar(rawBytes: ByteArray, rawFileName: String): ExtractedRomResult? {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        Archive(ByteArrayInputStream(rawBytes)).use { archive ->
            var header: FileHeader? = archive.nextFileHeader()
            while (header != null) {
                if (!header.isDirectory) {
                    val fullPath = header.fileName ?: header.fileNameW ?: ""
                    val cleanName = fullPath.substringAfterLast('/').substringAfterLast('\\')
                    if (!isIgnoredFile(cleanName)) {
                        val baos = ByteArrayOutputStream()
                        archive.extractFile(header, baos)
                        entries.add(Pair(cleanName, baos.toByteArray()))
                    }
                }
                header = archive.nextFileHeader()
            }
        }

        if (entries.isEmpty()) return null

        val best = selectBestEntry(entries.map { it.first }) ?: entries.first().first
        val matched = entries.firstOrNull { it.first == best } ?: entries.first()

        return ExtractedRomResult(
            bytes = matched.second,
            candidateFileName = matched.first,
            originalFileName = rawFileName,
            isExtractedFromArchive = true,
            archiveType = "RAR"
        )
    }

    /**
     * Decompresses a single GZIP stream.
     */
    private fun extractFromGzip(rawBytes: ByteArray, rawFileName: String): ExtractedRomResult? {
        val decompressed = GZIPInputStream(ByteArrayInputStream(rawBytes)).use { it.readBytes() }
        val candidateName = if (rawFileName.endsWith(".gz", ignoreCase = true)) {
            rawFileName.substringBeforeLast(".gz", rawFileName.substringBeforeLast('.'))
        } else {
            rawFileName
        }
        return ExtractedRomResult(
            bytes = decompressed,
            candidateFileName = candidateName,
            originalFileName = rawFileName,
            isExtractedFromArchive = true,
            archiveType = "GZIP"
        )
    }

    /**
     * Evaluates a list of entry filenames and picks the single best ROM candidate.
     * Prioritizes explicit console extensions (.gba, .sfc, .nes, .pce, .z64, .nds)
     * over generic binary files (.bin, .iso, .cue).
     */
    fun selectBestEntry(entryNames: List<String>): String? {
        if (entryNames.isEmpty()) return null

        // 1. High-priority console-specific extensions
        val highPriority = entryNames.firstOrNull { name ->
            val lower = name.lowercase(Locale.US)
            lower.endsWith(".gba") || lower.endsWith(".gbc") || lower.endsWith(".gb") ||
                lower.endsWith(".sfc") || lower.endsWith(".smc") || lower.endsWith(".snes") ||
                lower.endsWith(".nes") || lower.endsWith(".fds") ||
                lower.endsWith(".md") || lower.endsWith(".gen") || lower.endsWith(".smd") ||
                lower.endsWith(".pce") || lower.endsWith(".sgx") || lower.endsWith(".tg16") ||
                lower.endsWith(".z64") || lower.endsWith(".n64") || lower.endsWith(".v64") ||
                lower.endsWith(".nds") || lower.endsWith(".srl") ||
                lower.endsWith(".pbp") || lower.endsWith(".chd") || lower.endsWith(".cso")
        }
        if (highPriority != null) return highPriority

        // 2. Any supported ROM extension (including .bin, .iso, .cue, .img)
        val anyRom = entryNames.firstOrNull { name ->
            val lower = name.lowercase(Locale.US)
            SUPPORTED_ROM_EXTENSIONS.any { lower.endsWith(it) }
        }
        if (anyRom != null) return anyRom

        // 3. Nested archives
        val nestedArchive = entryNames.firstOrNull { name ->
            val lower = name.lowercase(Locale.US)
            ARCHIVE_EXTENSIONS.any { lower.endsWith(it) }
        }
        if (nestedArchive != null) return nestedArchive

        return entryNames.firstOrNull()
    }

    private fun isIgnoredFile(fileName: String): Boolean {
        val clean = fileName.trim()
        if (clean.isBlank() || clean.startsWith(".") || clean.startsWith("__MACOSX")) {
            return true
        }
        val lower = clean.lowercase(Locale.US)
        return IGNORED_EXTENSIONS.any { lower.endsWith(it) }
    }
}
