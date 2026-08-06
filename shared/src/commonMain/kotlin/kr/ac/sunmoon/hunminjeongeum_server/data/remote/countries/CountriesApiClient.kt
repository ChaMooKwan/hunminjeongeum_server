package kr.ac.sunmoon.hunminjeongeum_server.data.remote.countries

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kr.ac.sunmoon.hunminjeongeum_server.data.remote.HttpClientProvider

class CountriesApiClient(
    private val apiKey: String
) {
    private val client = HttpClientProvider.client

    suspend fun getCountries(
        limit: Int = 100,
        offset: Int = 0
    ): CountryResponse {
        return client.get(
            "https://api.restcountries.com/countries/v5"
        ) {
            header("Authorization", "Bearer $apiKey")

            parameter("limit", limit)
            parameter("offset", offset)

            parameter(
                "response_fields",
                "names.common,names.translations"
            )
        }.body()
    }

    suspend fun getAllCountries(): List<Country> {
        val allCountries = mutableListOf<Country>()

        val limit = 100
        var offset = 0

        while (true) {
            val response = getCountries(
                limit = limit,
                offset = offset
            )

            allCountries.addAll(response.data.objects)

            println(
                "현재 ${allCountries.size}개 수집 완료 " +
                        "/ 전체 ${response.data.meta.total}개"
            )

            if (!response.data.meta.more) {
                break
            }

            offset += limit
        }

        return allCountries
    }
}
