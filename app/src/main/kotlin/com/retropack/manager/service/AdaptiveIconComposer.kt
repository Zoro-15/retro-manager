package com.retropack.manager.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import java.io.ByteArrayOutputStream

/**
 * Smart Android Adaptive Icon Generator & Compositor.
 *
 * Implements Feature 5 specifications:
 * - Generates Android Adaptive Icon layers (108 x 108 dp canvas with 72 dp inner safe zone).
 * - Background Layer: Extracts dominant vibrant color from scraped boxart and renders a dark radial vignette gradient.
 * - Foreground Layer: Centers boxart in inner 72 dp safe zone with 12px rounded corners and 3D drop shadow.
 * - Composites launcher shape preview masks (Squircle, Circle, Rounded Rectangle).
 */
object AdaptiveIconComposer {

    const val CANVAS_SIZE = 432 // 108dp @ 4x (xxxhdpi)
    const val SAFE_ZONE_SIZE = 288 // 72dp @ 4x (xxxhdpi)
    const val CORNER_RADIUS = 24f // 6dp @ 4x
    const val SHADOW_OFFSET_Y = 16f
    const val SHADOW_BLUR = 24f

    enum class MaskShape {
        SQUIRCLE,
        CIRCLE,
        ROUNDED_RECTANGLE
    }

    data class AdaptiveIconResult(
        val foregroundPng: ByteArray,
        val backgroundPng: ByteArray,
        val compositePng: ByteArray,
        val previewBitmap: Bitmap,
        val dominantColor: Int
    )

    /**
     * Synthesizes adaptive icon layers from raw boxart image bytes.
     */
    fun composeAdaptiveIcon(boxartBytes: ByteArray): AdaptiveIconResult? {
        return try {
            val sourceBitmap = BitmapFactory.decodeByteArray(boxartBytes, 0, boxartBytes.size) ?: return null
            val dominantColor = extractDominantColor(sourceBitmap)

            val bgBitmap = generateBackgroundLayer(dominantColor)
            val fgBitmap = generateForegroundLayer(sourceBitmap)
            val preview = generateCompositePreview(bgBitmap, fgBitmap, MaskShape.SQUIRCLE)

            val bgStream = ByteArrayOutputStream()
            bgBitmap.compress(Bitmap.CompressFormat.PNG, 100, bgStream)

            val fgStream = ByteArrayOutputStream()
            fgBitmap.compress(Bitmap.CompressFormat.PNG, 100, fgStream)

            val compositeBitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
            val compositeCanvas = Canvas(compositeBitmap)
            compositeCanvas.drawBitmap(bgBitmap, 0f, 0f, null)
            compositeCanvas.drawBitmap(fgBitmap, 0f, 0f, null)
            val compositeStream = ByteArrayOutputStream()
            compositeBitmap.compress(Bitmap.CompressFormat.PNG, 100, compositeStream)

            AdaptiveIconResult(
                foregroundPng = fgStream.toByteArray(),
                backgroundPng = bgStream.toByteArray(),
                compositePng = compositeStream.toByteArray(),
                previewBitmap = preview,
                dominantColor = dominantColor
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a dark radial vignette background layer tinted with dominant color.
     */
    fun generateBackgroundLayer(dominantColor: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val r = Color.red(dominantColor)
        val g = Color.green(dominantColor)
        val b = Color.blue(dominantColor)

        // Darkened saturated center color
        val centerColor = Color.rgb(
            (r * 0.45f).toInt().coerceIn(12, 120),
            (g * 0.45f).toInt().coerceIn(12, 120),
            (b * 0.45f).toInt().coerceIn(12, 120)
        )
        // Deep black edge
        val edgeColor = Color.rgb(
            (r * 0.08f).toInt().coerceIn(6, 30),
            (g * 0.08f).toInt().coerceIn(6, 30),
            (b * 0.08f).toInt().coerceIn(6, 30)
        )

        val gradient = RadialGradient(
            CANVAS_SIZE / 2f,
            CANVAS_SIZE / 2f,
            CANVAS_SIZE * 0.65f,
            centerColor,
            edgeColor,
            Shader.TileMode.CLAMP
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }
        canvas.drawRect(0f, 0f, CANVAS_SIZE.toFloat(), CANVAS_SIZE.toFloat(), paint)

        return bitmap
    }

    /**
     * Generates a centered foreground layer with 12px rounded corners and 3D drop shadow.
     */
    fun generateForegroundLayer(sourceBitmap: Bitmap): Bitmap {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val srcW = sourceBitmap.width.toFloat()
        val srcH = sourceBitmap.height.toFloat()

        // Calculate aspect-fit bounds within safe zone
        val maxDim = (SAFE_ZONE_SIZE * 0.88f)
        val scale = Math.min(maxDim / srcW, maxDim / srcH)
        val targetW = srcW * scale
        val targetH = srcH * scale

        val left = (CANVAS_SIZE - targetW) / 2f
        val top = (CANVAS_SIZE - targetH) / 2f
        val rect = RectF(left, top, left + targetW, top + targetH)

        // 1. Draw 3D Drop Shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 0, 0, 0)
        }
        val shadowRect = RectF(
            rect.left,
            rect.top + SHADOW_OFFSET_Y,
            rect.right,
            rect.bottom + SHADOW_OFFSET_Y
        )
        canvas.drawRoundRect(shadowRect, CORNER_RADIUS, CORNER_RADIUS, shadowPaint)

        // 2. Draw Rounded Boxart
        val roundedBoxart = Bitmap.createBitmap(targetW.toInt(), targetH.toInt(), Bitmap.Config.ARGB_8888)
        val boxartCanvas = Canvas(roundedBoxart)
        val roundPath = Path().apply {
            addRoundRect(RectF(0f, 0f, targetW, targetH), CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CW)
        }
        boxartCanvas.clipPath(roundPath)
        boxartCanvas.drawBitmap(sourceBitmap, null, Rect(0, 0, targetW.toInt(), targetH.toInt()), Paint(Paint.FILTER_BITMAP_FLAG))

        canvas.drawBitmap(roundedBoxart, left, top, Paint(Paint.ANTI_ALIAS_FLAG))

        return bitmap
    }

    /**
     * Renders a masked composite preview (Squircle, Circle, Rounded Rectangle).
     */
    fun generateCompositePreview(
        bgBitmap: Bitmap,
        fgBitmap: Bitmap,
        shape: MaskShape
    ): Bitmap {
        val preview = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview)

        // Draw composite
        canvas.drawBitmap(bgBitmap, 0f, 0f, null)
        canvas.drawBitmap(fgBitmap, 0f, 0f, null)

        // Mask shape
        val maskBitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBitmap)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

        val rect = RectF(0f, 0f, CANVAS_SIZE.toFloat(), CANVAS_SIZE.toFloat())
        when (shape) {
            MaskShape.CIRCLE -> {
                maskCanvas.drawCircle(CANVAS_SIZE / 2f, CANVAS_SIZE / 2f, CANVAS_SIZE / 2f, maskPaint)
            }
            MaskShape.SQUIRCLE -> {
                maskCanvas.drawRoundRect(rect, 100f, 100f, maskPaint)
            }
            MaskShape.ROUNDED_RECTANGLE -> {
                maskCanvas.drawRoundRect(rect, 48f, 48f, maskPaint)
            }
        }

        val result = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val resultCanvas = Canvas(result)
        resultCanvas.drawBitmap(preview, 0f, 0f, null)

        val xferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        resultCanvas.drawBitmap(maskBitmap, 0f, 0f, xferPaint)

        return result
    }

    /**
     * Extracts the dominant vibrant color from an image bitmap.
     */
    fun extractDominantColor(bitmap: Bitmap): Int {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L

        val stepX = Math.max(1, bitmap.width / 20)
        val stepY = Math.max(1, bitmap.height / 20)

        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val a = Color.alpha(pixel)
                if (a > 128) {
                    rSum += Color.red(pixel)
                    gSum += Color.green(pixel)
                    bSum += Color.blue(pixel)
                    count++
                }
            }
        }

        if (count == 0L) return Color.rgb(40, 80, 160)
        val avgR = (rSum / count).toInt().coerceIn(0, 255)
        val avgG = (gSum / count).toInt().coerceIn(0, 255)
        val avgB = (bSum / count).toInt().coerceIn(0, 255)

        return Color.rgb(avgR, avgG, avgB)
    }
}
