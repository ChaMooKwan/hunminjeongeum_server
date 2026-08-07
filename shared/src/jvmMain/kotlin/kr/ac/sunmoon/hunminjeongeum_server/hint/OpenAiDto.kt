package kr.ac.sunmoon.hunminjeongeum_server.hint

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// ─────────────────────────────────────────────────────────────
// Structured Outputs 용 JSON Schema
// ─────────────────────────────────────────────────────────────

/** 난이도별 스키마 설명문. 키 이름과 설명을 한곳에 묶어 둡니다. */
private val LEVEL_DESCRIPTIONS: List<Pair<String, String>> = listOf(
    "easy" to "가장 약한 힌트. 범위를 좁혀 주기만 하고 정답을 특정할 수는 없어야 한다.",
    "normal" to "중간 힌트. 눈에 보이는 특징이나 쓰임새를 말한다.",
    "hard" to "가장 결정적인 힌트. 이것을 들으면 대부분 정답을 떠올릴 수 있어야 한다."
)

/** 난이도와 후보 번호로 스키마 키를 만듭니다. `easy_1`, `normal_2` 형태입니다. */
fun candidateKey(level: String, index: Int): String = "${level}_$index"

/**
 * strict = true 로 두면 모델이 이 스키마를 100% 지키도록 강제됩니다.
 * 이게 없으면 모델이 ``` 코드펜스를 붙이거나 설명을 덧붙여서
 * 파싱이 무작위로 깨집니다. 실시간 생성에서는 특히 치명적입니다.
 *
 * strict 모드의 제약:
 *   - properties 에 선언한 키를 전부 required 에 넣어야 합니다.
 *   - additionalProperties 는 반드시 false 여야 합니다.
 *
 * ── 왜 배열이 아니라 `easy_1`, `easy_2` 인가 ────────────────
 * `"easy": { "type": "array" }` 가 훨씬 깔끔해 보이지만 쓰면 안 됩니다.
 * OpenAI strict 모드는 **minItems / maxItems 를 지원하지 않습니다.**
 * 배열로 두면 모델이 1개만 주거나 5개를 줘도 스키마 위반이 아니게 되어,
 * "난이도마다 반드시 n개" 라는 우리 전제가 깨집니다.
 * 고정 키를 required 에 전부 넣는 방식만이 개수를 강제할 수 있습니다.
 */
fun hintJsonSchema(candidatesPerLevel: Int): JsonObject {
    val n = candidatesPerLevel.coerceIn(Prompt.CANDIDATE_RANGE)
    return buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") {
            for ((level, description) in LEVEL_DESCRIPTIONS) {
                for (i in 1..n) {
                    putJsonObject(candidateKey(level, i)) {
                        put("type", "string")
                        put("description", "$description (후보 $i, 다른 후보와 접근 각도가 달라야 한다)")
                    }
                }
            }
            putJsonObject("rejected") {
                put("type", "boolean")
                put("description", "단어가 게임에 부적합하면 true.")
            }
            putJsonObject("reject_reason") {
                put("type", "string")
                put("description", "rejected 가 true 일 때의 사유. false 면 빈 문자열.")
            }
        }
        putJsonArray("required") {
            for ((level, _) in LEVEL_DESCRIPTIONS) {
                for (i in 1..n) add(candidateKey(level, i))
            }
            add("rejected")
            add("reject_reason")
        }
    }
}

/**
 * 모델이 돌려준 JSON 객체를 [HintCandidates] 로 바꿉니다.
 *
 * strict 스키마가 키를 보장하지만 **여기서 방어적으로 읽습니다.**
 * 스키마를 지원하지 않는 모델로 갈아 끼우거나, 향후 OpenAI가 동작을 바꿀 수 있고,
 * 그때 예외로 라운드가 죽는 것보다 후보가 적은 채로 굴러가는 편이 낫기 때문입니다.
 * 빈 문자열은 후보 목록에서 아예 빼서 [HintSelector] 가 헛되이 검사하지 않게 합니다.
 */
fun parseHintCandidates(root: JsonObject, candidatesPerLevel: Int): HintCandidates {
    val n = candidatesPerLevel.coerceIn(Prompt.CANDIDATE_RANGE)

    fun texts(level: String): List<String> = (1..n).mapNotNull { i ->
        root[candidateKey(level, i)]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }

    return HintCandidates(
        easy = texts("easy"),
        normal = texts("normal"),
        hard = texts("hard"),
        rejected = root["rejected"]?.jsonPrimitive?.booleanOrNull ?: false,
        rejectReason = root["reject_reason"]?.jsonPrimitive?.contentOrNull.orEmpty()
    )
}

// ─────────────────────────────────────────────────────────────
// Chat Completions API 요청/응답
// ─────────────────────────────────────────────────────────────

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class JsonSchemaSpec(
    val name: String,
    val strict: Boolean,
    val schema: JsonObject
)

@Serializable
data class ResponseFormat(
    val type: String,
    @SerialName("json_schema") val jsonSchema: JsonSchemaSpec
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat,
    /**
     * null 이면 요청 본문에서 아예 빠집니다(Json 설정의 explicitNulls = false).
     * temperature 를 지원하지 않는 모델도 있으므로 기본값을 null 로 두는 편이 안전합니다.
     */
    val temperature: Double? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null
)

@Serializable
data class ResponseMessage(
    val content: String? = null,
    /** 모델이 요청을 거절하면 content 대신 여기에 사유가 담깁니다. */
    val refusal: String? = null
)

@Serializable
data class Choice(
    val message: ResponseMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class ChatResponse(
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)
