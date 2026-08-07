package kr.ac.sunmoon.hunminjeongeum_server

import kotlinx.coroutines.runBlocking
import kr.ac.sunmoon.hunminjeongeum_server.data.remote.foods.FoodApiClient

fun main() = runBlocking {

    val api = FoodApiClient(
        serviceKey = "fzOJwdZ5WM%2BI3astAfBG18w4BQFFuZcJOXKcwrFNPvBPS%2BfyXAwwGXzgqu1D2pJF6g9FPeoEtQGghDtt2i1PiA%3D%3D"
    )

    val response = api.getFoods()

    val foodNames = response.body.items.item
        .map{it.foodNm
            //.substringBefore("_")
            .substringBefore("(")
            .trim()}
        .filter{it.isNotBlank()}
        .distinct()
        .sorted()
        .take(200)

    println("=== 음식 목록 ===")
    println("총 ${foodNames.size}")

    foodNames.forEachIndexed { index, foodName ->
        println("${index + 1}. $foodName")
    }
    // 데이터가 몇개 정도 사라지는지 확인 코드
    val items = response.body.items.item

    println("API에서 받은 개수 : ${items.size}")

    val converted = items.map {
        it.foodNm
            .substringBefore("_")
            .substringBefore("(")
            .trim()
    }

    println("가공 후 개수 : ${converted.size}")
    println("중복 제거 후 개수 : ${converted.distinct().size}")
}
