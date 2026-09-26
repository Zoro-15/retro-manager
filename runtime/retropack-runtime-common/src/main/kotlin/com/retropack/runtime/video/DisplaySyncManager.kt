package com.retropack.runtime.video

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Window
import android.view.WindowManager
import com.retropack.runtime.logging.RuntimeLogger

/**
 * Dynamic Device-Adaptive Variable Refresh Rate (VRR / 90Hz / 120Hz / 144Hz) & Motion Sync Manager.
 *
 * Implements Feature 3 specifications:
 * - Dynamically discovers device physical display modes (60Hz, 90Hz, 120Hz, 144Hz, 165Hz).
 * - Synchronizes frame presentation with exact integer display cycles:
 *   * 120 Hz displays: Flips 60 FPS emulation frame exactly twice (2 vsync ticks/frame),
 *     eliminating 3:2 pull-down micro-stutters and uneven frame pacing.
 *   * 144 Hz / 90 Hz VRR displays: Dynamically locks vsync interval via Surface.setFrameRate() (Android 11+ / API 30+).
 *   * 60 Hz displays: Preserves native 60 Hz display timing with zero CPU overhead.
 * - Black Frame Insertion (BFI) motion clarity coordination for 120Hz+ displays.
 */
object DisplaySyncManager {

    data class DisplayCapabilities(
        val currentRefreshRate: Float,
        val supportedRefreshRates: List<Float>,
        val isHighRefreshRateSupported: Boolean,
        val is120HzSupported: Boolean,
        val isVrrSupported: Boolean,
        val recommendedRefreshRate: Float
    )

    /**
     * Inspects physical display capabilities of the host Android device.
     */
    fun queryDisplayCapabilities(context: Context): DisplayCapabilities {
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    context.display ?: wm?.defaultDisplay
                } catch (_: Throwable) {
                    @Suppress("DEPRECATION")
                    wm?.defaultDisplay
                }
            } else {
                @Suppress("DEPRECATION")
                wm?.defaultDisplay
            }

            if (display == null) {
                return DisplayCapabilities(
                    currentRefreshRate = 60.0f,
                    supportedRefreshRates = listOf(60.0f),
                    isHighRefreshRateSupported = false,
                    is120HzSupported = false,
                    isVrrSupported = false,
                    recommendedRefreshRate = 60.0f
                )
            }

            @Suppress("DEPRECATION")
            val currentRate = display.refreshRate

            val rates = mutableListOf<Float>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val modes = display.supportedModes
                if (modes != null) {
                    for (mode in modes) {
                        rates.add(mode.refreshRate)
                    }
                }
            }
            if (rates.isEmpty()) {
                rates.add(currentRate)
            }

            val distinctRates = rates.map { Math.round(it * 10.0f) / 10.0f }.distinct().sorted()
            val hasHighRate = distinctRates.any { it >= 89.0f }
            val has120Hz = distinctRates.any { it in 119.0f..121.0f }
            val isVrr = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasHighRate

            val recommended = if (has120Hz) 120.0f else (distinctRates.maxOrNull() ?: 60.0f)

            DisplayCapabilities(
                currentRefreshRate = currentRate,
                supportedRefreshRates = distinctRates,
                isHighRefreshRateSupported = hasHighRate,
                is120HzSupported = has120Hz,
                isVrrSupported = isVrr,
                recommendedRefreshRate = recommended
            )
        } catch (e: Throwable) {
            RuntimeLogger.w("DisplaySync", "Failed to query display capabilities: ${e.message}")
            DisplayCapabilities(
                currentRefreshRate = 60.0f,
                supportedRefreshRates = listOf(60.0f),
                isHighRefreshRateSupported = false,
                is120HzSupported = false,
                isVrrSupported = false,
                recommendedRefreshRate = 60.0f
            )
        }
    }

    /**
     * Configures the Activity Window refresh rate and Surface frame-rate timing.
     */
    fun configureDisplaySync(
        activity: Activity,
        surfaceView: SurfaceView?,
        vrrEnabled: Boolean = true,
        bfiEnabled: Boolean = false,
        targetFps: Float = 60.0f
    ) {
        val caps = queryDisplayCapabilities(activity)
        RuntimeLogger.i("DisplaySync", "Display detected: current=${caps.currentRefreshRate}Hz, supported=${caps.supportedRefreshRates}, 120Hz=${caps.is120HzSupported}")

        // 1. Window Mode negotiation
        try {
            val window = activity.window
            val lp = window.attributes

            if (bfiEnabled && caps.is120HzSupported) {
                // Request 120Hz display refresh for Black Frame Insertion cadence
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val mode120 = findModeForRate(activity, 120.0f)
                    if (mode120 != 0) {
                        lp.preferredDisplayModeId = mode120
                    }
                }
                lp.preferredRefreshRate = 120.0f
                window.attributes = lp
                RuntimeLogger.i("DisplaySync", "Locked Window preferredRefreshRate to 120.0Hz for Black Frame Insertion")
            } else if (vrrEnabled && caps.isHighRefreshRateSupported) {
                // Integer cadence optimization: 120Hz screen flips 60 FPS frames with 2:2 pulldown
                if (caps.is120HzSupported) {
                    lp.preferredRefreshRate = 120.0f
                    window.attributes = lp
                }
            }
        } catch (e: Throwable) {
            RuntimeLogger.w("DisplaySync", "Window refresh rate configuration skipped: ${e.message}")
        }

        // 2. Surface Frame Rate synchronization (Android 11+ / API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && surfaceView != null) {
            try {
                val surface = surfaceView.holder.surface
                if (surface != null && surface.isValid) {
                    val effectiveTarget = if (bfiEnabled && caps.is120HzSupported) 120.0f else targetFps
                    surface.setFrameRate(
                        effectiveTarget,
                        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
                    )
                    RuntimeLogger.i("DisplaySync", "Surface.setFrameRate configured to ${effectiveTarget}fps (FRAME_RATE_COMPATIBILITY_DEFAULT)")
                }
            } catch (e: Throwable) {
                RuntimeLogger.w("DisplaySync", "Surface.setFrameRate skipped: ${e.message}")
            }
        }
    }

    private fun findModeForRate(context: Context, targetRate: Float): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return 0
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val display = wm?.defaultDisplay ?: return 0
            val modes = display.supportedModes ?: return 0
            val match = modes.firstOrNull { Math.abs(it.refreshRate - targetRate) < 1.0f }
            match?.modeId ?: 0
        } catch (_: Throwable) {
            0
        }
    }
}
