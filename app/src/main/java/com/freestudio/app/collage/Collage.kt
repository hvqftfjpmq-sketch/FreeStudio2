package com.freestudio.app.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Шаблон коллажа: ячейки в долях 0..1 (left, top, right, bottom). */
class CollageLayout(val name: String, val cells: List<RectF>)

private fun r(l: Float, t: Float, rr: Float, b: Float) = RectF(l, t, rr, b)

private fun grid(rows: Int, cols: Int, count: Int): List<RectF> {
    val cells = ArrayList<RectF>()
    for (y in 0 until rows) for (x in 0 until cols) {
        if (cells.size < count) cells.add(r(x / cols.toFloat(), y / rows.toFloat(), (x + 1) / cols.toFloat(), (y + 1) / rows.toFloat()))
    }
    return cells
}

object CollageLayouts {
    fun forCount(n: Int): List<CollageLayout> = when (n) {
        1 -> listOf(CollageLayout("Одно фото", listOf(r(0f, 0f, 1f, 1f))))
        2 -> listOf(
            CollageLayout("Рядом", grid(1, 2, 2)),
            CollageLayout("Друг над другом", grid(2, 1, 2)),
            CollageLayout("1/3 + 2/3", listOf(r(0f, 0f, .35f, 1f), r(.35f, 0f, 1f, 1f))),
        )
        3 -> listOf(
            CollageLayout("Большое слева", listOf(r(0f, 0f, .6f, 1f), r(.6f, 0f, 1f, .5f), r(.6f, .5f, 1f, 1f))),
            CollageLayout("Большое сверху", listOf(r(0f, 0f, 1f, .6f), r(0f, .6f, .5f, 1f), r(.5f, .6f, 1f, 1f))),
            CollageLayout("Три колонки", grid(1, 3, 3)),
            CollageLayout("Три строки", grid(3, 1, 3)),
        )
        4 -> listOf(
            CollageLayout("Сетка 2×2", grid(2, 2, 4)),
            CollageLayout("Большое + полоса", listOf(r(0f, 0f, 1f, .65f), r(0f, .65f, 1f / 3, 1f), r(1f / 3, .65f, 2f / 3, 1f), r(2f / 3, .65f, 1f, 1f))),
            CollageLayout("Четыре колонки", grid(1, 4, 4)),
        )
        5 -> listOf(
            CollageLayout("2 + 3", listOf(r(0f, 0f, .5f, .5f), r(.5f, 0f, 1f, .5f), r(0f, .5f, 1f / 3, 1f), r(1f / 3, .5f, 2f / 3, 1f), r(2f / 3, .5f, 1f, 1f))),
            CollageLayout("Большое слева + 4", listOf(r(0f, 0f, .5f, 1f), r(.5f, 0f, .75f, .5f), r(.75f, 0f, 1f, .5f), r(.5f, .5f, .75f, 1f), r(.75f, .5f, 1f, 1f))),
        )
        6 -> listOf(
            CollageLayout("3×2", grid(2, 3, 6)),
            CollageLayout("2×3", grid(3, 2, 6)),
        )
        7, 8, 9 -> listOf(CollageLayout("Сетка 3×3", grid(3, 3, n)))
        else -> emptyList()
    }
}

object CollageRenderer {
    fun render(
        images: List<Bitmap>,
        layout: CollageLayout,
        outSide: Int,
        spacing: Float,      // 0..1
        corner: Float,       // 0..1
        background: Int,
    ): Bitmap {
        val out = Bitmap.createBitmap(outSide, outSide, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(background)
        val gap = spacing * outSide * 0.05f
        val radius = corner * outSide * 0.06f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        layout.cells.forEachIndexed { i, cell ->
            val img = images.getOrNull(i) ?: return@forEachIndexed
            val rect = RectF(
                cell.left * outSide + gap, cell.top * outSide + gap,
                cell.right * outSide - gap, cell.bottom * outSide - gap,
            )
            canvas.save()
            val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
            canvas.clipPath(clip)
            // Центральная обрезка фото под ячейку (как в GridArt)
            val scale = maxOf(rect.width() / img.width, rect.height() / img.height)
            val w = img.width * scale
            val h = img.height * scale
            val dst = RectF(rect.centerX() - w / 2, rect.centerY() - h / 2, rect.centerX() + w / 2, rect.centerY() + h / 2)
            canvas.drawBitmap(img, null, dst, paint)
            canvas.restore()
        }
        return out
    }
}
