package com.retropack.runtime.core

import kotlin.math.max
import kotlin.math.min

/**
 * Display scaling modes supported by the RetroPack OpenGL ES surface renderer.
 *
 * References:
 * - masterplan.md Section 5.4: "supports integer_fit (pixel-perfect square scaling) and aspect_fit with bilinear filter."
 * - architechture.md Contract 2: "video_scale_mode": "integer_fit"
 */
enum class ScaleMode(val configValue: String, val displayName: String) {
    /**
     * Pixel-perfect integer square scaling (1x, 2x, 3x, 4x...) with nearest-neighbor sampling.
     * Prevents pixel shimmering and irregular scanline widths.
     */
    INTEGER_FIT("integer_fit", "Integer Scale (Pixel Perfect)"),

    /**
     * Scales the frame to the maximum bounds preserving original aspect ratio (3:2 for GBA, 10:9 for GB/GBC).
     * Employs bilinear texture filtering to prevent aliasing.
     */
    ASPECT_FIT("aspect_fit", "Aspect Ratio Fit (Smooth)");

    /**
     * Represents the computed target viewport rectangle within a display surface.
     */
    data class ViewportRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val scaleFactor: Float
    )

    /**
     * Calculates the centered viewport coordinates and dimensions for rendering a frame of
     * ([nativeWidth] x [nativeHeight]) onto a surface of ([surfaceWidth] x [surfaceHeight]).
     */
    fun calculateViewport(
        surfaceWidth: Int,
        surfaceHeight: Int,
        nativeWidth: Int,
        nativeHeight: Int
    ): ViewportRect {
        require(surfaceWidth > 0 && surfaceHeight > 0) { "Surface dimensions must be positive" }
        require(nativeWidth > 0 && nativeHeight > 0) { "Native dimensions must be positive" }

        return when (this) {
            INTEGER_FIT -> {
                val scaleX = surfaceWidth / nativeWidth
                val scaleY = surfaceHeight / nativeHeight
                val integerScale = max(1, min(scaleX, scaleY))

                val vpWidth = nativeWidth * integerScale
                val vpHeight = nativeHeight * integerScale
                val offsetX = (surfaceWidth - vpWidth) / 2
                val offsetY = (surfaceHeight - vpHeight) / 2

                ViewportRect(
                    x = offsetX,
                    y = offsetY,
                    width = vpWidth,
                    height = vpHeight,
                    scaleFactor = integerScale.toFloat()
                )
            }
            ASPECT_FIT -> {
                val scaleX = surfaceWidth.toFloat() / nativeWidth.toFloat()
                val scaleY = surfaceHeight.toFloat() / nativeHeight.toFloat()
                val floatScale = min(scaleX, scaleY)

                val vpWidth = (nativeWidth * floatScale).toInt()
                val vpHeight = (nativeHeight * floatScale).toInt()
                val offsetX = (surfaceWidth - vpWidth) / 2
                val offsetY = (surfaceHeight - vpHeight) / 2

                ViewportRect(
                    x = offsetX,
                    y = offsetY,
                    width = vpWidth,
                    height = vpHeight,
                    scaleFactor = floatScale
                )
            }
        }
    }

    companion object {
        /**
         * Resolves a [ScaleMode] from the string value stored in `retropack.json`.
         * Defaults to [INTEGER_FIT] if null or unrecognized.
         */
        fun fromConfig(value: String?): ScaleMode {
            if (value == null) return INTEGER_FIT
            return entries.firstOrNull { it.configValue.equals(value.trim(), ignoreCase = true) }
                ?: INTEGER_FIT
        }
    }
}
