package com.retropack.runtime.video

import com.retropack.domain.model.BezelMode
import com.retropack.runtime.core.ScaleMode

/**
 * Responsive Bezel Placement & Cutout Calibration Calculator for RetroPack.
 *
 * Implements Feature 6 specifications:
 * - JSON/data-driven coordinate definitions specifying exact viewport bounding boxes (x, y, width, height)
 * - Dynamic adaptation between Landscape and Portrait phone screen aspect ratios
 * - Precise screen lens chamfer and inner shadow margins
 */
object BezelConfig {

    data class BezelCutout(
        val viewportX: Int,
        val viewportY: Int,
        val viewportWidth: Int,
        val viewportHeight: Int,
        val bezelPaddingLeft: Float,
        val bezelPaddingTop: Float,
        val bezelPaddingRight: Float,
        val bezelPaddingBottom: Float,
        val lensBorderRadius: Float = 8f,
        val showGlassGlare: Boolean = true,
        val showPowerLed: Boolean = true,
        val primaryColorHex: Long = 0xFF2A2E3D
    )

    /**
     * Resolves the active bezel mode when set to [BezelMode.AUTO], using the ROM's platform architecture.
     */
    fun resolveAutoBezel(platform: String): BezelMode {
        val norm = platform.lowercase().trim().removePrefix(".")
        return when {
            norm.contains("gba") || norm.contains("advance") -> BezelMode.GBA_INDIGO
            norm.contains("gbc") || norm.contains("color") -> BezelMode.GBC_ATOMIC_PURPLE
            norm.contains("gb") || norm.contains("gameboy") -> BezelMode.DMG_RETRO_OFFWHITE
            norm.contains("snes") || norm.contains("sfc") || norm.contains("genesis") ||
            norm.contains("nes") || norm.contains("psx") || norm.contains("ps1") ||
            norm.contains("n64") || norm.contains("pce") || norm.contains("arcade") -> BezelMode.CRT_TRINITRON_90S
            else -> BezelMode.GBA_INDIGO
        }
    }

    /**
     * Calculates the calibrated inner game screen cutout rectangle for the given screen dimensions.
     */
    fun calculateCutout(
        surfaceWidth: Int,
        surfaceHeight: Int,
        nativeWidth: Int,
        nativeHeight: Int,
        bezelMode: BezelMode,
        scaleMode: ScaleMode = ScaleMode.ASPECT_FIT
    ): BezelCutout {
        val isLandscape = surfaceWidth > surfaceHeight

        if (bezelMode.isOledBlack) {
            val baseRect = scaleMode.calculateViewport(surfaceWidth, surfaceHeight, nativeWidth, nativeHeight)
            return BezelCutout(
                viewportX = baseRect.x,
                viewportY = baseRect.y,
                viewportWidth = baseRect.width,
                viewportHeight = baseRect.height,
                bezelPaddingLeft = 0f,
                bezelPaddingTop = 0f,
                bezelPaddingRight = 0f,
                bezelPaddingBottom = 0f,
                lensBorderRadius = 4f,
                showGlassGlare = false,
                showPowerLed = false,
                primaryColorHex = 0xFF000000
            )
        }

        // Apply bezel inset margins depending on orientation
        val horizontalMarginRatio = if (isLandscape) 0.12f else 0.04f
        val verticalMarginRatio = if (isLandscape) 0.06f else 0.22f

        val availableW = (surfaceWidth * (1.0f - horizontalMarginRatio * 2)).toInt().coerceAtLeast(1)
        val availableH = (surfaceHeight * (1.0f - verticalMarginRatio * 2)).toInt().coerceAtLeast(1)

        val innerRect = scaleMode.calculateViewport(availableW, availableH, nativeWidth, nativeHeight)
        val offsetX = ((surfaceWidth - availableW) / 2) + innerRect.x
        val offsetY = ((surfaceHeight - availableH) / 2) + innerRect.y

        val primaryColor = when (bezelMode) {
            BezelMode.GBA_INDIGO -> 0xFF3F466E
            BezelMode.GBA_SP_PLATINUM -> 0xFF8E95A5
            BezelMode.DMG_RETRO_OFFWHITE -> 0xFFC8C4B7
            BezelMode.GBC_ATOMIC_PURPLE -> 0xDD563878
            BezelMode.CRT_TRINITRON_90S -> 0xFF1C1E24
            else -> 0xFF2A2E3D
        }

        return BezelCutout(
            viewportX = offsetX,
            viewportY = offsetY,
            viewportWidth = innerRect.width,
            viewportHeight = innerRect.height,
            bezelPaddingLeft = offsetX.toFloat(),
            bezelPaddingTop = offsetY.toFloat(),
            bezelPaddingRight = (surfaceWidth - (offsetX + innerRect.width)).toFloat(),
            bezelPaddingBottom = (surfaceHeight - (offsetY + innerRect.height)).toFloat(),
            lensBorderRadius = if (bezelMode == BezelMode.CRT_TRINITRON_90S) 16f else 8f,
            showGlassGlare = true,
            showPowerLed = true,
            primaryColorHex = primaryColor
        )
    }
}
