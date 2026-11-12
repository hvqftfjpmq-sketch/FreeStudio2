package com.freestudio.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.freestudio.app.Storage
import com.freestudio.app.collage.CollageLayouts
import com.freestudio.app.collage.CollageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CollageScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var images by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var layoutIndex by remember { mutableIntStateOf(0) }
    var spacing by remember { mutableFloatStateOf(0.4f) }
    var corner by remember { mutableFloatStateOf(0.3f) }
    var bg by remember { mutableStateOf(Color.White) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris: List<Uri> ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            images = withContext(Dispatchers.IO) { uris.map { Storage.loadBitmap(ctx, it, 2048) } }
            layoutIndex = 0
            busy = false
        }
    }
    LaunchedEffect(Unit) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val layouts = remember(images.size) { CollageLayouts.forCount(images.size) }
    LaunchedEffect(images, layoutIndex, spacing, corner, bg) {
        val l = layouts.getOrNull(layoutIndex) ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) {
            CollageRenderer.render(images, l, 900, spacing, corner, bg.toArgb())
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Выбрать фото") }
            Button(
                enabled = preview != null && !busy,
                onClick = {
                    val l = layouts.getOrNull(layoutIndex) ?: return@Button
                    scope.launch {
                        busy = true
                        val ok = withContext(Dispatchers.Default) {
                            Storage.saveImage(ctx, CollageRenderer.render(images, l, 2160, spacing, corner, bg.toArgb()), png = false)
                        }
                        Toast.makeText(ctx, if (ok != null) "Коллаж сохранён" else "Ошибка", Toast.LENGTH_SHORT).show()
                        busy = false
                    }
                },
            ) { Text("Сохранить") }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            val p = preview
            if (p != null) Image(p.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Text("Выберите от 1 до 9 фото")
            if (busy) CircularProgressIndicator()
        }

        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                layouts.forEachIndexed { i, l ->
                    FilterChip(selected = i == layoutIndex, onClick = { layoutIndex = i }, label = { Text(l.name) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Отступы", Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
                Slider(spacing, { spacing = it }, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Углы", Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
                Slider(corner, { corner = it }, Modifier.weight(1f))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Color.White, Color.Black, Color(0xFFFFE4E1), Color(0xFFD6E6FF), Color(0xFFFFF3C4), Color(0xFFD9F2E0), Color(0xFF2B2B3A)).forEach { c ->
                    Box(Modifier.size(34.dp).clip(CircleShape).background(c).clickable { bg = c })
                }
            }
        }
    }
}
