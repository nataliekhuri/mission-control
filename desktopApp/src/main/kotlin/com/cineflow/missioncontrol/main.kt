package com.cineflow.missioncontrol

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    // Opens (or creates on first launch) the SQLite database and its tables. No data is preloaded.
    val container = AppContainer()

    application {
        Window(
            onCloseRequest = {
                container.close()
                exitApplication()
            },
            title = "Mission Control",
            state = rememberWindowState(size = DpSize(1400.dp, 900.dp)),
        ) {
            window.minimumSize = java.awt.Dimension(1100, 700)
            App(container)
        }
    }
}
