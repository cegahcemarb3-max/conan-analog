package com.kokang.kamera

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

/** One exposed roll taken out of the camera: raw frames, prints once developed, and a small meta file. */
class Roll(val dir: File) {
    val id: Long = dir.name.toLongOrNull() ?: 0L
    var filmId = ""
    var name = ""
    var shots = 0
    var developed = false
    var ejectedAt = 0L

    private val meta get() = File(dir, "meta.properties")
    val hasMeta get() = meta.exists()
    val film: Film? get() = Films.byId(filmId)
    val rawDir get() = File(dir, "raw").apply { mkdirs() }
    val printsDir get() = File(dir, "prints").apply { mkdirs() }
    val dateText: String get() = SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(ejectedAt))

    fun prints(): List<File> = printsDir.listFiles { f -> f.extension == "jpg" }?.sortedBy { it.name } ?: emptyList()

    fun load(): Roll {
        val p = Properties()
        try { meta.reader(Charsets.UTF_8).use { p.load(it) } } catch (_: Exception) {}
        filmId = p.getProperty("film", ""); name = p.getProperty("name", "")
        shots = p.getProperty("shots", "0").toIntOrNull() ?: 0
        developed = p.getProperty("developed", "false") == "true"
        ejectedAt = p.getProperty("ejected", "0").toLongOrNull() ?: 0L
        return this
    }

    fun save() {
        val p = Properties()
        p.setProperty("film", filmId); p.setProperty("name", name); p.setProperty("shots", shots.toString())
        p.setProperty("developed", developed.toString()); p.setProperty("ejected", ejectedAt.toString())
        dir.mkdirs()
        meta.writer(Charsets.UTF_8).use { p.store(it, null) }
    }
}

object Rolls {
    fun root(ctx: Context) = File(ctx.filesDir, "rolls").apply { mkdirs() }
    fun dir(ctx: Context, id: Long) = File(root(ctx), id.toString()).apply { mkdirs() }

    /** Rolls on the shelf (the one still inside the camera has no meta file yet). */
    fun list(ctx: Context): List<Roll> =
        root(ctx).listFiles()?.map { Roll(it) }?.filter { it.hasMeta }?.map { it.load() }?.sortedByDescending { it.ejectedAt } ?: emptyList()

    /** Removes the roll, its raw frames, its prints, and the copies it exported to the phone gallery. */
    fun delete(ctx: Context, r: Roll) {
        val ex = File(r.dir, "exported.txt")
        if (ex.exists()) ex.readLines().filter { it.isNotBlank() }.forEach {
            try { ctx.contentResolver.delete(android.net.Uri.parse(it), null, null) } catch (_: Exception) {}
        }
        r.dir.deleteRecursively()
    }

    fun create(ctx: Context, id: Long, film: Film, shots: Int): Roll {
        val r = Roll(dir(ctx, id))
        r.filmId = film.id; r.shots = shots; r.developed = false; r.ejectedAt = System.currentTimeMillis()
        r.name = film.name + "  " + SimpleDateFormat("d MMM", Locale.US).format(Date(r.ejectedAt))
        r.save()
        return r
    }

    /** Moves data from the first versions (flat "roll" and "lab" folders) into per-roll folders. */
    fun migrate(ctx: Context, st: CamState) {
        val oldRaw = File(ctx.filesDir, "roll")
        if (oldRaw.exists()) {
            if (st.filmId != null) {
                val dst = File(dir(ctx, st.rollId), "raw").apply { mkdirs() }
                oldRaw.listFiles()?.forEach { it.renameTo(File(dst, it.name)) }
            }
            oldRaw.deleteRecursively()
        }
        val oldLab = File(ctx.filesDir, "lab")
        if (oldLab.exists()) {
            oldLab.listFiles { f -> f.extension == "jpg" }?.groupBy { it.name.substringBefore('_') }?.forEach { (rid, files) ->
                val id = rid.toLongOrNull() ?: return@forEach
                val r = Roll(dir(ctx, id))
                val parts = files.first().nameWithoutExtension.split("_")
                r.filmId = parts.getOrElse(1) { "" }; r.shots = files.size; r.developed = true; r.ejectedAt = id
                r.name = (r.film?.name ?: "Roll") + "  " + SimpleDateFormat("d MMM", Locale.US).format(Date(id))
                files.forEach { it.renameTo(File(r.printsDir, it.nameWithoutExtension.substringAfterLast('_') + ".jpg")) }
                r.save()
            }
            oldLab.deleteRecursively()
        }
    }
}
