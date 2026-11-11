package com.senza.assistivetouch

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
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


/**
 * Panel kaca gelap ala referensi: isi hitam berasap, kabut putih di kiri-atas,
 * rim ganda yang terang di sudut, kilau di atas, dan cahaya lembut di bawah.
 * shadowPx > 0 -> bayangan digambar DI DALAM bounds (beri padding sebesar ini).
 */
class DarkGlassPanelDrawable(
    private val density: Float,
    private val radiusPx: Float,
    private val shadowPx: Float,
    private val tint: Float = 0.42f
) : Drawable() {

    private val under = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haze = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outerRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val innerRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val innerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val spec = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bottom = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rect = RectF()
    private val innerRect = RectF()
    private val glowRect = RectF()
    private val specRect = RectF()
    private val bottomRect = RectF()
    private val shape = Path()
    private var r = 0f
    private var innerR = 0f
    private var glowR = 0f
    private var specR = 0f
    private var bottomR = 0f

    private fun w(f: Float) = Color.argb((f.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255)

    override fun onBoundsChange(b: Rect) {
        val rimW = 2.2f * density
        val inW = 1.2f * density
        val glowW = 7f * density
        rect.set(b)
        rect.inset(shadowPx + rimW / 2f, shadowPx + rimW / 2f)
        val width = rect.width()
        val height = rect.height()
        if (width <= 0f || height <= 0f) return
        r = minOf(radiusPx, minOf(width, height) / 2f)

        shape.reset()
        shape.addRoundRect(rect, r, r, Path.Direction.CW)

        under.color = Color.argb((tint * 255).toInt(), 0, 0, 0)

        haze.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(w(0.20f), w(0.03f), w(0.01f), w(0.08f)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP
        )

        outerRim.strokeWidth = rimW
        outerRim.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(w(0.95f), w(0.22f), w(0.18f), w(0.90f)),
            floatArrayOf(0f, 0.36f, 0.64f, 1f), Shader.TileMode.CLAMP
        )

        val gap = 3.2f * density
        innerRect.set(rect)
        innerRect.inset(gap, gap)
        innerR = (r - gap).coerceAtLeast(0f)
        innerRim.strokeWidth = inW
        innerRim.shader = LinearGradient(
            innerRect.left, innerRect.bottom, innerRect.right, innerRect.top,
            intArrayOf(w(0.55f), w(0.06f), w(0.05f), w(0.45f)),
            floatArrayOf(0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP
        )

        glowRect.set(rect)
        glowRect.inset(glowW / 2f + rimW / 2f, glowW / 2f + rimW / 2f)
        glowR = (r - glowW / 2f - rimW / 2f).coerceAtLeast(0f)
        innerGlow.strokeWidth = glowW
        innerGlow.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(w(0.14f), w(0.02f), w(0.02f), w(0.12f)),
            floatArrayOf(0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP
        )

        specRect.set(
            rect.left + width * 0.06f, rect.top + 3f * density,
            rect.right - width * 0.30f, rect.top + height * 0.30f
        )
        specR = (r * 0.8f).coerceAtMost(specRect.height() / 2f + 4f * density)
        spec.shader = LinearGradient(
            0f, specRect.top, 0f, specRect.bottom,
            w(0.26f), w(0f), Shader.TileMode.CLAMP
        )

        bottomRect.set(
            rect.left + width * 0.08f, rect.bottom - height * 0.22f,
            rect.right - width * 0.08f, rect.bottom - 3f * density
        )
        bottomR = (r * 0.8f).coerceAtMost(bottomRect.height() / 2f + 4f * density)
        bottom.shader = LinearGradient(
            0f, bottomRect.top, 0f, bottomRect.bottom,
            w(0f), w(0.16f), Shader.TileMode.CLAMP
        )
    }

    override fun draw(c: Canvas) {
        if (rect.width() <= 0f) return

        if (shadowPx > 0f) {
            c.save()
            c.clipOutPath(shape)
            val offY = shadowPx * 0.35f
            for (i in 1..7) {
                val e = shadowPx * i / 7f
                shadow.color = Color.argb(16, 0, 0, 0)
                c.drawRoundRect(
                    rect.left - e, rect.top - e + offY, rect.right + e, rect.bottom + e + offY,
                    r + e, r + e, shadow
                )
            }
            c.restore()
        }

        c.drawRoundRect(rect, r, r, under)
        c.drawRoundRect(rect, r, r, haze)
        c.drawRoundRect(glowRect, glowR, glowR, innerGlow)
        c.drawRoundRect(bottomRect, bottomR, bottomR, bottom)
        c.drawRoundRect(specRect, specR, specR, spec)
        c.drawRoundRect(innerRect, innerR, innerR, innerRim)
        c.drawRoundRect(rect, r, r, outerRim)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Tombol gelembung kaca: bola transparan dengan tepi terang, cincin dalam,
 * kilau melengkung di atas dan pantulan cahaya di bawah.
 */
class GlassBubbleDrawable(private val density: Float) : Drawable() {

    private val under = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outerRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val innerRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val top = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bottom = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rect = RectF()
    private val innerRect = RectF()
    private val topRect = RectF()
    private val bottomRect = RectF()
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private fun w(f: Float) = Color.argb((f.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255)

    override fun onBoundsChange(b: Rect) {
        val rimW = 1.8f * density
        val inW = 1f * density
        val pad = 3f * density
        rect.set(b)
        rect.inset(pad + rimW / 2f, pad + rimW / 2f)
        val d = minOf(rect.width(), rect.height())
        if (d <= 0f) return
        cx = rect.centerX(); cy = rect.centerY(); radius = d / 2f

        under.color = Color.argb(70, 0, 0, 0)

        body.shader = RadialGradient(
            cx, cy - radius * 0.1f, radius,
            intArrayOf(w(0.02f), w(0.05f), w(0.22f)),
            floatArrayOf(0f, 0.68f, 1f), Shader.TileMode.CLAMP
        )

        outerRim.strokeWidth = rimW
        outerRim.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(w(0.95f), w(0.25f), w(0.22f), w(0.80f)),
            floatArrayOf(0f, 0.40f, 0.62f, 1f), Shader.TileMode.CLAMP
        )

        val gap = 3f * density
        innerRect.set(rect)
        innerRect.inset(gap, gap)
        innerRim.strokeWidth = inW
        innerRim.shader = LinearGradient(
            innerRect.right, innerRect.top, innerRect.left, innerRect.bottom,
            intArrayOf(w(0.40f), w(0.04f), w(0.04f), w(0.38f)),
            floatArrayOf(0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP
        )

        topRect.set(
            cx - radius * 0.62f, rect.top + radius * 0.10f,
            cx + radius * 0.62f, rect.top + radius * 0.86f
        )
        top.shader = LinearGradient(
            0f, topRect.top, 0f, topRect.bottom,
            w(0.50f), w(0f), Shader.TileMode.CLAMP
        )

        bottomRect.set(
            cx - radius * 0.60f, cy + radius * 0.30f,
            cx + radius * 0.60f, rect.bottom - radius * 0.07f
        )
        bottom.shader = LinearGradient(
            0f, bottomRect.top, 0f, bottomRect.bottom,
            w(0f), w(0.30f), Shader.TileMode.CLAMP
        )
    }

    override fun draw(c: Canvas) {
        if (radius <= 0f) return
        val sh = 3f * density
        for (i in 1..4) {
            val e = sh * i / 4f
            shadow.color = Color.argb(18, 0, 0, 0)
            c.drawCircle(cx, cy + sh * 0.7f, radius + e, shadow)
        }
        c.drawCircle(cx, cy, radius, under)
        c.drawCircle(cx, cy, radius, body)
        c.drawCircle(cx, cy, radius - 3f * density, innerRim)
        c.drawOval(bottomRect, bottom)
        c.drawOval(topRect, top)
        c.drawCircle(cx, cy, radius, outerRim)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
