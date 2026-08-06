package kr.ac.sunmoon.hunminjeongeum_server

import kr.ac.sunmoon.hunminjeongeum_server.core.util.KoreanInitial
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
        println("===== 한국어 국가 목록 =====")
        println("총 ${koreanCountryNames.size}개")
        println()

        koreanCountryNames.forEachIndexed { index, countryName ->
            val initials = KoreanInitial.makeInitials(countryName)

            println(
                "${index + 1}. $countryName / $initials"
            )
        }

    } catch (e: Exception) {
        println("API 호출 실패: ${e.message}")
        e.printStackTrace()
    }
}
