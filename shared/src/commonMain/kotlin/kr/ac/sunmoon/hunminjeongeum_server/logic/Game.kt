package kr.ac.sunmoon.hunminjeongeum_server.logic

import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordDto
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordRepository

class Game {
    var questions: MutableList<QuizWordDto> = mutableListOf()
    private var index = 0
    var isStarted = false

    suspend fun getRandomQuiz(category: Int, times: Int): List<QuizWordDto> {
        val repository = QuizWordRepository()
        val list = mutableListOf<QuizWordDto>()
        repeat(times){
            val question = repository.getRandomQuizWord(category) ?: QuizWordDto(1,1,"1","게임 클래스 확인")
            list.add(question)
        }
        return list
    }

    fun getQ(): Int{
        return index
    }
    fun nextQ(){
        index++
    }

}
