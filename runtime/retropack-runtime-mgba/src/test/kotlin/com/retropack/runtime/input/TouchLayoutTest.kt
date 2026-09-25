package com.retropack.runtime.input

import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TouchLayoutTest {

    @Test
    fun `creates portrait layout for tall aspect ratio`() {
        val layout = TouchLayout.create(1080f, 1920f)
        assertEquals(1080f, layout.width)
        assertEquals(1920f, layout.height)

        val controlIds = layout.controls.map { it.id }.toSet()
        assertTrue(controlIds.contains(TouchLayout.ID_DPAD))
        assertTrue(controlIds.contains(TouchLayout.ID_A))
        assertTrue(controlIds.contains(TouchLayout.ID_B))
        assertTrue(controlIds.contains(TouchLayout.ID_L))
        assertTrue(controlIds.contains(TouchLayout.ID_R))
        assertTrue(controlIds.contains(TouchLayout.ID_START))
        assertTrue(controlIds.contains(TouchLayout.ID_SELECT))
    }

    @Test
    fun `creates landscape layout for wide aspect ratio`() {
        val layout = TouchLayout.create(1920f, 1080f)
        assertEquals(1920f, layout.width)
        assertEquals(1080f, layout.height)

        val controlIds = layout.controls.map { it.id }.toSet()
        assertEquals(7, controlIds.size)
        assertTrue(controlIds.contains(TouchLayout.ID_DPAD))
        assertTrue(controlIds.contains(TouchLayout.ID_A))
        assertTrue(controlIds.contains(TouchLayout.ID_B))
    }

    @Test
    fun `dpad hit testing detects cardinal directions`() {
        val layout = TouchLayout.portrait(1080f, 1920f)
        val dpad = layout.controls.first { it.id == TouchLayout.ID_DPAD }

        // Test UP
        val upMask = dpad.hitKeyMask(dpad.cx, dpad.cy - dpad.halfHeight * 0.7f)
        assertEquals(RetroKey.KEY_UP, upMask)

        // Test DOWN
        val downMask = dpad.hitKeyMask(dpad.cx, dpad.cy + dpad.halfHeight * 0.7f)
        assertEquals(RetroKey.KEY_DOWN, downMask)

        // Test LEFT
        val leftMask = dpad.hitKeyMask(dpad.cx - dpad.halfWidth * 0.7f, dpad.cy)
        assertEquals(RetroKey.KEY_LEFT, leftMask)

        // Test RIGHT
        val rightMask = dpad.hitKeyMask(dpad.cx + dpad.halfWidth * 0.7f, dpad.cy)
        assertEquals(RetroKey.KEY_RIGHT, rightMask)
    }

    @Test
    fun `dpad hit testing detects diagonal directions`() {
        val layout = TouchLayout.portrait(1080f, 1920f)
        val dpad = layout.controls.first { it.id == TouchLayout.ID_DPAD }

        // Test UP + RIGHT
        val upRightMask = dpad.hitKeyMask(
            dpad.cx + dpad.halfWidth * 0.6f,
            dpad.cy - dpad.halfHeight * 0.6f
        )
        assertEquals(RetroKey.KEY_UP or RetroKey.KEY_RIGHT, upRightMask)

        // Test DOWN + LEFT
        val downLeftMask = dpad.hitKeyMask(
            dpad.cx - dpad.halfWidth * 0.6f,
            dpad.cy + dpad.halfHeight * 0.6f
        )
        assertEquals(RetroKey.KEY_DOWN or RetroKey.KEY_LEFT, downLeftMask)
    }

    @Test
    fun `dpad hit testing ignores center deadzone`() {
        val layout = TouchLayout.portrait(1080f, 1920f)
        val dpad = layout.controls.first { it.id == TouchLayout.ID_DPAD }

        // Deadzone is halfWidth * 0.20f. Center is exact cx, cy
        val centerMask = dpad.hitKeyMask(dpad.cx, dpad.cy)
        assertEquals(RetroKey.NO_KEYS_MASK, centerMask)

        val tinyOffset = dpad.hitKeyMask(dpad.cx + dpad.halfWidth * 0.05f, dpad.cy)
        assertEquals(RetroKey.NO_KEYS_MASK, tinyOffset)
    }

    @Test
    fun `action and shoulder buttons hit test accurately`() {
        val layout = TouchLayout.portrait(1080f, 1920f)

        val btnA = layout.controls.first { it.id == TouchLayout.ID_A }
        assertEquals(RetroKey.KEY_A, layout.inputAt(btnA.cx, btnA.cy))

        val btnB = layout.controls.first { it.id == TouchLayout.ID_B }
        assertEquals(RetroKey.KEY_B, layout.inputAt(btnB.cx, btnB.cy))

        val btnL = layout.controls.first { it.id == TouchLayout.ID_L }
        assertEquals(RetroKey.KEY_L, layout.inputAt(btnL.cx, btnL.cy))

        val btnR = layout.controls.first { it.id == TouchLayout.ID_R }
        assertEquals(RetroKey.KEY_R, layout.inputAt(btnR.cx, btnR.cy))

        val btnStart = layout.controls.first { it.id == TouchLayout.ID_START }
        assertEquals(RetroKey.KEY_START, layout.inputAt(btnStart.cx, btnStart.cy))

        val btnSelect = layout.controls.first { it.id == TouchLayout.ID_SELECT }
        assertEquals(RetroKey.KEY_SELECT, layout.inputAt(btnSelect.cx, btnSelect.cy))
    }

    @Test
    fun `multi-touch pointer resolution aggregates key mask`() {
        val layout = TouchLayout.portrait(1080f, 1920f)
        val dpad = layout.controls.first { it.id == TouchLayout.ID_DPAD }
        val btnA = layout.controls.first { it.id == TouchLayout.ID_A }
        val btnB = layout.controls.first { it.id == TouchLayout.ID_B }

        // Thumb 1 on D-pad RIGHT, Thumb 2 on A, Thumb 3 on B
        val pointers = listOf(
            Pair(dpad.cx + dpad.halfWidth * 0.7f, dpad.cy),
            Pair(btnA.cx, btnA.cy),
            Pair(btnB.cx, btnB.cy)
        )

        val compositeMask = layout.resolvePointers(pointers)
        assertEquals(RetroKey.KEY_RIGHT or RetroKey.KEY_A or RetroKey.KEY_B, compositeMask)
    }

    @Test
    fun `custom layout overrides opacity and control positioning`() {
        val layout = TouchLayout.portrait(1080f, 1920f)
        val adjustedOpacity = layout.withOpacity(0.85f)
        assertEquals(0.85f, adjustedOpacity.opacity)

        val movedA = layout.withControlPosition(TouchLayout.ID_A, 500f, 600f, 1.2f)
        val controlA = movedA.controls.first { it.id == TouchLayout.ID_A }
        assertEquals(500f, controlA.cx)
        assertEquals(600f, controlA.cy)
    }
}
