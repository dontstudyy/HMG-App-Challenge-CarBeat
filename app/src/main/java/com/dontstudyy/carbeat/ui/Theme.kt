package com.dontstudyy.carbeat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 레인 색 (설계서 3장). 핸들 왼쪽 / 브레이크 / 핸들 오른쪽 / 와이퍼. */
object LaneColors {
    val SteerLeft = Color(0xFFFF7A1A)
    val Brake = Color(0xFFFF3355)
    val SteerRight = Color(0xFFFFC233)
    val Wiper = Color(0xFF4CC9FF)
    val all = listOf(SteerLeft, Brake, SteerRight, Wiper)
}

private val scheme = darkColorScheme(
    background = Color(0xFF0B0D12),
    surface = Color(0xFF151922),
    onBackground = Color(0xFFE8EBF2),
    onSurface = Color(0xFFE8EBF2),
    primary = LaneColors.Wiper,
    onPrimary = Color(0xFF00131C),
)

@Composable
fun CarBeatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
