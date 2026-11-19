package com.cineflow.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
import com.cineflow.missioncontrol.data.Incident
import com.cineflow.missioncontrol.data.IncidentSort
import com.cineflow.missioncontrol.data.Mission
import com.cineflow.missioncontrol.data.Severity

@Composable
fun IncidentsScreen(state: AppState) {
    var search by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf<Severity?>(null) }
    var mission by remember { mutableStateOf<Mission?>(null) }
    var sort by remember { mutableStateOf(IncidentSort.FECHA_DESC) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Incident?>(null) }
    var deleting by remember { mutableStateOf<Incident?>(null) }

    val missions = remember(state.dataVersion) { state.repo.getMissions(sort = com.cineflow.missioncontrol.data.MissionSort.NOMBRE) }
    val incidents = remember(state.dataVersion, search, severity, mission, sort) {
        state.repo.getIncidents(missionId = mission?.id, severity = severity, search = search, sort = sort)
    }
    val all = remember(state.dataVersion) { state.repo.getIncidents() }

    Column(Modifier.fillMaxSize().padding(32.dp)) {
        ScreenHeader("Incidencias", "Registro de todas las incidencias ocurridas durante las expediciones") {
            PrimaryButton("Registrar incidencia", Icons.Default.Add, enabled = missions.isNotEmpty()) { creating = true }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            SearchField(search, { search = it }, "Buscar en descripción o misión…", Modifier.weight(1.6f))
            SelectField("Misión", missions, mission, { mission = it }, { it.nombre }, Modifier.weight(1.2f), noneLabel = "Todas")
            SelectField("Gravedad", Severity.entries, severity, { severity = it }, { it.label }, Modifier.weight(1f), noneLabel = "Todas")
            SelectField("Ordenar por", IncidentSort.entries, sort, { if (it != null) sort = it }, { it.label }, Modifier.weight(1.3f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${incidents.size} de ${all.size} incidencias", color = Palette.TextMuted, modifier = Modifier.weight(1f))
            Severity.entries.reversed().forEach { s -> Badge("${s.label}: ${all.count { it.gravedad == s }}", s.color()) }
        }

        Panel(Modifier.fillMaxWidth().weight(1f), padding = 12.dp) {
            when {
                missions.isEmpty() -> EmptyState(Icons.Default.Warning, "No hay misiones", "Las incidencias deben pertenecer a una misión existente. Crea una misión primero.")
                all.isEmpty() -> EmptyState(Icons.Default.Warning, "Sin incidencias registradas", "Todas las expediciones transcurren sin novedades.") {
                    PrimaryButton("Registrar incidencia", Icons.Default.Add) { creating = true }
                }
                incidents.isEmpty() -> EmptyState(Icons.Default.Search, "Sin resultados", "Ninguna incidencia coincide con los filtros.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(incidents, key = { it.id }) { inc ->
                        IncidentCard(
                            inc, showMission = true,
                            onEdit = { editing = inc }, onDelete = { deleting = inc },
                            onOpenMission = { state.navigate(Screen.MissionDetail(inc.misionId)) },
                        )
                    }
                }
            }
        }
    }

    if (creating) IncidentFormDialog(state, null) { creating = false }
    editing?.let { IncidentFormDialog(state, it) { editing = null } }
    deleting?.let { inc ->
        ConfirmDialog(
            "¿Eliminar incidencia?", "Se eliminará la incidencia de \"${inc.misionNombre}\": ${inc.descripcion}",
            onConfirm = { state.performOrNotify("Incidencia eliminada") { state.repo.deleteIncident(inc.id) } },
            onDismiss = { deleting = null },
        )
    }
}
