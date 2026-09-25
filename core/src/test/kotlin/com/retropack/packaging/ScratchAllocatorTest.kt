package com.retropack.packaging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.StandardCharsets

class ScratchAllocatorTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `allocates scratch file in same directory as target`() {
        val target = File(tempDir, "game.apk")
        val scratch = ScratchAllocator.allocateScratchFile(target)

        assertEquals(target.parentFile.canonicalPath, scratch.parentFile.canonicalPath)
        assertTrue(scratch.name.startsWith("."))
        assertTrue(scratch.name.endsWith(".tmp.apk"))
    }

    @Test
    fun `detects signature residue correctly`() {
        assertTrue(ScratchAllocator.isSignatureResidue("META-INF/MANIFEST.MF"))
        assertTrue(ScratchAllocator.isSignatureResidue("META-INF/CERT.SF"))
        assertTrue(ScratchAllocator.isSignatureResidue("META-INF/CERT.RSA"))
        assertTrue(ScratchAllocator.isSignatureResidue("META-INF/CERT.DSA"))
        assertTrue(ScratchAllocator.isSignatureResidue("META-INF/CERT.EC"))
        assertTrue(ScratchAllocator.isSignatureResidue("META-INF/SIG-RELEASE.RSA"))
        assertTrue(ScratchAllocator.isSignatureResidue("META-INF/signing.sig"))

        // Standard non-signature entries must NOT be stripped
        assertFalse(ScratchAllocator.isSignatureResidue("AndroidManifest.xml"))
        assertFalse(ScratchAllocator.isSignatureResidue("classes.dex"))
        assertFalse(ScratchAllocator.isSignatureResidue("lib/arm64-v8a/libmgba.so"))
        assertFalse(ScratchAllocator.isSignatureResidue("META-INF/services/com.retropack.Service"))
    }

    @Test
    fun `atomicFinalize renames scratch file to target and replaces existing`() {
        val target = File(tempDir, "final.apk")
        val scratch = ScratchAllocator.allocateScratchFile(target)
        scratch.writeText("New APK Content", StandardCharsets.UTF_8)

        ScratchAllocator.atomicFinalize(scratch, target)

        assertFalse(scratch.exists(), "Scratch file must be cleaned up")
        assertTrue(target.exists(), "Target file must exist")
        assertEquals("New APK Content", target.readText(StandardCharsets.UTF_8))
    }
}
