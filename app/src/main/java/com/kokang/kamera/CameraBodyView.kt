package com.kokang.kamera

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Punggung kamera saku plastik awal 2000-an. Layar ponsel = punggung kamera:
 * jendela bidik mungil, LED AF, roda kokang, tombol rana, saklar flash/date,
 * engkol rewind, jendela film, dan kait pintu belakang.
 */
class CameraBodyView(ctx: Context) : View(ctx) {

    interface Listener {
        /** true bila roda boleh diputar */
        fun onWindAttempt(): Boolean
        fun onWindTick()
        fun onCocked()
        fun onShutter()
        fun onFlash()
        fun onDate()
        fun onRewind(deg: Float)
        /** true when the door may open; otherwise the latch only jiggles */
        fun onLatchAttempt(): Boolean
        fun onLatch()
        fun onLab()
    }

    var st: CamState? = null
    var listener: Listener? = null
    var onVfLayout: ((RectF) -> Unit)? = null

    private var u = 10f
    private val vfHouse = RectF()
    val vf = RectF()
    private val wheel = RectF()
    private val door = RectF()
    private val window = RectF()
    private val latch = RectF()
    private val grip = RectF()
    private val flashSw = RectF()
    private val dateSw = RectF()
    private val labBtn = RectF()
    private var shX = 0f; private var shY = 0f; private var shR = 0f
    private var crX = 0f; private var crY = 0f; private var crR = 0f
    private var cnX = 0f; private var cnY = 0f; private var cnR = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    enum class Alert { WIND, LOCK, NOFILM, REWIND, OPEN, OK, ERR, LOADED }
    private val mat = RectF()
    private var alertSeq: List<BooleanArray> = emptyList()
    private var alertUntil = 0L
    private var cntBmp: Bitmap? = null
    private var cntVerts = FloatArray(0)
    private var lastLabel: String? = null
    private var prevLabel = ""
    private var cntAnim = 1f
    private var cntAnimator: ValueAnimator? = null

    private var target = 0
    private var lastX = 0f
    private var lastAng = 0f
    private var tickAcc = 0f
    private var shutterDown = false
    private var windBlocked = false
    private var latchPos = 0f          // 0 = up, 1 = slid fully down
    private var latchLimit = 1f
    private var latchFired = false
    private var lastY = 0f
    private var latchAnim: ValueAnimator? = null
    private var decals: Bitmap? = null
    private val knobH get() = 12f * u
    private val latchTravel get() = latch.height() - knobH - 1.6f * u
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    /** Shows a symbol (or symbol + number/code, alternating) on the dot matrix for a couple of seconds. */
    fun alert(a: Alert, num: Int = -1) {
        val n = DotMatrix.text(if (num >= 0) num.coerceAtMost(99).toString() else "")
        alertSeq = when (a) {
            Alert.WIND -> listOf(DotMatrix.icon("wind"))
            Alert.LOCK -> listOf(DotMatrix.icon("lock"), n)
            Alert.NOFILM -> listOf(DotMatrix.icon("film"), DotMatrix.text("NO"))
            Alert.REWIND -> listOf(DotMatrix.icon("cw"), DotMatrix.text("RW"))
            Alert.OPEN -> listOf(DotMatrix.icon("down"), DotMatrix.text("OP"))
            Alert.OK -> listOf(DotMatrix.text("OK"))
            Alert.ERR -> listOf(DotMatrix.icon("warn"), DotMatrix.text("ER"))
            Alert.LOADED -> listOf(DotMatrix.icon("film"), n)
        }
        alertUntil = System.currentTimeMillis() + 2800
        invalidate()
    }

    private val ticker = object : Runnable {
        override fun run() { invalidate(); postDelayed(this, 300) }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(ticker) }
    override fun onDetachedFromWindow() { removeCallbacks(ticker); super.onDetachedFromWindow() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val W = w.toFloat(); val H = h.toFloat()
        u = H / 100f
        val vcx = W * 0.44f
        vfHouse.set(vcx - 17 * u, 5 * u, vcx + 17 * u, 31 * u)
        vf.set(vcx - 12 * u, 10 * u, vcx + 12 * u, 26 * u)
        cnX = maxOf(W - 68 * u, vfHouse.right + 21 * u); cnY = 8.5f * u; cnR = 5.2f * u
        wheel.set(maxOf(W - 56 * u, cnX + 7 * u), 2.2f * u, W - 12 * u, 11 * u)
        door.set(36 * u, 36 * u, W - 60 * u, 95 * u)
        window.set(door.left + 8 * u, door.centerY() - 18 * u, door.left + 27 * u, door.centerY() + 18 * u)
        latch.set(door.right - 4 * u, door.centerY() - 15 * u, door.right + 6 * u, door.centerY() + 15 * u)
        grip.set(W - 52 * u, 19 * u, W - 3 * u, 97 * u)
        shX = W - 27 * u; shY = 54 * u; shR = 13.5f * u
        labBtn.set(W - 47 * u, 82 * u, W - 8 * u, 92 * u)
        flashSw.set(8 * u, 25 * u, 26 * u, 33 * u)
        dateSw.set(8 * u, 46 * u, 26 * u, 54 * u)
        crX = 17 * u; crY = 78 * u; crR = 12 * u
        mat.set(vfHouse.left - 15 * u, 3 * u, vfHouse.left - 4 * u, 14 * u)
        val d = (cnR * 2).toInt().coerceAtLeast(8)
        cntBmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
        // mesh that magnifies the centre like a convex lens
        val n = 12
        cntVerts = FloatArray((n + 1) * (n + 1) * 2)
        for (j in 0..n) for (i in 0..n) {
            val nx = i * 2f / n - 1f; val ny = j * 2f / n - 1f
            val r = hypot(nx, ny)
            val k = if (r > 0.001f && r < 1f) r.pow(0.62f) / r else 1f
            cntVerts[(j * (n + 1) + i) * 2] = (nx * k + 1f) * d / 2f
            cntVerts[(j * (n + 1) + i) * 2 + 1] = (ny * k + 1f) * d / 2f
        }
        onVfLayout?.invoke(vf)
        decals = if (w > 0 && h > 0) buildDecals(w, h) else null
    }

    // ------------------------------------------------------------------ gambar

    override fun onDraw(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat()
        val s = st
        c.drawColor(Color.BLACK)
        p.alpha = 255

        // badan plastik perak-sampanye
        p.style = Paint.Style.FILL
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, H,
            intArrayOf(0xFFDAD7CE.toInt(), 0xFFC6C3B9.toInt(), 0xFFA8A59B.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, W, H, 7 * u, 7 * u, p)
        // pelat atas
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, 16 * u,
            intArrayOf(0xFFE9E7E0.toInt(), 0xFFBDBAB0.toInt()), null, Shader.TileMode.CLAMP)
        c.save(); c.clipRect(0f, 0f, W, 16 * u)
        c.drawRoundRect(0f, 0f, W, 30 * u, 7 * u, 7 * u, p)
        c.restore()
        p.shader = null
        p.color = 0x55000000; c.drawRect(0f, 16 * u, W, 16.4f * u, p)
        p.color = 0x66FFFFFF; c.drawRect(0f, 16.4f * u, W, 16.8f * u, p)

        // brand: red script wordmark, debossed into the top plate
        val script = Typeface.create("cursive", Typeface.BOLD)
        val lx = 6.5f * u; val ly = 12.2f * u; val ls = 10.5f * u
        text("Conan", lx + 0.28f * u, ly + 0.3f * u, ls, 0xD0FFFFFF.toInt(), script)      // lit lower rim
        text("Conan", lx - 0.22f * u, ly - 0.24f * u, ls, 0xB0000000.toInt(), script)    // shadowed upper wall
        tp.shader = LinearGradient(0f, ly - 7 * u, 0f, ly + 2 * u,
            intArrayOf(0xFF6E0E0B.toInt(), 0xFFB81F18.toInt(), 0xFFD9342A.toInt()), null, Shader.TileMode.CLAMP)
        text("Conan", lx, ly, ls, 0xFFB81F18.toInt(), script)
        tp.shader = null
        tp.typeface = script; tp.textSize = ls
        val bw = tp.measureText("Conan")
        text("AF-35", 9.5f * u + bw, 11 * u, 3.2f * u, 0xFF4A4840.toInt(), Typeface.create(Typeface.SERIF, Typeface.BOLD))
        text("DATE", 9.5f * u + bw, 6.6f * u, 2.1f * u, 0xFF6A685F.toInt(), Typeface.DEFAULT_BOLD)

        drawGrip(c)
        drawDoor(c, s)
        decals?.let { c.drawBitmap(it, 0f, 0f, null) }
        drawWindow(c, s)
        drawLatch(c)
        drawViewfinder(c, s)
        drawCounter(c, s)
        drawWheel(c, s)
        drawSwitch(c, flashSw, s?.flash == true, "FLASH")
        drawSwitch(c, dateSw, s?.date == true, "DATE")
        drawCrank(c, s)
        drawShutter(c)

        // tombol amplop hasil cuci
        p.color = 0xFF2A2927.toInt()
        c.drawRoundRect(labBtn, 2 * u, 2 * u, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.3f * u; p.color = 0xFF6B6962.toInt()
        c.drawRoundRect(labBtn, 2 * u, 2 * u, p)
        p.style = Paint.Style.FILL
        text("SHELF  ▸", labBtn.centerX(), labBtn.centerY() + 1.2f * u, 3.2f * u, 0xFFE8D9A8.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        drawMatrix(c, s)
    }

    private fun drawMatrix(c: Canvas, s: CamState?) {
        val now = System.currentTimeMillis()
        var grid: BooleanArray? = null
        if (now < alertUntil && alertSeq.isNotEmpty()) {
            val t = (alertUntil - now)
            grid = if (alertSeq.size == 1) { if (t / 300 % 4 != 0L) alertSeq[0] else null }   // blink
                   else alertSeq[(((2800 - t) / 700) % alertSeq.size).toInt()]                // alternate
        } else if (s != null) {
            val items = ArrayList<BooleanArray>()
            val f = s.film
            if (f == null) { items.add(DotMatrix.icon("film")); items.add(DotMatrix.text("NO")) }
            else {
                val left = f.frames - s.frames
                if (s.rewound) { items.add(DotMatrix.icon("down")); items.add(DotMatrix.text("OP")) }
                else if (left <= 0) { items.add(DotMatrix.icon("cw")); items.add(DotMatrix.text("RW")) }
                else if (left <= 3) { items.add(DotMatrix.icon("warn")); items.add(DotMatrix.text(left.toString())) }
                if (f.bw) items.add(DotMatrix.text("BW"))
                if (s.slow && !s.flash && left > 0) items.add(DotMatrix.icon("steady"))
            }
            if (s.batteryLow) { items.add(DotMatrix.icon("battery")); items.add(DotMatrix.text("LO")) }
            if (items.isNotEmpty()) grid = items[((now / 1200) % items.size).toInt()]
        }
        DotMatrix.draw(c, mat, grid, p, u)
    }

    private fun text(s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface = Typeface.DEFAULT, al: Paint.Align = Paint.Align.LEFT) {
        tp.typeface = tf; tp.textSize = size; tp.color = color; tp.textAlign = al
        tp.letterSpacing = 0f
        drawTextOn?.drawText(s, x, y, tp)
        tp.textAlign = Paint.Align.LEFT
    }

    private var drawTextOn: Canvas? = null
    override fun draw(canvas: Canvas) { drawTextOn = canvas; super.draw(canvas) }

    private fun drawGrip(c: Canvas) {
        p.alpha = 255; p.shader = LinearGradient(grip.left, 0f, grip.right, 0f,
            intArrayOf(0xFF3A3936.toInt(), 0xFF2B2A28.toInt(), 0xFF1E1D1C.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(grip, 5 * u, 5 * u, p)
        p.shader = null
        p.color = 0x22FFFFFF
        var y = grip.top + 4 * u
        var row = 0
        while (y < grip.bottom - 3 * u) {
            var x = grip.left + 4 * u + if (row % 2 == 0) 0f else 1.5f * u
            while (x < grip.right - 3 * u) { c.drawCircle(x, y, 0.35f * u, p); x += 3 * u }
            y += 2.6f * u; row++
        }
    }

    private fun drawDoor(c: Canvas, s: CamState?) {
        // celah pintu
        p.alpha = 255; p.shader = LinearGradient(0f, door.top, 0f, door.bottom,
            intArrayOf(0xFFD3D0C7.toInt(), 0xFFB9B6AC.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(door, 4 * u, 4 * u, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.5f * u; p.color = 0x70000000
        c.drawRoundRect(door, 4 * u, 4 * u, p)
        p.strokeWidth = 0.3f * u; p.color = 0x80FFFFFF.toInt()
        c.drawRoundRect(door.left + 0.6f * u, door.top + 0.6f * u, door.right - 0.6f * u, door.bottom - 0.6f * u, 3.6f * u, 3.6f * u, p)
        p.style = Paint.Style.FILL
        // engsel
        p.color = 0xFF8F8C83.toInt()
        c.drawRoundRect(door.left - 2 * u, door.top + 7 * u, door.left + 1.2f * u, door.top + 17 * u, u, u, p)
        c.drawRoundRect(door.left - 2 * u, door.bottom - 17 * u, door.left + 1.2f * u, door.bottom - 7 * u, u, u, p)

        val f2 = s?.film
        val info = if (f2 != null) "${f2.fullName}  •  ${f2.frames} EXP" else "EMPTY  •  NO FILM LOADED"
        text(info, window.right + 5 * u, door.top + 9 * u, 3.4f * u, 0xFF55534B.toInt(), Typeface.DEFAULT_BOLD)
        text("AUTO FOCUS  •  35mm  •  DX", window.right + 5 * u, door.top + 13.5f * u, 2.3f * u, 0xFF8A877D.toInt(), Typeface.DEFAULT_BOLD)
    }

    private fun drawWindow(c: Canvas, s: CamState?) {
        text("FILM", window.centerX(), window.top - 1.6f * u, 2.3f * u, 0xFF6A685F.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        p.color = 0xFF0B0B0B.toInt()
        c.drawRoundRect(window.left - 0.8f * u, window.top - 0.8f * u, window.right + 0.8f * u, window.bottom + 0.8f * u, 3 * u, 3 * u, p)
        c.save()
        path.reset(); path.addRoundRect(window, 2.4f * u, 2.4f * u, Path.Direction.CW)
        c.clipPath(path)
        p.color = 0xFF141414.toInt(); c.drawRect(window, p)
        val f = s?.film
        if (f != null) {
            Canister.draw(c, f, window.centerX(), window.centerY() + 1.5f * u, 35 * u, 1.8f)
        } else {
            p.color = 0xFF2A2A2A.toInt()
            c.drawRect(window.left, window.centerY() - 0.3f * u, window.right, window.centerY() + 0.3f * u, p)
        }
        p.alpha = 255; p.shader = LinearGradient(window.left, window.top, window.right, window.bottom,
            intArrayOf(0x50FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x30FFFFFF), floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(window, p)
        p.shader = null
        // scratches on the window glass
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.15f * u; p.color = 0x40FFFFFF
        c.drawLine(window.left + 2 * u, window.top + 9 * u, window.right - 3 * u, window.top + 15 * u, p)
        c.drawLine(window.left + 6 * u, window.bottom - 6 * u, window.right - 1 * u, window.bottom - 11 * u, p)
        p.style = Paint.Style.FILL
        c.restore()
    }

    private fun drawLatch(c: Canvas) {
        text("OPEN", latch.centerX(), latch.top - 1.4f * u, 2.3f * u, 0xFF55534B.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        // slot
        p.color = 0xFF0E0E0E.toInt()
        c.drawRoundRect(latch, 2.2f * u, 2.2f * u, p)
        p.color = 0xFF2A2927.toInt()
        c.drawRoundRect(latch.left + 0.7f * u, latch.top + 0.7f * u, latch.right - 0.7f * u, latch.bottom - 0.7f * u, 1.8f * u, 1.8f * u, p)
        // direction marks inside the slot
        val ax = latch.centerX()
        for (i in 0..2) {
            val ay = latch.bottom - (3.2f + i * 2.6f) * u
            p.color = if (latchPos * 3f > 2 - i) 0xFFE8842A.toInt() else 0xFF5A5852.toInt()
            path.reset(); path.moveTo(ax - 1.6f * u, ay - 1 * u); path.lineTo(ax + 1.6f * u, ay - 1 * u); path.lineTo(ax, ay + 0.9f * u); path.close()
            c.drawPath(path, p)
        }
        // sliding knob
        val ky = latch.top + 0.8f * u + latchPos * latchTravel
        p.color = 0x70000000; c.drawRoundRect(latch.left + 1 * u, ky + 0.8f * u, latch.right - 1 * u, ky + knobH + 0.8f * u, 1.6f * u, 1.6f * u, p)
        p.alpha = 255; p.shader = LinearGradient(0f, ky, 0f, ky + knobH, intArrayOf(0xFFF1EEE6.toInt(), 0xFFB0ADA3.toInt(), 0xFF86837A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(latch.left + 1 * u, ky, latch.right - 1 * u, ky + knobH, 1.6f * u, 1.6f * u, p)
        p.shader = null
        for (i in 0..3) {
            p.color = 0x66000000; c.drawRect(latch.left + 2.4f * u, ky + (2.2f + i * 2.1f) * u, latch.right - 2.4f * u, ky + (2.7f + i * 2.1f) * u, p)
            p.color = 0x88FFFFFF.toInt(); c.drawRect(latch.left + 2.4f * u, ky + (2.7f + i * 2.1f) * u, latch.right - 2.4f * u, ky + (2.95f + i * 2.1f) * u, p)
        }
    }

    // ------------------------------------------------------------------ stickers + wear (cached)

    private fun buildDecals(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val keep = drawTextOn
        drawTextOn = c
        val rnd = java.util.Random(2003)
        val W = w.toFloat(); val H = h.toFloat()
        val q = Paint(Paint.ANTI_ALIAS_FLAG)

        // yellowed, uneven plastic
        for (i in 0 until 7) {
            val x = rnd.nextFloat() * W; val y = rnd.nextFloat() * H; val r = (18 + rnd.nextFloat() * 30) * u
            q.alpha = 255; q.shader = RadialGradient(x, y, r, intArrayOf(0x1EA8842E, 0x00A8842E), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, q)
        }
        // grime in the corners and along the edges
        for ((x, y) in listOf(0f to 0f, W to 0f, 0f to H, W to H)) {
            q.alpha = 255; q.shader = RadialGradient(x, y, 26 * u, intArrayOf(0x553A3020, 0x003A3020), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, 26 * u, q)
        }
        q.shader = null

        // stickers (on the door, a couple on the grip)
        val ax0 = window.right + 5 * u; val ax1 = latch.left - 3 * u
        val aw = ax1 - ax0; val ay0 = door.top + 17 * u
        stStarburst(c, ax0 + aw * 0.14f + 4 * u, ay0 + 12 * u, 9f, rnd)
        stLabelTape(c, ax0 + aw * 0.60f, ay0 + 3.5f * u, -3f)
        stQc(c, ax0 + aw * 0.80f, ay0 + 14 * u, -8f, rnd)
        stPhotoLab(c, ax0 + aw * 0.46f, ay0 + 27 * u, 4f, rnd)
        stHolo(c, ax0 + aw * 0.86f, ay0 + 29 * u, -6f, rnd)
        stY2k(c, grip.left + 9 * u, grip.top + 9 * u, 12f, rnd)
        stSmiley(c, grip.right - 9 * u, grip.top + 10 * u, -14f, rnd)

        // scuffed patches: bundles of short parallel strokes
        q.style = Paint.Style.STROKE; q.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 16) {
            val cx = rnd.nextFloat() * W; val cy = rnd.nextFloat() * H
            val ang = rnd.nextFloat() * 3.14f
            val dark = rnd.nextInt(3) == 0
            for (k in 0 until 26) {
                val ox = (rnd.nextFloat() - 0.5f) * 9 * u; val oy = (rnd.nextFloat() - 0.5f) * 5 * u
                val len = (1.5f + rnd.nextFloat() * 6f) * u
                q.strokeWidth = (0.12f + rnd.nextFloat() * 0.3f) * u
                q.color = if (dark) (0x1C shl 24) or 0x1A1A1A else ((0x18 + rnd.nextInt(0x28)) shl 24) or 0xFFFFFF
                c.drawLine(cx + ox, cy + oy, cx + ox + cos(ang) * len, cy + oy + sin(ang) * len, q)
            }
        }
        // long fine scratches
        for (i in 0 until 55) {
            val x = rnd.nextFloat() * W; val y = rnd.nextFloat() * H
            val ang = -0.5f + rnd.nextFloat() * 1.0f + if (rnd.nextBoolean()) 1.57f else 0f
            val len = (4 + rnd.nextFloat() * 30) * u
            q.strokeWidth = (0.08f + rnd.nextFloat() * 0.16f) * u
            q.color = if (rnd.nextInt(3) == 0) 0x38000000 else 0x4AFFFFFF
            c.drawLine(x, y, x + cos(ang) * len, y + sin(ang) * len, q)
        }
        // paint rubbed off near the corners and where a thumb rests
        q.style = Paint.Style.FILL
        for (i in 0 until 240) {
            val edge = rnd.nextInt(4)
            val t = rnd.nextFloat()
            val d = rnd.nextFloat() * rnd.nextFloat() * 7 * u
            val x = when (edge) { 0 -> d; 1 -> W - d; else -> t * W }
            val y = when (edge) { 2 -> d; 3 -> H - d; else -> t * H }
            q.color = ((0x20 + rnd.nextInt(0x38)) shl 24) or 0x6B675C
            c.drawCircle(x, y, (0.1f + rnd.nextFloat() * 0.4f) * u, q)
        }
        // specks of dirt
        for (i in 0 until 160) {
            q.color = ((0x20 + rnd.nextInt(0x40)) shl 24) or 0x2A2418
            c.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * H, (0.06f + rnd.nextFloat() * 0.18f) * u, q)
        }
        drawTextOn = keep
        return b
    }

    /** Launcher icon artwork: the worn "NEW!" sticker on its own. */
    fun stickerIcon(size: Int): Bitmap {
        val keepU = u; val keepT = drawTextOn
        u = size * 0.29f / 9.5f
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        drawTextOn = c
        val rnd = java.util.Random(2003)
        stStarburst(c, size / 2f, size / 2f, 9f, rnd)
        // dulled: grime wash + a few scratches, only where the sticker is
        val q = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
        q.color = 0x338A7A50; c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), q)
        q.style = Paint.Style.STROKE
        for (i in 0 until 14) {
            val x = size * (0.25f + rnd.nextFloat() * 0.5f); val y = size * (0.25f + rnd.nextFloat() * 0.5f)
            val a = 0.6f + rnd.nextFloat() * 0.5f; val len = (2 + rnd.nextFloat() * 7) * u
            q.strokeWidth = (0.1f + rnd.nextFloat() * 0.2f) * u
            q.color = if (i % 3 == 0) 0x50000000 else 0x66FFFFFF
            c.drawLine(x, y, x + cos(a) * len, y + sin(a) * len, q)
        }
        u = keepU; drawTextOn = keepT
        return b
    }

    private inline fun sticker(c: Canvas, cx: Float, cy: Float, rot: Float, body: () -> Unit) {
        c.saveLayer(null, null)
        c.translate(cx, cy); c.rotate(rot)
        body()
        c.restore()
    }

    /** Rubs thin lines out of the current sticker layer. */
    private fun rub(c: Canvas, rnd: java.util.Random, hw: Float, hh: Float, n: Int, wMax: Float = 0.35f) {
        clear.style = Paint.Style.STROKE; clear.strokeCap = Paint.Cap.ROUND
        val ang = rnd.nextFloat() * 3.14f
        for (i in 0 until n) {
            val x = (rnd.nextFloat() * 2 - 1) * hw; val y = (rnd.nextFloat() * 2 - 1) * hh
            val a = ang + (rnd.nextFloat() - 0.5f) * 0.5f
            val len = (1 + rnd.nextFloat() * 6) * u
            clear.strokeWidth = (0.1f + rnd.nextFloat() * wMax) * u
            c.drawLine(x, y, x + cos(a) * len, y + sin(a) * len, clear)
        }
        clear.style = Paint.Style.FILL
    }

    private fun stQc(c: Canvas, cx: Float, cy: Float, rot: Float, rnd: java.util.Random) = sticker(c, cx, cy, rot) {
        p.shader = null; p.color = 0xFFF1D23B.toInt()
        c.drawOval(-9 * u, -4.5f * u, 9 * u, 4.5f * u, p)
        text("QC PASSED", 0f, -0.2f * u, 2.3f * u, 0xFF1A1A1A.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        text("No. 0335", 0f, 2.6f * u, 1.9f * u, 0xFF1A1A1A.toInt(), Typeface.DEFAULT, Paint.Align.CENTER)
        rub(c, rnd, 9 * u, 4.5f * u, 14)
    }

    private fun stStarburst(c: Canvas, cx: Float, cy: Float, rot: Float, rnd: java.util.Random) = sticker(c, cx, cy, rot) {
        val n = 16
        path.reset()
        for (i in 0 until n * 2) {
            val r = (if (i % 2 == 0) 9.5f else 7.6f) * u
            val a = i * Math.PI / n
            val x = (cos(a) * r).toFloat(); val y = (sin(a) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        p.shader = null; p.color = 0xFFE23B2E.toInt(); c.drawPath(path, p)
        p.color = 0xFFFFD83A.toInt(); c.drawCircle(0f, 0f, 6.6f * u, p)
        text("NEW!", 0f, -0.6f * u, 4.2f * u, 0xFFD3261A.toInt(), Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC), Paint.Align.CENTER)
        text("AUTO FOCUS", 0f, 2.3f * u, 1.75f * u, 0xFF1A1A1A.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        text("+ DATE BACK", 0f, 4.3f * u, 1.4f * u, 0xFF1A1A1A.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        // a chunk peeled away, leaving paper fuzz and glue behind
        path.reset(); path.moveTo(1 * u, -10 * u); path.lineTo(10 * u, -10 * u); path.lineTo(10 * u, 1.5f * u)
        path.lineTo(8.2f * u, 0.4f * u); path.lineTo(7.4f * u, -2.2f * u); path.lineTo(5.6f * u, -2.8f * u)
        path.lineTo(4.9f * u, -5.4f * u); path.lineTo(2.8f * u, -6.1f * u); path.close()
        c.drawPath(path, clear)
        for (i in 0 until 40) {
            val t = rnd.nextFloat(); val x = (3 + t * 5.5f) * u; val y = (-6.5f + t * 6.5f + (rnd.nextFloat() - 0.2f) * -2.2f) * u
            p.color = ((0x40 + rnd.nextInt(0x60)) shl 24) or 0xF2EEE2
            c.drawCircle(x, y, (0.2f + rnd.nextFloat() * 0.6f) * u, p)
        }
        rub(c, rnd, 8 * u, 8 * u, 10)
    }

    private fun stPhotoLab(c: Canvas, cx: Float, cy: Float, rot: Float, rnd: java.util.Random) = sticker(c, cx, cy, rot) {
        p.shader = null; p.color = 0xFFF6F3EA.toInt()
        c.drawRoundRect(-13 * u, -5.5f * u, 13 * u, 5.5f * u, u, u, p)
        p.color = 0xFF1E5AA8.toInt(); c.drawRect(-13 * u, -5.5f * u, 13 * u, -0.6f * u, p)
        text("1 HOUR PHOTO", -11.5f * u, -1.7f * u, 3f * u, 0xFFFFFFFF.toInt(), Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC))
        text("Prints  •  Reprints  •  Film", -11.5f * u, 2.1f * u, 1.7f * u, 0xFFD3261A.toInt(), Typeface.DEFAULT_BOLD)
        text("Thank you, come again!", -11.5f * u, 4.3f * u, 1.5f * u, 0xFF55534B.toInt(), Typeface.DEFAULT)
        // right third torn off along a ragged edge
        path.reset(); path.moveTo(14 * u, -6 * u)
        var y = -6f; var k = 0
        while (y <= 6f) { path.lineTo((5.2f + (if (k % 2 == 0) 0f else 1.3f) + rnd.nextFloat() * 0.8f) * u, y * u); y += 0.9f; k++ }
        path.lineTo(14 * u, 6 * u); path.close()
        c.drawPath(path, clear)
        for (i in 0 until 55) {
            p.color = ((0x30 + rnd.nextInt(0x70)) shl 24) or 0xF6F3EA
            c.drawCircle((5.5f + rnd.nextFloat() * rnd.nextFloat() * 7f) * u, (rnd.nextFloat() * 10.4f - 5.2f) * u, (0.2f + rnd.nextFloat() * 0.7f) * u, p)
        }
        rub(c, rnd, 12 * u, 5 * u, 9)
    }

    private fun stHolo(c: Canvas, cx: Float, cy: Float, rot: Float, rnd: java.util.Random) = sticker(c, cx, cy, rot) {
        p.alpha = 255; p.shader = LinearGradient(-8 * u, -5 * u, 8 * u, 5 * u,
            intArrayOf(0xFFD8D8E0.toInt(), 0xFFF7A8D8.toInt(), 0xFF9BE8F2.toInt(), 0xFFF6F0A0.toInt(), 0xFFA8F0B8.toInt(), 0xFFC0A8F4.toInt(), 0xFFE0E0E8.toInt()),
            null, Shader.TileMode.CLAMP)
        c.drawRoundRect(-8 * u, -4.6f * u, 8 * u, 4.6f * u, 1.2f * u, 1.2f * u, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.3f * u; p.color = 0x99FFFFFF.toInt()
        c.drawRoundRect(-7.2f * u, -3.8f * u, 7.2f * u, 3.8f * u, u, u, p)
        p.style = Paint.Style.FILL
        text("GENUINE", 0f, 0.2f * u, 2.6f * u, 0xCC2A2A44.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        text("★ SECURITY SEAL ★", 0f, 2.7f * u, 1.25f * u, 0xCC2A2A44.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        text("2001", 0f, -2.3f * u, 1.3f * u, 0xCC2A2A44.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        rub(c, rnd, 8 * u, 4.6f * u, 18, 0.5f)
    }

    private fun stLabelTape(c: Canvas, cx: Float, cy: Float, rot: Float) = sticker(c, cx, cy, rot) {
        p.shader = null; p.color = 0xFF16161A.toInt()
        c.drawRoundRect(-14 * u, -2.4f * u, 14 * u, 2.4f * u, 0.5f * u, 0.5f * u, p)
        tp.letterSpacing = 0.18f
        tp.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); tp.textSize = 2.6f * u; tp.color = 0xFFF2F2F2.toInt(); tp.textAlign = Paint.Align.CENTER
        c.drawText("DO NOT DROP!!", -0.6f * u, 0.95f * u, tp)
        tp.letterSpacing = 0f; tp.textAlign = Paint.Align.LEFT
        // lifted corner folded back over itself
        path.reset(); path.moveTo(14.2f * u, -2.6f * u); path.lineTo(9.6f * u, -2.6f * u); path.lineTo(14.2f * u, 1.4f * u); path.close()
        c.drawPath(path, clear)
        p.color = 0x55000000
        path.reset(); path.moveTo(9.8f * u, -2.2f * u); path.lineTo(13.9f * u, 1.9f * u); path.lineTo(10.6f * u, 1.7f * u); path.close()
        c.drawPath(path, p)
        p.color = 0xFFB8B4A6.toInt()
        path.reset(); path.moveTo(9.6f * u, -2.6f * u); path.lineTo(14.2f * u, 1.4f * u); path.lineTo(10.2f * u, 1.2f * u); path.close()
        c.drawPath(path, p)
    }

    private fun stY2k(c: Canvas, cx: Float, cy: Float, rot: Float, rnd: java.util.Random) = sticker(c, cx, cy, rot) {
        p.shader = null; p.color = 0xFFD8D8DC.toInt(); c.drawCircle(0f, 0f, 5.2f * u, p)
        p.alpha = 255; p.shader = LinearGradient(0f, -4.6f * u, 0f, 4.6f * u, intArrayOf(0xFF2B6CD8.toInt(), 0xFF39C6E0.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, 4.5f * u, p); p.shader = null
        text("Y2K", 0f, 0.6f * u, 3.4f * u, 0xFFFFFFFF.toInt(), Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC), Paint.Align.CENTER)
        text("READY", 0f, 2.9f * u, 1.5f * u, 0xFFFFFFFF.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        rub(c, rnd, 5 * u, 5 * u, 9, 0.3f)
    }

    private fun stSmiley(c: Canvas, cx: Float, cy: Float, rot: Float, rnd: java.util.Random) = sticker(c, cx, cy, rot) {
        p.shader = null; p.color = 0xFFFFD21F.toInt(); c.drawCircle(0f, 0f, 4.6f * u, p)
        p.color = 0xFF1A1A1A.toInt()
        c.drawOval(-2.1f * u, -2.2f * u, -1.1f * u, -0.3f * u, p); c.drawOval(1.1f * u, -2.2f * u, 2.1f * u, -0.3f * u, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.55f * u; p.strokeCap = Paint.Cap.ROUND
        c.drawArc(-2.9f * u, -2.6f * u, 2.9f * u, 2.9f * u, 20f, 140f, false, p)
        p.style = Paint.Style.FILL; p.strokeCap = Paint.Cap.BUTT
        // lower-left half worn away by a thumb
        c.drawCircle(-4.6f * u, 4.2f * u, 2.8f * u, clear)
        rub(c, rnd, 4.6f * u, 4.6f * u, 7, 0.3f)
    }

    private fun drawViewfinder(c: Canvas, s: CamState?) {
        p.color = 0xFF151515.toInt()
        c.drawRoundRect(vfHouse, 4 * u, 4 * u, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.6f * u; p.color = 0xFF3B3B3B.toInt()
        c.drawRoundRect(vfHouse.left + u, vfHouse.top + u, vfHouse.right - u, vfHouse.bottom - u, 3 * u, 3 * u, p)
        p.style = Paint.Style.FILL
        p.color = 0xFF050505.toInt()
        c.drawRect(vf, p)

        // LED AF (merah) dan LED flash (oranye)
        val lx = vfHouse.right + 6 * u
        led(c, lx, 12 * u, s?.focused == true, 0xFFFF2A1A.toInt(), 0xFF4A1512.toInt())
        text("AF", lx + 4 * u, 13.2f * u, 2.6f * u, 0xFF55534B.toInt(), Typeface.DEFAULT_BOLD)
        led(c, lx, 23 * u, s?.flash == true, 0xFFFF9A1A.toInt(), 0xFF4A3212.toInt())
        text("FLASH", lx + 4 * u, 24.2f * u, 2.6f * u, 0xFF55534B.toInt(), Typeface.DEFAULT_BOLD)
    }

    private fun led(c: Canvas, x: Float, y: Float, on: Boolean, cOn: Int, cOff: Int) {
        p.color = 0xFF1A1A1A.toInt(); c.drawCircle(x, y, 2.3f * u, p)
        if (on) {
            p.alpha = 255; p.shader = RadialGradient(x, y, 5.5f * u, intArrayOf(cOn and 0x99FFFFFF.toInt(), cOn and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, 5.5f * u, p)
            p.alpha = 255; p.shader = RadialGradient(x - 0.4f * u, y - 0.4f * u, 1.8f * u, intArrayOf(Color.WHITE, cOn), null, Shader.TileMode.CLAMP)
        } else {
            p.alpha = 255; p.shader = RadialGradient(x - 0.4f * u, y - 0.4f * u, 1.8f * u, intArrayOf(0xFF8A6A66.toInt(), cOff), null, Shader.TileMode.CLAMP)
        }
        c.drawCircle(x, y, 1.6f * u, p)
        p.shader = null
    }

    private fun drawCounter(c: Canvas, s: CamState?) {
        val label = when {
            s?.film == null -> "–"
            s.rewound -> "E"
            s.rewinding && s.rewindShown >= 0 -> s.rewindShown.toInt().toString()
            s.frames == 0 -> "S"
            else -> s.frames.toString()
        }
        if (label != lastLabel) {
            val first = lastLabel == null
            prevLabel = lastLabel ?: label; lastLabel = label
            cntAnimator?.cancel()
            if (!first && isAttachedToWindow) {
                cntAnim = 0f
                cntAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 320
                    interpolator = android.view.animation.OvershootInterpolator(1.6f)
                    addUpdateListener { cntAnim = it.animatedValue as Float; invalidate() }
                    start()
                }
            } else cntAnim = 1f
        }
        // chrome bezel
        p.alpha = 255; p.shader = LinearGradient(cnX - cnR, cnY - cnR, cnX + cnR, cnY + cnR,
            intArrayOf(0xFFF4F2EC.toInt(), 0xFF6E6B63.toInt(), 0xFFD8D5CC.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(cnX, cnY, cnR + 0.5f * u, p); p.shader = null
        p.color = 0xFF151515.toInt(); c.drawCircle(cnX, cnY, cnR - 0.3f * u, p)

        // dial drawn flat into a bitmap: the numbers ride on a disc that turns one step per frame
        val bmp = cntBmp ?: return
        val d = bmp.width.toFloat(); val R = d / 2f
        val bc = Canvas(bmp)
        bc.drawColor(0xFFEFE8D2.toInt())
        val pivotY = R + d * 1.25f
        val step = 26f
        val keep = drawTextOn; drawTextOn = bc
        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        fun num(txt: String, ang: Float) {
            bc.save(); bc.rotate(ang, R, pivotY)
            text(txt, R, R + d * 0.17f, d * 0.46f, 0xFF1A1A1A.toInt(), mono, Paint.Align.CENTER)
            bc.restore()
        }
        num(prevLabel, -cntAnim * step)
        num(label, (1f - cntAnim) * step)
        // tick marks between the numbers
        p.color = 0xFF8A2A20.toInt()
        for (k in -2..2) {
            bc.save(); bc.rotate((k + 0.5f - cntAnim) * step, R, pivotY)
            bc.drawRect(R - d * 0.012f, R - d * 0.36f, R + d * 0.012f, R - d * 0.22f, p)
            bc.restore()
        }
        drawTextOn = keep

        val rr = cnR - 0.9f * u
        c.save()
        path.reset(); path.addCircle(cnX, cnY, rr, Path.Direction.CW)
        c.clipPath(path)
        c.translate(cnX - R, cnY - R)
        c.drawBitmapMesh(bmp, 12, 12, cntVerts, 0, null, 0, null)
        c.restore()

        // pointer
        p.color = 0xFFD13A2A.toInt()
        path.reset(); path.moveTo(cnX, cnY - rr + 1.5f * u); path.lineTo(cnX - 0.8f * u, cnY - rr); path.lineTo(cnX + 0.8f * u, cnY - rr); path.close()
        c.drawPath(path, p)
        // convex glass: dark towards the rim, bright specular blob top-left, faint bounce bottom-right
        p.alpha = 255; p.shader = RadialGradient(cnX, cnY, rr, intArrayOf(0x00000000, 0x00000000, 0x30000000, 0x99000000.toInt()),
            floatArrayOf(0f, 0.55f, 0.85f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cnX, cnY, rr, p)
        val hx = cnX - rr * 0.38f; val hy = cnY - rr * 0.42f
        p.alpha = 255; p.shader = RadialGradient(hx, hy, rr * 0.5f, intArrayOf(0xD0FFFFFF.toInt(), 0x30FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.save(); c.rotate(-35f, hx, hy); c.scale(1f, 0.55f, hx, hy)
        c.drawCircle(hx, hy, rr * 0.5f, p)
        c.restore()
        p.alpha = 255; p.shader = RadialGradient(cnX + rr * 0.45f, cnY + rr * 0.5f, rr * 0.35f, intArrayOf(0x50FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(cnX + rr * 0.45f, cnY + rr * 0.5f, rr * 0.35f, p)
        p.shader = null
    }

    private fun drawWheel(c: Canvas, s: CamState?) {
        p.color = 0xFF111111.toInt()
        c.drawRoundRect(wheel.left - u, wheel.top - 0.8f * u, wheel.right + u, wheel.bottom + 0.8f * u, 2 * u, 2 * u, p)
        c.save(); c.clipRect(wheel)
        p.alpha = 255; p.shader = LinearGradient(wheel.left, 0f, wheel.right, 0f,
            intArrayOf(0xFF151515.toInt(), 0xFF4A4A4A.toInt(), 0xFF6A6A6A.toInt(), 0xFF4A4A4A.toInt(), 0xFF151515.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(wheel, p); p.shader = null
        val off = ((s?.windProgress ?: 0f) * 60f * u + (s?.frames ?: 0) * 7f * u) % (2.4f * u)
        var x = wheel.left - 2.4f * u + off
        while (x < wheel.right) {
            // gerigi makin rapat di tepi (kesan roda melengkung)
            val t = ((x - wheel.centerX()) / (wheel.width() / 2)).coerceIn(-1f, 1f)
            val xx = wheel.centerX() + sin(t * 1.5708f) * wheel.width() / 2
            p.color = 0xFF0C0C0C.toInt(); c.drawRect(xx, wheel.top, xx + 0.7f * u * cos(t * 1.4f), wheel.bottom, p)
            p.color = 0x33FFFFFF; c.drawRect(xx + 0.7f * u, wheel.top, xx + 0.95f * u, wheel.bottom, p)
            x += 2.4f * u
        }
        c.restore()
        val cocked = s?.cocked == true
        text(if (cocked) "READY" else "WIND  ▶▶", wheel.centerX(), wheel.bottom + 3.3f * u, 2.2f * u,
            if (cocked) 0xFF2E7D32.toInt() else 0xFF6A685F.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
    }

    private fun drawSwitch(c: Canvas, r: RectF, on: Boolean, label: String) {
        text(label, r.centerX(), r.top - 1.8f * u, 2.6f * u, 0xFF55534B.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        p.color = 0xFF1A1A1A.toInt(); c.drawRoundRect(r, 2 * u, 2 * u, p)
        val kw = r.width() * 0.48f
        val kx = if (on) r.right - kw - 0.8f * u else r.left + 0.8f * u
        p.alpha = 255; p.shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(0xFFEDEAE2.toInt(), 0xFF9C998F.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(kx, r.top + 0.8f * u, kx + kw, r.bottom - 0.8f * u, 1.4f * u, 1.4f * u, p)
        p.shader = null
        p.color = 0x66000000
        for (i in 0..2) c.drawRect(kx + kw * (0.3f + i * 0.2f), r.top + 2 * u, kx + kw * (0.3f + i * 0.2f) + 0.4f * u, r.bottom - 2 * u, p)
        text("OFF", r.left + 1 * u, r.bottom + 3.2f * u, 2.1f * u, if (!on) 0xFF2B2A28.toInt() else 0xFF9A978D.toInt(), Typeface.DEFAULT_BOLD)
        text("ON", r.right - 1 * u, r.bottom + 3.2f * u, 2.1f * u, if (on) 0xFFB5372A.toInt() else 0xFF9A978D.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.RIGHT)
    }

    private fun drawCrank(c: Canvas, s: CamState?) {
        text("REWIND  ↻", crX, crY - crR - 2 * u, 2.6f * u, 0xFF55534B.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        p.color = 0xFF8F8C83.toInt(); c.drawCircle(crX, crY, crR, p)
        p.alpha = 255; p.shader = RadialGradient(crX - 3 * u, crY - 3 * u, crR * 1.4f, intArrayOf(0xFF4A4946.toInt(), 0xFF1C1B1A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(crX, crY, crR - 1.2f * u, p); p.shader = null
        val a = Math.toRadians((s?.crankAngle ?: 0f).toDouble() - 60.0)
        val kx = crX + cos(a).toFloat() * (crR - 4 * u); val ky = crY + sin(a).toFloat() * (crR - 4 * u)
        p.color = 0xFFB9B6AC.toInt(); p.strokeWidth = 2.6f * u; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(crX, crY, kx, ky, p)
        p.strokeCap = Paint.Cap.BUTT
        p.color = 0xFF6B6962.toInt(); c.drawCircle(crX, crY, 1.6f * u, p)
        p.alpha = 255; p.shader = RadialGradient(kx - 0.6f * u, ky - 0.6f * u, 3 * u, intArrayOf(0xFFF4F2EC.toInt(), 0xFF8F8C83.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(kx, ky, 2.6f * u, p); p.shader = null
    }

    private fun drawShutter(c: Canvas) {
        p.color = 0x66000000; c.drawCircle(shX, shY + 0.8f * u, shR + 0.6f * u, p)
        p.alpha = 255; p.shader = LinearGradient(shX - shR, shY - shR, shX + shR, shY + shR,
            intArrayOf(0xFFFAFAF6.toInt(), 0xFF8D8A82.toInt(), 0xFFE2E0D8.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(shX, shY, shR, p)
        val r2 = shR - 2.4f * u
        val dy = if (shutterDown) 0.5f * u else 0f
        p.alpha = 255; p.shader = RadialGradient(shX - r2 * 0.3f, shY - r2 * 0.4f + dy, r2 * 1.5f,
            if (shutterDown) intArrayOf(0xFFB9B6AC.toInt(), 0xFF7E7B73.toInt()) else intArrayOf(0xFFF7F5EF.toInt(), 0xFFA5A298.toInt()),
            null, Shader.TileMode.CLAMP)
        c.drawCircle(shX, shY + dy, r2, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.3f * u; p.color = 0x55000000
        c.drawCircle(shX, shY + dy, r2, p); p.style = Paint.Style.FILL
    }

    // ------------------------------------------------------------------ sentuhan

    private fun hit(r: RectF, x: Float, y: Float, pad: Float) =
        x > r.left - pad && x < r.right + pad && y > r.top - pad && y < r.bottom + pad

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        val l = listener
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                target = when {
                    hit(wheel, x, y, 4 * u) -> 1
                    hypot(x - shX, y - shY) < shR + 3 * u -> 2
                    hit(flashSw, x, y, 4 * u) -> 3
                    hit(dateSw, x, y, 4 * u) -> 4
                    hypot(x - crX, y - crY) < crR + 5 * u -> 5
                    hit(latch, x, y, 4 * u) -> 6
                    hit(labBtn, x, y, 2 * u) -> 7
                    else -> 0
                }
                lastX = x; lastY = y; tickAcc = 0f
                when (target) {
                    1 -> windBlocked = l?.onWindAttempt() != true
                    2 -> { shutterDown = true; invalidate() }
                    6 -> {
                        latchAnim?.cancel(); latchFired = false
                        latchLimit = if (l?.onLatchAttempt() == true) 1f else 0.16f
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    5 -> lastAng = Math.toDegrees(atan2((y - crY).toDouble(), (x - crX).toDouble())).toFloat()
                }
                return target != 0
            }
            MotionEvent.ACTION_MOVE -> {
                val s = st ?: return true
                if (target == 1 && !windBlocked) {
                    val dx = x - lastX; lastX = x
                    if (dx > 0 && !s.cocked) {           // gerigi satu arah: hanya ke kanan
                        s.windProgress += dx / (60f * u)
                        tickAcc += dx
                        if (tickAcc > 5 * u) { tickAcc = 0f; l?.onWindTick(); performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                        if (s.windProgress >= 1f) {
                            s.windProgress = 0f
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            l?.onCocked()
                        }
                        invalidate()
                    }
                } else if (target == 6) {
                    val dy = y - lastY; lastY = y
                    val before = (latchPos * 5).toInt()
                    latchPos = (latchPos + dy / latchTravel).coerceIn(0f, latchLimit)
                    if ((latchPos * 5).toInt() != before) { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); l?.onWindTick() }
                    if (latchPos >= 0.98f && !latchFired) {
                        latchFired = true; latchPos = 1f
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        l?.onLatch()
                    }
                    invalidate()
                } else if (target == 5) {
                    val ang = Math.toDegrees(atan2((y - crY).toDouble(), (x - crX).toDouble())).toFloat()
                    var d = ang - lastAng
                    if (d > 180) d -= 360; if (d < -180) d += 360
                    lastAng = ang
                    if (d > 0 && hypot(x - crX, y - crY) > 2 * u) {   // hanya searah jarum jam
                        tickAcc += d
                        if (tickAcc > 30f) { tickAcc = 0f; performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                        l?.onRewind(d)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val up = e.actionMasked == MotionEvent.ACTION_UP
                when (target) {
                    2 -> { shutterDown = false; invalidate(); if (up && hypot(x - shX, y - shY) < shR + 6 * u) l?.onShutter() }
                    3 -> if (up) { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); l?.onFlash() }
                    4 -> if (up) { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); l?.onDate() }
                    6 -> {
                        // spring back up
                        latchAnim = ValueAnimator.ofFloat(latchPos, 0f).apply {
                            duration = (120 + 260 * latchPos).toLong()
                            startDelay = if (latchFired) 350 else 0
                            interpolator = android.view.animation.OvershootInterpolator(1.4f)
                            addUpdateListener { latchPos = (it.animatedValue as Float).coerceAtLeast(0f); invalidate() }
                            start()
                        }
                    }
                    7 -> if (up) l?.onLab()
                }
                target = 0
            }
        }
        return true
    }
}
