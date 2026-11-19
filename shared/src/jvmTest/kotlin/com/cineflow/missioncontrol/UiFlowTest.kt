package com.cineflow.missioncontrol

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.cineflow.missioncontrol.data.Database
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the real Compose screens (forms, dialogs, navigation) against a temporary SQLite database.
 * Native file pickers are the only part not automated: JSON import/export is covered in DataLayerTest.
 */
@OptIn(ExperimentalTestApi::class)
class UiFlowTest {

    private fun withApp(block: ComposeUiTest.(AppContainer) -> Unit) {
        val dir = Files.createTempDirectory("mc-ui")
        val container = AppContainer(Database(dir.resolve("ui.db")))
        try {
            runDesktopComposeUiTest(width = 1440, height = 900) {
                setContent { App(container) }
                waitForIdle()
                block(container)
            }
        } finally {
            container.close()
            dir.toFile().deleteRecursively()
        }
    }

    private fun ComposeUiTest.nav(label: String) {
        onAllNodesWithText(label).onFirst().performClick() // the sidebar entry is the first match
        waitForIdle()
    }

    private fun ComposeUiTest.type(label: String, text: String, clear: Boolean = false) {
        val node = onNodeWithText(label)
        if (clear) node.performTextClearance()
        node.performTextInput(text)
    }

    @Test
    fun everySectionShowsAnEmptyStateWithAnEmptyDatabase() = withApp {
        onNodeWithText("La base de datos está vacía").assertExists()
        onNodeWithText("Sin misiones próximas").assertExists()
        nav("Misiones"); onNodeWithText("Aún no hay misiones").assertExists()
        nav("Astronautas"); onNodeWithText("No hay astronautas registrados").assertExists()
        nav("Recursos"); onNodeWithText("Inventario vacío").assertExists()
        nav("Incidencias"); onNodeWithText("No hay misiones").assertExists()
        nav("Importar / Exportar"); onNodeWithText("Exportar a JSON").assertExists()
    }

    @Test
    fun fullMissionLifecycleThroughTheUi() = withApp { c ->
        val repo = c.repository

        // --- Validation: an empty form cannot be saved
        nav("Misiones")
        onNodeWithText("Nueva misión").performClick()
        onNodeWithText("Guardar").performClick()
        onNodeWithText("El nombre de la misión es obligatorio.", substring = true).assertExists()
        onNodeWithText("Cancelar").performClick()
        assertEquals(0, repo.getMissions().size)

        // --- Create two missions through the form
        fun createMission(name: String, destination: String) {
            onNodeWithText("Nueva misión").performClick()
            type("Nombre de la misión *", name)
            type("Destino *", destination)
            type("Fecha de lanzamiento *", "2027-05-10", clear = true)
            type("Duración (días) *", "20")
            type("Descripción / objetivo *", "Objetivo de prueba")
            onNodeWithText("Guardar").performClick()
            waitForIdle()
        }
        createMission("Prueba Lunar", "Luna")
        createMission("Ares Test", "Marte")
        assertEquals(2, repo.getMissions().size)
        onNodeWithText("Prueba Lunar").assertExists()
        onNodeWithText("Ares Test").assertExists()

        // --- Search (accent-insensitive) updates the list
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("LUNAR")
        waitForIdle()
        onNodeWithText("Prueba Lunar").assertExists()
        onNodeWithText("Ares Test").assertDoesNotExist()
        onAllNodes(hasSetTextAction()).onFirst().performTextClearance()
        waitForIdle()

        // --- Edit: the form is pre-filled, change the name
        onAllNodesWithContentDescription("Editar").onFirst().performClick()
        onNodeWithText("Editar misión").assertExists()
        onAllNodesWithText("Ares Test").assertCountEquals(2) // list row + current value loaded in the form
        type("Nombre de la misión *", "Ares Pathfinder Test", clear = true)
        onNodeWithText("Guardar").performClick()
        waitForIdle()
        assertTrue(repo.getMissions().any { it.nombre == "Ares Pathfinder Test" })

        // --- Astronaut and resource created through their forms
        nav("Astronautas")
        onNodeWithText("Nuevo astronauta").performClick()
        type("Nombre completo *", "Elena Vázquez")
        type("Experiencia (años) *", "12")
        onNodeWithText("Guardar").performClick()
        waitForIdle()
        onNodeWithText("Elena Vázquez").assertExists()

        nav("Recursos")
        onNodeWithText("Nuevo recurso").performClick()
        type("Nombre del recurso *", "Agua potable")
        type("Cantidad disponible *", "100")
        onNodeWithText("Guardar").performClick()
        waitForIdle()
        onNodeWithText("Agua potable").assertExists()

        // --- Mission detail: assign crew with role
        nav("Misiones")
        onNodeWithText("Ares Pathfinder Test").performClick()
        waitForIdle()
        onNodeWithText("Sin tripulación").assertExists()
        onNodeWithText("Sin recursos").assertExists()
        onNodeWithText("Sin incidencias").assertExists()
        val missionId = repo.getMissions().first { it.nombre == "Ares Pathfinder Test" }.id

        onAllNodesWithText("Asignar").onFirst().performClick()
        type("Rol en la misión *", "Comandante")
        onAllNodesWithText("Asignar").onLast().performClick()
        waitForIdle()
        onNodeWithText("Comandante").assertExists()
        assertEquals("Comandante", repo.getCrew(missionId).single().rol)

        // --- Assign resources: more than available is rejected, a valid amount updates the inventory
        onAllNodesWithText("Asignar")[1].performClick()
        type("Cantidad a asignar *", "150")
        onAllNodesWithText("Asignar").onLast().performClick()
        onNodeWithText("No puedes asignar 150", substring = true).assertExists()
        type("Cantidad a asignar *", "40", clear = true)
        onAllNodesWithText("Asignar").onLast().performClick()
        waitForIdle()
        assertEquals(60, repo.getResources().single().cantidad)
        assertEquals(40, repo.getMissionResources(missionId).single().cantidad)

        // --- Register an incident from the detail screen
        onNodeWithText("Registrar incidencia").performClick()
        type("Descripción *", "Fallo de prueba en el sensor")
        onNodeWithText("Guardar").performClick()
        waitForIdle()
        onNodeWithText("Fallo de prueba en el sensor").assertExists()
        assertEquals(1, repo.getIncidents(missionId = missionId).size)

        // --- Remove astronaut from the mission without deleting them
        onAllNodesWithContentDescription("Quitar de la misión").onFirst().performClick()
        onNodeWithText("Quitar").performClick()
        waitForIdle()
        assertTrue(repo.getCrew(missionId).isEmpty())
        assertEquals(1, repo.getAstronauts().size)

        // --- Delete the mission with confirmation: incidents go, resources return to inventory
        onAllNodesWithText("Eliminar").onFirst().performClick()
        onNodeWithText("¿Eliminar la misión?").assertExists()
        onAllNodesWithText("Eliminar").onLast().performClick()
        waitForIdle()
        assertEquals(1, repo.getMissions().size)
        assertEquals(100, repo.getResources().single().cantidad)
        assertEquals(0, repo.getIncidents().size)
        onNodeWithText("Prueba Lunar").assertExists() // back on the missions list
    }

    /**
     * Renders the real screens with the sample JSON loaded and saves PNGs to shared/build/screenshots.
     * Used to produce the README screenshots (copied by hand into docs/screenshots).
     */
    @Test
    fun renderScreenshotsWithSampleData() = withApp { c ->
        val sample = sequenceOf(File("../mission-control-sample.json"), File("mission-control-sample.json")).first { it.exists() }
        c.jsonService.import(sample, replaceExisting = false)
        val out = File("build/screenshots").apply { mkdirs() }
        fun shot(name: String) {
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
        nav("Misiones") // forces a refresh after the import
        nav("Panel de control"); shot("dashboard")
        nav("Misiones"); shot("missions")
        onNodeWithText("Ares Pathfinder").performClick(); shot("mission-detail")
        nav("Astronautas"); shot("astronauts")
        nav("Recursos"); shot("resources")
        nav("Incidencias"); shot("incidents")
        nav("Importar / Exportar"); shot("import-export")
        assertEquals(7, out.listFiles { f -> f.extension == "png" }!!.size)
    }
}
