package kr.ac.sunmoon.hunminjeongeum_server.hint

/**
 * 한글 음절을 다루는 순수 유틸리티. 외부 의존성이 전혀 없습니다.
 *
 * 한글 완성형 음절은 유니코드에 다음 공식으로 배치되어 있습니다.
 *
 *     코드포인트 = 0xAC00 + (초성index * 588) + (중성index * 28) + 종성index
 *
 * 588 = 21(중성 개수) * 28(종성 개수) 이므로
 * (코드포인트 - 0xAC00) / 588 이 곧 초성의 인덱스입니다.
 *
 * 검증된 케이스
 *   - 쌍자음 초성: 떡볶이(ㄸㅂㅇ), 까마귀(ㄲㅁㄱ), 쌍둥이(ㅆㄷㅇ), 짜장면(ㅉㅈㅁ), 토끼(ㅌㄲ)
 *   - 경계값: '가'(U+AC00) -> ㄱ, '힣'(U+D7A3) -> ㅎ
 *   - 비한글: 'A', ' ' -> null (예외를 던지지 않습니다)
 */
object Hangul {

    private const val SYLLABLE_BASE = 0xAC00   // '가'
    private const val SYLLABLE_LAST = 0xD7A3   // '힣'
    private const val CHOSUNG_STRIDE = 588     // 21 * 28

    /** 초성 19자. 순서를 절대 바꾸면 안 됩니다. 유니코드 배치 순서와 1:1 대응합니다. */
    private val CHOSUNG_TABLE = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    /** 완성형 한글 음절인지 확인합니다. 낱자음/낱모음('ㄱ', 'ㅏ')은 false 입니다. */
    fun isCompleteSyllable(ch: Char): Boolean = ch.code in SYLLABLE_BASE..SYLLABLE_LAST

    /** 문자열 전체가 완성형 한글 음절로만 이루어졌는지 확인합니다. */
    fun isPureHangul(text: String): Boolean =
        text.isNotEmpty() && text.all { isCompleteSyllable(it) }

    /** 한 글자의 초성을 반환합니다. 완성형 한글이 아니면 null 입니다. */
    fun chosungOf(ch: Char): Char? {
        if (!isCompleteSyllable(ch)) return null
        return CHOSUNG_TABLE[(ch.code - SYLLABLE_BASE) / CHOSUNG_STRIDE]
    }

    /**
     * 단어 전체를 초성으로 변환합니다. 한글이 아닌 문자는 그대로 둡니다.
     *
     *     toChosung("떡볶이") == "ㄸㅂㅇ"
     *     toChosung("대한민국") == "ㄷㅎㅁㄱ"
     *     toChosung("말") == "ㅁ"
     */
    fun toChosung(word: String): String = buildString(word.length) {
        for (ch in word) append(chosungOf(ch) ?: ch)
    }

    /**
     * 지정한 위치의 음절만 원래 글자로 공개하고 나머지는 초성으로 남깁니다.
     * 초성 힌트를 만드는 핵심 함수입니다.
     *
     *     revealSyllables("대한민국", setOf(1))    == "ㄷ한ㅁㄱ"
     *     revealSyllables("대한민국", setOf(1, 3)) == "ㄷ한ㅁ국"
     *     revealSyllables("일거양득", setOf(0))    == "일ㄱㅇㄷ"
     *     revealSyllables("일거양득", setOf(0, 2)) == "일ㄱ양ㄷ"
     *
     * @param revealed 공개할 음절 위치(0-based) 집합. 범위를 벗어난 값은 무시됩니다.
     */
    fun revealSyllables(word: String, revealed: Set<Int>): String = buildString(word.length) {
        word.forEachIndexed { i, ch ->
            if (i in revealed) append(ch) else append(chosungOf(ch) ?: ch)
        }
    }

    /**
     * 화면 표시용으로 글자 사이에 공백을 넣습니다. 기획 시안의 "ㄸ ㅂ ㅇ" 형태입니다.
     * 게임 로직에는 절대 쓰지 마세요. 오직 출력용입니다.
     */
    fun spaced(text: String): String = text.toCharArray().joinToString(" ")

    /**
     * 힌트에 절대 등장하면 안 되는 "정답 누출 조각"을 모읍니다.
     *
     * 검사 대상: 정답 전체, 정답의 초성 전체, 연속 2음절 부분 문자열(3음절 이상일 때).
     *
     *     leakFragments("수박")   == ["수박", "ㅅㅂ"]
     *     leakFragments("코끼리") == ["코끼리", "ㅋㄲㄹ", "코끼", "끼리"]
     *     leakFragments("말")     == ["말", "ㅁ"]
     *
     * ── 2음절 이상: 1음절 조각은 검사하지 않습니다 ──────────────
     * 한국어에서 1음절은 너무 흔해서, "코"를 막으면 "코가 길다" 를 못 써
     * 코끼리 힌트를 아예 만들 수 없게 됩니다. 정상 힌트까지 전부 거부됩니다.
     *
     * ── 1음절 단어: 그 1음절을 반드시 막습니다 ─────────────────
     * 위와 정반대로 보이지만 기준은 같습니다. "정답을 그대로 쓰지 말 것" 입니다.
     * 코끼리에게 "코" 는 조각이지만, 정답이 '말' 인 라운드에서 "말" 은 정답 그 자체입니다.
     *
     * 대신 이 엄격함의 대가는 큽니다. '말'(~라는 말입니다), '차'(차갑다, 자동차),
     * '배'(두 배, 배웁니다) 처럼 고빈도 형태소는 우연한 겹침으로도 계속 걸립니다.
     * 형태소 분석기 없이 "진짜 누출" 과 "우연한 겹침" 을 코드로 가를 방법은 없습니다.
     * 그래서 1음절 단어는 **단어 선정 단계에서 걸러야** 하고,
     * 그 근거를 `--audit` 의 실패율로 만들어 단어 DB 담당에게 넘깁니다.
     */
    fun leakFragments(word: String): List<String> {
        val fragments = mutableListOf(word, toChosung(word))
        if (word.length >= 3) {
            for (i in 0..word.length - 2) {
                fragments.add(word.substring(i, i + 2))
            }
        }
        return fragments.filter { it.isNotBlank() }.distinct()
    }
}
