package com.cineflow.missioncontrol.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

// ---------------------------------------------------------------------------------------------
// JSON document format. Ids inside the file are local references used to link records together;
// on import new SQLite ids are generated and every relationship is remapped.
// `recursos[].cantidadDisponible` is the stock remaining in the inventory (already discounted
// from the quantities listed in `recursosAsignados`), so export -> import round-trips exactly.
// ---------------------------------------------------------------------------------------------

@Serializable
data class MissionControlData(
    val version: Int = 1,
    val exportadoEn: String? = null,
    val misiones: List<MisionJson> = emptyList(),
    val astronautas: List<AstronautaJson> = emptyList(),
    val recursos: List<RecursoJson> = emptyList(),
    val incidencias: List<IncidenciaJson> = emptyList(),
    val tripulacion: List<TripulanteJson> = emptyList(),
    val recursosAsignados: List<RecursoAsignadoJson> = emptyList(),
)

@Serializable
data class MisionJson(
    val id: Long,
    val nombre: String,
    val destino: String,
    val fecha: String,
    val duracion: Int,
    val estado: MissionStatus,
    val prioridad: Priority,
    val descripcion: String,
)

@Serializable
data class AstronautaJson(
    val id: Long,
    val nombre: String,
    val especialidad: Specialty,
    val experiencia: Int,
    val estado: AstronautStatus,
)

@Serializable
data class RecursoJson(
    val id: Long,
    val nombre: String,
    val categoria: ResourceCategory,
    val cantidadDisponible: Int,
    val unidad: String,
)

@Serializable
data class IncidenciaJson(
    val id: Long,
    val misionId: Long,
    val fecha: String,
    val tipo: IncidentType,
    val gravedad: Severity,
    val descripcion: String,
)

@Serializable
data class TripulanteJson(val misionId: Long, val astronautaId: Long, val rol: String)

@Serializable
data class RecursoAsignadoJson(val misionId: Long, val recursoId: Long, val cantidad: Int)

data class ImportResult(
    val missions: Int,
    val astronauts: Int,
    val resources: Int,
    val incidents: Int,
    val crew: Int,
    val missionResources: Int,
) {
    fun summary() = "$missions misiones, $astronauts astronautas, $resources recursos, $incidents incidencias, " +
        "$crew asignaciones de tripulación y $missionResources asignaciones de recursos"
}

class JsonService(private val db: Database, private val repo: Repository) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ------------------------------------------------------------------ Export

    fun buildExport(): MissionControlData = MissionControlData(
        version = 1,
        exportadoEn = Dates.now(),
        misiones = repo.getMissions(sort = MissionSort.FECHA_ASC).sortedBy { it.id }.map {
            MisionJson(it.id, it.nombre, it.destino, it.fecha, it.duracion, it.estado, it.prioridad, it.descripcion)
        },
        astronautas = repo.getAstronauts().sortedBy { it.id }.map {
            AstronautaJson(it.id, it.nombre, it.especialidad, it.experiencia, it.estado)
        },
        recursos = repo.getResources().sortedBy { it.id }.map {
            RecursoJson(it.id, it.nombre, it.categoria, it.cantidad, it.unidad)
        },
        incidencias = repo.getIncidents(sort = IncidentSort.FECHA_ASC).map {
            IncidenciaJson(it.id, it.misionId, it.fecha, it.tipo, it.gravedad, it.descripcion)
        },
        tripulacion = repo.getAllCrew().map { (m, a, rol) -> TripulanteJson(m, a, rol) },
        recursosAsignados = repo.getAllMissionResources().map { (m, r, q) -> RecursoAsignadoJson(m, r, q) },
    )

    fun export(file: File): MissionControlData {
        val data = buildExport()
        try {
            file.writeText(json.encodeToString(MissionControlData.serializer(), data), Charsets.UTF_8)
        } catch (e: IOException) {
            throw ValidationException("No se pudo escribir el archivo: ${e.message}")
        }
        return data
    }

    // ------------------------------------------------------------------ Import

    /** Read -> deserialize -> validate -> insert in a single transaction. Nothing is written if any step fails. */
    fun import(file: File, replaceExisting: Boolean): ImportResult {
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            throw ValidationException("No se pudo leer el archivo: ${e.message}")
        }
        val data = parse(text)
        val errors = validate(data, checkAgainstDatabase = !replaceExisting)
        if (errors.isNotEmpty()) {
            val shown = errors.take(8).joinToString("\n• ", prefix = "• ")
            val more = if (errors.size > 8) "\n… y ${errors.size - 8} errores más." else ""
            throw ValidationException("El archivo JSON no es válido:\n$shown$more")
        }
        return db.transaction {
            if (replaceExisting) repo.clearAll()
            insert(data)
        }
    }

    fun parse(text: String): MissionControlData {
        if (text.isBlank()) throw ValidationException("El archivo está vacío.")
        return try {
            json.decodeFromString(MissionControlData.serializer(), text)
        } catch (e: SerializationException) {
            throw ValidationException("El archivo no tiene un formato JSON compatible con Mission Control.\n${e.message?.lineSequence()?.firstOrNull() ?: ""}")
        } catch (e: IllegalArgumentException) {
            throw ValidationException("El archivo no tiene un formato JSON compatible con Mission Control.\n${e.message ?: ""}")
        }
    }

    fun validate(data: MissionControlData, checkAgainstDatabase: Boolean): List<String> = buildList {
        val total = data.misiones.size + data.astronautas.size + data.recursos.size + data.incidencias.size
        if (total == 0) add("El archivo no contiene misiones, astronautas, recursos ni incidencias.")

        fun <T> duplicates(items: List<T>, key: (T) -> Any): Set<Any> =
            items.groupBy(key).filterValues { it.size > 1 }.keys

        duplicates(data.misiones) { it.id }.forEach { add("Id de misión duplicado: $it") }
        duplicates(data.astronautas) { it.id }.forEach { add("Id de astronauta duplicado: $it") }
        duplicates(data.recursos) { it.id }.forEach { add("Id de recurso duplicado: $it") }
        duplicates(data.misiones) { it.nombre.trim().lowercase() }.forEach { add("Nombre de misión repetido: \"$it\"") }
        duplicates(data.recursos) { it.nombre.trim().lowercase() }.forEach { add("Nombre de recurso repetido: \"$it\"") }

        data.misiones.forEach { m ->
            Validation.mission(m.nombre, m.destino, m.fecha, m.duracion, m.descripcion)
                .forEach { add("Misión ${m.id} (${m.nombre.ifBlank { "sin nombre" }}): $it") }
            if (checkAgainstDatabase && m.nombre.isNotBlank() && repo.missionNameExists(m.nombre))
                add("Ya existe en la base de datos una misión llamada \"${m.nombre.trim()}\".")
        }
        data.astronautas.forEach { a ->
            Validation.astronaut(a.nombre, a.experiencia).forEach { add("Astronauta ${a.id}: $it") }
        }
        data.recursos.forEach { r ->
            Validation.resource(r.nombre, r.cantidadDisponible, r.unidad).forEach { add("Recurso ${r.id}: $it") }
            if (checkAgainstDatabase && r.nombre.isNotBlank() && repo.resourceNameExists(r.nombre))
                add("Ya existe en la base de datos un recurso llamado \"${r.nombre.trim()}\".")
        }

        val missionIds = data.misiones.map { it.id }.toSet()
        val astronautIds = data.astronautas.map { it.id }.toSet()
        val resourceIds = data.recursos.map { it.id }.toSet()

        data.incidencias.forEach { i ->
            if (i.misionId !in missionIds) add("La incidencia ${i.id} hace referencia a una misión inexistente (${i.misionId}).")
            Validation.incident(i.fecha, i.descripcion).forEach { add("Incidencia ${i.id}: $it") }
        }
        data.tripulacion.forEach { t ->
            if (t.misionId !in missionIds) add("Tripulación: la misión ${t.misionId} no existe en el archivo.")
            if (t.astronautaId !in astronautIds) add("Tripulación: el astronauta ${t.astronautaId} no existe en el archivo.")
            if (t.rol.isBlank()) add("Tripulación: falta el rol del astronauta ${t.astronautaId} en la misión ${t.misionId}.")
        }
        duplicates(data.tripulacion) { it.misionId to it.astronautaId }
            .forEach { add("Tripulación: asignación repetida $it") }
        data.recursosAsignados.forEach { r ->
            if (r.misionId !in missionIds) add("Recursos asignados: la misión ${r.misionId} no existe en el archivo.")
            if (r.recursoId !in resourceIds) add("Recursos asignados: el recurso ${r.recursoId} no existe en el archivo.")
            if (r.cantidad <= 0) add("Recursos asignados: la cantidad del recurso ${r.recursoId} en la misión ${r.misionId} debe ser mayor que 0.")
        }
        duplicates(data.recursosAsignados) { it.misionId to it.recursoId }
            .forEach { add("Recursos asignados: asignación repetida $it") }
    }

    /** Inserts all records, translating file ids to the new SQLite ids. Caller provides the transaction. */
    private fun insert(data: MissionControlData): ImportResult {
        val missionMap = data.misiones.associate { m ->
            m.id to repo.insertMissionRow(Mission(0, m.nombre, m.destino, m.fecha, m.duracion, m.estado, m.prioridad, m.descripcion))
        }
        val astronautMap = data.astronautas.associate { a ->
            a.id to repo.insertAstronautRow(Astronaut(0, a.nombre, a.especialidad, a.experiencia, a.estado))
        }
        val resourceMap = data.recursos.associate { r ->
            r.id to repo.insertResourceRow(Resource(0, r.nombre, r.categoria, r.cantidadDisponible, r.unidad))
        }
        data.incidencias.forEach { i ->
            repo.insertIncidentRow(Incident(0, missionMap.getValue(i.misionId), i.fecha, i.tipo, i.gravedad, i.descripcion))
        }
        data.tripulacion.forEach { t ->
            repo.insertCrewRow(missionMap.getValue(t.misionId), astronautMap.getValue(t.astronautaId), t.rol)
        }
        data.recursosAsignados.forEach { r ->
            repo.insertMissionResourceRow(missionMap.getValue(r.misionId), resourceMap.getValue(r.recursoId), r.cantidad)
        }
        return ImportResult(
            missions = data.misiones.size,
            astronauts = data.astronautas.size,
            resources = data.recursos.size,
            incidents = data.incidencias.size,
            crew = data.tripulacion.size,
            missionResources = data.recursosAsignados.size,
        )
    }
}
