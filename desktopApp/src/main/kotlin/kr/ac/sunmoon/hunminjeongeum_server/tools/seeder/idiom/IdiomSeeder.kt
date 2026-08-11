package kr.ac.sunmoon.hunminjeongeum_server.tools.seeder.idiom

import kotlinx.coroutines.runBlocking
import kr.ac.sunmoon.hunminjeongeum_server.core.util.KoreanInitial
import kr.ac.sunmoon.hunminjeongeum_server.data.local.IdiomJsonReader
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordInsertDto
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordRepository

fun main() = runBlocking {
    val repository = QuizWordRepository()

    // Supabse 사자성어 카테고리 ID = 5
    val quizCategory = 5

    try {
        val allIdioms = IdiomJsonReader.readIdioms()

        val quizWords = allIdioms
            .map { idiom ->
                idiom.korean
            }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map { idiomName ->
                QuizWordInsertDto(
                    quizCategory = quizCategory,
                    word = idiomName,
                    wordQuiz = KoreanInitial.makeInitials(idiomName)
                )
            }

        println()
        println(" ==== TEST DB 저장 대상 사자성어 목록 ==== ")
        println("총 ${quizWords.size}개")
        println()

        quizWords.forEachIndexed { index, quizWord ->
            println(
                "${index+1}. ${quizWord.word} / ${quizWord.wordQuiz}"
            )
        }
        println()
        println("저장 시작: ${quizWords.size}개")

        repository.insertQuizWords(quizWords)

        println("DB 저장 성공: ${quizWords.size}개")
        println("저장완료")
    }catch(e: Exception){
        println("사자성어 데이터 저장 실패: ${e.message}")
        e.printStackTrace()
    }
}
























