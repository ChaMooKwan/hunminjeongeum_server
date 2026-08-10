package kr.ac.sunmoon.hunminjeongeum_server.tools.test.hint

import kotlinx.coroutines.runBlocking
import kr.ac.sunmoon.hunminjeongeum_server.hint.SupabaseWordRepository
import kotlin.random.Random

fun main() = runBlocking {

    val repository = SupabaseWordRepository()

    val category = "동물"

    // DB에서 해당 카테고리 단어 개수 확인
    val count = repository.countOf(category)

    println("===== DB 연동 테스트 =====")
    println("카테고리 : $category")
    println("단어 개수 : $count")

    // 랜덤 단어 하나 조회
    val word = repository.pickRandom(
        category = category,
        exclude = emptySet(),
        random = Random.Default
    )

    if (word == null) {
        println("단어를 가져오지 못했습니다.")
    } else {
        println()
        println("===== 랜덤 단어 =====")
        println("ID : ${word.id}")
        println("카테고리 ID : ${word.quizCategory}")
        println("단어 : ${word.word}")
        println("초성 : ${word.wordQuiz}")
    }
}
