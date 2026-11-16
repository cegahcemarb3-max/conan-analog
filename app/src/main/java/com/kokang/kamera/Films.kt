package com.kokang.kamera

/**
 * Film profiles. All numbers are starting estimates (not manufacturer data),
 * scale -100..+100 relative to neutral. Names are deliberate parodies.
 * Rolls 13-17 combine a base stock look with an unusual frame format.
 */
data class Film(
    val id: String,
    val maker: String,
    val name: String,
    val iso: Int,
    val type: String,
    val frames: Int,
    val vibe: String,
    val bestFor: String,
    val c1: Int,          // label base colour
    val c2: Int,          // label accent colour
    val c3: Int,          // label text colour
    val temp: Int = 0,
    val tint: Int = 0,
    val contrast: Int = 0,
    val highlights: Int = 0,
    val shadows: Int = 0,
    val saturation: Int = 0,
    val ev: Float = 0f,
    val lift: Int = 0,        // black lift 0..255
    val white: Int = 255,     // white point 0..255
    val shTint: Int = 0x808080,
    val hiTint: Int = 0x808080,
    val grain: Int = 20,
    val grainSize: Float = 1f,
    val halation: Int = 0,
    val bw: Boolean = false,
    val mixR: Int = 30, val mixG: Int = 59, val mixB: Int = 11,
    // ---- format / camera layer
    val ratio: Float = 1.5f,  // frame width / height with the camera held level
    val vignette: Int = 30,   // 0..100
    val edgeSoft: Int = 0,    // 0..100 blur growing towards the corners
    val baseSoft: Int = 0,    // 0..100 overall softness (tiny negative / plastic lens)
    val barrel: Int = 0,      // 0..100 barrel distortion
    val leak: Int = 0,        // 0..100 random red-orange light leaks
    val diptych: Boolean = false,
    val formatNote: String = "35mm full frame 3:2"
) {
    val gauge get() = when (id) { "gex617", "holgah120" -> "120"; "saku110" -> "110"; else -> "35mm" }
    val fullName get() = if (name.startsWith(maker)) name else "$maker $name"
}

object Films {
    val all = listOf(
        Film("potret400", "Kodok", "Potret 400", 400, "C-41 colour negative", 18,
            "Soft, pastel, low contrast. Warm peachy skin, creamy highlights, slightly teal shadows.",
            "Portraits, street, weddings, almost anything",
            0xE9DDF0, 0x7A4FA0, 0x3A2450,
            temp = 8, tint = 3, contrast = -15, highlights = -20, shadows = 15, saturation = -10, ev = 0.3f,
            lift = 8, white = 248, shTint = 0x3F6F6A, hiTint = 0xF2D9B8, grain = 25, grainSize = 1f),
        Film("ekstar100", "Kodok", "Ekstar 100", 100, "C-41 colour negative", 18,
            "Super vivid and sharp. Punchy reds and blues, high contrast, grain you can barely see.",
            "Landscape, architecture, bright sun",
            0xC8102E, 0x4B1D6B, 0xFFFFFF,
            temp = -3, tint = 4, contrast = 25, highlights = -10, shadows = -5, saturation = 35,
            lift = 3, shTint = 0x2B3A55, hiTint = 0xFFF1E6, grain = 10, grainSize = 0.8f),
        Film("emas200", "Kodok", "Emas 200", 200, "C-41 colour negative", 12,
            "Golden 90s holiday-snap nostalgia. Warm all over, saturated yellows and reds, muted blues.",
            "Golden hour, travel, warm moods",
            0xF6C416, 0xD93A1E, 0x2A1A00,
            temp = 22, tint = 2, contrast = 8, highlights = -5, saturation = 18,
            lift = 6, shTint = 0x5A3A2A, hiTint = 0xFFE3A0, grain = 40, grainSize = 1.2f),
        Film("ultramaks400", "Kodok", "UltraMaks 400", 400, "C-41 colour negative", 12,
            "The honest people's film. Warm, contrasty, grainy, strong reds, greenish-yellow shadows.",
            "Everyday street, outdoors, snapshots",
            0x1F4FA8, 0xF6C416, 0xFFFFFF,
            temp = 14, tint = -2, contrast = 15, shadows = -5, saturation = 20,
            lift = 5, shTint = 0x3E4A33, hiTint = 0xFFE8B5, grain = 55, grainSize = 1.5f),
        Film("superiyah400", "Pujifilem", "Superiyah 400", 400, "C-41 colour negative", 12,
            "Cool and fresh. Lively emerald greens, teal blues, green-cyan shadows, slightly pale skin.",
            "Street, foliage, urban Asia",
            0x0E8A4A, 0xE8F4EC, 0xFFFFFF,
            temp = -10, tint = -12, contrast = 5, shadows = 5, saturation = 10,
            lift = 8, shTint = 0x2F5E55, hiTint = 0xE4F2EA, grain = 45, grainSize = 1.3f),
        Film("pro400ha", "Pujifilem", "Pro 400Ha", 400, "C-41 colour negative", 18,
            "Pastel, airy, high-key. A hint of mint in the highlights, blue-green shadows, soft pink skin.",
            "Weddings, bright outdoor portraits",
            0xDDF0E4, 0xE58AA6, 0x1F5A45,
            temp = -6, tint = -6, contrast = -22, highlights = -15, shadows = 20, saturation = -12, ev = 0.5f,
            lift = 12, white = 248, shTint = 0x4B7C78, hiTint = 0xE8F5EE, grain = 22, grainSize = 1f),
        Film("sinestel800t", "SineStel", "800T", 800, "C-41 tungsten negative", 18,
            "Cinematic nights. Teal shadows, red-orange city lights with a halation glow around them.",
            "City nights, neon, dim coffee stalls",
            0x161616, 0xE0452B, 0xFFFFFF,
            temp = -18, tint = -5, contrast = 3, saturation = 8, highlights = -8,
            lift = 7, shTint = 0x1F4A5C, hiTint = 0xFFB090, grain = 40, grainSize = 1.3f, halation = 60),
        Film("kodokrom64", "Kodok", "Kodokrom 64", 64, "K-14 slide", 18,
            "A 20th-century icon. Rich reds, golden yellows, deep blues, inky blacks, high contrast.",
            "Documentary, retro travel, geographic-magazine look",
            0xF2B705, 0xC8102E, 0x1A1A1A,
            temp = 10, tint = 2, contrast = 28, highlights = -5, shadows = -15, saturation = 22,
            lift = 1, shTint = 0x1E2A3A, hiTint = 0xFFEBC8, grain = 12, grainSize = 0.8f),
        Film("beludru50", "Pujifilem", "Beludru 50", 50, "E-6 slide", 18,
            "Extreme saturation. Emerald greens, very deep blues, shadows that block up fast. Skin goes ruddy.",
            "Landscape, nature, golden hour",
            0x4A1F6B, 0x14A05A, 0xFFFFFF,
            temp = 0, tint = 5, contrast = 35, highlights = -10, shadows = -20, saturation = 45,
            lift = 0, shTint = 0x3A1F4A, hiTint = 0x808080, grain = 8, grainSize = 0.8f),
        Film("propia100f", "Pujifilem", "Propia 100F", 100, "E-6 slide", 18,
            "Natural and accurate. Clean, slightly cool whites, moderate saturation and contrast.",
            "All-rounder, product, architecture",
            0x1B5FB5, 0xFFFFFF, 0xFFFFFF,
            temp = -3, contrast = 10, highlights = -5, saturation = 12,
            lift = 2, shTint = 0x26364A, hiTint = 0xF8FAFF, grain = 10, grainSize = 0.8f),
        Film("trieks400", "Kodok", "Tri-Eks 400", 400, "Black & white", 18,
            "Gritty. Deep blacks, bright whites, coarse uneven grain. Street-journalism feel.",
            "Street, documentary, low light",
            0x111111, 0xF6C416, 0xF6C416,
            contrast = 30, highlights = 5, shadows = -10,
            lift = 1, grain = 70, grainSize = 1.8f, bw = true, mixR = 30, mixG = 50, mixB = 20),
        Film("hape5", "Ilpord", "HaPe5 Plus 400", 400, "Black & white", 18,
            "Gentler than Tri-Eks. Smooth greys, shadows that keep their detail, calm highlights.",
            "B&W portraits, mixed light",
            0xF4F4F0, 0x1A1A1A, 0x1A1A1A,
            contrast = 12, highlights = -10, shadows = 8,
            lift = 5, grain = 50, grainSize = 1.4f, bw = true, mixR = 35, mixG = 45, mixB = 20),

        // ------------------------------------------------ odd formats
        Film("penahalf", "Olimpes", "Pena Half 400", 400, "Half-frame colour negative", 18,
            "Half the negative, twice the grain. Vertical frames that come back from the lab as side-by-side diptychs.",
            "Visual diaries, pairs of moments, travel",
            0xF3EFE2, 0x2F6FB0, 0x1F2A44,
            temp = 8, tint = 3, contrast = -12, highlights = -18, shadows = 12, saturation = -6, ev = 0.3f,
            lift = 8, white = 248, shTint = 0x3F6F6A, hiTint = 0xF2D9B8, grain = 42, grainSize = 1.6f,
            ratio = 0.75f, vignette = 20, edgeSoft = 15, diptych = true,
            formatNote = "Half frame 3:4 vertical, printed as diptychs"),
        Film("ekspan", "Haselbled", "EksPan 100", 100, "35mm panoramic", 10,
            "Wide cinematic strip. Vivid, crisp corner to corner, made for horizons and long streets.",
            "Cityscapes, horizons, cinematic framing",
            0x1A1A1A, 0xD8B45A, 0xF2E6C4,
            temp = -3, tint = 4, contrast = 22, highlights = -10, shadows = -5, saturation = 30,
            lift = 3, shTint = 0x2B3A55, hiTint = 0xFFF1E6, grain = 10, grainSize = 0.8f,
            ratio = 65f / 24f, vignette = 12, edgeSoft = 5,
            formatNote = "Panoramic 65:24 (about 2.7:1)"),
        Film("gex617", "Pujifilem", "GeX 617", 50, "6x17 panoramic slide", 4,
            "Only four shots. Absurdly wide 3:1 frame, saturated slide colour, almost no grain. Choose carefully.",
            "Grand landscapes, architecture",
            0x0F3D2E, 0xE8E0C8, 0xFFFFFF,
            temp = 0, tint = 5, contrast = 32, highlights = -10, shadows = -18, saturation = 42,
            lift = 0, shTint = 0x3A1F4A, hiTint = 0x808080, grain = 6, grainSize = 0.7f,
            ratio = 3f, vignette = 5,
            formatNote = "Panoramic 3:1, 4 frames per roll"),
        Film("saku110", "Kodok", "Saku 110", 200, "110 pocket cartridge", 12,
            "Lo-fi pocket snapshots. Soft plastic lens, heavy grain, faded colour, obvious vignette.",
            "70s and 80s nostalgia, careless snapshots",
            0xE2562B, 0xF6C416, 0xFFFFFF,
            temp = 14, tint = -2, contrast = -4, saturation = 6, shadows = 5,
            lift = 20, white = 240, shTint = 0x3E4A33, hiTint = 0xFFE8B5, grain = 85, grainSize = 2.4f,
            ratio = 4f / 3f, vignette = 40, edgeSoft = 45, baseSoft = 45,
            formatNote = "110 cartridge, 4:3, soft and grainy"),
        Film("holgah120", "Holgah", "120 Kotak", 400, "Plastic medium format", 12,
            "Toy camera squares. Sharp-ish centre, smeared corners, heavy vignette and random light leaks.",
            "Dreamy portraits, happy accidents",
            0x1C1C1C, 0x58C4C4, 0xFFFFFF,
            temp = 8, tint = 3, contrast = 8, highlights = -12, shadows = 6, saturation = 4, ev = 0.2f,
            lift = 8, white = 250, shTint = 0x3F6F6A, hiTint = 0xF2D9B8, grain = 40, grainSize = 1.3f,
            ratio = 1f, vignette = 65, edgeSoft = 60, baseSoft = 8, leak = 30, barrel = 24,
            formatNote = "Square 1:1, light leaks included")
    )

    fun byId(id: String?): Film? = all.firstOrNull { it.id == id }
}
