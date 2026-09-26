package com.retropack.runtime.ui

import android.content.Context
import android.view.MotionEvent
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

        // Satellite 1 is at (1036, 108)
        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, 1036f, 108f)
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, 1036f, 108f)

        assertTrue(menu.onTouchEvent(downEvent))
        assertTrue(menu.onTouchEvent(upEvent))
        assertTrue(toggleInvoked)
        assertFalse(menu.isControlsActive)
    }

    @Test
    fun `expanded menu opens settings dialog and collapses menu on settings button tap`() {
        val menu = QuickMenuOverlay(Context())
        menu.setDimensions(1080, 1920)
        menu.isExpanded = true

        var settingsInvoked = false
        menu.onOpenSettings = { settingsInvoked = true }

        // Satellite 2 is at (1036, 168)
        val downEvent = MotionEvent.createTouch(MotionEvent.ACTION_DOWN, 1036f, 168f)
        val upEvent = MotionEvent.createTouch(MotionEvent.ACTION_UP, 1036f, 168f)

        assertTrue(menu.onTouchEvent(downEvent))
        assertTrue(menu.onTouchEvent(upEvent))
        assertTrue(settingsInvoked)
        assertFalse(menu.isExpanded, "Menu should collapse when opening settings")
    }
}
