package com.winterballs.oneios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.winterballs.oneios.ui.screens.HomeScreen
import com.winterballs.oneios.ui.screens.StartScreen
import com.winterballs.oneios.ui.screens.InstallScreen
import com.winterballs.oneios.ui.screens.LogsScreen
import com.winterballs.oneios.ui.theme.OneiOSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OneiOSTheme {
                var showLogs by remember { mutableStateOf(false) }
                var showInstall by remember { mutableStateOf(false) }
                var showRun by remember { mutableStateOf(false) }

                val screen = when {
                    showLogs -> 1
                    showInstall -> 2
                    showRun -> 3
                    else -> 0
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
                        },
                        label = "screenFade"
                    ) { target ->
                        when (target) {
                            1 -> {
                                androidx.activity.compose.BackHandler { showLogs = false }
                                LogsScreen(onBack = { showLogs = false })
                            }
                            2 -> {
                                androidx.activity.compose.BackHandler { showInstall = false }
                                InstallScreen(onBack = { showInstall = false })
                            }
                            3 -> {
                                androidx.activity.compose.BackHandler { showRun = false }
                                StartScreen(onBack = { showRun = false })
                            }
                            else -> HomeScreen(
                                onOpenLogs = { showLogs = true },
                                onOpenInstall = { showInstall = true },
                                onOpenRun = { showRun = true }
                            )
                        }
                    }
                }
            }
        }
    }
}
