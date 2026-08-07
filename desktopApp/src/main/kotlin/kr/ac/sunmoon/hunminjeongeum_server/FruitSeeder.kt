package kr.ac.sunmoon.hunminjeongeum_server


import kr.ac.sunmoon.hunminjeongeum_server.core.util.KoreanInitial
import kr.ac.sunmoon.hunminjeongeum_server.data.local.FruitJsonReader
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordInsertDto
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordRepository
import kotlinx.coroutines.runBlocking

// 과일 JSON 데이터를 읽어서 DB에 저장하는 기능
fun main() = runBlocking {

    val repository = QuizWordRepository()

    // Supabase category 테이블의 과일 카테고리 ID
    val quizCategory = 1   // 실제 과일 ID로 변경

    try {
        val allFruits = FruitJsonReader.read()

        val quizWords = allFruits
            .map { fruit ->
                fruit.korean
            }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map { fruitName ->
                QuizWordInsertDto(
                    quizCategory = quizCategory,
                    word = fruitName,
                    wordQuiz = KoreanInitial.makeInitials(fruitName)
                )
            }

        println()
        println("===== TEST DB 저장 대상 과일 목록 =====")
        println("총 ${quizWords.size}개")
        println()

        quizWords.forEachIndexed { index, quizWord ->
            println(
                "${index + 1}. ${quizWord.word} / ${quizWord.wordQuiz}"
            )
        }

        println()
        println("저장 시작: ${quizWords.size}개")

        repository.insertQuizWords(quizWords)

        println("DB 저장 성공: ${quizWords.size}개")
        println("저장 완료")

    } catch (e: Exception) {
        println("과일 데이터 저장 실패: ${e.message}")
        e.printStackTrace()
    }
}
