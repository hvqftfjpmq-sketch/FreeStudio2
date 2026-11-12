package com.freestudio.app.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import com.freestudio.app.editor.ImageProcessor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** ИИ-функции. Всё работает на устройстве, бесплатно, без подписок. */
object AiTools {

    /**
     * Удаление фона нейросетью Google ML Kit (работает на телефоне).
     * Модель один раз скачивается через Google Play Services.
     */
    suspend fun removeBackground(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val options = SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        val segmenter = SubjectSegmentation.getClient(options)
        try {
            val result = segmenter.process(InputImage.fromBitmap(src, 0)).await()
            result.foregroundBitmap ?: error("Объект не найден")
        } finally {
            segmenter.close()
        }
    }

    /** Автоулучшение: растяжка уровней, поправка гаммы, лёгкий подъём цвета. */
    suspend fun autoEnhance(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val hist = IntArray(256)
        var sumLum = 0.0
        for (p in px) {
            val l = (0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)).toInt()
            hist[l]++
            sumLum += l
        }
        val total = px.size
        var acc = 0
        var lo = 0
        var hi = 255
        for (i in 0..255) { acc += hist[i]; if (acc >= total * 0.01) { lo = i; break } }
        acc = 0
        for (i in 255 downTo 0) { acc += hist[i]; if (acc >= total * 0.01) { hi = i; break } }
        if (hi - lo < 20) { lo = 0; hi = 255 }

        val meanStretched = ((sumLum / total - lo) / (hi - lo)).coerceIn(0.05, 0.95)
        val gamma = (kotlin.math.ln(0.5) / kotlin.math.ln(meanStretched)).coerceIn(0.7, 1.4)

        val lut = IntArray(256) { i ->
            val t = ((i - lo).toDouble() / (hi - lo)).coerceIn(0.0, 1.0)
            (t.pow(1.0 / gamma) * 255.0 + 0.5).toInt().coerceIn(0, 255)
        }
        for (i in px.indices) {
            val p = px[i]
            val r = lut[(p shr 16) and 255]
            val g = lut[(p shr 8) and 255]
            val b = lut[p and 255]
            val l = (0.299f * r + 0.587f * g + 0.114f * b)
            val k = 1.12f // небольшой подъём насыщенности
            val rr = (l + (r - l) * k).toInt().coerceIn(0, 255)
            val gg = (l + (g - l) * k).toInt().coerceIn(0, 255)
            val bb = (l + (b - l) * k).toInt().coerceIn(0, 255)
            px[i] = (p and (255 shl 24)) or (rr shl 16) or (gg shl 8) or bb
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        out
    }

    /** Шумоподавление: медианный фильтр 3x3 по каждому каналу. */
    suspend fun denoise(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()
        val rr = IntArray(9); val gg = IntArray(9); val bb = IntArray(9)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var k = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val p = px[(y + dy) * w + (x + dx)]
                    rr[k] = (p shr 16) and 255; gg[k] = (p shr 8) and 255; bb[k] = p and 255; k++
                }
                rr.sort(); gg.sort(); bb.sort()
                val i = y * w + x
                out[i] = (px[i] and (255 shl 24)) or (rr[4] shl 16) or (gg[4] shl 8) or bb[4]
            }
        }
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        res
    }

    /** Увеличение в 2 раза с последующей резкостью. Ограничено 6000 пикселей по большей стороне. */
    suspend fun upscale2x(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val factor = if (max(src.width, src.height) * 2 > 6000) 6000f / max(src.width, src.height) else 2f
        val big = Bitmap.createScaledBitmap(src, (src.width * factor).toInt(), (src.height * factor).toInt(), true)
        ImageProcessor.sharpen(big, 0.7f)
    }

    /**
     * Стирание объекта: закрашенная область восстанавливается из соседних пикселей
     * (послойная заливка от границы внутрь + сглаживание). Хорошо работает на небольших
     * объектах на однородном фоне.
     */
    suspend fun eraseObject(src: Bitmap, mask: BooleanArray): Bitmap = withContext(Dispatchers.Default) {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val unknown = mask.copyOf()
        val original = mask.copyOf()
        var remaining = unknown.count { it }
        var guard = 0
        val fillIdx = IntArray(w * h)
        val fillCol = IntArray(w * h)

        while (remaining > 0 && guard++ < 2000) {
            var n = 0
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (!unknown[i]) continue
                var r = 0; var g = 0; var b = 0; var c = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (unknown[j]) continue
                    val p = px[j]
                    r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; c++
                }
                if (c > 0) {
                    fillIdx[n] = i
                    fillCol[n] = (255 shl 24) or ((r / c) shl 16) or ((g / c) shl 8) or (b / c)
                    n++
                }
            }
            if (n == 0) break
            for (k in 0 until n) { px[fillIdx[k]] = fillCol[k]; unknown[fillIdx[k]] = false }
            remaining -= n
        }

        // Сглаживание закрашенной области, чтобы убрать «лучи».
        repeat(8) {
            val copy = px.copyOf()
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                val i = y * w + x
                if (!original[i]) continue
                var r = 0; var g = 0; var b = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val p = copy[(y + dy) * w + (x + dx)]
                    r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255
                }
                px[i] = (255 shl 24) or ((r / 9) shl 16) or ((g / 9) shl 8) or (b / 9)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        out
    }

    /** Подкладывает цвет под прозрачный фон (после удаления фона). */
    fun fillBackground(fg: Bitmap, color: Int): Bitmap {
        val out = Bitmap.createBitmap(fg.width, fg.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(color)
        c.drawBitmap(fg, 0f, 0f, null)
        return out
    }
}
