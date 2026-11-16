package com.kokang.kamera

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Red-on-black dot matrix status display (9 x 21 dots): one 9x9 icon plus up to two 5x7 characters. */
object DotMatrix {
    const val ROWS = 9
    const val COLS = 9

    val ICONS: Map<String, Array<String>> = mapOf(
        // counter-clockwise arrow: wind the film
        "ccw" to arrayOf("...####..", ".##....#.", "###.....#", ".#......#", "........#", "#.......#", "#.......#", ".#.....#.", "..#####.."),
        // clockwise arrow: rewind
        "cw" to arrayOf("..####...", ".#....##.", "#.....###", "#......#.", "#........", "#.......#", "#.......#", ".#.....#.", "..#####.."),
        "warn" to arrayOf("....#....", "...#.#...", "...#.#...", "..#.#.#..", "..#.#.#..", ".#..#..#.", ".#.....#.", "#...#...#", "#########"),
        // hold steady: vibration marks around a lens
        "steady" to arrayOf(".........", ".#.....#.", "#..###..#", "#.#####.#", "#.##.##.#", "#.#####.#", "#..###..#", ".#.....#.", "........."),
        "battery" to arrayOf(".........", "#######..", "#..#..#..", "#..#..##.", "#..#..##.", "#.....##.", "#..#..#..", "#######..", "........."),
        "lock" to arrayOf("..#####..", ".#.....#.", ".#.....#.", "#########", "#########", "####.####", "####.####", "#########", "#########"),
        "film" to arrayOf("#########", "#.#.#.#.#", "#########", "#.......#", "#.......#", "#.......#", "#########", "#.#.#.#.#", "#########"),
        // wind on: double chevron
        "wind" to arrayOf(".........", "#...#....", ".#...#...", "..#...#..", "...#...#.", "..#...#..", ".#...#...", "#...#....", "........."),
        "down" to arrayOf("....#....", "....#....", "....#....", "....#....", "#...#...#", ".#..#..#.", "..#.#.#..", "...###...", "....#....")
    )

    // 4x7 font so two characters fit the 9-dot width
    private val FONT: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf(".##.", "#..#", "#..#", "#..#", "#..#", "#..#", ".##."),
        '1' to arrayOf("..#.", ".##.", "..#.", "..#.", "..#.", "..#.", ".###"),
        '2' to arrayOf(".##.", "#..#", "...#", "..#.", ".#..", "#...", "####"),
        '3' to arrayOf("###.", "...#", "...#", ".##.", "...#", "...#", "###."),
        '4' to arrayOf("#..#", "#..#", "#..#", "####", "...#", "...#", "...#"),
        '5' to arrayOf("####", "#...", "###.", "...#", "...#", "#..#", ".##."),
        '6' to arrayOf(".##.", "#...", "#...", "###.", "#..#", "#..#", ".##."),
        '7' to arrayOf("####", "...#", "...#", "..#.", "..#.", ".#..", ".#.."),
        '8' to arrayOf(".##.", "#..#", "#..#", ".##.", "#..#", "#..#", ".##."),
        '9' to arrayOf(".##.", "#..#", "#..#", ".###", "...#", "...#", ".##."),
        'B' to arrayOf("###.", "#..#", "#..#", "###.", "#..#", "#..#", "###."),
        'W' to arrayOf("#..#", "#..#", "#..#", "#..#", "####", "####", "#..#"),
        'N' to arrayOf("#..#", "##.#", "##.#", "#.##", "#.##", "#..#", "#..#"),
        'O' to arrayOf(".##.", "#..#", "#..#", "#..#", "#..#", "#..#", ".##."),
        'R' to arrayOf("###.", "#..#", "#..#", "###.", "#.#.", "#..#", "#..#"),
        'P' to arrayOf("###.", "#..#", "#..#", "###.", "#...", "#...", "#..."),
        'K' to arrayOf("#..#", "#.#.", "##..", "##..", "#.#.", "#..#", "#..#"),
        'E' to arrayOf("####", "#...", "#...", "###.", "#...", "#...", "####"),
        'L' to arrayOf("#...", "#...", "#...", "#...", "#...", "#...", "####")
    )

    fun icon(name: String): BooleanArray {
        val g = BooleanArray(ROWS * COLS)
        val ic = ICONS[name] ?: return g
        for (r in 0 until ROWS) for (c in 0 until COLS) if (ic[r][c] == '#') g[r * COLS + c] = true
        return g
    }

    /** Up to two characters, right-aligned. */
    fun text(t: String): BooleanArray {
        val g = BooleanArray(ROWS * COLS)
        val s = t.takeLast(2).padStart(2, ' ')
        for ((i, ch) in s.withIndex()) {
            val f = FONT[ch] ?: continue
            for (r in 0 until 7) for (c in 0 until 4) if (f[r][c] == '#') g[(r + 1) * COLS + i * 5 + c] = true
        }
        return g
    }

    fun draw(c: Canvas, r: RectF, grid: BooleanArray?, p: Paint, u: Float) {
        p.shader = null; p.style = Paint.Style.FILL
        p.color = 0xFF2B2A28.toInt(); c.drawRoundRect(r.left - 0.9f * u, r.top - 0.9f * u, r.right + 0.9f * u, r.bottom + 0.9f * u, 1.8f * u, 1.8f * u, p)
        p.color = 0xFF070303.toInt(); c.drawRoundRect(r, 1.1f * u, 1.1f * u, p)
        val pitch = minOf(r.width() / (COLS + 1), r.height() / (ROWS + 1))
        val x0 = r.centerX() - pitch * (COLS - 1) / 2f
        val y0 = r.centerY() - pitch * (ROWS - 1) / 2f
        p.color = 0xFF2C0707.toInt()
        for (row in 0 until ROWS) for (col in 0 until COLS) c.drawCircle(x0 + col * pitch, y0 + row * pitch, pitch * 0.34f, p)
        if (grid != null) {
            p.color = 0x48FF2A1A
            for (row in 0 until ROWS) for (col in 0 until COLS) if (grid[row * COLS + col]) c.drawCircle(x0 + col * pitch, y0 + row * pitch, pitch * 0.8f, p)
            p.color = 0xFFFF4030.toInt()
            for (row in 0 until ROWS) for (col in 0 until COLS) if (grid[row * COLS + col]) c.drawCircle(x0 + col * pitch, y0 + row * pitch, pitch * 0.4f, p)
        }
        // glass sheen
        p.color = 0x14FFFFFF
        c.drawRoundRect(r.left, r.top, r.right, r.top + r.height() * 0.42f, 1.1f * u, 1.1f * u, p)
    }
}
