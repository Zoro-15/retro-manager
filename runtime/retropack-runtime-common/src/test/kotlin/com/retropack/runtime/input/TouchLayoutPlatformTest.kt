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
    fun `test GBA touch layout contains A, B, L, R shoulders`() {
        val layout = TouchLayout.createForPlatform("gba", 1080f, 2400f)
        val controlIds = layout.controls.map { it.id }

        assertTrue(controlIds.contains(TouchLayout.ID_A))
        assertTrue(controlIds.contains(TouchLayout.ID_B))
        assertTrue(controlIds.contains(TouchLayout.ID_L))
        assertTrue(controlIds.contains(TouchLayout.ID_R))
        assertTrue(controlIds.contains(TouchLayout.ID_START))
        assertTrue(controlIds.contains(TouchLayout.ID_SELECT))
        assertTrue(controlIds.contains(TouchLayout.ID_DPAD))
    }

    @Test
    fun `test NES touch layout contains A, B, Turbo, and D-Pad`() {
        val layout = TouchLayout.createForPlatform("nes", 1080f, 2400f)
        val controlIds = layout.controls.map { it.id }

        assertTrue(controlIds.contains(TouchLayout.ID_A))
        assertTrue(controlIds.contains(TouchLayout.ID_B))
        assertTrue(controlIds.contains(TouchLayout.ID_START))
        assertTrue(controlIds.contains(TouchLayout.ID_SELECT))
        assertTrue(controlIds.contains(TouchLayout.ID_DPAD))
    }

    @Test
    fun `test PC Engine touch layout contains action buttons and Run Select`() {
        val layout = TouchLayout.createForPlatform("pce", 1080f, 2400f)
        val controlIds = layout.controls.map { it.id }

        assertTrue(controlIds.contains(TouchLayout.ID_A))
        assertTrue(controlIds.contains(TouchLayout.ID_B))
        assertTrue(controlIds.contains(TouchLayout.ID_START))
        assertTrue(controlIds.contains(TouchLayout.ID_SELECT))
        assertTrue(controlIds.contains(TouchLayout.ID_DPAD))
    }
}
