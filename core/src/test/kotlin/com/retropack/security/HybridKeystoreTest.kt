package com.retropack.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.Signature

class HybridKeystoreTest {

    @Test
    fun `test RSA-2048 identity generation and certificate validity`() {
        val identity = HybridKeystore.generateIdentity(
            alias = "test_rsa",
            keyType = KeyType.RSA_2048
        )

        assertEquals("test_rsa", identity.alias)
        assertEquals("RSA", identity.keyAlgorithm)
        assertNotNull(identity.privateKey.encoded)

        val cert = identity.certificate
        assertTrue(cert.subjectX500Principal.name.contains("CN=RetroPack Game Signer"))
        assertTrue(cert.subjectX500Principal.name.contains("O=Self-Signed"))
        cert.checkValidity()

        // Test sign and verify
        val testData = "RetroPack Signature Test".toByteArray()
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(identity.privateKey)
        signer.update(testData)
        val signature = signer.sign()

        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(cert.publicKey)
        verifier.update(testData)
        assertTrue(verifier.verify(signature))
    }

    @Test
    fun `test EC P-256 identity generation and certificate validity`() {
        val identity = HybridKeystore.generateIdentity(
            alias = "test_ec",
            keyType = KeyType.EC_P256
        )

        assertEquals("test_ec", identity.alias)
        assertEquals("EC", identity.keyAlgorithm)
        assertNotNull(identity.privateKey.encoded)

        val cert = identity.certificate
        cert.checkValidity()

        // Test sign and verify
        val testData = "RetroPack EC Test".toByteArray()
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(identity.privateKey)
        signer.update(testData)
        val signature = signer.sign()

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(cert.publicKey)
        verifier.update(testData)
        assertTrue(verifier.verify(signature))
    }

    @Test
    fun `test encrypted persistence at rest roundtrip`(@TempDir tempDir: File) {
        val masterProvider = AesGcmMasterKeyProvider.generateRandom()
        val original = HybridKeystore.generateIdentity("persistent_alias", KeyType.RSA_2048)

        val targetFile = File(tempDir, "managed_key.bin")
        HybridKeystore.saveEncrypted(original, targetFile, masterProvider)

        assertTrue(targetFile.exists())
        assertTrue(targetFile.length() > 0)

        val restored = HybridKeystore.loadEncrypted(targetFile, masterProvider)

        assertEquals(original.alias, restored.alias)
        assertEquals(original.keyAlgorithm, restored.keyAlgorithm)
        assertArrayEquals(original.privateKey.encoded, restored.privateKey.encoded)
        assertEquals(original.certificate, restored.certificate)
    }

    @Test
    fun `test PKCS12 password-protected export and re-import`() {
        val original = HybridKeystore.generateIdentity("backup_alias", KeyType.RSA_2048)
        val password = "StrongBackupPassword123!".toCharArray()

        val outStream = ByteArrayOutputStream()
        HybridKeystore.exportToPkcs12(original, password, outStream)

        val p12Bytes = outStream.toByteArray()
        assertTrue(p12Bytes.isNotEmpty())

        val inStream = ByteArrayInputStream(p12Bytes)
        val restored = HybridKeystore.importFromPkcs12(inStream, password, "backup_alias")

        assertEquals(original.alias, restored.alias)
        assertArrayEquals(original.privateKey.encoded, restored.privateKey.encoded)
        assertEquals(original.certificate, restored.certificate)
    }

    @Test
    fun `test corrupt payload sizes rejected without large allocation`() {
        // Valid header, absurd IV size: must throw before any big ByteArray.
        val bos = ByteArrayOutputStream()
        java.io.DataOutputStream(bos).use { dos ->
            dos.write(byteArrayOf('R'.code.toByte(), 'P'.code.toByte(), 'E'.code.toByte(), 'K'.code.toByte()))
            dos.writeInt(1)
            dos.writeUTF("AES/GCM/NoPadding")
            dos.writeInt(Int.MAX_VALUE)
        }
        assertThrows<IllegalArgumentException> {
            EncryptedPayload.fromBytes(bos.toByteArray())
        }
    }
}
