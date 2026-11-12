package com.freestudio.app.ui

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.freestudio.app.Storage
import java.io.File

private enum class VideoFilter(val title: String) { NONE("Без фильтра"), BW("Ч/Б"), VIVID("Яркий"), DRAMA("Драма") }

@OptIn(UnstableApi::class)
@Composable
@androidx.compose.material3.ExperimentalMaterial3Api
fun VideoScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var range by remember { mutableStateOf(0f..1f) }
    var speed by remember { mutableFloatStateOf(1f) }
    var mute by remember { mutableStateOf(false) }
    var vf by remember { mutableStateOf(VideoFilter.NONE) }
    var exporting by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u: Uri? ->
        if (u != null) {
            uri = u
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(ctx, u)
            durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            mmr.release()
            range = 0f..1f
        }
    }
    LaunchedEffect(Unit) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }

    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(uri) {
        uri?.let { player.setMediaItem(MediaItem.fromUri(it)); player.prepare(); player.playWhenReady = false }
    }

    fun export() {
        val u = uri ?: return
        val startMs = (range.start * durationMs).toLong()
        val endMs = (range.endInclusive * durationMs).toLong()
        val item = MediaItem.Builder()
            .setUri(u)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()
            ).build()

        val videoEffects = mutableListOf<Effect>()
        when (vf) {
            VideoFilter.NONE -> {}
            VideoFilter.BW -> videoEffects.add(RgbFilter.createGrayscaleFilter())
            VideoFilter.VIVID -> videoEffects.add(HslAdjustment.Builder().adjustSaturation(35f).build())
            VideoFilter.DRAMA -> { videoEffects.add(Contrast(0.35f)); videoEffects.add(HslAdjustment.Builder().adjustSaturation(-15f).build()) }
        }
        val audioProcessors = mutableListOf<AudioProcessor>()
        if (speed != 1f) {
            val s = speed
            videoEffects.add(SpeedChangeEffect(s))
            audioProcessors.add(SpeedChangingAudioProcessor(object : SpeedProvider {
                override fun getSpeed(timeUs: Long) = s
                override fun getNextSpeedChangeTimeUs(timeUs: Long) = C.TIME_UNSET
            }))
        }
        val edited = EditedMediaItem.Builder(item)
            .setRemoveAudio(mute)
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()

        val outFile = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        exporting = true
        Transformer.Builder(ctx)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    val saved = Storage.saveVideo(ctx, outFile)
                    outFile.delete()
                    exporting = false
                    Toast.makeText(ctx, if (saved != null) "Видео сохранено в Галерею → FreeStudio" else "Ошибка сохранения", Toast.LENGTH_LONG).show()
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    exporting = false
                    Toast.makeText(ctx, "Ошибка экспорта: ${exportException.errorCodeName}", Toast.LENGTH_LONG).show()
                }
            })
            .build()
            .start(edited, outFile.absolutePath)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) { Text("Выбрать видео") }
            Button(enabled = uri != null && !exporting, onClick = { export() }) { Text("Экспорт") }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            if (uri != null) {
                AndroidView(
                    factory = { c -> PlayerView(c).apply { this.player = player } },
                    modifier = Modifier.fillMaxSize(),
                )
            } else Text("Выберите видео")
            if (exporting) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x88000000)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Экспорт…", Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (durationMs > 0) {
                Text(
                    "Обрезка: ${fmt((range.start * durationMs).toLong())} — ${fmt((range.endInclusive * durationMs).toLong())}",
                    style = MaterialTheme.typography.bodySmall,
                )
                RangeSlider(value = range, onValueChange = { range = it })
            }
            Text("Скорость", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 1f, 1.5f, 2f, 3f).forEach { s ->
                    FilterChip(selected = speed == s, onClick = { speed = s }, label = { Text("${s}×") })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoFilter.entries.forEach { f ->
                    FilterChip(selected = vf == f, onClick = { vf = f }, label = { Text(f.title) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = mute, onCheckedChange = { mute = it })
                Text("  Убрать звук", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
