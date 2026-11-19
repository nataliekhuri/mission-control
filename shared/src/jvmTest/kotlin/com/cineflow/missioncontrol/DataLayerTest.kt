package com.cineflow.missioncontrol

import com.cineflow.missioncontrol.data.Astronaut
import com.cineflow.missioncontrol.data.AstronautStatus
import com.cineflow.missioncontrol.data.Database
import com.cineflow.missioncontrol.data.Incident
import com.cineflow.missioncontrol.data.IncidentType
import com.cineflow.missioncontrol.data.JsonService
import com.cineflow.missioncontrol.data.Mission
import com.cineflow.missioncontrol.data.MissionStatus
import com.cineflow.missioncontrol.data.Priority
import com.cineflow.missioncontrol.data.Repository
import com.cineflow.missioncontrol.data.Resource
import com.cineflow.missioncontrol.data.ResourceCategory
import com.cineflow.missioncontrol.data.Severity
import com.cineflow.missioncontrol.data.Specialty
import com.cineflow.missioncontrol.data.ValidationException
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the SQLite layer and JSON import/export against a temporary database. */
class DataLayerTest {
    private val dir = Files.createTempDirectory("mc-test")
    private val db = Database(dir.resolve("test.db"))
    private val repo = Repository(db)
    private val json = JsonService(db, repo)

    @AfterTest
    fun cleanup() {
        db.close()
        dir.toFile().deleteRecursively()
    }

    private fun mission(name: String = "Test", status: MissionStatus = MissionStatus.PLANIFICADA) =
        Mission(0, name, "Luna", "2027-01-01", 10, status, Priority.ALTA, "Objetivo")

    @Test
    fun newDatabaseHasAllTablesAndNoData() {
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table'") { it.getString(1) }
        listOf("MISIONES", "ASTRONAUTAS", "RECURSOS", "INCIDENCIAS", "MISION_ASTRONAUTA", "MISION_RECURSO").forEach {
            assertTrue(it in tables, "missing $it")
            assertEquals(0, db.count("SELECT COUNT(*) FROM $it"))
        }
        assertEquals(1, db.count("PRAGMA foreign_keys"))
        val stats = repo.getDashboardStats()
        assertEquals(0, stats.totalMissions)
        assertNull(stats.nextMission)
    }

    @Test
    fun missionCrudSearchAndFilters() {
        val id = repo.insertMission(mission("Ares"))
        repo.insertMission(mission("Selene", MissionStatus.EN_CURSO).copy(prioridad = Priority.BAJA))
        assertFailsWith<ValidationException> { repo.insertMission(mission("ares")) } // duplicate name
        assertFailsWith<ValidationException> { repo.insertMission(mission("X").copy(fecha = "2027-13-45")) }
        assertEquals(1, repo.getMissions(search = "sel").size)
        assertEquals(1, repo.getMissions(status = MissionStatus.EN_CURSO, priority = Priority.BAJA).size)
        assertEquals(0, repo.getMissions(status = MissionStatus.EN_CURSO, priority = Priority.ALTA).size)
        repo.updateMission(repo.getMission(id)!!.copy(destino = "Marte"))
        assertEquals("Marte", repo.getMission(id)!!.destino)
        repo.deleteMission(id)
        assertNull(repo.getMission(id))
    }

    @Test
    fun resourceAssignmentRespectsStockAndIsReversible() {
        val m = repo.insertMission(mission())
        val r = repo.insertResource(Resource(0, "Agua", ResourceCategory.AGUA, 100, "litros"))
        repo.assignResource(m, r, 60)
        assertEquals(40, repo.getResource(r)!!.cantidad)
        assertFailsWith<ValidationException> { repo.assignResource(m, r, 41) }
        assertEquals(40, repo.getResource(r)!!.cantidad)
        repo.assignResource(m, r, 40)
        assertEquals(0, repo.getResource(r)!!.cantidad)
        assertEquals(100, repo.getMissionResources(m).single().cantidad)
        assertFailsWith<ValidationException> { repo.deleteResource(r) }
        repo.releaseResource(m, r)
        assertEquals(100, repo.getResource(r)!!.cantidad)
        repo.assignResource(m, r, 30)
        repo.deleteMission(m) // resources go back to inventory
        assertEquals(100, repo.getResource(r)!!.cantidad)
    }

    @Test
    fun crewAndIncidents() {
        val m = repo.insertMission(mission())
        val a = repo.insertAstronaut(Astronaut(0, "Elena", Specialty.PILOTO, 10, AstronautStatus.DISPONIBLE))
        val off = repo.insertAstronaut(Astronaut(0, "Off", Specialty.CIENCIA, 1, AstronautStatus.NO_DISPONIBLE))
        repo.assignAstronaut(m, a, "Comandante")
        assertFailsWith<ValidationException> { repo.assignAstronaut(m, a, "Otra vez") }
        assertFailsWith<ValidationException> { repo.assignAstronaut(m, off, "Científica") }
        assertEquals("Comandante", repo.getAstronautMissions(a).single().rol)
        repo.removeAstronautFromMission(m, a)
        assertTrue(repo.getCrew(m).isEmpty())
        assertTrue(repo.getAstronaut(a) != null)

        assertFailsWith<ValidationException> {
            repo.insertIncident(Incident(0, 9999, "2027-01-02 10:00", IncidentType.TECNICA, Severity.ALTA, "x"))
        }
        repo.insertIncident(Incident(0, m, "2027-01-02 10:00", IncidentType.TECNICA, Severity.ALTA, "Fallo"))
        assertEquals(1, repo.getIncidents(missionId = m).size)
        repo.deleteMission(m)
        assertEquals(0, repo.getIncidents().size)
    }

    @Test
    fun sampleJsonImportsAndRoundTrips() {
        val sample = sequenceOf(File("../mission-control-sample.json"), File("mission-control-sample.json")).first { it.exists() }
        val result = json.import(sample, replaceExisting = false)
        assertEquals(9, result.missions)
        assertEquals(10, result.astronauts)
        assertTrue(repo.getDashboardStats().alerts.isNotEmpty())
        // Importing again in "add" mode is rejected (duplicate names) and leaves the DB untouched.
        assertFailsWith<ValidationException> { json.import(sample, replaceExisting = false) }
        assertEquals(9, repo.getMissions().size)

        val exported = dir.resolve("export.json").toFile()
        val before = json.export(exported)
        json.import(exported, replaceExisting = true)
        val after = json.buildExport()
        assertEquals(before.misiones.map { it.copy(id = 0) }, after.misiones.map { it.copy(id = 0) })
        assertEquals(before.recursos.map { it.cantidadDisponible }, after.recursos.map { it.cantidadDisponible })
        assertEquals(before.tripulacion.size, after.tripulacion.size)
        assertEquals(before.recursosAsignados.size, after.recursosAsignados.size)
    }

    @Test
    fun invalidJsonIsRejectedWithoutChanges() {
        val bad = dir.resolve("bad.json").toFile()
        bad.writeText("{ not json")
        assertFailsWith<ValidationException> { json.import(bad, false) }
        bad.writeText("""{"misiones":[{"id":1,"nombre":"A","destino":"B","fecha":"2027-01-01","duracion":5,"estado":"VOLANDO","prioridad":"ALTA","descripcion":"d"}]}""")
        assertFailsWith<ValidationException> { json.import(bad, false) }
        bad.writeText("""{"misiones":[{"id":1,"nombre":"A","destino":"B","fecha":"2027-01-01","duracion":5,"estado":"PLANIFICADA","prioridad":"ALTA","descripcion":"d"}],
            "incidencias":[{"id":1,"misionId":7,"fecha":"2027-01-01 10:00","tipo":"TECNICA","gravedad":"ALTA","descripcion":"x"}]}""")
        val e = assertFailsWith<ValidationException> { json.import(bad, false) }
        assertTrue("inexistente" in e.message!!)
        assertEquals(0, repo.getMissions().size)
    }
}
