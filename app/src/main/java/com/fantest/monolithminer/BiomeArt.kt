package com.fantest.monolithminer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

/**
 * Lightweight, asset-free M3 biomes. All artwork is drawn directly into the
 * existing GameView Canvas; no bitmaps, new dependencies or extra network calls.
 *
 * Depth bands: Surface 1-99, Crystal 100-299, Magma 300-599,
 * Void 600-999, Ancient Core 1000+.
 */
object BiomeArt {
    data class Theme(
        val index: Int,
        val title: String,
        val bodyDark: Int,
        val bodyLight: Int,
        val glow: Int,
        val ground: Int
    )

    private val themes = listOf(
        Theme(0, "DESERT SURFACE", Color.rgb(52, 42, 42), Color.rgb(159, 125, 94),
            Color.rgb(255, 187, 82), Color.rgb(70, 37, 39)),
        Theme(1, "CRYSTAL CAVES", Color.rgb(20, 45, 84), Color.rgb(106, 174, 212),
            Color.rgb(57, 218, 255), Color.rgb(14, 47, 81)),
        Theme(2, "MAGMA DEPTHS", Color.rgb(50, 27, 34), Color.rgb(151, 67, 48),
            Color.rgb(255, 96, 29), Color.rgb(67, 28, 31)),
        Theme(3, "VOID EXPANSE", Color.rgb(38, 24, 77), Color.rgb(116, 78, 197),
            Color.rgb(182, 93, 255), Color.rgb(32, 23, 64)),
        Theme(4, "ANCIENT CORE", Color.rgb(32, 56, 62), Color.rgb(139, 175, 171),
            Color.rgb(117, 241, 224), Color.rgb(36, 55, 61))
    )

    fun forDepth(depth: Int): Theme = themes[when {
        depth < 100 -> 0
        depth < 300 -> 1
        depth < 600 -> 2
        depth < 1000 -> 3
        else -> 4
    }]

    private fun sky(canvas: Canvas, paint: Paint, w: Float, h: Float, top: Int, bottom: Int) {
        paint.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun glow(canvas: Canvas, paint: Paint, x: Float, y: Float, radius: Float, color: Int, alpha: Int) {
        paint.shader = RadialGradient(x, y, radius,
            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
            Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null
    }

    private fun ridge(canvas: Canvas, paint: Paint, w: Float, h: Float, startY: Float,
                      amplitude: Float, color: Int, seed: Float) {
        val p = Path()
        p.moveTo(0f, h)
        p.lineTo(0f, startY)
        for (i in 1..14) {
            val x = w * i / 14f
            val y = startY + sin(i * 1.53f + seed) * amplitude +
                sin(i * 0.72f + seed * 2f) * amplitude * 0.45f
            p.lineTo(x, y)
        }
        p.lineTo(w, h)
        p.close()
        paint.color = color
        canvas.drawPath(p, paint)
    }

    private fun shard(canvas: Canvas, paint: Paint, x: Float, y: Float, size: Float,
                      color: Int, highlight: Int) {
        val p = Path().apply {
            moveTo(x, y - size)
            lineTo(x + size * 0.42f, y - size * 0.15f)
            lineTo(x + size * 0.30f, y + size * 0.70f)
            lineTo(x - size * 0.40f, y + size * 0.56f)
            lineTo(x - size * 0.54f, y - size * 0.25f)
            close()
        }
        paint.color = color
        canvas.drawPath(p, paint)
        val face = Path().apply {
            moveTo(x, y - size)
            lineTo(x + size * 0.42f, y - size * 0.15f)
            lineTo(x + size * 0.30f, y + size * 0.70f)
            lineTo(x, y + size * 0.37f)
            close()
        }
        paint.color = highlight
        canvas.drawPath(face, paint)
    }

    private fun column(canvas: Canvas, paint: Paint, x: Float, y: Float, w: Float,
                       height: Float, color: Int) {
        paint.color = color
        canvas.drawRect(x, y - height, x + w, y, paint)
        canvas.drawRect(x - w * 0.18f, y - height, x + w * 1.18f, y - height + w * 0.18f, paint)
        canvas.drawRect(x - w * 0.20f, y - w * 0.16f, x + w * 1.20f, y + w * 0.1f, paint)
        paint.color = Color.argb(55, 176, 244, 232)
        canvas.drawRect(x + w * 0.16f, y - height + w * 0.20f, x + w * 0.25f, y - w * 0.16f, paint)
    }

    fun drawBackdrop(canvas: Canvas, paint: Paint, w: Float, h: Float,
                     base: Float, time: Float, theme: Theme) {
        when (theme.index) {
            0 -> {
                sky(canvas, paint, w, h, Color.rgb(55, 35, 77), Color.rgb(233, 116, 69))
                glow(canvas, paint, w * 0.80f, h * 0.27f, w * 0.30f,
                    Color.rgb(255, 182, 106), 170)
                paint.color = Color.rgb(255, 198, 122)
                canvas.drawCircle(w * 0.80f, h * 0.27f, w * 0.078f, paint)
                ridge(canvas, paint, w, h, h * 0.64f, h * 0.085f, Color.rgb(160, 84, 81), 1f)
                ridge(canvas, paint, w, h, h * 0.76f, h * 0.060f, Color.rgb(111, 58, 69), 4f)
                paint.color = Color.rgb(93, 49, 59)
                val left = Path().apply {
                    moveTo(0f, h * 0.45f); lineTo(w * 0.12f, h * 0.55f)
                    lineTo(w * 0.06f, h * 0.75f); lineTo(w * 0.19f, h * 0.84f)
                    lineTo(0f, h); close()
                }
                canvas.drawPath(left, paint)
                val right = Path().apply {
                    moveTo(w, h * 0.52f); lineTo(w * 0.91f, h * 0.64f)
                    lineTo(w * 0.97f, h * 0.78f); lineTo(w * 0.87f, h * 0.87f)
                    lineTo(w, h); close()
                }
                canvas.drawPath(right, paint)
            }
            1 -> {
                sky(canvas, paint, w, h, Color.rgb(5, 15, 44), Color.rgb(13, 76, 118))
                glow(canvas, paint, w * 0.51f, h * 0.59f, w * 0.60f,
                    Color.rgb(8, 130, 228), 115)
                ridge(canvas, paint, w, h, h * 0.78f, h * 0.070f, Color.rgb(9, 37, 83), 3f)
                for (i in 0..5) {
                    val edge = if (i % 2 == 0) w * (0.07f + i * 0.055f) else w * (0.93f - i * 0.055f)
                    val size = h * (0.07f + (i % 3) * 0.024f)
                    shard(canvas, paint, edge, base - size * 0.35f, size,
                        Color.rgb(17, 95, 160), Color.rgb(39, 205, 243))
                }
                paint.color = Color.rgb(6, 20, 52)
                for (i in 0..7) {
                    val x = w * i / 7f
                    val p = Path().apply {
                        moveTo(x, 0f); lineTo(x + w * 0.09f, 0f)
                        lineTo(x + w * 0.035f, h * (0.07f + (i % 3) * 0.024f))
                        close()
                    }
                    canvas.drawPath(p, paint)
                }
            }
            2 -> {
                sky(canvas, paint, w, h, Color.rgb(20, 8, 26), Color.rgb(140, 29, 25))
                glow(canvas, paint, w * 0.55f, h * 0.85f, w * 0.64f,
                    Color.rgb(255, 64, 14), 165)
                ridge(canvas, paint, w, h, h * 0.76f, h * 0.105f, Color.rgb(57, 23, 32), 2f)
                ridge(canvas, paint, w, h, h * 0.86f, h * 0.085f, Color.rgb(34, 17, 24), 5f)
                paint.color = Color.rgb(255, 84, 24)
                canvas.drawRoundRect(RectF(w * 0.04f, base + h * 0.012f,
                    w * 0.31f, base + h * 0.025f), 6f, 6f, paint)
                canvas.drawRoundRect(RectF(w * 0.69f, base + h * 0.02f,
                    w * 0.96f, base + h * 0.034f), 6f, 6f, paint)
                for (i in 0..18) {
                    val x = (i * 47 % 97) / 97f * w
                    val y = (0.20f + (i * 31 % 60) / 100f) * h
                    val flicker = 0.5f + 0.5f * sin(time * 2f + i)
                    paint.color = Color.argb((95 + flicker * 115).toInt(), 255, 111, 45)
                    canvas.drawCircle(x, y, 1.8f + flicker * 2.6f, paint)
                }
            }
            3 -> {
                sky(canvas, paint, w, h, Color.rgb(8, 7, 36), Color.rgb(65, 31, 106))
                glow(canvas, paint, w * 0.59f, h * 0.46f, w * 0.57f,
                    Color.rgb(133, 40, 218), 138)
                ridge(canvas, paint, w, h, h * 0.86f, h * 0.048f, Color.rgb(31, 22, 68), 4f)
                for (i in 0..9) {
                    val x = w * ((i * 37 + 11) % 95) / 95f
                    val y = h * (0.22f + ((i * 23) % 55) / 100f) +
                        sin(time * 0.65f + i) * h * 0.013f
                    val size = h * (0.018f + (i % 3) * 0.011f)
                    shard(canvas, paint, x, y, size,
                        Color.rgb(42, 23, 94), Color.rgb(97, 53, 159))
                }
            }
            else -> {
                sky(canvas, paint, w, h, Color.rgb(12, 34, 46), Color.rgb(81, 118, 123))
                glow(canvas, paint, w * 0.51f, h * 0.50f, w * 0.47f,
                    Color.rgb(114, 235, 221), 95)
                ridge(canvas, paint, w, h, h * 0.81f, h * 0.055f, Color.rgb(36, 70, 77), 3f)
                for (i in 0..3) {
                    val x = if (i < 2) w * (0.08f + i * 0.15f) else w * (0.68f + (i - 2) * 0.18f)
                    val height = h * (0.30f + (i % 2) * 0.13f)
                    column(canvas, paint, x, base, w * 0.06f, height, Color.rgb(42, 76, 81))
                }
                paint.color = Color.argb(85, 155, 237, 229)
                for (i in 0..13) {
                    val x = ((i * 33 + 11) % 97) / 97f * w
                    val y = h * (0.18f + ((i * 29) % 60) / 100f)
                    canvas.drawCircle(x, y, 2.0f, paint)
                }
            }
        }
        paint.shader = LinearGradient(0f, base - 20f, 0f, h,
            theme.ground, Color.rgb(9, 11, 22), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, base - 20f, w, h, paint)
        paint.shader = null
    }

    /**
     * Layered atmospheric motion; deliberately low primitive counts to
     * preserve smooth frame pacing on inexpensive devices.
     */
    fun drawWeather(canvas: Canvas, paint: Paint, w: Float, h: Float,
                    base: Float, time: Float, theme: Theme) {
        when (theme.index) {
            0 -> {
                // Slow drifting sand-cloud silhouettes.
                for (i in 0..3) {
                    val x = ((time * (3.1f + i) + i * w * 0.37f) % (w * 1.55f)) - w * 0.25f
                    val y = h * (0.35f + i * 0.065f)
                    paint.color = Color.argb(21 + i * 7, 255, 217, 164)
                    canvas.drawOval(RectF(x - w * 0.24f, y - 11f, x + w * 0.20f, y + 16f), paint)
                }
            }
            1 -> {
                // Twinkling blue crystal fireflies.
                for (i in 0..21) {
                    val x = w * ((i * 31 + 7) % 97) / 97f
                    val y = h * (0.18f + (i * 17 % 53) / 100f) +
                        sin(time * 1.5f + i) * 5f
                    paint.color = Color.argb(
                        (45 + 95 * (0.5f + 0.5f * sin(time * 2.4f + i))).toInt(),
                        95, 247, 255)
                    canvas.drawCircle(x, y, 1.3f + i % 3, paint)
                }
            }
            2 -> {
                // Heat shimmer and bright moving lava streaks.
                for (i in 0..6) {
                    val x = w * (i * 17 + 5) / 112f
                    val y = base - h * (0.025f + (i % 3) * 0.016f)
                    val len = (24f + 17f * sin(time * 2f + i)).coerceAtLeast(7f)
                    paint.color = Color.argb(95, 255, 146, 35)
                    canvas.drawRoundRect(RectF(x, y, x + len, y + 3f), 2f, 2f, paint)
                }
            }
            3 -> {
                // Far-away astral particles travel in elliptical paths.
                for (i in 0..18) {
                    val x = w * ((i * 29 + 13) % 101) / 101f +
                        sin(time * 0.7f + i) * 9f
                    val y = h * (0.18f + (i * 19 % 59) / 100f) +
                        sin(time * 0.45f + i * 2f) * 16f
                    paint.color = Color.argb(110, 201, 149, 255)
                    canvas.drawCircle(x, y, if (i % 4 == 0) 3.3f else 1.6f, paint)
                }
            }
            else -> {
                // Sweeping dusty light shafts in the Ancient Core.
                for (i in 0..2) {
                    val alpha = (18f + 16f * (0.5f + 0.5f * sin(time * 0.9f + i))).toInt()
                    val center = w * (0.15f + i * 0.36f) + sin(time * 0.4f + i) * 20f
                    val polygon = Path().apply {
                        moveTo(center - 10f, h * 0.05f)
                        lineTo(center + 10f, h * 0.05f)
                        lineTo(center + 110f, base)
                        lineTo(center - 110f, base)
                        close()
                    }
                    paint.color = Color.argb(alpha, 164, 255, 236)
                    canvas.drawPath(polygon, paint)
                }
            }
        }
    }

    fun monolithPath(x: Float, top: Float, base: Float, width: Float, theme: Theme): Path {
        val half = width / 2f
        val h = base - top
        return Path().apply {
            when (theme.index) {
                1 -> {
                    moveTo(x - half * 0.76f, base)
                    lineTo(x - half * 0.61f, top + h * 0.17f)
                    lineTo(x - half * 0.16f, top + h * 0.07f)
                    lineTo(x, top)
                    lineTo(x + half * 0.27f, top + h * 0.10f)
                    lineTo(x + half * 0.69f, top + h * 0.19f)
                    lineTo(x + half * 0.79f, base)
                }
                2 -> {
                    moveTo(x - half, base)
                    lineTo(x - half * 0.72f, top + h * 0.38f)
                    lineTo(x - half * 0.82f, top + h * 0.27f)
                    lineTo(x - half * 0.25f, top + h * 0.12f)
                    lineTo(x, top)
                    lineTo(x + half * 0.37f, top + h * 0.18f)
                    lineTo(x + half * 0.91f, top + h * 0.29f)
                    lineTo(x + half * 0.76f, top + h * 0.42f)
                    lineTo(x + half, base)
                }
                3 -> {
                    moveTo(x - half * 0.83f, base - h * 0.045f)
                    lineTo(x - half * 0.65f, top + h * 0.23f)
                    lineTo(x - half * 0.26f, top + h * 0.17f)
                    lineTo(x, top - h * 0.03f)
                    lineTo(x + half * 0.42f, top + h * 0.18f)
                    lineTo(x + half * 0.74f, top + h * 0.29f)
                    lineTo(x + half * 0.86f, base - h * 0.045f)
                }
                4 -> {
                    moveTo(x - half, base)
                    lineTo(x - half, top + h * 0.28f)
                    lineTo(x - half * 0.68f, top + h * 0.28f)
                    lineTo(x - half * 0.68f, top + h * 0.14f)
                    lineTo(x - half * 0.38f, top + h * 0.14f)
                    lineTo(x, top)
                    lineTo(x + half * 0.38f, top + h * 0.14f)
                    lineTo(x + half * 0.68f, top + h * 0.14f)
                    lineTo(x + half * 0.68f, top + h * 0.28f)
                    lineTo(x + half, top + h * 0.28f)
                    lineTo(x + half, base)
                }
                else -> {
                    moveTo(x - half, base)
                    lineTo(x - half * 0.60f, top + h * 0.15f)
                    lineTo(x, top)
                    lineTo(x + half * 0.60f, top + h * 0.15f)
                    lineTo(x + half, base)
                }
            }
            close()
        }
    }
}
