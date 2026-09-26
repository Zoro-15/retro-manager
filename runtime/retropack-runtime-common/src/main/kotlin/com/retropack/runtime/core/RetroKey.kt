package com.retropack.runtime.core

/**
 * Universal hardware controller button definitions supporting multi-platform retro cores:
 * - GBA / GBC / GB (10 keys)
 * - SNES (12 keys: A, B, X, Y, L, R, Select, Start, D-Pad)
 * - Sega Genesis (10 keys: A, B, C, X, Y, Z, Start, Mode, D-Pad)
 * - NES (8 keys: A, B, Select, Start, D-Pad)
 * - PS1 (14 keys: Cross, Circle, Square, Triangle, L1, R1, L2, R2, Select, Start, D-Pad)
 * - N64 (14 keys: A, B, Z, Start, L, R, C-Up/Down/Left/Right, D-Pad)
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
    R(8),       // GBA R / SNES R / PS1 R1
    L(9),       // GBA L / SNES L / PS1 L1
    X(10),      // SNES X / PS1 Triangle / Genesis X
    Y(11),      // SNES Y / PS1 Square / Genesis Y
    C(12),      // Genesis C
    Z(13),      // Genesis Z / N64 Z
    L2(14),     // PS1 L2
    R2(15),     // PS1 R2
    L3(16),     // PS1 L3 (Thumbstick click)
    R3(17),     // PS1 R3 (Thumbstick click)
    MODE(18),   // Genesis Mode
    C_UP(19),   // N64 C-Up
    C_DOWN(20), // N64 C-Down
    C_LEFT(21), // N64 C-Left
    C_RIGHT(22);// N64 C-Right

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
        const val KEY_X: Int = 1 shl 10
        const val KEY_Y: Int = 1 shl 11
        const val KEY_C: Int = 1 shl 12
        const val KEY_Z: Int = 1 shl 13
        const val KEY_L2: Int = 1 shl 14
        const val KEY_R2: Int = 1 shl 15
        const val KEY_L3: Int = 1 shl 16
        const val KEY_R3: Int = 1 shl 17
        const val KEY_MODE: Int = 1 shl 18
        const val KEY_C_UP: Int = 1 shl 19
        const val KEY_C_DOWN: Int = 1 shl 20
        const val KEY_C_LEFT: Int = 1 shl 21
        const val KEY_C_RIGHT: Int = 1 shl 22

        /** Full 10-bit mask for classic GBA/GB. */
        const val GBA_KEYS_MASK: Int = 0x3FF
        const val ALL_KEYS_MASK: Int = 0x7FFFFF

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
 */
class KeyMaskBuilder(initialMask: Int = RetroKey.NO_KEYS_MASK) {
    private var currentMask: Int = initialMask and RetroKey.ALL_KEYS_MASK

    fun press(key: RetroKey): KeyMaskBuilder = apply {
        currentMask = currentMask or key.mask
    }

    fun release(key: RetroKey): KeyMaskBuilder = apply {
        currentMask = currentMask and key.mask.inv()
    }

    fun set(key: RetroKey, pressed: Boolean): KeyMaskBuilder = apply {
        if (pressed) press(key) else release(key)
    }

    fun isPressed(key: RetroKey): Boolean = (currentMask and key.mask) != 0

    fun clear(): KeyMaskBuilder = apply {
        currentMask = RetroKey.NO_KEYS_MASK
    }

    fun build(): Int = currentMask
}
