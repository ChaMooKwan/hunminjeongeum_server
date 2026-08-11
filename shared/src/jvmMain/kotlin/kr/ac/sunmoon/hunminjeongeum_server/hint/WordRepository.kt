package kr.ac.sunmoon.hunminjeongeum_server.hint

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

/**
 * 단어 공급원.
 *
 * 단어 DB 구축은 다른 팀원 담당이므로, 우리는 이 인터페이스 뒤에 숨겨 둡니다.
 * 지금은 [InMemoryWordRepository] 로 JSON 파일을 읽어 개발하고,
 * DB가 준비되면 이 인터페이스를 구현한 클래스([SupabaseWordRepository]) 하나만
 * 갈아 끼우면 됩니다. 나머지 코드는 한 줄도 바뀌지 않습니다.
 *
 * ── 카테고리를 왜 이름(String)으로 받나 ───────────────────
 * DB 계약은 숫자 ID(category_id)이지만, 게임 로직과 화면은 "동물" 같은 이름을 씁니다.
 * 이 인터페이스는 게임 쪽 언어(이름)로 말하고, 숫자 변환은 구현체 안에서 처리합니다.
 * 이름 ↔ 숫자 변환은 [QuizCategory] 한 곳에서만 일어납니다.
 *
 * 구현할 때 지켜야 할 계약
 *   - [pickRandom] 은 [exclude] 에 있는 id(정수) 를 절대 돌려주지 않습니다.
 *   - 더 뽑을 단어가 없으면 예외가 아니라 null 을 돌려줍니다.
 */
interface WordRepository {

    /** 사용 가능한 카테고리 이름 목록. */
    suspend fun categories(): List<String>

    /** 해당 카테고리에서 출제 가능한 단어 수. */
    suspend fun countOf(category: String): Int

    /**
     * 카테고리에서 단어 하나를 무작위로 뽑습니다.
     * @param category 카테고리 이름(또는 별칭). 예: "동물", "요리"
     * @param exclude 이번 게임에서 이미 출제한 단어 id(정수) 들
     */
    suspend fun pickRandom(category: String, exclude: Set<Int>, random: Random): WordEntry?
}

/**
 * 오프라인 개발용 JSON 한 행.
 *
 * ── DB 스키마와 필드 이름을 맞췄습니다 (v5) ───────────────
 * `words_sample.json` 의 키는 실제 DB 컬럼과 같은 이름을 씁니다.
 *   id / word / quizCategory(숫자 ID) / wordQuiz(초성)
 * 이렇게 두면 나중에 JSON → DB 로 갈아탈 때 매핑을 따로 만들 필요가 없습니다.
 *
 * [WordEntry] 와 필드가 거의 같지만 굳이 분리한 이유는, JSON 스키마가
 * 데이터 담당자의 것이고 도메인 모델은 우리 것이라, 서로 독립적으로 바뀔 수 있게
 * 경계를 그어 두기 위해서입니다.
 */
@kotlinx.serialization.Serializable
data class JsonWordEntry(
    val id: Int,
    val word: String,
    val quizCategory: Int,
    val wordQuiz: String = ""
) {
    fun toWordEntry(): WordEntry =
        WordEntry(id = id, word = word, quizCategory = quizCategory, wordQuiz = wordQuiz)
}

/**
 * 메모리에 올려 두고 뽑는 구현.
 *
 * 카테고리를 고르는 순간 100~200개를 통째로 읽어 두고 그중에서 뽑는 방식이라
 * 라운드마다 DB를 때리지 않습니다. 실제 DB 구현도 같은 전략을 권합니다.
 *
 * 생성 시점에 [HintValidator.validateWord] 로 한 번 걸러 둡니다.
 * 못 쓸 단어를 뽑아 놓고 API를 호출하는 낭비를 막기 위해서입니다.
 */
class InMemoryWordRepository(source: List<WordEntry>) : WordRepository {

    /** 출제 가능한 단어들. */
    val accepted: List<WordEntry>

    /** 걸러진 단어와 사유. 데이터 품질을 눈으로 확인할 때 씁니다. */
    val rejected: List<Pair<WordEntry, String>>

    /** 카테고리 숫자 ID 로 묶어 둡니다. 이름이 아니라 ID 가 DB 의 진짜 키이기 때문입니다. */
    private val byCategory: Map<Int, List<WordEntry>>

    init {
        val ok = mutableListOf<WordEntry>()
        val ng = mutableListOf<Pair<WordEntry, String>>()
        for (entry in source) {
            val result = HintValidator.validateWord(entry)
            if (result.valid) ok.add(entry) else ng.add(entry to result.reason)
        }
        accepted = ok
        rejected = ng
        byCategory = ok.groupBy { it.quizCategory }
    }

    /** 이름 → 숫자 ID. 모르는 이름이면 null(빈 결과로 이어짐). */
    private fun categoryId(category: String): Int? = QuizCategory.indexOfLabel(category)

    override suspend fun categories(): List<String> =
        byCategory.keys.map { QuizCategory.labelOfIndex(it) }.sorted()

    override suspend fun countOf(category: String): Int {
        val id = categoryId(category) ?: return 0
        return byCategory[id]?.size ?: 0
    }

    override suspend fun pickRandom(
        category: String,
        exclude: Set<Int>,
        random: Random
    ): WordEntry? {
        val id = categoryId(category) ?: return null
        val pool = byCategory[id] ?: return null
        val remaining = pool.filter { it.id !in exclude }
        if (remaining.isEmpty()) return null
        return remaining[random.nextInt(remaining.size)]
    }

    /** 출제 가능한 1음절 단어들. 폴백 힌트가 반드시 필요한 대상입니다. */
    fun singleSyllableWords(): List<WordEntry> = accepted.filter { it.isSingleSyllable }

    /** DB 초성(wordQuiz) 값이 코드 계산과 다른 단어들. 데이터 점검용입니다. */
    fun chosungMismatches(): List<WordEntry> = accepted.filterNot { it.wordQuizMatchesCode }

    /**
     * 같은 카테고리 안에서 출제 초성이 완전히 겹치는 단어 묶음.
     *
     * 이건 우리 모듈의 버그가 아니라 **게임 공정성 문제** 입니다.
     * `과일 / ㄱ` 은 감과 귤 둘 다이므로, 플레이어가 합리적으로 추론해도 오답이 됩니다.
     * 다음절에서는 거의 안 생기지만 1음절에서는 흔합니다.
     * 정답 판정은 우리 소관이 아니므로, 목록을 뽑아 단어 DB 담당에게 넘기는 것까지가 우리 몫입니다.
     */
    fun chosungCollisions(): List<List<WordEntry>> =
        accepted.groupBy { it.quizCategory to it.wordQuiz }
            .values
            .filter { it.size > 1 }
            .sortedBy { it.first().quizCategory }

    companion object {
        /** words_sample.json 같은 파일에서 읽어 옵니다. */
        fun fromJsonFile(file: File, json: Json): InMemoryWordRepository {
            val text = file.readText(Charsets.UTF_8)
            val rows = json.decodeFromString(ListSerializer(JsonWordEntry.serializer()), text)
            return InMemoryWordRepository(rows.map { it.toWordEntry() })
        }
    }
}
