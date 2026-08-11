package kr.ac.sunmoon.hunminjeongeum_server.tools.seeder.food

import kotlinx.coroutines.runBlocking
import kr.ac.sunmoon.hunminjeongeum_server.core.util.KoreanInitial
import kr.ac.sunmoon.hunminjeongeum_server.data.remote.foods.FoodApiClient
import kr.ac.sunmoon.hunminjeongeum_server.data.remote.foods.FoodItem
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordInsertDto
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordRepository

fun main() = runBlocking {
    val serviceKey = System.getenv("FOOD_API_KEY")
        ?: error("FOOD_API_KEY 환경변수가 설정되지 않았습니다.")

    val api = FoodApiClient(
        serviceKey = serviceKey
    )

    val repository = QuizWordRepository()

    // Supabase category 테이블에서 음식 카테고리 ID
    val foodCategoryId = 3
    // 여러 페이지에서 받은 음식 데이터 누적
    val allitems = mutableListOf<FoodItem>()

    // 1. 1~10페이지까지 요청
    for(page in 1..10){
        println()
        println(" ===== ${page}페이지 요청 ===== ")

        val response = api.getFoods(
            pageNo = page,
            numOfRows = 100
        )
        if(response == null){
            println("${page}페이지 API 응답 실패")
            continue
        }

        val items = response.body.items.item

        allitems.addAll(items)

        println("${page}페이지 받은 개수 : ${items.size}")
        println("현재 누적 개수 : ${allitems.size}")
    }

    // 2. 음식 이름 정리
    val foodNames = allitems
        .map{
            it.foodNm
                .substringBefore("(")
                .trim()
        }
        .filter{it.isNotBlank()}
        .filter { it. length <= 5 }
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
