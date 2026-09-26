package com.retropack.domain.rom

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32

enum class PatchFormat {
    IPS,
    UPS,
    BPS
}

data class PatchDescriptor(
    val format: PatchFormat,
    val sourceSizeBytes: Long?,
    val targetSizeBytes: Long?,
    val patchIntegrityValid: Boolean
)

data class PatchResult(
    val format: PatchFormat,
    val outputBytes: ByteArray
)

data class StreamPatchResult(
    val outputSizeBytes: Long
)

/**
 * High-performance binary patch applicator supporting IPS, UPS, and BPS formats.
 * Supports both fast in-memory patching and on-the-fly streaming transformation
 * without excessive memory footprint.
 */
object IpsUpsPatcher {
    const val MAX_SOURCE_SIZE_BYTES: Int = 64 * 1024 * 1024 // 64 MiB
    const val MAX_PATCH_SIZE_BYTES: Int = 32 * 1024 * 1024  // 32 MiB
    const val MAX_OUTPUT_SIZE_BYTES: Int = 64 * 1024 * 1024 // 64 MiB
    private const val FOOTER_SIZE = 12


    fun detectFormat(patch: ByteArray): PatchFormat = when {
        patch.startsWithAscii("PATCH") -> PatchFormat.IPS
        patch.startsWithAscii("UPS1") -> PatchFormat.UPS
        patch.startsWithAscii("BPS1") -> PatchFormat.BPS
        else -> throw InvalidPatchException("Unsupported patch format. Expected IPS, UPS, or BPS header.")
    }

    fun inspect(patch: ByteArray): PatchDescriptor {
        if (patch.size > MAX_PATCH_SIZE_BYTES) {
            throw InvalidPatchException("Patch exceeds the 32 MiB safety ceiling.")
        }
        val format = detectFormat(patch)

        return when (format) {
            PatchFormat.IPS -> PatchDescriptor(
                format = format,
                sourceSizeBytes = null,
                targetSizeBytes = null,
                patchIntegrityValid = true
            )
            PatchFormat.UPS, PatchFormat.BPS -> {
                if (patch.size < 4 + FOOTER_SIZE) throw InvalidPatchException("Patch is truncated.")
                val patchCrcValid = runCatching {
                    validatePatchCrc(patch)
                    true
                }.getOrDefault(false)

                val cursor = Cursor(patch, 4, patch.size - FOOTER_SIZE)
                val sourceSize = cursor.readVariableInteger()
                val targetSize = cursor.readVariableInteger()
                if (format == PatchFormat.BPS) {
                    val metadataSize = cursor.readVariableInteger().toBoundedInt("BPS metadata size")
                    if (metadataSize > cursor.remaining) throw InvalidPatchException("BPS metadata is truncated.")
                }
                PatchDescriptor(
                    format = format,
                    sourceSizeBytes = sourceSize,
                    targetSizeBytes = targetSize,
                    patchIntegrityValid = patchCrcValid
                )
            }
        }
    }

    /**
     * Applies the patch to the source ROM in memory.
     */
    fun apply(source: ByteArray, patch: ByteArray): PatchResult {
        requireBounded(source, patch)
        val format = detectFormat(patch)
        val output = when (format) {
            PatchFormat.IPS -> applyIps(source, patch)
            PatchFormat.UPS -> applyUps(source, patch)
            PatchFormat.BPS -> applyBps(source, patch)
        }
        if (output.size > MAX_OUTPUT_SIZE_BYTES) {
            throw InvalidPatchException("Patched output size (${output.size} bytes) exceeds the 64 MiB safety ceiling.")
        }

        return PatchResult(
            format = format,
            outputBytes = output
        )
    }

    /**
     * Applies the patch, buffering source and patch within their safety
     * ceilings (64/32 MiB). NOTE: despite the stream signature this is a
     * *buffered* transform, not bounded-memory streaming — callers handling
     * large ROMs should prefer file-backed staging (issue #17).
     */
    fun apply(source: InputStream, patch: InputStream, output: OutputStream): StreamPatchResult {
        val patchBytes = patch.readBytesLimited(MAX_PATCH_SIZE_BYTES)
        val sourceBytes = source.readBytesLimited(MAX_SOURCE_SIZE_BYTES)
        val result = apply(sourceBytes, patchBytes)
        output.write(result.outputBytes)
        output.flush()

        return StreamPatchResult(
            outputSizeBytes = result.outputBytes.size.toLong()
        )
    }

    private fun applyIps(source: ByteArray, patch: ByteArray): ByteArray {
        val cursor = Cursor(patch, 5)
        var output = source.copyOf()
        var sawFooter = false

        while (cursor.remaining >= 3) {
            val offset = cursor.readUnsigned24BigEndian()
            if (offset == 0x454F46) { // "EOF"
                sawFooter = true
                break
            }
            val size = cursor.readUnsigned16BigEndian()
            if (size == 0) {
                // RLE record
                val runLength = cursor.readUnsigned16BigEndian()
                if (runLength == 0) throw InvalidPatchException("IPS RLE record has zero length.")
                val value = cursor.readUnsignedByte().toByte()
                output = ensureOutputCapacity(output, checkedEnd(offset, runLength))
                output.fill(value, offset, offset + runLength)
            } else {
                // Literal record
                val end = checkedEnd(offset, size)
                output = ensureOutputCapacity(output, end)
                cursor.readBytes(size).copyInto(output, offset)
            }
        }

        if (!sawFooter) throw InvalidPatchException("IPS footer ('EOF') is missing.")

        when (cursor.remaining) {
            0 -> Unit
            3 -> {
                val truncateSize = cursor.readUnsigned24BigEndian()
                if (truncateSize > MAX_OUTPUT_SIZE_BYTES) {
                    throw InvalidPatchException("IPS truncate size exceeds the 64 MiB safety ceiling.")
                }
                output = output.copyOf(truncateSize)
            }
            else -> throw InvalidPatchException("IPS patch has unexpected trailing data.")
        }

        return output
    }

    private fun applyUps(source: ByteArray, patch: ByteArray): ByteArray {
        if (patch.size < 4 + FOOTER_SIZE) throw InvalidPatchException("UPS patch is truncated.")
        validatePatchCrc(patch)

        val cursor = Cursor(patch, 4, patch.size - FOOTER_SIZE)
        val sourceSize = cursor.readVariableInteger().toBoundedInt("UPS source size")
        val targetSize = cursor.readVariableInteger().toBoundedInt("UPS target size")

        if (sourceSize != source.size) {
            throw InvalidPatchException("UPS patch expects a $sourceSize-byte base ROM, but the selected ROM is ${source.size} bytes.")
        }
        if (targetSize > MAX_OUTPUT_SIZE_BYTES) {
            throw InvalidPatchException("UPS target size exceeds the 64 MiB safety ceiling.")
        }

        val output = source.copyOf(targetSize)
        var outputOffset = 0

        while (cursor.hasRemaining()) {
            val relative = cursor.readVariableInteger().toBoundedInt("UPS relative offset")
            outputOffset = checkedEnd(outputOffset, relative)
            if (outputOffset >= targetSize) throw InvalidPatchException("UPS record starts outside the target ROM.")

            // Bulk XOR run: one scan to the zero terminator, then a plain
            // indexed loop (no per-byte method call / bounds check).
            val diff = cursor.readXorDiff()
            if (diff.size > targetSize - outputOffset) {
                throw InvalidPatchException("UPS record writes outside the target ROM.")
            }
            for (i in diff.indices) {
                val sourceByte = if (outputOffset + i < source.size) source[outputOffset + i].toInt() and 0xFF else 0
                output[outputOffset + i] = (sourceByte xor (diff[i].toInt() and 0xFF)).toByte()
            }
            outputOffset += diff.size
            // Terminator skip (matches the original per-byte advance).
            outputOffset = checkedEnd(outputOffset, 1)
        }

        validateFooterChecksums(source, output, patch)
        return output
    }

    private fun applyBps(source: ByteArray, patch: ByteArray): ByteArray {
        if (patch.size < 4 + FOOTER_SIZE) throw InvalidPatchException("BPS patch is truncated.")
        validatePatchCrc(patch)

        val cursor = Cursor(patch, 4, patch.size - FOOTER_SIZE)
        val sourceSize = cursor.readVariableInteger().toBoundedInt("BPS source size")
        val targetSize = cursor.readVariableInteger().toBoundedInt("BPS target size")
        val metadataSize = cursor.readVariableInteger().toBoundedInt("BPS metadata size")

        if (sourceSize != source.size) {
            throw InvalidPatchException("BPS patch expects a $sourceSize-byte base ROM, but the selected ROM is ${source.size} bytes.")
        }
        if (targetSize > MAX_OUTPUT_SIZE_BYTES) throw InvalidPatchException("BPS target is too large.")
        if (metadataSize > cursor.remaining) throw InvalidPatchException("BPS metadata is truncated.")
        cursor.skip(metadataSize)

        val output = ByteArray(targetSize)
        var outputOffset = 0
        var sourceRelativeOffset = 0
        var targetRelativeOffset = 0

        while (cursor.hasRemaining()) {
            val actionAndLength = cursor.readVariableInteger()
            val action = (actionAndLength and 3L).toInt()
            val lengthLong = (actionAndLength ushr 2) + 1L
            if (lengthLong > Int.MAX_VALUE) throw InvalidPatchException("BPS action length is too large.")
            val length = lengthLong.toInt()
            if (length > targetSize - outputOffset) throw InvalidPatchException("BPS action writes outside the target ROM.")

            when (action) {
                0 -> { // SourceRead
                    if (length > source.size - outputOffset) throw InvalidPatchException("BPS SourceRead exceeds the base ROM.")
                    source.copyInto(output, outputOffset, outputOffset, outputOffset + length)
                    outputOffset += length
                }
                1 -> { // TargetRead
                    cursor.readBytes(length).copyInto(output, outputOffset)
                    outputOffset += length
                }
                2 -> { // SourceCopy
                    sourceRelativeOffset = checkedRelativeOffset(
                        sourceRelativeOffset,
                        decodeSignedOffset(cursor.readVariableInteger()),
                        "BPS source copy"
                    )
                    if (length > source.size - sourceRelativeOffset) throw InvalidPatchException("BPS SourceCopy exceeds base ROM.")
                    source.copyInto(output, outputOffset, sourceRelativeOffset, sourceRelativeOffset + length)
                    sourceRelativeOffset += length
                    outputOffset += length
                }
                3 -> { // TargetCopy
                    targetRelativeOffset = checkedRelativeOffset(
                        targetRelativeOffset,
                        decodeSignedOffset(cursor.readVariableInteger()),
                        "BPS target copy"
                    )
                    if (length <= outputOffset - targetRelativeOffset) {
                        // Source region is fully written: bulk copy. Otherwise
                        // (self-overlapping repeat) fall through to the
                        // byte loop, which has memmove-forward semantics.
                        output.copyInto(output, outputOffset, targetRelativeOffset, targetRelativeOffset + length)
                        targetRelativeOffset += length
                        outputOffset += length
                    } else {
                        repeat(length) {
                            if (targetRelativeOffset !in 0 until outputOffset) {
                                throw InvalidPatchException("BPS TargetCopy references unwritten output.")
                            }
                            output[outputOffset++] = output[targetRelativeOffset++]
                        }
                    }
                }
            }
        }

        if (outputOffset != targetSize) {
            throw InvalidPatchException("BPS produced $outputOffset bytes instead of declared $targetSize.")
        }
        validateFooterChecksums(source, output, patch)
        return output
    }

    private fun validateFooterChecksums(source: ByteArray, output: ByteArray, patch: ByteArray) {
        val sourceCrc = patch.readUnsigned32LittleEndian(patch.size - 12)
        val targetCrc = patch.readUnsigned32LittleEndian(patch.size - 8)
        if (crc32(source) != sourceCrc) {
            throw InvalidPatchException("Patch source CRC does not match the selected base ROM.")
        }
        if (crc32(output) != targetCrc) {
            throw InvalidPatchException("Patched output CRC verification failed.")
        }
    }

    private fun validatePatchCrc(patch: ByteArray) {
        val expected = patch.readUnsigned32LittleEndian(patch.size - 4)
        val actual = crc32(patch, 0, patch.size - 4)
        if (actual != expected) throw InvalidPatchException("Patch file CRC verification failed.")
    }

    private fun requireBounded(source: ByteArray, patch: ByteArray) {
        if (source.size < 0xC0) throw InvalidPatchException("Base ROM is too small to contain a valid header.")
        if (source.size > MAX_SOURCE_SIZE_BYTES) throw InvalidPatchException("Base ROM exceeds the 64 MiB safety ceiling.")
        if (patch.size > MAX_PATCH_SIZE_BYTES) throw InvalidPatchException("Patch exceeds the 32 MiB safety ceiling.")
    }

    private fun ensureOutputCapacity(current: ByteArray, required: Int): ByteArray {
        if (required > MAX_OUTPUT_SIZE_BYTES) throw InvalidPatchException("Patched output exceeds the 64 MiB safety ceiling.")
        return if (required <= current.size) current else current.copyOf(required)
    }

    private fun checkedEnd(offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > MAX_OUTPUT_SIZE_BYTES - length) {
            throw InvalidPatchException("Patch offset or length is outside supported range.")
        }
        return offset + length
    }

    private fun checkedRelativeOffset(current: Int, delta: Long, label: String): Int {
        val result = current.toLong() + delta
        if (result !in 0..MAX_OUTPUT_SIZE_BYTES.toLong()) throw InvalidPatchException("$label offset is outside supported range.")
        return result.toInt()
    }

    private fun decodeSignedOffset(encoded: Long): Long {
        val magnitude = encoded ushr 1
        return if (encoded and 1L == 0L) magnitude else -magnitude
    }

    private fun Long.toBoundedInt(label: String): Int {
        if (this !in 0..Int.MAX_VALUE.toLong()) throw InvalidPatchException("$label is too large.")
        return toInt()
    }

    private fun ByteArray.startsWithAscii(text: String): Boolean {
        val textBytes = text.toByteArray(Charsets.US_ASCII)
        return size >= textBytes.size && textBytes.indices.all { this[it] == textBytes[it] }
    }

    private fun ByteArray.readUnsigned32LittleEndian(offset: Int): Long {
        if (offset < 0 || offset > size - 4) throw InvalidPatchException("Patch checksum footer is truncated.")
        return (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun crc32(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Long = CRC32().run {
        update(bytes, offset, length)
        value
    }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw InvalidPatchException("Input stream exceeds safety ceiling of $limit bytes.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private class Cursor(
        private val bytes: ByteArray,
        start: Int,
        private val endExclusive: Int = bytes.size
    ) {
        private var position = start
        val remaining: Int get() = endExclusive - position

        init {
            if (start !in 0..endExclusive || endExclusive !in 0..bytes.size) {
                throw InvalidPatchException("Patch cursor range is invalid.")
            }
        }

        fun hasRemaining(): Boolean = position < endExclusive

        fun skip(count: Int) {
            requireAvailable(count)
            position += count
        }

        fun readUnsignedByte(): Int {
            requireAvailable(1)
            return bytes[position++].toInt() and 0xFF
        }

        fun readUnsigned16BigEndian(): Int = (readUnsignedByte() shl 8) or readUnsignedByte()

        fun readUnsigned24BigEndian(): Int =
            (readUnsignedByte() shl 16) or (readUnsignedByte() shl 8) or readUnsignedByte()

        fun readBytes(count: Int): ByteArray {
            requireAvailable(count)
            return bytes.copyOfRange(position, position + count).also { position += count }
        }

        /**
         * Reads one UPS XOR difference run up to (and consuming) its zero
         * terminator, returned as a bulk slice for indexed processing.
         */
        fun readXorDiff(): ByteArray {
            var end = position
            while (true) {
                if (end >= endExclusive) throw InvalidPatchException("UPS XOR record is not terminated.")
                if (bytes[end].toInt() and 0xFF == 0) break
                end++
            }
            return bytes.copyOfRange(position, end).also { position = end + 1 }
        }

        fun readVariableInteger(): Long {
            var data = 0L
            var shift = 1L
            repeat(10) {
                val current = readUnsignedByte()
                val low = current and 0x7F
                if (low != 0 && shift > Long.MAX_VALUE / low) throw InvalidPatchException("Patch variable integer overflows.")
                val addition = low * shift
                if (data > Long.MAX_VALUE - addition) throw InvalidPatchException("Patch variable integer overflows.")
                data += addition
                if (current and 0x80 != 0) return data
                if (shift > (Long.MAX_VALUE ushr 7)) throw InvalidPatchException("Patch variable integer overflows.")
                shift = shift shl 7
                if (data > Long.MAX_VALUE - shift) throw InvalidPatchException("Patch variable integer overflows.")
                data += shift
            }
            throw InvalidPatchException("Patch variable integer is too long.")
        }

        private fun requireAvailable(count: Int) {
            if (count < 0 || count > remaining) throw InvalidPatchException("Patch data is truncated.")
        }
    }
}
