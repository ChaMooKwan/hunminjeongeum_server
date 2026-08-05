package kr.ac.sunmoon.hunminjeongeum_server

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "hunminjeongeum_server",
    ) {
        App()
    }
}