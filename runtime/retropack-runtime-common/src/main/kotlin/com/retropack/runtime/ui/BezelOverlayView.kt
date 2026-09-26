package com.retropack.runtime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.retropack.domain.model.BezelMode
import com.retropack.runtime.core.ScaleMode
import com.retropack.runtime.video.BezelConfig

/**
 * Multi-layer Console Bezel & Screen Frame Overlay for RetroPack.
 *
 * Implements Feature 6 specifications:
 * - Layer 0 (Frame / Background): High-resolution handheld & console housing (GBA, SP, DMG, GBC, 90s CRT TV, OLED Pure Black)
 * - Layer 1 (Screen Lens & Chamfers): Inner bezel shadow, lens highlights, and screen borders
 * - Layer 2 (Glass Glare & Ambient Glow): Authentic subtle diagonal glass reflection
 */
class BezelOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var bezelMode: BezelMode = BezelMode.AUTO
        set(value) {
            field = value
            invalidate()
        }

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

    var platform: String = "gba"
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
    private val glarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val tempCutoutRect = RectF()

    fun renderForTesting(canvas: Canvas) {
        onDraw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!bezelEnabled) return

        val viewW = if (width > 0) width.toFloat() else 1080f
        val viewH = if (height > 0) height.toFloat() else 1920f
        val isLandscape = viewW > viewH

        val activeMode = if (bezelMode == BezelMode.AUTO) {
            BezelConfig.resolveAutoBezel(platform)
        } else {
            bezelMode
        }

        val cutout = BezelConfig.calculateCutout(
            surfaceWidth = viewW.toInt(),
            surfaceHeight = viewH.toInt(),
            nativeWidth = nativeWidth,
            nativeHeight = nativeHeight,
            bezelMode = activeMode,
            scaleMode = scaleMode
        )

        val gx = cutout.viewportX.toFloat()
        val gy = cutout.viewportY.toFloat()
        val gw = cutout.viewportWidth.toFloat()
        val gh = cutout.viewportHeight.toFloat()
        tempCutoutRect.set(gx, gy, gx + gw, gy + gh)

        // ==========================================
        // 1. OLED Pure Black Mode (#000000)
        // ==========================================
        if (activeMode.isOledBlack) {
            // Subtle glowing cyber grid / accent lines
            borderPaint.color = Color.argb(60, 99, 102, 241) // RetroPrimary indigo glow
            borderPaint.strokeWidth = 2f
            canvas.drawRoundRect(tempCutoutRect.left - 2f, tempCutoutRect.top - 2f, tempCutoutRect.right + 2f, tempCutoutRect.bottom + 2f, 4f, 4f, borderPaint)
            return
        }

        // ==========================================
        // 2. Casing Background & Margin Filling (Layer 0)
        // ==========================================
        when (activeMode) {
            BezelMode.GBA_INDIGO -> {
                bgPaint.color = Color.argb(255, 63, 70, 110) // Indigo
                drawBezelMargins(canvas, viewW, viewH, tempCutoutRect, bgPaint)
                drawGbaDetails(canvas, viewW, viewH, tempCutoutRect, isLandscape, "RETROPACK ADVANCE", Color.argb(255, 45, 50, 80))
            }
            BezelMode.GBA_SP_PLATINUM -> {
                bgPaint.color = Color.argb(255, 142, 149, 165) // Platinum metallic silver
                drawBezelMargins(canvas, viewW, viewH, tempCutoutRect, bgPaint)
                drawGbaDetails(canvas, viewW, viewH, tempCutoutRect, isLandscape, "RETROPACK SP", Color.argb(255, 110, 116, 130))
            }
            BezelMode.DMG_RETRO_OFFWHITE -> {
                bgPaint.color = Color.argb(255, 200, 196, 183) // DMG-01 off-white
                drawBezelMargins(canvas, viewW, viewH, tempCutoutRect, bgPaint)
                drawDmgDetails(canvas, viewW, viewH, tempCutoutRect, isLandscape)
            }
            BezelMode.GBC_ATOMIC_PURPLE -> {
                bgPaint.color = Color.argb(255, 86, 56, 120) // Translucent purple
                drawBezelMargins(canvas, viewW, viewH, tempCutoutRect, bgPaint)
                drawGbcDetails(canvas, viewW, viewH, tempCutoutRect, isLandscape)
            }
            BezelMode.CRT_TRINITRON_90S -> {
                bgPaint.color = Color.argb(255, 28, 30, 36) // Dark charcoal CRT cabinet
                drawBezelMargins(canvas, viewW, viewH, tempCutoutRect, bgPaint)
                drawCrtTvDetails(canvas, viewW, viewH, tempCutoutRect, isLandscape)
            }
            else -> {
                bgPaint.color = Color.argb(255, 42, 46, 61)
                drawBezelMargins(canvas, viewW, viewH, tempCutoutRect, bgPaint)
            }
        }

        // ==========================================
        // 3. Screen Lens Chamfers & Inner Shadow (Layer 1)
        // ==========================================
        borderPaint.color = Color.argb(240, 20, 22, 28)
        borderPaint.strokeWidth = 6f
        canvas.drawRoundRect(tempCutoutRect.left - 3f, tempCutoutRect.top - 3f, tempCutoutRect.right + 3f, tempCutoutRect.bottom + 3f, cutout.lensBorderRadius, cutout.lensBorderRadius, borderPaint)

        borderPaint.color = Color.argb(120, 255, 255, 255)
        borderPaint.strokeWidth = 1.5f
        canvas.drawRoundRect(tempCutoutRect.left - 1f, tempCutoutRect.top - 1f, tempCutoutRect.right + 1f, tempCutoutRect.bottom + 1f, cutout.lensBorderRadius, cutout.lensBorderRadius, borderPaint)

        // ==========================================
        // 4. Diagonal Glass Glare Sweep (Layer 2)
        // ==========================================
        if (cutout.showGlassGlare) {
            glarePaint.shader = LinearGradient(
                tempCutoutRect.left, tempCutoutRect.top,
                tempCutoutRect.right, tempCutoutRect.bottom,
                intArrayOf(Color.argb(28, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(12, 255, 255, 255)),
                floatArrayOf(0.0f, 0.45f, 1.0f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(tempCutoutRect, cutout.lensBorderRadius, cutout.lensBorderRadius, glarePaint)
            glarePaint.shader = null
        }
    }

    private fun drawBezelMargins(canvas: Canvas, w: Float, h: Float, cutout: RectF, paint: Paint) {
        // Top
        if (cutout.top > 0) canvas.drawRect(0f, 0f, w, cutout.top, paint)
        // Bottom
        if (h > cutout.bottom) canvas.drawRect(0f, cutout.bottom, w, h, paint)
        // Left
        if (cutout.left > 0) canvas.drawRect(0f, cutout.top, cutout.left, cutout.bottom, paint)
        // Right
        if (w > cutout.right) canvas.drawRect(cutout.right, cutout.top, w, cutout.bottom, paint)
    }

    private fun drawGbaDetails(canvas: Canvas, w: Float, h: Float, cutout: RectF, isLandscape: Boolean, badge: String, accentColor: Int) {
        detailPaint.color = accentColor
        if (isLandscape) {
            val leftGutterW = cutout.left
            if (leftGutterW > 40f) {
                canvas.drawCircle(leftGutterW * 0.5f, h * 0.20f, 8f, detailPaint)
                drawSpeakerGrill(canvas, leftGutterW * 0.5f, h * 0.80f, Color.argb(180, 30, 35, 50))
            }
            val rightGutterW = w - cutout.right
            if (rightGutterW > 40f) {
                val cx = cutout.right + rightGutterW * 0.5f
                // Glowing Power LED
                detailPaint.color = Color.argb(255, 46, 204, 113)
                canvas.drawCircle(cx, h * 0.20f, 5f, detailPaint)
                detailPaint.color = Color.argb(70, 46, 204, 113)
                canvas.drawCircle(cx, h * 0.20f, 11f, detailPaint)
                drawSpeakerGrill(canvas, cx, h * 0.80f, Color.argb(180, 30, 35, 50))
            }
            if (cutout.top > 20f) {
                textPaint.textSize = 12f
                textPaint.color = Color.argb(190, 180, 195, 220)
                canvas.drawText(badge, cutout.centerX(), cutout.top - 8f, textPaint)
            }
        } else {
            if (cutout.top > 30f) {
                // LED
                detailPaint.color = Color.argb(255, 46, 204, 113)
                canvas.drawCircle(w * 0.12f, cutout.top * 0.55f, 5f, detailPaint)
                textPaint.textSize = 13f
                textPaint.color = Color.argb(210, 190, 205, 230)
                canvas.drawText(badge, w * 0.5f, cutout.top * 0.60f, textPaint)
            }
            if (h - cutout.bottom > 30f) {
                textPaint.textSize = 10f
                textPaint.color = Color.argb(150, 150, 170, 195)
                canvas.drawText("WIDE COLOR SCREEN", w * 0.5f, cutout.bottom + 18f, textPaint)
                drawSpeakerGrill(canvas, w * 0.88f, cutout.bottom + 24f, Color.argb(180, 30, 35, 50))
            }
        }
    }

    private fun drawDmgDetails(canvas: Canvas, w: Float, h: Float, cutout: RectF, isLandscape: Boolean) {
        // Authentic Game Boy Magenta/Maroon striping & BATTERY indicator
        detailPaint.color = Color.argb(255, 155, 44, 78) // Maroon
        if (cutout.top > 24f) {
            canvas.drawRect(cutout.left - 10f, cutout.top - 18f, cutout.right + 10f, cutout.top - 14f, detailPaint)
            detailPaint.color = Color.argb(255, 30, 45, 90) // Navy stripe
            canvas.drawRect(cutout.left - 10f, cutout.top - 12f, cutout.right + 10f, cutout.top - 9f, detailPaint)

            textPaint.textSize = 11f
            textPaint.color = Color.argb(220, 20, 25, 40)
            canvas.drawText("DOT MATRIX WITH STEREO SOUND", cutout.centerX(), cutout.top - 22f, textPaint)
        }

        // Red Battery LED on Left
        val ledX = if (isLandscape) cutout.left * 0.5f else w * 0.10f
        val ledY = if (isLandscape) h * 0.35f else cutout.centerY()
        detailPaint.color = Color.argb(255, 231, 76, 60)
        canvas.drawCircle(ledX, ledY, 5f, detailPaint)
        textPaint.textSize = 8f
        textPaint.color = Color.argb(180, 60, 65, 75)
        canvas.drawText("BATTERY", ledX, ledY + 14f, textPaint)
    }

    private fun drawGbcDetails(canvas: Canvas, w: Float, h: Float, cutout: RectF, isLandscape: Boolean) {
        if (cutout.top > 20f) {
            textPaint.textSize = 13f
            textPaint.color = Color.argb(220, 220, 210, 240)
            canvas.drawText("COLOR", cutout.centerX(), cutout.top - 8f, textPaint)
        }
        val ledX = if (isLandscape) cutout.left * 0.5f else w * 0.12f
        val ledY = if (isLandscape) h * 0.25f else cutout.top * 0.55f
        detailPaint.color = Color.argb(255, 231, 76, 60)
        canvas.drawCircle(ledX, ledY, 4.5f, detailPaint)
    }

    private fun drawCrtTvDetails(canvas: Canvas, w: Float, h: Float, cutout: RectF, isLandscape: Boolean) {
        // TV Speaker Grill Slits and Retro TV Badge
        if (cutout.top > 22f) {
            textPaint.textSize = 11f
            textPaint.color = Color.argb(200, 180, 185, 195)
            canvas.drawText("TRINITRON COLOR TV", cutout.centerX(), cutout.top - 10f, textPaint)
        }
        if (isLandscape) {
            val rightGutterW = w - cutout.right
            if (rightGutterW > 40f) {
                val cx = cutout.right + rightGutterW * 0.5f
                // TV Power Indicator (Red)
                detailPaint.color = Color.argb(255, 230, 40, 40)
                canvas.drawCircle(cx, h * 0.85f, 4f, detailPaint)
                // Volume / Channel Dials
                detailPaint.color = Color.argb(255, 45, 50, 60)
                canvas.drawCircle(cx, h * 0.35f, 14f, detailPaint)
                canvas.drawCircle(cx, h * 0.55f, 14f, detailPaint)
            }
        }
    }

    private fun drawSpeakerGrill(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        detailPaint.color = color
        val dotRadius = 2.5f
        val dotGap = 7f
        for (row in -1..1) {
            for (col in -1..1) {
                canvas.drawCircle(cx + col * dotGap, cy + row * dotGap, dotRadius, detailPaint)
            }
        }
    }
}
