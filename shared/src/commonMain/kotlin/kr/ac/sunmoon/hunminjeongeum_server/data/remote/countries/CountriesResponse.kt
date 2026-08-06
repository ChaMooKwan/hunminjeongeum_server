package kr.ac.sunmoon.hunminjeongeum_server.data.remote.countries

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CountryResponse(
    val data: CountryData
)

@Serializable
data class CountryData(
    val objects: List<Country>,
    val meta: CountryPageMeta
)

@Serializable
data class Country(
    val names: CountryNames,

    @SerialName("_meta")
    val objectMeta: CountryObjectMeta? = null
)

@Serializable
data class CountryNames(
    val common: String,
    val translations: Map<String, CountryTranslation> = emptyMap()
)

@Serializable
data class CountryTranslation(
    val common: String,
    val official: String? = null
)

@Serializable
data class CountryPageMeta(
    val total: Int,
    val count: Int,
    val limit: Int,
    val offset: Int,
    val more: Boolean,

    @SerialName("request_id")
    val requestId: String? = null,

    val duration: Int? = null
)

@Serializable
data class CountryObjectMeta(
    val lastUpdatedTimestamp: Long? = null
)