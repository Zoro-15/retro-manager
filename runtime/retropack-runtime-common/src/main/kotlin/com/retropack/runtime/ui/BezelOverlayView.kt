package com.retropack.runtime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.retropack.runtime.core.ScaleMode

/**
 * Handheld Console Screen Bezel & Frame Overlay for RetroPack.
 *
 * Recreates the authentic handheld console frame aesthetics (speaker grills,
 * debossed RetroPack Advance badge, screen lens chamfers, and power indicator)
 * inside the letterbox/pillarbox margins on wide 19.5:9 / 20:9 phone displays.
 */
class BezelOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var bezelEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var scaleMode: ScaleMode = ScaleMode.ASPECT_FIT
        set(value) {
            field = value
            invalidate()
        }

    var nativeWidth: Int = 240
        set(value) {
            field = value
            invalidate()
        }

    var nativeHeight: Int = 160
        set(value) {
            field = value
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(200, 160, 175, 200)
    }

    private val tempGameRect = RectF()

    fun renderForTesting(canvas: Canvas) {
        onDraw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!bezelEnabled) return

        val viewW = if (width > 0) width.toFloat() else 1080f
        val viewH = if (height > 0) height.toFloat() else 1920f

        val viewportRect = scaleMode.calculateViewport(
            surfaceWidth = viewW.toInt(),
            surfaceHeight = viewH.toInt(),
            nativeWidth = nativeWidth,
            nativeHeight = nativeHeight
        )

        // Game viewport in screen coordinates
        val gw = viewportRect.width.toFloat()
        val gh = viewportRect.height.toFloat()
        val gx = viewportRect.x.toFloat()
        val gy = viewH - (viewportRect.y + viewportRect.height).toFloat() // Flip Y from GL to Android 2D
        tempGameRect.set(gx, gy, gx + gw, gy + gh)

        val isLandscape = viewW > viewH

        // 1. Draw Lens Frame Border around the Game Screen
        borderPaint.color = Color.argb(255, 38, 44, 58)
        borderPaint.strokeWidth = 4f
        canvas.drawRoundRect(
            tempGameRect.left - 4f,
            tempGameRect.top - 4f,
            tempGameRect.right + 4f,
            tempGameRect.bottom + 4f,
            6f, 6f, borderPaint
        )

        borderPaint.color = Color.argb(180, 70, 85, 115)
        borderPaint.strokeWidth = 1.5f
        canvas.drawRoundRect(
            tempGameRect.left - 2f,
            tempGameRect.top - 2f,
            tempGameRect.right + 2f,
            tempGameRect.bottom + 2f,
            4f, 4f, borderPaint
        )

        // 2. Render Handheld Console Details in Margins
        if (isLandscape) {
            // Landscape Margins: Left and Right Gutters
            val leftGutterWidth = gx
            val rightGutterLeft = tempGameRect.right

            if (leftGutterWidth > 40f) {
                // Left Gutter: Console D-Pad Housing & Retro Badge
                detailPaint.color = Color.argb(140, 50, 60, 80)
                canvas.drawCircle(leftGutterWidth * 0.5f, viewH * 0.18f, 6f, detailPaint)

                // Speaker Grill Matrix on Left
                drawSpeakerGrill(canvas, leftGutterWidth * 0.5f, viewH * 0.82f)
            }

            if (viewW - rightGutterLeft > 40f) {
                // Right Gutter: Power LED & Speaker Grill
                val rightCenterX = rightGutterLeft + (viewW - rightGutterLeft) * 0.5f

                // Power LED (Glowing Green)
                detailPaint.color = Color.argb(255, 50, 205, 50)
                canvas.drawCircle(rightCenterX, viewH * 0.18f, 5f, detailPaint)
                detailPaint.color = Color.argb(80, 50, 255, 50)
                canvas.drawCircle(rightCenterX, viewH * 0.18f, 10f, detailPaint)

                // Speaker Grill Matrix on Right
                drawSpeakerGrill(canvas, rightCenterX, viewH * 0.82f)
            }

            // Top / Bottom subtle badges if any margin
            if (gy > 20f) {
                textPaint.textSize = 12f
                textPaint.color = Color.argb(180, 140, 160, 190)
                canvas.drawText("RETROPACK ADVANCE", tempGameRect.centerX(), gy - 8f, textPaint)
            }
        } else {
            // Portrait Margins: Top Bezel & Console Lower Lens Bar
            if (gy > 30f) {
                // Power LED on Top Left
                detailPaint.color = Color.argb(255, 50, 205, 50)
                canvas.drawCircle(viewW * 0.12f, gy * 0.55f, 5f, detailPaint)
                detailPaint.color = Color.argb(80, 50, 255, 50)
                canvas.drawCircle(viewW * 0.12f, gy * 0.55f, 9f, detailPaint)

                // Console Header Badge
                textPaint.textSize = 13f
                textPaint.color = Color.argb(210, 160, 180, 210)
                canvas.drawText("RETROPACK ADVANCE", viewW * 0.5f, gy * 0.60f, textPaint)
            }

            if (viewH - tempGameRect.bottom > 40f) {
                // Lens Footer Strip & WIDE SCREEN logo
                textPaint.textSize = 10f
                textPaint.color = Color.argb(140, 120, 140, 170)
                canvas.drawText("WIDE COLOR LCD", viewW * 0.5f, tempGameRect.bottom + 18f, textPaint)

                // Speaker Grill in Portrait Right Corner
                drawSpeakerGrill(canvas, viewW * 0.88f, tempGameRect.bottom + 26f)
            }
        }
    }

    private fun drawSpeakerGrill(canvas: Canvas, cx: Float, cy: Float) {
        detailPaint.color = Color.argb(160, 30, 36, 48)
        val dotRadius = 2.5f
        val dotGap = 7f

        for (row in -1..1) {
            for (col in -1..1) {
                canvas.drawCircle(cx + col * dotGap, cy + row * dotGap, dotRadius, detailPaint)
            }
        }
    }
}
