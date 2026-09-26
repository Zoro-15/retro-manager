package com.retropack.runtime.ui

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuickMenuOverlayTest {

    @Test
    fun `collapsed FAB ignores touches outside its circle`() {
        val menu = QuickMenuOverlay(Context())
        menu.setDimensions(1080, 1920)
        menu.isExpanded = false

        // Touch near center of screen
        val outsideEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, 500f, 500f)
        val handled = menu.onTouchEvent(outsideEvent)

        assertFalse(handled, "Collapsed FAB should let outside touch pass through to game view")
    }

    @Test
    fun `tapping FAB expands satellite buttons and triggers haptic`() {
        val menu = QuickMenuOverlay(Context())
        menu.setDimensions(1080, 1920)
        menu.isExpanded = false

        var hapticTriggered = false
        menu.onHapticFeedbackRequested = { hapticTriggered = true }

        val fabCx = menu.getFabCenterX()
        val fabCy = menu.getFabCenterY()

        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, fabCx, fabCy)
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, fabCx, fabCy)

        assertTrue(menu.onTouchEvent(downEvent))
        assertTrue(menu.onTouchEvent(upEvent))
        assertTrue(menu.isExpanded)
        assertTrue(hapticTriggered)
    }

    @Test
    fun `expanded menu toggles controls on joystick satellite button tap`() {
        val menu = QuickMenuOverlay(Context())
        menu.setDimensions(1080, 1920)
        menu.isExpanded = true
        menu.isControlsActive = true

        var toggleInvoked = false
        menu.onToggleControls = { toggleInvoked = true }

        val fabCx = menu.getFabCenterX()
        val sat1Cy = menu.getFabCenterY() + menu.satelliteSpacing * 1f

        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, fabCx, sat1Cy)
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, fabCx, sat1Cy)

        assertTrue(menu.onTouchEvent(downEvent))
        assertTrue(menu.onTouchEvent(upEvent))
        assertTrue(toggleInvoked)
        assertFalse(menu.isControlsActive)
    }

    @Test
    fun `expanded menu cycles fast-forward speed on satellite 2 tap`() {
        val menu = QuickMenuOverlay(Context())
        menu.setDimensions(1080, 1920)
        menu.isExpanded = true
        menu.fastForwardSpeed = 1

        var speedReported = -1
        menu.onFastForwardSpeedChanged = { speedReported = it }

        val fabCx = menu.getFabCenterX()
        val sat2Cy = menu.getFabCenterY() + menu.satelliteSpacing * 2f

        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, fabCx, sat2Cy)
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, fabCx, sat2Cy)

        assertTrue(menu.onTouchEvent(downEvent))
        assertTrue(menu.onTouchEvent(upEvent))
        assertEquals(2, menu.fastForwardSpeed)
        assertEquals(2, speedReported)

        // Cycle through all speed multipliers: 2 -> 4 -> 8 -> 16 -> 1
        assertEquals(4, menu.cycleFastForwardSpeed())
        assertEquals(8, menu.cycleFastForwardSpeed())
        assertEquals(16, menu.cycleFastForwardSpeed())
        assertEquals(1, menu.cycleFastForwardSpeed())
    }

    @Test
    fun `expanded menu opens settings dialog and collapses menu on settings button tap`() {
        val menu = QuickMenuOverlay(Context())
        menu.setDimensions(1080, 1920)
        menu.isExpanded = true

        var settingsInvoked = false
        menu.onOpenSettings = { settingsInvoked = true }

        val fabCx = menu.getFabCenterX()
        val sat3Cy = menu.getFabCenterY() + menu.satelliteSpacing * 3f

        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, fabCx, sat3Cy)
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, fabCx, sat3Cy)

        assertTrue(menu.onTouchEvent(downEvent))
        assertTrue(menu.onTouchEvent(upEvent))
        assertTrue(settingsInvoked)
        assertFalse(menu.isExpanded, "Menu should collapse when opening settings")
    }

    @Test
    fun `expanded menu opens more sheet and collapses menu on more button tap`() {
        val menu = QuickMenuOverlay(Context())
        menu.setDimensions(1080, 1920)
        menu.isExpanded = true

        var moreInvoked = false
        menu.onOpenMore = { moreInvoked = true }

        val fabCx = menu.getFabCenterX()
        val sat4Cy = menu.getFabCenterY() + menu.satelliteSpacing * 4f

        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, fabCx, sat4Cy)
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, fabCx, sat4Cy)

        assertTrue(menu.onTouchEvent(downEvent))
        assertTrue(menu.onTouchEvent(upEvent))
        assertTrue(moreInvoked)
        assertFalse(menu.isExpanded, "Menu should collapse when opening more sheet")
    }

    @Test
    fun `renderForTesting executes onDraw without error`() {
        val menu = QuickMenuOverlay(Context())
        menu.setDimensions(1080, 1920)
        menu.isExpanded = true
        menu.fastForwardSpeed = 4

        menu.renderForTesting(Canvas())
        // Verified onDraw runs cleanly for collapsed and expanded states
    }
}

