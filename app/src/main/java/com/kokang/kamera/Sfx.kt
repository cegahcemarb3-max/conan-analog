package com.kokang.kamera

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Bunyi mekanik sintetis (tanpa aset): rana, gerigi kokang, kunci, pintu. */
class Sfx(ctx: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build()).build()
    private val ids = HashMap<String, Int>()
    private val sr = 44100

    init {
        val dir = File(ctx.cacheDir, "sfx").apply { mkdirs() }
        val rnd = Random(7)
        fun make(name: String, ms: Int, gen: (t: Float) -> Float) {
            val n = sr * ms / 1000
            val data = ShortArray(n) { i -> (gen(i / sr.toFloat()).coerceIn(-1f, 1f) * 30000).toInt().toShort() }
            val f = File(dir, "$name.wav")
            writeWav(f, data)
            ids[name] = pool.load(f.absolutePath, 1)
        }
        // "krek": a short ratchet rasp = a few low-passed micro-clicks
        var lp = 0f
        fun rasp(t: Float, clicks: Int, gap: Float, decay: Float, alpha: Float, grow: Float): Float {
            var x = 0f
            for (k in 0 until clicks) {
                val dt = t - k * gap
                if (dt >= 0f) x += (rnd.nextFloat() * 2 - 1) * exp(-dt * decay) * (1f + grow * k)
            }
            lp += alpha * (x - lp)
            return lp
        }
        make("tick", 45) { t -> if (t == 0f) lp = 0f; rasp(t, 3, 0.009f, 650f, 0.42f, 0f) * 1.5f }
        // the last, deeper KREK when the wheel locks
        make("krek", 260) { t ->
            if (t == 0f) lp = 0f
            rasp(t, 6, 0.012f, 260f, 0.16f, 0.25f) * 2.6f +
                sin(2 * PI.toFloat() * 92f * t) * exp(-t * 30f) * 0.75f +
                sin(2 * PI.toFloat() * 184f * t) * exp(-t * 45f) * 0.3f
        }
        make("clunk", 110) { t ->
            sin(2 * PI.toFloat() * 150f * t) * exp(-t * 45f) * 0.9f + (rnd.nextFloat() * 2 - 1) * exp(-t * 220f) * 0.5f
        }
        make("shutter", 120) { t ->
            val a = (rnd.nextFloat() * 2 - 1) * exp(-t * 260f)
            val t2 = t - 0.045f
            val b = if (t2 > 0) (rnd.nextFloat() * 2 - 1) * exp(-t2 * 180f) * 0.8f else 0f
            a + b + sin(2 * PI.toFloat() * 220f * t) * exp(-t * 80f) * 0.3f
        }
        make("door", 160) { t ->
            sin(2 * PI.toFloat() * 90f * t) * exp(-t * 30f) * 0.9f + (rnd.nextFloat() * 2 - 1) * exp(-t * 120f) * 0.6f
        }
    }

    fun play(name: String, vol: Float = 1f) {
        ids[name]?.let { pool.play(it, vol, vol, 1, 0, 1f) }
    }

    fun release() = pool.release()

    private fun writeWav(f: File, d: ShortArray) {
        val bb = ByteBuffer.allocate(44 + d.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + d.size * 2).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sr).putInt(sr * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(d.size * 2)
        for (s in d) bb.putShort(s)
        f.writeBytes(bb.array())
    }
}
