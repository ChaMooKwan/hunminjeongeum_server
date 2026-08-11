package kr.ac.sunmoon.hunminjeongeum_server.hint

import kotlinx.serialization.Serializable

/** 한 라운드의 전체 길이(초). 여기 한 곳만 바꾸면 전부 따라옵니다. */
const val ROUND_SECONDS: Int = 30

/**
 * 힌트 문장 길이 기준(공백 포함 글자 수). 검증기가 이 값으로 힌트를 거릅니다.
 *
 * ── v5에서 왜 여기로 옮겼나 ────────────────────────────────
 * v4까지는 길이 상수가 [HintValidator] 안에 있었고, 프롬프트("권장 2~10자")와
 * 검증기(8~50자)가 서로 다른 값을 말했습니다. 프롬프트가 제시한 모범 예시
 * "과일의 여왕이죠 ㅋㅋ"(11자)가 검증기에서 떨어지는 모순까지 있었습니다.
 *
 * v5의 방침(팀 합의): **검증기는 게임이 깨지지 않을 최소 방어선만 맡는다.**
 *   - 세 난이도 모두 2~20자로 통일합니다. 세부 품질(easy는 짧게 등)은 프롬프트가 맡습니다.
 *   - 검증기가 촘촘하면 멀쩡한 힌트까지 버려 실패율만 오릅니다.
 *
 * 난이도별로 상수를 나눠 둔 이유는 나중을 위해서입니다. 지금은 셋 다 2..20 이지만,
 * easy 만 상한을 조이고 싶어지면 여기 [EASY_MAX] 한 줄만 고치면 됩니다.
 * 규칙을 한곳(RoundPlan)에 모으는 이 파일의 원칙에 맞춥니다.
 */
object HintLength {
    const val EASY_MIN = 2
    const val EASY_MAX = 20
    const val NORMAL_MIN = 2
    const val NORMAL_MAX = 20
    const val HARD_MIN = 2
    const val HARD_MAX = 20

    /** 난이도별 (하한, 상한). 초성힌트는 코드가 만들어 검사 대상이 아니므로 특징힌트만 있습니다. */
    fun rangeOf(kind: HintKind): IntRange = when (kind) {
        HintKind.EASY -> EASY_MIN..EASY_MAX
        HintKind.NORMAL -> NORMAL_MIN..NORMAL_MAX
        HintKind.HARD -> HARD_MIN..HARD_MAX
        // 초성힌트 종류가 실수로 들어와도 안전하게 넓은 범위를 돌려줍니다.
        else -> 1..ROUND_SECONDS * 4
    }
}

/**
 * 힌트의 종류. 이름이 곧 팀에서 합의한 변수명과 1:1 대응합니다.
 *   EASY   -> easyHint      (특징힌트 하)
 *   NORMAL -> normalHint    (특징힌트 중)
 *   HARD   -> hardHint      (특징힌트 상)
 *   WORD_1 -> easyWordHint    (초성힌트 1)
 *   WORD_2 -> normalWordHint  (초성힌트 2)
 */
enum class HintKind(val label: String) {
    EASY("특징힌트(하)"),
    WORD_1("초성힌트1"),
    NORMAL("특징힌트(중)"),
    WORD_2("초성힌트2"),
    HARD("특징힌트(상)");

    val isFeature: Boolean get() = this == EASY || this == NORMAL || this == HARD
    val isChosung: Boolean get() = this == WORD_1 || this == WORD_2
}

/**
 * "남은 시간이 [remainingSec] 초가 되는 순간 이 힌트를 공개한다" 는 예약입니다.
 * 화면의 '남은 시간' 표시와 같은 기준이라 서버가 그대로 비교만 하면 됩니다.
 */
@Serializable
data class HintCue(
    val kind: HintKind,
    val remainingSec: Int
) {
    /** 라운드 시작 후 경과 초. 타이머를 증가 방식으로 돌리는 구현을 위한 편의 값입니다. */
    val elapsedSec: Int get() = ROUND_SECONDS - remainingSec
}

/**
 * 음절 수에 따라 힌트 개수와 공개 시각을 정하는 규칙표.
 * 규칙을 바꿀 일이 생기면 오직 이 파일만 고치면 됩니다.
 *
 * ── v4에서 무엇이 바뀌었나 ────────────────────────────────
 * v3까지는 "특징힌트 종류"와 "공개 시각"이 한 덩어리로 붙어 있었습니다.
 * 그래서 easy 하나가 검증에서 떨어지면 그 라운드의 특징힌트가 통째로 사라졌습니다.
 *
 * v4는 둘을 분리합니다.
 *   - [featureSlots] 는 "특징힌트가 나갈 자리(시각)" 만 정합니다. 어느 난이도가 올지는 모릅니다.
 *   - 그 자리에 무엇을 앉힐지는 [HintSelector] 가 살아남은 힌트로 정합니다.
 * 살아남은 힌트가 슬롯보다 적으면 **앞 슬롯부터 당겨서** 채웁니다(슬롯 압축).
 * easy -> normal -> hard 의 상대 순서는 절대 뒤집지 않으므로
 * "뒤로 갈수록 정보량이 많다" 는 게임의 약속은 압축 후에도 그대로 유지됩니다.
 *
 *   4음절에서 easy 가 떨어진 경우
 *     v3 : 특징힌트 0개 (강등)
 *     v4 : normal@25초, hard@15초  (5초 슬롯은 비워 둠)
 *
 * ── 음절 수별 자리표 ──────────────────────────────────────
 *   1음절   : 특징 25                      / 초성 없음
 *   2음절   : 특징 25                      / 초성 15
 *   3음절   : 특징 25, 20                  / 초성 15
 *   4~5음절 : 특징 25, 15, 5               / 초성 20, 10
 */
object RoundPlan {

    /** 특징힌트 난이도의 표준 순서. 이 순서는 화면 공개 순서이기도 합니다. */
    val FEATURE_KINDS: List<HintKind> = listOf(HintKind.EASY, HintKind.NORMAL, HintKind.HARD)

    /**
     * 특징힌트가 나갈 자리(남은 시간, 초). 리스트 길이가 곧 슬롯 개수입니다.
     *
     * 1음절이 2음절과 같은 자리표를 쓰는 것은 우연이 아닙니다.
     * 첫 힌트가 25초에 나오는 리듬을 모든 음절 수에서 똑같이 유지해야
     * 플레이어가 "이쯤이면 힌트가 나온다" 는 감각을 배울 수 있기 때문입니다.
     */
    fun featureSlots(syllableCount: Int): List<Int> = when {
        syllableCount <= 2 -> listOf(25)
        syllableCount == 3 -> listOf(25, 20)
        else -> listOf(25, 15, 5)
    }

    /**
     * 초성힌트가 나갈 자리. **1음절은 비어 있습니다.**
     *
     * 1음절은 음절을 하나라도 열면 그 순간 단어가 완성되어 정답 그 자체가 됩니다.
     * (`ㅁ` -> `말`) 중성만 여는 방식도 초성이 이미 화면에 나가 있으므로 마찬가지이고,
     * 종성만 여는 방식은 받침 없는 단어에서 열 것이 없어 규칙이 단어마다 달라집니다.
     * 그래서 1음절은 초성힌트를 포기하고 특징힌트 하나에 의존합니다.
     */
    fun chosungSlots(syllableCount: Int): List<Int> = when {
        syllableCount <= 1 -> emptyList()
        syllableCount <= 3 -> listOf(15)
        else -> listOf(20, 10)
    }

    /** 음절 수에 따른 특징힌트 개수(= 슬롯 개수). */
    fun featureHintCount(syllableCount: Int): Int = featureSlots(syllableCount).size

    /** 음절 수에 따른 초성힌트 개수. [RevealPlanner] 와 항상 같은 값이어야 합니다. */
    fun chosungHintCount(syllableCount: Int): Int = chosungSlots(syllableCount).size

    /**
     * 슬롯을 채울 때 시도하는 우선순위입니다. 앞에 있는 난이도부터 자리를 가져갑니다.
     *
     * 1음절만 normal 을 맨 앞에 둡니다. 팀 합의이자 게임 구조상 당연한 결과입니다.
     * 1음절은 후보를 넓게 좁혀 가는 게임이 아니라 두세 개 중 하나를 찍는 게임이라,
     * "정답을 특정하면 안 되는" easy 는 정보량이 사실상 0이기 때문입니다.
     * hard 가 두 번째인 것은 normal 이 떨어졌을 때 대체해도 손해가 없기 때문입니다.
     */
    fun featurePreference(syllableCount: Int): List<HintKind> =
        if (syllableCount <= 1) listOf(HintKind.NORMAL, HintKind.HARD, HintKind.EASY)
        else FEATURE_KINDS

    /**
     * 채택된 특징힌트 종류를 자리에 앉혀 최종 공개 일정을 만듭니다.
     *
     * @param featureKinds [HintSelector] 가 고른, 실제로 문구가 채워진 난이도들.
     *                     공개 순서대로 들어와야 합니다. 비어 있으면 초성힌트만 남습니다.
     */
    fun cues(syllableCount: Int, featureKinds: List<HintKind>): List<HintCue> {
        val slots = featureSlots(syllableCount)
        val feature = featureKinds.take(slots.size)
            .mapIndexed { i, kind -> HintCue(kind, slots[i]) }
        val chosung = chosungSlots(syllableCount)
            .mapIndexed { i, sec -> HintCue(if (i == 0) HintKind.WORD_1 else HintKind.WORD_2, sec) }

        // 남은 시간이 큰 것부터 = 먼저 공개되는 것부터.
        return (feature + chosung).sortedByDescending { it.remainingSec }
    }

    /** 모든 슬롯이 정상적으로 채워졌을 때의 일정. 테스트와 문서용 편의 함수입니다. */
    fun cues(syllableCount: Int): List<HintCue> =
        cues(syllableCount, featurePreference(syllableCount).take(featureHintCount(syllableCount)))

    /**
     * 특징힌트를 하나도 건지지 못했을 때의 일정. 초성힌트만 남습니다.
     * 게임은 어떤 경우에도 멈추면 안 되므로 이 경로가 반드시 있어야 합니다.
     *
     * **1음절에서는 이 일정이 비어 있습니다.** 그래서 폴백 힌트가 필요합니다.
     * ([FallbackHintRepository] 참고)
     */
    fun degradedCues(syllableCount: Int): List<HintCue> = cues(syllableCount, emptyList())
}
