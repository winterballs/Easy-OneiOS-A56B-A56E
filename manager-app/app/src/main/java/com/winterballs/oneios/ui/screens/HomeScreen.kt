package com.winterballs.oneios.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.winterballs.oneios.logic.OneIosRunner
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenLogs: () -> Unit, onOpenInstall: () -> Unit, onOpenRun: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by rememberSaveable { mutableStateOf("ПРОВЕРКА") }
    var busy by remember { mutableStateOf(false) }
    var logText by rememberSaveable { mutableStateOf("") }
    
    val context = LocalContext.current
    var freeSpaceOk by remember { mutableStateOf(true) }
    var batteryOk by remember { mutableStateOf(true) }
    var freeSpaceGb by remember { mutableStateOf(0.0) }
    var batteryPctValue by remember { mutableStateOf(0) }
    
    // Функция проверки свободного места (нужно хотя бы 1 ГБ)
    fun checkFreeSpace(): Boolean {
        return try {
            val stat = StatFs("/data/local/tmp/oneios")
            val freeBytes = stat.availableBytes
            val requiredBytes = 1024L * 1024L * 1024L // 1 ГБ
            freeSpaceGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
            freeBytes >= requiredBytes
        } catch (e: Exception) {
            true // Если не можем проверить, не блокируем
        }
    }
    
    // Функция проверки батареи (должно быть больше 30%)
    fun checkBattery(): Boolean {
        return try {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (scale > 0) (level * 100 / scale) else 100
            batteryPct > 30
        } catch (e: Exception) {
            true
        }
    }
    
    // Функция проверки блокировки экрана (должна быть отключена)
    
    // Запускаем проверки при старте
    LaunchedEffect(Unit) {
        freeSpaceOk = checkFreeSpace()
        batteryOk = checkBattery()
        batteryPctValue = (context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { b -> val l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1); if (sc > 0) l * 100 / sc else 0 }) ?: 0
    }

    // Проверка статуса при старте (только если лог ещё пустой)
    LaunchedEffect(Unit) {
        if (logText.isEmpty()) {
            val r = OneIosRunner.checkStatus()
            status = if (r.ok) "СИСТЕМА ГОТОВА" else "НЕ ГОТОВО"
            logText = r.output
        }
    }

    // Анимация нажатия для главной кнопки
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(120),
        label = "buttonScale"
    )

    var showAbout by remember { mutableStateOf(false) }
    var showRebootConfirm by remember { mutableStateOf(false) }
    if (showRebootConfirm) AlertDialog(
        onDismissRequest = { showRebootConfirm = false },
        title = { Text("Перезагрузить One UI?") },
        text = { Text("Сессия iOS будет остановлена.") },
        confirmButton = {
            TextButton(onClick = {
                showRebootConfirm = false
                if (!busy) {
                    busy = true
                    scope.launch {
                        val r = OneIosRunner.stopSession()
                        logText = r.output
                        status = "НЕ ГОТОВО"
                        busy = false
                    }
                }
            }) { Text("Да") }
        },
        dismissButton = { TextButton(onClick = { showRebootConfirm = false }) { Text("Отмена") } }
    )
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("EOneiOS", fontWeight = FontWeight.Bold)
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
                    Box(Modifier.fillMaxWidth().height(0.dp)) { Box(Modifier.align(Alignment.TopEnd).offset(y = 24.dp)) { AboutButton(onClick = { showAbout = true }) } }
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

            // Плитки проверок
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Одна карточка: место / батарея / экран
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Место", style = MaterialTheme.typography.labelMedium)
                            Text(String.format(java.util.Locale.US, "%.1f ГБ", freeSpaceGb).replace(".", ","), style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                        }
                        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Батарея", style = MaterialTheme.typography.labelMedium)
                            Text("$batteryPctValue%", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                        }
                    }
                }

            }

            val runSrc = remember { MutableInteractionSource() }
            val runPressed by runSrc.collectIsPressedAsState()
            val runRadius by animateDpAsState(if (runPressed) 8.dp else 28.dp, label = "runRadius")
            val installSrc = remember { MutableInteractionSource() }
            val installPressed by installSrc.collectIsPressedAsState()
            val installRadius by animateDpAsState(if (installPressed) 8.dp else 24.dp, label = "installRadius")
            val logsSrc = remember { MutableInteractionSource() }
            val logsPressed by logsSrc.collectIsPressedAsState()
            val logsRadius by animateDpAsState(if (logsPressed) 8.dp else 24.dp, label = "logsRadius")
            val offSrc = remember { MutableInteractionSource() }
            val offPressed by offSrc.collectIsPressedAsState()
            val offRadius by animateDpAsState(if (offPressed) 8.dp else 24.dp, label = "offRadius")

            // Кнопка запуска iOS (главная, с анимацией)
            Button(
                onClick = { onOpenRun() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(scale),
                shape = RoundedCornerShape(runRadius),
                interactionSource = runSrc,
                enabled = !busy && freeSpaceOk && batteryOk
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Запустить iOS", style = MaterialTheme.typography.titleMedium)
            }

            // Кнопка установки iOS
            FilledTonalButton(
                onClick = { onOpenInstall() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(installRadius),
                interactionSource = installSrc,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                enabled = !busy
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Установить iOS")
            }

            // Ряд: Перезагрузка и Логи
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showRebootConfirm = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(offRadius),
                    interactionSource = offSrc,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Перезагрузка", maxLines = 1, softWrap = false, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onOpenLogs,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(logsRadius),
                    interactionSource = logsSrc
                ) {
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
            Text("designed by winterballs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}
