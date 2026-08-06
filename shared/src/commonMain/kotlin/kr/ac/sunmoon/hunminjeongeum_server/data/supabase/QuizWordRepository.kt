package kr.ac.sunmoon.hunminjeongeum_server.data.supabase

import io.github.jan.supabase.postgrest.from


class QuizWordRepository{
    private val supabase = SupabaseClientProvider.client

    // 단어 한개 저장
    suspend fun insertQuizWord(
        quizWord: QuizWordInsertDto
    ){
        supabase
            .from("quiz_word")
            .insert(quizWord)
    }

    // 단어 여러 개 저장
    suspend fun insertQuizWords(
        quizWords: List<QuizWordInsertDto>
    ){
        if(quizWords.isEmpty()) return

        supabase
            .from("quiz_word")
            .insert(quizWords)
    }

    suspend fun getQuizWordsByCategory(
        categoryId: Int
    ): List<QuizWordDto> {
        return supabase
            .from("quiz_word")
            .select {
                filter {
                    eq("category_id", categoryId)
                }
            }
            .decodeList<QuizWordDto>()
    }

    suspend fun getRandomQuizWord(
        categoryId: Int
    ): QuizWordDto? {
        return getQuizWordsByCategory(categoryId)
            .randomOrNull()
    }

}
