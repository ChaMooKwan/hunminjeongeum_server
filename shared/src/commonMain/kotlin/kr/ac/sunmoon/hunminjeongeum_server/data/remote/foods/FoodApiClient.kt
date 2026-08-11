package kr.ac.sunmoon.hunminjeongeum_server.data.remote.foods


import kr.ac.sunmoon.hunminjeongeum_server.data.remote.HttpClientProvider
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class FoodApiClient(
    private val serviceKey: String
) {

    suspend fun getFoods(
        pageNo: Int,
        numOfRows: Int = 100
    ): FoodResponse? {

        val decodedKey = URLDecoder.decode(
            serviceKey,
            StandardCharsets.UTF_8
        )

        val response = HttpClientProvider.client.get(
            "https://api.data.go.kr/openapi/tn_pubr_public_nutri_food_info_api"
        ) {
            parameter("serviceKey", decodedKey)
            parameter("pageNo", pageNo)
            parameter("numOfRows", numOfRows)
            parameter("type", "json")
        }

        val rawText = response.bodyAsText()

        println("===== API 원본 응답 =====")
        println(rawText)

        if(rawText.contains("OpenAPI_ServiceResponse")){
            println("API 서버 오류가 발생했습니다.")
            return null
        }

        return Json {
            ignoreUnknownKeys = true
        }.decodeFromString<FoodResponse>(rawText)
    }
}
