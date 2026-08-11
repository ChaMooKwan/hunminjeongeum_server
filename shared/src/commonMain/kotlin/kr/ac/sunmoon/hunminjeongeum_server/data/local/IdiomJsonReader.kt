package kr.ac.sunmoon.hunminjeongeum_server.data.local

import kotlinx.serialization.json.Json

object IdiomJsonReader {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun readIdioms(): List<IdiomJsonDto> {

        val inputStream =
            IdiomJsonReader::class.java.getResourceAsStream("/idioms_100.json")
                ?: error("idioms_100.json 파일을 찾을 수 없습니다.")

        val jsonText = inputStream
            .bufferedReader()
            .use { it.readText() }

        return json.decodeFromString<List<IdiomJsonDto>>(jsonText)
    }
}
