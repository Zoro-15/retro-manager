package com.retropack.security

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date

/**
 * Generates standards-compliant self-signed X.509 code-signing certificates
 * with 30-year validity using BouncyCastle.
 */
object CertificateGenerator {

    private const val DEFAULT_VALIDITY_YEARS = 30
    private const val DEFAULT_DN = "CN=RetroPack Game Signer, OU=RetroPack, O=Self-Signed"

    fun generateSelfSignedCertificate(
        keyPair: KeyPair,
        signatureAlgorithm: String,
        distinguishedName: String = DEFAULT_DN,
        validityYears: Int = DEFAULT_VALIDITY_YEARS
    ): X509Certificate {
        val subject = X500Name(distinguishedName)
        val serialNumber = BigInteger(159, SecureRandom())

        val now = Date()
        val calendar = Calendar.getInstance()
        calendar.time = now
        calendar.add(Calendar.YEAR, validityYears)
        val notAfter = calendar.time

        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serialNumber,
            now,
            notAfter,
            subject,
            keyPair.public
        )

        // Code-signing certificate extensions
        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            BasicConstraints(false)
        )
        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature)
        )
        certBuilder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(KeyPurposeId.id_kp_codeSigning)
        )

        val signer = JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.private)
        val certHolder = certBuilder.build(signer)

        return JcaX509CertificateConverter().getCertificate(certHolder)
    }
}
