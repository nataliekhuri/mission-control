package com.cineflow.missioncontrol.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cineflow.missioncontrol.AppState
import com.cineflow.missioncontrol.Screen
import com.cineflow.missioncontrol.data.AstronautStatus
import com.cineflow.missioncontrol.data.CrewMember
import com.cineflow.missioncontrol.data.Dates
import com.cineflow.missioncontrol.data.Incident
import com.cineflow.missioncontrol.data.IncidentSort
import com.cineflow.missioncontrol.data.Mission
import com.cineflow.missioncontrol.data.MissionResource
import com.cineflow.missioncontrol.data.MissionStatus

@Composable
fun MissionDetailScreen(state: AppState, missionId: Long) {
    val mission = remember(state.dataVersion, missionId) { state.repo.getMission(missionId) }
    var incidentSort by remember { mutableStateOf(IncidentSort.FECHA_DESC) }
    val crew = remember(state.dataVersion, missionId) { state.repo.getCrew(missionId) }
    val resources = remember(state.dataVersion, missionId) { state.repo.getMissionResources(missionId) }
    val incidents = remember(state.dataVersion, missionId, incidentSort) { state.repo.getIncidents(missionId = missionId, sort = incidentSort) }

    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var assigningCrew by remember { mutableStateOf(false) }
    var assigningResource by remember { mutableStateOf(false) }
    var incidentDialog by remember { mutableStateOf<Incident?>(null) }
    var creatingIncident by remember { mutableStateOf(false) }
    var removingCrew by remember { mutableStateOf<CrewMember?>(null) }
    var releasing by remember { mutableStateOf<MissionResource?>(null) }
    var deletingIncident by remember { mutableStateOf<Incident?>(null) }

    if (mission == null) {
        Column(Modifier.fillMaxSize().padding(32.dp)) {
            TextButton(onClick = { state.back() }) { Text("← Volver") }
            EmptyState(Icons.Default.Search, "Misión no encontrada", "La misión ya no existe en la base de datos.")
        }
        return
    }
    val closed = mission.estado == MissionStatus.COMPLETADA || mission.estado == MissionStatus.CANCELADA

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp)) {
        TextButton(onClick = { state.back() }, modifier = Modifier.padding(bottom = 8.dp)) {
            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            Spacer(Modifier.width(6.dp))
            Text("Volver")
        }
        ScreenHeader(mission.nombre, "Misión #${mission.id} · Destino: ${mission.destino}") {
            SecondaryButton("Editar", Icons.Default.Edit) { editing = true }
            SecondaryButton("Eliminar", Icons.Default.Delete, color = Palette.Danger) { deleting = true }
        }

        // General information
        Panel(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(mission.estado.label, mission.estado.color())
                Badge("Prioridad ${mission.prioridad.label}", mission.prioridad.color())
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoItem("Destino", mission.destino, Modifier.weight(1f))
                InfoItem("Fecha de lanzamiento", Dates.displayDate(mission.fecha), Modifier.weight(1f))
                InfoItem("Duración", "${mission.duracion} días", Modifier.weight(1f))
                val end = Dates.parseDate(mission.fecha)?.plusDays(mission.duracion.toLong())?.format(Dates.DATE)
                InfoItem("Regreso estimado", end?.let { Dates.displayDate(it) } ?: "-", Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            InfoItem("Descripción / objetivo", mission.descripcion)
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Crew
            Panel(Modifier.weight(1f)) {
                SectionTitle("Tripulación (${crew.size})") {
                    SecondaryButton("Asignar", Icons.Default.Add, enabled = !closed) { assigningCrew = true }
                }
                if (crew.isEmpty()) {
                    EmptyState(Icons.Default.Person, "Sin tripulación", if (closed) "La misión está cerrada." else "Asigna astronautas indicando su rol.")
                } else crew.forEach { c ->
                    TableRow(onClick = { state.navigate(Screen.AstronautDetail(c.astronaut.id)) }) {
                        Cell(c.astronaut.nombre, 1.6f, bold = true, secondary = "${c.astronaut.especialidad.label} · ${c.astronaut.experiencia} años")
                        Cell(c.rol, 1.2f, color = Palette.Cyan)
                        Box(Modifier.weight(1.1f)) { Badge(c.astronaut.estado.label, c.astronaut.estado.color()) }
                        ActionIcon(Icons.Default.Close, "Quitar de la misión", Palette.Danger) { removingCrew = c }
                    }
                }
            }
            // Resources
            Panel(Modifier.weight(1f)) {
                SectionTitle("Recursos asignados (${resources.size})") {
                    SecondaryButton("Asignar", Icons.Default.Add, enabled = !closed) { assigningResource = true }
                }
                if (resources.isEmpty()) {
                    EmptyState(Icons.Default.Build, "Sin recursos", if (closed) "La misión está cerrada." else "Asigna recursos desde el inventario general.")
                } else resources.forEach { r ->
                    TableRow {
                        Cell(r.resource.nombre, 1.6f, bold = true, secondary = r.resource.categoria.label)
                        Cell("${r.cantidad} ${r.resource.unidad}", 1.2f, color = Palette.Cyan)
                        Cell("Quedan ${r.resource.cantidad} en inventario", 1.3f, color = Palette.TextMuted)
                        ActionIcon(Icons.Default.Close, "Liberar recurso", Palette.Danger) { releasing = r }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Incidents
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle("Incidencias (${incidents.size})") {
                SelectField("Ordenar", IncidentSort.entries, incidentSort, { if (it != null) incidentSort = it }, { it.label }, Modifier.width(260.dp))
                PrimaryButton("Registrar incidencia", Icons.Default.Add) { creatingIncident = true }
            }
            if (incidents.isEmpty()) {
                EmptyState(Icons.Default.Warning, "Sin incidencias", "No hay incidencias registradas para esta misión.")
            } else {
                incidents.forEach { inc ->
                    IncidentCard(inc, showMission = false, onEdit = { incidentDialog = inc }, onDelete = { deletingIncident = inc })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // ----- dialogs
    if (editing) MissionFormDialog(state, mission) { editing = false }
    if (deleting) DeleteMissionDialog(state, mission, onDeleted = { state.navigate(Screen.Missions, root = true) }) { deleting = false }
    if (assigningCrew) AssignCrewDialog(state, mission, crew) { assigningCrew = false }
    if (assigningResource) AssignResourceDialog(state, mission) { assigningResource = false }
    if (creatingIncident) IncidentFormDialog(state, null, fixedMissionId = mission.id) { creatingIncident = false }
    incidentDialog?.let { IncidentFormDialog(state, it, fixedMissionId = mission.id) { incidentDialog = null } }
    removingCrew?.let { c ->
        ConfirmDialog(
            "¿Quitar de la tripulación?",
            "${c.astronaut.nombre} dejará de formar parte de \"${mission.nombre}\". El astronauta seguirá registrado en el sistema.",
            confirmText = "Quitar",
            onConfirm = { state.performOrNotify("${c.astronaut.nombre} retirado de la misión") { state.repo.removeAstronautFromMission(mission.id, c.astronaut.id) } },
            onDismiss = { removingCrew = null },
        )
    }
    releasing?.let { r ->
        ConfirmDialog(
            "¿Liberar recurso?",
            "Se devolverán ${r.cantidad} ${r.resource.unidad} de ${r.resource.nombre} al inventario general.",
            confirmText = "Liberar",
            onConfirm = { state.performOrNotify("Recurso devuelto al inventario") { state.repo.releaseResource(mission.id, r.resource.id) } },
            onDismiss = { releasing = null },
        )
    }
    deletingIncident?.let { inc ->
        ConfirmDialog(
            "¿Eliminar incidencia?", "Se eliminará la incidencia \"${inc.descripcion}\".",
            onConfirm = { state.performOrNotify("Incidencia eliminada") { state.repo.deleteIncident(inc.id) } },
            onDismiss = { deletingIncident = null },
        )
    }
}

/** Incident row with a colored severity stripe, used in mission detail and the incidents section. */
@Composable
fun IncidentCard(inc: Incident, showMission: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onOpenMission: (() -> Unit)? = null) {
    val color = inc.gravedad.color()
    Row(
        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.06f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(inc.gravedad.label, color)
                Badge(inc.tipo.label, Palette.Blue)
                Text(Dates.displayDateTime(inc.fecha), color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                if (showMission) {
                    TextButton(onClick = { onOpenMission?.invoke() }) {
                        Text("Misión: ${inc.misionNombre}", color = Palette.Cyan, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(inc.descripcion, style = MaterialTheme.typography.bodyMedium)
        }
        ActionIcon(Icons.Default.Edit, "Editar", onClick = onEdit)
        ActionIcon(Icons.Default.Delete, "Eliminar", Palette.Danger, onClick = onDelete)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun AssignCrewDialog(state: AppState, mission: Mission, crew: List<CrewMember>, onDismiss: () -> Unit) {
    val assignedIds = crew.map { it.astronaut.id }.toSet()
    val candidates = remember(state.dataVersion) {
        state.repo.getAstronauts().filter { it.id !in assignedIds && it.estado != AstronautStatus.NO_DISPONIBLE }
    }
    var selected by remember { mutableStateOf(candidates.firstOrNull()) }
    var rol by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(emptyList<String>()) }

    FormDialog(
        title = "Asignar astronauta",
        errors = errors,
        onDismiss = onDismiss,
        saveText = "Asignar",
        onSave = {
            val astronaut = selected
            errors = buildList {
                if (astronaut == null) add("Selecciona un astronauta.")
                if (rol.isBlank()) add("Indica el rol que tendrá en la misión.")
            }
            if (errors.isEmpty()) {
                val error = state.perform("${astronaut!!.nombre} asignado como ${rol.trim()}") {
                    state.repo.assignAstronaut(mission.id, astronaut.id, rol)
                }
                if (error == null) onDismiss() else errors = listOf(error)
            }
        },
    ) {
        if (candidates.isEmpty()) {
            Text("No hay astronautas disponibles para asignar. Registra astronautas o cambia su disponibilidad.", color = Palette.Warning)
        }
        SelectField(
            "Astronauta *", candidates, selected, { selected = it },
            { "${it.nombre} · ${it.especialidad.label} · ${it.estado.label}" }, Modifier.fillMaxWidth(),
        )
        FormField("Rol en la misión *", rol, { rol = it }, placeholder = "Comandante, piloto, especialista de misión…")
        Text("Solo se muestran astronautas que no están en esta misión y no figuran como no disponibles.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}

@Composable
private fun AssignResourceDialog(state: AppState, mission: Mission, onDismiss: () -> Unit) {
    val resources = remember(state.dataVersion) { state.repo.getResources() }
    var selected by remember { mutableStateOf(resources.firstOrNull { it.cantidad > 0 }) }
    var cantidad by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(emptyList<String>()) }

    FormDialog(
        title = "Asignar recurso a ${mission.nombre}",
        errors = errors,
        onDismiss = onDismiss,
        saveText = "Asignar",
        onSave = {
            val r = selected
            val qty = cantidad.toIntOrNull()
            errors = buildList {
                if (r == null) add("Selecciona un recurso.")
                if (qty == null || qty <= 0) add("La cantidad debe ser un número entero mayor que 0.")
                else if (r != null && qty > r.cantidad) add("No puedes asignar $qty ${r.unidad}: solo hay ${r.cantidad} disponibles.")
            }
            if (errors.isEmpty()) {
                val error = state.perform("$qty ${r!!.unidad} de ${r.nombre} asignados") { state.repo.assignResource(mission.id, r.id, qty!!) }
                if (error == null) onDismiss() else errors = listOf(error)
            }
        },
    ) {
        if (resources.isEmpty()) Text("El inventario está vacío. Crea recursos en la sección Recursos.", color = Palette.Warning)
        SelectField(
            "Recurso *", resources, selected, { selected = it },
            { "${it.nombre} · disponible: ${it.cantidad} ${it.unidad}" }, Modifier.fillMaxWidth(),
        )
        FormField("Cantidad a asignar *", cantidad, { cantidad = it }, numeric = true)
        selected?.let {
            Text(
                "Disponible: ${it.cantidad} ${it.unidad}. La cantidad se descontará del inventario general.",
                style = MaterialTheme.typography.bodySmall,
                color = if (it.cantidad == 0) Palette.Danger else Palette.TextMuted,
            )
        }
    }
}
