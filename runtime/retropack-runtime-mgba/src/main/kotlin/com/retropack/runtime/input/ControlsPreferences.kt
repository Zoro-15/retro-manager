package com.retropack.runtime.input

import android.content.Context
import com.retropack.runtime.core.ScaleMode

/**
 * Persistence manager for user touch control preferences, normalized layout coordinates,
 * visual opacity, haptic feedback, and display scaling settings.
 */
object ControlsPreferences {
    const val PREFS_NAME = "retropack_controls_prefs"

    private const val KEY_PREFIX_POS = "pos_"
    private const val KEY_PREFIX_SCALE = "scale_"
    private const val KEY_TOUCH_OPACITY = "touch_opacity"
    private const val KEY_HAPTICS = "haptics_enabled"
    private const val KEY_SCALE_MODE = "scale_mode"
    private const val KEY_FAST_FORWARD_SPEED = "fast_forward_speed"
    private const val KEY_MUTE_FF_AUDIO = "mute_ff_audio"
    private const val KEY_TURBO_ENABLED = "turbo_enabled"
    private const val KEY_COMBO_MACRO_ENABLED = "combo_macro_enabled"
    private const val KEY_TOUCH_THEME = "touch_theme"
    private const val KEY_LCD_GRID = "lcd_grid_enabled"
    private const val KEY_GBA_COLOR = "gba_color_enabled"
    private const val KEY_BEZEL = "bezel_enabled"
    private const val KEY_HAS_CUSTOM_PORTRAIT = "has_custom_portrait"
    private const val KEY_HAS_CUSTOM_LANDSCAPE = "has_custom_landscape"

    /**
     * Persists normalized (0.0 to 1.0) cluster coordinates for portrait or landscape orientation.
     */
    fun saveLayoutPositions(
        context: Context,
        isLandscape: Boolean,
        positions: Map<String, Pair<Float, Float>>
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = prefs.edit()
        val orient = if (isLandscape) "land" else "port"

        for ((clusterId, pos) in positions) {
            editor.putFloat("${KEY_PREFIX_POS}${orient}_${clusterId}_x", pos.first)
            editor.putFloat("${KEY_PREFIX_POS}${orient}_${clusterId}_y", pos.second)
        }

        if (isLandscape) {
            editor.putBoolean(KEY_HAS_CUSTOM_LANDSCAPE, true)
        } else {
            editor.putBoolean(KEY_HAS_CUSTOM_PORTRAIT, true)
        }
        editor.apply()
    }

    /**
     * Persists custom cluster scale factors (0.5x to 2.0x) for portrait or landscape orientation.
     */
    fun saveLayoutScales(
        context: Context,
        isLandscape: Boolean,
        scales: Map<String, Float>
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = prefs.edit()
        val orient = if (isLandscape) "land" else "port"

        for ((clusterId, scale) in scales) {
            editor.putFloat("${KEY_PREFIX_SCALE}${orient}_${clusterId}", scale)
        }
        editor.apply()
    }

    /**
     * Loads custom normalized cluster coordinates if previously saved, or returns null if defaults should be used.
     */
    fun loadLayoutPositions(
        context: Context,
        isLandscape: Boolean
    ): Map<String, Pair<Float, Float>>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val orient = if (isLandscape) "land" else "port"
        val hasCustom = if (isLandscape) {
            prefs.getBoolean(KEY_HAS_CUSTOM_LANDSCAPE, false)
        } else {
            prefs.getBoolean(KEY_HAS_CUSTOM_PORTRAIT, false)
        }
        if (!hasCustom) return null

        val result = mutableMapOf<String, Pair<Float, Float>>()
        val clusterIds = listOf(
            TouchLayout.CLUSTER_DPAD,
            TouchLayout.CLUSTER_ACTION,
            TouchLayout.CLUSTER_SHOULDER_L,
            TouchLayout.CLUSTER_SHOULDER_R,
            TouchLayout.CLUSTER_SYSTEM
        )

        for (id in clusterIds) {
            val keyX = "${KEY_PREFIX_POS}${orient}_${id}_x"
            val keyY = "${KEY_PREFIX_POS}${orient}_${id}_y"
            if (prefs.contains(keyX) && prefs.contains(keyY)) {
                result[id] = Pair(prefs.getFloat(keyX, 0f), prefs.getFloat(keyY, 0f))
            }
        }
        return if (result.isNotEmpty()) result else null
    }

    /**
     * Loads custom cluster scale factors (0.5x to 2.0x) if previously saved.
     */
    fun loadLayoutScales(
        context: Context,
        isLandscape: Boolean
    ): Map<String, Float>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val orient = if (isLandscape) "land" else "port"

        val result = mutableMapOf<String, Float>()
        val clusterIds = listOf(
            TouchLayout.CLUSTER_DPAD,
            TouchLayout.CLUSTER_ACTION,
            TouchLayout.CLUSTER_SHOULDER_L,
            TouchLayout.CLUSTER_SHOULDER_R,
            TouchLayout.CLUSTER_SYSTEM
        )

        for (id in clusterIds) {
            val keyScale = "${KEY_PREFIX_SCALE}${orient}_${id}"
            if (prefs.contains(keyScale)) {
                result[id] = prefs.getFloat(keyScale, 1.0f)
            }
        }
        return if (result.isNotEmpty()) result else null
    }

    /**
     * Checks whether a custom layout has been saved for the given orientation.
     */
    fun hasCustomLayout(context: Context, isLandscape: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        return if (isLandscape) {
            prefs.getBoolean(KEY_HAS_CUSTOM_LANDSCAPE, false)
        } else {
            prefs.getBoolean(KEY_HAS_CUSTOM_PORTRAIT, false)
        }
    }

    /**
     * Clears custom layout coordinates and scales, resetting back to the canonical geometric default.
     */
    fun clearCustomLayout(context: Context, isLandscape: Boolean? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = prefs.edit()
        val allClusterIds = listOf(
            TouchLayout.CLUSTER_DPAD,
            TouchLayout.CLUSTER_ACTION,
            TouchLayout.CLUSTER_SHOULDER_L,
            TouchLayout.CLUSTER_SHOULDER_R,
            TouchLayout.CLUSTER_SYSTEM
        )

        if (isLandscape == null || !isLandscape) {
            editor.putBoolean(KEY_HAS_CUSTOM_PORTRAIT, false)
            for (id in allClusterIds) {
                editor.remove("${KEY_PREFIX_POS}port_${id}_x")
                editor.remove("${KEY_PREFIX_POS}port_${id}_y")
                editor.remove("${KEY_PREFIX_SCALE}port_${id}")
            }
        }

        if (isLandscape == null || isLandscape) {
            editor.putBoolean(KEY_HAS_CUSTOM_LANDSCAPE, false)
            for (id in allClusterIds) {
                editor.remove("${KEY_PREFIX_POS}land_${id}_x")
                editor.remove("${KEY_PREFIX_POS}land_${id}_y")
                editor.remove("${KEY_PREFIX_SCALE}land_${id}")
            }
        }

        editor.apply()
    }

    /**
     * Persists virtual control touch opacity (0.0 to 1.0).
     */
    fun saveOpacity(context: Context, opacity: Float) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putFloat(KEY_TOUCH_OPACITY, opacity.coerceIn(0.0f, 1.0f))
            .apply()
    }

    /**
     * Loads virtual control touch opacity, falling back to [defaultOpacity].
     */
    fun loadOpacity(context: Context, defaultOpacity: Float): Float {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getFloat(KEY_TOUCH_OPACITY, defaultOpacity)
    }

    /**
     * Persists haptic feedback toggle state.
     */
    fun saveHaptics(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_HAPTICS, enabled)
            .apply()
    }

    /**
     * Loads haptic feedback toggle state, falling back to [defaultHaptics].
     */
    fun loadHaptics(context: Context, defaultHaptics: Boolean): Boolean {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getBoolean(KEY_HAPTICS, defaultHaptics)
    }

    /**
     * Persists OpenGL video scale mode.
     */
    fun saveScaleMode(context: Context, scaleMode: ScaleMode) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putString(KEY_SCALE_MODE, scaleMode.name)
            .apply()
    }

    /**
     * Loads OpenGL video scale mode, falling back to [defaultScaleMode].
     */
    fun loadScaleMode(context: Context, defaultScaleMode: ScaleMode): ScaleMode {
        val name = context.getSharedPreferences(PREFS_NAME, 0)
            .getString(KEY_SCALE_MODE, defaultScaleMode.name)
        return try {
            if (name != null) ScaleMode.valueOf(name) else defaultScaleMode
        } catch (_: Exception) {
            defaultScaleMode
        }
    }

    /**
     * Persists fast-forward emulation speed multiplier (1, 2, 4, 8, 16).
     */
    fun saveFastForwardSpeed(context: Context, speed: Int) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putInt(KEY_FAST_FORWARD_SPEED, speed)
            .apply()
    }

    /**
     * Loads fast-forward emulation speed multiplier.
     */
    fun loadFastForwardSpeed(context: Context, defaultSpeed: Int = 1): Int {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getInt(KEY_FAST_FORWARD_SPEED, defaultSpeed)
    }

    /**
     * Persists mute audio during fast-forward preference.
     */
    fun saveMuteAudioOnFastForward(context: Context, mute: Boolean) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_MUTE_FF_AUDIO, mute)
            .apply()
    }

    /**
     * Loads mute audio during fast-forward preference.
     */
    fun loadMuteAudioOnFastForward(context: Context, defaultMute: Boolean = true): Boolean {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getBoolean(KEY_MUTE_FF_AUDIO, defaultMute)
    }

    /**
     * Persists turbo buttons preference.
     */
    fun saveTurboEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_TURBO_ENABLED, enabled)
            .apply()
    }

    /**
     * Loads turbo buttons preference.
     */
    fun loadTurboEnabled(context: Context, defaultEnabled: Boolean = false): Boolean {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getBoolean(KEY_TURBO_ENABLED, defaultEnabled)
    }

    /**
     * Persists A+B combo macro pill toggle.
     */
    fun saveComboMacroEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_COMBO_MACRO_ENABLED, enabled)
            .apply()
    }

    /**
     * Loads A+B combo macro pill toggle.
     */
    fun loadComboMacroEnabled(context: Context, defaultEnabled: Boolean = false): Boolean {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getBoolean(KEY_COMBO_MACRO_ENABLED, defaultEnabled)
    }

    /**
     * Persists touch controls theme skin name.
     */
    fun saveTouchTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putString(KEY_TOUCH_THEME, theme)
            .apply()
    }

    /**
     * Loads touch controls theme skin name.
     */
    fun loadTouchTheme(context: Context, defaultTheme: String = "neon"): String {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getString(KEY_TOUCH_THEME, defaultTheme) ?: defaultTheme
    }

    /**
     * Persists LCD grid shader toggle.
     */
    fun saveLcdGridEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_LCD_GRID, enabled)
            .apply()
    }

    /**
     * Loads LCD grid shader toggle.
     */
    fun loadLcdGridEnabled(context: Context, defaultEnabled: Boolean = false): Boolean {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getBoolean(KEY_LCD_GRID, defaultEnabled)
    }

    /**
     * Persists GBA color correction shader toggle.
     */
    fun saveGbaColorCorrectionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_GBA_COLOR, enabled)
            .apply()
    }

    /**
     * Loads GBA color correction shader toggle.
     */
    fun loadGbaColorCorrectionEnabled(context: Context, defaultEnabled: Boolean = false): Boolean {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getBoolean(KEY_GBA_COLOR, defaultEnabled)
    }

    /**
     * Persists screen bezel overlay toggle.
     */
    fun saveBezelEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_BEZEL, enabled)
            .apply()
    }

    /**
     * Loads screen bezel overlay toggle.
     */
    fun loadBezelEnabled(context: Context, defaultEnabled: Boolean = false): Boolean {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getBoolean(KEY_BEZEL, defaultEnabled)
    }

    /**
     * Resets all RetroPack controls preferences back to factory defaults.
     */
    fun resetAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .clear()
            .apply()
    }
}
