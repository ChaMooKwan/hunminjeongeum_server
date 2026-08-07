package kr.ac.sunmoon.hunminjeongeum_server

import kotlinx.coroutines.runBlocking
import kr.ac.sunmoon.hunminjeongeum_server.core.util.KoreanInitial
import kr.ac.sunmoon.hunminjeongeum_server.data.remote.foods.FoodApiClient
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordInsertDto
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordRepository

fun main() = runBlocking {
    val api = FoodApiClient(
        serviceKey = "fzOJwdZ5WM%2BI3astAfBG18w4BQFFuZcJOXKcwrFNPvBPS%2BfyXAwwGXzgqu1D2pJF6g9FPeoEtQGghDtt2i1PiA%3D%3D"
    )

    val repository = QuizWordRepository()

    // Supabase category 테이블에서 음식 카테고리 ID
    val foodCategoryId = 3

    // 1. 음식 API 호출
    val response = api.getFoods()

    // 2. 음식 이름 정리
    val foodNames = response.body.items.item
        .map{
            it.foodNm
                .substringBefore("(")
                .trim()
        }
        .filter{it.isNotBlank()}
        .distinct()
        .sorted()
        .take(100)

    println("==== 저장할 음식 ====")
    println("총 ${foodNames.size}개")

    foodNames.forEachIndexed {index, foodName ->
        println("${index + 1}. $foodName")
    }

    // 3. DB저장 DTO로 변환
    val quizWords = foodNames.map {foodName ->
        QuizWordInsertDto(
            quizCategory = foodCategoryId,
            word = foodName,
            wordQuiz = KoreanInitial.makeInitials(foodName)
        )
    }

    // 4. Supabase 저장
    try{
        println()
        println("DB 저장 시작: ${quizWords.size}개")

        repository.insertQuizWords(quizWords)

        println("DB 저장 성공: ${quizWords.size}개")
    }catch(e:Exception){
        println("DB 저장 실패")
        println(e.message)

        e.printStackTrace()
    }


}
