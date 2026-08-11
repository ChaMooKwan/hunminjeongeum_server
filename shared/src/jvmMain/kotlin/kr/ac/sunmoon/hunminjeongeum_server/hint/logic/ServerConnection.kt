package kr.ac.sunmoon.hunminjeongeum_server.logic

import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kr.ac.sunmoon.hunminjeongeum_server.hint.hint.HintService
import kr.ac.sunmoon.hunminjeongeum_server.hint.hint.OpenAiHintGenerator
import kr.ac.sunmoon.hunminjeongeum_server.hint.hint.RoundHints
import kr.ac.sunmoon.hunminjeongeum_server.hint.hint.SupabaseWordRepository
import kr.ac.sunmoon.hunminjeongeum_server.hint.logic.HintData



class ServerConnection(
    private val port: Int = 9999,
    private val maxClients: Int = 7
) {
    private var serverSocket: ServerSocket? = null
    private lateinit var room: Room
    @Volatile
    private var running = true
    private val game = Game()
    // 힌트를 생성할 ai 모델을 가져오는 속성
    private val generator = OpenAiHintGenerator(
        apiKey = System.getenv("OPENAI_API_KEY")
            ?: error("OPENAI_API_KEY가 설정되지 않았습니다."),
        model = System.getenv("OPENAI_MODEL")
            ?: "gpt-4.1-nano"
    )

    // 힌트 서비스 객체 생성, repository와 generator을 제외한 나머지 값을 클래스 내의 기본값을 사용함
    // repository는 현재 사용자에게 문제를 전달받고 힌트를 생성하기에 필요 없지만, 생성자 명시 상 추가함
    private val hintService = HintService(SupabaseWordRepository(), generator)

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
                    // LLM API 사용해서 힌트 전송 "/hintAnswer,'
                    else if (message.startsWith("/hint,")) {
                        // 전달받은 문제정보 문자열을 HintData 객체로 변환
                        val hintData : HintData? = decodeHintMessage(message)
                        if (hintData != null) {
                            CoroutineScope(Dispatchers.IO).launch {

                                // 힌트 생성로직에 문제 정보를 전달, 힌트 목록 객체를 받아옴
                                val roundHints = hintService.generate(
                                    word = hintData.word,
                                    wordQuiz = hintData.wordQuiz,
                                    category = hintData.category
                                )
                                // 힌트 목록 객체를 사용자에게 보낼 '/hintAnswer, 힌트1, 힌트2...'로 변환
                                val response = encodeHints(roundHints)
                                // 전체 사용자들에게 전달
                                broadcast(response)
                            }
                        }
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

    // 클라이언트에게 받은 문제 정보를 HintData라는 데이터 클래스로 변환
    private fun decodeHintMessage(
        message: String
    ): HintData? {
        val parts = message.split(",", limit = 4)

        if (parts.size != 4) {
            return null
        }

        val category =
            parts[3].trim().toIntOrNull()
                ?: return null

        return HintData(
            word = parts[1].trim(),
            wordQuiz = parts[2].trim(),
            category = category
        )
    }

    // 힌트 데이터를 클라이언트에게 보낼 문자열 데이터로 변환
    private fun encodeHints(
        roundHints: RoundHints
    ): String {

        /*  roundHint의 timeline으로 시간대별 초성, 특징 힌트들을 가져옴,
        그리고 map으로 힌트 문자열만 가져와 저장
        ex) "동아시아에 위치합니다", "ㄷ한ㅁㄱ", "수도는 서울입니다"... */
        val hints =
            roundHints.timeline()
                .map { it.text }

        return "/hintAnswer," +
            hints.joinToString(",")
    }

    private fun encodedMessage(message: String): ChatMessage{
        val list: List<String> = message.split(',')
        val chatMessage = ChatMessage(list[1],list[2])
        // 반환된 메시지 : 이름,내용
        return chatMessage
    }



    private fun giveQuestion(){
        broadcast("/question,${game.questions[game.getQ()].wordQuiz}")
    }
}
