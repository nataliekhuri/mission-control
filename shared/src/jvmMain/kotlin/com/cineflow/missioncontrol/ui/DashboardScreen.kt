package com.cineflow.missioncontrol.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cineflow.missioncontrol.AppState
import com.cineflow.missioncontrol.Screen
import com.cineflow.missioncontrol.data.Alert
import com.cineflow.missioncontrol.data.Dates
import com.cineflow.missioncontrol.data.MissionStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun DashboardScreen(state: AppState) {
    val stats = remember(state.dataVersion) { state.repo.getDashboardStats() }
    val todayText = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-ES")))
            .replaceFirstChar { it.uppercase() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp)) {
        ScreenHeader("Panel de control", "Estado general de la agencia · $todayText")

        val isEmpty = stats.totalMissions == 0 && stats.totalAstronauts == 0 && stats.totalResources == 0
        if (isEmpty) {
            Panel(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = "La base de datos está vacía",
                    message = "Crea tu primera misión o importa el archivo mission-control-sample.json para cargar datos de demostración.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton("Nueva misión", Icons.Default.Add) { state.navigate(Screen.Missions, root = true) }
                        SecondaryButton("Importar JSON", Icons.Default.Share) { state.navigate(Screen.DataTransfer, root = true) }
                    }
                }
            }
        }

        // KPI cards
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("Misiones totales", stats.totalMissions.toString(), "Registradas en el sistema", Icons.AutoMirrored.Filled.Send, Palette.Blue, Modifier.weight(1f)) {
                state.navigate(Screen.Missions, root = true)
            }
            StatCard("Misiones activas", stats.activeMissions.toString(), "En preparación o en curso", Icons.Default.PlayArrow, Palette.Cyan, Modifier.weight(1f)) {
                state.navigate(Screen.Missions, root = true)
            }
            StatCard("Completadas", stats.completedMissions.toString(), "Misiones finalizadas", Icons.Default.CheckCircle, Palette.Success, Modifier.weight(1f)) {
                state.navigate(Screen.Missions, root = true)
            }
            StatCard("Astronautas disponibles", stats.availableAstronauts.toString(), "De ${stats.totalAstronauts} en plantilla", Icons.Default.Person, Palette.Indigo, Modifier.weight(1f)) {
                state.navigate(Screen.Astronauts, root = true)
            }
            StatCard("Incidencias", stats.totalIncidents.toString(), "${stats.criticalIncidents} críticas", Icons.Default.Warning, Palette.Orange, Modifier.weight(1f)) {
                state.navigate(Screen.Incidents, root = true)
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Left column: next mission + status distribution
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle("Próxima misión planificada")
                    val next = stats.nextMission
                    if (next == null) {
                        EmptyState(Icons.Default.DateRange, "Sin misiones próximas", "No hay misiones planificadas o en preparación con fecha futura.")
                    } else {
                        val days = ChronoUnit.DAYS.between(LocalDate.now(), Dates.parseDate(next.fecha) ?: LocalDate.now())
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Palette.Cyan.copy(alpha = 0.07f))
                                .clickable { state.navigate(Screen.MissionDetail(next.id)) }
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(next.nombre, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                                Text("Destino: ${next.destino}", color = Palette.TextMuted)
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Badge(next.estado.label, next.estado.color())
                                    Badge("Prioridad ${next.prioridad.label}", next.prioridad.color())
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (days == 0L) "HOY" else "T-$days", style = MaterialTheme.typography.headlineMedium, color = Palette.Cyan)
                                Text(if (days == 1L) "día para el lanzamiento" else "días para el lanzamiento", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth()) {
                            InfoItem("Lanzamiento", Dates.displayDate(next.fecha), Modifier.weight(1f))
                            InfoItem("Duración", "${next.duracion} días", Modifier.weight(1f))
                            InfoItem("Tripulación", "${next.crewCount} astronautas", Modifier.weight(1f))
                        }
                    }
                }

                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle("Misiones por estado")
                    if (stats.totalMissions == 0) {
                        Text("Todavía no hay misiones registradas.", color = Palette.TextMuted)
                    } else {
                        MissionStatus.entries.forEach { status ->
                            val count = stats.missionsByStatus[status] ?: 0
                            StatusBar(status.label, count, stats.totalMissions, status.color())
                        }
                    }
                }
            }

            // Right column: alerts + recent incidents
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle("Alertas del sistema") {
                        if (stats.alerts.isNotEmpty()) Badge("${stats.alerts.size}", Palette.Danger)
                    }
                    if (stats.alerts.isEmpty()) {
                        EmptyState(Icons.Default.CheckCircle, "Todo en orden", "No se han detectado alertas en los datos actuales.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            stats.alerts.take(8).forEach { AlertRow(it) { id -> state.navigate(Screen.MissionDetail(id)) } }
                            if (stats.alerts.size > 8) Text("… y ${stats.alerts.size - 8} alertas más", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle("Últimas incidencias")
                    if (stats.recentIncidents.isEmpty()) {
                        EmptyState(Icons.Default.Notifications, "Sin incidencias", "No se ha registrado ninguna incidencia.")
                    } else {
                        stats.recentIncidents.forEach { inc ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .clickable { state.navigate(Screen.MissionDetail(inc.misionId)) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Badge(inc.gravedad.label, inc.gravedad.color())
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(inc.descripcion, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${inc.misionNombre} · ${inc.tipo.label} · ${Dates.displayDateTime(inc.fecha)}", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBar(label: String, count: Int, total: Int, color: androidx.compose.ui.graphics.Color) {
    val fraction = if (total == 0) 0f else count.toFloat() / total
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("$count", fontWeight = FontWeight.SemiBold, color = color)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.SurfaceHigh)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

@Composable
private fun AlertRow(alert: Alert, onOpenMission: (Long) -> Unit) {
    val color = alert.level.color()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.08f))
            .then(if (alert.missionId != null) Modifier.clickable { onOpenMission(alert.missionId) } else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Default.Warning, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(alert.title, fontWeight = FontWeight.SemiBold, color = color, style = MaterialTheme.typography.bodyMedium)
            Text(alert.message, color = Palette.Text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
