package kr.ac.sunmoon.hunminjeongeum_server.hint

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 사람이 미리 써 둔 최후의 힌트 한 문장을 보관합니다.
 *
 * ── 왜 필요한가 ───────────────────────────────────────────
 * 다음절 단어는 AI가 전멸해도 초성힌트가 남습니다. 라운드가 심심해질 뿐 굴러갑니다.
 * 그런데 **1음절은 초성힌트가 없어서 남는 것이 하나도 없습니다.**
 * 30초 동안 화면에 아무것도 없는 라운드가 되는데, 그건 게임이 멈춘 것과 같습니다.
 *
 * 후보 과생성도 슬롯 압축도 이 구멍을 못 막습니다. 둘 다 "AI 응답이 왔다" 는 전제 위에
 * 서 있는데, 키가 틀렸거나 네트워크가 끊기면 응답 자체가 없기 때문입니다.
 * 그때 마지막으로 남는 것이 이 파일입니다.
 *
 * ── 설계상 지킨 것 ────────────────────────────────────────
 *   - 파일이 없으면 기능이 그냥 꺼진 채 동작합니다. 필수 의존이 아닙니다.
 *   - DB에 컬럼을 요청할 필요가 없습니다. 이 모듈이 파일 하나로 소유합니다.
 *   - **사람이 쓴 문장도 [HintValidator] 로 검사합니다.** 손으로 쓰다 보면
 *     정답을 그대로 넣는 실수가 반드시 나옵니다. ("배는 가을 과일입니다")
 *   - 폴백이 나가도 `degraded = true` 를 유지합니다.
 *     화면은 안 비게 하되 AI 실패 통계는 정직하게 남겨야 프롬프트를 고칠 수 있습니다.
 */
class FallbackHintRepository private constructor(
    private val byWord: Map<String, String>,
    /** 검사에서 떨어진 항목과 사유. 실행 시 화면에 찍어 사람이 고치게 합니다. */
    val rejected: List<Pair<FallbackHint, String>>
) {

    val size: Int get() = byWord.size

    val isEmpty: Boolean get() = byWord.isEmpty()

    /** 이 단어의 폴백 힌트. 없으면 null 입니다. */
    fun hintFor(entry: WordEntry): String? = byWord[entry.word.trim()]

    /** 폴백이 없는 단어들. 1음절인데 여기 걸리면 빈 라운드 위험이 있다는 뜻입니다. */
    fun missingFor(words: List<WordEntry>): List<WordEntry> =
        words.filterNot { byWord.containsKey(it.word.trim()) }

    companion object {

        /** 폴백을 쓰지 않을 때. 파일이 없어도 서비스가 그대로 돌아가게 합니다. */
        val EMPTY: FallbackHintRepository = FallbackHintRepository(emptyMap(), emptyList())

        /**
         * 항목들을 검사해 담습니다.
         *
         * 검사 기준은 AI 힌트와 완전히 같습니다(길이, 정답 누출, 금지 표현).
         * 사람이 썼다고 봐주면 안 됩니다. 오히려 사람이 쓴 문장은 아무도 다시 안 보므로
         * 여기서 못 잡으면 시연 당일에 정답이 그대로 화면에 뜹니다.
         */
        fun of(entries: List<FallbackHint>): FallbackHintRepository {
            val ok = LinkedHashMap<String, String>()
            val ng = mutableListOf<Pair<FallbackHint, String>>()

            for (item in entries) {
                val word = item.word.trim()
                // 폴백 검사에서 보고 싶은 것은 힌트 내용(길이·누출·금지어)뿐입니다.
                // 카테고리 이름이 비었거나 우리가 모르는 값이어도 폴백 등록을 막을 이유는 없으므로,
                // 모르면 유효한 아무 ID(과일=1) 로 대체해 validateWord 의 카테고리 검사를 통과시킵니다.
                val categoryId = QuizCategory.indexOfLabel(item.category) ?: QuizCategory.FRUIT.index
                val probe = WordEntry(
                    id = 0,   // 폴백은 DB 행이 아니므로 임의 값. 검사에 쓰이지 않습니다.
                    word = word,
                    quizCategory = categoryId
                )

                val wordCheck = HintValidator.validateWord(probe)
                if (!wordCheck.valid) {
                    ng += item to wordCheck.reason
                    continue
                }

                // 폴백은 항상 normal 자리에 들어가므로 그 난이도로 검사합니다.
                val hintCheck = HintValidator.checkHint(probe, HintKind.NORMAL, item.hint)
                if (!hintCheck.valid) {
                    ng += item to hintCheck.reason
                    continue
                }
                if (ok.containsKey(word)) {
                    ng += item to "같은 단어의 폴백이 이미 있습니다"
                    continue
                }
                ok[word] = item.hint.trim()
            }
            return FallbackHintRepository(ok, ng)
        }

        /**
         * 파일에서 읽습니다. **파일이 없거나 깨져 있어도 예외를 던지지 않습니다.**
         * 폴백은 게임을 살리기 위한 장치인데 그것 때문에 게임이 안 뜨면 본말전도입니다.
         *
         * @return 읽지 못했으면 [EMPTY] 와 사유
         */
        fun fromJsonFileOrEmpty(file: File, json: Json): Pair<FallbackHintRepository, String?> {
            if (!file.exists()) return EMPTY to "폴백 파일 없음 (${file.path})"
            return try {
                val text = file.readText(Charsets.UTF_8)
                val items = json.decodeFromString(ListSerializer(FallbackHint.serializer()), text)
                of(items) to null
            } catch (e: Exception) {
                EMPTY to "폴백 파일을 읽지 못했습니다: ${e.message}"
            }
        }
    }
}
