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

    @Test
    fun `evaluates clusters and discovers all standard control groups`() {
        val layout = TouchLayout.portrait(1080f, 1920f)
        val clusters = layout.getClusters()
        assertEquals(5, clusters.size)

        val clusterIds = clusters.map { it.id }.toSet()
        assertTrue(clusterIds.contains(TouchLayout.CLUSTER_DPAD))
        assertTrue(clusterIds.contains(TouchLayout.CLUSTER_ACTION))
        assertTrue(clusterIds.contains(TouchLayout.CLUSTER_SHOULDER_L))
        assertTrue(clusterIds.contains(TouchLayout.CLUSTER_SHOULDER_R))
        assertTrue(clusterIds.contains(TouchLayout.CLUSTER_SYSTEM))

        val actionCluster = layout.getCluster(TouchLayout.CLUSTER_ACTION)
        assertNotNull(actionCluster)
        assertEquals(listOf(TouchLayout.ID_B, TouchLayout.ID_A), actionCluster!!.controlIds)
    }

    @Test
    fun `withClusterPosition translates all controls in cluster maintaining relative spacing`() {
        val layout = TouchLayout.portrait(1080f, 1920f)
        val btnA_orig = layout.controls.first { it.id == TouchLayout.ID_A }
        val btnB_orig = layout.controls.first { it.id == TouchLayout.ID_B }
        val origDeltaX = btnA_orig.cx - btnB_orig.cx
        val origDeltaY = btnA_orig.cy - btnB_orig.cy

        val moved = layout.withClusterPosition(TouchLayout.CLUSTER_ACTION, 400f, 500f)
        val btnA_moved = moved.controls.first { it.id == TouchLayout.ID_A }
        val btnB_moved = moved.controls.first { it.id == TouchLayout.ID_B }

        // Relative spacing must be identically preserved
        assertEquals(origDeltaX, btnA_moved.cx - btnB_moved.cx, 0.001f)
        assertEquals(origDeltaY, btnA_moved.cy - btnB_moved.cy, 0.001f)

        // Midpoint should match the new anchor (400, 500)
        assertEquals(400f, (btnA_moved.cx + btnB_moved.cx) / 2f, 0.001f)
        assertEquals(500f, (btnA_moved.cy + btnB_moved.cy) / 2f, 0.001f)
    }

    @Test
    fun `normalized cluster coordinates apply accurately across resolutions`() {
        val layout1 = TouchLayout.portrait(1080f, 1920f)
        val customLayout = layout1.withClusterPosition(TouchLayout.CLUSTER_DPAD, 300f, 1200f)

        val normPositions = customLayout.getNormalizedClusterPositions()
        assertEquals(300f / 1080f, normPositions[TouchLayout.CLUSTER_DPAD]!!.first, 0.001f)
        assertEquals(1200f / 1920f, normPositions[TouchLayout.CLUSTER_DPAD]!!.second, 0.001f)

        // Apply normalized coordinates to a higher resolution screen (1440x2560)
        val layout2 = TouchLayout.portrait(1440f, 2560f)
        val scaledLayout = layout2.applyNormalizedClusterPositions(normPositions)

        val scaledDpad = scaledLayout.controls.first { it.id == TouchLayout.ID_DPAD }
        assertEquals(1440f * (300f / 1080f), scaledDpad.cx, 0.01f)
        assertEquals(2560f * (1200f / 1920f), scaledDpad.cy, 0.01f)
    }

    @Test
    fun `findClusterAt identifies touched control cluster`() {
        val layout = TouchLayout.portrait(1080f, 1920f)
        val dpad = layout.controls.first { it.id == TouchLayout.ID_DPAD }

        val hitCluster = layout.findClusterAt(dpad.cx, dpad.cy)
        assertNotNull(hitCluster)
        assertEquals(TouchLayout.CLUSTER_DPAD, hitCluster!!.id)
    }

    @Test
    fun `20 percent hitbox expansion detects touches slightly outside visual boundary`() {
        val layout = TouchLayout.portrait(1080f, 1920f)
        val btnA = layout.controls.first { it.id == TouchLayout.ID_A }

        // Point exactly at 1.10x radius (outside 1.0x visual, but inside 1.20x slop)
        val nearPointX = btnA.cx + btnA.halfWidth * 1.10f
        val nearPointY = btnA.cy

        val inputMask = layout.inputAt(nearPointX, nearPointY)
        assertEquals(RetroKey.KEY_A, inputMask, "20% touch slop must activate button within 1.20x radius")

        // Point at 1.35x radius (outside 1.20x slop)
        val farPointX = btnA.cx + btnA.halfWidth * 1.35f
        val farPointY = btnA.cy

        val farMask = layout.inputAt(farPointX, farPointY)
        assertEquals(RetroKey.NO_KEYS_MASK, farMask, "Point beyond 1.20x slop must not activate button")
    }

    @Test
    fun `thumb roll assist activates both buttons when rolling across A and B corridor`() {
        val layout = TouchLayout.portrait(1080f, 1920f)
        val btnA = layout.controls.first { it.id == TouchLayout.ID_A }
        val btnB = layout.controls.first { it.id == TouchLayout.ID_B }

        // Midpoint between B and A
        val midX = (btnA.cx + btnB.cx) * 0.5f
        val midY = (btnA.cy + btnB.cy) * 0.5f

        val rollMask = layout.inputAt(midX, midY)
        assertEquals(RetroKey.KEY_A or RetroKey.KEY_B, rollMask, "Rolling between A and B must activate both keys")
    }

    @Test
    fun `withClusterScale scales cluster controls around anchor center`() {
        val layout = TouchLayout.portrait(1080f, 1920f)
        val origActionCluster = layout.getCluster(TouchLayout.CLUSTER_ACTION)!!
        val origBtnA = layout.controls.first { it.id == TouchLayout.ID_A }
        val origBtnB = layout.controls.first { it.id == TouchLayout.ID_B }

        // Scale action cluster to 1.5x
        val scaledLayout = layout.withClusterScale(TouchLayout.CLUSTER_ACTION, 1.5f)
        val scaledBtnA = scaledLayout.controls.first { it.id == TouchLayout.ID_A }
        val scaledBtnB = scaledLayout.controls.first { it.id == TouchLayout.ID_B }

        assertEquals(origBtnA.halfWidth * 1.5f, scaledBtnA.halfWidth, 0.001f)
        assertEquals(origBtnB.halfWidth * 1.5f, scaledBtnB.halfWidth, 0.001f)

        // Cluster anchor should remain identical
        val newCluster = scaledLayout.getCluster(TouchLayout.CLUSTER_ACTION)!!
        assertEquals(origActionCluster.anchorX, newCluster.anchorX, 0.001f)
        assertEquals(origActionCluster.anchorY, newCluster.anchorY, 0.001f)
    }

    @Test
    fun `withSuperpowers adds turbo and combo macro buttons`() {
        val baseLayout = TouchLayout.portrait(1080f, 1920f)
        assertFalse(baseLayout.turboEnabled)
        assertFalse(baseLayout.comboEnabled)
        assertEquals(7, baseLayout.controls.size)

        val superLayout = baseLayout.withSuperpowers(newTurboEnabled = true, newComboEnabled = true)
        assertTrue(superLayout.turboEnabled)
        assertTrue(superLayout.comboEnabled)
        assertEquals(10, superLayout.controls.size) // +2 Turbo (TA, TB) + 1 Combo (A+B)

        val turboA = superLayout.controls.first { it.id == TouchLayout.ID_TURBO_A }
        val turboB = superLayout.controls.first { it.id == TouchLayout.ID_TURBO_B }
        val combo = superLayout.controls.first { it.id == TouchLayout.ID_COMBO_AB }

        assertTrue(turboA.isTurbo)
        assertTrue(turboB.isTurbo)

        // Turbo A during active turbo phase -> KEY_A, inactive phase -> NO_KEYS_MASK
        assertEquals(RetroKey.KEY_A, superLayout.inputAt(turboA.cx, turboA.cy, isTurboPhase = true))
        assertEquals(RetroKey.NO_KEYS_MASK, superLayout.inputAt(turboA.cx, turboA.cy, isTurboPhase = false))

        // Combo A+B pill always fires both A and B
        assertEquals(RetroKey.KEY_A or RetroKey.KEY_B, superLayout.inputAt(combo.cx, combo.cy))
    }
}


