package kr.ac.sunmoon.hunminjeongeum_server

import  kr.ac.sunmoon.hunminjeongeum_server.data.remote.fruityvice.FruityviceApiClient
import kotlinx.coroutines.runBlocking

// 과일 API를 받아오는 기능
fun main() = runBlocking{
    val apiClient = FruityviceApiClient()

    try{
        val fruits = apiClient.getAllFruits()

        println("전체 과일 개수: ${fruits.size}")
        println("======== 과일 목록 ========")

        fruits.forEachIndexed {index, fruit ->
            println("${index + 1}."+
                "이름 = ${fruit.name}," +
                "과 = ${fruit.family}," +
                "칼로리 = ${fruit.nutritions.calories},"
            )
        }
    }catch (e:Exception){
        println("Fruityvice API 호출 실패")
        e.printStackTrace()
    }finally{
        apiClient.close()
    }
}





