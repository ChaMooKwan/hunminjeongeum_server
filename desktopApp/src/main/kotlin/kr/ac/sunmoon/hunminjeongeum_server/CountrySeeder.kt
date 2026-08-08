package kr.ac.sunmoon.hunminjeongeum_server

import kr.ac.sunmoon.hunminjeongeum_server.core.util.KoreanInitial
import kr.ac.sunmoon.hunminjeongeum_server.data.remote.countries.CountriesApiClient
import kotlinx.coroutines.runBlocking
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordInsertDto
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordRepository

// 국가 데이터를 받아와서 DB에 저장하는 기능
fun main() = runBlocking {
    val countriesApi = CountriesApiClient(
        apiKey = "rc_live_62f9005831e44c2b9faf8fd375a778cd"
    )
    val repository = QuizWordRepository()

    // Supabase category 테이블의 국가 카테고리 ID
    val quizCategory = 2

    try {
        val allCountries = countriesApi.getAllCountries()

        val quizWords = allCountries
            .map { country ->
                country.names.translations["kor"]?.common
                    ?: country.names.common
            }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map { countryName ->
                QuizWordInsertDto(
                    quizCategory = quizCategory,
                    word = countryName,
                    wordQuiz = KoreanInitial.makeInitials(countryName)
                )
            }

        println()
        println("===== TEST DB 저장 대상 국가 목록 =====")
        println("총 ${quizWords.size}개")
        println()

        quizWords.forEachIndexed { index, quizWord ->
            println(
                "${index + 1}. $quizWord.word / ${quizWord.wordQuiz}"
            )
        }

        // Supabase에 여러 행 한 번에 저장
        println("저장 시작: ${quizWords.size}개")
        repository.insertQuizWords(quizWords)


        println("DB 저장 성공: ${quizWords.size}개")
        println("저장 완료")

    } catch (e: Exception) {
        println("API 호출 실패: ${e.message}")
        e.printStackTrace()
    }
}
