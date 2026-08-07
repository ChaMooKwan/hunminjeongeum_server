package kr.ac.sunmoon.hunminjeongeum_server.hint

/**
 * AI가 만든 힌트를 코드로 다시 검사하는 안전망.
 *
 * 이 파일이 이 프로젝트에서 가장 중요합니다.
 * 프롬프트에 "정답을 쓰지 마세요" 라고 아무리 강하게 써도 모델은 가끔 어깁니다.
 * "가끔" 이 문제인데, 시연 도중 딱 그 한 번이 터지면 게임이 무너집니다.
 * 그래서 AI를 믿지 않고 기계적으로 다시 검사합니다.
 *
 * ── v4에서 무엇이 바뀌었나 ────────────────────────────────
 * v3의 `validate()` 는 힌트 3개를 **한 세트로** 받아 하나라도 틀리면 즉시 실패했습니다.
 * 그래서 easy 에 '찌개' 가 새면 멀쩡한 normal/hard 까지 통째로 버려졌습니다.
 *
 * v4는 **문장 하나만 보는 [checkHint]** 만 제공합니다.
 * 여러 후보 중 무엇을 쓸지 고르는 일은 [HintSelector] 의 몫으로 넘겼습니다.
 * 검사기는 "이 문장이 나가도 되는가" 만 답하고, 라운드 성패를 혼자 결정하지 않습니다.
 *
 * ── v5에서 무엇이 바뀌었나 ────────────────────────────────
 * v4의 길이 기준은 모든 난이도에 8~50자로 고정이었고, 상수도 이 파일이 갖고 있었습니다.
 * 그래서 프롬프트("easy 권장 2~10자")와 검증기가 서로 다른 값을 말하는 모순이 있었습니다.
 *
 * v5는 두 가지를 바꿉니다.
 *   1. 길이 상수를 [HintLength]([RoundPlan.kt])로 옮겼습니다. 규칙은 한곳에 모읍니다.
 *   2. 길이 검사를 **난이도별**로 합니다. (지금은 셋 다 2~20자로 통일)
 * 검증기는 최소 방어선만 맡고, 세부 길이 품질은 프롬프트가 책임집니다.
 */
object HintValidator {

    data class Result(val valid: Boolean, val reason: String = "")

    private val OK = Result(true)

    /**
     * v3까지는 2였습니다. v4부터 1음절 단어를 출제하므로 1로 내립니다.
     * 1음절은 초성힌트를 줄 수 없어 특징힌트 하나에 전적으로 의존합니다.
     * ([RoundPlan.chosungSlots] 참고)
     */
    const val MIN_SYLLABLES = 1
    const val MAX_SYLLABLES = 5

    /**
     * 초성 게임에서 반칙인 표현들.
     * 글자 수·자모 구조를 언급하는 순간 초성 힌트의 존재 이유가 사라집니다.
     *
     * 1음절 단어에서 특히 중요합니다. 프롬프트가 "이 단어는 특별하다" 는 신호를 주는 순간
     * 모델이 "한 글자입니다" 라고 쓰고 싶어지는데, 여기서 기계적으로 막습니다.
     */
    private val BANNED_PATTERNS = listOf(
        "글자", "음절", "초성", "받침", "자음", "모음", "정답은", "답은"
    )

    /**
     * 힌트 문장 **하나**를 검사합니다. 후보 선택의 최소 단위입니다.
     *
     * @param kind 오류 메시지에 표시할 난이도. 검사 내용 자체는 난이도와 무관합니다.
     */
    fun checkHint(entry: WordEntry, kind: HintKind, text: String): Result {
        val trimmed = text.trim()
        val tag = kind.label

        // 길이 기준은 난이도마다 다를 수 있으므로 [HintLength] 에서 가져옵니다.
        // 지금은 세 난이도 모두 2~20자이지만, 값의 출처는 한곳(RoundPlan)입니다.
        val range = HintLength.rangeOf(kind)

        if (trimmed.isEmpty()) return Result(false, "[$tag] 힌트가 비어 있습니다")
        if (trimmed.length < range.first) {
            return Result(false, "[$tag] 너무 짧습니다 (${trimmed.length}자)")
        }
        if (trimmed.length > range.last) {
            return Result(false, "[$tag] 너무 깁니다 (${trimmed.length}자)")
        }

        // 핵심 검사: 정답이나 그 조각이 힌트에 새어 나왔는가?
        for (fragment in Hangul.leakFragments(entry.word)) {
            if (trimmed.contains(fragment)) {
                return Result(false, "[$tag] 정답 누출: '$fragment' 가 힌트에 포함됨")
            }
        }

        for (banned in BANNED_PATTERNS) {
            if (trimmed.contains(banned)) {
                return Result(false, "[$tag] 금지 표현 포함: '$banned'")
            }
        }

        return OK
    }

    /**
     * 힌트 생성 이전에, 단어 자체가 게임에 쓸 만한지 거릅니다.
     * 사전 API에서 긁어온 단어에는 한자 표기, 띄어쓰기, 접사 등이 섞여 들어옵니다.
     * 여기서 걸러야 API 호출을 낭비하지 않습니다.
     */
    fun validateWord(entry: WordEntry): Result {
        val w = entry.word.trim()

        if (w.isBlank()) return Result(false, "빈 단어")
        if (!Hangul.isPureHangul(w)) {
            return Result(false, "완성형 한글이 아닌 문자 포함: '$w'")
        }
        if (w.length < MIN_SYLLABLES) {
            return Result(false, "${MIN_SYLLABLES}음절 미만은 초성 게임에 부적합: '$w'")
        }
        if (w.length > MAX_SYLLABLES) {
            return Result(false, "${MAX_SYLLABLES}음절 초과는 너무 어려움: '$w'")
        }
        // 카테고리 ID 가 우리가 아는 값인지 확인합니다. 모르는 ID 면 프롬프트가
        // 카테고리별 지침을 못 붙이고 화면에 "미지정#7" 같은 값이 새어 나갑니다.
        if (QuizCategory.ofIndex(entry.quizCategory) == null) {
            return Result(false, "알 수 없는 카테고리 ID(${entry.quizCategory}): '$w'")
        }

        return OK
    }

    /** 힌트 문장 비교용 정규화. 공백만 다른 문장을 "같은 힌트" 로 봅니다. */
    fun normalizeForCompare(text: String): String = text.replace(" ", "").trim()
}
