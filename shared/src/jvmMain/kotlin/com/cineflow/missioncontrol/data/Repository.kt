package com.cineflow.missioncontrol.data

import com.cineflow.missioncontrol.data.Database.Companion.rank
import java.sql.ResultSet
import java.time.LocalDate

/**
 * All SQL access for the application. Public "business" methods validate their input and throw
 * [ValidationException] with a user friendly message; `*Row` methods are raw inserts reused by the JSON importer.
 */
class Repository(private val db: Database) {

    // ------------------------------------------------------------------ Mappers

    private fun ResultSet.toMission() = Mission(
        id = getLong("id"),
        nombre = getString("nombre"),
        destino = getString("destino"),
        fecha = getString("fecha"),
        duracion = getInt("duracion"),
        estado = MissionStatus.valueOf(getString("estado")),
        prioridad = Priority.valueOf(getString("prioridad")),
        descripcion = getString("descripcion"),
        crewCount = intOrZero("crew_count"),
        incidentCount = intOrZero("incident_count"),
    )

    private fun ResultSet.toAstronaut() = Astronaut(
        id = getLong("id"),
        nombre = getString("nombre"),
        especialidad = Specialty.valueOf(getString("especialidad")),
        experiencia = getInt("experiencia"),
        estado = AstronautStatus.valueOf(getString("estado")),
        missionCount = intOrZero("mission_count"),
    )

    private fun ResultSet.toResource() = Resource(
        id = getLong("id"),
        nombre = getString("nombre"),
        categoria = ResourceCategory.valueOf(getString("categoria")),
        cantidad = getInt("cantidad"),
        unidad = getString("unidad"),
        assigned = intOrZero("assigned"),
    )

    private fun ResultSet.toIncident() = Incident(
        id = getLong("id"),
        misionId = getLong("mision_id"),
        fecha = getString("fecha"),
        tipo = IncidentType.valueOf(getString("tipo")),
        gravedad = Severity.valueOf(getString("gravedad")),
        descripcion = getString("descripcion"),
        misionNombre = getString("mision_nombre") ?: "",
    )

    private fun ResultSet.intOrZero(column: String): Int =
        try { getInt(column) } catch (_: java.sql.SQLException) { 0 }

    // ------------------------------------------------------------------ Missions

    private val missionSelect = """
        SELECT m.*,
            (SELECT COUNT(*) FROM MISION_ASTRONAUTA ma WHERE ma.mision_id = m.id) AS crew_count,
            (SELECT COUNT(*) FROM INCIDENCIAS i WHERE i.mision_id = m.id) AS incident_count
        FROM MISIONES m
    """.trimIndent()

    /** Search + combinable filters + sorting, all resolved in SQL. */
    fun getMissions(
        search: String = "",
        status: MissionStatus? = null,
        priority: Priority? = null,
        sort: MissionSort = MissionSort.FECHA_ASC,
    ): List<Mission> {
        val where = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        if (search.isNotBlank()) {
            where += "(m.nombre LIKE ? OR m.destino LIKE ? OR m.descripcion LIKE ?)"
            val like = "%${search.trim()}%"
            repeat(3) { params += like }
        }
        if (status != null) { where += "m.estado = ?"; params += status }
        if (priority != null) { where += "m.prioridad = ?"; params += priority }
        val orderBy = when (sort) {
            MissionSort.FECHA_ASC -> "m.fecha ASC, m.nombre COLLATE NOCASE"
            MissionSort.FECHA_DESC -> "m.fecha DESC, m.nombre COLLATE NOCASE"
            MissionSort.NOMBRE -> "m.nombre COLLATE NOCASE ASC"
            MissionSort.PRIORIDAD -> "${rank<Priority>("m.prioridad")} DESC, m.fecha ASC"
            MissionSort.ESTADO -> "${rank<MissionStatus>("m.estado")} ASC, m.fecha ASC"
            MissionSort.DURACION -> "m.duracion DESC, m.fecha ASC"
        }
        val sql = buildString {
            append(missionSelect)
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY ").append(orderBy)
        }
        return db.query(sql, *params.toTypedArray()) { it.toMission() }
    }

    fun getMission(id: Long): Mission? =
        db.query("$missionSelect WHERE m.id = ?", id) { it.toMission() }.firstOrNull()

    fun insertMission(m: Mission): Long {
        validateMission(m)
        return insertMissionRow(m)
    }

    internal fun insertMissionRow(m: Mission): Long = db.insert(
        "INSERT INTO MISIONES (nombre, destino, fecha, duracion, estado, prioridad, descripcion) VALUES (?,?,?,?,?,?,?)",
        m.nombre.trim(), m.destino.trim(), m.fecha.trim(), m.duracion, m.estado, m.prioridad, m.descripcion.trim(),
    )

    fun updateMission(m: Mission) {
        validateMission(m)
        val rows = db.execute(
            "UPDATE MISIONES SET nombre=?, destino=?, fecha=?, duracion=?, estado=?, prioridad=?, descripcion=? WHERE id=?",
            m.nombre.trim(), m.destino.trim(), m.fecha.trim(), m.duracion, m.estado, m.prioridad, m.descripcion.trim(), m.id,
        )
        if (rows == 0) throw ValidationException("La misión ya no existe.")
    }

    /** Deletes a mission. Its assigned resources are returned to the inventory; crew links and incidents cascade. */
    fun deleteMission(id: Long) = db.transaction {
        db.execute(
            """
            UPDATE RECURSOS SET cantidad = cantidad +
                (SELECT mr.cantidad FROM MISION_RECURSO mr WHERE mr.recurso_id = RECURSOS.id AND mr.mision_id = ?)
            WHERE id IN (SELECT recurso_id FROM MISION_RECURSO WHERE mision_id = ?)
            """.trimIndent(), id, id,
        )
        db.execute("DELETE FROM MISION_RECURSO WHERE mision_id = ?", id)
        db.execute("DELETE FROM MISIONES WHERE id = ?", id)
    }

    fun missionNameExists(nombre: String, excludeId: Long = 0): Boolean =
        db.count("SELECT COUNT(*) FROM MISIONES WHERE nombre = ? COLLATE NOCASE AND id <> ?", nombre.trim(), excludeId) > 0

    private fun validateMission(m: Mission) {
        val errors = Validation.mission(m.nombre, m.destino, m.fecha, m.duracion, m.descripcion)
        if (errors.isNotEmpty()) throw ValidationException(errors.joinToString("\n"))
        if (missionNameExists(m.nombre, m.id)) throw ValidationException("Ya existe una misión llamada \"${m.nombre.trim()}\".")
    }

    // ------------------------------------------------------------------ Astronauts

    private val astronautSelect = """
        SELECT a.*, (SELECT COUNT(*) FROM MISION_ASTRONAUTA ma WHERE ma.astronauta_id = a.id) AS mission_count
        FROM ASTRONAUTAS a
    """.trimIndent()

    fun getAstronauts(search: String = "", status: AstronautStatus? = null): List<Astronaut> {
        val where = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        if (search.isNotBlank()) {
            // Search by name, or by specialty label typed by the user (e.g. "pilot", "medic").
            val matchingSpecialties = Specialty.entries.filter {
                it.label.contains(search.trim(), ignoreCase = true) || it.name.contains(search.trim(), ignoreCase = true)
            }
            val specClause = if (matchingSpecialties.isEmpty()) "" else
                " OR a.especialidad IN (${matchingSpecialties.joinToString(",") { "'${it.name}'" }})"
            where += "(a.nombre LIKE ?$specClause)"
            params += "%${search.trim()}%"
        }
        if (status != null) { where += "a.estado = ?"; params += status }
        val sql = buildString {
            append(astronautSelect)
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY a.nombre COLLATE NOCASE")
        }
        return db.query(sql, *params.toTypedArray()) { it.toAstronaut() }
    }

    fun getAstronaut(id: Long): Astronaut? =
        db.query("$astronautSelect WHERE a.id = ?", id) { it.toAstronaut() }.firstOrNull()

    fun insertAstronaut(a: Astronaut): Long {
        validateAstronaut(a)
        return insertAstronautRow(a)
    }

    internal fun insertAstronautRow(a: Astronaut): Long = db.insert(
        "INSERT INTO ASTRONAUTAS (nombre, especialidad, experiencia, estado) VALUES (?,?,?,?)",
        a.nombre.trim(), a.especialidad, a.experiencia, a.estado,
    )

    fun updateAstronaut(a: Astronaut) {
        validateAstronaut(a)
        val rows = db.execute(
            "UPDATE ASTRONAUTAS SET nombre=?, especialidad=?, experiencia=?, estado=? WHERE id=?",
            a.nombre.trim(), a.especialidad, a.experiencia, a.estado, a.id,
        )
        if (rows == 0) throw ValidationException("El astronauta ya no existe.")
    }

    /** Deleting an astronaut also removes them from every crew (ON DELETE CASCADE). */
    fun deleteAstronaut(id: Long) {
        db.execute("DELETE FROM ASTRONAUTAS WHERE id = ?", id)
    }

    private fun validateAstronaut(a: Astronaut) {
        val errors = Validation.astronaut(a.nombre, a.experiencia)
        if (errors.isNotEmpty()) throw ValidationException(errors.joinToString("\n"))
    }

    // ------------------------------------------------------------------ Crew (MISION_ASTRONAUTA)

    fun getCrew(missionId: Long): List<CrewMember> = db.query(
        """
        SELECT a.*, ma.rol FROM MISION_ASTRONAUTA ma
        JOIN ASTRONAUTAS a ON a.id = ma.astronauta_id
        WHERE ma.mision_id = ? ORDER BY a.nombre COLLATE NOCASE
        """.trimIndent(), missionId,
    ) { CrewMember(missionId, it.toAstronaut(), it.getString("rol")) }

    fun getAstronautMissions(astronautId: Long): List<AstronautMission> = db.query(
        """
        SELECT m.*, ma.rol FROM MISION_ASTRONAUTA ma
        JOIN MISIONES m ON m.id = ma.mision_id
        WHERE ma.astronauta_id = ? ORDER BY m.fecha DESC
        """.trimIndent(), astronautId,
    ) { AstronautMission(it.toMission(), it.getString("rol")) }

    fun assignAstronaut(missionId: Long, astronautId: Long, rol: String) {
        if (rol.isBlank()) throw ValidationException("Debes indicar el rol del astronauta en la misión.")
        if (rol.trim().length > 60) throw ValidationException("El rol no puede superar los 60 caracteres.")
        val mission = getMission(missionId) ?: throw ValidationException("La misión no existe.")
        val astronaut = getAstronaut(astronautId) ?: throw ValidationException("El astronauta no existe.")
        if (mission.estado == MissionStatus.COMPLETADA || mission.estado == MissionStatus.CANCELADA)
            throw ValidationException("No se puede modificar la tripulación de una misión ${mission.estado.label.lowercase()}.")
        if (astronaut.estado == AstronautStatus.NO_DISPONIBLE)
            throw ValidationException("${astronaut.nombre} no está disponible y no puede asignarse.")
        if (db.count("SELECT COUNT(*) FROM MISION_ASTRONAUTA WHERE mision_id=? AND astronauta_id=?", missionId, astronautId) > 0)
            throw ValidationException("${astronaut.nombre} ya forma parte de esta misión.")
        insertCrewRow(missionId, astronautId, rol)
    }

    internal fun insertCrewRow(missionId: Long, astronautId: Long, rol: String) {
        db.execute("INSERT INTO MISION_ASTRONAUTA (mision_id, astronauta_id, rol) VALUES (?,?,?)", missionId, astronautId, rol.trim())
    }

    /** Removes the astronaut from the mission only; the astronaut stays in the system. */
    fun removeAstronautFromMission(missionId: Long, astronautId: Long) {
        db.execute("DELETE FROM MISION_ASTRONAUTA WHERE mision_id=? AND astronauta_id=?", missionId, astronautId)
    }

    fun getAllCrew(): List<Triple<Long, Long, String>> =
        db.query("SELECT mision_id, astronauta_id, rol FROM MISION_ASTRONAUTA ORDER BY mision_id") {
            Triple(it.getLong(1), it.getLong(2), it.getString(3))
        }

    // ------------------------------------------------------------------ Resources

    private val resourceSelect = """
        SELECT r.*, COALESCE((SELECT SUM(mr.cantidad) FROM MISION_RECURSO mr WHERE mr.recurso_id = r.id), 0) AS assigned
        FROM RECURSOS r
    """.trimIndent()

    fun getResources(search: String = "", category: ResourceCategory? = null): List<Resource> {
        val where = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        if (search.isNotBlank()) { where += "r.nombre LIKE ?"; params += "%${search.trim()}%" }
        if (category != null) { where += "r.categoria = ?"; params += category }
        val sql = buildString {
            append(resourceSelect)
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY r.nombre COLLATE NOCASE")
        }
        return db.query(sql, *params.toTypedArray()) { it.toResource() }
    }

    fun getResource(id: Long): Resource? =
        db.query("$resourceSelect WHERE r.id = ?", id) { it.toResource() }.firstOrNull()

    fun insertResource(r: Resource): Long {
        validateResource(r)
        return insertResourceRow(r)
    }

    internal fun insertResourceRow(r: Resource): Long = db.insert(
        "INSERT INTO RECURSOS (nombre, categoria, cantidad, unidad) VALUES (?,?,?,?)",
        r.nombre.trim(), r.categoria, r.cantidad, r.unidad.trim(),
    )

    fun updateResource(r: Resource) {
        validateResource(r)
        val rows = db.execute(
            "UPDATE RECURSOS SET nombre=?, categoria=?, cantidad=?, unidad=? WHERE id=?",
            r.nombre.trim(), r.categoria, r.cantidad, r.unidad.trim(), r.id,
        )
        if (rows == 0) throw ValidationException("El recurso ya no existe.")
    }

    fun deleteResource(id: Long) {
        val inUse = db.count("SELECT COUNT(*) FROM MISION_RECURSO WHERE recurso_id = ?", id)
        if (inUse > 0) throw ValidationException(
            "El recurso está asignado a $inUse misión(es). Libéralo de esas misiones antes de eliminarlo."
        )
        db.execute("DELETE FROM RECURSOS WHERE id = ?", id)
    }

    fun resourceNameExists(nombre: String, excludeId: Long = 0): Boolean =
        db.count("SELECT COUNT(*) FROM RECURSOS WHERE nombre = ? COLLATE NOCASE AND id <> ?", nombre.trim(), excludeId) > 0

    private fun validateResource(r: Resource) {
        val errors = Validation.resource(r.nombre, r.cantidad, r.unidad)
        if (errors.isNotEmpty()) throw ValidationException(errors.joinToString("\n"))
        if (resourceNameExists(r.nombre, r.id)) throw ValidationException("Ya existe un recurso llamado \"${r.nombre.trim()}\".")
    }

    // ------------------------------------------------------------------ Mission resources (MISION_RECURSO)

    fun getMissionResources(missionId: Long): List<MissionResource> = db.query(
        """
        SELECT r.*, mr.cantidad AS asignada FROM MISION_RECURSO mr
        JOIN RECURSOS r ON r.id = mr.recurso_id
        WHERE mr.mision_id = ? ORDER BY r.nombre COLLATE NOCASE
        """.trimIndent(), missionId,
    ) { MissionResource(missionId, it.toResource(), it.getInt("asignada")) }

    /**
     * Moves [quantity] units of a resource from the general inventory to a mission, atomically.
     * Fails if there is not enough stock. Assigning the same resource twice accumulates the quantity.
     */
    fun assignResource(missionId: Long, resourceId: Long, quantity: Int) = db.transaction {
        if (quantity <= 0) throw ValidationException("La cantidad a asignar debe ser mayor que 0.")
        val mission = getMission(missionId) ?: throw ValidationException("La misión no existe.")
        if (mission.estado == MissionStatus.COMPLETADA || mission.estado == MissionStatus.CANCELADA)
            throw ValidationException("No se pueden asignar recursos a una misión ${mission.estado.label.lowercase()}.")
        val resource = getResource(resourceId) ?: throw ValidationException("El recurso no existe.")
        if (quantity > resource.cantidad) throw ValidationException(
            "No hay suficiente ${resource.nombre}: solicitado $quantity ${resource.unidad}, disponible ${resource.cantidad} ${resource.unidad}."
        )
        // Guarded decrement: the WHERE clause guarantees stock never goes negative.
        val updated = db.execute("UPDATE RECURSOS SET cantidad = cantidad - ? WHERE id = ? AND cantidad >= ?", quantity, resourceId, quantity)
        if (updated != 1) throw ValidationException("No hay suficiente cantidad disponible de ${resource.nombre}.")
        db.execute(
            """
            INSERT INTO MISION_RECURSO (mision_id, recurso_id, cantidad) VALUES (?,?,?)
            ON CONFLICT(mision_id, recurso_id) DO UPDATE SET cantidad = cantidad + excluded.cantidad
            """.trimIndent(), missionId, resourceId, quantity,
        )
    }

    /** Returns the assigned quantity of a resource from the mission back to the inventory. */
    fun releaseResource(missionId: Long, resourceId: Long) = db.transaction {
        val qty = db.query("SELECT cantidad FROM MISION_RECURSO WHERE mision_id=? AND recurso_id=?", missionId, resourceId) { it.getInt(1) }
            .firstOrNull() ?: throw ValidationException("El recurso no está asignado a esta misión.")
        db.execute("DELETE FROM MISION_RECURSO WHERE mision_id=? AND recurso_id=?", missionId, resourceId)
        db.execute("UPDATE RECURSOS SET cantidad = cantidad + ? WHERE id = ?", qty, resourceId)
    }

    internal fun insertMissionResourceRow(missionId: Long, resourceId: Long, quantity: Int) {
        db.execute("INSERT INTO MISION_RECURSO (mision_id, recurso_id, cantidad) VALUES (?,?,?)", missionId, resourceId, quantity)
    }

    fun getAllMissionResources(): List<Triple<Long, Long, Int>> =
        db.query("SELECT mision_id, recurso_id, cantidad FROM MISION_RECURSO ORDER BY mision_id") {
            Triple(it.getLong(1), it.getLong(2), it.getInt(3))
        }

    // ------------------------------------------------------------------ Incidents

    fun getIncidents(
        missionId: Long? = null,
        severity: Severity? = null,
        search: String = "",
        sort: IncidentSort = IncidentSort.FECHA_DESC,
    ): List<Incident> {
        val where = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        if (missionId != null) { where += "i.mision_id = ?"; params += missionId }
        if (severity != null) { where += "i.gravedad = ?"; params += severity }
        if (search.isNotBlank()) {
            where += "(i.descripcion LIKE ? OR m.nombre LIKE ?)"
            params += "%${search.trim()}%"; params += "%${search.trim()}%"
        }
        val orderBy = when (sort) {
            IncidentSort.FECHA_DESC -> "i.fecha DESC"
            IncidentSort.FECHA_ASC -> "i.fecha ASC"
            IncidentSort.GRAVEDAD -> "${rank<Severity>("i.gravedad")} DESC, i.fecha DESC"
        }
        val sql = buildString {
            append("SELECT i.*, m.nombre AS mision_nombre FROM INCIDENCIAS i JOIN MISIONES m ON m.id = i.mision_id")
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY ").append(orderBy)
        }
        return db.query(sql, *params.toTypedArray()) { it.toIncident() }
    }

    fun insertIncident(i: Incident): Long {
        validateIncident(i)
        return insertIncidentRow(i)
    }

    internal fun insertIncidentRow(i: Incident): Long = db.insert(
        "INSERT INTO INCIDENCIAS (mision_id, fecha, tipo, gravedad, descripcion) VALUES (?,?,?,?,?)",
        i.misionId, i.fecha.trim(), i.tipo, i.gravedad, i.descripcion.trim(),
    )

    fun updateIncident(i: Incident) {
        validateIncident(i)
        val rows = db.execute(
            "UPDATE INCIDENCIAS SET mision_id=?, fecha=?, tipo=?, gravedad=?, descripcion=? WHERE id=?",
            i.misionId, i.fecha.trim(), i.tipo, i.gravedad, i.descripcion.trim(), i.id,
        )
        if (rows == 0) throw ValidationException("La incidencia ya no existe.")
    }

    fun deleteIncident(id: Long) {
        db.execute("DELETE FROM INCIDENCIAS WHERE id = ?", id)
    }

    private fun validateIncident(i: Incident) {
        val errors = Validation.incident(i.fecha, i.descripcion)
        if (errors.isNotEmpty()) throw ValidationException(errors.joinToString("\n"))
        // Explicit check (the foreign key would also reject it) to give a clear message.
        if (getMission(i.misionId) == null) throw ValidationException("La misión asociada no existe.")
    }

    // ------------------------------------------------------------------ Dashboard

    fun getDashboardStats(): DashboardStats {
        val today = Dates.today()
        val byStatus = db.query("SELECT estado, COUNT(*) FROM MISIONES GROUP BY estado") {
            MissionStatus.valueOf(it.getString(1)) to it.getInt(2)
        }.toMap()
        val next = db.query(
            "$missionSelect WHERE m.estado IN ('PLANIFICADA','PREPARACION') AND m.fecha >= ? ORDER BY m.fecha ASC LIMIT 1", today,
        ) { it.toMission() }.firstOrNull()

        return DashboardStats(
            totalMissions = byStatus.values.sum(),
            activeMissions = (byStatus[MissionStatus.PREPARACION] ?: 0) + (byStatus[MissionStatus.EN_CURSO] ?: 0),
            completedMissions = byStatus[MissionStatus.COMPLETADA] ?: 0,
            totalAstronauts = db.count("SELECT COUNT(*) FROM ASTRONAUTAS"),
            availableAstronauts = db.count("SELECT COUNT(*) FROM ASTRONAUTAS WHERE estado = 'DISPONIBLE'"),
            totalIncidents = db.count("SELECT COUNT(*) FROM INCIDENCIAS"),
            criticalIncidents = db.count("SELECT COUNT(*) FROM INCIDENCIAS WHERE gravedad = 'CRITICA'"),
            totalResources = db.count("SELECT COUNT(*) FROM RECURSOS"),
            nextMission = next,
            missionsByStatus = byStatus,
            recentIncidents = getIncidents().take(5),
            alerts = computeAlerts(),
        )
    }

    /** Alerts derived from the stored data. Sorted from most to least severe. */
    private fun computeAlerts(): List<Alert> {
        val alerts = mutableListOf<Alert>()
        val today = LocalDate.now()
        val open = "('PLANIFICADA','PREPARACION','EN_CURSO')"

        // 1. Critical incidents in missions that are still open.
        db.query(
            """
            SELECT i.*, m.nombre AS mision_nombre FROM INCIDENCIAS i JOIN MISIONES m ON m.id = i.mision_id
            WHERE i.gravedad = 'CRITICA' AND m.estado IN $open ORDER BY i.fecha DESC
            """.trimIndent(),
        ) { it.toIncident() }.forEach {
            alerts += Alert(Severity.CRITICA, "Incidencia crítica en ${it.misionNombre}", it.descripcion, it.misionId)
        }

        // 2. Open missions without crew that are active or launch within 14 days.
        val soon = today.plusDays(14).format(Dates.DATE)
        db.query(
            """
            $missionSelect WHERE m.estado IN $open
              AND (m.estado IN ('PREPARACION','EN_CURSO') OR m.fecha <= ?)
              AND NOT EXISTS (SELECT 1 FROM MISION_ASTRONAUTA ma WHERE ma.mision_id = m.id)
            """.trimIndent(), soon,
        ) { it.toMission() }.forEach {
            val level = if (it.prioridad == Priority.CRITICA || it.estado == MissionStatus.EN_CURSO) Severity.ALTA else Severity.MEDIA
            alerts += Alert(level, "Misión sin tripulación", "${it.nombre} (${it.estado.label}, ${Dates.displayDate(it.fecha)}) no tiene astronautas asignados.", it.id)
        }

        // 3. Unavailable astronauts assigned to active missions.
        db.query(
            """
            SELECT a.nombre AS astronauta, m.nombre AS mision, m.id AS mision_id FROM MISION_ASTRONAUTA ma
            JOIN ASTRONAUTAS a ON a.id = ma.astronauta_id JOIN MISIONES m ON m.id = ma.mision_id
            WHERE a.estado = 'NO_DISPONIBLE' AND m.estado IN $open
            """.trimIndent(),
        ) { Triple(it.getString(1), it.getString(2), it.getLong(3)) }.forEach { (astronaut, mission, id) ->
            alerts += Alert(Severity.ALTA, "Tripulante no disponible", "$astronaut figura como no disponible pero forma parte de la tripulación de $mission.", id)
        }

        // 4. Planned missions whose launch date has already passed.
        db.query(
            "$missionSelect WHERE m.estado IN ('PLANIFICADA','PREPARACION') AND m.fecha < ? ORDER BY m.fecha", today.format(Dates.DATE),
        ) { it.toMission() }.forEach {
            alerts += Alert(Severity.MEDIA, "Fecha de lanzamiento superada", "${it.nombre} sigue ${it.estado.label.lowercase()} y su fecha era el ${Dates.displayDate(it.fecha)}.", it.id)
        }

        // 5. Inventory: out of stock / low stock.
        getResources().forEach {
            when {
                it.cantidad == 0 -> alerts += Alert(Severity.ALTA, "Recurso agotado", "${it.nombre} no tiene unidades disponibles en inventario.")
                it.cantidad <= LOW_STOCK -> alerts += Alert(Severity.MEDIA, "Stock bajo", "${it.nombre}: quedan ${it.cantidad} ${it.unidad}.")
            }
        }
        return alerts.sortedByDescending { it.level.ordinal }
    }

    // ------------------------------------------------------------------ Bulk

    /** Removes every record (used by "replace" JSON imports). Must run inside a transaction. */
    internal fun clearAll() {
        listOf("MISION_RECURSO", "MISION_ASTRONAUTA", "INCIDENCIAS", "MISIONES", "ASTRONAUTAS", "RECURSOS").forEach {
            db.execute("DELETE FROM $it")
        }
        db.execute("DELETE FROM sqlite_sequence")
    }

    companion object {
        const val LOW_STOCK = 10
    }
}

/** Field validation shared by the forms, the repository and the JSON importer. Returns error messages. */
object Validation {
    fun mission(nombre: String, destino: String, fecha: String, duracion: Int?, descripcion: String): List<String> = buildList {
        if (nombre.isBlank()) add("El nombre de la misión es obligatorio.")
        else if (nombre.trim().length > 80) add("El nombre no puede superar los 80 caracteres.")
        if (destino.isBlank()) add("El destino es obligatorio.")
        if (Dates.parseDate(fecha) == null) add("La fecha debe tener el formato AAAA-MM-DD y ser válida.")
        if (duracion == null || duracion <= 0) add("La duración debe ser un número entero de días mayor que 0.")
        else if (duracion > 3650) add("La duración no puede superar los 3650 días.")
        if (descripcion.isBlank()) add("La descripción (objetivo de la misión) es obligatoria.")
    }

    fun astronaut(nombre: String, experiencia: Int?): List<String> = buildList {
        if (nombre.isBlank()) add("El nombre del astronauta es obligatorio.")
        else if (nombre.trim().length > 80) add("El nombre no puede superar los 80 caracteres.")
        if (experiencia == null || experiencia < 0) add("La experiencia debe ser un número entero de años (0 o más).")
        else if (experiencia > 60) add("La experiencia no puede superar los 60 años.")
    }

    fun resource(nombre: String, cantidad: Int?, unidad: String): List<String> = buildList {
        if (nombre.isBlank()) add("El nombre del recurso es obligatorio.")
        if (cantidad == null || cantidad < 0) add("La cantidad disponible debe ser un número entero (0 o más).")
        if (unidad.isBlank()) add("La unidad es obligatoria (p. ej. kg, litros, unidades).")
    }

    fun incident(fecha: String, descripcion: String): List<String> = buildList {
        if (Dates.parseDateTime(fecha) == null) add("La fecha debe tener el formato AAAA-MM-DD HH:MM y ser válida.")
        if (descripcion.isBlank()) add("La descripción de la incidencia es obligatoria.")
    }
}
