package com.retropack.runtime.input

import android.content.Context
import com.retropack.runtime.core.ScaleMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ControlsPreferencesTest {

    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        context = Context()
        ControlsPreferences.resetAll(context)
    }

    @Test
    fun `persists and loads custom cluster positions for portrait and landscape`() {
        val portraitPositions = mapOf(
            TouchLayout.CLUSTER_DPAD to Pair(0.25f, 0.70f),
            TouchLayout.CLUSTER_ACTION to Pair(0.75f, 0.70f)
        )

        val landscapePositions = mapOf(
            TouchLayout.CLUSTER_DPAD to Pair(0.15f, 0.60f),
            TouchLayout.CLUSTER_ACTION to Pair(0.85f, 0.60f)
        )

        // Save portrait and landscape layouts
        ControlsPreferences.saveLayoutPositions(context, isLandscape = false, portraitPositions)
        ControlsPreferences.saveLayoutPositions(context, isLandscape = true, landscapePositions)

        assertTrue(ControlsPreferences.hasCustomLayout(context, isLandscape = false))
        assertTrue(ControlsPreferences.hasCustomLayout(context, isLandscape = true))

        val loadedPortrait = ControlsPreferences.loadLayoutPositions(context, isLandscape = false)
        assertNotNull(loadedPortrait)
        assertEquals(0.25f, loadedPortrait!![TouchLayout.CLUSTER_DPAD]!!.first, 0.001f)
        assertEquals(0.70f, loadedPortrait[TouchLayout.CLUSTER_DPAD]!!.second, 0.001f)

        val loadedLandscape = ControlsPreferences.loadLayoutPositions(context, isLandscape = true)
        assertNotNull(loadedLandscape)
        assertEquals(0.15f, loadedLandscape!![TouchLayout.CLUSTER_DPAD]!!.first, 0.001f)
        assertEquals(0.60f, loadedLandscape[TouchLayout.CLUSTER_DPAD]!!.second, 0.001f)
    }

    @Test
    fun `clearCustomLayout removes custom coordinates for specified orientation`() {
        val portraitPositions = mapOf(
            TouchLayout.CLUSTER_DPAD to Pair(0.25f, 0.70f)
        )
        ControlsPreferences.saveLayoutPositions(context, isLandscape = false, portraitPositions)
        assertTrue(ControlsPreferences.hasCustomLayout(context, isLandscape = false))

        ControlsPreferences.clearCustomLayout(context, isLandscape = false)
        assertFalse(ControlsPreferences.hasCustomLayout(context, isLandscape = false))
        assertNull(ControlsPreferences.loadLayoutPositions(context, isLandscape = false))
    }

    @Test
    fun `persists and loads opacity, haptics, and scale mode`() {
        ControlsPreferences.saveOpacity(context, 0.85f)
        assertEquals(0.85f, ControlsPreferences.loadOpacity(context, 0.60f), 0.001f)

        ControlsPreferences.saveHaptics(context, false)
        assertFalse(ControlsPreferences.loadHaptics(context, true))

        ControlsPreferences.saveScaleMode(context, ScaleMode.INTEGER_FIT)
        assertEquals(ScaleMode.INTEGER_FIT, ControlsPreferences.loadScaleMode(context, ScaleMode.ASPECT_FIT))
    }

    @Test
    fun `persists and loads cluster scales for portrait and landscape`() {
        val portraitScales = mapOf(
            TouchLayout.CLUSTER_DPAD to 1.3f,
            TouchLayout.CLUSTER_ACTION to 0.8f
        )
        ControlsPreferences.saveLayoutScales(context, isLandscape = false, portraitScales)

        val loaded = ControlsPreferences.loadLayoutScales(context, isLandscape = false)
        assertNotNull(loaded)
        assertEquals(1.3f, loaded!![TouchLayout.CLUSTER_DPAD]!!, 0.001f)
        assertEquals(0.8f, loaded[TouchLayout.CLUSTER_ACTION]!!, 0.001f)
    }

    @Test
    fun `persists and loads fast-forward and feature hub preferences`() {
        ControlsPreferences.saveFastForwardSpeed(context, 4)
        assertEquals(4, ControlsPreferences.loadFastForwardSpeed(context, 1))

        ControlsPreferences.saveMuteAudioOnFastForward(context, false)
        assertFalse(ControlsPreferences.loadMuteAudioOnFastForward(context, true))

        ControlsPreferences.saveTurboEnabled(context, true)
        assertTrue(ControlsPreferences.loadTurboEnabled(context, false))

        ControlsPreferences.saveComboMacroEnabled(context, true)
        assertTrue(ControlsPreferences.loadComboMacroEnabled(context, false))

        ControlsPreferences.saveTouchTheme(context, "cyber")
        assertEquals("cyber", ControlsPreferences.loadTouchTheme(context, "neon"))

        ControlsPreferences.saveLcdGridEnabled(context, true)
        assertTrue(ControlsPreferences.loadLcdGridEnabled(context, false))

        ControlsPreferences.saveGbaColorCorrectionEnabled(context, true)
        assertTrue(ControlsPreferences.loadGbaColorCorrectionEnabled(context, false))

        ControlsPreferences.saveBezelEnabled(context, true)
        assertTrue(ControlsPreferences.loadBezelEnabled(context, false))

        // D-Pad Type Persistence
        ControlsPreferences.saveDpadType(context, DpadType.FIXED_JOYSTICK)
        assertEquals(DpadType.FIXED_JOYSTICK, ControlsPreferences.loadDpadType(context))
        assertFalse(ControlsPreferences.loadFloatingDpadEnabled(context))

        ControlsPreferences.saveDpadType(context, DpadType.FLOATING_JOYSTICK)
        assertEquals(DpadType.FLOATING_JOYSTICK, ControlsPreferences.loadDpadType(context))
        assertTrue(ControlsPreferences.loadFloatingDpadEnabled(context))

        // Joystick Snap Mode Persistence
        ControlsPreferences.saveJoystickSnapMode(context, JoystickSnapMode.ACTION_8WAY)
        assertEquals(JoystickSnapMode.ACTION_8WAY, ControlsPreferences.loadJoystickSnapMode(context))

        // Joystick Deadzone & Sensitivity
        ControlsPreferences.saveJoystickDeadzone(context, 16.0f)
        assertEquals(16.0f, ControlsPreferences.loadJoystickDeadzone(context), 0.001f)

        ControlsPreferences.saveJoystickSensitivity(context, 1.5f)
        assertEquals(1.5f, ControlsPreferences.loadJoystickSensitivity(context), 0.001f)
    }
}

