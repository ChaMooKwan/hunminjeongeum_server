package kr.ac.sunmoon.hunminjeongeum_server.hint

/**
 * AI가 준 후보 더미에서 **실제로 화면에 나갈 힌트를 고릅니다.** v4의 핵심입니다.
 *
 * ── 왜 이 파일이 새로 생겼나 ──────────────────────────────
 * v3에서는 "검증"과 "채택"이 한 덩어리였습니다. 3개를 검사해서 전부 통과하면 쓰고,
 * 하나라도 틀리면 전부 버렸습니다. 실제 로그에서 이런 일이 벌어졌습니다.
 *
 *     [10/10] 음식  ㄱㅊㅉㄱ
 *          ! AI 힌트 없음 - 정답 누출: '찌개' 가 힌트에 포함됨
 *
 * easy 하나가 '찌개' 를 썼다는 이유로 normal 과 hard 를 읽어 보지도 않고 버린 것입니다.
 * v4는 검증(문장 하나가 나가도 되는가)과 채택(무엇을 어느 자리에 놓을까)을 분리하고,
 * 채택을 이 파일이 맡습니다.
 *
 * ── 두 겹의 방어 ──────────────────────────────────────────
 *   가로 (같은 난이도 안) : 후보 n개 중 통과하는 것 하나를 씁니다.
 *                          길이 초과·문장 흔들림 같은 **우발적** 실패를 막습니다.
 *   세로 (난이도를 가로질러) : 슬롯이 남으면 다음 난이도를 당겨 채웁니다.
 *                          그 난이도 축에서 필연적으로 나오는 **체계적** 실패를 막습니다.
 *
 * 김치찌개의 easy 는 "어느 나라 음식인지" 축을 지시받으므로 후보를 몇 개를 만들든
 * 자꾸 '찌개' 에 손을 댑니다. 가로 방어만으로는 못 막고, 세로 방어가 있어야 삽니다.
 * 반대로 normal 이 51자로 나온 것 같은 사고는 세로 방어보다 가로 방어가 쌉니다.
 * 그래서 둘 다 필요합니다.
 */
object HintSelector {

    /**
     * 채택 결과.
     *
     * @param texts     난이도 -> 채택된 문장. **공개 순서대로** 순회됩니다(LinkedHashMap).
     * @param promoted  앞 난이도가 죽어 뒤 난이도가 앞 슬롯으로 당겨졌는지.
     * @param rejections 떨어진 후보들의 사유 전부. 프롬프트를 고칠 때 보는 재료입니다.
     */
    data class Selection(
        val texts: Map<HintKind, String>,
        val slots: Int,
        val promoted: Boolean,
        val rejections: List<String>
    ) {
        /** 채운 슬롯 수. */
        val filled: Int get() = texts.size

        /** 공개 순서대로의 난이도 목록. [RoundPlan.cues] 에 그대로 넘깁니다. */
        val kinds: List<HintKind> get() = texts.keys.toList()

        /** 필요한 슬롯을 전부 채웠는지. */
        val isComplete: Boolean get() = filled >= slots

        /** 특징힌트를 하나도 못 건졌는지. 이때만 강등(degraded)입니다. */
        val isEmpty: Boolean get() = filled == 0

        /** 로그에 남길 한 줄 요약. */
        fun summary(): String = when {
            isComplete && !promoted -> ""
            isEmpty -> "특징힌트 전멸 (후보 ${rejections.size}개 전부 탈락)"
            else -> "슬롯 $filled/$slots 채움" +
                (if (promoted) " · 승격(${kinds.joinToString("→") { it.label }})" else "")
        }
    }

    /**
     * @param entry      정답 단어. 누출 검사의 기준입니다.
     * @param candidates AI가 준 난이도별 후보 묶음.
     */
    fun select(entry: WordEntry, candidates: HintCandidates): Selection {
        val syllables = entry.syllableCount
        val slots = RoundPlan.featureHintCount(syllables)
        val preference = RoundPlan.featurePreference(syllables)

        // 모델이 스스로 부적합 판정한 단어는 후보를 보지 않습니다.
        // 뜻을 모르면서 지어낸 문장일 가능성이 높아, 검증을 통과해도 신뢰할 수 없습니다.
        if (candidates.rejected) {
            val why = candidates.rejectReason.ifBlank { "사유 미기재" }
            return Selection(emptyMap(), slots, false, listOf("모델 부적합 판정: $why"))
        }

        val chosen = LinkedHashMap<HintKind, String>()
        val usedTexts = mutableListOf<String>()
        val rejections = mutableListOf<String>()

        for (kind in preference) {
            if (chosen.size >= slots) break

            for ((index, raw) in candidates.of(kind).withIndex()) {
                val text = raw.trim()
                val suffix = " (후보 ${index + 1})"

                // 사유 형식: "[특징힌트(중)] 정답 누출: '말' 가 힌트에 포함됨 (후보 1)"
                // --audit 이 "] " 뒤 ":" 앞을 잘라 사유별로 집계하므로 이 형식을 유지하세요.
                val check = HintValidator.checkHint(entry, kind, text)
                if (!check.valid) {
                    rejections += check.reason + suffix
                    continue
                }

                // 중복 검사를 여기서 하는 이유:
                // 후보가 여러 개가 되면서 easy#2 와 normal#1 이 같은 말인 경우가 생깁니다.
                // v3처럼 "3개를 다 고른 뒤" 비교하면 이미 늦습니다. 고르는 순간 걸러야
                // 다음 후보로 넘어갈 기회가 남습니다.
                val normalized = HintValidator.normalizeForCompare(text)
                if (normalized in usedTexts) {
                    rejections += "[${kind.label}] 이미 채택된 힌트와 중복$suffix"
                    continue
                }

                chosen[kind] = text
                usedTexts += normalized
                break
            }
        }

        // 승격 여부: 우선순위 앞쪽부터 그대로 채워졌으면 승격이 아닙니다.
        val promoted = chosen.keys.toList() != preference.take(chosen.size)

        return Selection(chosen, slots, promoted, rejections)
    }

    /** 후보가 아예 없을 때(네트워크 실패 등) 쓰는 빈 결과. */
    fun empty(entry: WordEntry, reason: String): Selection =
        Selection(emptyMap(), RoundPlan.featureHintCount(entry.syllableCount), false, listOf(reason))
}
