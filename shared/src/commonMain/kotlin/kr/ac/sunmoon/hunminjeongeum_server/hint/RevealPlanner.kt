package kr.ac.sunmoon.hunminjeongeum_server.hint

import kotlin.random.Random

/**
 * 초성 힌트에서 "어느 음절을 열어줄 것인가" 를 정합니다.
 *
 * 이 계산은 AI가 아니라 코드가 합니다. 이유는 세 가지입니다.
 *   1. 유니코드 산술이라 코드는 100% 정확하지만 모델은 가끔 틀립니다.
 *   2. AI에게 물으면 왕복 시간이 늘어나는데, 라운드는 30초뿐입니다.
 *   3. "어느 음절이 더 좋은 힌트인가" 는 의미 판단이 거의 없는 문제입니다.
 *
 * 다만 위치가 고정되면 플레이어가 패턴을 학습해 버리므로 매 라운드 무작위로 뽑습니다.
 * [Random] 을 주입받으므로 테스트에서는 시드를 고정해 결과를 재현할 수 있습니다.
 */
object RevealPlanner {

    /**
     * 음절 수에 따른 초성 힌트 개수.
     *
     * v3까지는 이 함수가 규칙의 출처였고 [RoundPlan] 이 이걸 참조했습니다.
     * v4에서는 방향을 뒤집어 [RoundPlan] 을 유일한 출처로 삼습니다.
     * 공개 시각과 개수가 서로 다른 파일에 흩어져 있으면 언젠가 어긋나기 때문입니다.
     *
     *   1음절   -> 0개 (열면 곧 정답이라 불가능)
     *   2~3음절 -> 1개
     *   4~5음절 -> 2개
     */
    fun revealCount(syllableCount: Int): Int = RoundPlan.chosungHintCount(syllableCount)

    /**
     * 공개할 음절 위치를 뽑습니다. 반환 순서가 곧 공개 순서입니다.
     * 중복 없이 뽑히며, 결과 길이는 [revealCount] 와 같습니다.
     */
    fun planIndices(word: String, random: Random): List<Int> {
        val count = revealCount(word.length)
        if (count <= 0) return emptyList()
        return (0 until word.length).shuffled(random).take(count)
    }

    /**
     * 공개 위치 목록을 실제 초성 힌트 문자열로 바꿉니다. **누적 공개** 입니다.
     *
     *     buildHints("일거양득", listOf(0, 2)) == ["일ㄱㅇㄷ", "일ㄱ양ㄷ"]
     *
     * 두 번째 힌트는 첫 번째에서 연 음절을 그대로 유지한 채 하나를 더 엽니다.
     * 앞 힌트가 사라지면 플레이어가 정보를 잃기 때문입니다.
     */
    fun buildHints(word: String, indices: List<Int>): List<String> {
        val opened = LinkedHashSet<Int>()
        return indices.map { index ->
            opened.add(index)
            Hangul.revealSyllables(word, opened.toSet())
        }
    }

    /** [planIndices] + [buildHints] 를 한 번에. 실제 서비스에서 쓰는 진입점입니다. */
    fun plan(word: String, random: Random): List<String> =
        buildHints(word, planIndices(word, random))
}
