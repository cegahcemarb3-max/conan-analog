package com.kokang.kamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot

/** Render tampilan dan contoh hasil film ke build/shots untuk pemeriksaan visual. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ShotTest {
    private val out = File("build/shots").apply { mkdirs() }

    private fun shot(v: View, name: String, w: Int = 2400, h: Int = 1080) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b)); v.draw(Canvas(b)); v.draw(Canvas(b))   // several frames: catches paint state leaking between frames
        FileOutputStream(File(out, "$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun views() {
        val ctx = RuntimeEnvironment.getApplication()
        val st = CamState(ctx)
        val body = CameraBodyView(ctx).also { it.st = st }
        shot(body, "body_empty")
        st.load(Films.all[2]); st.frames = 7; st.cocked = true; st.focused = true; st.flash = true
        body.alert(CameraBodyView.Alert.WIND)
        shot(body, "body_loaded")
        shot(body, "body_169", 1920, 1080)
        FileOutputStream(File(out, "icon.png")).use { body.stickerIcon(432).compress(Bitmap.CompressFormat.PNG, 100, it) }
        st.load(Films.all[10]); st.frames = 16; st.cocked = false
        shot(body, "body_bw")
        val shelf = ShelfView(ctx)
        val tmp = File(out, "fake").apply { deleteRecursively(); mkdirs() }
        shelf.rolls = listOf(2, 6, 12, 10, 16, 0, 15).mapIndexed { i, fi ->
            Roll(File(tmp, (1000L + i).toString())).apply { filmId = Films.all[fi].id; name = listOf("Night walk", "Market day", "Beach trip", "Untitled", "Old town", "Family", "Hills")[i]; shots = Films.all[fi].frames; developed = i % 3 != 0; ejectedAt = 1794800000000L }
        }
        shot(shelf, "shelf")
        shelf.selected = shelf.rolls[0]
        shot(shelf, "shelf_card", 1920, 1080)
        val back = BackDoorView(ctx)
        back.note = "Pick a film, then LOAD THIS ROLL."
        shot(back, "back_0")
        back.index = 12
        shot(back, "back_6")
        back.index = 16
        shot(back, "back_169", 1920, 1080)
    }

    @Test
    fun looks() {
        val w = 600; val h = 400
        val base = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var r: Float; var g: Float; var b: Float
            if (y < 150) { val t = y / 150f; r = 0.35f + 0.35f * t; g = 0.55f + 0.25f * t; b = 0.92f - 0.05f * t }        // langit
            else if (y < 250) { val t = (y - 150) / 100f; r = 0.22f - 0.08f * t; g = 0.5f - 0.2f * t; b = 0.16f - 0.06f * t } // rumput
            else { r = 0.06f; g = 0.06f; b = 0.08f }                                                                    // malam
            // patch warna + kulit
            val pc = arrayOf(floatArrayOf(.8f, .1f, .1f), floatArrayOf(.95f, .8f, .1f), floatArrayOf(.1f, .6f, .2f), floatArrayOf(.1f, .25f, .8f),
                floatArrayOf(.93f, .72f, .6f), floatArrayOf(.62f, .42f, .32f), floatArrayOf(1f, 1f, 1f), floatArrayOf(.5f, .5f, .5f))
            if (y in 170..230) { val k = (x - 20) / 70; if (k in 0..7 && (x - 20) % 70 < 60) { r = pc[k][0]; g = pc[k][1]; b = pc[k][2] } }
            if (y in 380..399) { r = x / 599f; g = r; b = r }
            // lampu
            for ((lx, ly, col) in listOf(Triple(120, 310, floatArrayOf(1f, .95f, .8f)), Triple(330, 300, floatArrayOf(1f, .3f, .2f)), Triple(500, 320, floatArrayOf(.3f, 1f, .9f)))) {
                val d = hypot((x - lx).toFloat(), (y - ly).toFloat())
                if (d < 14) { r = col[0]; g = col[1]; b = col[2] }
            }
            base[y * w + x] = (255 shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
        }
        val sheet = Bitmap.createBitmap(w * 4, h * 6, Bitmap.Config.ARGB_8888)
        sheet.setPixels(base, 0, w, 0, 0, w, h)
        Films.all.forEachIndexed { i, f ->
            val px = base.copyOf()
            val t0 = System.nanoTime()
            FilmLook.process(px, w, h, f, 42L + i)
            println("${f.fullName}: ${(System.nanoTime() - t0) / 1_000_000} ms")
            val k = i + 1
            sheet.setPixels(px, 0, w, (k % 4) * w, (k / 4) * h, w, h)
        }
        FileOutputStream(File(out, "looks.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
