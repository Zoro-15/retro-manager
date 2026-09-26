package com.retropack.runtime.input

import com.retropack.runtime.core.RetroKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VirtualJoystickTest {

    @Test
    fun `deadzone core ignores jitter below deadzone radius`() {
        val joystick = VirtualJoystick(baseCenterX = 200f, baseCenterY = 400f, deadzoneRadius = 12f, baseRadius = 54f)

        // Touch at 5px offset (within 12px deadzone)
        val res = joystick.onDown(205f, 400f, id = 0, isFloating = false)

        assertTrue(res.isDeadzone)
        assertEquals(RetroKey.NO_KEYS_MASK, res.keyMask)
        assertEquals(VirtualJoystick.SECTOR_DEADZONE, res.sector)
        assertEquals(5f, res.distance, 0.001f)
    }

    @Test
    fun `smooth angle clamping clamps knob displacement to base radius`() {
        val joystick = VirtualJoystick(baseCenterX = 200f, baseCenterY = 400f, deadzoneRadius = 12f, baseRadius = 54f)

        // Touch far to the right at 100px deflection (r > 54px)
        val res = joystick.onDown(300f, 400f, id = 0, isFloating = false)

        assertFalse(res.isDeadzone)
        assertEquals(RetroKey.KEY_RIGHT, res.keyMask)
        assertEquals(VirtualJoystick.SECTOR_RIGHT, res.sector)
        assertEquals(100f, res.distance, 0.001f)

        // Knob position clamped to 200 + 54 = 254
        assertEquals(254f, joystick.knobX, 0.01f)
        assertEquals(400f, joystick.knobY, 0.01f)
    }

    @Test
    fun `spring snap back resets knob coordinates to center on release`() {
        val joystick = VirtualJoystick(baseCenterX = 200f, baseCenterY = 400f, baseRadius = 54f)

        joystick.onDown(250f, 400f, id = 0, isFloating = false)
        assertTrue(joystick.isActive)
        assertEquals(250f, joystick.knobX, 0.01f)

        // Release
        val upRes = joystick.onUp()
        assertFalse(joystick.isActive)
        assertEquals(RetroKey.NO_KEYS_MASK, upRes.keyMask)
        assertEquals(200f, joystick.knobX, 0.01f)
        assertEquals(400f, joystick.knobY, 0.01f)
    }

    @Test
    fun `rpg 4-way grid mode enforces 60-degree cardinal windows and 30-degree diagonal windows`() {
        val joystick = VirtualJoystick(
            baseCenterX = 200f,
            baseCenterY = 400f,
            deadzoneRadius = 10f,
            baseRadius = 50f,
            snapMode = JoystickSnapMode.RPG_GRID_4WAY
        )

        // Angle 0° (Pure Right) -> Sector 0 (RIGHT)
        assertEquals(VirtualJoystick.SECTOR_RIGHT, joystick.resolveSector(0f, JoystickSnapMode.RPG_GRID_4WAY))
        assertEquals(RetroKey.KEY_RIGHT, joystick.sectorToKeyMask(VirtualJoystick.SECTOR_RIGHT))

        // Angle 25° (Within 60° cardinal Right window: 330° to 30°) -> Sector 0 (RIGHT)
        assertEquals(VirtualJoystick.SECTOR_RIGHT, joystick.resolveSector(25f, JoystickSnapMode.RPG_GRID_4WAY))

        // Angle 35° (Within 30° narrowed diagonal window: 30° to 60°) -> Sector 1 (DOWN_RIGHT)
        assertEquals(VirtualJoystick.SECTOR_DOWN_RIGHT, joystick.resolveSector(35f, JoystickSnapMode.RPG_GRID_4WAY))
        assertEquals(RetroKey.KEY_RIGHT or RetroKey.KEY_DOWN, joystick.sectorToKeyMask(VirtualJoystick.SECTOR_DOWN_RIGHT))

        // Angle 85° (Within 60° cardinal Down window: 60° to 120°) -> Sector 2 (DOWN)
        assertEquals(VirtualJoystick.SECTOR_DOWN, joystick.resolveSector(85f, JoystickSnapMode.RPG_GRID_4WAY))
        assertEquals(RetroKey.KEY_DOWN, joystick.sectorToKeyMask(VirtualJoystick.SECTOR_DOWN))

        // Angle 135° (Within 30° diagonal window: 120° to 150°) -> Sector 3 (DOWN_LEFT)
        assertEquals(VirtualJoystick.SECTOR_DOWN_LEFT, joystick.resolveSector(135f, JoystickSnapMode.RPG_GRID_4WAY))
        assertEquals(RetroKey.KEY_DOWN or RetroKey.KEY_LEFT, joystick.sectorToKeyMask(VirtualJoystick.SECTOR_DOWN_LEFT))

        // Angle 180° (Within 60° cardinal Left window: 150° to 210°) -> Sector 4 (LEFT)
        assertEquals(VirtualJoystick.SECTOR_LEFT, joystick.resolveSector(180f, JoystickSnapMode.RPG_GRID_4WAY))
        assertEquals(RetroKey.KEY_LEFT, joystick.sectorToKeyMask(VirtualJoystick.SECTOR_LEFT))

        // Angle 225° (Within 30° diagonal window: 210° to 240°) -> Sector 5 (UP_LEFT)
        assertEquals(VirtualJoystick.SECTOR_UP_LEFT, joystick.resolveSector(225f, JoystickSnapMode.RPG_GRID_4WAY))
        assertEquals(RetroKey.KEY_LEFT or RetroKey.KEY_UP, joystick.sectorToKeyMask(VirtualJoystick.SECTOR_UP_LEFT))

        // Angle 270° (Within 60° cardinal Up window: 240° to 300°) -> Sector 6 (UP)
        assertEquals(VirtualJoystick.SECTOR_UP, joystick.resolveSector(270f, JoystickSnapMode.RPG_GRID_4WAY))
        assertEquals(RetroKey.KEY_UP, joystick.sectorToKeyMask(VirtualJoystick.SECTOR_UP))

        // Angle 315° (Within 30° diagonal window: 300° to 330°) -> Sector 7 (UP_RIGHT)
        assertEquals(VirtualJoystick.SECTOR_UP_RIGHT, joystick.resolveSector(315f, JoystickSnapMode.RPG_GRID_4WAY))
        assertEquals(RetroKey.KEY_UP or RetroKey.KEY_RIGHT, joystick.sectorToKeyMask(VirtualJoystick.SECTOR_UP_RIGHT))
    }

    @Test
    fun `8-way action mode balances 45-degree octagonal sector gates`() {
        val joystick = VirtualJoystick(snapMode = JoystickSnapMode.ACTION_8WAY)

        // Right: 337.5° to 22.5°
        assertEquals(VirtualJoystick.SECTOR_RIGHT, joystick.resolveSector(10f, JoystickSnapMode.ACTION_8WAY))
        // Down-Right: 22.5° to 67.5°
        assertEquals(VirtualJoystick.SECTOR_DOWN_RIGHT, joystick.resolveSector(45f, JoystickSnapMode.ACTION_8WAY))
        // Down: 67.5° to 112.5°
        assertEquals(VirtualJoystick.SECTOR_DOWN, joystick.resolveSector(90f, JoystickSnapMode.ACTION_8WAY))
        // Down-Left: 112.5° to 157.5°
        assertEquals(VirtualJoystick.SECTOR_DOWN_LEFT, joystick.resolveSector(135f, JoystickSnapMode.ACTION_8WAY))
        // Left: 157.5° to 202.5°
        assertEquals(VirtualJoystick.SECTOR_LEFT, joystick.resolveSector(180f, JoystickSnapMode.ACTION_8WAY))
        // Up-Left: 202.5° to 247.5°
        assertEquals(VirtualJoystick.SECTOR_UP_LEFT, joystick.resolveSector(225f, JoystickSnapMode.ACTION_8WAY))
        // Up: 247.5° to 292.5°
        assertEquals(VirtualJoystick.SECTOR_UP, joystick.resolveSector(270f, JoystickSnapMode.ACTION_8WAY))
        // Up-Right: 292.5° to 337.5°
        assertEquals(VirtualJoystick.SECTOR_UP_RIGHT, joystick.resolveSector(315f, JoystickSnapMode.ACTION_8WAY))
    }

    @Test
    fun `detent crossed flags sector quadrant transitions and deadzone exits`() {
        val joystick = VirtualJoystick(baseCenterX = 100f, baseCenterY = 100f, deadzoneRadius = 10f, baseRadius = 50f)

        // 1. Initial touch inside deadzone
        val step1 = joystick.onDown(105f, 100f, id = 0, isFloating = false)
        assertFalse(step1.detentCrossed)

        // 2. Deflect out of deadzone into RIGHT sector -> detentCrossed is true!
        val step2 = joystick.update(130f, 100f)
        assertTrue(step2.detentCrossed)
        assertEquals(VirtualJoystick.SECTOR_RIGHT, step2.sector)

        // 3. Move further right within same sector -> detentCrossed is false
        val step3 = joystick.update(140f, 100f)
        assertFalse(step3.detentCrossed)

        // 4. Deflect down into DOWN sector -> detentCrossed is true!
        val step4 = joystick.update(100f, 140f)
        assertTrue(step4.detentCrossed)
        assertEquals(VirtualJoystick.SECTOR_DOWN, step4.sector)
    }

    @Test
    fun `dynamic floating mode anchors center to touch coordinates`() {
        val joystick = VirtualJoystick()

        joystick.onDown(touchX = 350f, touchY = 800f, id = 1, isFloating = true)
        assertEquals(350f, joystick.baseCenterX, 0.01f)
        assertEquals(800f, joystick.baseCenterY, 0.01f)
        assertTrue(joystick.isActive)

        // Move to (350, 760) -> Up
        val res = joystick.update(350f, 760f)
        assertEquals(RetroKey.KEY_UP, res.keyMask)
        assertEquals(VirtualJoystick.SECTOR_UP, res.sector)
    }
}
