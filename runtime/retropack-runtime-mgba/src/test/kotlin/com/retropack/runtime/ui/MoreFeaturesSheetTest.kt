package com.retropack.runtime.ui

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.retropack.runtime.core.ScaleMode
import com.retropack.runtime.save.SaveStateManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MoreFeaturesSheetTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `show and hide toggle visibility and trigger dismissal callback`() {
        val sheet = MoreFeaturesSheet(Context())
        assertFalse(sheet.isShowing())
        assertEquals(View.GONE, sheet.visibility)

        sheet.show()
        assertTrue(sheet.isShowing())
        assertEquals(View.VISIBLE, sheet.visibility)

        var dismissed = false
        sheet.onDismissed = { dismissed = true }
        sheet.hide()
        assertFalse(sheet.isShowing())
        assertEquals(View.GONE, sheet.visibility)
        assertTrue(dismissed)
    }

    @Test
    fun `tapping slot cards updates selectedSlot and triggers save action`() {
        val sheet = MoreFeaturesSheet(Context())
        val ssm = SaveStateManager(tempDir)
        sheet.saveStateManager = ssm
        sheet.setDimensions(1080, 1920)
        sheet.show()
        sheet.renderForTesting(Canvas())

        var savedSlot = -1
        sheet.onSaveStateClicked = { savedSlot = it }

        // Tap Slot 3 card
        val slot3X = sheet.slotCardRects[3].centerX()
        val slot3Y = sheet.slotCardRects[3].centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, slot3X, slot3Y))
        assertEquals(3, sheet.selectedSlot)

        // Tap Save State Button
        val saveX = sheet.saveStateBtnRect.centerX()
        val saveY = sheet.saveStateBtnRect.centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, saveX, saveY))
        assertEquals(3, savedSlot)
    }

    @Test
    fun `tapping load state button invokes load callback and dismisses sheet`() {
        val sheet = MoreFeaturesSheet(Context())
        sheet.setDimensions(1080, 1920)
        sheet.show()
        sheet.selectedSlot = 2
        sheet.renderForTesting(Canvas())

        var loadedSlot = -1
        sheet.onLoadStateClicked = { loadedSlot = it }

        val loadX = sheet.loadStateBtnRect.centerX()
        val loadY = sheet.loadStateBtnRect.centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, loadX, loadY))

        assertEquals(2, loadedSlot)
        assertFalse(sheet.isShowing(), "Loading save state should auto-dismiss sheet to resume game")
    }

    @Test
    fun `speed selector updates fast-forward multiplier and notifies callback`() {
        val sheet = MoreFeaturesSheet(Context())
        sheet.setDimensions(1080, 1920)
        sheet.show()
        sheet.renderForTesting(Canvas())

        var selectedSpeed = -1
        sheet.onFastForwardSpeedChanged = { selectedSpeed = it }

        // Tap 4x speed pill (index 2)
        val speed4xX = sheet.speedRects[2].centerX()
        val speed4xY = sheet.speedRects[2].centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, speed4xX, speed4xY))

        assertEquals(4, sheet.fastForwardSpeed)
        assertEquals(4, selectedSpeed)
    }

    @Test
    fun `toggles for mute, turbo, and macro switch states`() {
        val sheet = MoreFeaturesSheet(Context())
        sheet.setDimensions(1080, 1920)
        sheet.show()
        sheet.renderForTesting(Canvas())

        var turboReported: Boolean? = null
        sheet.onTurboChanged = { turboReported = it }

        val turboX = sheet.turboToggleRect.centerX()
        val turboY = sheet.turboToggleRect.centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, turboX, turboY))

        assertTrue(sheet.turboButtonsEnabled)
        assertEquals(true, turboReported)

        var macroReported: Boolean? = null
        sheet.onComboMacroChanged = { macroReported = it }

        val macroX = sheet.comboMacroToggleRect.centerX()
        val macroY = sheet.comboMacroToggleRect.centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, macroX, macroY))

        assertTrue(sheet.comboMacroEnabled)
        assertEquals(true, macroReported)
    }

    @Test
    fun `touch theme and display shader toggles update preferences`() {
        val sheet = MoreFeaturesSheet(Context())
        sheet.setDimensions(1080, 1920)
        sheet.show()
        sheet.renderForTesting(Canvas())

        var themeReported: String? = null
        sheet.onTouchThemeChanged = { themeReported = it }

        // Tap Cyber theme pill (index 3)
        val cyberX = sheet.themeRects[3].centerX()
        val cyberY = sheet.themeRects[3].centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, cyberX, cyberY))

        assertEquals("cyber", sheet.touchTheme)
        assertEquals("cyber", themeReported)

        // Tap LCD Grid Filter toggle
        var lcdReported: Boolean? = null
        sheet.onLcdGridChanged = { lcdReported = it }
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, sheet.lcdGridToggleRect.centerX(), sheet.lcdGridToggleRect.centerY()))
        assertTrue(sheet.lcdGridEnabled)
        assertEquals(true, lcdReported)

        // Tap Scale Mode: Integer Fit
        var scaleReported: ScaleMode? = null
        sheet.onScaleModeChanged = { scaleReported = it }
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, sheet.scaleIntegerRect.centerX(), sheet.scaleIntegerRect.centerY()))
        assertEquals(ScaleMode.INTEGER_FIT, sheet.scaleMode)
        assertEquals(ScaleMode.INTEGER_FIT, scaleReported)
    }

    @Test
    fun `edit controls button dismisses sheet and launches layout editor`() {
        val sheet = MoreFeaturesSheet(Context())
        sheet.setDimensions(1080, 1920)
        sheet.show()
        sheet.renderForTesting(Canvas())

        var editLaunched = false
        sheet.onEditControlsClicked = { editLaunched = true }

        val editX = sheet.editControlsBtnRect.centerX()
        val editY = sheet.editControlsBtnRect.centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, editX, editY))

        assertTrue(editLaunched)
        assertFalse(sheet.isShowing())
    }
}
