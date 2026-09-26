package com.retropack.runtime.ui

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.retropack.runtime.core.ScaleMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InGameSettingsOverlayTest {

    @Test
    fun `show and hide toggle visibility and state`() {
        val overlay = InGameSettingsOverlay(Context())
        assertFalse(overlay.isShowing())
        assertEquals(View.GONE, overlay.visibility)

        overlay.show()
        assertTrue(overlay.isShowing())
        assertEquals(View.VISIBLE, overlay.visibility)

        var dismissed = false
        overlay.onDismissed = { dismissed = true }
        overlay.hide()
        assertFalse(overlay.isShowing())
        assertEquals(View.GONE, overlay.visibility)
        assertTrue(dismissed)
    }

    @Test
    fun `edit controls button dismisses dialog and invokes callback`() {
        val overlay = InGameSettingsOverlay(Context())
        overlay.setDimensions(1080, 1920)
        overlay.show()

        // Trigger onDraw to populate hit rects
        overlay.renderForTesting(Canvas())

        var editClicked = false
        overlay.onEditControlsClicked = { editClicked = true }

        val clickX = overlay.editControlsBtnRect.centerX()
        val clickY = overlay.editControlsBtnRect.centerY()

        val event = MotionEvent.createTouch(MotionEvent.ACTION_UP, clickX, clickY)
        overlay.onTouchEvent(event)

        assertTrue(editClicked)
        assertFalse(overlay.isShowing())
    }

    @Test
    fun `scale mode selector changes scaling mode and notifies callback`() {
        val overlay = InGameSettingsOverlay(Context())
        overlay.setDimensions(1080, 1920)
        overlay.show()
        overlay.renderForTesting(Canvas())

        var selectedMode: ScaleMode? = null
        overlay.onScaleModeChanged = { selectedMode = it }

        // Click Integer Fit segment
        val clickX = overlay.scaleIntegerRect.centerX()
        val clickY = overlay.scaleIntegerRect.centerY()

        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, clickX, clickY))

        assertEquals(ScaleMode.INTEGER_FIT, overlay.scaleMode)
        assertEquals(ScaleMode.INTEGER_FIT, selectedMode)
    }

    @Test
    fun `opacity selector updates opacity value and notifies callback`() {
        val overlay = InGameSettingsOverlay(Context())
        overlay.setDimensions(1080, 1920)
        overlay.show()
        overlay.renderForTesting(Canvas())

        var selectedOpacity = -1f
        overlay.onOpacityChanged = { selectedOpacity = it }

        // Click 80% opacity pill (index 3)
        val clickX = overlay.opacityRects[3].centerX()
        val clickY = overlay.opacityRects[3].centerY()

        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, clickX, clickY))

        assertEquals(0.80f, overlay.touchOpacity, 0.001f)
        assertEquals(0.80f, selectedOpacity, 0.001f)
    }

    @Test
    fun `haptics toggle switches haptics state`() {
        val overlay = InGameSettingsOverlay(Context())
        overlay.setDimensions(1080, 1920)
        overlay.show()
        overlay.hapticsEnabled = true
        overlay.renderForTesting(Canvas())

        var hapticsReported: Boolean? = null
        overlay.onHapticsChanged = { hapticsReported = it }

        val clickX = overlay.hapticsToggleRect.centerX()
        val clickY = overlay.hapticsToggleRect.centerY()

        overlay.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, clickX, clickY))

        assertFalse(overlay.hapticsEnabled)
        assertEquals(false, hapticsReported)
    }
}
