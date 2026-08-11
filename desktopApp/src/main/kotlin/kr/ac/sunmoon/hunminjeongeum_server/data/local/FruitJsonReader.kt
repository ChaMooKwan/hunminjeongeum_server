package kr.ac.sunmoon.hunminjeongeum_server.data.local

import kotlinx.serialization.json.Json

object FruitJsonReader {

    fun read(): List<FruitJsonDto> {
        val inputStream = FruitJsonReader::class.java
            .classLoader.getResourceAsStream("fruits_140.json")
            ?: error("fruits_140.json 파일을 찾을 수 없습니다")

        val text = inputStream
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        return Json {
            ignoreUnknownKeys = true
        }.decodeFromString<List<FruitJsonDto>>(text)
    }
}
