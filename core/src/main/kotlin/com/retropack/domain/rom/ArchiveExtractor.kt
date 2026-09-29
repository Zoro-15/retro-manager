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
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel

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
     * File-based result of streaming archive extraction to disk.
     */
    data class ExtractedRomFileResult(
        val file: File,
        val candidateFileName: String,
        val originalFileName: String,
        val isExtractedFromArchive: Boolean,
        val archiveType: String? = null
    )

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
     * Determines whether the header indicates a modern RAR 5.0 archive.
     */
    fun isRar5(bytes: ByteArray): Boolean {
        return bytes.size >= 8 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x61.toByte() &&
            bytes[2] == 0x72.toByte() && bytes[3] == 0x21.toByte() &&
            bytes[4] == 0x1A.toByte() && bytes[5] == 0x07.toByte() &&
            bytes[6] == 0x01.toByte() && bytes[7] == 0x00.toByte()
    }

    /**
     * Checks if the filename indicates a multi-part split archive (e.g. .part1.rar, .r00, .z01).
     */
    fun isMultiPartArchive(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.US)
        return lower.matches(Regex(""".*\.part\d+\.rar$""")) ||
            lower.matches(Regex(""".*\.r\d{2,}$""")) ||
            lower.matches(Regex(""".*\.z\d{2,}$""")) ||
            lower.matches(Regex(""".*\.7z\.\d{3,}$"""))
    }

    /**
     * Inspects an incoming file on disk. If it is an archive, extracts the primary ROM file directly
     * to [destinationDir] using constant 64 KB memory streaming. Recursively unpacks nested archives.
     */
    fun extractCandidateRomToDisk(
        sourceFile: File,
        destinationDir: File,
        currentDepth: Int = 0
    ): ExtractedRomFileResult {
        destinationDir.mkdirs()

        if (isMultiPartArchive(sourceFile.name)) {
            throw InvalidRomException(
                "Multi-part split archive detected for '${sourceFile.name}'. " +
                "Please unpack the full set using 7-Zip, ZArchiver, or WinRAR, then import the uncompressed ROM or disc image."
            )
        }

        val prefix = ByteArray(16)
        val readLen = runCatching {
            sourceFile.inputStream().use { it.read(prefix) }
        }.getOrDefault(0)
        val headerBytes = if (readLen > 0) prefix.copyOf(readLen) else ByteArray(0)

        if (currentDepth >= MAX_RECURSION_DEPTH || !isArchive(headerBytes, sourceFile.name)) {
            return ExtractedRomFileResult(
                file = sourceFile,
                candidateFileName = sourceFile.name,
                originalFileName = sourceFile.name,
                isExtractedFromArchive = currentDepth > 0
            )
        }

        val lowerName = sourceFile.name.lowercase(Locale.US)

        // 1. Try ZIP extraction directly to disk file
        if (isZip(headerBytes, lowerName)) {
            try {
                val zipResult = extractFromZipFileToDisk(sourceFile, destinationDir)
                if (zipResult != null) {
                    val nextHeader = ByteArray(16)
                    val nextLen = runCatching { zipResult.file.inputStream().use { it.read(nextHeader) } }.getOrDefault(0)
                    if (isArchive(nextHeader.copyOf(nextLen), zipResult.candidateFileName) && currentDepth < MAX_RECURSION_DEPTH) {
                        return extractCandidateRomToDisk(zipResult.file, destinationDir, currentDepth + 1)
                    }
                    return zipResult
                }
            } catch (e: InvalidRomException) {
                throw e
            } catch (e: Exception) {
                // Ignore fallback to raw file if not a valid zip
            }
        }

        // 2. Try RAR extraction
        if (isRar(headerBytes, lowerName)) {
            if (isRar5(headerBytes)) {
                throw InvalidRomException(
                    "RAR 5.0 archive format detected for '${sourceFile.name}'. " +
                    "Pure Java Junrar runtime cannot decompress RAR5 archives. Please unpack this game using 7-Zip, ZArchiver, or WinRAR, or import standard .zip / .7z archives or uncompressed ROM files (.z64, .nds, .gba, .iso, .cue, .chd, .pbp)."
                )
            }
            try {
                val rarResult = extractFromRarFileToDisk(sourceFile, destinationDir)
                if (rarResult != null) {
                    val nextHeader = ByteArray(16)
                    val nextLen = runCatching { rarResult.file.inputStream().use { it.read(nextHeader) } }.getOrDefault(0)
                    if (isArchive(nextHeader.copyOf(nextLen), rarResult.candidateFileName) && currentDepth < MAX_RECURSION_DEPTH) {
                        return extractCandidateRomToDisk(rarResult.file, destinationDir, currentDepth + 1)
                    }
                    return rarResult
                }
            } catch (e: InvalidRomException) {
                throw e
            } catch (e: com.github.junrar.exception.UnsupportedRarV5Exception) {
                throw InvalidRomException(
                    "RAR 5.0 archive format detected for '${sourceFile.name}'. " +
                    "Pure Java Junrar runtime cannot decompress RAR5 archives. Please unpack this game using 7-Zip, ZArchiver, or WinRAR, or import standard .zip / .7z archives or uncompressed ROM files (.z64, .nds, .gba, .iso, .cue, .chd, .pbp)."
                )
            } catch (e: Exception) {
                throw InvalidRomException("Failed to unpack RAR archive '${sourceFile.name}': ${e.message ?: "corrupted or unsupported format"}. Please unpack with 7-Zip/ZArchiver or import raw ROM.")
            }
        }

        // 3. Try 7Z extraction directly to disk file
        if (is7z(headerBytes, lowerName)) {
            try {
                val sevenZResult = extractFrom7zFileToDisk(sourceFile, destinationDir)
                if (sevenZResult != null) {
                    val nextHeader = ByteArray(16)
                    val nextLen = runCatching { sevenZResult.file.inputStream().use { it.read(nextHeader) } }.getOrDefault(0)
                    if (isArchive(nextHeader.copyOf(nextLen), sevenZResult.candidateFileName) && currentDepth < MAX_RECURSION_DEPTH) {
                        return extractCandidateRomToDisk(sevenZResult.file, destinationDir, currentDepth + 1)
                    }
                    return sevenZResult
                }
            } catch (e: InvalidRomException) {
                throw e
            } catch (e: Exception) {
                // Ignore fallback
            }
        }

        // 4. Try GZIP decompression to disk file
        if (isGzip(headerBytes, lowerName)) {
            try {
                val gzResult = extractFromGzipFileToDisk(sourceFile, destinationDir)
                if (gzResult != null) {
                    val nextHeader = ByteArray(16)
                    val nextLen = runCatching { gzResult.file.inputStream().use { it.read(nextHeader) } }.getOrDefault(0)
                    if (isArchive(nextHeader.copyOf(nextLen), gzResult.candidateFileName) && currentDepth < MAX_RECURSION_DEPTH) {
                        return extractCandidateRomToDisk(gzResult.file, destinationDir, currentDepth + 1)
                    }
                    return gzResult
                }
            } catch (e: InvalidRomException) {
                throw e
            } catch (e: Exception) {
                // Ignore fallback
            }
        }

        return ExtractedRomFileResult(
            file = sourceFile,
            candidateFileName = sourceFile.name,
            originalFileName = sourceFile.name,
            isExtractedFromArchive = currentDepth > 0
        )
    }

    private fun extractFromZipFileToDisk(sourceFile: File, outputDir: File): ExtractedRomFileResult? {
        val zip = runCatching { java.util.zip.ZipFile(sourceFile) }.getOrNull() ?: return null
        zip.use {
            val entries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
            val validNames = entries.map { entry ->
                entry.name.substringAfterLast('/').substringAfterLast('\\')
            }.filter { !isIgnoredFile(it) }

            if (validNames.isEmpty()) return null
            val bestName = selectBestEntry(validNames) ?: validNames.first()
            val bestEntry = entries.firstOrNull {
                it.name.substringAfterLast('/').substringAfterLast('\\') == bestName
            } ?: entries.first()

            val outputFile = File(outputDir, bestName)
            zip.getInputStream(bestEntry).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }

            return ExtractedRomFileResult(
                file = outputFile,
                candidateFileName = bestName,
                originalFileName = sourceFile.name,
                isExtractedFromArchive = true,
                archiveType = "ZIP"
            )
        }
    }

    private fun extractFrom7zFileToDisk(sourceFile: File, outputDir: File): ExtractedRomFileResult? {
        val sevenZFile = runCatching { SevenZFile(sourceFile) }.getOrNull() ?: return null
        sevenZFile.use { szf ->
            val entryList = mutableListOf<String>()
            var e = szf.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val clean = e.name.substringAfterLast('/').substringAfterLast('\\')
                    if (!isIgnoredFile(clean)) {
                        entryList.add(clean)
                    }
                }
                e = szf.nextEntry
            }

            if (entryList.isEmpty()) return null
            val bestName = selectBestEntry(entryList) ?: entryList.first()

            // Re-open to extract chosen entry
            SevenZFile(sourceFile).use { reader ->
                var targetEntry = reader.nextEntry
                while (targetEntry != null) {
                    val clean = targetEntry.name.substringAfterLast('/').substringAfterLast('\\')
                    if (clean == bestName) {
                        val outputFile = File(outputDir, bestName)
                        outputFile.outputStream().use { fos ->
                            val buffer = ByteArray(64 * 1024)
                            var readCount: Int
                            while (reader.read(buffer, 0, buffer.size).also { readCount = it } > 0) {
                                fos.write(buffer, 0, readCount)
                            }
                        }
                        return ExtractedRomFileResult(
                            file = outputFile,
                            candidateFileName = bestName,
                            originalFileName = sourceFile.name,
                            isExtractedFromArchive = true,
                            archiveType = "7Z"
                        )
                    }
                    targetEntry = reader.nextEntry
                }
            }
        }
        return null
    }

    private fun extractFromRarFileToDisk(sourceFile: File, outputDir: File): ExtractedRomFileResult? {
        Archive(sourceFile).use { archive ->
            if (archive.isEncrypted) {
                throw InvalidRomException("The RAR archive '${sourceFile.name}' is password-protected or encrypted. Password-protected archives cannot be unpacked automatically.")
            }
            val headers = mutableListOf<FileHeader>()
            var h: FileHeader? = archive.nextFileHeader()
            while (h != null) {
                if (h.isEncrypted) {
                    throw InvalidRomException("The RAR archive entry '${h.fileName}' is password-protected and cannot be extracted.")
                }
                if (!h.isDirectory) {
                    val fullPath = h.fileName ?: h.fileNameW ?: ""
                    val clean = fullPath.substringAfterLast('/').substringAfterLast('\\')
                    if (!isIgnoredFile(clean)) {
                        headers.add(h)
                    }
                }
                h = archive.nextFileHeader()
            }

            if (headers.isEmpty()) return null
            val names = headers.map { (it.fileName ?: it.fileNameW ?: "").substringAfterLast('/').substringAfterLast('\\') }
            val bestName = selectBestEntry(names) ?: names.first()
            val bestHeader = headers.firstOrNull {
                (it.fileName ?: it.fileNameW ?: "").substringAfterLast('/').substringAfterLast('\\') == bestName
            } ?: headers.first()

            val outputFile = File(outputDir, bestName)
            outputFile.outputStream().use { fos ->
                archive.extractFile(bestHeader, fos)
            }

            return ExtractedRomFileResult(
                file = outputFile,
                candidateFileName = bestName,
                originalFileName = sourceFile.name,
                isExtractedFromArchive = true,
                archiveType = "RAR"
            )
        }
    }

    private fun extractFromGzipFileToDisk(sourceFile: File, outputDir: File): ExtractedRomFileResult? {
        val candidateName = if (sourceFile.name.endsWith(".gz", ignoreCase = true)) {
            sourceFile.name.substringBeforeLast(".gz", sourceFile.name.substringBeforeLast('.'))
        } else {
            sourceFile.name
        }
        val outputFile = File(outputDir, candidateName)
        GZIPInputStream(FileInputStream(sourceFile)).use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }
        return ExtractedRomFileResult(
            file = outputFile,
            candidateFileName = candidateName,
            originalFileName = sourceFile.name,
            isExtractedFromArchive = true,
            archiveType = "GZIP"
        )
    }

    /**
     * Inspects incoming file from disk. If it is a ZIP or RAR archive, extracts the primary ROM file.
     */
    fun extractCandidateRom(file: File): ExtractedRomResult {
        val tempDir = File(file.parentFile ?: File("."), "rom_extract_${System.currentTimeMillis()}").also { it.mkdirs() }
        return try {
            val fileRes = extractCandidateRomToDisk(file, tempDir)
            if (fileRes.isExtractedFromArchive && fileRes.file.exists()) {
                val bytes = fileRes.file.readBytes()
                ExtractedRomResult(
                    bytes = bytes,
                    candidateFileName = fileRes.candidateFileName,
                    originalFileName = fileRes.originalFileName,
                    isExtractedFromArchive = true,
                    archiveType = fileRes.archiveType
                )
            } else {
                val rawBytes = file.readBytes()
                extractCandidateRom(rawBytes, file.name)
            }
        } finally {
            tempDir.deleteRecursively()
        }
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
        if (isMultiPartArchive(rawFileName)) {
            throw InvalidRomException(
                "Multi-part split archive detected for '$rawFileName'. " +
                "Please unpack the full set using 7-Zip, ZArchiver, or WinRAR, then import the uncompressed ROM or disc image."
            )
        }

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
            try {
                val zipResult = extractFromZip(rawBytes, rawFileName)
                if (zipResult != null) {
                    if (isArchive(zipResult.bytes, zipResult.candidateFileName) && currentDepth < MAX_RECURSION_DEPTH) {
                        return extractCandidateRom(zipResult.bytes, zipResult.candidateFileName, currentDepth + 1)
                    }
                    return zipResult
                }
            } catch (e: InvalidRomException) {
                throw e
            } catch (e: Exception) {
                // Ignore fallback
            }
        }

        // 2. Try RAR extraction
        if (isRar(rawBytes, lowerName)) {
            if (isRar5(rawBytes)) {
                throw InvalidRomException(
                    "RAR 5.0 archive format detected for '$rawFileName'. " +
                    "Pure Java Junrar runtime cannot decompress RAR5 archives. Please unpack this game using 7-Zip, ZArchiver, or WinRAR, or import standard .zip / .7z archives or uncompressed ROM files (.z64, .nds, .gba, .iso, .cue, .chd, .pbp)."
                )
            }
            try {
                val rarResult = extractFromRar(rawBytes, rawFileName)
                if (rarResult != null) {
                    if (isArchive(rarResult.bytes, rarResult.candidateFileName) && currentDepth < MAX_RECURSION_DEPTH) {
                        return extractCandidateRom(rarResult.bytes, rarResult.candidateFileName, currentDepth + 1)
                    }
                    return rarResult
                }
            } catch (e: InvalidRomException) {
                throw e
            } catch (e: com.github.junrar.exception.UnsupportedRarV5Exception) {
                throw InvalidRomException(
                    "RAR 5.0 archive format detected for '$rawFileName'. " +
                    "Pure Java Junrar runtime cannot decompress RAR5 archives. Please unpack this game using 7-Zip, ZArchiver, or WinRAR, or import standard .zip / .7z archives or uncompressed ROM files (.z64, .nds, .gba, .iso, .cue, .chd, .pbp)."
                )
            } catch (e: Exception) {
                throw InvalidRomException("Failed to unpack RAR archive '$rawFileName': ${e.message ?: "corrupted or unsupported format"}. Please unpack with 7-Zip/ZArchiver or import raw ROM.")
            }
        }

        // 3. Try GZIP decompression (e.g. game.nes.gz)
        if (isGzip(rawBytes, lowerName)) {
            try {
                val gzResult = extractFromGzip(rawBytes, rawFileName)
                if (gzResult != null) {
                    if (isArchive(gzResult.bytes, gzResult.candidateFileName) && currentDepth < MAX_RECURSION_DEPTH) {
                        return extractCandidateRom(gzResult.bytes, gzResult.candidateFileName, currentDepth + 1)
                    }
                    return gzResult
                }
            } catch (e: InvalidRomException) {
                throw e
            } catch (e: Exception) {
                // Ignore fallback
            }
        }

        // 4. Try 7Z extraction
        if (is7z(rawBytes, lowerName)) {
            try {
                val sevenZResult = extractFrom7z(rawBytes, rawFileName)
                if (sevenZResult != null) {
                    if (isArchive(sevenZResult.bytes, sevenZResult.candidateFileName) && currentDepth < MAX_RECURSION_DEPTH) {
                        return extractCandidateRom(sevenZResult.bytes, sevenZResult.candidateFileName, currentDepth + 1)
                    }
                    return sevenZResult
                }
            } catch (e: InvalidRomException) {
                throw e
            } catch (e: Exception) {
                // Ignore fallback
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
        return (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) ||
            (bytes.size < 4 && lowerName.endsWith(".zip"))
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

    private fun is7z(bytes: ByteArray, lowerName: String): Boolean {
        return lowerName.endsWith(".7z") ||
            (bytes.size >= 6 &&
                bytes[0] == 0x37.toByte() && bytes[1] == 0x7A.toByte() &&
                bytes[2] == 0xBC.toByte() && bytes[3] == 0xAF.toByte() &&
                bytes[4] == 0x27.toByte() && bytes[5] == 0x1C.toByte())
    }

    /**
     * Extracts the best candidate ROM entry from a ZIP archive.
     */
    private fun extractFromZip(rawBytes: ByteArray, rawFileName: String): ExtractedRomResult? {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        val zis = runCatching { ZipInputStream(ByteArrayInputStream(rawBytes)) }.getOrNull() ?: return null
        zis.use {
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
            if (archive.isEncrypted) {
                throw InvalidRomException("The RAR archive '$rawFileName' is password-protected or encrypted. Password-protected archives cannot be unpacked automatically.")
            }
            var header: FileHeader? = archive.nextFileHeader()
            while (header != null) {
                if (header.isEncrypted) {
                    throw InvalidRomException("The RAR archive entry '${header.fileName}' is password-protected and cannot be extracted.")
                }
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
     * Extracts the best candidate ROM entry from a 7Z archive using Apache Commons Compress.
     */
    private fun extractFrom7z(rawBytes: ByteArray, rawFileName: String): ExtractedRomResult? {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        val channel = SeekableInMemoryByteChannel(rawBytes)
        val sevenZFile = runCatching { SevenZFile(channel) }.getOrNull() ?: return null
        sevenZFile.use { szf ->
            var entry = szf.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val cleanName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    if (!isIgnoredFile(cleanName)) {
                        val size = entry.size
                        if (size in 1..0x40000000L) {
                            val content = ByteArray(size.toInt())
                            var offset = 0
                            while (offset < content.size) {
                                val read = szf.read(content, offset, content.size - offset)
                                if (read < 0) break
                                offset += read
                            }
                            entries.add(Pair(cleanName, content))
                        }
                    }
                }
                entry = szf.nextEntry
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
            archiveType = "7Z"
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
