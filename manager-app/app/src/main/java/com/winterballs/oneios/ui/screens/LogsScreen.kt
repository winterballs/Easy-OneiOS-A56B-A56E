package com.winterballs.oneios.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    var logLines by remember { mutableStateOf(listOf<String>()) }
    val logFile = File("/data/local/tmp/oneios/session/xnuhost.log")

    // Читаем лог сразу при открытии
    LaunchedEffect(Unit) {
        try {
            if (logFile.exists()) {
                logLines = logFile.readLines().takeLast(200)
            } else {
                logLines = listOf("(лог прошлой сессии не найден)")
            }
        } catch (e: Exception) {
            logLines = listOf("Ошибка чтения лога: ${e.message}")
        }
    }

    // И продолжаем читать каждые 2 секунды (если сессия запущена)
    LaunchedEffect(Unit) {
        while (true) {
            try {
                if (logFile.exists()) {
                    // Читаем последние 200 строк (чтобы не грузить память)
                    val allLines = logFile.readLines()
                    logLines = allLines.takeLast(200)
                } else {
                    logLines = listOf("(лог ещё не создан. Запустите iOS.)")
                }
            } catch (e: Exception) {
                logLines = listOf("Ошибка чтения лога: ${e.message}")
            }
            delay(2000) // Обновление раз в 2 секунды
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Логи сессии") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            items(logLines) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}
