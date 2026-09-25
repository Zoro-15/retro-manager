package com.retropack.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Serialized container for data encrypted with AES-GCM.
 * Includes a magic header, version, IV/nonce, and authentication tag + ciphertext.
 */
data class EncryptedPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val algorithm: String = ALGORITHM_AES_GCM
) {
    fun toBytes(): ByteArray {
        val byteStream = ByteArrayOutputStream()
        DataOutputStream(byteStream).use { dos ->
            dos.write(MAGIC)
            dos.writeInt(VERSION)
            dos.writeUTF(algorithm)
            dos.writeInt(iv.size)
            dos.write(iv)
            dos.writeInt(ciphertext.size)
            dos.write(ciphertext)
            dos.flush()
        }
        return byteStream.toByteArray()
    }

    companion object {
        val MAGIC = byteArrayOf('R'.code.toByte(), 'P'.code.toByte(), 'E'.code.toByte(), 'K'.code.toByte())
        const val VERSION = 1
        const val ALGORITHM_AES_GCM = "AES/GCM/NoPadding"

        fun fromBytes(bytes: ByteArray): EncryptedPayload {
            DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
                val magic = ByteArray(4)
                dis.readFully(magic)
                require(magic.contentEquals(MAGIC)) { "Invalid magic bytes in encrypted payload" }

                val version = dis.readInt()
                require(version == VERSION) { "Unsupported payload version: $version" }

                val algorithm = dis.readUTF()
                val ivSize = dis.readInt()
                val iv = ByteArray(ivSize)
                dis.readFully(iv)

                val ciphertextSize = dis.readInt()
                val ciphertext = ByteArray(ciphertextSize)
                dis.readFully(ciphertext)

                return EncryptedPayload(iv = iv, ciphertext = ciphertext, algorithm = algorithm)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedPayload
        if (!iv.contentEquals(other.iv)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (algorithm != other.algorithm) return false
        return true
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        return result
    }
}
