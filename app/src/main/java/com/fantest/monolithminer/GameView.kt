package com.fantest.monolithminer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var state: GameState? = null
    /** Scenery-only preview mode: touch mining is disabled while active. */
    var previewDepth: Int? = null
    var onTapListener: ((Float, Float) -> Unit)? = null
    var onFrame: ((Double) -> Unit)? = null

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float
    )

    private class FloatText(
        var x: Float, var y: Float, val text: String, var life: Float,
        val color: Int, val size: Float
    )

    private class Star(val x: Float, val y: Float, val r: Float, val phase: Float)
    private class Crack(val pts: List<Pair<Float, Float>>)

    private val particles = ArrayList<Particle>()
    private val floats = ArrayList<FloatText>()
    private var stars = listOf<Star>()
    private var cracks = listOf<Crack>()
    private var crackDepth = -1

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private var running = false
    private var lastNanos = 0L
    private var time = 0f
    private var shake = 0f
    private var flash = 0f
    private var lastDepth = -1
    private var idleTextTimer = 0f
    private var impactX = 0f
    private var impactY = 0f
    private var impactLife = 0f

    private var obX = 0f
    private var obTop = 0f
    private var obBase = 0f
    private var obW = 0f

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = ((now - lastNanos) / 1e9).coerceIn(0.0, 0.1)
            lastNanos = now
            step(dt.toFloat())
            onFrame?.invoke(dt)
            invalidate()
            // Limit continuous full-scene redraws to roughly 60 fps, even on
            // 120 Hz screens, to reduce CPU/GPU and battery load.
            postDelayed(this, 16L)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastNanos = System.nanoTime()
        postOnAnimation(frame)
    }

    fun stop() {
        running = false
        removeCallbacks(frame)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val rnd = Random(7)
        stars = List(70) {
            Star(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.75f, 0.8f + rnd.nextFloat() * 1.8f, rnd.nextFloat() * 6.28f)
        }
        obX = w / 2f
        obBase = h * 0.86f
        obTop = h * 0.20f
        obW = min(w * 0.30f, h * 0.30f)
        crackDepth = -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex
            if (previewDepth == null) onTapListener?.invoke(e.getX(i), e.getY(i))
            return true
        }
        return true
    }

    // ----- Effects API -----

    fun tapEffect(x: Float, y: Float, damage: Double, crit: Boolean) {
        shake = min(1f, shake + if (crit) 0.9f else 0.35f)
        impactX = x
        impactY = y
        impactLife = if (crit) 0.50f else 0.28f
        val color = if (crit) Color.rgb(255, 120, 90) else Color.rgb(255, 214, 120)
        spawnBurst(x, y, if (crit) 14 else 6, color)
        floats.add(FloatText(x + Random.nextFloat() * 40f - 20f, y - 20f, (if (crit) "CRIT " else "") + Fmt.num(damage), 0.9f, color, if (crit) 46f else 34f))
        if (floats.size > 50) floats.subList(0, floats.size - 50).clear()
    }

    private fun spawnBurst(x: Float, y: Float, n: Int, color: Int) {
        repeat(n) {
            val ang = Random.nextFloat() * 2f * PI.toFloat()
            val sp = 140f + Random.nextFloat() * 380f
            particles.add(
                Particle(x, y, cos(ang) * sp, sin(ang) * sp - 120f, 0.5f + Random.nextFloat() * 0.5f, 1f, color, 3f + Random.nextFloat() * 5f)
            )
        }
        if (particles.size > 400) particles.subList(0, particles.size - 400).clear()
    }

    // ----- Simulation -----

    private fun step(dt: Float) {
        time += dt
        shake = max(0f, shake - dt * 3.2f)
        flash = max(0f, flash - dt * 2.5f)
        impactLife = max(0f, impactLife - dt)

        val s = state ?: return
        if (lastDepth != s.depth) {
            if (lastDepth != -1 && s.depth > lastDepth) {
                flash = 1f
                spawnBurst(obX, (obTop + obBase) / 2f, 30, BiomeArt.forDepth(s.depth).glow)
            }
            lastDepth = s.depth
        }

        idleTextTimer += dt
        if (idleTextTimer > 0.8f) {
            idleTextTimer = 0f
            val d = s.dps()
            if (d > 0) {
                floats.add(FloatText(obX + Random.nextFloat() * obW - obW / 2f, obBase - 40f, "+" + Fmt.num(d * 0.8), 1.0f, Color.argb(200, 160, 220, 255), 26f))
            }
        }

        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.vy += 900f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        val ft = floats.iterator()
        while (ft.hasNext()) {
            val f = ft.next()
            f.life -= dt
            if (f.life <= 0f) { ft.remove(); continue }
            f.y -= 90f * dt
        }
    }

    private fun buildCracks(depth: Int) {
        val rnd = Random(depth * 31 + 5)
        val list = ArrayList<Crack>()
        repeat(14) {
            var x = obX + (rnd.nextFloat() - 0.5f) * obW * 0.6f
            var y = obTop + (obBase - obTop) * (0.15f + rnd.nextFloat() * 0.7f)
            val pts = ArrayList<Pair<Float, Float>>()
            pts.add(x to y)
            repeat(4) {
                x += (rnd.nextFloat() - 0.5f) * obW * 0.22f
                y += rnd.nextFloat() * obW * 0.16f
                pts.add(x to y)
            }
            list.add(Crack(pts))
        }
        cracks = list
        crackDepth = depth
    }

    // ----- Drawing -----

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = state

        // M3: procedural atmospheric layers selected by depth.
        val depthToDraw = previewDepth ?: (s?.depth ?: 1)
        val biome = BiomeArt.forDepth(depthToDraw)
        BiomeArt.drawBackdrop(canvas, fill, w, h, obBase, time, biome)
        BiomeArt.drawWeather(canvas, fill, w, h, obBase, time, biome)
        if (biome.index == 1 || biome.index == 3 || biome.index == 4) {
            for (st in stars) {
                val a = (65 + 80 * sin(time * 1.6f + st.phase)).toInt().coerceIn(15, 170)
                fill.color = Color.argb(a, 206, 240, 255)
                canvas.drawCircle(st.x, st.y, st.r, fill)
            }
        }

        if (s == null) return
        if (crackDepth != depthToDraw) buildCracks(depthToDraw)

        val boss = previewDepth == null && s.isBoss(depthToDraw)
        val echo = previewDepth == null && s.isEchoNode(depthToDraw)
        val bodyDark = biome.bodyDark
        val bodyLight = biome.bodyLight
        val glow = if (boss) Color.rgb(255, 80, 120)
            else if (echo) Color.rgb(79, 236, 255) else biome.glow

        // Animated guardian rings, visible behind the monolith silhouette.
        if (boss) SceneActors.drawGuardian(canvas, obX, (obTop + obBase) / 2f,
            (obBase - obTop) * 0.49f, time, glow)
        if (echo) SceneActors.drawEchoNode(canvas, obX, (obTop + obBase) / 2f,
            (obBase - obTop) * 0.46f, time)

        // Glow behind obelisk
        val cy = (obTop + obBase) / 2f
        val glowR = (obBase - obTop) * 0.75f
        val pulse = 0.55f + 0.25f * sin(time * 2f)
        fill.shader = RadialGradient(obX, cy, glowR, Color.argb((90 * pulse + flash * 120).toInt().coerceIn(0, 255), Color.red(glow), Color.green(glow), Color.blue(glow)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(obX, cy, glowR, fill)
        fill.shader = null

        canvas.save()
        val sx = (sin(time * 70f) * 8f * shake)
        canvas.translate(sx, 0f)

        // Obelisk body
        val bw = obW
        val tw = obW * 0.62f
        val tipY = obTop
        val shoulderY = obTop + (obBase - obTop) * 0.14f
        val path = BiomeArt.monolithPath(obX, tipY, obBase, bw, biome)
        fill.shader = LinearGradient(obX - bw / 2f, 0f, obX + bw / 2f, 0f, intArrayOf(bodyDark, bodyLight, bodyDark), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, fill)
        fill.shader = null

        // Right-side facet shading
        val facet = Path().apply {
            moveTo(obX, tipY)
            lineTo(obX + tw / 2f, shoulderY)
            lineTo(obX + bw / 2f, obBase)
            lineTo(obX, obBase)
            close()
        }
        fill.color = Color.argb(65, 0, 0, 0)
        canvas.save()
        canvas.clipPath(path)
        canvas.drawPath(facet, fill)
        canvas.restore()

        stroke.strokeWidth = 3f
        stroke.color = Color.argb(160, Color.red(glow), Color.green(glow), Color.blue(glow))
        canvas.drawPath(path, stroke)

        // Runes
        val runeA = (120 + 110 * sin(time * 3f)).toInt().coerceIn(40, 230)
        fill.color = Color.argb(runeA, Color.red(glow), Color.green(glow), Color.blue(glow))
        for (i in 0 until 5) {
            val ry = shoulderY + (obBase - shoulderY) * (0.12f + i * 0.17f)
            val half = (tw / 2f + (bw - tw) / 2f * ((ry - shoulderY) / (obBase - shoulderY))) * 0.18f
            val rp = Path().apply {
                moveTo(obX, ry - half)
                lineTo(obX + half * 0.7f, ry)
                lineTo(obX, ry + half)
                lineTo(obX - half * 0.7f, ry)
                close()
            }
            canvas.drawPath(rp, fill)
        }

        // Cracks reveal as HP drops
        val frac = (s.hp / s.maxHp()).toFloat().coerceIn(0f, 1f)
        val shown = ((1f - frac) * cracks.size).toInt()
        stroke.strokeWidth = 3.5f
        stroke.color = Color.argb(205, Color.red(glow), Color.green(glow), Color.blue(glow))
        for (i in 0 until min(shown, cracks.size)) {
            val c = cracks[i]
            val cp = Path()
            cp.moveTo(c.pts[0].first, c.pts[0].second)
            for (j in 1 until c.pts.size) cp.lineTo(c.pts[j].first, c.pts[j].second)
            canvas.drawPath(cp, stroke)
        }
        canvas.restore()

        // Layered foreground with animated miners and purchased machinery.
        SceneActors.foreground(canvas, w, h, obBase, time, biome)
        SceneActors.drawMiner(canvas, w * 0.16f, obBase + 5f, w, time,
            s.totalTaps, s.minerLevels.sum(), glow)
        SceneActors.drawCompanions(canvas, w, obBase, time, s.minerLevels, biome)
        if (impactLife > 0f) {
            SceneActors.drawImpact(canvas, impactX, impactY, 44f,
                if (boss) Color.rgb(255, 95, 112) else biome.glow,
                time, (impactLife / 0.50f).coerceIn(0f, 1f))
        }

        // Particles
        for (p in particles) {
            val a = (255 * (p.life / p.maxLife).coerceIn(0f, 1f)).toInt()
            fill.color = Color.argb(a, Color.red(p.color), Color.green(p.color), Color.blue(p.color))
            canvas.drawCircle(p.x, p.y, p.size, fill)
        }

        // Floating numbers
        for (f in floats) {
            val a = (255 * min(1f, f.life * 1.6f)).toInt().coerceIn(0, 255)
            textPaint.textSize = f.size
            textPaint.color = Color.argb(a, Color.red(f.color), Color.green(f.color), Color.blue(f.color))
            canvas.drawText(f.text, f.x, f.y, textPaint)
        }

        // HP bar + depth header
        val barW = w * 0.72f
        val barH = 22f
        val barX = (w - barW) / 2f
        val barY = 52f
        textPaint.color = Color.WHITE
        textPaint.textSize = 34f
        canvas.drawText(
            if (previewDepth != null) "PREVIEW • ${biome.title}"
            else if (boss) "Depth ${s.depth}  -  GUARDIAN"
            else if (echo) "Depth ${s.depth}  -  ECHO NODE"
            else "Depth ${s.depth}", w / 2f, 38f, textPaint)
        fill.color = Color.argb(150, 0, 0, 0)
        val r = RectF(barX, barY, barX + barW, barY + barH)
        canvas.drawRoundRect(r, 11f, 11f, fill)
        fill.color = if (boss) Color.rgb(255, 80, 120) else if (echo) Color.rgb(66, 231, 255) else Color.rgb(255, 181, 71)
        canvas.drawRoundRect(RectF(barX, barY, barX + barW * frac, barY + barH), 11f, 11f, fill)
        textPaint.textSize = 22f
        textPaint.color = Color.WHITE
        canvas.drawText(
            if (previewDepth != null) "Scenery preview — no mining"
            else Fmt.num(s.hp) + " / " + Fmt.num(s.maxHp()),
            w / 2f, barY + barH + 28f, textPaint)
        textPaint.textSize = 20f
        textPaint.color = Color.argb(225, 240, 239, 246)
        canvas.drawText(biome.title, w / 2f, barY + barH + 52f, textPaint)

        // Non-modal new-player hint, fades out permanently after five taps.
        if (previewDepth == null && s.totalTaps < 5L) {
            val alpha = (140 + 55 * sin(time * 2.6f)).toInt().coerceIn(70, 215)
            val hint = "TAP TO MINE  •  BUY MINERS BELOW"
            textPaint.textSize = (w * 0.028f).coerceIn(14f, 24f)
            val boxY = h * 0.95f
            fill.color = Color.argb(145, 8, 18, 32)
            canvas.drawRoundRect(RectF(w * 0.17f, boxY - 29f, w * 0.83f, boxY + 13f), 14f, 14f, fill)
            textPaint.color = Color.argb(alpha, 255, 206, 97)
            canvas.drawText(hint, w * 0.5f, boxY, textPaint)
        }

        // Break flash
        if (flash > 0f) {
            fill.color = Color.argb((flash * 90).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawRect(0f, 0f, w, h, fill)
        }
    }
}


