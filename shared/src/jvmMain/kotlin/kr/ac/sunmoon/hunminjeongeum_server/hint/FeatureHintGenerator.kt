package kr.ac.sunmoon.hunminjeongeum_server.hint

/**
 * 특징힌트(하/중/상)를 만들어 내는 쪽의 계약.
 *
 * 이 인터페이스가 있어야
 *   - API 키 없이도 [FakeHintGenerator] 로 전체 흐름을 테스트할 수 있고,
 *   - 나중에 다른 모델 제공자로 갈아탈 때 이 파일 아래만 바꾸면 됩니다.
 */
interface FeatureHintGenerator : AutoCloseable {

    /** 로그에 남길 이름. 예: "gpt-4.1-nano" */
    val name: String

    /**
     * 난이도마다 만드는 후보 개수.
     *
     * 생성기가 이 값을 갖는 이유는, 이 값이 JSON 스키마의 모양을 결정하기 때문입니다.
     * 서비스([HintService])는 후보가 몇 개로 왔든 신경 쓰지 않고 고르기만 합니다.
     */
    val candidatesPerLevel: Int

    /**
     * @param attempt 1부터 시작하는 시도 횟수.
     * @param feedback 직전 시도가 검증에서 떨어진 사유. 첫 시도면 null.
     * @throws OpenAiFatalException 재시도해도 소용없는 오류(키 오류, 모델명 오류 등)
     */
    suspend fun generate(entry: WordEntry, attempt: Int = 1, feedback: String? = null): HintCandidates

    override fun close() {
        // 대부분의 구현은 정리할 자원이 없습니다.
    }
}

/** 재시도해도 소용없는 오류. 즉시 중단해야 합니다. */
class OpenAiFatalException(message: String) : RuntimeException(message)

/** 재시도할 가치가 있는 오류. */
class OpenAiTransientException(message: String) : RuntimeException(message)

/**
 * API 키 없이 전체 파이프라인을 돌려 보기 위한 가짜 생성기.
 *
 * 단어 DB가 아직 없고 OpenAI 키도 없는 상태에서
 * "라운드 진행 -> 힌트 일정 -> 초성 힌트" 가 제대로 동작하는지 확인하는 용도입니다.
 * 실제 힌트 품질과는 아무 상관이 없습니다.
 */
class FakeHintGenerator(
    private val latencyMs: Long = 0,
    override val candidatesPerLevel: Int = Prompt.DEFAULT_CANDIDATES_PER_LEVEL,
    /** true 면 항상 빈 후보를 돌려줍니다. 강등·폴백 경로를 눈으로 확인할 때 씁니다. */
    private val alwaysFail: Boolean = false
) : FeatureHintGenerator {

    override val name: String get() = if (alwaysFail) "fake-fail" else "fake"

    /**
     * 어떤 단어에도 정답이 새지 않도록, 단어를 전혀 참조하지 않는 문장만 씁니다.
     * 그래도 1음절 단어('말', '차')는 흔한 형태소라 우연히 걸릴 수 있으므로
     * 난이도마다 넉넉히 준비해 두고 통과하는 것부터 골라 씁니다.
     */
    private val pool: Map<HintKind, List<String>> = mapOf(
        HintKind.EASY to listOf(
            "같은 분류 안에서 비교적 널리 알려진 편입니다",
            "학교에서 한 번쯤은 들어 보았을 대상입니다",
            "많은 사람이 익숙하게 여기는 소재입니다",
            "어릴 적부터 자연스럽게 접해 온 것입니다",
            "이름을 들으면 대체로 고개를 끄덕입니다"
        ),
        HintKind.NORMAL to listOf(
            "생김새와 쓰임새로 어렵지 않게 구분됩니다",
            "주변에서 자주 마주칠 만한 흔한 것입니다",
            "겉모습만 보아도 금방 알아볼 수 있습니다",
            "특징이 뚜렷해 헷갈릴 일이 드뭅니다",
            "눈에 잘 띄는 생김새를 지니고 있습니다"
        ),
        HintKind.HARD to listOf(
            "떠올리면 누구나 아는 대표적인 사례입니다",
            "이야기할 때 가장 먼저 나오는 이름입니다",
            "관련해서 가장 유명한 것으로 꼽힙니다",
            "누구에게 물어도 비슷한 대답이 나옵니다",
            "그 분야를 대표한다고 해도 좋습니다"
        )
    )

    override suspend fun generate(entry: WordEntry, attempt: Int, feedback: String?): HintCandidates {
        if (latencyMs > 0) kotlinx.coroutines.delay(latencyMs)
        if (alwaysFail) return HintCandidates()

        val n = candidatesPerLevel.coerceIn(Prompt.CANDIDATE_RANGE)

        fun pick(kind: HintKind): List<String> {
            val passing = pool.getValue(kind).filter { HintValidator.checkHint(entry, kind, it).valid }
            // 전부 걸렸다면 원본을 그대로 넘겨 [HintSelector] 가 판단하게 둡니다.
            // 가짜 생성기가 검증을 대신 하기 시작하면 진짜 경로와 동작이 달라집니다.
            return (passing.ifEmpty { pool.getValue(kind) }).take(n)
        }

        return HintCandidates(
            easy = pick(HintKind.EASY),
            normal = pick(HintKind.NORMAL),
            hard = pick(HintKind.HARD)
        )
    }
}
