package com.retropack.domain.model

/**
 * Haptic Feedback and Rumble operational modes for RetroPack.
 *
 * Implements Feature 2 specifications:
 * - [NATIVE_RUMBLE]: Emulates authentic motor vibration for rumble-enabled cartridges (e.g. GBA Drill Dozer).
 * - [AUDIO_REACTIVE]: Real-time sub-bass transient frequency analyzer (< 120Hz) for classic consoles (NES, SNES, Genesis, Game Boy, PC Engine).
 * - [OFF]: Haptic rumble disabled.
 */
enum class HapticFeedbackMode(val displayName: String) {
    NATIVE_RUMBLE("Native Rumble"),
    AUDIO_REACTIVE("Audio-Reactive"),
    OFF("Off");

    companion object {
        fun fromString(value: String?): HapticFeedbackMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true) }
                ?: AUDIO_REACTIVE
        }
    }
}
