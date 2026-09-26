package com.retropack.runtime.input

import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TouchLayoutPlatformTest {

    @Test
    fun `test SNES touch layout contains X, Y, A, B and L, R shoulders`() {
        val layout = TouchLayout.createForPlatform("snes", 1080f, 2400f)
        val controlIds = layout.controls.map { it.id }

        assertTrue(controlIds.contains(TouchLayout.ID_A))
        assertTrue(controlIds.contains(TouchLayout.ID_B))
        assertTrue(controlIds.contains(TouchLayout.ID_X))
        assertTrue(controlIds.contains(TouchLayout.ID_Y))
        assertTrue(controlIds.contains(TouchLayout.ID_L))
        assertTrue(controlIds.contains(TouchLayout.ID_R))
        assertTrue(controlIds.contains(TouchLayout.ID_START))
        assertTrue(controlIds.contains(TouchLayout.ID_SELECT))
        assertTrue(controlIds.contains(TouchLayout.ID_DPAD))

        val btnX = layout.controls.first { it.id == TouchLayout.ID_X }
        assertEquals(RetroKey.X, btnX.key)
    }

    @Test
    fun `test Genesis 6-button touch layout contains ABC and XYZ`() {
        val layout = TouchLayout.createForPlatform("genesis", 1080f, 2400f)
        val controlIds = layout.controls.map { it.id }

        assertTrue(controlIds.contains(TouchLayout.ID_A))
        assertTrue(controlIds.contains(TouchLayout.ID_B))
        assertTrue(controlIds.contains(TouchLayout.ID_C))
        assertTrue(controlIds.contains(TouchLayout.ID_X))
        assertTrue(controlIds.contains(TouchLayout.ID_Y))
        assertTrue(controlIds.contains(TouchLayout.ID_Z))
        assertTrue(controlIds.contains(TouchLayout.ID_START))
        assertTrue(controlIds.contains(TouchLayout.ID_MODE))

        val btnC = layout.controls.first { it.id == TouchLayout.ID_C }
        assertEquals(RetroKey.C, btnC.key)
        val btnZ = layout.controls.first { it.id == TouchLayout.ID_Z }
        assertEquals(RetroKey.Z, btnZ.key)
    }

    @Test
    fun `test PS1 touch layout contains Dual Shoulders and 4 Face Buttons`() {
        val layout = TouchLayout.createForPlatform("psx", 1080f, 2400f)
        val controlIds = layout.controls.map { it.id }

        assertTrue(controlIds.contains(TouchLayout.ID_A))
        assertTrue(controlIds.contains(TouchLayout.ID_B))
        assertTrue(controlIds.contains(TouchLayout.ID_X))
        assertTrue(controlIds.contains(TouchLayout.ID_Y))
        assertTrue(controlIds.contains(TouchLayout.ID_L))
        assertTrue(controlIds.contains(TouchLayout.ID_L2))
        assertTrue(controlIds.contains(TouchLayout.ID_R))
        assertTrue(controlIds.contains(TouchLayout.ID_R2))
    }

    @Test
    fun `test N64 touch layout contains C-Buttons and Z-Trigger`() {
        val layout = TouchLayout.createForPlatform("n64", 1080f, 2400f)
        val controlIds = layout.controls.map { it.id }

        assertTrue(controlIds.contains(TouchLayout.ID_A))
        assertTrue(controlIds.contains(TouchLayout.ID_B))
        assertTrue(controlIds.contains(TouchLayout.ID_Z))
        assertTrue(controlIds.contains(TouchLayout.ID_C_UP))
        assertTrue(controlIds.contains(TouchLayout.ID_C_DOWN))
        assertTrue(controlIds.contains(TouchLayout.ID_C_LEFT))
        assertTrue(controlIds.contains(TouchLayout.ID_C_RIGHT))
    }
}
