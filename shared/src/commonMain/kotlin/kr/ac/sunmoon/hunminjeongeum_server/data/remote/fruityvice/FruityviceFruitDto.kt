package kr.ac.sunmoon.hunminjeongeum_server.data.remote.fruityvice

import kotlinx.serialization.Serializable

@Serializable
data class FruityviceFruitDto(
    val name: String,
    val id: Int,
    val family: String,
    val order: String,
    val genus: String,
    val nutritions: FruityviceNutritionDto
)

@Serializable
data class FruityviceNutritionDto(
    val calories: Double,
    val fat: Double,
    val sugar: Double,
    val carbohydrates: Double,
    val protein: Double
)

