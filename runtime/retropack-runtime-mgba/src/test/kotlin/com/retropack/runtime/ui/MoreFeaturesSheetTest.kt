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

        // Tap DMG theme pill (index 3)
        val dmgX = sheet.themeRects[3].centerX()
        val dmgY = sheet.themeRects[3].centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, dmgX, dmgY))

        assertEquals("retro_dmg", sheet.touchTheme)
        assertEquals("retro_dmg", themeReported)

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

    @Test
    fun `floating dpad and gestures toggles update state and invoke callbacks`() {
        val sheet = MoreFeaturesSheet(Context())
        sheet.setDimensions(1080, 1920)
        sheet.show()
        sheet.renderForTesting(Canvas())

        var floatingDpadReported: Boolean? = null
        sheet.onFloatingDpadChanged = { floatingDpadReported = it }
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, sheet.floatingDpadToggleRect.centerX(), sheet.floatingDpadToggleRect.centerY()))
        assertTrue(sheet.floatingDpadEnabled)
        assertEquals(true, floatingDpadReported)

        var gesturesReported: Boolean? = null
        sheet.onGesturesChanged = { gesturesReported = it }
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, sheet.gesturesToggleRect.centerX(), sheet.gesturesToggleRect.centerY()))
        assertFalse(sheet.gesturesEnabled) // Initially true, toggles to false
        assertEquals(false, gesturesReported)
    }

    @Test
    fun `sensor mode pills and calibration button trigger callbacks`() {
        val sheet = MoreFeaturesSheet(Context())
        sheet.setDimensions(1080, 1920)
        sheet.show()
        sheet.renderForTesting(Canvas())

        var sensorModeReported: String? = null
        sheet.onSensorModeChanged = { sensorModeReported = it }

        // Tap D-Pad Tilt (index 1)
        val tiltX = sheet.sensorModeRects[1].centerX()
        val tiltY = sheet.sensorModeRects[1].centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, tiltX, tiltY))
        assertEquals("DPAD_EMULATION", sheet.sensorMode)
        assertEquals("DPAD_EMULATION", sensorModeReported)

        // Tap Native Gyro (index 2)
        val gyroX = sheet.sensorModeRects[2].centerX()
        val gyroY = sheet.sensorModeRects[2].centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, gyroX, gyroY))
        assertEquals("NATIVE_GYRO", sheet.sensorMode)
        assertEquals("NATIVE_GYRO", sensorModeReported)

        // Tap Calibration button
        var calibrated = false
        sheet.onCalibrateSensorClicked = { calibrated = true }
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, sheet.sensorCalibrateBtnRect.centerX(), sheet.sensorCalibrateBtnRect.centerY()))
        assertTrue(calibrated)
    }

    @Test
    fun `gamepad remap button dismisses sheet and launches remap overlay`() {
        val sheet = MoreFeaturesSheet(Context())
        sheet.setDimensions(1080, 1920)
        sheet.show()
        sheet.renderForTesting(Canvas())

        var remapLaunched = false
        sheet.onRemapGamepadClicked = { remapLaunched = true }

        val remapX = sheet.gamepadRemapBtnRect.centerX()
        val remapY = sheet.gamepadRemapBtnRect.centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, remapX, remapY))

        assertTrue(remapLaunched)
        assertFalse(sheet.isShowing())
    }

    @Test
    fun `dpad type and joystick snap mode segmented buttons update state and fire callbacks`() {
        val sheet = MoreFeaturesSheet(Context())
        sheet.setDimensions(1080, 1920)
        sheet.show()
        sheet.renderForTesting(Canvas())

        var reportedDpadType: DpadType? = null
        var reportedSnapMode: JoystickSnapMode? = null
        sheet.onDpadTypeChanged = { reportedDpadType = it }
        sheet.onJoystickSnapModeChanged = { reportedSnapMode = it }

        // 1. Select Fixed Joystick (index 1)
        val fixedX = sheet.dpadTypeRects[1].centerX()
        val fixedY = sheet.dpadTypeRects[1].centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, fixedX, fixedY))
        assertEquals(DpadType.FIXED_JOYSTICK, sheet.dpadType)
        assertEquals(DpadType.FIXED_JOYSTICK, reportedDpadType)
        assertFalse(sheet.floatingDpadEnabled)

        // 2. Select Floating Stick (index 2)
        val floatX = sheet.dpadTypeRects[2].centerX()
        val floatY = sheet.dpadTypeRects[2].centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, floatX, floatY))
        assertEquals(DpadType.FLOATING_JOYSTICK, sheet.dpadType)
        assertEquals(DpadType.FLOATING_JOYSTICK, reportedDpadType)
        assertTrue(sheet.floatingDpadEnabled)

        // 3. Select 8-Way Action Snap Mode (index 1)
        val actionSnapX = sheet.snapModeRects[1].centerX()
        val actionSnapY = sheet.snapModeRects[1].centerY()
        sheet.onTouchEvent(MotionEvent.createTouch(MotionEvent.ACTION_UP, actionSnapX, actionSnapY))
        assertEquals(JoystickSnapMode.ACTION_8WAY, sheet.joystickSnapMode)
        assertEquals(JoystickSnapMode.ACTION_8WAY, reportedSnapMode)
    }
}
