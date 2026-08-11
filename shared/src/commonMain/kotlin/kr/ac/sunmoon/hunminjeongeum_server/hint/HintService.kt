package kr.ac.sunmoon.hunminjeongeum_server.hint

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** 실시간 생성의 시간 예산. 라운드가 30초라 여유가 없습니다. */
data class HintConfig(
    /** 검증 실패 시 다시 만들어 보는 총 횟수(첫 시도 포함). */
    val maxGenerationAttempts: Int = 2,
    /** 한 라운드분 힌트를 만드는 데 쓸 수 있는 전체 시간. 넘으면 있는 것만 내보냅니다. */
    val generationBudgetMs: Long = 6_000,
    /**
     * 슬롯을 일부만 채웠을 때도 다시 시도할지.
     *
     * true 면 4음절에서 2/3만 채운 경우에도 한 번 더 불러 3개를 노립니다.
     * 실패해도 손해가 없습니다. **직전 시도 결과를 버리지 않고 들고 있다가**
     * 더 나은 것이 나오지 않으면 그대로 쓰기 때문입니다.
     * 대신 API 호출이 늘어나므로, 비용이 부담되면 false 로 두세요.
     */
    val retryOnPartialFill: Boolean = true
)

class HintServiceException(message: String) : RuntimeException(message)

/**
 * 게임 서버가 실제로 쓰는 진입점.
 *
 * ── 왜 프리페치인가 ────────────────────────────────────────
 * 라운드가 시작될 때 API를 부르면 응답이 오기까지 1~3초 동안 화면이 비고,
 * 드물게 타임아웃이 나면 그 라운드는 힌트 없이 흘러갑니다.
 * 그래서 **N라운드가 진행되는 동안 N+1라운드의 단어를 뽑아 힌트를 미리 만들어 둡니다.**
 * 30초라는 넉넉한 시간이 통째로 생기므로 체감 지연이 0이 되고, 재시도 여유도 생깁니다.
 *
 * ── 사용법 ────────────────────────────────────────────────
 *     val service = HintService(repository, generator, fallback)
 *     service.prepare("동물")                  // 게임 시작 직전 1라운드 예열
 *     repeat(totalRound) {
 *         val round = service.nextRound("동물") // 즉시 반환 + 다음 라운드 미리 생성 시작
 *         // round.wordQuiz 를 화면에, round.word 는 정답 판정에만
 *     }
 *     service.close()
 *
 * ── 스레드 안전 ────────────────────────────────────────────
 * 내부 상태는 [Mutex] 로 보호합니다. 여러 방(room)을 동시에 돌린다면
 * 방마다 [HintService] 인스턴스를 따로 만드세요. 출제 이력이 방 단위이기 때문입니다.
 */
class HintService(
    private val repository: WordRepository,
    private val generator: FeatureHintGenerator,
    /**
     * AI가 전멸했을 때 쓸 최후의 문장들. 기본값은 "폴백을 쓰지 않음" 입니다.
     * 1음절 단어를 출제한다면 반드시 넣으세요. 없으면 빈 라운드가 나올 수 있습니다.
     */
    private val fallback: FallbackHintRepository = FallbackHintRepository.EMPTY,
    private val config: HintConfig = HintConfig(),
    private val random: Random = Random.Default,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : AutoCloseable {

    private val mutex = Mutex()

    /** 이번 게임에서 이미 출제한 단어 id(정수). 같은 단어가 두 번 나오지 않게 합니다. */
    private val usedWordIds = LinkedHashSet<Int>()

    private var pending: Deferred<RoundHints>? = null
    private var pendingCategory: String? = null

    /** 직전에 뽑은 단어. 이력을 비우고 순환할 때 연속 출제를 피하는 데만 씁니다. */
    private var lastIssuedId: Int? = null

    /** 게임 시작 직전에 한 번 불러 1라운드를 미리 만들어 둡니다. */
    suspend fun prepare(category: String) {
        mutex.withLock {
            if (pending == null || pendingCategory != category) {
                pending?.cancel()
                pending = schedule(category)
                pendingCategory = category
            }
        }
    }

    /**
     * 이번 라운드에 쓸 힌트를 돌려주고, 곧바로 다음 라운드 생성을 시작합니다.
     * [prepare] 를 먼저 불렀다면 거의 즉시 반환됩니다.
     */
    suspend fun nextRound(category: String, prefetchNext: Boolean = true): RoundHints {
        val current = mutex.withLock {
            val ready = pending?.takeIf { pendingCategory == category }
            if (ready == null) pending?.cancel()
            val chosen = ready ?: schedule(category)

            // 다음 라운드 프리페치. 마지막 라운드에서는 prefetchNext = false 로 불러
            // 쓰지도 않을 API 호출을 아끼세요.
            pending = if (prefetchNext) schedule(category) else null
            pendingCategory = if (prefetchNext) category else null
            chosen
        }
        return current.await()
    }

    /** 새 게임을 시작할 때 출제 이력을 비웁니다. */
    suspend fun reset() {
        mutex.withLock {
            pending?.cancel()
            pending = null
            pendingCategory = null
            usedWordIds.clear()
            lastIssuedId = null
        }
    }

    /** 지금까지 출제한 단어 id 목록. */
    suspend fun issuedWordIds(): List<Int> = mutex.withLock { usedWordIds.toList() }

    override fun close() {
        pending?.cancel()
        pending = null
        scope.cancel()
        generator.close()
    }

    // ─────────────────────────────────────────────────────────
    // 내부
    // ─────────────────────────────────────────────────────────

    /**
     * 단어 선택은 여기서 **동기적으로** 끝냅니다(호출자가 mutex 를 잡은 상태).
     * 출제 이력 갱신이 백그라운드로 밀리면 같은 단어가 두 번 뽑힐 수 있기 때문입니다.
     * 오래 걸리는 것은 AI 호출뿐이므로 그것만 [scope] 로 넘깁니다.
     */
    private suspend fun schedule(category: String): Deferred<RoundHints> {
        val entry = pickWord(category)
        // 난수 사용을 전부 여기(잠금 안)로 모읍니다.
        // 시드를 고정한 Random 은 스레드 안전하지 않아서, 백그라운드에서 쓰면
        // 테스트 재현성이 깨질 수 있습니다.
        val chosungHints = RevealPlanner.plan(entry.word, random)
        return scope.async { buildRound(entry, chosungHints) }
    }

    private suspend fun pickWord(category: String): WordEntry {
        var entry = repository.pickRandom(category, usedWordIds, random)
        if (entry == null) {
            // 카테고리 단어를 한 바퀴 다 썼습니다. 이력을 비우고 다시 돕니다.
            // 이때 직전 단어가 곧바로 또 나오면 플레이어 눈에는 버그로 보이므로 한 번 피해 봅니다.
            usedWordIds.clear()
            val avoidRepeat = setOfNotNull(lastIssuedId)
            entry = repository.pickRandom(category, avoidRepeat, random)
                ?: repository.pickRandom(category, emptySet(), random)
        }
        if (entry == null) {
            throw HintServiceException("카테고리 '$category' 에 출제 가능한 단어가 없습니다")
        }
        usedWordIds.add(entry.id)
        lastIssuedId = entry.id
        return entry
    }

    private data class Outcome(
        val selection: HintSelector.Selection,
        val reason: String,
        val attempts: Int
    )

    /** @param chosungHints 코드가 이미 확정해 둔 초성 힌트. AI와 무관하며 절대 실패하지 않습니다. */
    private suspend fun buildRound(entry: WordEntry, chosungHints: List<String>): RoundHints {
        val startedAt = System.currentTimeMillis()
        val syllables = entry.syllableCount

        val outcome = generateWithRetry(entry)
        val elapsed = System.currentTimeMillis() - startedAt

        var texts = outcome.selection.texts
        var promoted = outcome.selection.promoted
        var source = if (texts.isEmpty()) HintSource.NONE else HintSource.AI
        val notes = mutableListOf<String>()

        if (outcome.reason.isNotBlank()) notes += outcome.reason

        // AI가 전멸했을 때만 폴백을 꺼냅니다.
        // 일부라도 살아 있으면 그쪽이 이 단어에 더 맞는 문장이므로 건드리지 않습니다.
        if (texts.isEmpty()) {
            val spare = fallback.hintFor(entry)
            if (spare != null) {
                // 폴백은 항상 normal 자리로 보냅니다.
                // easy 는 "정답을 특정하면 안 되는" 힌트라 최후의 한 방으로는 약하고,
                // 자리를 하나로 고정해 두어야 UI 팀이 예측할 수 있기 때문입니다.
                texts = mapOf(HintKind.NORMAL to spare)
                source = HintSource.FALLBACK
                promoted = false
                notes += "폴백 힌트로 대체"
            }
        }

        // degraded 의 정의는 "AI가 쓸 만한 특징힌트를 하나도 만들지 못했다" 입니다.
        // 폴백이 화면을 채웠더라도 AI는 실패한 것이므로 true 를 유지합니다.
        // 여기서 false 로 바꾸면 실패율 통계가 거짓말을 하고 프롬프트를 고칠 수 없게 됩니다.
        val degraded = outcome.selection.isEmpty

        val cues = RoundPlan.cues(syllables, texts.keys.toList())

        val round = RoundHints(
            wordId = entry.id,
            word = entry.word,
            wordQuiz = entry.wordQuiz,
            quizCategory = entry.quizCategory,
            quizCategoryName = entry.quizCategoryName,
            // 이 라운드에서 쓰지 않는 힌트는 아예 빈 문자열로 지웁니다.
            // 검증하지 않은 문장이 실수로 화면에 나가는 사고를 원천 차단합니다.
            easyHint = texts[HintKind.EASY].orEmpty(),
            normalHint = texts[HintKind.NORMAL].orEmpty(),
            hardHint = texts[HintKind.HARD].orEmpty(),
            easyWordHint = chosungHints.getOrElse(0) { "" },
            normalWordHint = chosungHints.getOrElse(1) { "" },
            cues = cues,
            degraded = degraded,
            note = notes.filter { it.isNotBlank() }.joinToString(" · "),
            hintSource = source,
            slotPromoted = promoted,
            generatedBy = "${generator.name}/${Prompt.PROMPT_VERSION}",
            latencyMs = elapsed
        )

        // 30초 동안 아무것도 못 내는 라운드는 게임이 멈춘 것과 같습니다.
        // 여기까지 왔다는 것은 1음절 단어인데 폴백도 없다는 뜻이므로 사유를 남겨 둡니다.
        return if (round.isEmptyRound) {
            round.copy(note = (round.note + " · 힌트 0개 라운드! 폴백 힌트를 등록하세요").trim(' ', '·'))
        } else {
            round
        }
    }

    /**
     * 시간 예산 안에서 생성 -> 선택 -> 부족하면 사유를 되먹여 재생성.
     *
     * v3와 결정적으로 다른 점: **중간 결과를 버리지 않습니다.**
     * 2/3만 채운 시도가 있었다면 그것을 들고 있다가, 재시도가 더 나아지지 않으면
     * 그대로 씁니다. 재시도가 손해로 이어지는 경우를 없앤 것입니다.
     */
    private suspend fun generateWithRetry(entry: WordEntry): Outcome {
        var best: HintSelector.Selection? = null
        var tried = 0

        val result = withTimeoutOrNull(config.generationBudgetMs) {
            var reason = "생성을 시도하지 못했습니다"

            for (attempt in 1..config.maxGenerationAttempts) {
                tried = attempt

                val produced = try {
                    generator.generate(entry, attempt, if (attempt == 1) null else reason)
                } catch (e: CancellationException) {
                    // 시간 초과로 인한 취소는 반드시 그대로 던져야 합니다.
                    // 여기서 삼키면 withTimeoutOrNull 이 동작하지 않습니다.
                    throw e
                } catch (e: OpenAiFatalException) {
                    // 키·모델명 오류 등은 다시 해봐야 똑같습니다.
                    return@withTimeoutOrNull Outcome(
                        best ?: HintSelector.empty(entry, e.message ?: "치명적 오류"),
                        e.message ?: "치명적 오류",
                        attempt
                    )
                } catch (e: Exception) {
                    reason = e.message ?: e.toString()
                    continue
                }

                val selection = HintSelector.select(entry, produced)
                if (best == null || selection.filled > best!!.filled) best = selection

                if (selection.isComplete) {
                    return@withTimeoutOrNull Outcome(selection, selection.summary(), attempt)
                }
                if (!config.retryOnPartialFill && !selection.isEmpty) {
                    return@withTimeoutOrNull Outcome(selection, selection.summary(), attempt)
                }

                // 다음 시도에 되먹일 사유. 가장 먼저 걸린 후보의 사유를 씁니다.
                reason = selection.rejections.firstOrNull() ?: selection.summary()
            }

            val final = best ?: HintSelector.empty(entry, reason)
            Outcome(final, final.summary().ifBlank { reason }, config.maxGenerationAttempts)
        }

        if (result != null) return result

        val timedOut = best ?: HintSelector.empty(entry, "시간 초과")
        return Outcome(
            timedOut,
            "시간 초과(${config.generationBudgetMs}ms) - ${timedOut.summary().ifBlank { "결과 없음" }}",
            tried
        )
    }
}
