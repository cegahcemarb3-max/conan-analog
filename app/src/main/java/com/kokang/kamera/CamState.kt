package com.kokang.kamera

import android.content.Context

/** Keadaan kamera yang bertahan walau aplikasi ditutup: roll terpasang tetap terpasang. */
class CamState(ctx: Context) {
    private val p = ctx.getSharedPreferences("cam", Context.MODE_PRIVATE)

    var filmId: String? = p.getString("film", null)
    var frames: Int = p.getInt("frames", 0)          // jumlah frame yang sudah dijepret
    var cocked: Boolean = p.getBoolean("cocked", false)
    var rewinding: Boolean = p.getBoolean("rewinding", false)
    var rewound: Boolean = p.getBoolean("rewound", false)
    var rollId: Long = p.getLong("roll", 0L)
    var flash: Boolean = p.getBoolean("flash", false)
    var date: Boolean = p.getBoolean("date", true)

    // sementara (tidak disimpan)
    var windProgress = 0f       // 0..1
    var rewindShown = -1f       // angka counter selama rewind
    var crankAngle = 0f
    var focused = false
    var slow = false            // shutter slower than 1/30 s
    var batteryLow = false

    val film: Film? get() = Films.byId(filmId)
    val remaining: Int get() = (film?.frames ?: 0) - frames

    fun load(f: Film) {
        filmId = f.id; frames = 0; cocked = false; rewinding = false; rewound = false
        rollId = System.currentTimeMillis(); windProgress = 0f; rewindShown = -1f
        save()
    }

    fun eject() {
        filmId = null; frames = 0; cocked = false; rewinding = false; rewound = false
        windProgress = 0f; rewindShown = -1f
        save()
    }

    fun save() {
        p.edit().putString("film", filmId).putInt("frames", frames).putBoolean("cocked", cocked)
            .putBoolean("rewinding", rewinding).putBoolean("rewound", rewound).putLong("roll", rollId)
            .putBoolean("flash", flash).putBoolean("date", date).apply()
    }
}
