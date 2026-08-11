package kr.ac.sunmoon.hunminjeongeum_server.hint

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordDto
import kr.ac.sunmoon.hunminjeongeum_server.data.supabase.QuizWordRepository
import kotlin.random.Random

/**
 * 실제 단어 DB(Supabase) 를 [WordRepository] 로 감싸는 어댑터.
 *
 * ── 왜 팀 코드를 한 겹 더 감싸나 ──────────────────────────
 * 팀의 [QuizWordRepository] 는 카테고리를 숫자 ID 로 받고 [QuizWordDto] 를 돌려줍니다.
 * 반면 우리 힌트 로직은 카테고리를 이름("동물")으로 말하고 [WordEntry] 를 씁니다.
 * 이 변환을 여기 한 곳에서만 합니다. 나머지 힌트 코드는 DB 가 있는지조차 모릅니다.
 *
 * ── Supabase SDK 타입을 직접 쓰지 않는다 ──────────────────
 * import 는 팀의 [QuizWordRepository]·[QuizWordDto] 까지만 합니다.
 * `io.github.jan.supabase.*` 같은 SDK 타입은 이 파일에 한 줄도 없습니다.
 * SDK 버전이 올라가거나 백엔드가 바뀌어도 우리 모듈은 영향받지 않습니다.
 *
 * ── 캐시 전략 ─────────────────────────────────────────────
 * 팀의 `getRandomQuizWord` 는 호출마다 카테고리 전체를 DB 에서 가져와 하나를 고릅니다.
 * 라운드마다 그러면 DB 를 반복해서 때리므로, 여기서는 카테고리를 처음 요청할 때
 * 한 번만 통째로 읽어 두고([load]) 그 뒤로는 메모리에서 뽑습니다.
 * [InMemoryWordRepository] 와 같은 전략입니다.
 *
 * 캐시는 [Mutex] 로 보호합니다. 여러 라운드가 동시에 같은 카테고리를 처음 요청해도
 * DB 를 두 번 읽지 않게 하기 위해서입니다.
 *
 * @param delegate 팀이 만든 실제 저장소. 테스트에서는 가짜 구현을 넣을 수 있습니다.
 */
class SupabaseWordRepository(
    private val delegate: QuizWordRepository = QuizWordRepository()
) : WordRepository {

    private val mutex = Mutex()

    /** 카테고리 숫자 ID → 그 카테고리의 출제 가능한 단어들. 처음 요청 시 채워집니다. */
    private val cache = HashMap<Int, List<WordEntry>>()

    /**
     * DB 행([QuizWordDto]) 을 우리 도메인 모델([WordEntry]) 로 옮깁니다.
     * DTO 의 필드 이름이 이미 우리 모델과 같아(id, quizCategory, word, wordQuiz)
     * 그대로 옮기기만 하면 됩니다.
     */
    private fun QuizWordDto.toEntry(): WordEntry =
        WordEntry(id = id, word = word, quizCategory = quizCategory, wordQuiz = wordQuiz)

    /**
     * 카테고리 하나를 DB 에서 읽어 캐시에 올립니다. 이미 있으면 그대로 씁니다.
     * 읽어 온 뒤 [HintValidator.validateWord] 로 한 번 걸러, 못 쓸 단어(한자 표기,
     * 잘못된 카테고리 등)를 애초에 후보에서 뺍니다. API 호출 낭비를 막기 위해서입니다.
     */
    private suspend fun load(categoryId: Int): List<WordEntry> = mutex.withLock {
        cache[categoryId]?.let { return it }
        val rows = delegate.getQuizWordsByCategory(categoryId)
        val entries = rows.map { it.toEntry() }.filter { HintValidator.validateWord(it).valid }
        cache[categoryId] = entries
        entries
    }

    override suspend fun categories(): List<String> =
        // enum 에 정의된 모든 카테고리 이름을 돌려줍니다. DB 에 단어가 실제로 있는지는
        // countOf 로 확인하세요. (게임 시작 시 빈 카테고리를 거르는 책임은 호출자에게 있습니다.)
        QuizCategory.entries.map { it.label }.sorted()

    override suspend fun countOf(category: String): Int {
        val id = QuizCategory.indexOfLabel(category) ?: return 0
        return load(id).size
    }

    override suspend fun pickRandom(
        category: String,
        exclude: Set<Int>,
        random: Random
    ): WordEntry? {
        val id = QuizCategory.indexOfLabel(category) ?: return null
        val pool = load(id)
        val remaining = pool.filter { it.id !in exclude }
        if (remaining.isEmpty()) return null
        return remaining[random.nextInt(remaining.size)]
    }
}
