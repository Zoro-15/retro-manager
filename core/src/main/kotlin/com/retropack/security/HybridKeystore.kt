package com.retropack.security

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.UUID

enum class KeyType {
    RSA_2048,
    EC_P256
}

/**
 * High-level coordinator for the Hybrid Keystore System.
 * Manages key generation, encryption at rest via MasterKeyProvider,
 * and standard .p12 / .jks backup import/export.
 */
object HybridKeystore {

    /**
     * Generates a fresh software code-signing identity (RSA-2048 or EC P-256)
     * accompanied by a 30-year self-signed X.509 certificate.
     */
    fun generateIdentity(
        alias: String = "retropack_default",
        keyType: KeyType = KeyType.RSA_2048
    ): SigningIdentity {
        val (keyPair, sigAlg) = when (keyType) {
            KeyType.RSA_2048 -> {
                val kpg = KeyPairGenerator.getInstance("RSA")
                kpg.initialize(2048, SecureRandom())
                Pair(kpg.generateKeyPair(), "SHA256withRSA")
            }
            KeyType.EC_P256 -> {
                val kpg = KeyPairGenerator.getInstance("EC")
                kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
                Pair(kpg.generateKeyPair(), "SHA256withECDSA")
            }
        }

        val certificate = CertificateGenerator.generateSelfSignedCertificate(
            keyPair = keyPair,
            signatureAlgorithm = sigAlg,
            distinguishedName = "CN=RetroPack Game Signer, OU=RetroPack, O=Self-Signed"
        )

        return SigningIdentity(
            alias = alias,
            privateKey = keyPair.private,
            certificate = certificate,
            certificateChain = listOf(certificate)
        )
    }

    /**
     * Encrypts and persists a SigningIdentity to disk using the provided MasterKeyProvider.
     * Uses atomic temporary file write and replacement.
     */
    fun saveEncrypted(
        identity: SigningIdentity,
        targetFile: File,
        masterProvider: MasterKeyProvider
    ) {
        val serializedBytes = identity.toSerializedBytes()
        val encryptedPayload = masterProvider.encrypt(serializedBytes)
        val payloadBytes = encryptedPayload.toBytes()

        val parentDir = targetFile.parentFile ?: targetFile.absoluteFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        val tmpFile = if (parentDir != null) {
            File(parentDir, "${targetFile.name}.${UUID.randomUUID()}.tmp")
        } else {
            File("${targetFile.name}.${UUID.randomUUID()}.tmp")
        }
        tmpFile.outputStream().use { fos ->
            fos.write(payloadBytes)
            fos.flush()
            fos.fd.sync()
        }

        if (!tmpFile.renameTo(targetFile)) {
            // Fallback for cross-filesystem link or atomic failure
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
    }

    /**
     * Loads and decrypts an identity from app-private storage.
     */
    fun loadEncrypted(
        sourceFile: File,
        masterProvider: MasterKeyProvider
    ): SigningIdentity {
        require(sourceFile.exists()) { "Encrypted key file does not exist: ${sourceFile.absolutePath}" }
        val payloadBytes = sourceFile.readBytes()
        val payload = EncryptedPayload.fromBytes(payloadBytes)
        val decryptedBytes = masterProvider.decrypt(payload)
        return SigningIdentity.fromSerializedBytes(decryptedBytes)
    }

    /**
     * Exports a SigningIdentity to a standard PKCS#12 (.p12) password-protected keystore stream.
     */
    fun exportToPkcs12(
        identity: SigningIdentity,
        password: CharArray,
        outStream: OutputStream
    ) {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, password)
        ks.setKeyEntry(
            identity.alias,
            identity.privateKey,
            password,
            identity.certificateChain.toTypedArray()
        )
        ks.store(outStream, password)
    }

    /**
     * Imports a SigningIdentity from a PKCS#12 (.p12) keystore stream.
     */
    fun importFromPkcs12(
        inStream: InputStream,
        password: CharArray,
        targetAlias: String? = null
    ): SigningIdentity {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(inStream, password)

        val resolvedAlias = targetAlias ?: run {
            val aliases = ks.aliases().toList()
            require(aliases.isNotEmpty()) { "No key aliases found in PKCS12 keystore" }
            aliases.first()
        }

        val key = ks.getKey(resolvedAlias, password) as java.security.PrivateKey
        val rawChain = ks.getCertificateChain(resolvedAlias)
        require(rawChain != null && rawChain.isNotEmpty()) { "Certificate chain missing for alias: $resolvedAlias" }

        val certChain = rawChain.map { it as java.security.cert.X509Certificate }
        return SigningIdentity(
            alias = resolvedAlias,
            privateKey = key,
            certificate = certChain.first(),
            certificateChain = certChain
        )
    }
}
