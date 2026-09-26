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
}
