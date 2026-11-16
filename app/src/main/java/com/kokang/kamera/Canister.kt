package com.kokang.kamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.asin

/** Tabung film pseudo-3D: tekstur label dibungkus ke silinder (pemetaan asin) lalu diberi shading. */
object Canister {
    private const val TW = 720
    private const val TH = 300
    private val cache = HashMap<String, Bitmap>()
    private val src = Rect()
    private val dst = RectF()
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun opaque(c: Int) = c or (0xFF shl 24)

    fun label(f: Film): Bitmap = cache.getOrPut(f.id) {
        val b = Bitmap.createBitmap(TW, TH, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(opaque(f.c1))
        p.color = opaque(f.c2)
        c.drawRect(0f, 0f, TW.toFloat(), 40f, p)
        c.drawRect(0f, TH - 40f, TW.toFloat(), TH.toFloat(), p)
        // bibir beludru tempat film keluar
        p.color = 0xFF0C0C0C.toInt()
        c.drawRect(0f, 0f, 34f, TH.toFloat(), p)
        // sisi A: merek + nama
        p.color = opaque(f.c3)
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        p.textSize = 40f
        c.drawText(f.maker.uppercase(), 56f, 104f, p)
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = 66f
        val maxW = 300f
        val tw = p.measureText(f.name)
        if (tw > maxW) p.textSize = 66f * maxW / tw
        c.drawText(f.name, 56f, 176f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = 26f
        c.drawText("${f.gauge}  •  ${f.frames} EXP", 56f, 226f, p)
        // sisi B: ISO besar
        p.color = opaque(f.c2)
        c.drawRoundRect(410f, 62f, 690f, 238f, 14f, 14f, p)
        p.color = opaque(f.c1)
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = 110f
        p.textAlign = Paint.Align.CENTER
        c.drawText(f.iso.toString(), 550f, 172f, p)
        p.textSize = 30f
        c.drawText("ISO  •  ${f.frames}", 550f, 218f, p)
        b
    }

    /** @param h tinggi total tabung, @param rot rotasi dalam radian */
    fun draw(c: Canvas, f: Film, cx: Float, cy: Float, h: Float, rot: Float) {
        val tex = label(f)
        val bodyH = h * 0.76f
        val r = h * 0.27f
        val top = cy - bodyH / 2f
        val bot = cy + bodyH / 2f
        val capH = h * 0.07f

        // bayangan lantai
        sp.shader = null
        sp.color = 0x55000000
        c.drawOval(cx - r * 1.25f, bot + capH * 0.5f, cx + r * 1.25f, bot + capH * 1.9f, sp)

        // badan berlabel
        val n = 44
        val twoPi = (2 * PI).toFloat()
        for (i in 0 until n) {
            val a0 = -1f + 2f * i / n
            val a1 = -1f + 2f * (i + 1) / n
            val t0 = asin(a0.coerceIn(-1f, 1f))
            val t1 = asin(a1.coerceIn(-1f, 1f))
            var u0 = ((t0 + rot) / twoPi) % 1f
            if (u0 < 0f) u0 += 1f
            val du = (t1 - t0) / twoPi
            val sx0 = (u0 * TW).toInt().coerceIn(0, TW - 1)
            val sx1 = ((u0 + du) * TW).toInt().coerceIn(sx0 + 1, TW)
            src.set(sx0, 0, sx1, TH)
            dst.set(cx + a0 * r, top, cx + a1 * r + 0.6f, bot)
            c.drawBitmap(tex, src, dst, bp)
        }
        sp.alpha = 255; sp.shader = LinearGradient(cx - r, 0f, cx + r, 0f,
            intArrayOf(0xB0000000.toInt(), 0x22000000, 0x48FFFFFF, 0x00000000, 0x30000000, 0xC0000000.toInt()),
            floatArrayOf(0f, 0.2f, 0.34f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(cx - r, top, cx + r, bot, sp)

        // tutup logam atas dan bawah + poros spool
        sp.alpha = 255
        val metal = LinearGradient(cx - r, 0f, cx + r, 0f,
            intArrayOf(0xFF3A3A3A.toInt(), 0xFF9A9A9A.toInt(), 0xFFE8E8E8.toInt(), 0xFF8A8A8A.toInt(), 0xFF2C2C2C.toInt()),
            floatArrayOf(0f, 0.22f, 0.36f, 0.6f, 1f), Shader.TileMode.CLAMP)
        sp.shader = metal
        val cr = r * 1.05f
        c.drawRoundRect(cx - cr, top - capH, cx + cr, top + capH * 0.15f, capH * 0.3f, capH * 0.3f, sp)
        c.drawRoundRect(cx - cr, bot - capH * 0.15f, cx + cr, bot + capH, capH * 0.3f, capH * 0.3f, sp)
        sp.alpha = 255; sp.shader = LinearGradient(cx - r * 0.3f, 0f, cx + r * 0.3f, 0f,
            intArrayOf(0xFF101010.toInt(), 0xFF555555.toInt(), 0xFF101010.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(cx - r * 0.3f, top - capH - h * 0.08f, cx + r * 0.3f, top - capH + 1f, sp)
        sp.shader = null
        sp.color = Color.BLACK
    }
}
