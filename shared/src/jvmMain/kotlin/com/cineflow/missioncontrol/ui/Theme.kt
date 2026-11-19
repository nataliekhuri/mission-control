package com.cineflow.missioncontrol.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cineflow.missioncontrol.data.AstronautStatus
import com.cineflow.missioncontrol.data.MissionStatus
import com.cineflow.missioncontrol.data.Priority
import com.cineflow.missioncontrol.data.Severity

object Palette {
    val Background = Color(0xFF07111F)
    val Sidebar = Color(0xFF0A1729)
    val Surface = Color(0xFF0F1E33)
    val SurfaceHigh = Color(0xFF15273F)
    val SurfaceHover = Color(0xFF1A2F4B)
    val Border = Color(0xFF1F3555)
    val Cyan = Color(0xFF22D3EE)
    val Blue = Color(0xFF3B82F6)
    val Indigo = Color(0xFF6366F1)
    val Text = Color(0xFFE2E8F0)
    val TextMuted = Color(0xFF8DA2BF)
    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val Orange = Color(0xFFF97316)
    val Danger = Color(0xFFEF4444)
    val Slate = Color(0xFF64748B)
}

private val colors = darkColorScheme(
    primary = Palette.Cyan,
    onPrimary = Palette.Background,
    primaryContainer = Color(0xFF0E3A4A),
    onPrimaryContainer = Palette.Cyan,
    secondary = Palette.Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF16305A),
    onSecondaryContainer = Palette.Text,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextMuted,
    surfaceContainerLowest = Palette.Background,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.SurfaceHigh,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHover,
    outline = Palette.Border,
    outlineVariant = Palette.Border,
    error = Palette.Danger,
    onError = Color.White,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Text),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
)

@Composable
fun MissionControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

fun MissionStatus.color(): Color = when (this) {
    MissionStatus.PLANIFICADA -> Palette.Blue
    MissionStatus.PREPARACION -> Palette.Warning
    MissionStatus.EN_CURSO -> Palette.Cyan
    MissionStatus.COMPLETADA -> Palette.Success
    MissionStatus.CANCELADA -> Palette.Slate
}

fun Priority.color(): Color = when (this) {
    Priority.BAJA -> Palette.Slate
    Priority.MEDIA -> Palette.Blue
    Priority.ALTA -> Palette.Orange
    Priority.CRITICA -> Palette.Danger
}

fun Severity.color(): Color = when (this) {
    Severity.BAJA -> Palette.Success
    Severity.MEDIA -> Palette.Warning
    Severity.ALTA -> Palette.Orange
    Severity.CRITICA -> Palette.Danger
}

fun AstronautStatus.color(): Color = when (this) {
    AstronautStatus.DISPONIBLE -> Palette.Success
    AstronautStatus.EN_MISION -> Palette.Cyan
    AstronautStatus.NO_DISPONIBLE -> Palette.Slate
}
