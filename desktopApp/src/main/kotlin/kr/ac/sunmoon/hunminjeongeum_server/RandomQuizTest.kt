package kr.ac.sunmoon.hunminjeongeum_server

import kotlinx.coroutines.runBlocking
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordRepository

// DB엣 랜덤 국가 데이터 단어 받아오는 기능
fun main() = runBlocking {

    val repository = QuizWordRepository()
    // 카테고리 1 = 과일, 2 = 국가
    val quizCategory = 1
    val quiz = repository.getRandomQuizWord(quizCategory)

    if (quiz == null) {
        println("국가 문제가 없습니다.")
        return@runBlocking
    }

    println("===== 랜덤 문제 =====")
    println("ID : ${quiz.id}")
    println("카테고리 : ${quiz.quizCategory}")
    println("초성 : ${quiz.wordQuiz}")
    println("정답 : ${quiz.word}")
}
