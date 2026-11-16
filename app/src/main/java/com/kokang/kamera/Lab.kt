package com.kokang.kamera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * "Lab cuci film". Jepretan mentah disimpan tersembunyi di rollDir dan baru diproses
 * (diberi tampilan film) saat roll dikeluarkan dari kamera.
 */
object Lab {

    fun rawFile(ctx: Context, rollId: Long, frame: Int, date: Boolean) =
        File(File(Rolls.dir(ctx, rollId), "raw").apply { mkdirs() }, String.format(Locale.US, "%02d_d%d.jpg", frame, if (date) 1 else 0))

    fun develop(ctx: Context, roll: Roll, onProgress: (Int, Int) -> Unit, onDone: (Int) -> Unit) {
        val film = roll.film ?: Films.all[0]
        val rollId = roll.id
        val main = Handler(Looper.getMainLooper())
        Thread {
            val raws = roll.rawDir.listFiles { f -> f.extension == "jpg" }?.sortedBy { it.name } ?: emptyList()
            var ok = 0
            var held: Bitmap? = null          // first half of a diptych
            var heldName = ""
            var heldTime = 0L; var heldDate = false
            fun save(bmp: Bitmap, frame: String, time: Long, date: Boolean) {
                if (date) dateStamp(bmp, time, film.bw)
                val name = "$frame.jpg"
                val out = File(roll.printsDir, name)
                FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                export(ctx, roll, out, roll.name.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-') + "_" + name)
                bmp.recycle()
                ok++
            }
            raws.forEachIndexed { i, raw ->
                main.post { onProgress(i, raws.size) }
                try {
                    val bmp = load(raw, film)
                    val w = bmp.width; val h = bmp.height
                    val px = IntArray(w * h)
                    bmp.getPixels(px, 0, w, 0, 0, w, h)
                    FilmLook.process(px, w, h, film, rollId + i)
                    bmp.setPixels(px, 0, w, 0, 0, w, h)
                    val frame = raw.name.substring(0, 2)
                    val date = raw.name.contains("_d1")
                    val h0 = held
                    if (!film.diptych) save(bmp, frame, raw.lastModified(), date)
                    else if (h0 == null) { held = bmp; heldName = frame; heldTime = raw.lastModified(); heldDate = date }
                    else if (h0.height == bmp.height) {
                        val gap = h0.height / 60
                        val both = Bitmap.createBitmap(h0.width + gap + bmp.width, h0.height, Bitmap.Config.ARGB_8888)
                        val c = Canvas(both)
                        c.drawColor(0xFF0A0A0A.toInt())
                        c.drawBitmap(h0, 0f, 0f, null); c.drawBitmap(bmp, (h0.width + gap).toFloat(), 0f, null)
                        h0.recycle(); bmp.recycle(); held = null
                        save(both, heldName, raw.lastModified(), date || heldDate)
                    } else {
                        save(h0, heldName, heldTime, heldDate); held = null
                        save(bmp, frame, raw.lastModified(), date)
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                raw.delete()
            }
            held?.let { try { save(it, heldName, heldTime, heldDate) } catch (e: Throwable) { e.printStackTrace() } }
            roll.developed = true; roll.save()
            main.post { onDone(ok) }
        }.start()
    }

    /** Decode, rotate per EXIF, crop to the film's frame ratio. */
    private fun load(f: File, film: Film): Bitmap {
        val LONG_SIDE = if (film.ratio > 2.4f) 3000 else if (film.diptych) 1500 else 2000
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (max(o.outWidth, o.outHeight) / (s * 2) >= LONG_SIDE) s *= 2
        val src = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
            inSampleSize = s; inMutable = false
        }) ?: throw IllegalStateException("gagal decode ${f.name}")
        val deg = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        // crop in sensor orientation; rotating afterwards swaps the sides for us
        val want = if (src.width >= src.height) film.ratio else 1f / film.ratio
        var cw = src.width; var ch = src.height
        if (cw.toFloat() / ch > want) cw = (ch * want).toInt() else ch = (cw / want).toInt()
        val scale = LONG_SIDE.toFloat() / max(cw, ch)
        val m = Matrix()
        if (scale < 1f) m.postScale(scale, scale)
        m.postRotate(deg)
        val out = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch, m, true)
        val res = if (out.isMutable) out else out.copy(Bitmap.Config.ARGB_8888, true)
        if (res !== src) src.recycle()
        if (res !== out) out.recycle()
        return res
    }

    private fun dateStamp(b: Bitmap, time: Long, bw: Boolean) {
        val c = Canvas(b)
        val s = max(b.width, b.height) * 0.028f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = s
            textAlign = Paint.Align.RIGHT
            color = if (bw) 0xFFF2F2F2.toInt() else 0xFFFF9A2E.toInt()
            setShadowLayer(s * 0.35f, 0f, 0f, if (bw) 0xAAFFFFFF.toInt() else 0xCCFF5A00.toInt())
        }
        val txt = SimpleDateFormat("''yy MM dd", Locale.US).format(Date(time))
        c.drawText(txt, b.width - s * 1.6f, b.height - s * 1.4f, p)
    }

    private fun export(ctx: Context, roll: Roll, f: File, name: String) {
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Conan35_$name")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Conan35")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { os -> f.inputStream().use { it.copyTo(os) } }
        File(roll.dir, "exported.txt").appendText(uri.toString() + "\n")
    }
}
