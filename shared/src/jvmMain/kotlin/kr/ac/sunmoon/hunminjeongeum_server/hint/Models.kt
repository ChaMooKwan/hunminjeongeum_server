package kr.ac.sunmoon.hunminjeongeum_server.hint

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────
// 카테고리
// ─────────────────────────────────────────────────────────────

/**
 * 문제 유형(quizCategory).
 *
 * ── DB는 카테고리를 숫자 ID로 준다 ────────────────────────
 * 팀의 실제 스키마에서 `quiz_word.category_id` 는 정수입니다. 반면 프롬프트와
 * 게임 화면은 "동물", "과일" 같은 한글 이름을 씁니다. 이 둘을 잇는 변환을
 * **오직 이 enum 한 곳에서만** 합니다. DB가 준 숫자는 [ofIndex] 로 이름을 얻고,
 * 사람이 준 이름은 [ofLabel] 로 숫자를 얻습니다.
 *
 * ── ID 값 (팀 확정, 2026-08) ──────────────────────────────
 *   과일=1, 나라=2, 요리(음식)=3, 동물=4, 사자성어=5
 * DB 담당이 값을 바꾸면 아래 [index] 표 한 줄만 고치면 나머지 코드는 그대로입니다.
 *
 * ── "요리" vs "음식" ──────────────────────────────────────
 * 팀 문서·화면은 "요리", 프롬프트(Prompt.kt)의 카테고리 지침은 "음식"을 씁니다.
 * 프롬프트 본문은 수정하지 않기로 했으므로, 내부 표준 이름은 프롬프트에 맞춰 "음식"으로
 * 두고 "요리"를 별칭으로 흡수합니다. 어느 쪽으로 들어와도 같은 곳을 가리킵니다.
 *
 * 주의: 사자성어=5 는 아직 임시 값입니다. DB에서 확정되면 이 표를 갱신하세요. (TODO)
 */
enum class QuizCategory(val index: Int, val label: String) {
    FRUIT(1, "과일"),
    NATION(2, "나라"),
    FOOD(3, "음식"),
    ANIMAL(4, "동물"),
    IDIOM(5, "사자성어");   // TODO: DB에서 사자성어 category_id 확정 후 값 교체

    companion object {
        /** 같은 뜻으로 팀·문서마다 다르게 쓰이는 이름들을 흡수합니다. */
        private val ALIASES: Map<String, QuizCategory> = mapOf(
            "국가" to NATION,
            "나라" to NATION,
            "과일" to FRUIT,
            "동물" to ANIMAL,
            "음식" to FOOD,
            "요리" to FOOD,   // 팀 화면 표기 "요리" 를 표준 "음식" 으로 흡수
            "사자성어" to IDIOM,
            "고사성어" to IDIOM
        )

        /** DB가 준 숫자 category_id → enum. 모르는 값이면 null. */
        fun ofIndex(index: Int): QuizCategory? = entries.firstOrNull { it.index == index }

        /** 한글 이름(또는 별칭) → enum. 모르는 값이면 null. */
        fun ofLabel(label: String): QuizCategory? = ALIASES[label.trim()]

        /** 어떤 표기로 들어와도 게임에서 쓸 표준 이름으로 바꿉니다. 모르는 값은 원본을 그대로 씁니다. */
        fun normalize(label: String): String = ofLabel(label)?.label ?: label.trim()

        /** 숫자 category_id → 표준 이름. 모르는 값은 "미지정#<id>" 로 눈에 띄게 남깁니다. */
        fun labelOfIndex(index: Int): String = ofIndex(index)?.label ?: "미지정#$index"

        /** 한글 이름 → 숫자 category_id. DB 조회에 넘길 때 씁니다. 모르는 값이면 null. */
        fun indexOfLabel(label: String): Int? = ofLabel(label)?.index
    }
}

// ─────────────────────────────────────────────────────────────
// 입력: 단어 DB 한 행
// ─────────────────────────────────────────────────────────────

/**
 * 단어 DB에서 읽어온 단어 하나.
 *
 * ── DB 계약과 필드 이름을 맞췄습니다 (v5) ─────────────────
 * 팀의 실제 스키마(`QuizWordDto`)와 오프라인 개발용 `words_sample.json` 의 키가
 * 서로 다르면 매번 손으로 변환해야 하고 실수가 납니다. 그래서 이 모델의 필드 이름을
 * DB 계약에 그대로 맞춥니다.
 *
 *   DB(QuizWordDto)      words_sample.json    WordEntry
 *   id: Int              id                   id: Int
 *   category_id: Int     quizCategory         quizCategory: Int
 *   word: String         word                 word: String
 *   word_initial         wordQuiz             wordQuiz: String
 *
 * ── wordQuiz ──
 * 초성 문자열입니다. 코드가 계산한 값을 우선 사용하며,
 * DB 값과 일치 여부는 [wordQuizMatchesCode] 로 점검할 수 있습니다.
 *
 * 사전 뜻풀이(definition)는 **선택** 입니다. DB에 아직 컬럼이 없어 기본값이 비어 있고,
 * 나중에 컬럼이 생기면 값만 채우면 프롬프트가 자동으로 활용합니다.
 */
@Serializable
data class WordEntry(
    /** DB 기본키. 문자열이 아니라 정수입니다. */
    val id: Int,
    /** 정답 단어. 예: "대한민국" */
    val word: String,
    /** 카테고리 숫자 ID. 과일=1 / 나라=2 / 음식=3 / 동물=4 / 사자성어=5 */
    val quizCategory: Int,
    /** 초성 문자열. 예: "ㄷㅎㅁㄱ". 출제와 초성힌트 계산에 쓰입니다. */
    val wordQuiz: String = "",
    /** 사전 뜻풀이. 있으면 힌트 품질이 올라가지만 없어도 동작합니다. */
    val definition: String = ""
) {
    /** 카테고리 표준 이름. 프롬프트·로그·화면에서 씁니다. 모르는 ID면 "미지정#<id>". */
    val quizCategoryName: String get() = QuizCategory.labelOfIndex(quizCategory)
    val syllableCount: Int get() = word.length

    /** 초성힌트를 줄 수 없는 단어인지. 1음절은 음절을 열면 곧 정답이라 불가능합니다. */
    val isSingleSyllable: Boolean get() = syllableCount <= 1

    /**
     * 실제 출제에 쓰는 초성. DB 값이 아니라 항상 코드가 계산합니다.
     * DB 초성이 틀려 있어도 게임은 정상 동작해야 하기 때문입니다.
     */
    val quizChosung: String get() = Hangul.toChosung(word)

    /** DB 초성(wordQuiz)이 코드 계산과 일치하는지. 데이터 품질 점검용입니다. */
    val wordQuizMatchesCode: Boolean get() = wordQuiz.isBlank() || wordQuiz == Hangul.toChosung(word)
}

// ─────────────────────────────────────────────────────────────
// AI가 만들어 내는 특징 힌트 후보들
// ─────────────────────────────────────────────────────────────

/**
 * OpenAI 가 한 번의 호출로 돌려주는 **난이도별 후보 묶음**.
 *
 * v3까지는 난이도마다 문장이 하나씩이었습니다(`easy`, `normal`, `hard`).
 * 그래서 한 문장이 검증에서 떨어지면 곧바로 재시도해야 했는데,
 * 재시도는 긴 SYSTEM 프롬프트를 통째로 다시 보내는 일이라 후보를 하나 더 받는 것보다
 * 토큰도 시간도 훨씬 비쌌습니다.
 *
 * v4는 난이도마다 후보를 n개씩(기본 2개) 한 번에 받습니다.
 * 입력 토큰은 그대로이고 출력만 늘어나므로, 재시도 한 번보다 압도적으로 쌉니다.
 *
 * 주의: 이것만으로는 부족합니다. 같은 난이도의 후보들은 같은 지시를 받고 태어나므로
 * **같은 이유로 함께 죽습니다.** (김치찌개의 easy 후보 둘 다 '찌개' 를 쓰는 식)
 * 그래서 난이도를 가로지르는 [RoundPlan] 의 슬롯 압축이 함께 필요합니다.
 * 두 장치는 대체재가 아니라 보완재입니다.
 */
@Serializable
data class HintCandidates(
    val easy: List<String> = emptyList(),
    val normal: List<String> = emptyList(),
    val hard: List<String> = emptyList(),
    /** 모델이 스스로 "이 단어는 게임에 부적합하다" 고 판단하면 true */
    val rejected: Boolean = false,
    @SerialName("reject_reason") val rejectReason: String = ""
) {
    /** 해당 난이도의 후보 목록. 초성힌트 종류를 넣으면 빈 목록입니다. */
    fun of(kind: HintKind): List<String> = when (kind) {
        HintKind.EASY -> easy
        HintKind.NORMAL -> normal
        HintKind.HARD -> hard
        else -> emptyList()
    }

    val totalCount: Int get() = easy.size + normal.size + hard.size

    companion object {
        /** 난이도마다 후보 하나씩. 테스트와 가짜 생성기에서 씁니다. */
        fun single(easy: String, normal: String, hard: String): HintCandidates =
            HintCandidates(listOf(easy), listOf(normal), listOf(hard))
    }
}

// ─────────────────────────────────────────────────────────────
// 폴백: 사람이 미리 써 둔 최후의 한 문장
// ─────────────────────────────────────────────────────────────

/**
 * AI가 끝내 실패했을 때 대신 내보낼, 사람이 손으로 쓴 힌트.
 *
 * 다음절 단어는 AI가 실패해도 초성힌트가 남지만, **1음절은 남는 것이 하나도 없습니다.**
 * 30초 동안 화면에 아무것도 없는 라운드를 막는 것이 유일한 목적입니다.
 * 파일이 없으면 기능이 그냥 꺼진 채로 동작합니다.
 */
@Serializable
data class FallbackHint(
    val word: String,
    val category: String = "",
    val hint: String
)

// ─────────────────────────────────────────────────────────────
// 출력: 한 라운드분 완성품. UI/서버 팀이 실제로 받아 쓰는 객체
// ─────────────────────────────────────────────────────────────

/** 공개 시각과 문구를 한 덩어리로 묶은 것. UI는 이 리스트만 순서대로 소비하면 됩니다. */
@Serializable
data class TimedHint(
    val kind: HintKind,
    /** 남은 시간이 이 값이 되는 순간 공개 */
    val remainingSec: Int,
    val text: String
)

/** 이 라운드의 특징힌트가 어디서 왔는지. 강등 통계를 정직하게 남기기 위한 값입니다. */
enum class HintSource {
    /** AI가 만들고 검증을 통과했습니다. */
    AI,

    /** AI가 실패해 사람이 미리 써 둔 문장으로 대체했습니다. */
    FALLBACK,

    /** 특징힌트가 하나도 없습니다. 초성힌트만 나갑니다. */
    NONE
}

/**
 * 한 라운드에 필요한 모든 것.
 *
 * 주의: [word] 는 정답입니다. 서버 내부 판정용이며 클라이언트로 내려보내면 안 됩니다.
 * 클라이언트로 보낼 때는 [withoutAnswer] 를 거치세요.
 */
@Serializable
data class RoundHints(
    /** 단어 DB 기본키. DB 계약에 맞춰 정수입니다. */
    @SerialName("word_id") val wordId: Int,
    /** 정답 단어. 예: "대한민국" — 서버 전용 */
    val word: String,
    /** 화면에 출제되는 초성 문제. 예: "ㄷㅎㅁㄱ" */
    val wordQuiz: String,
    /**
     * 문제 유형의 숫자 ID. DB 계약(category_id)과 같습니다. 과일=1 / 나라=2 / 음식=3 / 동물=4 / 사자성어=5
     * 화면에 사람이 읽을 이름이 필요하면 [quizCategoryName] 을 쓰세요.
     */
    val quizCategory: Int,
    /** 문제 유형의 표준 이름. 예: "나라". 화면 표시용 파생 값입니다. */
    @SerialName("quiz_category_name") val quizCategoryName: String = "",

    /** 특징힌트(하). 사용하지 않는 라운드에서는 빈 문자열입니다. */
    val easyHint: String = "",
    /** 특징힌트(중). */
    val normalHint: String = "",
    /** 특징힌트(상). */
    val hardHint: String = "",
    /**
     * 초성힌트1(2~3음절 단어에서 첫 공개). 예: "ㄷ한ㅁㄱ" — 1음절 단어에서는 항상 빈 문자열입니다.
     * v4의 `wordHint_1` 을 팀 합의 변수명으로 바꾼 것입니다. 의미·계산은 동일합니다.
     */
    @SerialName("easyWordHint") val easyWordHint: String = "",
    /**
     * 초성힌트2(4음절 이상에서 두 번째 공개, 첫 힌트에 누적). 예: "ㄷ한ㅁ국"
     * v4의 `wordHint_2` 를 팀 합의 변수명으로 바꾼 것입니다.
     */
    @SerialName("normalWordHint") val normalWordHint: String = "",

    /** 공개 일정. */
    val cues: List<HintCue> = emptyList(),

    /**
     * true 면 AI가 쓸 만한 특징힌트를 **하나도** 만들지 못한 상태입니다.
     *
     * v3에서는 3개 중 1개만 떨어져도 true 였습니다. v4는 슬롯 압축 덕분에
     * 살아남은 것만으로 라운드를 굴리므로, **전멸한 경우에만** true 가 됩니다.
     * 즉 v3와 v4의 강등률은 정의가 달라 직접 비교하면 안 됩니다.
     */
    val degraded: Boolean = false,
    /** degraded 이거나 슬롯 승격이 일어났을 때의 사유. 프롬프트 개선의 재료가 됩니다. */
    val note: String = "",
    /** 특징힌트의 출처. */
    @SerialName("hint_source") val hintSource: HintSource = HintSource.AI,
    /**
     * true 면 앞 난이도가 떨어져 뒤 난이도가 앞 슬롯으로 당겨졌습니다.
     * (예: easy 가 죽어 normal 이 25초에 나감) 라운드가 계획보다 쉬워졌다는 뜻이라
     * `--audit` 에서 빈도를 세어 프롬프트를 고칠 근거로 씁니다.
     */
    @SerialName("slot_promoted") val slotPromoted: Boolean = false,
    @SerialName("generated_by") val generatedBy: String = "",
    @SerialName("latency_ms") val latencyMs: Long = 0
) {
    val syllableCount: Int get() = word.length

    fun hintOf(kind: HintKind): String = when (kind) {
        HintKind.EASY -> easyHint
        HintKind.NORMAL -> normalHint
        HintKind.HARD -> hardHint
        HintKind.WORD_1 -> easyWordHint
        HintKind.WORD_2 -> normalWordHint
    }

    /** 실제로 문구가 채워진 힌트만 공개 순서대로 돌려줍니다. */
    fun timeline(): List<TimedHint> = cues.mapNotNull { cue ->
        val text = hintOf(cue.kind)
        if (text.isBlank()) null else TimedHint(cue.kind, cue.remainingSec, text)
    }

    /**
     * "남은 시간이 [remainingSec] 초일 때 지금까지 공개된 힌트" 를 돌려줍니다.
     * 서버 타이머가 매 초 이 함수를 부르면 됩니다.
     */
    fun revealedAt(remainingSec: Int): List<TimedHint> =
        timeline().filter { it.remainingSec >= remainingSec }

    /**
     * 이 라운드가 30초 동안 힌트를 단 하나도 못 내는 상태인지.
     * **이 값이 true 인 라운드는 절대 화면에 나가면 안 됩니다.** 게임이 멈춘 것과 같습니다.
     */
    val isEmptyRound: Boolean get() = timeline().isEmpty()

    /** 클라이언트 전송용. 정답을 지웁니다. */
    fun withoutAnswer(): RoundHints = copy(word = "")
}

// ─────────────────────────────────────────────────────────────
// 로그: 프롬프트 튜닝의 재료
// ─────────────────────────────────────────────────────────────

/** 한 단어에 대한 생성 결과 기록. out/audit.json, out/failed.json 에 쌓입니다. */
@Serializable
data class GenerationLog(
    /** 단어 DB 기본키(정수). */
    val id: Int,
    val word: String,
    /** 카테고리 표준 이름. 로그는 사람이 읽으므로 숫자 ID가 아니라 이름을 남깁니다. */
    val category: String,
    @SerialName("syllable_count") val syllableCount: Int,
    val ok: Boolean,
    val reason: String = "",
    val attempts: Int = 0,
    @SerialName("latency_ms") val latencyMs: Long = 0,
    /** 채운 특징힌트 슬롯 수 / 필요한 슬롯 수. `2/3` 이면 압축이 일어난 라운드입니다. */
    val filled: Int = 0,
    val slots: Int = 0,
    @SerialName("slot_promoted") val promoted: Boolean = false,
    val easy: String = "",
    val normal: String = "",
    val hard: String = "",
    @SerialName("word_hints") val wordHints: List<String> = emptyList(),
    /** 후보가 왜 떨어졌는지 전부. 프롬프트를 고칠 때 가장 자주 보게 될 필드입니다. */
    @SerialName("rejected_candidates") val rejectedCandidates: List<String> = emptyList(),
    @SerialName("generated_by") val generatedBy: String = ""
)
