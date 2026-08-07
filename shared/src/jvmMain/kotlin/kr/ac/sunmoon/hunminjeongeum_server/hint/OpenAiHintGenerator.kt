package kr.ac.sunmoon.hunminjeongeum_server.hint

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * OpenAI Chat Completions 로 특징힌트를 만드는 구현.
 *
 * 라운드가 30초뿐이므로 여기서는 오래 기다리지 않습니다.
 * 긴 재시도는 상위([HintService])의 시간 예산 안에서 관리하고,
 * 이 클래스는 "한 번의 논리적 생성" 안에서 일시적 네트워크 오류만 짧게 한 번 더 시도합니다.
 */
class OpenAiHintGenerator(
    private val apiKey: String,
    private val model: String,
    private val temperature: Double? = 0.8,
    /**
     * 난이도마다 만들 후보 개수.
     *
     * 3을 넘기지 마세요. 출력 토큰은 순차 생성이라 **출력량이 곧 응답 시간** 입니다.
     * 후보 3개(=문장 9개)면 응답이 10초를 넘기는 경우가 생기는데,
     * [HintConfig.generationBudgetMs] 기본값이 6초라 라운드가 통째로 강등됩니다.
     * 힌트를 더 건지려다 라운드를 잃는 셈입니다.
     */
    override val candidatesPerLevel: Int = Prompt.DEFAULT_CANDIDATES_PER_LEVEL,
    /** 429/5xx 같은 일시적 오류에 한해 이 횟수만큼 시도합니다. */
    private val httpAttempts: Int = 2,
    private val requestTimeoutMs: Long = 12_000
) : FeatureHintGenerator {

    override val name: String get() = model

    /** 스키마는 후보 개수에 따라 모양이 달라지므로 한 번만 만들어 재사용합니다. */
    private val schema = hintJsonSchema(candidatesPerLevel)

    private val effectiveCandidates = candidatesPerLevel.coerceIn(Prompt.CANDIDATE_RANGE)

    private val json = Json {
        ignoreUnknownKeys = true   // API가 필드를 추가해도 깨지지 않도록
        encodeDefaults = true
        explicitNulls = false      // temperature = null 이면 본문에서 아예 뺍니다
        isLenient = false
    }

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeoutMs
            connectTimeoutMillis = 5_000
        }
        expectSuccess = false      // 상태 코드를 직접 처리합니다
    }

    override suspend fun generate(entry: WordEntry, attempt: Int, feedback: String?): HintCandidates {
        val request = ChatRequest(
            model = model,
            messages = listOf(
                ChatMessage("system", Prompt.SYSTEM),
                ChatMessage("user", Prompt.buildUserMessage(entry, effectiveCandidates, feedback))
            ),
            responseFormat = ResponseFormat(
                type = "json_schema",
                jsonSchema = JsonSchemaSpec(
                    name = "hint_candidates",
                    strict = true,
                    schema = schema
                )
            ),
            temperature = temperature
        )

        var lastError = "알 수 없는 오류"

        for (round in 0 until httpAttempts) {
            val response = http.post(ENDPOINT) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val parsed: ChatResponse = response.body()
                val message = parsed.choices.firstOrNull()?.message
                    ?: throw OpenAiTransientException("응답에 choices 가 비어 있습니다")

                val refusal = message.refusal
                if (!refusal.isNullOrBlank()) {
                    throw OpenAiTransientException("모델이 요청을 거절했습니다: $refusal")
                }

                val content = message.content
                    ?: throw OpenAiTransientException("응답 content 가 null 입니다")

                // strict 스키마 덕분에 content 는 순수 JSON 문자열입니다.
                // 그래도 데이터 클래스로 직접 역직렬화하지 않고 JsonObject 로 한 번 받는 이유는,
                // 후보 개수(=키 개수)가 설정에 따라 달라져 고정 클래스로 표현할 수 없기 때문입니다.
                val root = try {
                    json.parseToJsonElement(content).jsonObject
                } catch (e: Exception) {
                    throw OpenAiTransientException("응답 JSON 파싱 실패: ${e.message}")
                }
                return parseHintCandidates(root, effectiveCandidates)
            }

            val code = response.status.value
            val bodyText = runCatching { response.bodyAsText() }.getOrDefault("")
            val head = bodyText.take(300)

            // 재시도해도 소용없는 오류는 즉시 중단합니다.
            when (code) {
                400 -> throw OpenAiFatalException("요청이 잘못되었습니다 (400). $head")
                401 -> throw OpenAiFatalException("API 키가 잘못되었습니다 (401). 환경변수 OPENAI_API_KEY 를 확인하세요.")
                403 -> throw OpenAiFatalException("권한이 없습니다 (403). $head")
                404 -> throw OpenAiFatalException("모델 '$model' 을 찾을 수 없습니다 (404). 모델명을 확인하세요.")
            }

            lastError = "HTTP $code: $head"

            // 429(요청 과다) 와 5xx(서버 오류) 만 여기까지 옵니다.
            if (round < httpAttempts - 1) delay(RETRY_DELAY_MS)
        }

        throw OpenAiTransientException(lastError)
    }

    override fun close() {
        http.close()
    }

    companion object {
        private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val RETRY_DELAY_MS = 400L
    }
}
