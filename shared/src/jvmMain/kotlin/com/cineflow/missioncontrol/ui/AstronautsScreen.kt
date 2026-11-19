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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.cineflow.missioncontrol.data.Astronaut
import com.cineflow.missioncontrol.data.AstronautStatus
import com.cineflow.missioncontrol.data.Dates
import com.cineflow.missioncontrol.data.MissionStatus

@Composable
fun AstronautsScreen(state: AppState) {
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<AstronautStatus?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Astronaut?>(null) }
    var deleting by remember { mutableStateOf<Astronaut?>(null) }

    val astronauts = remember(state.dataVersion, search, status) { state.repo.getAstronauts(search, status) }
    val all = remember(state.dataVersion) { state.repo.getAstronauts() }

    Column(Modifier.fillMaxSize().padding(32.dp)) {
        ScreenHeader("Astronautas", "Plantilla de la agencia, especialidades y disponibilidad") {
            PrimaryButton("Nuevo astronauta", Icons.Default.Add) { creating = true }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            SearchField(search, { search = it }, "Buscar por nombre o especialidad…", Modifier.weight(2f))
            SelectField("Disponibilidad", AstronautStatus.entries, status, { status = it }, { it.label }, Modifier.weight(1f), noneLabel = "Todas")
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${astronauts.size} de ${all.size} astronautas", color = Palette.TextMuted, modifier = Modifier.weight(1f))
            AstronautStatus.entries.forEach { s -> Badge("${s.label}: ${all.count { it.estado == s }}", s.color()) }
        }

        Panel(Modifier.fillMaxWidth().weight(1f), padding = 12.dp) {
            when {
                all.isEmpty() -> EmptyState(Icons.Default.Person, "No hay astronautas registrados", "Registra el primer miembro de la tripulación.") {
                    PrimaryButton("Registrar astronauta", Icons.Default.Add) { creating = true }
                }
                astronauts.isEmpty() -> EmptyState(Icons.Default.Search, "Sin resultados", "Ningún astronauta coincide con la búsqueda.")
                else -> {
                    TableHeader(listOf("Nombre" to 2f, "Especialidad" to 1.2f, "Experiencia" to 1f, "Estado" to 1.2f, "Misiones" to 0.8f), trailingWidth = 110.dp)
                    LazyColumn {
                        items(astronauts, key = { it.id }) { a ->
                            TableRow(onClick = { state.navigate(Screen.AstronautDetail(a.id)) }) {
                                Cell(a.nombre, 2f, bold = true, secondary = "#${a.id}")
                                Cell(a.especialidad.label, 1.2f)
                                Cell("${a.experiencia} años", 1f)
                                Box(Modifier.weight(1.2f)) { Badge(a.estado.label, a.estado.color()) }
                                Cell("${a.missionCount}", 0.8f, color = Palette.TextMuted)
                                Row(Modifier.width(110.dp), horizontalArrangement = Arrangement.End) {
                                    ActionIcon(Icons.Default.Info, "Ver detalle", Palette.Cyan) { state.navigate(Screen.AstronautDetail(a.id)) }
                                    ActionIcon(Icons.Default.Edit, "Editar") { editing = a }
                                    ActionIcon(Icons.Default.Delete, "Eliminar", Palette.Danger) { deleting = a }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) AstronautFormDialog(state, null) { creating = false }
    editing?.let { AstronautFormDialog(state, it) { editing = null } }
    deleting?.let { a -> DeleteAstronautDialog(state, a) { deleting = null } }
}

@Composable
private fun DeleteAstronautDialog(state: AppState, a: Astronaut, onDeleted: () -> Unit = {}, onDismiss: () -> Unit) {
    ConfirmDialog(
        "¿Eliminar astronauta?",
        "Se eliminará a ${a.nombre} del sistema" +
            (if (a.missionCount > 0) " y se le retirará de ${a.missionCount} misión(es)" else "") + ". Esta acción no se puede deshacer.",
        onConfirm = {
            val error = state.perform("Astronauta eliminado") { state.repo.deleteAstronaut(a.id) }
            if (error != null) state.notify(error, true) else onDeleted()
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun AstronautDetailScreen(state: AppState, astronautId: Long) {
    val astronaut = remember(state.dataVersion, astronautId) { state.repo.getAstronaut(astronautId) }
    val missions = remember(state.dataVersion, astronautId) { state.repo.getAstronautMissions(astronautId) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp)) {
        TextButton(onClick = { state.back() }, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            Spacer(Modifier.width(6.dp))
            Text("Volver")
        }
        if (astronaut == null) {
            EmptyState(Icons.Default.Search, "Astronauta no encontrado", "El astronauta ya no existe en la base de datos.")
            return@Column
        }
        ScreenHeader(astronaut.nombre, "Astronauta #${astronaut.id}") {
            SecondaryButton("Editar", Icons.Default.Edit) { editing = true }
            SecondaryButton("Eliminar", Icons.Default.Delete, color = Palette.Danger) { deleting = true }
        }
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                InfoItem("Especialidad", astronaut.especialidad.label, Modifier.weight(1f))
                InfoItem("Experiencia", "${astronaut.experiencia} años", Modifier.weight(1f))
                Column(Modifier.weight(1f)) {
                    Text("ESTADO ACTUAL", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
                    Spacer(Modifier.height(6.dp))
                    Badge(astronaut.estado.label, astronaut.estado.color())
                }
                InfoItem("Misiones", "${missions.size}", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))

        val current = missions.filter { it.mission.estado in setOf(MissionStatus.PLANIFICADA, MissionStatus.PREPARACION, MissionStatus.EN_CURSO) }
        val past = missions - current.toSet()
        listOf("Misiones actuales y futuras" to current, "Misiones en las que ha participado" to past).forEach { (title, list) ->
            Panel(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                SectionTitle("$title (${list.size})")
                if (list.isEmpty()) {
                    Text("Ninguna.", color = Palette.TextMuted)
                } else {
                    TableHeader(listOf("Misión" to 2f, "Rol" to 1.3f, "Destino" to 1.2f, "Fecha" to 1f, "Estado" to 1.1f))
                    list.forEach { am ->
                        TableRow(onClick = { state.navigate(Screen.MissionDetail(am.mission.id)) }) {
                            Cell(am.mission.nombre, 2f, bold = true)
                            Cell(am.rol, 1.3f, color = Palette.Cyan)
                            Cell(am.mission.destino, 1.2f)
                            Cell(Dates.displayDate(am.mission.fecha), 1f)
                            Box(Modifier.weight(1.1f)) { Badge(am.mission.estado.label, am.mission.estado.color()) }
                        }
                    }
                }
            }
        }
        if (missions.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Filled.Send, "Sin misiones", "Este astronauta todavía no ha sido asignado a ninguna misión. Asígnalo desde el detalle de una misión.")
        }
    }

    if (editing && astronaut != null) AstronautFormDialog(state, astronaut) { editing = false }
    if (deleting && astronaut != null) DeleteAstronautDialog(state, astronaut, onDeleted = { state.navigate(Screen.Astronauts, root = true) }) { deleting = false }
}
