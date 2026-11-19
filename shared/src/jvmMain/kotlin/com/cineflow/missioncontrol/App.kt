package com.cineflow.missioncontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cineflow.missioncontrol.ui.AstronautDetailScreen
import com.cineflow.missioncontrol.ui.AstronautsScreen
import com.cineflow.missioncontrol.ui.DashboardScreen
import com.cineflow.missioncontrol.ui.DataTransferScreen
import com.cineflow.missioncontrol.ui.IncidentsScreen
import com.cineflow.missioncontrol.ui.MissionControlTheme
import com.cineflow.missioncontrol.ui.MissionDetailScreen
import com.cineflow.missioncontrol.ui.MissionsScreen
import com.cineflow.missioncontrol.ui.Palette
import com.cineflow.missioncontrol.ui.ResourcesScreen

private data class NavItem(val label: String, val icon: ImageVector, val screen: Screen)

private val navItems = listOf(
    NavItem("Panel de control", Icons.Default.Home, Screen.Dashboard),
    NavItem("Misiones", Icons.AutoMirrored.Filled.Send, Screen.Missions),
    NavItem("Astronautas", Icons.Default.Person, Screen.Astronauts),
    NavItem("Recursos", Icons.Default.Build, Screen.Resources),
    NavItem("Incidencias", Icons.Default.Warning, Screen.Incidents),
    NavItem("Importar / Exportar", Icons.Default.Share, Screen.DataTransfer),
)

@Composable
fun App(container: AppContainer) {
    MissionControlTheme {
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val state = remember { AppState(container, snackbar, scope) }

        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(state)
                VerticalDivider(color = Palette.Border)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (val screen = state.screen) {
                        Screen.Dashboard -> DashboardScreen(state)
                        Screen.Missions -> MissionsScreen(state)
                        is Screen.MissionDetail -> MissionDetailScreen(state, screen.id)
                        Screen.Astronauts -> AstronautsScreen(state)
                        is Screen.AstronautDetail -> AstronautDetailScreen(state, screen.id)
                        Screen.Resources -> ResourcesScreen(state)
                        Screen.Incidents -> IncidentsScreen(state)
                        Screen.DataTransfer -> DataTransferScreen(state)
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomEnd).padding(24.dp)) { data ->
                val isError = (data.visuals as? MessageVisuals)?.isError == true
                Snackbar(
                    snackbarData = data,
                    containerColor = if (isError) Color(0xFF3A1520) else Color(0xFF0E3340),
                    contentColor = if (isError) Color(0xFFFECACA) else Palette.Text,
                    dismissActionContentColor = Palette.TextMuted,
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }
}

@Composable
private fun Sidebar(state: AppState) {
    Column(
        Modifier.width(248.dp).fillMaxHeight().background(Palette.Sidebar).padding(vertical = 24.dp, horizontal = 14.dp),
    ) {
        // Brand
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Palette.Cyan, Palette.Blue))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Star, null, tint = Palette.Background, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("MISSION", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 2.sp, color = Palette.Text)
                Text("CONTROL", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 2.sp, color = Palette.Cyan)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("NAVEGACIÓN", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            navItems.forEach { item ->
                val current = state.screen
                val selected = current == item.screen ||
                    (item.screen == Screen.Missions && current is Screen.MissionDetail) ||
                    (item.screen == Screen.Astronauts && current is Screen.AstronautDetail)
                NavButton(item, selected) { state.navigate(item.screen, root = true) }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Surface).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Success))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("SQLite conectado", style = MaterialTheme.typography.labelMedium, color = Palette.Text)
                Text("Datos persistentes", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
        }
    }
}

@Composable
private fun NavButton(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Palette.Cyan.copy(alpha = 0.12f) else Color.Transparent
    val fg = if (selected) Palette.Cyan else Palette.TextMuted
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) Palette.Cyan else Color.Transparent))
        Spacer(Modifier.width(10.dp))
        Icon(item.icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(item.label, color = if (selected) Palette.Text else Palette.TextMuted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
