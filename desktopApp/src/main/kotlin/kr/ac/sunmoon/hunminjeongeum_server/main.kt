package kr.ac.sunmoon.hunminjeongeum_server

import kr.ac.sunmoon.hunminjeongeum_server.data.remote.countries.CountriesApiClient
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val apiKey = System.getenv("COUNTRY_API_KEY")
        ?: error("COUNTRY_API_KEY 환경변수가 설정되지 않았습니다.")

    val api = CountriesApiClient(
        apiKey = apiKey
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
