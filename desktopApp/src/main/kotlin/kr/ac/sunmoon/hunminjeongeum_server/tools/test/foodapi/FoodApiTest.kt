package kr.ac.sunmoon.hunminjeongeum_server.tools.test.foodapi

import kotlinx.coroutines.runBlocking
import kr.ac.sunmoon.hunminjeongeum_server.data.remote.foods.FoodApiClient
import kr.ac.sunmoon.hunminjeongeum_server.data.remote.foods.FoodItem

fun main() = runBlocking {

    val api = FoodApiClient(
        serviceKey = "fzOJwdZ5WM%2BI3astAfBG18w4BQFFuZcJOXKcwrFNPvBPS%2BfyXAwwGXzgqu1D2pJF6g9FPeoEtQGghDtt2i1PiA%3D%3D"
    )

    val allItems = mutableListOf<FoodItem>()

    // 1. 여러 페이지 요청
    for (page in 1..20) {

        println()
        println("===== ${page}페이지 요청 =====")

        val response = api.getFoods(
            pageNo = page,
            numOfRows = 100
        )

        if (response == null) {
            println("${page}페이지 API 응답 실패")
            continue
        }

        val items = response.body.items.item

        allItems.addAll(items)

        println("${page}페이지 받은 개수 : ${items.size}")
        println("현재 누적 개수 : ${allItems.size}")
    }

    // 2. 음식 이름 가공
    val converted = allItems.map {
        it.foodNm
            .substringBefore("_")
            .substringBefore("(")
            .trim()
    }

    // 3. 빈 값 제거 + 중복 제거 + 정렬
    val foodNames = converted
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
        .take(200)

    println()
    println("=== 음식 목록 ===")
    println("총 ${foodNames.size}개")
    println()

    foodNames.forEachIndexed { index, foodName ->
        println("${index + 1}. $foodName")
    }

    // 4. 가공 과정에서 얼마나 줄어드는지 확인
    println()
    println("===== 데이터 개수 확인 =====")
    println("API에서 받은 전체 개수 : ${allItems.size}")
    println("가공 후 개수 : ${converted.size}")
    println("빈 값 제거 후 개수 : ${converted.count { it.isNotBlank() }}")
    println(
        "중복 제거 후 개수 : ${
            converted
                .filter { it.isNotBlank() }
                .distinct()
                .size
        }"
    )
}
