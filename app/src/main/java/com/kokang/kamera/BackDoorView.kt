package com.kokang.kamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** Pintu belakang terbuka: ruang film kamera di kiri, rak film (tabung 3D) di kanan. */
class BackDoorView(ctx: Context) : View(ctx) {

    interface Listener {
        fun onLoad(f: Film)
        fun onCloseDoor()
        fun onLab()
    }

    var listener: Listener? = null
    var index = 0
    var developing = false
    var devDone = 0
    var devTotal = 0
    var note: String? = null

    private var u = 10f
    private var rot = 0.6f
    private var spinning = true
    private val inner = RectF()
    private val card = RectF()
    private val btnPrev = RectF()
    private val btnNext = RectF()
    private val btnLoad = RectF()
    private val btnClose = RectF()
    private val btnLab = RectF()
    private val canArea = RectF()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var target = 0
    private var lastX = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val W = w.toFloat(); val H = h.toFloat()
        u = H / 100f
        inner.set(4 * u, 7 * u, W * 0.44f, 78 * u)
        btnClose.set(4 * u, 84 * u, 4 * u + (inner.width() - 3 * u) * 0.55f, 96 * u)
        btnLab.set(btnClose.right + 3 * u, 84 * u, inner.right, 96 * u)
        card.set(W * 0.47f, 4 * u, W - 4 * u, 96 * u)
        canArea.set(card.left + 2 * u, card.top + 12 * u, card.left + 34 * u, card.bottom - 20 * u)
        btnPrev.set(card.left + 3 * u, card.bottom - 16 * u, card.left + 17 * u, card.bottom - 4 * u)
        btnNext.set(card.right - 17 * u, card.bottom - 16 * u, card.right - 3 * u, card.bottom - 4 * u)
        btnLoad.set(btnPrev.right + 3 * u, card.bottom - 16 * u, btnNext.left - 3 * u, card.bottom - 4 * u)
    }

    private fun t(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface = Typeface.DEFAULT, al: Paint.Align = Paint.Align.LEFT) {
        tp.typeface = tf; tp.textSize = size; tp.color = color; tp.textAlign = al
        c.drawText(s, x, y, tp)
        tp.textAlign = Paint.Align.LEFT
    }

    private fun button(c: Canvas, r: RectF, label: String, bg: Int, fg: Int, size: Float = 3.6f) {
        p.shader = null
        p.color = 0x55000000; c.drawRoundRect(r.left, r.top + 0.8f * u, r.right, r.bottom + 0.8f * u, 2.5f * u, 2.5f * u, p)
        p.color = bg; c.drawRoundRect(r, 2.5f * u, 2.5f * u, p)
        t(c, label, r.centerX(), r.centerY() + size * 0.36f * u, size * u, fg, Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
    }

    override fun onDraw(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat()
        // meja
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(0xFF4A3B2E.toInt(), 0xFF2C231B.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p)

        // ---- badan kamera terbuka
        p.alpha = 255; p.shader = LinearGradient(0f, inner.top, 0f, inner.bottom, intArrayOf(0xFFD3D0C7.toInt(), 0xFFA8A59B.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(inner, 5 * u, 5 * u, p)
        p.shader = null
        p.color = 0xFF141414.toInt()
        val ir = RectF(inner.left + 3 * u, inner.top + 3 * u, inner.right - 3 * u, inner.bottom - 3 * u)
        c.drawRoundRect(ir, 3 * u, 3 * u, p)
        // ruang tabung (kiri)
        val ch = RectF(ir.left + 3 * u, ir.top + 5 * u, ir.left + 25 * u, ir.bottom - 5 * u)
        p.alpha = 255; p.shader = LinearGradient(ch.left, 0f, ch.right, 0f, intArrayOf(0xFF050505.toInt(), 0xFF2A2A2A.toInt(), 0xFF050505.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(ch, 3 * u, 3 * u, p)
        // spool penggulung (kanan)
        val sp = RectF(ir.right - 22 * u, ir.top + 5 * u, ir.right - 3 * u, ir.bottom - 5 * u)
        c.drawRoundRect(sp, 3 * u, 3 * u, p)
        p.alpha = 255; p.shader = LinearGradient(sp.left + 5 * u, 0f, sp.right - 5 * u, 0f, intArrayOf(0xFF2A2A2A.toInt(), 0xFF777777.toInt(), 0xFF2A2A2A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(sp.left + 5 * u, sp.top + 4 * u, sp.right - 5 * u, sp.bottom - 4 * u, p)
        p.shader = null
        p.color = 0xFF0A0A0A.toInt()
        var yy = sp.top + 8 * u
        while (yy < sp.bottom - 8 * u) { c.drawRect(sp.centerX() - 0.6f * u, yy, sp.centerX() + 0.6f * u, yy + 5 * u, p); yy += 9 * u }
        // gerbang film 3:2 + rel
        val gw = (sp.left - ch.right - 10 * u).coerceAtLeast(12 * u)
        val gh = gw * 2f / 3f
        val gx = (ch.right + sp.left) / 2f; val gy = ir.centerY()
        p.color = 0xFF2E2E2E.toInt()
        c.drawRect(ch.right + 1 * u, gy - gh / 2 - 4 * u, sp.left - 1 * u, gy - gh / 2 - 3 * u, p)
        c.drawRect(ch.right + 1 * u, gy + gh / 2 + 3 * u, sp.left - 1 * u, gy + gh / 2 + 4 * u, p)
        p.color = 0xFF000000.toInt(); c.drawRect(gx - gw / 2, gy - gh / 2, gx + gw / 2, gy + gh / 2, p)
        p.color = 0xFF1B2430.toInt(); c.drawCircle(gx, gy, gh * 0.32f, p)
        p.color = 0xFF0D1218.toInt(); c.drawCircle(gx, gy, gh * 0.2f, p)
        // gigi sprocket
        p.color = 0xFF8A8A8A.toInt()
        for (i in 0..5) c.drawRect(sp.left - 3.5f * u, gy - gh / 2 + i * gh / 5.6f, sp.left - 2.2f * u, gy - gh / 2 + i * gh / 5.6f + 1.2f * u, p)
        t(c, "PREVIEW IN FILM CHAMBER", ch.centerX(), ch.bottom + 3.4f * u, 2.2f * u, 0xFF777777.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        val f = Films.all[index]
        // pratinjau tabung di ruang film
        c.save(); c.clipRect(ch)
        p.color = 0x99000000.toInt()
        Canister.draw(c, f, ch.centerX(), ch.centerY(), ch.height() * 0.82f, rot)
        c.drawRect(ch, p)
        c.restore()

        button(c, btnClose, "◂  CLOSE DOOR", 0xFF2B2A28.toInt(), 0xFFE8D9A8.toInt())
        button(c, btnLab, "ROLL SHELF", 0xFF7A4B2A.toInt(), 0xFFFFF1CC.toInt())

        // ---- kartu rak film
        p.color = 0xFFEFE6D2.toInt(); c.drawRoundRect(card, 3 * u, 3 * u, p)
        p.color = 0xFFD9CDB2.toInt(); c.drawRect(card.left, card.top + 9 * u, card.right, card.top + 9.4f * u, p)
        t(c, "FILM SHELF", card.left + 4 * u, card.top + 6.6f * u, 3.6f * u, 0xFF7A4B2A.toInt(), Typeface.DEFAULT_BOLD)
        t(c, "${index + 1} / ${Films.all.size}", card.right - 4 * u, card.top + 6.6f * u, 3.2f * u, 0xFF7A4B2A.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.RIGHT)

        Canister.draw(c, f, canArea.centerX(), canArea.centerY() - 1 * u, canArea.height() * 0.84f, rot)
        t(c, "drag to spin", canArea.centerX(), canArea.bottom + 1.5f * u, 2.1f * u, 0xFFA59A80.toInt(), Typeface.DEFAULT, Paint.Align.CENTER)

        val tx = canArea.right + 3 * u
        val tw = (card.right - 4 * u - tx).toInt().coerceAtLeast(10)
        var ty = card.top + 17 * u
        tp.typeface = Typeface.DEFAULT_BOLD; tp.textSize = 5.4f * u
        val nameSize = if (tp.measureText(f.fullName) > tw) 5.4f * u * tw / tp.measureText(f.fullName) else 5.4f * u
        t(c, f.fullName, tx, ty, nameSize, 0xFF1F1B14.toInt(), Typeface.DEFAULT_BOLD)
        ty += 5 * u
        t(c, "${f.type}  •  ISO ${f.iso}", tx, ty, 3f * u, 0xFF7A4B2A.toInt(), Typeface.DEFAULT_BOLD)
        ty += 3 * u
        ty = para(c, f.vibe, tx, ty, tw, 3.3f * u, 0xFF2E2A22.toInt())
        ty = para(c, "Format: ${f.formatNote}", tx, ty + 1.5f * u, tw, 2.9f * u, 0xFF7A4B2A.toInt())
        ty = para(c, "Best for: ${f.bestFor}", tx, ty + 1.5f * u, tw, 2.9f * u, 0xFF5E5646.toInt())
        // lencana isi roll
        val badge = "${f.frames} EXPOSURES"
        tp.typeface = Typeface.DEFAULT_BOLD; tp.textSize = 3.2f * u
        val bw = tp.measureText(badge) + 5 * u
        p.color = 0xFF1F1B14.toInt()
        c.drawRoundRect(tx, ty + 2 * u, tx + bw, ty + 8 * u, 3 * u, 3 * u, p)
        t(c, badge, tx + bw / 2, ty + 6.1f * u, 3.2f * u, 0xFFFFE9A8.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)

        button(c, btnPrev, "◀", 0xFFD9CDB2.toInt(), 0xFF1F1B14.toInt(), 4.5f)
        button(c, btnNext, "▶", 0xFFD9CDB2.toInt(), 0xFF1F1B14.toInt(), 4.5f)
        button(c, btnLoad, "LOAD THIS ROLL", 0xFFC8351F.toInt(), 0xFFFFFFFF.toInt(), 3.8f)

        note?.let {
            t(c, it, inner.centerX(), 5 * u, 2.8f * u, 0xFFFFE9A8.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        }

        if (spinning && visibility == VISIBLE && isAttachedToWindow) {
            rot += 0.012f
            postInvalidateOnAnimation()
        }
    }

    private fun para(c: Canvas, s: String, x: Float, y: Float, w: Int, size: Float, color: Int): Float {
        tp.typeface = Typeface.DEFAULT; tp.textSize = size; tp.color = color; tp.textAlign = Paint.Align.LEFT
        val sl = StaticLayout.Builder.obtain(s, 0, s.length, tp, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.12f).build()
        c.save(); c.translate(x, y); sl.draw(c); c.restore()
        return y + sl.height
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (developing) return true
        val x = e.x; val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                target = when {
                    btnPrev.contains(x, y) -> 1
                    btnNext.contains(x, y) -> 2
                    btnLoad.contains(x, y) -> 3
                    btnClose.contains(x, y) -> 4
                    btnLab.contains(x, y) -> 5
                    canArea.contains(x, y) -> 6
                    else -> 0
                }
                lastX = x
                if (target == 6) spinning = false
            }
            MotionEvent.ACTION_MOVE -> if (target == 6) {
                rot -= (x - lastX) / (canArea.width() * 0.25f)
                lastX = x; invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val n = Films.all.size
                when (target) {
                    1 -> if (btnPrev.contains(x, y)) { index = (index + n - 1) % n; rot = 0.6f }
                    2 -> if (btnNext.contains(x, y)) { index = (index + 1) % n; rot = 0.6f }
                    3 -> if (btnLoad.contains(x, y)) listener?.onLoad(Films.all[index])
                    4 -> if (btnClose.contains(x, y)) listener?.onCloseDoor()
                    5 -> if (btnLab.contains(x, y)) listener?.onLab()
                    6 -> spinning = true
                }
                if (abs(x - lastX) >= 0f) invalidate()
                target = 0
            }
            MotionEvent.ACTION_CANCEL -> { target = 0; spinning = true; invalidate() }
        }
        return true
    }
}
