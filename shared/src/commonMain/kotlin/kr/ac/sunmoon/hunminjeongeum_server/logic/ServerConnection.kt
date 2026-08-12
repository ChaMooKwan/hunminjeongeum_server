package kr.ac.sunmoon.hunminjeongeum_server.logic

import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kr.ac.sunmoon.hunminjeongeum_server.hint.getHint

class ServerConnection(
    private val port: Int = 9999,
    private val maxClients: Int = 7
) {
    private var serverSocket: ServerSocket? = null
    private lateinit var room: Room
    @Volatile
    private var running = true
    private val hints = mutableListOf<List<String>>()
    private var currentRound = -1

    private val game = Game()
    private var job: Job? = null
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
            game.userInfos.add(UserInfo(name,0))// 중복이지만 추가

            println("$name entered!")
            broadcast(encodedName())

            thread(isDaemon = true) {
                while (running) {
                    val message = reader.readLine() ?: break
                    if (message.contains("/startGame,")){
                        println("received /startGame")
                        val list = message.split(',')
                        startGame(list[1].toInt())
                    }
                    else if (message.contains("/chat,")){
                        val chatMessage = encodedMessage(message)
                        val currentQuestion = game.questions.getOrNull(game.getQ())
                        if (game.isStarted &&
                            currentQuestion != null &&
                            chatMessage.message == currentQuestion.word){
                            val i = game.userInfos.indexOfFirst{
                                chatMessage.userName == it.userName
                            }
                            if (i >= 0) {
                                game.userInfos[i].score += 10
                                broadcastScores(game.userInfos)
                                //문제 맞추는 이펙트 호출..? 은 클라이언트 쪽에서 알아서...
                                game.nextQ()
                                if (game.getQ() >= game.questions.size) {
                                    finishGame()
                                } else {
                                    giveQuestion()
                                }
                                print(message)
                            }
                        }
                        broadcastChat(chatMessage)
                    }
                }
            }

        } catch (e: Exception) {
            println("client error: ${e.message}")
        }
    }

    private fun startGame(category: Int) {
        if (game.isStarted) return
        CoroutineScope(Dispatchers.IO).launch { // 단어 불러오기
            game.getRandomQuiz(category,5)
            println("question added in the server!")
            game.isStarted = true
            broadcast("/playGame,$category") // 다음 화면으로 넘어가라고 신호를 주는 것
            giveQuestion()
            startTimer()
            for (i in 0..4){
                hints.add(getHint(game.questions[i]))
            }
        }
    }


    // 시간을 세는 함수
    private fun timer(totalSeconds: Int = 300): Flow<Int> = flow {
        var remaining = totalSeconds

        while (remaining >= 0 && game.isStarted) {
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
                if (time == 0) {
                    finishGame()
                }
            }
        }
    }
    private fun hintTimer(){
        job = CoroutineScope(Dispatchers.Default).launch {
            timer(60).collect { time ->
                if (time == 40) {
                    broadcast("/hint^easy^${hints[currentRound][0]}")
                }
                else if (time == 20){
                    broadcast("/hint^normal^${hints[currentRound][1]}")
                }
                else if (time == 0){
                    broadcast("/hint^hard^${hints[currentRound][2]}")
                }
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
    private fun broadcastScores(userInfos: List<UserInfo>) {
        val scoresMessage = StringBuilder("/score,")
        userInfos.forEach { userInfo ->
            scoresMessage.append("${userInfo.userName}${'$'}${userInfo.score},")
        }
        val scoreMessage = scoresMessage.dropLast(1).toString()
        broadcast(scoreMessage)
    }

    private fun broadcastChat(chatMessage: ChatMessage){
        val stringMessage = "/chat,${chatMessage.userName}&${chatMessage.message}"
        broadcast(stringMessage)
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
        val stringChatMessage = list[1].split("&")
        val chatMessage = ChatMessage(stringChatMessage[0],stringChatMessage[1])
        return chatMessage
    }

    private fun giveQuestion(){
        currentRound++
        if (job != null) job?.cancel()
        val question = game.questions.getOrNull(game.getQ()) ?: return
        broadcast("/question,${question.wordQuiz}")
        hintTimer()
    }

    private fun finishGame() {
        if (game.isOver) return
        game.isOver = true
        game.isStarted = false
        broadcast("/gameOver,")
        currentRound = -1
        hints.clear()
    }
}
