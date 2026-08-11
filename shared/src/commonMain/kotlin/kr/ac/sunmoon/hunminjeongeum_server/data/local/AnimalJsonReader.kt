package kr.ac.sunmoon.hunminjeongeum_server.data.local

import kotlinx.serialization.json.Json

object AnimalJsonReader {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun readAnimals(): List<AnimalJsonDto> {

        val inputStream =
            AnimalJsonReader::class.java.getResourceAsStream("/animals_150.json")
                ?: error("animals_150.json 파일을 찾을 수 없습니다.")

        val jsonText = inputStream
            .bufferedReader()
            .use { it.readText() }

        return json.decodeFromString<List<AnimalJsonDto>>(jsonText)
    }
}
