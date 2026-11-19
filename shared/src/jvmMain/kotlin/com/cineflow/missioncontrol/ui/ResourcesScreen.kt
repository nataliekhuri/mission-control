package com.cineflow.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cineflow.missioncontrol.AppState
import com.cineflow.missioncontrol.data.Repository
import com.cineflow.missioncontrol.data.Resource
import com.cineflow.missioncontrol.data.ResourceCategory

@Composable
fun ResourcesScreen(state: AppState) {
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ResourceCategory?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Resource?>(null) }
    var deleting by remember { mutableStateOf<Resource?>(null) }

    val resources = remember(state.dataVersion, search, category) { state.repo.getResources(search, category) }
    val all = remember(state.dataVersion) { state.repo.getResources() }

    Column(Modifier.fillMaxSize().padding(32.dp)) {
        ScreenHeader("Recursos", "Inventario general · las cantidades asignadas a misiones se descuentan del disponible") {
            PrimaryButton("Nuevo recurso", Icons.Default.Add) { creating = true }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            SearchField(search, { search = it }, "Buscar recurso…", Modifier.weight(2f))
            SelectField("Categoría", ResourceCategory.entries, category, { category = it }, { it.label }, Modifier.weight(1f), noneLabel = "Todas")
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${resources.size} de ${all.size} recursos", color = Palette.TextMuted, modifier = Modifier.weight(1f))
            Badge("Agotados: ${all.count { it.cantidad == 0 }}", Palette.Danger)
            Badge("Stock bajo (≤${Repository.LOW_STOCK}): ${all.count { it.cantidad in 1..Repository.LOW_STOCK }}", Palette.Warning)
        }

        Panel(Modifier.fillMaxWidth().weight(1f), padding = 12.dp) {
            when {
                all.isEmpty() -> EmptyState(Icons.Default.Build, "Inventario vacío", "Añade recursos para poder asignarlos a las misiones.") {
                    PrimaryButton("Añadir recurso", Icons.Default.Add) { creating = true }
                }
                resources.isEmpty() -> EmptyState(Icons.Default.Search, "Sin resultados", "Ningún recurso coincide con la búsqueda.")
                else -> {
                    TableHeader(listOf("Recurso" to 2f, "Categoría" to 1.2f, "Disponible" to 1.2f, "Asignado a misiones" to 1.2f, "Estado" to 1f), trailingWidth = 80.dp)
                    LazyColumn {
                        items(resources, key = { it.id }) { r ->
                            TableRow {
                                Cell(r.nombre, 2f, bold = true, secondary = "#${r.id}")
                                Cell(r.categoria.label, 1.2f)
                                Cell("${r.cantidad} ${r.unidad}", 1.2f, color = Palette.Cyan, bold = true)
                                Cell("${r.assigned} ${r.unidad}", 1.2f, color = Palette.TextMuted)
                                Box(Modifier.weight(1f)) {
                                    when {
                                        r.cantidad == 0 -> Badge("Agotado", Palette.Danger)
                                        r.cantidad <= Repository.LOW_STOCK -> Badge("Stock bajo", Palette.Warning)
                                        else -> Badge("Disponible", Palette.Success)
                                    }
                                }
                                Row(Modifier.width(80.dp), horizontalArrangement = Arrangement.End) {
                                    ActionIcon(Icons.Default.Edit, "Editar / modificar cantidad") { editing = r }
                                    ActionIcon(Icons.Default.Delete, "Eliminar", Palette.Danger) { deleting = r }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) ResourceFormDialog(state, null) { creating = false }
    editing?.let { ResourceFormDialog(state, it) { editing = null } }
    deleting?.let { r ->
        ConfirmDialog(
            "¿Eliminar recurso?",
            "Se eliminará \"${r.nombre}\" del inventario. Si está asignado a alguna misión, primero deberás liberarlo.",
            onConfirm = { state.performOrNotify("Recurso eliminado") { state.repo.deleteResource(r.id) } },
            onDismiss = { deleting = null },
        )
    }
}
