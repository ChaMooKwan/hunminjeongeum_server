package kr.ac.sunmoon.hunminjeongeum_server

import kr.ac.sunmoon.hunminjeongeum_server.data.remote.countries.CountriesApiClient
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val api = CountriesApiClient(
        apiKey = "rc_live_62f9005831e44c2b9faf8fd375a778cd"
    )

    try {
        val allCountries = api.getAllCountries()

        val koreanCountryNames = allCountries
            .map { country ->
                country.names.translations["kor"]?.common
                    ?: country.names.common
            }
            .distinct()
            .sorted()

        println()
        println("===== MAIN한국어 국가 목록 =====")
        println("총 ${koreanCountryNames.size}개")
        println()

        koreanCountryNames.forEachIndexed { index, countryName ->
            println("${index + 1}. $countryName")
        }

    } catch (e: Exception) {
        println("API 호출 실패: ${e.message}")
        e.printStackTrace()
    }
}
