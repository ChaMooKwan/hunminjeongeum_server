package kr.ac.sunmoon.hunminjeongeum_server.data.remote.foods

import kotlinx.serialization.Serializable

@Serializable
data class FoodResponse(
    val header: FoodHeader,
    val body: FoodBody
)

@Serializable
data class FoodHeader(
    val resultCode: String,
    val resultMsg: String
)

@Serializable
data class FoodBody(
    val items: FoodItems,
    val numOfRows: Int,
    val pageNo: Int,
    val totalCount: Int
)

@Serializable
data class FoodItems(
    val item: List<FoodItem>
)

@Serializable
data class FoodItem(
    val foodCd: String,
    val foodNm: String,
    val foodLv3Nm: String? = null,
    val foodLv4Nm: String? = null,
    val restNm: String? = null
)
