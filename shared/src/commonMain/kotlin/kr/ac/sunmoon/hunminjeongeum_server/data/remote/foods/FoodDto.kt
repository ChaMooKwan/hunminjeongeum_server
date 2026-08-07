package kr.ac.sunmoon.hunminjeongeum_server.data.remote.foods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FoodDto(
    @SerialName("foodNm")
    val foodName: String = "",

    @SerialName("foodLv4Nm")
    val representativeFoodName: String = ""
)
