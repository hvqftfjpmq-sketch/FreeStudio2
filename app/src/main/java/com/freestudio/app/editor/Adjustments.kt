package com.freestudio.app.editor

import android.graphics.ColorMatrix

/** Все значения в диапазоне -1..1, кроме sharpness/blur/vignette (0..1). */
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val sharpness: Float = 0f,
    val blur: Float = 0f,
    val vignette: Float = 0f,
)

/** Готовые фильтры в стиле Snapseed / InShot / PicsArt. */
enum class Filter(val title: String, val matrix: () -> ColorMatrix) {
    ORIGINAL("Оригинал", { ColorMatrix() }),
    BW("Ч/Б", { ColorMatrix().apply { setSaturation(0f) } }),
    SEPIA("Сепия", {
        ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }),
    VINTAGE("Винтаж", {
        ColorMatrix(
            floatArrayOf(
                0.90f, 0.10f, 0.00f, 0f, 12f,
                0.10f, 0.80f, 0.10f, 0f, 6f,
                0.00f, 0.10f, 0.70f, 0f, 22f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }),
    COOL("Холод", {
        ColorMatrix(
            floatArrayOf(
                0.92f, 0f, 0f, 0f, 0f,
                0f, 1.00f, 0f, 0f, 4f,
                0f, 0f, 1.18f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }),
    WARM("Тепло", {
        ColorMatrix(
            floatArrayOf(
                1.15f, 0f, 0f, 0f, 10f,
                0f, 1.02f, 0f, 0f, 3f,
                0f, 0f, 0.88f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }),
    DRAMA("Драма", {
        ColorMatrix().apply {
            setSaturation(0.75f)
            postConcat(contrastMatrix(1.35f))
        }
    }),
    VIVID("Яркий", { ColorMatrix().apply { setSaturation(1.55f); postConcat(contrastMatrix(1.08f)) } }),
    FADE("Фейд", {
        ColorMatrix().apply {
            setSaturation(0.85f)
            postConcat(contrastMatrix(0.85f))
            postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 18f,
                0f, 1f, 0f, 0f, 18f,
                0f, 0f, 1f, 0f, 18f,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
    }),
    NOIR("Нуар", { ColorMatrix().apply { setSaturation(0f); postConcat(contrastMatrix(1.6f)) } }),
}

fun contrastMatrix(c: Float): ColorMatrix {
    val t = 128f * (1f - c)
    return ColorMatrix(
        floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    )
}
