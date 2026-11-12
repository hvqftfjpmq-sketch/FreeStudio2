package com.freestudio.app.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/** Вся обработка фото: на CPU, без интернета и серверов. */
object ImageProcessor {

    fun render(src: Bitmap, adj: Adjustments, filter: Filter): Bitmap {
        var bmp = applyColor(src, adj, filter)
        if (adj.shadows != 0f || adj.highlights != 0f) bmp = applyTone(bmp, adj)
        if (adj.blur > 0f) bmp = boxBlur(bmp, (adj.blur * 14f).toInt().coerceAtLeast(1))
        if (adj.sharpness > 0f) bmp = sharpen(bmp, adj.sharpness * 2f)
        if (adj.vignette > 0f) bmp = vignette(bmp, adj.vignette)
        return bmp
    }

    private fun applyColor(src: Bitmap, adj: Adjustments, filter: Filter): Bitmap {
        val cm = filter.matrix()
        if (adj.saturation != 0f) cm.postConcat(ColorMatrix().apply { setSaturation(1f + adj.saturation) })
        if (adj.contrast != 0f) cm.postConcat(contrastMatrix(1f + adj.contrast * 0.8f))
        if (adj.brightness != 0f) {
            val o = adj.brightness * 90f
            cm.postConcat(
                ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, o,
                    0f, 1f, 0f, 0f, o,
                    0f, 0f, 1f, 0f, o,
                    0f, 0f, 0f, 1f, 0f,
                ))
            )
        }
        if (adj.warmth != 0f || adj.tint != 0f) {
            cm.postConcat(
                ColorMatrix(floatArrayOf(
                    1f + 0.25f * adj.warmth, 0f, 0f, 0f, 0f,
                    0f, 1f - 0.2f * adj.tint, 0f, 0f, 0f,
                    0f, 0f, 1f - 0.25f * adj.warmth, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ))
            )
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** Тени и света (как в Snapseed / Photoshop) через таблицу яркости. */
    private fun applyTone(src: Bitmap, adj: Adjustments): Bitmap {
        val lut = IntArray(256) { i ->
            val t = i / 255f
            val shadowW = (1f - t) * (1f - t)
            val highW = t * t * (1f - t) * 2f
            val v = t + 0.30f * adj.shadows * shadowW * t * 2f + 0.30f * adj.highlights * highW
            (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            val r = lut[(p shr 16) and 255]
            val g = lut[(p shr 8) and 255]
            val b = lut[p and 255]
            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        var px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        repeat(2) {
            px = blurPass(px, w, h, radius, horizontal = true)
            px = blurPass(px, w, h, radius, horizontal = false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun blurPass(src: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean): IntArray {
        val dst = IntArray(src.size)
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            fun idx(k: Int) = if (horizontal) line * w + k else k * w + line
            for (k in -r..r) {
                val p = src[idx(k.coerceIn(0, len - 1))]
                sa += p ushr 24; sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255
            }
            val n = 2 * r + 1
            for (k in 0 until len) {
                dst[idx(k)] = ((sa / n) shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                val add = src[idx(min(k + r + 1, len - 1))]
                val rem = src[idx(max(k - r, 0))]
                sa += (add ushr 24) - (rem ushr 24)
                sr += ((add shr 16) and 255) - ((rem shr 16) and 255)
                sg += ((add shr 8) and 255) - ((rem shr 8) and 255)
                sb += (add and 255) - (rem and 255)
            }
        }
        return dst
    }

    /** Нерезкая маска (unsharp mask) — «резкость» как в Snapseed. */
    fun sharpen(src: Bitmap, amount: Float): Bitmap {
        val blurred = boxBlur(src, 2)
        val w = src.width
        val h = src.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        blurred.getPixels(b, 0, w, 0, 0, w, h)
        for (i in a.indices) {
            val p = a[i]
            val q = b[i]
            val r = ((p shr 16) and 255) + (amount * (((p shr 16) and 255) - ((q shr 16) and 255))).toInt()
            val g = ((p shr 8) and 255) + (amount * (((p shr 8) and 255) - ((q shr 8) and 255))).toInt()
            val bl = (p and 255) + (amount * ((p and 255) - (q and 255))).toInt()
            a[i] = (p and (255 shl 24)) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or bl.coerceIn(0, 255)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    private fun vignette(src: Bitmap, strength: Float): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val cx = out.width / 2f
        val cy = out.height / 2f
        val radius = kotlin.math.hypot(cx, cy)
        val alpha = (strength * 230f).toInt().coerceIn(0, 255)
        val paint = Paint().apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(0x00000000, 0x00000000, alpha shl 24),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(out).drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        return out
    }

    // ---------- Геометрия ----------

    fun rotate90(src: Bitmap): Bitmap {
        val m = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun flip(src: Bitmap, horizontal: Boolean): Bitmap {
        val m = Matrix().apply { if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** Обрезка по центру под нужное соотношение сторон. */
    fun cropToRatio(src: Bitmap, ratioW: Int, ratioH: Int): Bitmap {
        val target = ratioW.toFloat() / ratioH
        val cur = src.width.toFloat() / src.height
        var w = src.width
        var h = src.height
        if (cur > target) w = (src.height * target).toInt() else h = (src.width / target).toInt()
        val x = (src.width - w) / 2
        val y = (src.height - h) / 2
        return Bitmap.createBitmap(src, x, y, w, h)
    }

    fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val m = max(src.width, src.height)
        if (m <= maxSide) return src
        val s = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true)
    }
}
