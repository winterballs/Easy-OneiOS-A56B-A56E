package com.winterballs.oneios.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winterballs.oneios.R

@Composable
fun AboutButton(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val radius by animateDpAsState(if (pressed) 8.dp else 18.dp, label = "aboutRadius")
    Box(
        modifier = Modifier
            .requiredSize(36.dp)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("i", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        icon = {
            Image(
                painter = painterResource(R.drawable.avatar_winterballs),
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(CircleShape)
            )
        },
        title = { Text("Easy OneiOS", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Text(
                "Easy OneiOS — это форк проекта OneiOS, который приносит аутентичный, ультра-оптимизированный iOS userland (Darwin Kernel 22.5.0 / iOS 16.5, XNU, launchd, backboardd и SpringBoard) на Samsung Galaxy A56 5G (SM-A566B / SM-A566E) с возможностью работы параллельно с Samsung One UI."
            )
        }
    )
}
