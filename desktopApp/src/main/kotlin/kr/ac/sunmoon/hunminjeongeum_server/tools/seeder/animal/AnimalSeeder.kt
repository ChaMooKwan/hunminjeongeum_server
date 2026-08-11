package kr.ac.sunmoon.hunminjeongeum_server.tools.seeder.animal

import kr.ac.sunmoon.hunminjeongeum_server.core.util.KoreanInitial
import kr.ac.sunmoon.hunminjeongeum_server.data.local.AnimalJsonReader
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordInsertDto
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordRepository
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {

    val repository = QuizWordRepository()

    // category 테이블의 동물 ID
    val quizCategory = 4

    try {

        val allAnimals = AnimalJsonReader.readAnimals()

        val quizWords = allAnimals
            .map { animal ->
                animal.korean
            }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it. length <= 5 }
            .distinct()
            .sorted()
            .map { animalName ->
                QuizWordInsertDto(
                    quizCategory = quizCategory,
                    word = animalName,
                    wordQuiz = KoreanInitial.makeInitials(animalName)
                )
            }

        println()
        println("===== TEST DB 저장 대상 동물 목록 =====")
        println("총 ${quizWords.size}개")
        println()

        quizWords.forEachIndexed { index, quizWord ->
            println("${index + 1}. ${quizWord.word} / ${quizWord.wordQuiz}")
        }

        println()
        println("저장 시작: ${quizWords.size}개")

        repository.insertQuizWords(quizWords)

        println("DB 저장 성공: ${quizWords.size}개")
        println("저장 완료")

    } catch (e: Exception) {
        println("동물 데이터 저장 실패: ${e.message}")
        e.printStackTrace()
    }
}
