package com.retropack.manager.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.retropack.manager.service.AdaptiveIconComposer
import java.io.ByteArrayOutputStream

object IconSynthesizer {

    private const val ICON_SIZE = 512
    private const val FOREGROUND_INSET_RATIO = 0.20f // 20% margin for safe adaptive icon mask

    data class IconLayers(
        val foregroundBytes: ByteArray,
        val backgroundBytes: ByteArray,
        val rasterBytes: ByteArray
    )

    /**
     * Synthesizes foreground, background, and composited fallback raster icon PNG bytes
     * from an arbitrary user boxart image.
     */
    fun synthesizeLayers(sourceBytes: ByteArray): IconLayers? {
        val result = AdaptiveIconComposer.composeAdaptiveIcon(sourceBytes)
        if (result != null) {
            return IconLayers(result.foregroundPng, result.backgroundPng, result.compositePng)
        }
        return try {
            val originalBitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return null

            // 1. Synthesize Foreground Layer (512x512 with safe margin)
            val fgBitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            val fgCanvas = Canvas(fgBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val inset = (ICON_SIZE * FOREGROUND_INSET_RATIO).toInt()
            val targetWidth = ICON_SIZE - (inset * 2)
            val targetHeight = ICON_SIZE - (inset * 2)

            val srcAspect = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
            val dstAspect = targetWidth.toFloat() / targetHeight.toFloat()

            val drawWidth: Float
            val drawHeight: Float
            if (srcAspect > dstAspect) {
                drawWidth = targetWidth.toFloat()
                drawHeight = targetWidth.toFloat() / srcAspect
            } else {
                drawHeight = targetHeight.toFloat()
                drawWidth = targetHeight.toFloat() * srcAspect
            }

            val left = inset + (targetWidth - drawWidth) / 2f
            val top = inset + (targetHeight - drawHeight) / 2f
            val destRect = RectF(left, top, left + drawWidth, top + drawHeight)

            fgCanvas.drawBitmap(originalBitmap, null, destRect, paint)

            val fgOut = ByteArrayOutputStream()
            fgBitmap.compress(Bitmap.CompressFormat.PNG, 100, fgOut)
            val fgBytes = fgOut.toByteArray()

            // 2. Synthesize Background Layer (512x512 deep dark OLED slate canvas)
            val bgBitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            val bgCanvas = Canvas(bgBitmap)
            val bgPaint = Paint().apply {
                color = Color.parseColor("#0F141C")
            }
            bgCanvas.drawRect(0f, 0f, ICON_SIZE.toFloat(), ICON_SIZE.toFloat(), bgPaint)

            val bgOut = ByteArrayOutputStream()
            bgBitmap.compress(Bitmap.CompressFormat.PNG, 100, bgOut)
            val bgBytes = bgOut.toByteArray()

            // 3. Synthesize Composite Fallback Raster Icon
            val compositeBitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            val compositeCanvas = Canvas(compositeBitmap)
            compositeCanvas.drawBitmap(bgBitmap, 0f, 0f, null)
            compositeCanvas.drawBitmap(fgBitmap, 0f, 0f, null)
            val compositeOut = ByteArrayOutputStream()
            compositeBitmap.compress(Bitmap.CompressFormat.PNG, 100, compositeOut)
            val rasterBytes = compositeOut.toByteArray()

            IconLayers(fgBytes, bgBytes, rasterBytes)
        } catch (e: Exception) {
            null
        }
    }
}
