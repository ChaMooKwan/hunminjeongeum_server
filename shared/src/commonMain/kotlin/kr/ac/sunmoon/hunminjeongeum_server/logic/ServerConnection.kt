package kr.ac.sunmoon.hunminjeongeum_server.logic

import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ServerConnection(
    private val port: Int = 9999,
    private val maxClients: Int = 7
) {
    private var serverSocket: ServerSocket? = null
    private lateinit var room: Room
    @Volatile
    private var running = true

    fun start() {
        serverSocket = ServerSocket(port)
        println("=== Chatting Server Start ===")
        println("port: $port")
        println("max clients: $maxClients")

        room = Room()
        println("Room Created")

        makeWaitingRoom()
        println("Waiting Room Created")
    }

    fun makeWaitingRoom(){
        val server = serverSocket ?: return

        while (true) {
            try {
                val socket = server.accept()

                if (room.getSize() >= maxClients) {
                    PrintWriter(socket.getOutputStream(), true).use {
                        it.println("[SERVER] max Clients.")
                    }
                    socket.close()
                    continue
                }

                addClient(socket)

            } catch(e: Exception) {
                println("error in waitingRoom() : ${e.message}")
            }
        }
    }

    fun addClient(socket: Socket){
        val reader = socket.getInputStream().bufferedReader()
        val writer = PrintWriter(socket.getOutputStream(), true)
        var client: ClientConnection? = null

        try{
            val name = reader.readLine()
            client = ClientConnection(name, socket, writer)
            room.add(client)

            println("$name entered!")
            broadcast(encodedName())

            thread(isDaemon = true) {
                while (running) {
                    val message = reader.readLine()
                    if (message == "/startGame,"){
                        // SQL 사용해서 DB에서 단어 5개 끌어오기. 이후 그걸 클라이언트에게 전송 // '/question,'로 감
                        // 타이머 '/timer,'로 1초간격으로 전송
                        // 이 모든걸 startGame() 호출로 처리
                        startGame()
                    }
                    else if (message == "/hint,"){
                        // LLM API 사용해서 힌트 전송 "/hintAnswer,'
                    }
                }
            }

        } catch (e: Exception) {
            println("client error: ${e.message}")
        } finally {
            if (client != null){
                room.remove(client)
                println("Something went wrong during adding client!")
            }
        }
    }

    fun startGame() {
        playGame()
    }

    fun playGame() {
        /*
        클라이언트는 대기창에서 스레드로 시작신호(/playGame)을 수신대기하다가 수신하면 다음 게임 창으로 넘어감
        [클라이언트가 받으면 채팅 말고 다른 행동을 하게되는 메시지]
        1. /playGame : 게임 시작 화면으로 전환
        2. '/timer,'가 포함된 문자열: '/timer,'를 삭제하고 남은 시간을 초단위로 받아 timer 변수에 반영
        3.
         */
        // 단어 불러오기
        broadcast("/playGame,")

        startTimer()
    }

    // 시간을 세는 함수
    fun timer(totalSeconds: Int = 300): Flow<Int> = flow {
        var remaining = totalSeconds

        while (remaining >= 0) {
            emit(remaining) // 현재 남은 시간 반환
            delay(1000)    // 1초 대기
            remaining--
        }
    }
    // 시간을 보내는 함수 문자열에 '/sync,'가 포함되어 있으면 다음에 오는 숫자는 동기화 시간으로(timer 변수)
    fun startTimer(){
        CoroutineScope(Dispatchers.Default).launch {
            timer().collect { time ->
                broadcast("/timer,${time}")
            }
        }
    }

    private fun broadcast(message: String) {
        val clients = room.getClients()
        for (client in clients) {
            try {
                client.writer.println(message)
            } catch (e: Exception) {
                println("[SERVER] broadcast() error: ${client.userName}")
            }
        }
    }

    private fun encodedName(): String{
        val clients = room.getClients()
        var encodedNames: String = ""
        for (client in clients) {
            encodedNames = encodedNames + client.userName + ","
        }
        encodedNames = encodedNames.dropLast(1)
        return encodedNames
    }
}
