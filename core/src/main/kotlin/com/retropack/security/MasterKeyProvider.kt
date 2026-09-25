package com.retropack.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Strategy interface providing master key encryption for software code-signing keys at rest.
 */
interface MasterKeyProvider {
    val providerName: String
    fun encrypt(plainBytes: ByteArray): EncryptedPayload
    fun decrypt(payload: EncryptedPayload): ByteArray
}

/**
 * Standard AES-256-GCM master key provider using JCA.
 * Can be initialized with a pre-existing key or generates a fresh 256-bit random key.
 */
class AesGcmMasterKeyProvider(
    private val secretKey: SecretKey
) : MasterKeyProvider {

    override val providerName: String = "AesGcmMasterKeyProvider"
    private val secureRandom = SecureRandom()

    constructor(rawKeyBytes: ByteArray) : this(SecretKeySpec(rawKeyBytes, "AES"))

    companion object {
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128

        fun generateRandom(): AesGcmMasterKeyProvider {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256, SecureRandom())
            return AesGcmMasterKeyProvider(keyGen.generateKey())
        }
    }

    override fun encrypt(plainBytes: ByteArray): EncryptedPayload {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(EncryptedPayload.ALGORITHM_AES_GCM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val ciphertext = cipher.doFinal(plainBytes)
        return EncryptedPayload(iv = iv, ciphertext = ciphertext)
    }

    override fun decrypt(payload: EncryptedPayload): ByteArray {
        val cipher = Cipher.getInstance(payload.algorithm)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        return cipher.doFinal(payload.ciphertext)
    }
}
