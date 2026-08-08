package kr.ac.sunmoon.hunminjeongeum_server.data.remote.foods

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kr.ac.sunmoon.hunminjeongeum_server.data.remote.HttpClientProvider
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class FoodApiClient(
    private val serviceKey: String
) {

    suspend fun getFoods(): FoodResponse {

        val decodedKey = URLDecoder.decode(
            serviceKey,
            StandardCharsets.UTF_8
        )

        return HttpClientProvider.client.get(
            "https://api.data.go.kr/openapi/tn_pubr_public_nutri_food_info_api"
        ) {
            parameter("serviceKey", decodedKey)
            parameter("pageNo", 1)
            parameter("numOfRows", 500)
            parameter("type", "json")
        }.body()
    }
}
