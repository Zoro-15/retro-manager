package com.retropack.packaging

import com.android.apksig.ApkSigner
import java.io.File
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Cryptographic APK signing service applying v1, v2, and v3 schemes simultaneously (Step 12).
 *
 * Consumes private key bytes and certificate chains from HybridKeystore,
 * producing Android OS compatible multi-scheme signatures in a single deterministic pass.
 */
object ApkSignerService {

    const val DEFAULT_MIN_SDK = 24
    const val CREATED_BY = "RetroPack"

    /**
     * Signs [inputApk] and writes the signed package to [outputApk].
     *
     * Enables APK Signature Scheme v1 (JAR signing), v2 (APK Signing Block),
     * and v3 (Key rotation support) simultaneously.
     */
    fun signApk(
        inputApk: File,
        outputApk: File,
        privateKey: PrivateKey,
        certificateChain: List<X509Certificate>,
        minSdkVersion: Int = DEFAULT_MIN_SDK,
        v1SigningEnabled: Boolean = true,
        v2SigningEnabled: Boolean = true,
        v3SigningEnabled: Boolean = true,
        signerName: String = "RetroPackSigner"
    ) {
        require(inputApk.exists()) { "Input APK file does not exist: ${inputApk.absolutePath}" }
        require(certificateChain.isNotEmpty()) { "Certificate chain cannot be empty" }

        outputApk.parentFile?.mkdirs()

        val signerConfig = ApkSigner.SignerConfig.Builder(
            signerName,
            privateKey,
            certificateChain
        ).build()

        val builder = ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setMinSdkVersion(minSdkVersion)
            .setV1SigningEnabled(v1SigningEnabled)
            .setV2SigningEnabled(v2SigningEnabled)
            .setV3SigningEnabled(v3SigningEnabled)
            .setCreatedBy(CREATED_BY)

        val signer = builder.build()
        signer.sign()
    }
}
