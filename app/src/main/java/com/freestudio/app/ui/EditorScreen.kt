package com.freestudio.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.freestudio.app.Storage
import com.freestudio.app.ai.AiTools
import com.freestudio.app.editor.Adjustments
import com.freestudio.app.editor.Filter
import com.freestudio.app.editor.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

private enum class EditorTab(val title: String) {
    FILTERS("Фильтры"), ADJUST("Коррекция"), CROP("Обрезка"), DRAW("Кисть"), TEXT("Текст"), AI("ИИ")
}

private class StrokeData(val points: SnapshotStateList<Offset>, val color: Color, val width: Float)

private val PALETTE = listOf(
    Color.White, Color.Black, Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00),
    Color(0xFF34C759), Color(0xFF00C7BE), Color(0xFF0A84FF), Color(0xFFAF52DE), Color(0xFFFF2D92),
)

@Composable
fun EditorScreen(uri: Uri, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var base by remember { mutableStateOf<Bitmap?>(null) }
    val undo = remember { mutableStateListOf<Bitmap>() }
    var adj by remember { mutableStateOf(Adjustments()) }
    var filter by remember { mutableStateOf(Filter.ORIGINAL) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var tab by remember { mutableStateOf(EditorTab.FILTERS) }
    var busy by remember { mutableStateOf(false) }
    var transparent by remember { mutableStateOf(false) }

    // Кисть / стирание объектов / текст
    val strokes = remember { mutableStateListOf<StrokeData>() }
    var current by remember { mutableStateOf<StrokeData?>(null) }
    var brushColor by remember { mutableStateOf(Color.White) }
    var brushSize by remember { mutableFloatStateOf(14f) }
    var eraseMode by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf("Привет!") }
    var textColor by remember { mutableStateOf(Color.White) }
    var textSize by remember { mutableFloatStateOf(8f) }
    var textPos by remember { mutableStateOf<Offset?>(null) }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    LaunchedEffect(uri) {
        base = withContext(Dispatchers.IO) { Storage.loadBitmap(ctx, uri, 4096) }
    }
    LaunchedEffect(base, adj, filter) {
        val b = base ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) {
            ImageProcessor.render(ImageProcessor.scaleDown(b, 1280), adj, filter)
        }
    }

    fun commit(newBase: Bitmap) {
        base?.let { undo.add(it); if (undo.size > 8) undo.removeAt(0) }
        base = newBase
        strokes.clear(); current = null; textPos = null
    }

    fun runAi(label: String, block: suspend (Bitmap) -> Bitmap) {
        val b = base ?: return
        scope.launch {
            busy = true
            try { commit(block(b)); toast("Готово: $label") }
            catch (e: Exception) { toast("Не получилось: ${e.message}") }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        // Верхняя панель
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = undo.isNotEmpty() && !busy,
                onClick = { base = undo.removeAt(undo.lastIndex); strokes.clear(); transparent = false },
            ) { Text("↶ Отмена") }
            Button(
                enabled = base != null && !busy,
                onClick = {
                    val b = base ?: return@Button
                    scope.launch {
                        busy = true
                        val ok = withContext(Dispatchers.Default) {
                            val final = ImageProcessor.render(b, adj, filter)
                            Storage.saveImage(ctx, final, png = transparent)
                        }
                        toast(if (ok != null) "Сохранено в Галерею → FreeStudio" else "Ошибка сохранения")
                        busy = false
                    }
                },
            ) { Text("Сохранить") }
        }

        // Холст
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            val b = base
            val p = preview
            if (b == null || p == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val scale = min(w / b.width, h / b.height)
                val dx = (w - b.width * scale) / 2f
                val dy = (h - b.height * scale) / 2f
                val drawing = tab == EditorTab.DRAW || (tab == EditorTab.AI && eraseMode)

                Image(
                    bitmap = p.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                ComposeCanvas(
                    Modifier.fillMaxSize()
                        .pointerInput(tab, eraseMode, brushColor, brushSize) {
                            if (drawing) {
                                detectDragGestures(
                                    onDragStart = { o ->
                                        val col = if (eraseMode) Color(0x99FF3B30) else brushColor
                                        current = StrokeData(mutableStateListOf(o), col, brushSize * (if (eraseMode) 1.6f else 1f))
                                    },
                                    onDrag = { ch, _ -> current?.points?.add(ch.position) },
                                    onDragEnd = { current?.let { strokes.add(it) }; current = null },
                                )
                            }
                        }
                        .pointerInput(tab) {
                            if (tab == EditorTab.TEXT) detectTapGestures { textPos = it }
                        }
                ) {
                    for (s in strokes + listOfNotNull(current)) {
                        val path = androidx.compose.ui.graphics.Path()
                        s.points.forEachIndexed { i, o ->
                            if (i == 0) { path.moveTo(o.x, o.y); path.lineTo(o.x + 0.1f, o.y) } else path.lineTo(o.x, o.y)
                        }
                        drawPath(path, s.color, style = DrawStroke(s.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    textPos?.let { pos ->
                        val px = textSize * b.width / 20f * scale
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = textColor.toArgb(); this.textSize = px
                            textAlign = Paint.Align.CENTER; isFakeBoldText = true
                            setShadowLayer(6f, 0f, 2f, 0xAA000000.toInt())
                        }
                        drawContext.canvas.nativeCanvas.drawText(textValue, pos.x, pos.y, paint)
                    }
                }

                // Кнопки подтверждения для кисти / текста
                if (tab == EditorTab.DRAW && strokes.isNotEmpty()) {
                    Button(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        onClick = {
                            commit(drawStrokes(b, strokes, scale, dx, dy, brushColorOverride = null))
                        },
                    ) { Text("Применить рисунок") }
                }
                if (tab == EditorTab.TEXT && textPos != null) {
                    Button(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        onClick = {
                            val pos = textPos ?: return@Button
                            commit(drawText(b, textValue, textColor, textSize, (pos.x - dx) / scale, (pos.y - dy) / scale))
                        },
                    ) { Text("Применить текст") }
                }
                if (tab == EditorTab.AI && eraseMode && strokes.isNotEmpty()) {
                    Button(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        onClick = {
                            val mask = strokesToMask(b, strokes, scale, dx, dy)
                            eraseMode = false
                            runAi("объект стёрт") { AiTools.eraseObject(it, mask) }
                        },
                    ) { Text("Стереть выделенное") }
                }
            }
            if (busy) {
                Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // Вкладки
        ScrollableTabRow(
            selectedTabIndex = tab.ordinal,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            EditorTab.entries.forEach { t ->
                Tab(selected = tab == t, onClick = { tab = t; eraseMode = false; strokes.clear() }, text = { Text(t.title) })
            }
        }

        // Панель инструментов
        Box(Modifier.fillMaxWidth().heightIn(min = 130.dp, max = 260.dp).background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
            when (tab) {
                EditorTab.FILTERS -> Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Filter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.title) })
                    }
                }

                EditorTab.ADJUST -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    AdjSlider("Яркость", adj.brightness, -1f..1f) { adj = adj.copy(brightness = it) }
                    AdjSlider("Контраст", adj.contrast, -1f..1f) { adj = adj.copy(contrast = it) }
                    AdjSlider("Насыщенность", adj.saturation, -1f..1f) { adj = adj.copy(saturation = it) }
                    AdjSlider("Тепло", adj.warmth, -1f..1f) { adj = adj.copy(warmth = it) }
                    AdjSlider("Оттенок", adj.tint, -1f..1f) { adj = adj.copy(tint = it) }
                    AdjSlider("Тени", adj.shadows, -1f..1f) { adj = adj.copy(shadows = it) }
                    AdjSlider("Света", adj.highlights, -1f..1f) { adj = adj.copy(highlights = it) }
                    AdjSlider("Резкость", adj.sharpness, 0f..1f) { adj = adj.copy(sharpness = it) }
                    AdjSlider("Размытие", adj.blur, 0f..1f) { adj = adj.copy(blur = it) }
                    AdjSlider("Виньетка", adj.vignette, 0f..1f) { adj = adj.copy(vignette = it) }
                    TextButton(onClick = { adj = Adjustments(); filter = Filter.ORIGINAL }) { Text("Сбросить всё") }
                }

                EditorTab.CROP -> Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1:1" to (1 to 1), "4:5" to (4 to 5), "3:4" to (3 to 4), "16:9" to (16 to 9), "9:16" to (9 to 16), "3:2" to (3 to 2)).forEach { (name, r) ->
                            AssistChip(onClick = { base?.let { commit(ImageProcessor.cropToRatio(it, r.first, r.second)) } }, label = { Text(name) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { base?.let { commit(ImageProcessor.rotate90(it)) } }, label = { Text("Повернуть ⟳") })
                        AssistChip(onClick = { base?.let { commit(ImageProcessor.flip(it, true)) } }, label = { Text("Отразить ⇆") })
                        AssistChip(onClick = { base?.let { commit(ImageProcessor.flip(it, false)) } }, label = { Text("Отразить ⇅") })
                    }
                    Text("Обрезка делается по центру. Рисуйте кистью и добавляйте текст в соседних вкладках.", style = MaterialTheme.typography.bodySmall)
                }

                EditorTab.DRAW -> Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorRow(brushColor) { brushColor = it }
                    AdjSlider("Толщина", brushSize, 4f..60f) { brushSize = it }
                    Text("Рисуйте пальцем по фото, затем нажмите «Применить рисунок».", style = MaterialTheme.typography.bodySmall)
                }

                EditorTab.TEXT -> Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = textValue, onValueChange = { textValue = it }, label = { Text("Надпись") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ColorRow(textColor) { textColor = it }
                    AdjSlider("Размер", textSize, 3f..20f) { textSize = it }
                    Text("Нажмите на фото, чтобы поставить текст, потом «Применить текст».", style = MaterialTheme.typography.bodySmall)
                }

                EditorTab.AI -> Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { runAi("фон удалён") { b -> AiTools.removeBackground(b).also { transparent = true } } }, label = { Text("✂ Удалить фон") })
                        AssistChip(onClick = { runAi("автоулучшение") { AiTools.autoEnhance(it) } }, label = { Text("✨ Автоулучшение") })
                        AssistChip(onClick = { runAi("шум убран") { AiTools.denoise(it) } }, label = { Text("Убрать шум") })
                        AssistChip(onClick = { runAi("увеличено ×2") { AiTools.upscale2x(it) } }, label = { Text("Увеличить ×2") })
                        AssistChip(
                            onClick = { eraseMode = true; strokes.clear() },
                            label = { Text(if (eraseMode) "Закрасьте объект…" else "🧽 Стереть объект") },
                        )
                    }
                    if (transparent) {
                        Text("Новый фон:", style = MaterialTheme.typography.bodySmall)
                        ColorRow(Color.White) { c ->
                            base?.let { commit(AiTools.fillBackground(it, c.toArgb())); transparent = false }
                        }
                    }
                    Text(
                        "Удаление фона использует нейросеть на устройстве (модель один раз загрузится через Google Play Services). Остальные функции работают полностью офлайн.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ColorRow(selected: Color, onPick: (Color) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PALETTE.forEach { c ->
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c)
                    .then(if (c == selected) Modifier.background(Color.Transparent) else Modifier)
                    .clickable { onPick(c) },
            ) {
                if (c == selected) Box(Modifier.align(Alignment.Center).size(12.dp).clip(CircleShape).background(if (c == Color.Black) Color.White else Color.Black))
            }
        }
    }
}

// ---------- Перенос штрихов и текста на настоящий битмап ----------

private fun drawStrokes(
    src: Bitmap, strokes: List<StrokeData>, scale: Float, dx: Float, dy: Float, brushColorOverride: Int?,
): Bitmap {
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    for (s in strokes) {
        val path = Path()
        s.points.forEachIndexed { i, o ->
            val x = (o.x - dx) / scale
            val y = (o.y - dy) / scale
            if (i == 0) { path.moveTo(x, y); path.lineTo(x + 0.1f, y) } else path.lineTo(x, y)
        }
        paint.color = brushColorOverride ?: s.color.toArgb()
        paint.strokeWidth = s.width / scale
        canvas.drawPath(path, paint)
    }
    return out
}

private fun strokesToMask(src: Bitmap, strokes: List<StrokeData>, scale: Float, dx: Float, dy: Float): BooleanArray {
    val maskBmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val drawn = drawStrokes(maskBmp, strokes, scale, dx, dy, brushColorOverride = 0xFFFFFFFF.toInt())
    val px = IntArray(drawn.width * drawn.height)
    drawn.getPixels(px, 0, drawn.width, 0, 0, drawn.width, drawn.height)
    return BooleanArray(px.size) { (px[it] ushr 24) > 40 }
}

private fun drawText(src: Bitmap, text: String, color: Color, sizeUnits: Float, x: Float, y: Float): Bitmap {
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = sizeUnits * src.width / 20f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(6f, 0f, 2f, 0xAA000000.toInt())
    }
    Canvas(out).drawText(text, x, y, paint)
    return out
}
