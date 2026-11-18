package com.winterballs.oneios.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winterballs.oneios.logic.ImageDownloader
import com.winterballs.oneios.logic.ImageStager
import com.winterballs.oneios.logic.OneIosRunner
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallScreen(onBack: () -> Unit) {
    var statusText by remember { mutableStateOf("Ожидание...") }
    var downloadProgress by remember { mutableStateOf(0f) }
    var extractProgress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // Автоматически запускаем процесс при открытии экрана
    LaunchedEffect(Unit) {
        if (!isDownloading && !isExtracting && !isDone) {
            isDownloading = true
            statusText = "Скачивание образа..."
            
            // 1. Скачивание
            ImageDownloader.downloadAll().collectLatest { p ->
                if (p.error != null) {
                    errorText = p.error
                    isDownloading = false
                    statusText = "Ошибка скачивания"
                    return@collectLatest
                }
                
                if (p.totalBytes > 0) {
                    downloadProgress = p.bytesRead.toFloat() / p.totalBytes
                }
                statusText = "Скачивание: ${p.bytesRead / 1024 / 1024} MB / ${p.totalBytes / 1024 / 1024} MB"
                
                if (p.done) {
                    isDownloading = false
                    isExtracting = true
                    statusText = "Распаковка и настройка..."
                    
                    // 2. Распаковка и стейджинг
                    val result = ImageStager.stage()
                    if (result.ok) {
                        extractProgress = 1f
                        statusText = "Проверка системы..."
                        
                        // 3. Проверка PREFLIGHT
                        val check = OneIosRunner.checkStatus()
                        if (check.ok) {
                            isDone = true
                            statusText = "Система готова к запуску!"
                        } else {
                            errorText = check.output
                            isExtracting = false
                            statusText = "Ошибка проверки"
                        }
                    } else {
                        errorText = result.output
                        isExtracting = false
                        statusText = "Ошибка распаковки"
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Установка iOS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Прогресс скачивания
            if (isDownloading || downloadProgress > 0) {
                Column {
                    Text("Скачивание образа", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Прогресс распаковки
            if (isExtracting) {
                Column {
                    Text("Распаковка и настройка", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Финальный статус
            if (isDone) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Установка завершена!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Можно запускать iOS.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Ошибка
            if (errorText != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Ошибка",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            errorText ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
