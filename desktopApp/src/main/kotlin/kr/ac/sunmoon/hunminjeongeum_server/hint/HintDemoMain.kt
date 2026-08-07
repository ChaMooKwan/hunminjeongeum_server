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

    val wordsPath = argOf(args, "words") ?: "words_sample.json"
    val fallbackPath = argOf(args, "fallback") ?: "fallback_hints.json"
    val outputDir = argOf(args, "out") ?: "out"
    val rounds = argOf(args, "rounds")?.toIntOrNull() ?: 6
    val onlyCategory = argOf(args, "category")
    val seed = argOf(args, "seed")?.toLongOrNull()
    val playMs = argOf(args, "playMs")?.toLongOrNull() ?: 0L
    val candidates = (argOf(args, "candidates")?.toIntOrNull() ?: Prompt.DEFAULT_CANDIDATES_PER_LEVEL)
        .coerceIn(Prompt.CANDIDATE_RANGE)
    val useFake = hasFlag(args, "fake")
    val failAi = hasFlag(args, "failAi")
    val auditMode = hasFlag(args, "audit")
    // --db 를 주면 JSON 대신 실제 단어 DB(Supabase) 를 씁니다.
    // audit 은 로컬 데이터 품질 점검이 목적이라 DB 모드에서는 지원하지 않습니다.
    val useDb = hasFlag(args, "db")

    val (fallback, fallbackNote) = HintJson.loadFallback(File(fallbackPath))
    val generator = createGenerator(useFake, failAi, argOf(args, "model"), candidates)
        ?: return@runBlocking
    val random = if (seed != null) Random(seed) else Random.Default

    File(outputDir).mkdirs()

    // ── DB 모드: 실제 Supabase 를 감싼 어댑터로 라운드만 진행 ──
    if (useDb) {
        if (auditMode) {
            System.err.println("[오류] --audit 은 로컬 JSON 전용입니다. --db 와 함께 쓸 수 없습니다.")
            return@runBlocking
        }
        val dbRepo = SupabaseWordRepository()
        println("초성 게임 힌트 생성기 v5 — [DB 모드] 모델 ${generator.name} / 프롬프트 ${Prompt.PROMPT_VERSION}")
        println("─".repeat(64))
        generator.use {
            runPlay(dbRepo, generator, fallback, outputDir, rounds, onlyCategory, random, playMs)
        }
        return@runBlocking
    }

    // ── 로컬 JSON 모드 (기본) ──
    val inputFile = File(wordsPath)
    if (!inputFile.exists()) {
        System.err.println("[오류] 입력 파일을 찾을 수 없습니다: ${inputFile.absolutePath}")
        return@runBlocking
    }

    val repository = try {
        HintJson.loadWords(inputFile)
    } catch (e: Exception) {
        System.err.println("[오류] 단어 파일을 읽지 못했습니다: ${e.message}")
        return@runBlocking
    }

    printHeader(repository, fallback, fallbackNote, generator, seed)

    generator.use {
        if (auditMode) {
            runAudit(repository, generator, outputDir)
        } else {
            runPlay(repository, generator, fallback, outputDir, rounds, onlyCategory, random, playMs)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 모드 1: 라운드 진행 흉내
// ─────────────────────────────────────────────────────────────

private suspend fun runPlay(
    repository: WordRepository,
    generator: FeatureHintGenerator,
    fallback: FallbackHintRepository,
    outputDir: String,
    totalRound: Int,
    onlyCategory: String?,
    random: Random,
    playMs: Long
) {
    val categories = when {
        onlyCategory != null -> listOf(QuizCategory.normalize(onlyCategory))
        else -> repository.categories()
    }.filter { repository.countOf(it) > 0 }

    if (categories.isEmpty()) {
        System.err.println("[오류] 출제 가능한 카테고리가 없습니다.")
        return
    }

    val service = HintService(repository, generator, fallback, random = random)
    val collected = mutableListOf<RoundHints>()

    try {
        // 1라운드는 게임 시작 직전에 미리 만들어 둡니다.
        val warmupStart = System.currentTimeMillis()
        service.prepare(categories[0])
        println("1라운드 예열 요청 완료 (${System.currentTimeMillis() - warmupStart}ms)\n")

        for (countRound in 1..totalRound) {
            val quizCategory = categories[(countRound - 1) % categories.size]
            val waited = System.currentTimeMillis()
            val round = service.nextRound(quizCategory, prefetchNext = countRound < totalRound)
            val waitMs = System.currentTimeMillis() - waited

            collected += round
            printRound(countRound, totalRound, round, waitMs)

            // 실제 게임에서는 여기서 30초가 흐릅니다.
            // 그동안 다음 라운드 힌트가 백그라운드에서 만들어집니다.
            if (playMs > 0) delay(playMs)
        }
    } catch (e: HintServiceException) {
        System.err.println("[중단] ${e.message}")
    } finally {
        service.close()
    }

    val file = File(outputDir, "rounds.json")
    HintJson.writeRounds(file, collected)

    val degraded = collected.count { it.degraded }
    val promoted = collected.count { it.slotPromoted }
    val fromFallback = collected.count { it.hintSource == HintSource.FALLBACK }
    val emptyRounds = collected.filter { it.isEmptyRound }
    val latencies = collected.map { it.latencyMs }.sorted()

    println("─".repeat(64))
    println("라운드 ${collected.size}개")
    println("  AI 특징힌트 전멸 ${degraded}개 / 그중 폴백으로 살린 라운드 ${fromFallback}개")
    println("  슬롯 승격이 일어난 라운드 ${promoted}개")
    if (latencies.isNotEmpty()) {
        println("  생성 소요 시간  최소 ${latencies.first()}ms / 중앙값 ${latencies[latencies.size / 2]}ms / 최대 ${latencies.last()}ms")
    }
    if (emptyRounds.isNotEmpty()) {
        System.err.println("  ! 힌트가 하나도 없는 라운드 ${emptyRounds.size}개: " +
            emptyRounds.joinToString(", ") { it.word })
        System.err.println("    fallback_hints.json 에 위 단어의 힌트를 등록하세요.")
    }
    println("결과 파일: ${file.path}")

    val reasons = collected.filter { it.degraded }.map { it.note }
    if (reasons.isNotEmpty()) {
        println("\n강등 사유:")
        reasons.groupingBy { it.substringBefore("]").take(40) }
            .eachCount()
            .entries.sortedByDescending { it.value }
            .forEach { println("  ${it.value}건  ${it.key}") }
    }
}

private fun printRound(countRound: Int, totalRound: Int, round: RoundHints, waitMs: Long) {
    val quiz = if (round.wordQuiz.length <= 1) round.wordQuiz else Hangul.spaced(round.wordQuiz)
    println("[$countRound/$totalRound] ${round.quizCategory}  $quiz   (대기 ${waitMs}ms / 생성 ${round.latencyMs}ms)")
    when {
        round.isEmptyRound -> println("     !! 힌트 0개 라운드 - ${round.note}")
        round.hintSource == HintSource.FALLBACK -> println("     ! 폴백 힌트 사용 - ${round.note}")
        round.degraded -> println("     ! AI 힌트 없음 - ${round.note}")
        round.slotPromoted -> println("     · 슬롯 승격 - ${round.note}")
    }
    for (hint in round.timeline()) {
        println("     ${hint.remainingSec.toString().padStart(2)}초 남음  ${hint.kind.label.padEnd(12)} ${hint.text}")
    }
    println("     정답: ${round.word}")
    println()
}

// ─────────────────────────────────────────────────────────────
// 모드 2: 품질 점검 (프롬프트 튜닝의 본체)
// ─────────────────────────────────────────────────────────────

private suspend fun runAudit(
    repository: InMemoryWordRepository,
    generator: FeatureHintGenerator,
    outputDir: String
) {
    val logs = mutableListOf<GenerationLog>()

    for ((index, entry) in repository.accepted.withIndex()) {
        print("[${index + 1}/${repository.accepted.size}] ${entry.word} (${entry.categoryLabel}) ... ")

        val slots = RoundPlan.featureHintCount(entry.syllableCount)
        val startedAt = System.currentTimeMillis()

        var best: HintSelector.Selection? = null
        var reason = "시도 없음"
        var attempts = 0
        var fatal = false

        for (attempt in 1..2) {
            attempts = attempt
            val produced = try {
                generator.generate(entry, attempt, if (attempt == 1) null else reason)
            } catch (e: OpenAiFatalException) {
                reason = e.message ?: "치명적 오류"
                fatal = true
                break
            } catch (e: Exception) {
                reason = e.message ?: e.toString()
                continue
            }

            val selection = HintSelector.select(entry, produced)
            if (best == null || selection.filled > best.filled) best = selection
            if (selection.isComplete) {
                reason = ""
                break
            }
            reason = selection.rejections.firstOrNull() ?: selection.summary()
        }

        val elapsed = System.currentTimeMillis() - startedAt
        val filled = best?.filled ?: 0
        val ok = filled >= slots

        println(
            when {
                ok -> "통과 (${elapsed}ms, ${attempts}회)"
                filled > 0 -> "부분 $filled/$slots (${elapsed}ms) - $reason"
                else -> "실패 - $reason"
            }
        )

        logs += GenerationLog(
            id = entry.id,
            word = entry.word,
            category = entry.categoryLabel,
            syllableCount = entry.syllableCount,
            ok = ok,
            reason = reason,
            attempts = attempts,
            latencyMs = elapsed,
            filled = filled,
            slots = slots,
            promoted = best?.promoted ?: false,
            easy = best?.texts?.get(HintKind.EASY).orEmpty(),
            normal = best?.texts?.get(HintKind.NORMAL).orEmpty(),
            hard = best?.texts?.get(HintKind.HARD).orEmpty(),
            wordHints = RevealPlanner.plan(entry.word, Random(entry.id.toLong())),
            rejectedCandidates = best?.rejections ?: listOf(reason),
            generatedBy = "${generator.name}/${Prompt.PROMPT_VERSION}"
        )

        if (fatal) {
            System.err.println("\n[중단] 복구 불가능한 오류라 점검을 멈춥니다: $reason")
            break
        }
        delay(200)   // 429 예방
    }

    HintJson.writeLogs(File(outputDir, "audit.json"), logs)
    HintJson.writeLogs(File(outputDir, "failed.json"), logs.filterNot { it.ok })

    // 프롬프트 버전과 후보 개수를 파일 이름에 박아 사본을 하나 더 남깁니다.
    // audit.json 은 매번 덮어써지지만, 이 사본은 버전마다 남아 v4↔v5 를 나란히
    // 비교할 수 있습니다. 이 기록이 곧 "프롬프트를 튜닝했다" 는 작업 증거입니다.
    val stamp = "${Prompt.PROMPT_VERSION}_c${generator.candidatesPerLevel}_${generator.name.replace('/', '-')}"
    HintJson.writeLogs(File(outputDir, "audit_$stamp.json"), logs)

    val ok = logs.count { it.ok }
    val total = logs.size
    val playable = logs.count { it.filled > 0 }
    println("─".repeat(64))
    if (total > 0) {
        val rate = 100.0 * (total - ok) / total
        val dead = 100.0 * (total - playable) / total
        println("프롬프트 ${Prompt.PROMPT_VERSION} / 후보 ${generator.candidatesPerLevel}개")
        println("  슬롯 전부 채움 $ok / $total   실패율 ${"%.1f".format(rate)}%")
        println("  특징힌트 전멸  ${total - playable} / $total   ${"%.1f".format(dead)}%  <- 이 값이 진짜 위험 지표입니다")
    }

    val bySyllable = logs.groupBy { it.syllableCount }
    if (bySyllable.size > 1) {
        println("\n음절 수별 전멸률:")
        for ((syllables, items) in bySyllable.entries.sortedBy { it.key }) {
            val dead = items.count { it.filled == 0 }
            println("  ${syllables}음절  ${dead}/${items.size}")
        }
    }

    val byCategory = logs.groupBy { it.category }
    if (byCategory.size > 1) {
        println("\n카테고리별 실패율:")
        for ((category, items) in byCategory.entries.sortedBy { it.key }) {
            val bad = items.count { !it.ok }
            println("  ${category.padEnd(6)} ${bad}/${items.size}")
        }
    }

    val failures = logs.filterNot { it.ok }
    if (failures.isNotEmpty()) {
        println("\n후보 탈락 사유 요약:")
        failures.flatMap { it.rejectedCandidates }
            .groupingBy { it.substringAfter("] ").substringBefore(":").trim() }
            .eachCount()
            .entries.sortedByDescending { it.value }
            .take(10)
            .forEach { println("  ${it.value}건  ${it.key}") }
        println("\nout/failed.json 을 열어 실제 문장을 보고 Prompt.kt 를 고치세요.")
    }
    println("결과 파일: $outputDir/audit.json, $outputDir/failed.json")
}

// ─────────────────────────────────────────────────────────────
// 보조
// ─────────────────────────────────────────────────────────────

private fun argOf(args: Array<String>, key: String): String? =
    args.firstOrNull { it.startsWith("--$key=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }

private fun hasFlag(args: Array<String>, key: String): Boolean = args.any { it == "--$key" }

private fun createGenerator(
    useFake: Boolean,
    failAi: Boolean,
    modelArg: String?,
    candidates: Int
): FeatureHintGenerator? {
    if (useFake) return FakeHintGenerator(candidatesPerLevel = candidates, alwaysFail = failAi)

    val apiKey = System.getenv("OPENAI_API_KEY")
    if (apiKey.isNullOrBlank()) {
        System.err.println(
            """
            [오류] 환경변수 OPENAI_API_KEY 가 설정되지 않았습니다.

              macOS / Linux :  export OPENAI_API_KEY="sk-..."
              Windows (PS)  :  ${'$'}env:OPENAI_API_KEY="sk-..."
              IntelliJ      :  Run > Edit Configurations > Environment variables

            키 없이 전체 흐름만 확인하려면 --fake 를 붙여 실행하세요.
            키를 코드에 직접 적지 마세요. 깃허브에 올라가는 순간 유출됩니다.
            """.trimIndent()
        )
        return null
    }

    val model = modelArg
        ?: System.getenv("OPENAI_MODEL")?.takeIf { it.isNotBlank() }
        ?: DEFAULT_MODEL
    return OpenAiHintGenerator(apiKey = apiKey, model = model, candidatesPerLevel = candidates)
}

/** 모델명은 여기 한 곳에서만 바꾸면 됩니다. platform.openai.com/docs/models 에서 정확한 문자열을 확인하세요. */
private const val DEFAULT_MODEL = "gpt-4.1-nano"

private fun printHeader(
    repository: InMemoryWordRepository,
    fallback: FallbackHintRepository,
    fallbackNote: String?,
    generator: FeatureHintGenerator,
    seed: Long?
) {
    println("초성 게임 힌트 생성기 v5 — 모델 ${generator.name} / 프롬프트 ${Prompt.PROMPT_VERSION} / 후보 ${generator.candidatesPerLevel}개")
    println("라운드 길이 ${ROUND_SECONDS}초" + if (seed != null) " / 시드 $seed" else "")
    println("출제 가능 단어 ${repository.accepted.size}개, 걸러진 단어 ${repository.rejected.size}개")

    for ((word, reason) in repository.rejected) {
        println("  제외  ${word.word.padEnd(8)} $reason")
    }

    val mismatches = repository.chosungMismatches()
    if (mismatches.isNotEmpty()) {
        println("  ! DB 초성이 계산값과 다른 단어 ${mismatches.size}개:")
        for (word in mismatches) {
            println("    ${word.word} — DB '${word.wordQuiz}' vs 계산 '${word.quizChosung}'")
        }
    }

    // 1음절은 초성힌트가 없어 폴백이 없으면 빈 라운드가 됩니다. 실행 전에 알려 줍니다.
    val singles = repository.singleSyllableWords()
    if (singles.isNotEmpty()) {
        println("  1음절 단어 ${singles.size}개: ${singles.joinToString(", ") { it.word }}")
        if (fallbackNote != null) println("    폴백: $fallbackNote")
        else println("    폴백 힌트 ${fallback.size}개 로드됨")
        val missing = fallback.missingFor(singles)
        if (missing.isNotEmpty()) {
            System.err.println("    ! 폴백이 없는 1음절 단어: ${missing.joinToString(", ") { it.word }}")
            System.err.println("      AI가 실패하면 이 단어들은 힌트 0개 라운드가 됩니다.")
        }
    }
    for ((item, reason) in fallback.rejected) {
        System.err.println("  ! 폴백 거부  ${item.word} — $reason")
    }

    // 같은 카테고리 안에서 초성이 겹치면 플레이어가 맞혀도 오답이 될 수 있습니다.
    val collisions = repository.chosungCollisions()
    if (collisions.isNotEmpty()) {
        println("  ! 초성이 겹치는 단어 묶음 ${collisions.size}개 (정답 판정 담당자에게 공유 필요):")
        for (group in collisions) {
            println("    ${group.first().categoryLabel} / ${group.first().quizChosung} — ${group.joinToString(", ") { it.word }}")
        }
    }
    println("─".repeat(64))
}
