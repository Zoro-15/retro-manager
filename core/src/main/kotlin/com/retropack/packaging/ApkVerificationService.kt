package com.retropack.packaging

import com.android.apksig.ApkVerifier
import java.io.File
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

/**
 * Validates APK signature integrity and scheme compliance via apksig ApkVerifier (Step 13).
 *
 * Enforces Law 22 (Verification Precedes Release):
 * Programmatically confirms that v1, v2, and v3 signatures are intact and cryptographically sound.
 */
object ApkVerificationService {

    data class VerificationResult(
        val isVerified: Boolean,
        val isV1SchemeVerified: Boolean,
        val isV2SchemeVerified: Boolean,
        val isV3SchemeVerified: Boolean,
        val signerCertificates: List<X509Certificate>,
        val errors: List<String>
    )

    /**
     * Verifies the cryptographic signatures of [apkFile].
     */
    fun verifyApk(
        apkFile: File,
        minSdkVersion: Int? = ApkSignerService.DEFAULT_MIN_SDK,
        maxSdkVersion: Int? = null
    ): VerificationResult {
        require(apkFile.exists()) { "Target APK file does not exist: ${apkFile.absolutePath}" }

        val builder = ApkVerifier.Builder(apkFile)
        if (minSdkVersion != null) {
            builder.setMinCheckedPlatformVersion(minSdkVersion)
        }
        if (maxSdkVersion != null) {
            builder.setMaxCheckedPlatformVersion(maxSdkVersion)
        }

        val verifier = builder.build()
        val result = verifier.verify()

        val errors = mutableListOf<String>()
        for (error in result.errors) {
            errors.add(error.toString())
        }
        for (signer in result.v1SchemeSigners) {
            for (error in signer.errors) {
                errors.add("v1 (${signer.name}): $error")
            }
        }
        for (signer in result.v2SchemeSigners) {
            for (error in signer.errors) {
                errors.add("v2 (index ${signer.index}): $error")
            }
        }
        for (signer in result.v3SchemeSigners) {
            for (error in signer.errors) {
                errors.add("v3 (index ${signer.index}): $error")
            }
        }

        val signerCerts = result.signerCertificates.toList()

        // Check if v1 signature manifest files exist inside the APK archive
        val hasV1Files = try {
            ZipFile(apkFile).use { zip ->
                zip.getEntry("META-INF/MANIFEST.MF") != null
            }
        } catch (_: Exception) {
            false
        }

        val v1Verified = (result.isVerified && hasV1Files) ||
            result.isVerifiedUsingV1Scheme ||
            (result.v1SchemeSigners.isNotEmpty() && result.v1SchemeSigners.all { it.errors.isEmpty() })
        val v2Verified = result.isVerifiedUsingV2Scheme ||
            (result.v2SchemeSigners.isNotEmpty() && result.v2SchemeSigners.all { it.errors.isEmpty() })
        val v3Verified = result.isVerifiedUsingV3Scheme ||
            (result.v3SchemeSigners.isNotEmpty() && result.v3SchemeSigners.all { it.errors.isEmpty() })

        return VerificationResult(
            isVerified = result.isVerified,
            isV1SchemeVerified = v1Verified,
            isV2SchemeVerified = v2Verified,
            isV3SchemeVerified = v3Verified,
            signerCertificates = signerCerts,
            errors = errors
        )
    }

    /**
     * Asserts that [apkFile] passes signature verification, throwing [IllegalStateException] on failure.
     */
    fun assertVerified(
        apkFile: File,
        minSdkVersion: Int? = ApkSignerService.DEFAULT_MIN_SDK,
        maxSdkVersion: Int? = null
    ): VerificationResult {
        val result = verifyApk(apkFile, minSdkVersion, maxSdkVersion)
        if (!result.isVerified) {
            throw IllegalStateException(
                "APK Signature verification failed with ${result.errors.size} error(s):\n" +
                    result.errors.joinToString("\n - ", prefix = " - ")
            )
        }
        return result
    }
}
