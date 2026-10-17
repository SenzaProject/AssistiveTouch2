package com.senza.assistivetouch

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Kaca cair ala iOS: isi transparan bergradasi, cahaya tepi dalam, rim bergradasi
 * (terang di kiri-atas & kanan-bawah), kilau spekular di atas, dan bayangan lembut.
 *
 * radiusPx < 0  -> bentuk bulat / pil penuh
 * level 0..100  -> kekuatan efek glass
 * shadowPx > 0  -> bayangan digambar DI DALAM bounds (beri padding sebesar ini)
 */
class GlassDrawable(
    private val density: Float,
    private val radiusPx: Float,
    level: Int,
    private val underlay: Float = 0.14f,
    private val shadowPx: Float = 0f
) : Drawable() {

    private val k = level.coerceIn(0, 100) / 100f

    private val under = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val spec = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rect = RectF()
    private val glowRect = RectF()
    private val specRect = RectF()
    private val shape = Path()
    private var r = 0f
    private var glowR = 0f
    private var specR = 0f

    private fun w(f: Float) = Color.argb((f.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255)

    override fun onBoundsChange(b: Rect) {
        val rimW = 1.3f * density
        val glowW = 3.5f * density
        rect.set(b)
        rect.inset(shadowPx + rimW / 2f, shadowPx + rimW / 2f)
        val width = rect.width()
        val height = rect.height()
        if (width <= 0f || height <= 0f) return
        r = if (radiusPx < 0f) minOf(width, height) / 2f else minOf(radiusPx, minOf(width, height) / 2f)

        shape.reset()
        shape.addRoundRect(rect, r, r, Path.Direction.CW)

        under.color = Color.argb((underlay * 255).toInt(), 0, 0, 0)

        fill.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            w(0.10f + 0.22f * k), w(0.03f + 0.09f * k), Shader.TileMode.CLAMP
        )

        glow.strokeWidth = glowW
        glow.color = w(0.05f + 0.15f * k)
        glowRect.set(rect)
        glowRect.inset(glowW / 2f, glowW / 2f)
        glowR = (r - glowW / 2f).coerceAtLeast(0f)

        rim.strokeWidth = rimW
        rim.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(w(0.55f + 0.40f * k), w(0.10f), w(0.08f), w(0.30f + 0.30f * k)),
            floatArrayOf(0f, 0.38f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )

        specRect.set(
            rect.left + width * 0.10f, rect.top + 1.5f * density,
            rect.right - width * 0.10f, rect.top + height * 0.42f
        )
        specR = (r * 0.85f).coerceAtMost(specRect.height() / 2f + 2f * density)
        spec.shader = LinearGradient(
            0f, specRect.top, 0f, specRect.bottom,
            w(0.04f + 0.30f * k), w(0f), Shader.TileMode.CLAMP
        )
    }

    override fun draw(c: Canvas) {
        if (rect.width() <= 0f) return

        if (shadowPx > 0f) {
            c.save()
            c.clipOutPath(shape)
            val offY = shadowPx * 0.30f
            for (i in 1..6) {
                val e = shadowPx * i / 6f
                shadow.color = Color.argb(11, 0, 0, 0)
                c.drawRoundRect(
                    rect.left - e, rect.top - e + offY, rect.right + e, rect.bottom + e + offY,
                    r + e, r + e, shadow
                )
            }
            c.restore()
        }

        c.drawRoundRect(rect, r, r, under)
        c.drawRoundRect(rect, r, r, fill)
        c.drawRoundRect(glowRect, glowR, glowR, glow)
        c.drawRoundRect(specRect, specR, specR, spec)
        c.drawRoundRect(rect, r, r, rim)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Efek "cair": mengecil saat ditekan, memantul (overshoot) saat dilepas. */
fun pressAnim(v: View, e: MotionEvent, pressed: Float = 0.94f) {
    when (e.actionMasked) {
        MotionEvent.ACTION_DOWN ->
            v.animate().scaleX(pressed).scaleY(pressed).setDuration(110)
                .setInterpolator(DecelerateInterpolator()).start()
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
            v.animate().scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(OvershootInterpolator(2.4f)).start()
    }
}
