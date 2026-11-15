package com.winterballs.oneios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.winterballs.oneios.ui.screens.HomeScreen
import com.winterballs.oneios.ui.screens.LogsScreen
import com.winterballs.oneios.ui.theme.OneiOSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OneiOSTheme {
                var showLogs by remember { mutableStateOf(false) }
                if (showLogs) {
                    LogsScreen(
                        lines = listOf("(логи появятся здесь после запуска сессии)"),
                        onBack = { showLogs = false }
                    )
                } else {
                    HomeScreen(onOpenLogs = { showLogs = true })
                }
            }
        }
    }
}
