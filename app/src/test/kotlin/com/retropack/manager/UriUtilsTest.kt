package com.retropack.manager

import com.retropack.manager.util.UriUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UriUtilsTest {

    @Test
    fun testFormatFileSize() {
        assertEquals("512 B", UriUtils.formatFileSize(512L))
        assertEquals("1.0 KB", UriUtils.formatFileSize(1024L))
        assertEquals("512.0 KB", UriUtils.formatFileSize(512 * 1024L))
        assertEquals("1.00 MB", UriUtils.formatFileSize(1024 * 1024L))
        assertEquals("16.00 MB", UriUtils.formatFileSize(16 * 1024 * 1024L))
        assertEquals("32.50 MB", UriUtils.formatFileSize((32.5 * 1024 * 1024).toLong()))
    }
}
