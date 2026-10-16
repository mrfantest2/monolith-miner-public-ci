package com.fantest.monolithminer

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Hand-drawn articulated mining character and companion machines.
 * Animated at frame rate using Canvas transforms, not frame-by-frame PNGs.
 * Sizes are proportional to the gameplay viewport, including small screens.
 */
object SceneActors {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val ink = Color.rgb(12, 20, 36)
    private val gold = Color.rgb(255, 186, 59)
    private val copper = Color.rgb(190, 106, 46)
    private val cyan = Color.rgb(81, 231, 255)

    private fun disc(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        p.color = color; p.shader = null; c.drawCircle(x, y, r, p)
    }
    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int) {
        p.color = color; p.shader = null; c.drawOval(RectF(x - rx, y - ry, x + rx, y + ry), p)
    }
    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int) {
        pen.color = color; pen.strokeWidth = width; c.drawLine(x1, y1, x2, y2, pen)
    }
    private fun round(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        p.shader = null; p.color = color
        c.drawRoundRect(RectF(l, t, r, b), rad, rad, p)
    }
    private fun poly(c: Canvas, color: Int, vararg points: Float) {
        if (points.size < 6) return
        val shape = Path().apply {
            moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
            close()
        }
        p.shader = null; p.color = color; c.drawPath(shape, p)
    }
    private fun aura(c: Canvas, x: Float, y: Float, rad: Float, color: Int, strength: Int) {
        p.shader = RadialGradient(x, y, rad,
            Color.argb(strength, Color.red(color), Color.green(color), Color.blue(color)),
            Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rad, p); p.shader = null
    }

    /** Always show the lead miner: the gameplay should never feel like an unattended tower. */
    fun drawMiner(c: Canvas, x: Float, feetY: Float, viewportW: Float, time: Float,
                  strikes: Long, gearLevel: Int, glow: Int) {
        val unit = (viewportW / 720f).coerceIn(0.52f, 1.65f)
        c.save(); c.translate(x, feetY); c.scale(unit, unit)
        val step = time * 2.1f
        val gait = sin(step) * 3f
        oval(c, 0f, 3f, 52f, 12f, Color.argb(130, 6, 8, 19))
        // Legs and boots
        line(c, -17f, -35f, -22f + gait, -12f, 15f, ink)
        line(c, 13f, -35f, 16f - gait, -12f, 15f, ink)
        round(c, -39f + gait, -16f, -12f + gait, 0f, 7f, Color.rgb(65, 75, 97))
        round(c, 10f - gait, -16f, 38f - gait, 0f, 7f, Color.rgb(65, 75, 97))
        // Backpack with batteries, oxygen tanks and lit progress panel
        round(c, -53f, -95f, -22f, -38f, 9f, ink)
        round(c, -48f, -89f, -28f, -44f, 6f, Color.rgb(37, 66, 93))
        round(c, -43f, -83f, -33f, -66f, 3f, cyan)
        disc(c, -38f, -55f, 4.5f, gold)
        // Torso and utility belt
        poly(c, ink, -33f, -89f, 24f, -89f, 33f, -36f, -33f, -34f)
        poly(c, Color.rgb(221, 133, 48), -28f, -85f, 22f, -85f, 25f, -43f, -28f, -43f)
        poly(c, Color.rgb(245, 177, 77), 0f, -85f, 20f, -85f, 22f, -49f, -1f, -49f)
        round(c, -31f, -49f, 29f, -39f, 3f, ink)
        round(c, -5f, -49f, 11f, -39f, 2f, gold)
        // Neck / face, helmet with dark glass
        disc(c, 0f, -113f, 32f, ink)
        disc(c, 0f, -115f, 27f, Color.rgb(246, 160, 63))
        round(c, -26f, -116f, 23f, -98f, 10f, Color.rgb(20, 48, 78))
        p.shader = LinearGradient(-15f, -115f, 20f, -99f,
            Color.rgb(126, 250, 255), Color.rgb(18, 115, 177), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(-21f, -112f, 18f, -101f), 7f, 7f, p); p.shader = null
        // Helmet hard-hat, lamp, rim
        oval(c, 0f, -135f, 34f, 20f, ink)
        oval(c, 0f, -138f, 29f, 17f, gold)
        line(c, -33f, -128f, 34f, -128f, 7f, copper)
        round(c, 15f, -148f, 32f, -132f, 5f, ink)
        aura(c, 25f, -139f, 27f, cyan, 128)
        disc(c, 25f, -139f, 6f, cyan)
        // Foreground arm, swinging pickaxe (periodic contact with obelisk)
        val swing = (sin(time * 5.1f) * 0.62f - 0.13f)
        line(c, 23f, -78f, 42f, -60f, 18f, ink)
        line(c, 23f, -78f, 42f, -60f, 12f, Color.rgb(228, 152, 52))
        c.save(); c.translate(42f, -60f); c.rotate(swing * 65f)
        disc(c, 0f, 0f, 12f, Color.rgb(59, 78, 107))
        line(c, 0f, 0f, 65f, -88f, 9f, ink)
        line(c, 0f, 0f, 65f, -88f, 6f, Color.rgb(152, 93, 46))
        poly(c, ink, 35f, -99f, 91f, -99f, 115f, -78f, 85f, -87f, 48f, -81f, 17f, -61f)
        poly(c, Color.rgb(168, 211, 229), 41f, -96f, 92f, -96f, 110f, -83f, 85f, -85f, 49f, -77f, 23f, -65f)
        line(c, 50f, -93f, 82f, -91f, 3f, Color.rgb(238, 250, 255))
        c.restore()
        // Rear balancing arm
        line(c, -27f, -76f, -45f, -57f, 15f, ink)
        line(c, -27f, -76f, -45f, -57f, 9f, copper)
        disc(c, -45f, -57f, 11f, Color.rgb(61, 78, 98))
        // Miner progression glow and cosmetic upgrades
        if (gearLevel >= 3) {
            aura(c, 0f, -80f, 77f, glow, 30)
            round(c, -12f, -81f, 12f, -67f, 4f, Color.rgb(44, 111, 152))
            disc(c, 0f, -75f, 4f, cyan)
        }
        if (gearLevel >= 10) {
            // Electric jet assisted boots
            for (i in 0..1) {
                val tx = if (i == 0) -24f else 20f
                val flame = 5f + (1f + sin(time * 19f + i)) * 5f
                poly(c, Color.rgb(70, 214, 255), tx - 5f, 0f, tx + 5f, 0f, tx, flame)
            }
        }
        c.restore()
    }

    /** Automatic machine progression: visible when the player owns miners. */
    fun drawCompanions(c: Canvas, w: Float, base: Float, time: Float,
                       miners: IntArray, theme: BiomeArt.Theme) {
        val unit = (w / 720f).coerceIn(0.55f, 1.7f)
        if (miners.getOrElse(0) { 0 } > 0) {
            // The very first miner purchase now changes the actual scene.
            // Apprentice animates independently of the lead player character.
            c.save()
            c.translate(w * 0.74f, base - 5f * unit)
            c.scale(unit * 0.60f, unit * 0.60f)
            val bounce = sin(time * 4.9f) * 4f
            oval(c, 0f, 4f, 48f, 11f, Color.argb(100, 5, 11, 24))
            line(c, -16f, -32f, -21f, -8f, 13f, ink)
            line(c, 13f, -32f, 19f, -8f, 13f, ink)
            round(c, -37f, -12f, -12f, 3f, 5f, Color.rgb(52, 67, 88))
            round(c, 8f, -12f, 35f, 3f, 5f, Color.rgb(52, 67, 88))
            round(c, -25f, -83f + bounce, 26f, -32f + bounce, 9f, ink)
            round(c, -19f, -78f + bounce, 18f, -39f + bounce, 6f, Color.rgb(205, 137, 56))
            disc(c, 0f, -110f + bounce, 29f, ink)
            disc(c, 0f, -111f + bounce, 24f, Color.rgb(238, 169, 75))
            round(c, -24f, -113f + bounce, 23f, -100f + bounce, 7f, Color.rgb(38, 70, 95))
            round(c, -18f, -111f + bounce, 18f, -103f + bounce, 4f, cyan)
            oval(c, 0f, -133f + bounce, 30f, 13f, gold)
            line(c, -30f, -127f + bounce, 31f, -127f + bounce, 6f, ink)
            val swing = sin(time * 5.0f) * 36f
            c.save()
            c.translate(24f, -72f + bounce)
            c.rotate(swing)
            line(c, 0f, 0f, 52f, -52f, 9f, ink)
            line(c, 0f, 0f, 52f, -52f, 5f, copper)
            poly(c, Color.rgb(175, 220, 236), 28f, -65f, 69f, -66f, 88f, -52f, 58f, -57f)
            c.restore()
            c.restore()
        }
        if (miners.getOrElse(1) { 0 } > 0) {
            c.save(); c.translate(w * 0.86f, base - 35f * unit); c.scale(unit, unit)
            val hover = sin(time * 2.2f) * 7f
            aura(c, 0f, -56f + hover, 70f, theme.glow, 80)
            round(c, -37f, -82f + hover, 37f, -38f + hover, 18f, ink)
            round(c, -31f, -79f + hover, 31f, -44f + hover, 15f, Color.rgb(75, 99, 132))
            oval(c, 0f, -60f + hover, 23f, 11f, cyan)
            line(c, -32f, -53f + hover, -56f, -25f + hover, 7f, Color.rgb(151, 171, 184))
            line(c, 32f, -53f + hover, 56f, -25f + hover, 7f, Color.rgb(151, 171, 184))
            disc(c, 0f, -89f + hover, 9f, gold)
            c.restore()
        }
        if (miners.getOrElse(2) { 0 } > 0) {
            c.save(); c.translate(w * 0.87f, base - 4f * unit); c.scale(unit, unit)
            round(c, -55f, -25f, 53f, -6f, 7f, ink)
            round(c, -44f, -78f, 33f, -24f, 12f, Color.rgb(68, 86, 106))
            round(c, -35f, -68f, 12f, -41f, 4f, Color.rgb(38, 53, 73))
            disc(c, -12f, -55f, 9f, theme.glow)
            line(c, 20f, -53f, 52f, -113f + sin(time * 7f) * 5f, 12f, ink)
            line(c, 20f, -53f, 52f, -113f + sin(time * 7f) * 5f, 7f, gold)
            disc(c, 52f, -117f + sin(time * 7f) * 5f, 10f, theme.glow)
            c.restore()
        }

        if (miners.getOrElse(3) { 0 } > 0) {
            // Fuse-lit blast charge periodically thrown toward the monolith.
            val phase = (time * 0.31f) % 1f
            val bx = w * (0.76f - phase * 0.14f)
            val by = base - (45f + 90f * sin(phase * PI.toFloat())) * unit
            aura(c, bx, by, 42f * unit, Color.rgb(255, 96, 38), 72)
            disc(c, bx, by, 16f * unit, ink)
            disc(c, bx - 3f * unit, by - 3f * unit, 8f * unit, Color.rgb(87, 91, 106))
            line(c, bx, by - 15f * unit, bx + 10f * unit, by - 30f * unit, 4f * unit, gold)
            disc(c, bx + 11f * unit, by - 30f * unit, 5f * unit, Color.rgb(255, 204, 78))
        }
        if (miners.getOrElse(4) { 0 } > 0) {
            // Stone golem, a much larger shadowed teammate walking in the back.
            c.save(); c.translate(w * 0.77f, base - 15f * unit); c.scale(unit * 0.75f, unit * 0.75f)
            val bob = sin(time * 1.75f) * 4f
            aura(c, 0f, -125f, 107f, theme.glow, 38)
            poly(c, ink, -51f, -80f, -59f, -38f, -28f, -3f, -1f, -36f, 22f, -3f, 50f, -39f, 37f, -86f)
            poly(c, Color.rgb(69, 86, 103), -51f, -80f, -40f, -143f + bob, 13f, -166f + bob, 50f, -110f, 37f, -86f)
            poly(c, Color.rgb(112, 129, 135), -40f, -143f + bob, -4f, -153f + bob, -8f, -95f, -51f, -80f)
            round(c, -54f, -124f + bob, 49f, -82f + bob, 8f, Color.rgb(54, 77, 93))
            line(c, -54f, -110f + bob, -79f, -59f + bob, 19f, ink)
            line(c, 49f, -110f + bob, 76f, -60f + bob, 19f, ink)
            disc(c, -16f, -133f + bob, 8f, theme.glow)
            disc(c, 18f, -133f + bob, 8f, theme.glow)
            c.restore()
        }
        if (miners.getOrElse(5) { 0 } > 0) {
            // Laser turret aimed at the monolith - live moving scan line.
            val x = w * 0.87f
            val y = base - 208f * unit
            val target = base - (200f + 33f * sin(time * 2.3f)) * unit
            aura(c, x, y, 57f * unit, cyan, 72)
            line(c, x, y, w * 0.52f, target, 7f * unit, Color.argb(72, 69, 199, 255))
            line(c, x, y, w * 0.52f, target, 2.5f * unit, Color.rgb(143, 246, 255))
            disc(c, x, y, 17f * unit, ink)
            disc(c, x, y, 10f * unit, cyan)
            disc(c, w * 0.52f, target, 8f * unit, Color.rgb(232, 255, 255))
        }
        if (miners.getOrElse(6) { 0 } > 0) {
            // End-game void excavation portal.
            val x = w * 0.83f
            val y = base - 314f * unit
            val r = 54f * unit
            aura(c, x, y, r * 1.7f, Color.rgb(183, 98, 255), 120)
            for (i in 0..2) {
                val rr = r * (1.0f - i * 0.20f)
                pen.color = Color.argb(210 - i * 48, 197, 109, 255)
                pen.strokeWidth = 5f * unit
                c.drawArc(RectF(x - rr, y - rr, x + rr, y + rr),
                    time * (40f + 10f * i) + i * 74f, 225f, false, pen)
            }
            disc(c, x, y, r * 0.46f, Color.rgb(31, 14, 62))
        }
    }

    /** Crisp, polished impact rays plus ember/crystal fragments over the existing effects. */
    fun drawImpact(c: Canvas, x: Float, y: Float, radius: Float, color: Int,
                   time: Float, intensity: Float) {
        val r = radius * (0.50f + intensity * 0.75f)
        aura(c, x, y, r * 2.5f, color, (110f * intensity).toInt().coerceIn(0, 255))
        for (i in 0..7) {
            val angle = i * PI.toFloat() / 4f + time * 0.3f
            val inner = r * 0.35f
            val outer = r * (0.90f + 0.17f * sin(time * 5f + i))
            line(c, x + cos(angle) * inner, y + sin(angle) * inner,
                x + cos(angle) * outer, y + sin(angle) * outer,
                2f + 2f * intensity, Color.argb((230f * intensity).toInt().coerceIn(0, 255),
                    Color.red(color), Color.green(color), Color.blue(color)))
        }
        disc(c, x, y, 3.6f + r * 0.12f, Color.WHITE)
    }

    /** A guardian isn't just a red health bar: animated concentric rune rings surround it. */
    fun drawGuardian(c: Canvas, x: Float, y: Float, radius: Float,
                     time: Float, color: Int) {
        c.save(); c.translate(x, y)
        aura(c, 0f, 0f, radius * 1.30f, color, 75)
        for (j in 0..1) {
            val r = radius * (0.65f + j * 0.24f)
            pen.color = Color.argb(if (j == 0) 145 else 90,
                Color.red(color), Color.green(color), Color.blue(color))
            pen.strokeWidth = if (j == 0) 4f else 2.5f
            c.drawArc(RectF(-r, -r, r, r), time * (if (j == 0) 35f else -21f),
                210f, false, pen)
        }
        for (i in 0..7) {
            val angle = i * PI.toFloat() / 4f + time * 0.26f
            val r = radius * 0.89f
            val x0 = cos(angle) * r
            val y0 = sin(angle) * r
            poly(c, color, x0 - 6f, y0, x0 + 6f, y0 - 7f, x0 + 6f, y0 + 7f)
        }
        c.restore()
    }

    /** Echo nodes introduce a different silhouette and floating reward shards. */
    fun drawEchoNode(c: Canvas, x: Float, y: Float, radius: Float, time: Float) {
        val blue = Color.rgb(95, 242, 255)
        aura(c, x, y, radius * 1.55f, blue, 90)
        for (i in 0..5) {
            val angle = i * PI.toFloat() / 3f + time * 0.32f
            val rad = radius * (0.83f + 0.04f * sin(time * 2f + i))
            val px = x + cos(angle) * rad
            val py = y + sin(angle) * rad * 0.70f
            c.save(); c.translate(px, py); c.rotate(time * 37f + i * 33f)
            poly(c, Color.rgb(11, 81, 126), 0f, -16f, 11f, 0f, 0f, 16f, -11f, 0f)
            poly(c, Color.rgb(132, 255, 250), 0f, -16f, 11f, 0f, 0f, 16f)
            c.restore()
        }
        pen.color = Color.argb(105, 125, 243, 251)
        pen.strokeWidth = 2.5f
        c.drawOval(RectF(x - radius, y - radius * 0.69f, x + radius, y + radius * 0.69f), pen)
    }

    /** Atmospheric foreground mist plus moving dust specks. */
    fun foreground(c: Canvas, w: Float, h: Float, base: Float, time: Float,
                   theme: BiomeArt.Theme) {
        val fog = Color.argb(28, Color.red(theme.glow), Color.green(theme.glow), Color.blue(theme.glow))
        p.shader = LinearGradient(0f, base - h * 0.08f, 0f, h, Color.TRANSPARENT, fog, Shader.TileMode.CLAMP)
        c.drawRect(0f, base - h * 0.08f, w, h, p); p.shader = null
        for (i in 0..26) {
            val x = w * ((i * 37 + 17) % 103) / 103f +
                sin(time * 0.45f + i) * w * 0.018f
            val y = h * (0.18f + ((i * 31) % 72) / 100f) -
                (time * (4f + i % 4) % (h * 0.1f))
            disc(c, x, y, if (i % 5 == 0) 2.3f else 1.2f,
                Color.argb(60 + i % 5 * 24, Color.red(theme.glow), Color.green(theme.glow), Color.blue(theme.glow)))
        }
    }
}

