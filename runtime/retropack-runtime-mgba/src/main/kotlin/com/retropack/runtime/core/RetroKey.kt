package com.retropack.runtime.core

/**
 * Hardware controller button definitions matching the canonical Game Boy Advance / Game Boy hardware register order.
 *
 * Hardware Bit Mapping:
 * - Bit 0: A
 * - Bit 1: B
 * - Bit 2: SELECT
 * - Bit 3: START
 * - Bit 4: RIGHT
 * - Bit 5: LEFT
 * - Bit 6: UP
 * - Bit 7: DOWN
 * - Bit 8: R (Right Shoulder)
 * - Bit 9: L (Left Shoulder)
 */
enum class RetroKey(val bitIndex: Int) {
    A(0),
    B(1),
    SELECT(2),
    START(3),
    RIGHT(4),
    LEFT(5),
    UP(6),
    DOWN(7),
    R(8),
    L(9);

    /**
     * Integer bitmask representation of this key.
     */
    val mask: Int get() = 1 shl bitIndex

    companion object {
        const val KEY_A: Int = 1 shl 0
        const val KEY_B: Int = 1 shl 1
        const val KEY_SELECT: Int = 1 shl 2
        const val KEY_START: Int = 1 shl 3
        const val KEY_RIGHT: Int = 1 shl 4
        const val KEY_LEFT: Int = 1 shl 5
        const val KEY_UP: Int = 1 shl 6
        const val KEY_DOWN: Int = 1 shl 7
        const val KEY_R: Int = 1 shl 8
        const val KEY_L: Int = 1 shl 9

        /** Full 10-bit mask with all keys pressed. */
        const val ALL_KEYS_MASK: Int = 0x3FF

        /** Zero mask with no keys pressed. */
        const val NO_KEYS_MASK: Int = 0

        /**
         * Combines multiple [RetroKey] entries into a single integer bitmask.
         */
        fun maskOf(vararg keys: RetroKey): Int {
            var result = 0
            for (key in keys) {
                result = result or key.mask
            }
            return result
        }

        /**
         * Resolves all active [RetroKey] elements present in [mask].
         */
        fun fromMask(mask: Int): Set<RetroKey> {
            val result = mutableSetOf<RetroKey>()
            for (key in entries) {
                if ((mask and key.mask) != 0) {
                    result.add(key)
                }
            }
            return result
        }
    }
}

/**
 * Mutable builder and query helper for managing active input bitmasks.
 *
 * Supports thread-confined or synchronized key updates from virtual touch overlays
 * and physical HID gamepads.
 */
class KeyMaskBuilder(initialMask: Int = RetroKey.NO_KEYS_MASK) {
    private var currentMask: Int = initialMask and RetroKey.ALL_KEYS_MASK

    /**
     * Marks [key] as pressed in the bitmask.
     */
    fun press(key: RetroKey): KeyMaskBuilder = apply {
        currentMask = currentMask or key.mask
    }

    /**
     * Marks [key] as released in the bitmask.
     */
    fun release(key: RetroKey): KeyMaskBuilder = apply {
        currentMask = currentMask and key.mask.inv()
    }

    /**
     * Sets the state of [key] depending on [pressed].
     */
    fun set(key: RetroKey, pressed: Boolean): KeyMaskBuilder = apply {
        if (pressed) press(key) else release(key)
    }

    /**
     * Returns true if [key] is currently marked as pressed.
     */
    fun isPressed(key: RetroKey): Boolean = (currentMask and key.mask) != 0

    /**
     * Clears all pressed keys back to [RetroKey.NO_KEYS_MASK].
     */
    fun clear(): KeyMaskBuilder = apply {
        currentMask = RetroKey.NO_KEYS_MASK
    }

    /**
     * Returns the 10-bit composite integer bitmask.
     */
    fun build(): Int = currentMask
}
