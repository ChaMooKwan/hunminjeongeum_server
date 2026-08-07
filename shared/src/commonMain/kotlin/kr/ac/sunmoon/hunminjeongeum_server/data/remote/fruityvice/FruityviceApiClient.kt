package kr.ac.sunmoon.hunminjeongeum_server.data.remote.fruityvice

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class FruityviceApiClient{

    private val client = HttpClient(CIO){
        install(ContentNegotiation){
            json(
                Json{
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }
    suspend fun getAllFruits(): List<FruityviceFruitDto>{
        val response = client.get(ALL_FRUITS_URL)

        if(!response.status.isSuccess()){
            throw IllegalStateException("" +
                "Fruityvice API 요청 실패: ${response.status}")
        }
        return response.body()
    }

    suspend fun getFruit(name:String): FruityviceFruitDto{
        val response = client.get("$BASE_URL/$name")

        if(!response.status.isSuccess()){
            throw IllegalStateException(
                "과일 조회 실패: ${response.status}, ${response.bodyAsText()}"
            )
        }
        return response.body()
    }
    fun close(){
        client.close()
    }

    companion object {
        private const val BASE_URL = "https://www.fruityvice.com/api/fruit"

        private const val ALL_FRUITS_URL =
            "$BASE_URL/all"
    }

}









