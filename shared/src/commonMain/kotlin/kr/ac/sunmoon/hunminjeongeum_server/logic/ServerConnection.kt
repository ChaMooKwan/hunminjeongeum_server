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

    private val game = Game()
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

    private fun makeWaitingRoom(){
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

            } catch (e: Exception) {
                println("error in waitingRoom() : ${e.message}")
            }
        }
    }

    private fun addClient(socket: Socket){
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
                        startGame()
                    }
                    else if (message == "/hint,"){
                        // LLM API 사용해서 힌트 전송 "/hintAnswer,'
                    }
                    else if (message == "/chat,"){
                        val chatMessage = encodedMessage(message)
                        if (game.isStarted &&
                            chatMessage.message == game.questions[game.getQ()].word){
                            val i = room.getClients().indexOfFirst{
                                chatMessage.userName == it.userName
                            }
                            game.scores[i] = game.scores[i] + 10
                            broadcastScores(game.scores)
                            //문제 맞추는 이펙트 호출..? 은 클라이언트 쪽에서 알아서...
                            game.nextQ()
                            giveQuestion()
                        }
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

    private fun startGame() {
        CoroutineScope(Dispatchers.IO).launch { // 단어 불러오기
            game.getRandomQuiz(2,5)
            println("question added in the server!")
        }
        game.isStarted = true
        repeat(room.getSize()) { // 방인원 만큼 점수 리스트 초기화
            game.scores.add(0)
        }
        broadcast("/playGame,") // 다음 화면으로 넘어가라고 신호를 주는 것
        giveQuestion()
        startTimer()
    }


    // 시간을 세는 함수
    private fun timer(totalSeconds: Int = 300): Flow<Int> = flow {
        var remaining = totalSeconds

        while (remaining >= 0) {
            emit(remaining) // 현재 남은 시간 반환
            delay(1000)    // 1초 대기
            remaining--
        }
    }
    // 시간을 보내는 함수 문자열에 '/sync,'가 포함되어 있으면 다음에 오는 숫자는 동기화 시간으로(timer 변수)
    private fun startTimer(){
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
    private fun broadcastScores(scores: List<Int>) {
        val scoresMessage = StringBuilder("/score,")
        scores.forEach { score ->
            scoresMessage.append("$score,")
        }
        val scoreMessage = scoresMessage.dropLast(1).toString()
        broadcast(scoreMessage)
    }

    private fun encodedName(): String{
        val clients = room.getClients()
        var encodedNames: String = "/userNames,"
        for (client in clients) {
            encodedNames = encodedNames + client.userName + ","
        }
        encodedNames = encodedNames.dropLast(1)
        return encodedNames
    }

    private fun encodedMessage(message: String): ChatMessage{
        val list: List<String> = message.split(',')
        val chatMessage = ChatMessage(list[1],list[2])
        return chatMessage
    }

    private fun giveQuestion(){
        broadcast("/question,${game.questions[game.getQ()].wordQuiz}")
    }
}
