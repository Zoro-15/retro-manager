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
    private const val KEY_TOUCH_OPACITY = "touch_opacity"
    private const val KEY_HAPTICS = "haptics_enabled"
    private const val KEY_SCALE_MODE = "scale_mode"
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
     * Clears custom layout coordinates, resetting back to the canonical geometric default.
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
            }
        }

        if (isLandscape == null || isLandscape) {
            editor.putBoolean(KEY_HAS_CUSTOM_LANDSCAPE, false)
            for (id in allClusterIds) {
                editor.remove("${KEY_PREFIX_POS}land_${id}_x")
                editor.remove("${KEY_PREFIX_POS}land_${id}_y")
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
     * Resets all RetroPack controls preferences back to factory defaults.
     */
    fun resetAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .clear()
            .apply()
    }
}
