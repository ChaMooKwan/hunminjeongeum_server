package kr.ac.sunmoon.hunminjeongeum_server.tools.test.fruitjson

import kr.ac.sunmoon.hunminjeongeum_server.data.local.FruitJsonReader

fun main() {

    val fruits = FruitJsonReader.read()

    println("과일 개수: ${fruits.size}")

    fruits.take(100).forEach {
        println(
            "${it.korean} → ${it.initial}"
        )
    }
}
