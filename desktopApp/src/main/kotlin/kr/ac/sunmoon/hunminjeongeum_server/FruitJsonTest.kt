package kr.ac.sunmoon.hunminjeongeum_server

import kr.ac.sunmoon.hunminjeongeum_server.data.local.FruitJsonReader

fun main() {

    val fruits = FruitJsonReader.read()

    println("과일 개수: ${fruits.size}")

    fruits.take(100).forEach {
        println(
            "${it.english} → ${it.korean} → ${it.initial}"
        )
    }
}
