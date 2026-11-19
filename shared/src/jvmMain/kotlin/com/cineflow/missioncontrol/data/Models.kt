package com.cineflow.missioncontrol.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.text.Normalizer

/** Every enum shown in the UI exposes a human readable label. The enum name is what is stored in SQLite/JSON. */
interface Labeled {
    val label: String
}

@Serializable
enum class MissionStatus(override val label: String) : Labeled {
    PLANIFICADA("Planificada"),
    PREPARACION("Preparación"),
    EN_CURSO("En curso"),
    COMPLETADA("Completada"),
    CANCELADA("Cancelada"),
}

@Serializable
enum class Priority(override val label: String) : Labeled {
    BAJA("Baja"),
    MEDIA("Media"),
    ALTA("Alta"),
    CRITICA("Crítica"),
}

@Serializable
enum class Specialty(override val label: String) : Labeled {
    PILOTO("Piloto"),
    INGENIERIA("Ingeniería"),
    MEDICINA("Medicina"),
    CIENCIA("Ciencia"),
    COMUNICACIONES("Comunicaciones"),
}

@Serializable
enum class AstronautStatus(override val label: String) : Labeled {
    DISPONIBLE("Disponible"),
    EN_MISION("En misión"),
    NO_DISPONIBLE("No disponible"),
}

@Serializable
enum class ResourceCategory(override val label: String) : Labeled {
    COMBUSTIBLE("Combustible"),
    OXIGENO("Oxígeno"),
    AGUA("Agua"),
    ALIMENTOS("Alimentos"),
    MEDICO("Material médico"),
    EQUIPAMIENTO("Equipamiento"),
    REPUESTOS("Repuestos"),
    OTRO("Otro"),
}

@Serializable
enum class IncidentType(override val label: String) : Labeled {
    TECNICA("Técnica"),
    MEDICA("Médica"),
    COMUNICACIONES("Comunicaciones"),
    NAVEGACION("Navegación"),
    METEOROLOGICA("Meteorológica"),
    SEGURIDAD("Seguridad"),
    OTRA("Otra"),
}

/** Incident severity. Same scale as mission priority, but a separate concept. */
@Serializable
enum class Severity(override val label: String) : Labeled {
    BAJA("Baja"),
    MEDIA("Media"),
    ALTA("Alta"),
    CRITICA("Crítica"),
}

data class Mission(
    val id: Long = 0,
    val nombre: String,
    val destino: String,
    val fecha: String,          // ISO yyyy-MM-dd (launch date)
    val duracion: Int,          // days
    val estado: MissionStatus,
    val prioridad: Priority,
    val descripcion: String,
    val crewCount: Int = 0,
    val incidentCount: Int = 0,
)

data class Astronaut(
    val id: Long = 0,
    val nombre: String,
    val especialidad: Specialty,
    val experiencia: Int,       // years
    val estado: AstronautStatus,
    val missionCount: Int = 0,
)

data class Resource(
    val id: Long = 0,
    val nombre: String,
    val categoria: ResourceCategory,
    val cantidad: Int,          // quantity currently available in the general inventory
    val unidad: String,
    val assigned: Int = 0,      // total quantity assigned to missions
)

data class Incident(
    val id: Long = 0,
    val misionId: Long,
    val fecha: String,          // yyyy-MM-dd HH:mm
    val tipo: IncidentType,
    val gravedad: Severity,
    val descripcion: String,
    val misionNombre: String = "",
)

data class CrewMember(val misionId: Long, val astronaut: Astronaut, val rol: String)

data class MissionResource(val misionId: Long, val resource: Resource, val cantidad: Int)

data class AstronautMission(val mission: Mission, val rol: String)

data class Alert(val level: Severity, val title: String, val message: String, val missionId: Long? = null)

data class DashboardStats(
    val totalMissions: Int,
    val activeMissions: Int,
    val completedMissions: Int,
    val totalAstronauts: Int,
    val availableAstronauts: Int,
    val totalIncidents: Int,
    val criticalIncidents: Int,
    val totalResources: Int,
    val nextMission: Mission?,
    val missionsByStatus: Map<MissionStatus, Int>,
    val recentIncidents: List<Incident>,
    val alerts: List<Alert>,
)

enum class MissionSort(override val label: String) : Labeled {
    FECHA_ASC("Fecha (más antigua primero)"),
    FECHA_DESC("Fecha (más reciente primero)"),
    NOMBRE("Nombre (A-Z)"),
    PRIORIDAD("Prioridad (mayor primero)"),
    ESTADO("Estado"),
    DURACION("Duración (mayor primero)"),
}

enum class IncidentSort(override val label: String) : Labeled {
    FECHA_DESC("Fecha (más reciente primero)"),
    FECHA_ASC("Fecha (más antigua primero)"),
    GRAVEDAD("Gravedad (mayor primero)"),
}

/** Text normalization used by searches: lower case and without accents ("Órbita" -> "orbita"). */
object TextSearch {
    private val marks = Regex("""\p{M}+""")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase()

    /** LIKE pattern for the normalized text. '!' is the ESCAPE character, so typed % and _ are literal. */
    fun likePattern(search: String): String =
        "%" + normalize(search.trim()).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
}

/** Thrown for any business/validation rule violation. Its message is shown to the user. */
class ValidationException(message: String) : Exception(message)

object Dates {
    // STRICT: rejects impossible dates such as 2027-02-30 instead of silently adjusting them.
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
    private val DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun parseDate(text: String): LocalDate? =
        try { LocalDate.parse(text.trim(), DATE) } catch (_: DateTimeParseException) { null }

    fun parseDateTime(text: String): LocalDateTime? =
        try { LocalDateTime.parse(text.trim(), DATE_TIME) } catch (_: DateTimeParseException) { null }

    fun today(): String = LocalDate.now().format(DATE)
    fun now(): String = LocalDateTime.now().format(DATE_TIME)

    fun displayDate(iso: String): String = parseDate(iso)?.format(DISPLAY_DATE) ?: iso
    fun displayDateTime(iso: String): String = parseDateTime(iso)?.format(DISPLAY_DATE_TIME) ?: iso
}
