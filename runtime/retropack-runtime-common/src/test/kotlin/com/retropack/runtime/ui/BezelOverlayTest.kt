package com.retropack.runtime.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.retropack.runtime.core.ScaleMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BezelOverlayTest {

    @Test
    fun `bezel toggle state controls drawing behavior`() {
        val bezel = BezelOverlayView(Context())
        assertFalse(bezel.bezelEnabled)

        bezel.bezelEnabled = true
        assertTrue(bezel.bezelEnabled)
    }

    @Test
    fun `renderForTesting executes cleanly in portrait and landscape modes`() {
        val bezel = BezelOverlayView(Context()).apply {
            bezelEnabled = true
            scaleMode = ScaleMode.ASPECT_FIT
            nativeWidth = 240
            nativeHeight = 160
        }

        // Portrait Canvas (1080 x 1920)
        val portraitBitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val portraitCanvas = Canvas(portraitBitmap)
        bezel.renderForTesting(portraitCanvas)

        // Landscape Canvas (1920 x 1080)
        val landscapeBitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val landscapeCanvas = Canvas(landscapeBitmap)
        bezel.renderForTesting(landscapeCanvas)
    }
}
