package com.retropack.packaging

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.retropack.security.HybridKeystore
import com.retropack.security.KeyType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkSignerAndVerificationTest {

    @field:TempDir
    lateinit var tempDir: File

    private fun createSampleAxml(
        packageName: String = "com.retropack.test",
        activityName: String = "com.retropack.runtime.GameActivity",
        extractNativeLibs: Boolean = false
    ): ByteArray {
        val block = AndroidManifestBlock()
        block.packageName = packageName
        block.versionCode = 1
        block.versionName = "1.0"
        block.setApplicationLabel("Test Application")

        val app = block.getOrCreateApplicationElement()
        val extractAttr = app.getOrCreateAndroidAttribute("extractNativeLibs", 0x010104ea)
        extractAttr.setValueAsBoolean(extractNativeLibs)

        val activity = app.createChildElement("activity")
        val nameAttr = activity.getOrCreateAndroidAttribute("name", 0x01010003)
        nameAttr.valueAsString = activityName

        block.refresh()
        val out = ByteArrayOutputStream()
        block.writeBytes(out)
        return out.toByteArray()
    }

    private fun createMockApk(file: File): File {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(createSampleAxml())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write("dex-bytecode-payload-data".toByteArray())
            zos.closeEntry()
        }
        return file
    }

    @Test
    fun `test single-pass v1 v2 v3 signing and verification with RSA key`() {
        val unsignedApk = createMockApk(File(tempDir, "unsigned_rsa.apk"))
        val signedApk = File(tempDir, "signed_rsa.apk")

        val identity = HybridKeystore.generateIdentity("rsa_signer", KeyType.RSA_2048)

        ApkSignerService.signApk(
            inputApk = unsignedApk,
            outputApk = signedApk,
            privateKey = identity.privateKey,
            certificateChain = listOf(identity.certificate),
            minSdkVersion = 24,
            v1SigningEnabled = true,
            v2SigningEnabled = true,
            v3SigningEnabled = true
        )

        assertTrue(signedApk.exists())
        assertTrue(signedApk.length() > unsignedApk.length())

        val result = ApkVerificationService.verifyApk(signedApk)
        assertTrue(result.isVerified, "Verification failed with errors: ${result.errors}")
        assertTrue(result.isV1SchemeVerified, "Expected v1 scheme verification")
        assertTrue(result.isV2SchemeVerified, "Expected v2 scheme verification")
        assertTrue(result.isV3SchemeVerified, "Expected v3 scheme verification")
        assertEquals(1, result.signerCertificates.size)
        assertEquals(identity.certificate, result.signerCertificates[0])
        assertTrue(result.errors.isEmpty())

        // assertVerified should complete without throwing
        assertDoesNotThrow {
            ApkVerificationService.assertVerified(signedApk)
        }
    }

    @Test
    fun `test single-pass v1 v2 v3 signing and verification with EC P-256 key`() {
        val unsignedApk = createMockApk(File(tempDir, "unsigned_ec.apk"))
        val signedApk = File(tempDir, "signed_ec.apk")

        val identity = HybridKeystore.generateIdentity("ec_signer", KeyType.EC_P256)

        ApkSignerService.signApk(
            inputApk = unsignedApk,
            outputApk = signedApk,
            privateKey = identity.privateKey,
            certificateChain = listOf(identity.certificate),
            minSdkVersion = 24,
            v1SigningEnabled = true,
            v2SigningEnabled = true,
            v3SigningEnabled = true
        )

        val result = ApkVerificationService.verifyApk(signedApk)
        assertTrue(result.isVerified, "Verification failed with errors: ${result.errors}")
        assertTrue(result.isV1SchemeVerified)
        assertTrue(result.isV2SchemeVerified)
        assertTrue(result.isV3SchemeVerified)
    }

    @Test
    fun `test tampered signed APK fails verification`() {
        val unsignedApk = createMockApk(File(tempDir, "unsigned_tamper.apk"))
        val signedApk = File(tempDir, "signed_tamper.apk")

        val identity = HybridKeystore.generateIdentity("rsa_signer", KeyType.RSA_2048)
        ApkSignerService.signApk(
            inputApk = unsignedApk,
            outputApk = signedApk,
            privateKey = identity.privateKey,
            certificateChain = listOf(identity.certificate)
        )

        assertTrue(ApkVerificationService.verifyApk(signedApk).isVerified)

        // Tamper with bytes in the middle of the signed file
        RandomAccessFile(signedApk, "rw").use { raf ->
            raf.seek(64)
            val b = raf.readByte()
            raf.seek(64)
            raf.writeByte((b.toInt() xor 0xFF))
        }

        val tamperedResult = ApkVerificationService.verifyApk(signedApk)
        assertFalse(tamperedResult.isVerified, "Tampered APK must fail verification")
        assertTrue(tamperedResult.errors.isNotEmpty())

        assertThrows(IllegalStateException::class.java) {
            ApkVerificationService.assertVerified(signedApk)
        }
    }
}
