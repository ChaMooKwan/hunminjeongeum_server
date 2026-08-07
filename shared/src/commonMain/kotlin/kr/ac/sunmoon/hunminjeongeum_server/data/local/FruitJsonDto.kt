package kr.ac.sunmoon.hunminjeongeum_server.data.local

import kotlinx.serialization.Serializable

@Serializable
data class FruitJsonDto(
    val english: String,
    val korean: String,
    val initial: String,
    val category: String
)
