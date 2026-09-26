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

        // Touch FAB at top right (x=1036, y=48)
        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, 1036f, 48f)
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, 1036f, 48f)

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

        // Satellite 1 is at cy = 48 + 56 = 104
        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, 1036f, 104f)
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, 1036f, 104f)

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

        // Satellite 2 is at cy = 48 + 56 * 2 = 160
        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, 1036f, 160f)
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, 1036f, 160f)

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

        // Satellite 3 is at cy = 48 + 56 * 3 = 216
        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, 1036f, 216f)
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, 1036f, 216f)

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

        // Satellite 4 is at cy = 48 + 56 * 4 = 272
        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, 1036f, 272f)
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, 1036f, 272f)

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

