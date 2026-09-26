package com.retropack.runtime.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RetroKeyTest {

    @Test
    fun `verify canonical hardware bit assignments match GBA register`() {
        assertEquals(0, RetroKey.A.bitIndex)
        assertEquals(1, RetroKey.B.bitIndex)
        assertEquals(2, RetroKey.SELECT.bitIndex)
        assertEquals(3, RetroKey.START.bitIndex)
        assertEquals(4, RetroKey.RIGHT.bitIndex)
        assertEquals(5, RetroKey.LEFT.bitIndex)
        assertEquals(6, RetroKey.UP.bitIndex)
        assertEquals(7, RetroKey.DOWN.bitIndex)
        assertEquals(8, RetroKey.R.bitIndex)
        assertEquals(9, RetroKey.L.bitIndex)

        assertEquals(1 shl 0, RetroKey.A.mask)
        assertEquals(1 shl 1, RetroKey.B.mask)
        assertEquals(1 shl 2, RetroKey.SELECT.mask)
        assertEquals(1 shl 3, RetroKey.START.mask)
        assertEquals(1 shl 4, RetroKey.RIGHT.mask)
        assertEquals(1 shl 5, RetroKey.LEFT.mask)
        assertEquals(1 shl 6, RetroKey.UP.mask)
        assertEquals(1 shl 7, RetroKey.DOWN.mask)
        assertEquals(1 shl 8, RetroKey.R.mask)
        assertEquals(1 shl 9, RetroKey.L.mask)
    }

    @Test
    fun `verify key mask combination and resolution`() {
        val maskAB = RetroKey.maskOf(RetroKey.A, RetroKey.B)
        assertEquals(0x03, maskAB)

        val keysResolved = RetroKey.fromMask(maskAB)
        assertEquals(setOf(RetroKey.A, RetroKey.B), keysResolved)

        val fullMask = RetroKey.maskOf(*RetroKey.entries.toTypedArray())
        assertEquals(RetroKey.ALL_KEYS_MASK, fullMask)
        assertEquals(RetroKey.GBA_KEYS_MASK, 1023)
        assertEquals(RetroKey.entries.toSet(), RetroKey.fromMask(fullMask))

        assertEquals(0, RetroKey.NO_KEYS_MASK)
        assertTrue(RetroKey.fromMask(RetroKey.NO_KEYS_MASK).isEmpty())
    }

    @Test
    fun `verify KeyMaskBuilder press, release and toggle mechanics`() {
        val builder = KeyMaskBuilder()
        assertEquals(0, builder.build())
        assertFalse(builder.isPressed(RetroKey.A))

        builder.press(RetroKey.A).press(RetroKey.START)
        assertTrue(builder.isPressed(RetroKey.A))
        assertTrue(builder.isPressed(RetroKey.START))
        assertFalse(builder.isPressed(RetroKey.B))
        assertEquals(RetroKey.A.mask or RetroKey.START.mask, builder.build())

        builder.release(RetroKey.A)
        assertFalse(builder.isPressed(RetroKey.A))
        assertTrue(builder.isPressed(RetroKey.START))
        assertEquals(RetroKey.START.mask, builder.build())

        builder.set(RetroKey.B, true)
        assertTrue(builder.isPressed(RetroKey.B))
        builder.set(RetroKey.B, false)
        assertFalse(builder.isPressed(RetroKey.B))

        builder.clear()
        assertEquals(0, builder.build())
        assertFalse(builder.isPressed(RetroKey.START))
    }

    @Test
    fun `verify KeyMaskBuilder clamps to all-keys hardware mask`() {
        val builder = KeyMaskBuilder(0xFFFFFFFF.toInt())
        assertEquals(RetroKey.ALL_KEYS_MASK, builder.build())
    }
}
