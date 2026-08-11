package kr.ac.sunmoon.hunminjeongeum_server

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kr.ac.sunmoon.hunminjeongeum_server.data.remote.countries.CountriesApiClient
import kotlinx.coroutines.runBlocking

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Hunminjeongeum_server",
    ) {
        App()
    }
}
