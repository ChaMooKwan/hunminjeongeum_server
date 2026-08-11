package kr.ac.sunmoon.hunminjeongeum_server.hint.logic

import java.io.PrintWriter
import java.net.Socket

class ClientConnection (
    val userName: String,
    val socket: Socket,
    val writer: PrintWriter
)
