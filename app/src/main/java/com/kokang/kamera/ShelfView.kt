package com.kokang.kamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/** Wooden shelf holding every roll taken out of the camera, developed or not. */
class ShelfView(ctx: Context) : View(ctx) {

    interface Listener {
        fun onShelfBack()
        fun onRename(r: Roll)
        fun onDevelop(r: Roll)
        fun onViewPrints(r: Roll)
        fun onDelete(r: Roll)
    }

    var listener: Listener? = null
    var rolls: List<Roll> = emptyList()
    var selected: Roll? = null
    var developing = false
    var devDone = 0
    var devTotal = 0

    private var u = 10f
    private var scroll = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val btnBack = RectF()
    private val card = RectF()
    private val btnA = RectF()
    private val btnB = RectF()
    private val btnName = RectF()
    private val btnX = RectF()
    private val btnDel = RectF()
    private var downX = 0f; private var downY = 0f; private var lastX = 0f
    private var dragging = false

    private val slot get() = 23f * u
    private val boardY get() = floatArrayOf(51f * u, 91f * u)
    private val canH get() = 27f * u

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val W = w.toFloat()
        u = h / 100f
        btnBack.set(W - 36 * u, 2.5f * u, W - 4 * u, 11 * u)
        card.set(W - 92 * u, 14 * u, W - 4 * u, 97 * u)
        btnX.set(card.right - 10 * u, card.top + 2 * u, card.right - 2 * u, card.top + 10 * u)
        btnA.set(card.left + 4 * u, card.bottom - 15 * u, card.centerX() - 1.5f * u, card.bottom - 4 * u)
        btnB.set(card.centerX() + 1.5f * u, card.bottom - 15 * u, card.right - 4 * u, card.bottom - 4 * u)
        btnDel.set(card.left + 30 * u, card.top + 51 * u, card.left + 60 * u, card.top + 59 * u)
        btnName.set(card.left + 30 * u, card.top + 27 * u, card.right - 4 * u, card.top + 35 * u)
    }

    private fun t(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface = Typeface.DEFAULT, al: Paint.Align = Paint.Align.LEFT, maxW: Float = 0f) {
        tp.typeface = tf; tp.textSize = size; tp.color = color; tp.textAlign = al
        val str = if (maxW > 0f) TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END).toString() else s
        c.drawText(str, x, y, tp)
        tp.textAlign = Paint.Align.LEFT
    }

    private fun button(c: Canvas, r: RectF, label: String, bg: Int, fg: Int, size: Float = 3.4f) {
        p.shader = null
        p.color = 0x66000000; c.drawRoundRect(r.left, r.top + 0.8f * u, r.right, r.bottom + 0.8f * u, 2.2f * u, 2.2f * u, p)
        p.color = bg; c.drawRoundRect(r, 2.2f * u, 2.2f * u, p)
        t(c, label, r.centerX(), r.centerY() + size * 0.36f * u, size * u, fg, Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
    }

    private fun rollX(i: Int) = 8 * u + (i / 2) * slot + slot / 2 - scroll
    private fun maxScroll() = ((rolls.size + 1) / 2 * slot + 16 * u - width).coerceAtLeast(0f)

    override fun onDraw(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat()
        // back panel: dark vertical planks with grain
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(0xFF5A3A1F.toInt(), 0xFF3C2512.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p); p.shader = null
        val rnd = java.util.Random(7)
        p.style = Paint.Style.STROKE
        for (i in 0 until 70) {
            val y = rnd.nextFloat() * H; val amp = (0.3f + rnd.nextFloat() * 1.2f) * u; val ph = rnd.nextFloat() * 6f
            p.strokeWidth = (0.1f + rnd.nextFloat() * 0.25f) * u
            p.color = if (rnd.nextBoolean()) 0x22000000 else 0x18FFD9A0
            path.reset(); path.moveTo(0f, y)
            var x = 0f
            while (x <= W) { path.lineTo(x, y + sin(x / (40 * u) + ph) * amp); x += 8 * u }
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
        p.color = 0x40000000
        var sx = 37 * u
        while (sx < W) { c.drawRect(sx, 0f, sx + 0.35f * u, H, p); sx += 41 * u }

        // brass plaque + back button
        p.color = 0xFF000000.toInt()
        p.alpha = 255; p.shader = LinearGradient(0f, 2.5f * u, 0f, 11 * u, intArrayOf(0xFFE3C77A.toInt(), 0xFFA8873A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(4 * u, 2.5f * u, 52 * u, 11 * u, 1.2f * u, 1.2f * u, p); p.shader = null
        p.color = 0xFF6B5320.toInt()
        for (x in listOf(6 * u, 50 * u)) c.drawCircle(x, 6.75f * u, 0.7f * u, p)
        t(c, "ROLL SHELF", 28 * u, 8.2f * u, 4f * u, 0xFF3E2E0E.toInt(), Typeface.create(Typeface.SERIF, Typeface.BOLD), Paint.Align.CENTER)
        val nDev = rolls.count { it.developed }
        t(c, "${rolls.size} rolls  •  $nDev developed  •  ${rolls.size - nDev} waiting", 56 * u, 8f * u, 2.8f * u, 0xFFE8D2A8.toInt(), Typeface.DEFAULT_BOLD)
        button(c, btnBack, "◂  BACK", 0xFF2B2A28.toInt(), 0xFFE8D9A8.toInt())

        // two boards
        for (by in boardY) {
            p.alpha = 255; p.shader = LinearGradient(0f, by + 7 * u, 0f, by + 16 * u, intArrayOf(0x88000000.toInt(), 0x00000000), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, by + 7 * u, W, by + 16 * u, p)
            p.alpha = 255; p.shader = LinearGradient(0f, by - 2.5f * u, 0f, by, intArrayOf(0xFF7A4E26.toInt(), 0xFFA9743C.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, by - 2.5f * u, W, by, p)
            p.alpha = 255; p.shader = LinearGradient(0f, by, 0f, by + 7 * u, intArrayOf(0xFFB98445.toInt(), 0xFF8A5A2B.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, by, W, by + 7 * u, p)
            p.shader = null
            p.color = 0x55FFE2B0; c.drawRect(0f, by, W, by + 0.3f * u, p)
        }

        if (rolls.isEmpty()) {
            t(c, "No rolls here yet.", W / 2, 30 * u, 4.2f * u, 0xFFE8D2A8.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
            t(c, "Finish a roll, rewind it, and take it out of the camera.", W / 2, 37 * u, 3f * u, 0xFFC9AE80.toInt(), Typeface.DEFAULT, Paint.Align.CENTER)
        }

        rolls.forEachIndexed { i, r ->
            val x = rollX(i)
            if (x < -slot || x > W + slot) return@forEachIndexed
            val by = boardY[i % 2]
            val f = r.film ?: Films.all[0]
            Canister.draw(c, f, x, by - 2.2f * u - canH * 0.45f, canH, 1.8f)
            // paper label on the board edge: name + state
            p.color = 0xFFF3EAD3.toInt()
            c.drawRoundRect(x - slot / 2 + 1.2f * u, by + 0.6f * u, x + slot / 2 - 1.2f * u, by + 6.4f * u, 0.6f * u, 0.6f * u, p)
            t(c, r.name, x, by + 3f * u, 2.1f * u, 0xFF2A2218.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER, slot - 4 * u)
            p.color = if (r.developed) 0xFF2E7D32.toInt() else 0xFFC8351F.toInt()
            c.drawCircle(x - slot / 2 + 3.4f * u, by + 4.85f * u, 0.75f * u, p)
            t(c, if (r.developed) "DEVELOPED" else "NOT DEVELOPED", x - slot / 2 + 5 * u, by + 5.5f * u, 1.7f * u,
                if (r.developed) 0xFF2E7D32.toInt() else 0xFFC8351F.toInt(), Typeface.DEFAULT_BOLD)
            // state tag hanging on the canister
            if (r.developed) {
                p.color = 0xFF2E7D32.toInt(); c.drawCircle(x + canH * 0.24f, by - canH * 0.86f, 2.5f * u, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.7f * u; p.strokeCap = Paint.Cap.ROUND; p.color = 0xFFFFFFFF.toInt()
                path.reset(); path.moveTo(x + canH * 0.24f - 1.1f * u, by - canH * 0.86f); path.rLineTo(0.8f * u, 0.9f * u); path.rLineTo(1.5f * u, -1.8f * u)
                c.drawPath(path, p); p.style = Paint.Style.FILL; p.strokeCap = Paint.Cap.BUTT
            } else {
                // film leader still sticking out of an undeveloped roll
                p.color = 0xFF3A2A22.toInt()
                c.drawRect(x + canH * 0.26f, by - canH * 0.62f, x + canH * 0.26f + 3.2f * u, by - canH * 0.30f, p)
                p.color = 0xFF1A1210.toInt()
                for (k in 0..3) c.drawRect(x + canH * 0.26f + 2.2f * u, by - canH * 0.60f + k * 2.1f * u, x + canH * 0.26f + 2.9f * u, by - canH * 0.60f + k * 2.1f * u + 1.1f * u, p)
            }
        }

        selected?.let { drawCard(c, it) }

        if (developing) {
            p.color = 0xE6100E0C.toInt(); c.drawRect(0f, 0f, W, H, p)
            t(c, "Developing your film at the lab…", W / 2, H * 0.42f, 5.5f * u, 0xFFFFE9A8.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
            t(c, "frame ${devDone + 1} of $devTotal", W / 2, H * 0.52f, 3.6f * u, 0xFFD9CDB2.toInt(), Typeface.DEFAULT, Paint.Align.CENTER)
            val bw = W * 0.4f
            p.color = 0xFF3A322A.toInt(); c.drawRoundRect(W / 2 - bw / 2, H * 0.58f, W / 2 + bw / 2, H * 0.61f, u, u, p)
            p.color = 0xFFC8351F.toInt()
            val fr = if (devTotal > 0) devDone.toFloat() / devTotal else 0f
            c.drawRoundRect(W / 2 - bw / 2, H * 0.58f, W / 2 - bw / 2 + bw * fr, H * 0.61f, u, u, p)
        }
    }

    private fun drawCard(c: Canvas, r: Roll) {
        p.color = 0x99000000.toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.color = 0xFFEFE6D2.toInt(); c.drawRoundRect(card, 3 * u, 3 * u, p)
        val f = r.film ?: Films.all[0]
        Canister.draw(c, f, card.left + 15 * u, card.top + 34 * u, 44 * u, 1.8f)
        val tx = card.left + 30 * u
        val tw = card.right - 12 * u - tx
        t(c, if (r.developed) "DEVELOPED ROLL" else "FRESH OUT OF THE CAMERA", tx, card.top + 8 * u, 2.6f * u,
            if (r.developed) 0xFF2E7D32.toInt() else 0xFFC8351F.toInt(), Typeface.DEFAULT_BOLD)
        t(c, f.fullName, tx, card.top + 15 * u, 4.6f * u, 0xFF1F1B14.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.LEFT, tw)
        t(c, "${r.shots} exposures  •  ${r.dateText}", tx, card.top + 20 * u, 2.8f * u, 0xFF7A4B2A.toInt(), Typeface.DEFAULT_BOLD)
        t(c, "NAME / TAG", tx, card.top + 25.5f * u, 2.1f * u, 0xFF8A7F68.toInt(), Typeface.DEFAULT_BOLD)
        p.color = 0xFFFFFBF0.toInt(); c.drawRoundRect(btnName, 1.2f * u, 1.2f * u, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.3f * u; p.color = 0xFFB9AD92.toInt()
        c.drawRoundRect(btnName, 1.2f * u, 1.2f * u, p); p.style = Paint.Style.FILL
        t(c, r.name, btnName.left + 2 * u, btnName.centerY() + 1.2f * u, 3.3f * u, 0xFF1F1B14.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.LEFT, btnName.width() - 12 * u)
        t(c, "✎ EDIT", btnName.right - 2 * u, btnName.centerY() + 0.9f * u, 2.3f * u, 0xFFC8351F.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.RIGHT)
        val l1 = if (r.developed) "Tap VIEW PRINTS to open" else "Develop it now, or keep it on the"
        val l2 = if (r.developed) "the photos from this roll." else "shelf and develop it later."
        t(c, l1, tx, card.top + 41 * u, 2.6f * u, 0xFF5E5646.toInt())
        t(c, l2, tx, card.top + 44.6f * u, 2.6f * u, 0xFF5E5646.toInt())
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.35f * u; p.color = 0xFFC8351F.toInt()
        c.drawRoundRect(btnDel, 1.6f * u, 1.6f * u, p); p.style = Paint.Style.FILL
        t(c, "DELETE ROLL", btnDel.centerX(), btnDel.centerY() + 1f * u, 2.7f * u, 0xFFC8351F.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        t(c, "✕", btnX.centerX(), btnX.centerY() + 1.6f * u, 4.4f * u, 0xFF7A4B2A.toInt(), Typeface.DEFAULT_BOLD, Paint.Align.CENTER)
        if (r.developed) {
            button(c, btnA, "VIEW PRINTS", 0xFFC8351F.toInt(), 0xFFFFFFFF.toInt())
            button(c, btnB, "CLOSE", 0xFF2B2A28.toInt(), 0xFFE8D9A8.toInt())
        } else {
            button(c, btnA, "DEVELOP NOW", 0xFFC8351F.toInt(), 0xFFFFFFFF.toInt())
            button(c, btnB, "KEEP ON SHELF", 0xFF2B2A28.toInt(), 0xFFE8D9A8.toInt())
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (developing) return true
        val x = e.x; val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = x; downY = y; lastX = x; dragging = false }
            MotionEvent.ACTION_MOVE -> if (selected == null) {
                if (abs(x - downX) > 2 * u) dragging = true
                if (dragging) { scroll = (scroll - (x - lastX)).coerceIn(0f, maxScroll()); invalidate() }
                lastX = x
            }
            MotionEvent.ACTION_UP -> if (!dragging) {
                val sel = selected
                if (sel != null) {
                    when {
                        btnName.contains(x, y) -> listener?.onRename(sel)
                        btnDel.contains(x, y) -> listener?.onDelete(sel)
                        btnA.contains(x, y) -> if (sel.developed) listener?.onViewPrints(sel) else listener?.onDevelop(sel)
                        btnB.contains(x, y) || btnX.contains(x, y) || !card.contains(x, y) -> selected = null
                    }
                } else if (btnBack.contains(x, y)) listener?.onShelfBack()
                else {
                    rolls.forEachIndexed { i, r ->
                        val by = boardY[i % 2]
                        if (abs(x - rollX(i)) < slot / 2 && y > by - canH - 2 * u && y < by + 7 * u) selected = r
                    }
                }
                invalidate()
            }
        }
        return true
    }
}
