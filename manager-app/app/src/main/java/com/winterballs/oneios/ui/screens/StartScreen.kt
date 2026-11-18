package com.winterballs.oneios.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winterballs.oneios.logic.OneIosRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(onBack: () -> Unit) {
    var logLines by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Запуск iOS...") }
    val scope = rememberCoroutineScope()
    val logFile = File("/data/local/tmp/oneios/session/xnuhost.log")

    // Автоматически запускаем сессию при открытии экрана
    LaunchedEffect(Unit) {
        if (!isRunning) {
            isRunning = true
            statusText = "Запуск сессии..."
            
            // Запускаем xnuhost (в фоне)
            val result = OneIosRunner.startSession()
            if (!result.ok) {
                logLines = listOf("Ошибка запуска: ${result.output}")
                statusText = "Ошибка"
                isRunning = false
            }
        }
    }

    // Читаем лог сразу при открытии (чтобы увидеть прошлую сессию)
    LaunchedEffect(Unit) {
        try {
            if (logFile.exists()) {
                val allLines = logFile.readLines()
                logLines = allLines.takeLast(300)
            } else {
                logLines = listOf("(лог прошлой сессии не найден)")
            }
        } catch (e: Exception) {
            logLines = listOf("Ошибка чтения лога: ${e.message}")
        }
    }

    // Читаем лог каждые 1.5 секунды, пока запущено
    LaunchedEffect(isRunning) {
        while (isRunning) {
            try {
                if (logFile.exists()) {
                    val allLines = logFile.readLines()
                    logLines = allLines.takeLast(300) // последние 300 строк
                } else {
                    logLines = listOf("(ожидание лога...)")
                }
            } catch (e: Exception) {
                logLines = listOf("Ошибка чтения лога: ${e.message}")
            }
            delay(1500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Запуск iOS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            // Кнопка Остановить внизу
            Button(
                onClick = {
                    scope.launch {
                        statusText = "Остановка..."
                        OneIosRunner.stopSession()
                        isRunning = false
                        statusText = "Сессия остановлена"
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Остановить iOS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            // Статус
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Лог
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                items(logLines) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
