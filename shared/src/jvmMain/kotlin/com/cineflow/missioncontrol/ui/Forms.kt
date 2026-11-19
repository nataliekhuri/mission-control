package com.cineflow.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cineflow.missioncontrol.AppState
import com.cineflow.missioncontrol.data.Astronaut
import com.cineflow.missioncontrol.data.AstronautStatus
import com.cineflow.missioncontrol.data.Dates
import com.cineflow.missioncontrol.data.Incident
import com.cineflow.missioncontrol.data.IncidentType
import com.cineflow.missioncontrol.data.Mission
import com.cineflow.missioncontrol.data.MissionStatus
import com.cineflow.missioncontrol.data.Priority
import com.cineflow.missioncontrol.data.Resource
import com.cineflow.missioncontrol.data.ResourceCategory
import com.cineflow.missioncontrol.data.Severity
import com.cineflow.missioncontrol.data.Specialty
import com.cineflow.missioncontrol.data.Validation

/** Create (existing == null) or edit a mission. Current values are loaded into the form when editing. */
@Composable
fun MissionFormDialog(state: AppState, existing: Mission?, onDismiss: () -> Unit) {
    var nombre by remember { mutableStateOf(existing?.nombre ?: "") }
    var destino by remember { mutableStateOf(existing?.destino ?: "") }
    var fecha by remember { mutableStateOf(existing?.fecha ?: Dates.today()) }
    var duracion by remember { mutableStateOf(existing?.duracion?.toString() ?: "") }
    var estado by remember { mutableStateOf(existing?.estado ?: MissionStatus.PLANIFICADA) }
    var prioridad by remember { mutableStateOf(existing?.prioridad ?: Priority.MEDIA) }
    var descripcion by remember { mutableStateOf(existing?.descripcion ?: "") }
    var errors by remember { mutableStateOf(emptyList<String>()) }

    FormDialog(
        title = if (existing == null) "Nueva misión" else "Editar misión",
        errors = errors,
        onDismiss = onDismiss,
        onSave = {
            val days = duracion.toIntOrNull()
            errors = Validation.mission(nombre, destino, fecha, days, descripcion)
            if (errors.isEmpty()) {
                val mission = Mission(existing?.id ?: 0, nombre, destino, fecha, days!!, estado, prioridad, descripcion)
                val error = if (existing == null) state.perform("Misión \"${nombre.trim()}\" creada") { state.repo.insertMission(mission) }
                else state.perform("Misión actualizada") { state.repo.updateMission(mission) }
                if (error == null) onDismiss() else errors = listOf(error)
            }
        },
    ) {
        FormField("Nombre de la misión *", nombre, { nombre = it })
        FormField("Destino *", destino, { destino = it }, placeholder = "Luna, Marte, ISS, órbita baja…")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField("Fecha de lanzamiento *", fecha, { fecha = it }, Modifier.weight(1f), placeholder = "AAAA-MM-DD")
            FormField("Duración (días) *", duracion, { duracion = it }, Modifier.weight(1f), numeric = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectField("Estado", MissionStatus.entries, estado, { if (it != null) estado = it }, { it.label }, Modifier.weight(1f))
            SelectField("Prioridad", Priority.entries, prioridad, { if (it != null) prioridad = it }, { it.label }, Modifier.weight(1f))
        }
        FormField("Descripción / objetivo *", descripcion, { descripcion = it }, singleLine = false, minLines = 3)
        Text("* Campos obligatorios", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}

@Composable
fun AstronautFormDialog(state: AppState, existing: Astronaut?, onDismiss: () -> Unit) {
    var nombre by remember { mutableStateOf(existing?.nombre ?: "") }
    var especialidad by remember { mutableStateOf(existing?.especialidad ?: Specialty.PILOTO) }
    var experiencia by remember { mutableStateOf(existing?.experiencia?.toString() ?: "") }
    var estado by remember { mutableStateOf(existing?.estado ?: AstronautStatus.DISPONIBLE) }
    var errors by remember { mutableStateOf(emptyList<String>()) }

    FormDialog(
        title = if (existing == null) "Nuevo astronauta" else "Editar astronauta",
        errors = errors,
        onDismiss = onDismiss,
        onSave = {
            val years = experiencia.toIntOrNull()
            errors = Validation.astronaut(nombre, years)
            if (errors.isEmpty()) {
                val astronaut = Astronaut(existing?.id ?: 0, nombre, especialidad, years!!, estado)
                val error = if (existing == null) state.perform("Astronauta \"${nombre.trim()}\" registrado") { state.repo.insertAstronaut(astronaut) }
                else state.perform("Astronauta actualizado") { state.repo.updateAstronaut(astronaut) }
                if (error == null) onDismiss() else errors = listOf(error)
            }
        },
    ) {
        FormField("Nombre completo *", nombre, { nombre = it })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectField("Especialidad", Specialty.entries, especialidad, { if (it != null) especialidad = it }, { it.label }, Modifier.weight(1f))
            FormField("Experiencia (años) *", experiencia, { experiencia = it }, Modifier.weight(1f), numeric = true)
        }
        SelectField("Disponibilidad", AstronautStatus.entries, estado, { if (it != null) estado = it }, { it.label }, Modifier.fillMaxWidth())
    }
}

@Composable
fun ResourceFormDialog(state: AppState, existing: Resource?, onDismiss: () -> Unit) {
    var nombre by remember { mutableStateOf(existing?.nombre ?: "") }
    var categoria by remember { mutableStateOf(existing?.categoria ?: ResourceCategory.EQUIPAMIENTO) }
    var cantidad by remember { mutableStateOf(existing?.cantidad?.toString() ?: "") }
    var unidad by remember { mutableStateOf(existing?.unidad ?: "unidades") }
    var errors by remember { mutableStateOf(emptyList<String>()) }

    FormDialog(
        title = if (existing == null) "Nuevo recurso" else "Editar recurso",
        errors = errors,
        onDismiss = onDismiss,
        onSave = {
            val qty = cantidad.toIntOrNull()
            errors = Validation.resource(nombre, qty, unidad)
            if (errors.isEmpty()) {
                val resource = Resource(existing?.id ?: 0, nombre, categoria, qty!!, unidad)
                val error = if (existing == null) state.perform("Recurso \"${nombre.trim()}\" añadido al inventario") { state.repo.insertResource(resource) }
                else state.perform("Recurso actualizado") { state.repo.updateResource(resource) }
                if (error == null) onDismiss() else errors = listOf(error)
            }
        },
    ) {
        FormField("Nombre del recurso *", nombre, { nombre = it })
        SelectField("Categoría", ResourceCategory.entries, categoria, { if (it != null) categoria = it }, { it.label }, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField("Cantidad disponible *", cantidad, { cantidad = it }, Modifier.weight(1f), numeric = true)
            FormField("Unidad *", unidad, { unidad = it }, Modifier.weight(1f), placeholder = "kg, litros, unidades…")
        }
        if (existing != null && existing.assigned > 0) {
            Text(
                "Actualmente hay ${existing.assigned} ${existing.unidad} asignados a misiones (no incluidos en la cantidad disponible).",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
            )
        }
    }
}

/**
 * Create or edit an incident. If [fixedMissionId] is set the mission cannot be changed
 * (used from the mission detail screen). Only existing missions can be selected.
 */
@Composable
fun IncidentFormDialog(state: AppState, existing: Incident?, fixedMissionId: Long? = null, onDismiss: () -> Unit) {
    val missions = remember(state.dataVersion) { state.repo.getMissions() }
    var missionId by remember { mutableStateOf(existing?.misionId ?: fixedMissionId) }
    var fecha by remember { mutableStateOf(existing?.fecha ?: Dates.now()) }
    var tipo by remember { mutableStateOf(existing?.tipo ?: IncidentType.TECNICA) }
    var gravedad by remember { mutableStateOf(existing?.gravedad ?: Severity.MEDIA) }
    var descripcion by remember { mutableStateOf(existing?.descripcion ?: "") }
    var errors by remember { mutableStateOf(emptyList<String>()) }

    FormDialog(
        title = if (existing == null) "Registrar incidencia" else "Editar incidencia",
        errors = errors,
        onDismiss = onDismiss,
        onSave = {
            errors = buildList {
                if (missionId == null || missions.none { it.id == missionId }) add("Debes seleccionar una misión existente.")
                addAll(Validation.incident(fecha, descripcion))
            }
            if (errors.isEmpty()) {
                val incident = Incident(existing?.id ?: 0, missionId!!, fecha, tipo, gravedad, descripcion)
                val error = if (existing == null) state.perform("Incidencia registrada") { state.repo.insertIncident(incident) }
                else state.perform("Incidencia actualizada") { state.repo.updateIncident(incident) }
                if (error == null) onDismiss() else errors = listOf(error)
            }
        },
    ) {
        SelectField(
            "Misión afectada *", missions, missions.firstOrNull { it.id == missionId }, { missionId = it?.id },
            { "${it.nombre} (${it.estado.label})" }, Modifier.fillMaxWidth(), enabled = fixedMissionId == null,
        )
        FormField("Fecha y hora *", fecha, { fecha = it }, placeholder = "AAAA-MM-DD HH:MM")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectField("Tipo", IncidentType.entries, tipo, { if (it != null) tipo = it }, { it.label }, Modifier.weight(1f))
            SelectField("Gravedad", Severity.entries, gravedad, { if (it != null) gravedad = it }, { it.label }, Modifier.weight(1f))
        }
        FormField("Descripción *", descripcion, { descripcion = it }, singleLine = false, minLines = 3)
    }
}
