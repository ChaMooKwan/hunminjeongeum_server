package kr.ac.sunmoon.hunminjeongeum_server.hint

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * JSON 직렬화·파일 입출력을 한곳에 모은 헬퍼.
 *
 * ── 왜 이 파일이 v5 에서 새로 생겼나 ──────────────────────
 * 확인용 진입점([HintDemoMain]) 은 `desktopApp` 모듈에 있는데, 그 모듈에는
 * kotlinx-serialization 의존성이 **없습니다**(팀의 build.gradle.kts 기준).
 * serialization 은 `shared` 에만 `implementation` 으로 있어 desktopApp 으로 전파되지 않습니다.
 *
 * 그래서 진입점이 `Json` 이나 `.serializer()` 를 직접 만지면 컴파일이 깨집니다.
 * 직렬화가 필요한 일을 전부 이 오브젝트(=serialization 을 가진 shared 안)로 옮기고,
 * 진입점은 이 함수들만 부릅니다. 진입점은 serialization 을 몰라도 됩니다.
 *
 * 이 파일은 `java.io.File` 을 쓰므로 shared 의 **jvmMain** 에 둡니다.
 */
object HintJson {

    /** 출력 파일에 쓸 사람이 읽기 좋은 설정. */
    private val pretty = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 단어 파일을 읽어 저장소를 만듭니다. 파일 형식은 words_sample.json 과 같습니다. */
    fun loadWords(file: File): InMemoryWordRepository =
        InMemoryWordRepository.fromJsonFile(file, pretty)

    /** 폴백 파일을 읽습니다. 없거나 깨져도 예외 없이 (EMPTY, 사유) 를 돌려줍니다. */
    fun loadFallback(file: File): Pair<FallbackHintRepository, String?> =
        FallbackHintRepository.fromJsonFileOrEmpty(file, pretty)

    /** 라운드 목록을 JSON 파일로 저장합니다. */
    fun writeRounds(file: File, rounds: List<RoundHints>) {
        file.writeText(
            pretty.encodeToString(ListSerializer(RoundHints.serializer()), rounds),
            Charsets.UTF_8
        )
    }

    /** 생성 로그를 JSON 파일로 저장합니다. */
    fun writeLogs(file: File, logs: List<GenerationLog>) {
        file.writeText(
            pretty.encodeToString(ListSerializer(GenerationLog.serializer()), logs),
            Charsets.UTF_8
        )
    }
}
