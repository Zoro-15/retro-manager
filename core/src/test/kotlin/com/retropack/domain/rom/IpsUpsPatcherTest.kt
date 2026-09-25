package com.retropack.domain.rom

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

class IpsUpsPatcherTest {

    @Test
    fun `test IPS literal and RLE record application`() {
        val source = ByteArray(0x400) { 0x00 }
        val patch = byteArrayOf(
            'P'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte(), 'C'.code.toByte(), 'H'.code.toByte(),
            // Literal record at 0x0200, length 3: [1, 2, 3]
            0x00, 0x02, 0x00, 0x00, 0x03, 1, 2, 3,
            // RLE record at 0x0300, length 0, rleLength 4, value 0x7F
            0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x04, 0x7F,
            // EOF footer
            'E'.code.toByte(), 'O'.code.toByte(), 'F'.code.toByte()
        )

        val result = IpsUpsPatcher.apply(source, patch)
        assertEquals(PatchFormat.IPS, result.format)
        assertArrayEquals(byteArrayOf(1, 2, 3), result.outputBytes.copyOfRange(0x0200, 0x0203))
        assertArrayEquals(ByteArray(4) { 0x7F }, result.outputBytes.copyOfRange(0x0300, 0x0304))
    }

    @Test
    fun `test IPS truncation record`() {
        val source = ByteArray(0x400) { 0xFF.toByte() }
        val truncateSize = 0x0150
        val patch = byteArrayOf(
            'P'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte(), 'C'.code.toByte(), 'H'.code.toByte(),
            'E'.code.toByte(), 'O'.code.toByte(), 'F'.code.toByte(),
            (truncateSize ushr 16).toByte(),
            (truncateSize ushr 8).toByte(),
            truncateSize.toByte()
        )

        val result = IpsUpsPatcher.apply(source, patch)
        assertEquals(truncateSize, result.outputBytes.size)
    }

    @Test
    fun `test UPS inspection and XOR patch application`() {
        val source = ByteArray(0x400) { it.toByte() }
        val target = source.copyOf().also {
            it[0x00] = 0x42
            it[0x50] = 0x99.toByte()
        }

        val patch = createUpsPatch(source, target)
        val descriptor = IpsUpsPatcher.inspect(patch)

        assertEquals(PatchFormat.UPS, descriptor.format)
        assertEquals(source.size.toLong(), descriptor.sourceSizeBytes)
        assertEquals(target.size.toLong(), descriptor.targetSizeBytes)
        assertTrue(descriptor.patchIntegrityValid)

        val result = IpsUpsPatcher.apply(source, patch)
        assertEquals(PatchFormat.UPS, result.format)
        assertArrayEquals(target, result.outputBytes)
    }

    @Test
    fun `test streaming patch application produces identical result to in-memory application`() {
        val source = ByteArray(0x400) { it.toByte() }
        val target = source.copyOf().also { it[0x10] = 0xAA.toByte() }
        val patch = createUpsPatch(source, target)

        val outStream = ByteArrayOutputStream()
        val streamResult = IpsUpsPatcher.apply(
            ByteArrayInputStream(source),
            ByteArrayInputStream(patch),
            outStream
        )

        assertEquals(target.size.toLong(), streamResult.outputSizeBytes)
        assertArrayEquals(target, outStream.toByteArray())
    }

    @Test
    fun `test rejection of unknown patch signature`() {
        val invalidPatch = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        assertThrows<InvalidPatchException> {
            IpsUpsPatcher.apply(ByteArray(0x100), invalidPatch)
        }
    }

    @Test
    fun `test UPS patch rejects mismatched base ROM CRC`() {
        val source = ByteArray(0x400) { it.toByte() }
        val wrongSource = ByteArray(0x400) { (it + 1).toByte() }
        val target = source.copyOf().also { it[0] = 0x77 }
        val patch = createUpsPatch(source, target)

        assertThrows<InvalidPatchException> {
            IpsUpsPatcher.apply(wrongSource, patch)
        }
    }

    private fun createUpsPatch(source: ByteArray, target: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply {
            write("UPS1".toByteArray(Charsets.US_ASCII))
            writeVariable(source.size.toLong())
            writeVariable(target.size.toLong())

            var offset = 0
            var currentOutputOffset = 0
            while (offset < target.size) {
                if (source[offset] != target[offset]) {
                    val relative = (offset - currentOutputOffset).toLong()
                    writeVariable(relative)
                    currentOutputOffset = offset
                    while (offset < target.size && source[offset] != target[offset]) {
                        write(source[offset].toInt() xor target[offset].toInt())
                        offset++
                        currentOutputOffset++
                    }
                    write(0) // terminator
                    currentOutputOffset++
                } else {
                    offset++
                }
            }

            writeLittle32(crc32(source))
            writeLittle32(crc32(target))
        }

        val patchBytesBeforeCrc = body.toByteArray()
        val patchCrc = crc32(patchBytesBeforeCrc)
        return patchBytesBeforeCrc + little32(patchCrc)
    }

    private fun ByteArrayOutputStream.writeVariable(initial: Long) {
        var value = initial
        while (true) {
            val current = (value and 0x7F).toInt()
            value = value ushr 7
            if (value == 0L) {
                write(current or 0x80)
                return
            }
            write(current)
            value -= 1
        }
    }

    private fun ByteArrayOutputStream.writeLittle32(value: Long) = write(little32(value))

    private fun little32(value: Long) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte()
    )

    private fun crc32(bytes: ByteArray) = CRC32().apply { update(bytes) }.value
}
