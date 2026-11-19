package com.cineflow.missioncontrol

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cineflow.missioncontrol.data.Database
import com.cineflow.missioncontrol.data.JsonService
import com.cineflow.missioncontrol.data.Repository
import com.cineflow.missioncontrol.data.ValidationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.sql.SQLException

/** Sections reachable from the sidebar plus the two detail screens. */
sealed interface Screen {
    data object Dashboard : Screen
    data object Missions : Screen
    data class MissionDetail(val id: Long) : Screen
    data object Astronauts : Screen
    data class AstronautDetail(val id: Long) : Screen
    data object Resources : Screen
    data object Incidents : Screen
    data object DataTransfer : Screen
}

/** Wires the database, repository and JSON service together. Created once in main(). */
class AppContainer(val database: Database = Database()) : AutoCloseable {
    val repository = Repository(database)
    val jsonService = JsonService(database, repository)
    override fun close() = database.close()
}

class MessageVisuals(override val message: String, val isError: Boolean) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = true
    override val duration: SnackbarDuration = if (isError) SnackbarDuration.Long else SnackbarDuration.Short
}

/**
 * UI-wide state: current screen, user messages and a [dataVersion] counter.
 * Every screen reads the database keyed on [dataVersion], so bumping it after a write refreshes all data.
 */
class AppState(
    val container: AppContainer,
    val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    val repo get() = container.repository
    val json get() = container.jsonService

    var screen by mutableStateOf<Screen>(Screen.Dashboard)
    var dataVersion by mutableIntStateOf(0)
        private set

    private val backStack = ArrayDeque<Screen>()

    fun navigate(to: Screen, root: Boolean = false) {
        if (root) backStack.clear() else backStack.addLast(screen)
        screen = to
    }

    fun back() {
        screen = backStack.removeLastOrNull() ?: Screen.Dashboard
    }

    fun refresh() { dataVersion++ }

    fun notify(message: String, isError: Boolean = false) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(MessageVisuals(message, isError))
        }
    }

    /**
     * Runs a database write. On success refreshes the data and shows [successMessage].
     * Returns null on success or the error message on failure (so forms can show it inline).
     */
    fun perform(successMessage: String?, block: () -> Unit): String? = try {
        block()
        refresh()
        successMessage?.let { notify(it) }
        null
    } catch (e: ValidationException) {
        e.message ?: "Operación no válida."
    } catch (e: SQLException) {
        "Error de base de datos: ${e.message}"
    }

    /** Like [perform] but shows any error as a message instead of returning it. */
    fun performOrNotify(successMessage: String?, block: () -> Unit) {
        perform(successMessage, block)?.let { notify(it, isError = true) }
    }
}
