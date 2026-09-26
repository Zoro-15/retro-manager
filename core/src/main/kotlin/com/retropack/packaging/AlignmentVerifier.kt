package com.retropack.packaging

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Validates 16 KB (16,384 bytes) page alignment of uncompressed native shared libraries (Step 11).
 *
 * Enforces Constitutional Invariant 3 and Law 22:
 * Every uncompressed `.so` file payload in the APK container must start at a byte offset
 * that is a multiple of 16,384 bytes (`offset % 16384 == 0`), enabling direct zero-copy
 * memory mapping by the Android 15/16 Linux kernel (`extractNativeLibs="false"`).
 */
object AlignmentVerifier {

    const val PAGE_SIZE_16KB = 16384
    private const val LOCAL_FILE_HEADER_MAGIC = 0x04034b50
    private const val CENTRAL_DIR_HEADER_MAGIC = 0x02014b50
    private const val END_OF_CENTRAL_DIR_MAGIC = 0x06054b50

    data class EntryAlignment(
        val dataOffset: Long,
        val isAligned16Kb: Boolean
    )

    data class VerificationReport(
        val isCompliant: Boolean,
        val totalEntries: Int,
        val nativeLibraries: List<EntryAlignment>,
        val violations: List<String>
    )

    /**
     * Inspects [apkFile] and returns a detailed [VerificationReport].
     */
    fun verify(apkFile: File, requiredAlignment: Int = PAGE_SIZE_16KB): VerificationReport {
        require(apkFile.exists()) { "Target APK does not exist: ${apkFile.absolutePath}" }
        require(apkFile.length() > 22) { "File too small to be a valid ZIP/APK archive" }

        val nativeLibs = mutableListOf<EntryAlignment>()
        val violations = mutableListOf<String>()
        var totalEntries = 0

        RandomAccessFile(apkFile, "r").use { raf ->
            val centralDirEntries = findCentralDirectoryEntries(raf)
            totalEntries = centralDirEntries.size

            for (cdEntry in centralDirEntries) {
                if (cdEntry.name.endsWith(".so", ignoreCase = true)) {
                    raf.seek(cdEntry.localHeaderOffset)
                    val localHeaderBytes = ByteArray(30)
                    raf.readFully(localHeaderBytes)

                    val buffer = ByteBuffer.wrap(localHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val magic = buffer.int
                    if (magic != LOCAL_FILE_HEADER_MAGIC) {
                        violations.add(
                            "Invalid local file header magic at offset ${cdEntry.localHeaderOffset} for entry '${cdEntry.name}'"
                        )
                        continue
                    }

                    buffer.position(8)
                    val compressionMethod = buffer.short.toInt() and 0xFFFF
                    buffer.position(26)
                    val fileNameLength = buffer.short.toInt() and 0xFFFF
                    val extraFieldLength = buffer.short.toInt() and 0xFFFF

                    val dataOffset = cdEntry.localHeaderOffset + 30 + fileNameLength + extraFieldLength
                    val isAligned = (dataOffset % requiredAlignment) == 0L

                    val entryAlignment = EntryAlignment(
                        dataOffset = dataOffset,
                        isAligned16Kb = isAligned
                    )
                    nativeLibs.add(entryAlignment)

                    if (compressionMethod != 0) {
                        violations.add(
                            "Native library '${cdEntry.name}' is compressed (method=$compressionMethod). Must be uncompressed (STORED/0) for 16 KB memory-mapping."
                        )
                    }

                    if (!isAligned) {
                        violations.add(
                            "Native library '${cdEntry.name}' payload at offset $dataOffset is NOT aligned to $requiredAlignment bytes (remainder=${dataOffset % requiredAlignment})."
                        )
                    }
                }
            }
        }

        return VerificationReport(
            isCompliant = violations.isEmpty(),
            totalEntries = totalEntries,
            nativeLibraries = nativeLibs,
            violations = violations
        )
    }

    /**
     * Asserts 16 KB page-size compliance, throwing [IllegalStateException] if violations are detected.
     */
    fun assertCompliant(apkFile: File, requiredAlignment: Int = PAGE_SIZE_16KB) {
        val report = verify(apkFile, requiredAlignment)
        if (!report.isCompliant) {
            throw IllegalStateException(
                "16 KB Page Alignment verification failed with ${report.violations.size} violations:\n" +
                    report.violations.joinToString("\n - ", prefix = " - ")
            )
        }
    }

    private data class CentralDirectoryEntry(
        val name: String,
        val localHeaderOffset: Long,
        val compressionMethod: Int
    )

    private fun findCentralDirectoryEntries(raf: RandomAccessFile): List<CentralDirectoryEntry> {
        val fileLength = raf.length()
        // Search back up to 65 KB + 22 bytes for EOCD record
        val maxSearch = minOf(fileLength, 65536L + 22L)
        val searchBuffer = ByteArray(maxSearch.toInt())
        val searchStart = fileLength - maxSearch
        raf.seek(searchStart)
        raf.readFully(searchBuffer)

        var eocdOffsetInSearch = -1
        for (i in (searchBuffer.size - 22) downTo 0) {
            if (searchBuffer[i] == 0x50.toByte() &&
                searchBuffer[i + 1] == 0x4B.toByte() &&
                searchBuffer[i + 2] == 0x05.toByte() &&
                searchBuffer[i + 3] == 0x06.toByte()
            ) {
                eocdOffsetInSearch = i
                break
            }
        }

        if (eocdOffsetInSearch == -1) {
            throw IllegalStateException("End of Central Directory record not found")
        }

        val eocdBuffer = ByteBuffer.wrap(searchBuffer, eocdOffsetInSearch, 22).slice().order(ByteOrder.LITTLE_ENDIAN)
        eocdBuffer.position(10)
        val entryCount = eocdBuffer.short.toInt() and 0xFFFF
        val cdSize = eocdBuffer.int.toLong() and 0xFFFFFFFFL
        val cdOffset = eocdBuffer.int.toLong() and 0xFFFFFFFFL

        val entries = mutableListOf<CentralDirectoryEntry>()
        raf.seek(cdOffset)

        for (i in 0 until entryCount) {
            val cdHeaderBytes = ByteArray(46)
            raf.readFully(cdHeaderBytes)
            val buf = ByteBuffer.wrap(cdHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buf.int
            if (magic != CENTRAL_DIR_HEADER_MAGIC) {
                break
            }

            buf.position(10)
            val compression = buf.short.toInt() and 0xFFFF
            buf.position(28)
            val fileNameLen = buf.short.toInt() and 0xFFFF
            val extraLen = buf.short.toInt() and 0xFFFF
            val commentLen = buf.short.toInt() and 0xFFFF
            buf.position(42)
            val localOffset = buf.int.toLong() and 0xFFFFFFFFL

            val nameBytes = ByteArray(fileNameLen)
            raf.readFully(nameBytes)
            val name = String(nameBytes, Charsets.UTF_8)

            raf.skipBytes(extraLen + commentLen)

            entries.add(
                CentralDirectoryEntry(
                    name = name,
                    localHeaderOffset = localOffset,
                    compressionMethod = compression
                )
            )
        }

        return entries
    }
}
