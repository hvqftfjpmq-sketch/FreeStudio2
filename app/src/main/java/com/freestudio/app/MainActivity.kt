package com.freestudio.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freestudio.app.ui.CollageScreen
import com.freestudio.app.ui.EditorScreen
import com.freestudio.app.ui.FreeStudioTheme
import com.freestudio.app.ui.VideoScreen

private sealed interface Screen {
    data object Home : Screen
    data class Editor(val uri: Uri) : Screen
    data object Collage : Screen
    data object Video : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreeStudioTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) screen = Screen.Editor(uri)
                }
                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

                when (val s = screen) {
                    Screen.Home -> HomeScreen(
                        onPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onCollage = { screen = Screen.Collage },
                        onVideo = { screen = Screen.Video },
                    )
                    is Screen.Editor -> EditorScreen(s.uri) { screen = Screen.Home }
                    Screen.Collage -> CollageScreen { screen = Screen.Home }
                    Screen.Video -> VideoScreen { screen = Screen.Home }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onPhoto: () -> Unit, onCollage: () -> Unit, onVideo: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Free Studio", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "Фото, коллажи, видео и ИИ — полностью бесплатно. Без рекламы, без подписок, без водяных знаков.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        HomeCard("🖼  Фоторедактор", "Фильтры, кривые тона, обрезка, кисть, текст", onPhoto)
        HomeCard("✨  ИИ-инструменты", "Удаление фона, автоулучшение, стирание объектов", onPhoto)
        HomeCard("🧩  Коллажи", "Сетки на 1–9 фото, отступы, скругления", onCollage)
        HomeCard("🎬  Видео", "Обрезка, скорость, фильтры, без звука", onVideo)
    }
}

@Composable
private fun HomeCard(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
}
