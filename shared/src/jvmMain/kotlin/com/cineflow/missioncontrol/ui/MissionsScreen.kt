package com.cineflow.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cineflow.missioncontrol.AppState
import com.cineflow.missioncontrol.Screen
import com.cineflow.missioncontrol.data.Dates
import com.cineflow.missioncontrol.data.Mission
import com.cineflow.missioncontrol.data.MissionSort
import com.cineflow.missioncontrol.data.MissionStatus
import com.cineflow.missioncontrol.data.Priority

@Composable
fun MissionsScreen(state: AppState) {
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<MissionStatus?>(null) }
    var priority by remember { mutableStateOf<Priority?>(null) }
    var sort by remember { mutableStateOf(MissionSort.FECHA_ASC) }

    var editing by remember { mutableStateOf<Mission?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Mission?>(null) }

    // Re-queried whenever a filter changes or the data changes.
    val missions = remember(state.dataVersion, search, status, priority, sort) {
        state.repo.getMissions(search, status, priority, sort)
    }
    val total = remember(state.dataVersion) { state.repo.getMissions().size }
    val filtersActive = search.isNotBlank() || status != null || priority != null

    Column(Modifier.fillMaxSize().padding(32.dp)) {
        ScreenHeader("Misiones", "Planificación y seguimiento de las expediciones espaciales") {
            PrimaryButton("Nueva misión", Icons.Default.Add) { creating = true }
        }

        // Toolbar: search + combinable filters + sort
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            SearchField(search, { search = it }, "Buscar por nombre, destino o descripción…", Modifier.weight(2f))
            SelectField("Estado", MissionStatus.entries, status, { status = it }, { it.label }, Modifier.weight(1f), noneLabel = "Todos")
            SelectField("Prioridad", Priority.entries, priority, { priority = it }, { it.label }, Modifier.weight(1f), noneLabel = "Todas")
            SelectField("Ordenar por", MissionSort.entries, sort, { if (it != null) sort = it }, { it.label }, Modifier.weight(1.3f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (filtersActive) "Mostrando ${missions.size} de $total misiones" else "$total misiones",
                color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            if (filtersActive) SecondaryButton("Limpiar filtros", Icons.Default.Clear, color = Palette.TextMuted) {
                search = ""; status = null; priority = null
            }
        }

        Panel(Modifier.fillMaxWidth().weight(1f), padding = 12.dp) {
            when {
                total == 0 -> EmptyState(
                    Icons.AutoMirrored.Filled.Send, "Aún no hay misiones",
                    "Crea la primera misión o importa datos desde un archivo JSON.",
                ) { PrimaryButton("Crear misión", Icons.Default.Add) { creating = true } }

                missions.isEmpty() -> EmptyState(
                    Icons.Default.Search, "Sin resultados",
                    "Ninguna misión coincide con la búsqueda y los filtros seleccionados.",
                )

                else -> {
                    TableHeader(
                        listOf("Misión" to 2.2f, "Destino" to 1.2f, "Lanzamiento" to 1f, "Duración" to 0.8f, "Estado" to 1.1f, "Prioridad" to 1f, "Tripulación" to 0.8f),
                        trailingWidth = 110.dp,
                    )
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(missions, key = { it.id }) { m ->
                            TableRow(onClick = { state.navigate(Screen.MissionDetail(m.id)) }) {
                                Cell(m.nombre, 2.2f, bold = true, secondary = "#${m.id} · ${m.descripcion}")
                                Cell(m.destino, 1.2f)
                                Cell(Dates.displayDate(m.fecha), 1f)
                                Cell("${m.duracion} días", 0.8f)
                                Box(Modifier.weight(1.1f)) { Badge(m.estado.label, m.estado.color()) }
                                Box(Modifier.weight(1f)) { Badge(m.prioridad.label, m.prioridad.color()) }
                                Cell("${m.crewCount}", 0.8f, color = Palette.TextMuted)
                                Row(Modifier.width(110.dp), horizontalArrangement = Arrangement.End) {
                                    ActionIcon(Icons.Default.Info, "Ver detalle", Palette.Cyan) { state.navigate(Screen.MissionDetail(m.id)) }
                                    ActionIcon(Icons.Default.Edit, "Editar") { editing = m }
                                    ActionIcon(Icons.Default.Delete, "Eliminar", Palette.Danger) { deleting = m }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }

    if (creating) MissionFormDialog(state, null) { creating = false }
    editing?.let { m -> MissionFormDialog(state, m) { editing = null } }
    deleting?.let { m -> DeleteMissionDialog(state, m) { deleting = null } }
}

@Composable
fun DeleteMissionDialog(state: AppState, mission: Mission, onDeleted: () -> Unit = {}, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "¿Eliminar la misión?",
        message = "Se eliminará \"${mission.nombre}\" junto con sus ${mission.incidentCount} incidencias y su tripulación asignada " +
            "(los astronautas no se eliminan). Los recursos asignados volverán al inventario. Esta acción no se puede deshacer.",
        onConfirm = {
            val error = state.perform("Misión \"${mission.nombre}\" eliminada") { state.repo.deleteMission(mission.id) }
            if (error != null) state.notify(error, isError = true) else onDeleted()
        },
        onDismiss = onDismiss,
    )
}
