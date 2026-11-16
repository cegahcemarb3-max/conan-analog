package com.kokang.kamera

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Mesin "cuci film": murni Kotlin (tanpa API Android) supaya bisa diuji di JVM.
 * Urutan: exposure + white balance + kurva kontras + black lift (LUT per channel),
 * lalu shadows/highlights, saturasi atau mixer B&W, split toning, halation,
 * vignette lensa murah, dan grain prosedural yang bergantung luminance.
 */
object FilmLook {

    fun process(px: IntArray, w: Int, h: Int, f: Film, seed: Long) {
        if (f.barrel > 0) barrel(px, w, h, f.barrel / 100f * 0.5f)
        if (f.edgeSoft > 0 || f.baseSoft > 0) soften(px, w, h, f.edgeSoft / 100f, f.baseSoft / 100f)
        val vigK = f.vignette / 100f
        // random light leak (toy camera body seams)
        val lr0 = java.util.Random(seed * 31 + 7)
        val leakOn = f.leak > 0 && lr0.nextFloat() < 0.75f
        val leakSide = lr0.nextInt(4)
        val leakReach = 0.16f + lr0.nextFloat() * 0.26f
        val leakPos = lr0.nextFloat()
        val leakAmp = f.leak / 100f * (1.2f + lr0.nextFloat() * 1.2f)
        val gain = 2f.pow(f.ev)
        val wbR = 1f + 0.005f * f.temp
        val wbB = 1f - 0.005f * f.temp
        val wbG = 1f - 0.004f * f.tint
        val lr = lut(f, gain * wbR)
        val lg = lut(f, gain * wbG)
        val lb = lut(f, gain * wbB)

        val sh = f.shadows / 100f * 0.22f
        val hi = f.highlights / 100f * 0.22f
        val sat = 1f + f.saturation / 100f
        val mixSum = (f.mixR + f.mixG + f.mixB).toFloat()
        val mr = f.mixR / mixSum; val mg = f.mixG / mixSum; val mb = f.mixB / mixSum

        val sd = dev(f.shTint); val hd = dev(f.hiTint)

        // --- halation: mask highlight pada 1/8 resolusi, di-blur, lalu ditambahkan merah-oranye
        val hal = f.halation / 100f * 1.7f
        val hs = 8
        val hw = w / hs + 1; val hh = h / hs + 1
        var halo: FloatArray? = null
        if (hal > 0f) {
            val m = FloatArray(hw * hh)
            for (y in 0 until h step hs) for (x in 0 until w step hs) {
                val p = px[y * w + x]
                val mx = maxOf((p shr 16) and 255, (p shr 8) and 255, p and 255) / 255f
                val l = (0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)) / 255f
                m[(y / hs) * hw + x / hs] = min(1f, max(0f, 0.5f * mx + 0.5f * l - 0.8f) * 5f)
            }
            val r = max(2, w / hs / 28)
            repeat(3) { boxBlur(m, hw, hh, r) }
            halo = m
        }

        // --- grain: noise pada resolusi lebih rendah (ukuran butir), di-sample bilinear
        val gs = max(1f, f.grainSize * w / 1800f)
        val gw = (w / gs).toInt() + 2; val gh = (h / gs).toInt() + 2
        val noise = FloatArray(gw * gh)
        var s = seed xor 0x5DEECE66DL
        for (i in noise.indices) {
            s = s * 6364136223846793005L + 1442695040888963407L
            val a = ((s ushr 33) and 0xFFFF).toInt() / 65535f
            s = s * 6364136223846793005L + 1442695040888963407L
            val b = ((s ushr 33) and 0xFFFF).toInt() / 65535f
            noise[i] = (a + b - 1f) * 1.6f
        }
        val gAmp = f.grain / 100f * 0.17f

        val cx = w / 2f; val cy = h / 2f
        val invR2 = 1f / (cx * cx + cy * cy)

        for (y in 0 until h) {
            val dy = y - cy
            for (x in 0 until w) {
                val i = y * w + x
                val p = px[i]
                var r = lr[(p shr 16) and 255]
                var g = lg[(p shr 8) and 255]
                var b = lb[p and 255]
                var l = 0.299f * r + 0.587f * g + 0.114f * b

                // shadows / highlights
                val sm = (1f - l) * (1f - l) * min(1f, l * 4f)
                val hm = l * l * min(1f, (1f - l) * 4f)
                val d = sh * sm + hi * hm
                r += d; g += d; b += d; l += d

                if (f.bw) {
                    val v = mr * r + mg * g + mb * b
                    r = v; g = v; b = v; l = v
                } else {
                    r = l + (r - l) * sat; g = l + (g - l) * sat; b = l + (b - l) * sat
                    val lc = if (l < 0f) 0f else if (l > 1f) 1f else l
                    val ws = (1f - lc) * (1f - lc) * 0.28f
                    val wh = lc * lc * 0.5f
                    r += ws * sd[0] + wh * hd[0]
                    g += ws * sd[1] + wh * hd[1]
                    b += ws * sd[2] + wh * hd[2]
                }

                if (halo != null) {
                    val m = bilinear(halo, hw, hh, x / hs.toFloat(), y / hs.toFloat()) * hal
                    r += m; g += m * 0.32f; b += m * 0.12f
                }

                if (leakOn) {
                    val u = x / w.toFloat(); val v = y / h.toFloat()
                    val d = when (leakSide) { 0 -> u; 1 -> 1f - u; 2 -> v; else -> 1f - v } / leakReach
                    val t = ((if (leakSide < 2) v else u) - leakPos) / 0.4f
                    val a = leakAmp * kotlin.math.exp(-d * 2.4f) * (0.35f + 0.65f * kotlin.math.exp(-t * t))
                    r += a; g += a * 0.42f; b += a * 0.08f
                }

                // vignette lensa plastik
                val dx = x - cx
                val rr = (dx * dx + dy * dy) * invR2
                val vg = 1f - vigK * rr * sqrt(rr)
                r *= vg; g *= vg; b *= vg

                // grain, paling terlihat di midtone
                val lc = if (l < 0f) 0f else if (l > 1f) 1f else l
                val n = bilinear(noise, gw, gh, x / gs, y / gs) * gAmp * (0.35f + 0.65f * 4f * lc * (1f - lc))
                r += n; g += n; b += n

                val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
                val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
                val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
                px[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
    }

    /** Barrel distortion: centre bulges, straight lines bow outwards; corners stay put so no black edges. */
    private fun barrel(px: IntArray, w: Int, h: Int, k: Float) {
        val src = px.copyOf()
        val cx = (w - 1) / 2f; val cy = (h - 1) / 2f
        val invR2 = 1f / (cx * cx + cy * cy)
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x - cx; val dy = y - cy
            val s = (1f + k * (dx * dx + dy * dy) * invR2) / (1f + k)
            val fx = (cx + dx * s).coerceIn(0f, w - 1.001f); val fy = (cy + dy * s).coerceIn(0f, h - 1.001f)
            val x0 = fx.toInt(); val y0 = fy.toInt()
            val tx = fx - x0; val ty = fy - y0
            val a = src[y0 * w + x0]; val b = src[y0 * w + x0 + 1]; val c = src[(y0 + 1) * w + x0]; val d = src[(y0 + 1) * w + x0 + 1]
            var out = 0xFF shl 24
            var sh = 16
            while (sh >= 0) {
                val top = ((a shr sh) and 255) + (((b shr sh) and 255) - ((a shr sh) and 255)) * tx
                val bot = ((c shr sh) and 255) + (((d shr sh) and 255) - ((c shr sh) and 255)) * tx
                out = out or ((top + (bot - top) * ty + 0.5f).toInt().coerceIn(0, 255) shl sh)
                sh -= 8
            }
            px[y * w + x] = out
        }
    }

    /** Blur that grows towards the corners (plus an overall base amount). */
    private fun soften(px: IntArray, w: Int, h: Int, edge: Float, base: Float) {
        val ds = 4
        val sw = w / ds; val sh = h / ds
        if (sw < 4 || sh < 4) return
        val R = FloatArray(sw * sh); val G = FloatArray(sw * sh); val B = FloatArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            var r = 0; var g = 0; var b = 0
            for (k in 0 until 4) {
                val p = px[(y * ds + (k shr 1) * 2) * w + x * ds + (k and 1) * 2]
                r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255
            }
            R[y * sw + x] = r / 4f; G[y * sw + x] = g / 4f; B[y * sw + x] = b / 4f
        }
        val rad = max(1, (sw * max(edge, base) * 0.012f).toInt())
        repeat(2) { boxBlur(R, sw, sh, rad); boxBlur(G, sw, sh, rad); boxBlur(B, sw, sh, rad) }
        val cx = w / 2f; val cy = h / 2f
        val invR2 = 1f / (cx * cx + cy * cy)
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x - cx; val dy = y - cy
            val rr = (dx * dx + dy * dy) * invR2
            val wgt = (base + edge * rr * sqrt(sqrt(rr)) * 1.6f).coerceIn(0f, 1f)
            if (wgt < 0.02f) continue
            val fx = (x + 0.5f) / ds - 0.5f; val fy = (y + 0.5f) / ds - 0.5f
            val p = px[y * w + x]
            val r = ((p shr 16) and 255) * (1 - wgt) + bilinear(R, sw, sh, fx, fy) * wgt
            val g = ((p shr 8) and 255) * (1 - wgt) + bilinear(G, sw, sh, fx, fy) * wgt
            val b = (p and 255) * (1 - wgt) + bilinear(B, sw, sh, fx, fy) * wgt
            px[y * w + x] = (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
        }
    }

    private fun lut(f: Film, gain: Float): FloatArray {
        val k = 1f + f.contrast / 100f * 0.9f
        val lo = f.lift / 255f
        val hi = f.white / 255f
        return FloatArray(256) { i ->
            var x = i / 255f * gain
            // bahu highlight halus supaya overexposure tidak langsung clip
            if (gain > 1.001f && x > 0.8f) {
                val norm = 1f - kotlin.math.exp(-(gain - 0.8f) / 0.2f)
                x = 0.8f + 0.2f * (1f - kotlin.math.exp(-(x - 0.8f) / 0.2f)) / norm
            }
            x = x.coerceIn(0f, 1f)
            x = if (x < 0.5f) 0.5f * (2f * x).pow(k) else 1f - 0.5f * (2f * (1f - x)).pow(k)
            lo + x * (hi - lo)
        }
    }

    private fun dev(c: Int): FloatArray {
        val r = ((c shr 16) and 255) / 255f
        val g = ((c shr 8) and 255) / 255f
        val b = (c and 255) / 255f
        val a = (r + g + b) / 3f
        return floatArrayOf(r - a, g - a, b - a)
    }

    private fun bilinear(a: FloatArray, w: Int, h: Int, fx: Float, fy: Float): Float {
        val x0 = min(fx.toInt(), w - 2).coerceAtLeast(0)
        val y0 = min(fy.toInt(), h - 2).coerceAtLeast(0)
        val tx = (fx - x0).coerceIn(0f, 1f); val ty = (fy - y0).coerceIn(0f, 1f)
        val i = y0 * w + x0
        val top = a[i] + (a[i + 1] - a[i]) * tx
        val bot = a[i + w] + (a[i + w + 1] - a[i + w]) * tx
        return top + (bot - top) * ty
    }

    private fun boxBlur(a: FloatArray, w: Int, h: Int, r: Int) {
        val t = FloatArray(max(w, h))
        val n = (2 * r + 1).toFloat()
        for (y in 0 until h) {
            var acc = 0f
            for (k in -r..r) acc += a[y * w + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                t[x] = acc / n
                acc += a[y * w + (x + r + 1).coerceAtMost(w - 1)] - a[y * w + (x - r).coerceAtLeast(0)]
            }
            for (x in 0 until w) a[y * w + x] = t[x]
        }
        for (x in 0 until w) {
            var acc = 0f
            for (k in -r..r) acc += a[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                t[y] = acc / n
                acc += a[(y + r + 1).coerceAtMost(h - 1) * w + x] - a[(y - r).coerceAtLeast(0) * w + x]
            }
            for (y in 0 until h) a[y * w + x] = t[y]
        }
    }
}
