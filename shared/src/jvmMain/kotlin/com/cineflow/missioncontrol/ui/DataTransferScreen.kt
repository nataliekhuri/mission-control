package com.cineflow.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cineflow.missioncontrol.AppState
import com.cineflow.missioncontrol.data.ValidationException
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun DataTransferScreen(state: AppState) {
    val current = remember(state.dataVersion) { state.json.buildExport() }
    var lastResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }

    fun doImport(replace: Boolean) {
        val file = chooseFile(save = false) ?: return
        try {
            val result = state.json.import(file, replace)
            state.refresh()
            val msg = "Importado desde ${file.name}: ${result.summary()}."
            lastResult = true to msg
            state.notify("Importación completada")
        } catch (e: ValidationException) {
            lastResult = false to (e.message ?: "Archivo no válido")
            state.notify("No se pudo importar el archivo", isError = true)
        } catch (e: Exception) {
            lastResult = false to "Error inesperado durante la importación: ${e.message}"
            state.notify("No se pudo importar el archivo", isError = true)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp)) {
        ScreenHeader("Importar / Exportar", "Intercambio de información entre SQLite y archivos JSON")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.weight(1f)) {
                SectionTitle("Exportar a JSON")
                Text("Genera un archivo JSON con toda la información almacenada actualmente en la base de datos.", color = Palette.TextMuted)
                Spacer(Modifier.height(14.dp))
                CountLine("Misiones", current.misiones.size)
                CountLine("Astronautas", current.astronautas.size)
                CountLine("Recursos", current.recursos.size)
                CountLine("Incidencias", current.incidencias.size)
                CountLine("Asignaciones de tripulación", current.tripulacion.size)
                CountLine("Asignaciones de recursos", current.recursosAsignados.size)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Exportar…", Icons.Default.Share) {
                    val file = chooseFile(save = true, defaultName = "mission-control-export.json") ?: return@PrimaryButton
                    try {
                        val data = state.json.export(file)
                        val total = data.misiones.size + data.astronautas.size + data.recursos.size + data.incidencias.size
                        lastResult = true to "Exportados $total registros principales a ${file.absolutePath}"
                        state.notify("Exportación completada")
                    } catch (e: ValidationException) {
                        lastResult = false to (e.message ?: "Error al exportar")
                        state.notify("No se pudo exportar", isError = true)
                    }
                }
            }

            Panel(Modifier.weight(1f)) {
                SectionTitle("Importar desde JSON")
                Text(
                    "Lee un archivo JSON compatible, lo valida (formato, campos obligatorios, fechas, valores permitidos y referencias entre registros) " +
                        "e inserta los datos en SQLite dentro de una transacción: si hay cualquier error no se guarda nada.",
                    color = Palette.TextMuted,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Importar y añadir…", Icons.Default.Done) { doImport(replace = false) }
                    SecondaryButton("Reemplazar todo…", Icons.Default.Refresh, color = Palette.Warning) { confirmReplace = true }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Para la demostración, importa el archivo mission-control-sample.json incluido en la carpeta del proyecto.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                )
            }
        }

        lastResult?.let { (ok, msg) ->
            Spacer(Modifier.height(16.dp))
            val color = if (ok) Palette.Success else Palette.Danger
            Panel(Modifier.fillMaxWidth()) {
                Row {
                    androidx.compose.material3.Icon(if (ok) Icons.Default.Done else Icons.Default.Warning, null, tint = color)
                    Spacer(Modifier.padding(4.dp))
                    Column {
                        Text(if (ok) "Operación completada" else "Operación rechazada", color = color, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(msg, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle("Base de datos")
            Text("Archivo SQLite:", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            Text(
                state.container.database.path.toAbsolutePath().toString(),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(Palette.SurfaceHigh).padding(8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tablas: MISIONES, ASTRONAUTAS, RECURSOS, INCIDENCIAS, MISION_ASTRONAUTA, MISION_RECURSO. " +
                    "Se crean automáticamente al iniciar la aplicación si no existen.",
                color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (confirmReplace) {
        ConfirmDialog(
            "¿Reemplazar todos los datos?",
            "Se borrarán TODAS las misiones, astronautas, recursos, incidencias y asignaciones actuales y se sustituirán por el contenido del archivo. " +
                "Si el archivo no es válido no se borrará nada.",
            confirmText = "Elegir archivo y reemplazar",
            onConfirm = { doImport(replace = true) },
            onDismiss = { confirmReplace = false },
        )
    }
}

@Composable
private fun CountLine(label: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Text("$count", color = Palette.Cyan)
    }
}

/** Native file chooser. Returns null if the user cancels. */
private fun chooseFile(save: Boolean, defaultName: String? = null): File? {
    val dialog = FileDialog(null as Frame?, if (save) "Exportar a JSON" else "Seleccionar archivo JSON", if (save) FileDialog.SAVE else FileDialog.LOAD)
    dialog.file = defaultName ?: "*.json"
    dialog.setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    val finalName = if (save && !name.endsWith(".json", ignoreCase = true)) "$name.json" else name
    return File(dir, finalName)
}
