package com.winterballs.oneios.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LinearProgressIndicator
import com.winterballs.oneios.logic.ImageDownloader
import com.winterballs.oneios.logic.ImageStager
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winterballs.oneios.logic.OneIosRunner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenLogs: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var status by remember { mutableStateOf("ПРОВЕРКА") }
    var busy by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    // Проверка статуса при старте
    LaunchedEffect(Unit) {
        val r = OneIosRunner.checkStatus()
        status = if (r.ok) "СИСТЕМА ГОТОВА" else "НЕ ГОТОВО"
        logText = r.output
    }

    // Анимация нажатия для главной кнопки
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(120),
        label = "buttonScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("OneiOS", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Карточка устройства
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Samsung Galaxy A56 5G",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "SoC: Exynos 1580 · GPU: Xclipse 540\nKernel: XNU 25.5.0 · Образ: iOS 16.5",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    AssistChip(
                        onClick = { },
                        label = { Text(status) }
                    )
                }
            }

            // Кнопка запуска iOS (главная, с анимацией)
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    scope.launch {
                        val r = OneIosRunner.startSession()
                        logText = r.output
                        status = if (r.ok) "СИСТЕМА ГОТОВА" else "ОШИБКА"
                        busy = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(scale),
                shape = RoundedCornerShape(28.dp),
                enabled = !busy
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Запустить iOS", style = MaterialTheme.typography.titleMedium)
            }

            // Скачать образ
            FilledTonalButton(
                onClick = {
                    if (downloading) return@FilledTonalButton
                    downloading = true
                    progress = 0f
                    scope.launch {
                        var total = 0L
                        var read = 0L
                        ImageDownloader.downloadAll().collectLatest { p ->
                            read = p.bytesRead
                            total = p.totalBytes
                            progress = if (total > 0) read.toFloat() / total else 0f
                            logText = "Скачивание части ${p.part}: ${read / 1024 / 1024} MB" +
                                    (if (total > 0) " / ${total / 1024 / 1024} MB" else "") +
                                    "\n" + (p.error ?: "")
                            if (p.done) {
                                status = "РАСПАКОВКА"
                                val r = ImageStager.stage()
                                logText = r.output
                                status = if (r.ok) "СИСТЕМА ГОТОВА" else "ОШИБКА СТЕЙДЖА"
                                downloading = false
                            }
                            if (p.error != null) {
                                status = "ОШИБКА СКАЧИВАНИЯ"
                                downloading = false
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(24.dp),
                enabled = !downloading && !busy
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (downloading) "Скачивание..." else "Скачать образ iOS 16.5")
            }
            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Кнопка авт��матической установки
            FilledTonalButton(
                onClick = {
                    if (installing) return@FilledTonalButton
                    installing = true
                    scope.launch {
                        val r = OneIosRunner.runInstaller(context)
                        logText = r.output
                        status = if (r.ok) "СИСТЕМА ГОТОВА" else "ОШИБКА УСТАНОВКИ"
                        installing = false
                        // После установки сразу проверяем статус
                        val check = OneIosRunner.checkStatus()
                        if (check.ok) status = "СИСТЕМА ГОТОВА"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(24.dp),
                enabled = !installing && !busy
            ) {
                Icon(Icons.Default.Build, contentDescription = null) // Потребуется импорт Icons.Default.Build
                Spacer(Modifier.width(8.dp))
                Text(if (installing) "Установка..." else "Установить iOS (автоматически)")
            }

            // Вернуться в One UI
            OutlinedButton(
                onClick = {
                    if (busy) return@OutlinedButton
                    busy = true
                    scope.launch {
                        val r = OneIosRunner.stopSession()
                        logText = r.output
                        status = "НЕ ГОТОВО"
                        busy = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Перезагрузка (One UI)")
            }

            // Обновить статус + логи
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            val r = OneIosRunner.checkStatus()
                            status = if (r.ok) "СИСТЕМА ГОТОВА" else "НЕ ГОТОВО"
                            logText = r.output
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Обновить")
                }
                TextButton(onClick = onOpenLogs, modifier = Modifier.weight(1f)) {
                    Text("Логи")
                }
            }

            AnimatedVisibility(visible = logText.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = logText.takeLast(1500),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
