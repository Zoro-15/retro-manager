package com.retropack.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Represents an in-memory code-signing identity holding software private key bytes
 * and its associated X.509 certificate chain.
 */
data class SigningIdentity(
    val alias: String,
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
    val certificateChain: List<X509Certificate> = listOf(certificate)
) {
    init {
        // Enforce fundamental invariant: private key bytes must be accessible for apksig v1 JAR signing
        requireNotNull(privateKey.encoded) {
            "Private key encoded bytes cannot be null. Hardware non-exportable keys must not be used for SigningIdentity."
        }
    }

    val keyAlgorithm: String
        get() = privateKey.algorithm

    /**
     * Serializes the identity into raw bytes for encryption at rest:
     * [Alias length][Alias UTF][Algorithm UTF][Key bytes length][Key bytes][Cert count][For each cert: length + bytes]
     */
    fun toSerializedBytes(): ByteArray {
        val byteStream = ByteArrayOutputStream()
        DataOutputStream(byteStream).use { dos ->
            dos.writeUTF(alias)
            dos.writeUTF(keyAlgorithm)

            val keyBytes = privateKey.encoded
            dos.writeInt(keyBytes.size)
            dos.write(keyBytes)

            dos.writeInt(certificateChain.size)
            for (cert in certificateChain) {
                val certBytes = cert.encoded
                dos.writeInt(certBytes.size)
                dos.write(certBytes)
            }
            dos.flush()
        }
        return byteStream.toByteArray()
    }

    companion object {
        // Corrupt inputs must be rejected before allocating (issue #18):
        // PKCS8 RSA-2048 is ~1.2KB and X.509 certs are ~1KB; caps are generous.
        const val MAX_KEY_BYTES = 64 * 1024
        const val MAX_CERT_BYTES = 64 * 1024
        const val MAX_CERT_CHAIN = 10

        fun fromSerializedBytes(bytes: ByteArray): SigningIdentity {
            DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
                val alias = dis.readUTF()
                val keyAlgorithm = dis.readUTF()

                val keySize = dis.readInt()
                require(keySize in 1..MAX_KEY_BYTES) { "Invalid key size: $keySize" }
                val keyBytes = ByteArray(keySize)
                dis.readFully(keyBytes)

                val keyFactory = KeyFactory.getInstance(keyAlgorithm)
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(keyBytes))

                val certCount = dis.readInt()
                require(certCount in 1..MAX_CERT_CHAIN) { "Invalid certificate count: $certCount" }
                val certFactory = CertificateFactory.getInstance("X.509")
                val certChain = mutableListOf<X509Certificate>()

                for (i in 0 until certCount) {
                    val certSize = dis.readInt()
                    require(certSize in 1..MAX_CERT_BYTES) { "Invalid certificate size: $certSize" }
                    val certBytes = ByteArray(certSize)
                    dis.readFully(certBytes)
                    val cert = certFactory.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
                    certChain.add(cert)
                }

                require(certChain.isNotEmpty()) { "Certificate chain cannot be empty" }
                return SigningIdentity(
                    alias = alias,
                    privateKey = privateKey,
                    certificate = certChain.first(),
                    certificateChain = certChain
                )
            }
        }
    }
}
