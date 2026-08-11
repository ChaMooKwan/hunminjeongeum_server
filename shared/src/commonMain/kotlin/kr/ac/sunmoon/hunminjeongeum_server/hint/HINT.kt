package kr.ac.sunmoon.hunminjeongeum_server.hint

import kotlinx.coroutines.runBlocking
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordDto

fun getHint(quizWordDto: QuizWordDto):List<String>{
    return runBlocking {
        val list = mutableListOf<String>()
        val problem = WordEntry(
            id = quizWordDto.id,
            word = quizWordDto.word,
            quizCategory = quizWordDto.quizCategory,
            wordQuiz = quizWordDto.wordQuiz,
        )

        val apiKey = System.getenv("OPENAI_API_KEY")
            ?: error("OPENAI_API_KEY가 없습니다.")

        val generator = OpenAiHintGenerator(
            apiKey = apiKey,
            model = "gpt-4.1-nano",
            candidatesPerLevel = 2
        )

        generator.use {
            try {
                val result = generator.generate(
                    entry = problem,
                    attempt = 1,
                )
                println(result)
                list.add(result.easy.toString())
                list.add(result.normal.toString())
                list.add(result.hard.toString())
            } catch (e: Exception) {
                println("LLM 요청 실패: ${e.message}")
            }
        }
        list
    }

}
