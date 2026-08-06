package kr.ac.sunmoon.hunminjeongeum_server.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QuizWordDto(
    val id: Int,

    @SerialName("category_id")
    val quizCategory: Int, //

    val word: String,

    @SerialName("word_initial")
    val wordQuiz: String
)
