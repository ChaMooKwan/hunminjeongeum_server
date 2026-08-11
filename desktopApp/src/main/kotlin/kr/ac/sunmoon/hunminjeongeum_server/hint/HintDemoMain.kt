package kr.ac.sunmoon.hunminjeongeum_server.hint

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.random.Random

/**
 * 혼자서 기능을 확인하기 위한 실행기입니다. 게임 서버가 쓰는 코드는 [HintService] 쪽입니다.
 *
 * ── 실행 방법 (IntelliJ) ───────────────────────────────────
 * 이 파일의 `fun main` 왼쪽 ▶ 를 눌러 실행합니다. 인자·환경변수는
 * Run > Edit Configurations 에서 넣습니다.
 *   - Program arguments : 아래 옵션들 (예: --fake --rounds=6)
 *   - Environment variables : OPENAI_API_KEY=sk-...  (실제 AI 를 쓸 때만)
 *
 * ── 실행 예 (Program arguments 에 넣을 값) ─────────────────
 *   --fake --rounds=6                       API 키 없이 전체 흐름만 확인(무료, 즉시)
 *   --fake --failAi --rounds=4 --category=과일   1음절 폴백 경로까지 확인
 *   --rounds=5 --category=동물               실제 OpenAI 로 5라운드(키 필요)
 *   --audit                                 전 단어를 한 번씩 돌려 실패율 측정(키 필요)
 *   --audit --candidates=1                  후보 1개일 때와 비교
 *   --db --rounds=5 --category=나라          로컬 JSON 대신 실제 Supabase DB 사용(키 필요)
 *
 * ── 옵션 ──────────────────────────────────────────────────
 *   --words=<경로>      입력 단어 JSON            (기본 words_sample.json)
 *   --fallback=<경로>   폴백 힌트 JSON            (기본 fallback_hints.json)
 *   --out=<경로>        결과 출력 폴더            (기본 out)
 *   --rounds=<n>        진행할 라운드 수          (기본 6)
 *   --category=<이름>   한 카테고리만 사용        (기본: 모든 카테고리를 번갈아)
 *   --seed=<n>          난수 시드 고정            (기본: 매번 다름)
 *   --model=<이름>      모델명                    (기본: 환경변수 OPENAI_MODEL 또는 gpt-4.1-nano)
 *   --candidates=<n>    난이도별 후보 개수 1~3    (기본 2)
 *   --playMs=<ms>       라운드 진행 시간을 흉내   (기본 0. 프리페치 효과를 보려면 2000 정도)
 *   --fake              API를 부르지 않고 가짜 힌트 사용
 *   --failAi            AI가 항상 실패하는 상황을 흉내 (--fake 와 함께 씀. 폴백 확인용)
 *   --audit             라운드 진행 대신 전 단어 품질 점검 (로컬 JSON 전용)
 *   --db                로컬 JSON 대신 실제 단어 DB(Supabase) 사용 (audit 과 함께 못 씀)
 */
fun main(args: Array<String>) = runBlocking {
    val problem = WordEntry(
        id = 1,
        word = "사과",
        quizCategory = 1,
        wordQuiz = "ㅅㄱ"
    )

    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: error("OPENAI_API_KEY가 없습니다.")

    val generator = OpenAiHintGenerator(
        apiKey = apiKey,
        model = "gpt-4.1-nano",
        candidatesPerLevel = 2
    )

    generator.use {
        try {
            val result = generator.generate(
                entry = problem,
                attempt = 1,
            )

            println("문제: ${problem.word}")
            println("초성: ${problem.wordQuiz}")
            println("카테고리: ${problem.quizCategoryName}")
            println()

            println("생성된 힌트:")
            println(result)

        } catch (e: Exception) {
            println("LLM 요청 실패: ${e.message}")
        }
    }
}
