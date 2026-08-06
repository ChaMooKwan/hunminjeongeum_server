package kr.ac.sunmoon.hunminjeongeum_server

import kotlinx.coroutines.runBlocking
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordRepository

// DB엣 랜덤 국가 데이터 단어 받아오는 기능
fun main() = runBlocking {

    val repository = QuizWordRepository()

    val quiz = repository.getRandomQuizWord(2)

    if (quiz == null) {
        println("국가 문제가 없습니다.")
        return@runBlocking
    }

    println("===== 랜덤 문제 =====")
    println("ID : ${quiz.id}")
    println("카테고리 : ${quiz.categoryId}")
    println("초성 : ${quiz.wordInitial}")
    println("정답 : ${quiz.word}")
}
