package kr.ac.sunmoon.hunminjeongeum_server.hint

import kr.ac.sunmoon.hunminjeongeum_server.core.util.KoreanInitial
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 우리 [Hangul.toChosung] 이 팀 공용 [KoreanInitial.makeInitials] 와
 * **항상 같은 초성 결과** 를 내는지 지키는 테스트.
 *
 * ── 왜 이 테스트가 필요한가 ────────────────────────────────
 * 초성 변환 로직이 두 곳(우리 Hangul, 팀 KoreanInitial)에 존재합니다.
 * 원래는 하나로 합치는 게 맞지만 [KoreanInitial] 은 다른 팀원 소유라 건드리지 않기로 했고,
 * 우리 힌트 로직은 [Hangul] 의 다른 기능([revealSyllables] 등)에도 의존하고 있어
 * 통째로 갈아탈 수도 없습니다.
 *
 * 그래서 "둘을 합치는" 대신 "둘이 어긋나면 빌드가 깨지게" 만듭니다.
 * 누군가 한쪽 초성표를 고치는 순간 이 테스트가 빨갛게 되어 알려 줍니다.
 * 초성이 어긋나면 화면의 초성 문제(wordQuiz)와 게임 로직이 서로 다른 글자를 보게 되어
 * 정답 판정이 깨지므로, 이건 반드시 잡아야 하는 종류의 버그입니다.
 *
 * 주의: [KoreanInitial] 은 완성형 한글만 초성으로 바꾸고 나머지 letterOrDigit 은
 * 그대로 둡니다. [Hangul.toChosung] 도 한글이 아닌 문자는 그대로 두므로,
 * **완성형 한글로만 이루어진 단어** 에서는 두 결과가 정확히 같아야 합니다.
 * 게임 단어는 항상 완성형 한글이므로([HintValidator.validateWord] 가 보장),
 * 이 범위에서 일치하면 충분합니다.
 */
class HangulConsistencyTest {

    private val words = listOf(
        // 카테고리별 대표 + 쌍자음 + 1음절 + 받침 유무
        "사자", "기린", "다람쥐", "코끼리", "고슴도치",
        "말", "곰", "닭",
        "바나나", "딸기", "수박", "블루베리",
        "떡볶이", "김치찌개", "삼겹살",
        "대한민국", "이탈리아",
        "유비무환", "새옹지마",
        // 경계값
        "가", "힣"
    )

    @Test
    fun `toChosung 은 KoreanInitial 과 같은 결과를 낸다`() {
        for (w in words) {
            assertEquals(
                KoreanInitial.makeInitials(w),
                Hangul.toChosung(w),
                "초성 불일치: '$w'"
            )
        }
    }

    @Test
    fun `쌍자음 초성이 정확하다`() {
        assertEquals("ㄸㅂㅇ", Hangul.toChosung("떡볶이"))
        assertEquals("ㄲㅁㄱ", Hangul.toChosung("까마귀"))
        assertEquals("ㅆㄷㅇ", Hangul.toChosung("쌍둥이"))
    }
}
