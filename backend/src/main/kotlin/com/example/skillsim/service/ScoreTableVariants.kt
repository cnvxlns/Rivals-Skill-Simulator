package com.example.skillsim.service

import com.example.skillsim.enums.Handedness
import com.example.skillsim.model.ScoreSkill
import kotlin.math.floor

/**
 * 점수표에서 한 스킬을 여러 줄로 나누는 규칙.
 *
 * 어떤 조건은 경기 중에 우연히 걸리는 게 아니라 카드를 고르거나 라인업을 짜는 순간 정해진다.
 * 기본 주루+수비 합, 자기 능력치끼리의 비교, 투타, 타순, 선발 순번, 그리고 `정확 능력치의 2%`처럼
 * 능력치에 비례하는 효과가 그렇다. 이런 조건을 확률로 곱한 기댓값 한 줄로 두면 어느 카드·어느
 * 자리에 붙였을 때의 점수인지 알 수 없다. 그래서 경우마다 한 줄씩 나누고 이름 옆에 그 경우를
 * 적는다(`엘 그란데 (주+수합 155 이상 165 미만)`, `숨은 위협 (타순 6~9, 정확 150~199)`).
 *
 * 상대에 따라 달라지는 조건(`상대 구위 > 파워`, 주자, 이닝)은 나누지 않는다. 고를 수 없는 것이라
 * 발생 확률을 곱한 기댓값 한 줄이 맞고, 이름에도 적지 않는다.
 *
 * 라인업에서 고르는 조건(타순·순번)은 조건이 하나도 걸리지 않는 경우의 줄을 만들지 않는다. 그 자리에
 * 이 스킬을 쓸 이유가 없어서다. 카드가 타고나는 투타는 걸리지 않는 경우도 남긴다(`스위치 히터 아님`).
 */
internal object ScoreTableVariants {

    /**
     * 한 줄. [label]이 null이면 나누지 않은 원래 줄이다.
     * [overrides]는 확률표에, [stats]는 채점에 쓰는 능력치에 덮어쓴다.
     */
    data class Variant(
        val label: String?,
        val overrides: Map<String, Double>,
        val stats: Map<String, Double> = emptyMap(),
    )

    /** 경우 하나. 확률표에 덮어쓸 토큰 값과 능력치 값. */
    private data class Case(val tokens: Map<String, Double>, val stats: Map<String, Double> = emptyMap())

    /**
     * 서로 배타인 경우의 묶음.
     *
     * 스킬이 쓰는 토큰으로만 보면 같은 값이 되는 경우는 한 줄로 합친다. 타순3_4_5와 타순4_5를
     * 쓰는 스킬이라면 3번 / 4~5번 두 줄이 된다. [label]은 합쳐진 경우의 번호로 이름을 짓는다.
     *
     * @param keepInactive 스킬의 조건이 하나도 걸리지 않는 경우도 줄로 남길지.
     * @param tokenFree 토큰이 아니라 능력치로 가르는 묶음. 스킬마다 새로 만들어서 합칠 게 없다.
     */
    private class Dimension(
        val cases: List<Case>,
        val keepInactive: Boolean = false,
        val tokenFree: Boolean = false,
        val label: (List<Int>) -> String,
    ) {
        val tokens: Set<String> = cases.flatMap { it.tokens.keys }.toSet()
    }

    /** 자기 능력치끼리 비교하는 조건. 카드가 정해지면 정해진다. */
    private val OWN_STAT_COMPARISONS = listOf(
        listOf("파워정확합>주루수비합") to "파+정합 > 주+수합",
    )

    private val STARTER_SLOT_TOKENS = listOf("선발1", "선발1_2", "선발3_4", "선발3_4_5", "선발4_5")
    private val RELIEVER_SLOT_TOKENS = listOf("중계3_4_5")

    /**
     * 능력치 비례 효과를 나눌 범위. 워크북이 구간을 매긴 범위와 같다. 그 밖의 값은 드물다.
     * 덱 스코어는 선수 능력치가 아니라 덱 누적 지표라 범위가 다르다. 스페셜 덱 스코어는 대개 700
     * 안팎에서 멈추므로 마지막 구간을 `700 이상`으로 열어 둔다([OPEN_ENDED_RANGES]).
     */
    private val SINGLE_STAT_RANGE = 100..199
    private val SUM_STAT_RANGE = 200..399
    private val DECK_SCORE_RANGE = 500..700

    /** 끝값을 넘는 경우도 드물게 있어 마지막 구간을 `N 이상`으로 적는 범위. */
    private val OPEN_ENDED_RANGES = setOf(DECK_SCORE_RANGE)

    private val STAT_SHORT_NAMES = mapOf(
        "파워" to "파", "정확" to "정", "선구" to "선", "인내" to "인", "주루" to "주", "수비" to "수",
        "변화" to "변", "제구" to "제", "구위" to "구위", "구속" to "구속", "지구력" to "지",
    )

    /**
     * [skill]의 줄 목록. 나눌 묶음이 없으면 원래 줄 하나다.
     * 여러 묶음에 걸리면 곱으로 나누고 이름을 `, `로 잇는다.
     *
     * @param level 채점에 쓰는 레벨. 능력치 비례 효과의 비율을 읽는다.
     * @param probabilities 요청 기준 확률표. 기본 능력치 조건이 이미 0이나 1로 정해졌는지 본다.
     */
    fun variants(
        skill: ScoreSkill,
        level: Int,
        role: String,
        battingOrder: Int?,
        pitcherSlot: Int?,
        batHand: Handedness?,
        throwHand: Handedness?,
        probabilities: Map<String, Double>,
    ): List<Variant> {
        val usedTokens = skill.effects.flatMap { conditionTokens(it.condition) }.toSet()
        val dimensions = dimensions(role, battingOrder, pitcherSlot, batHand, throwHand, probabilities) +
            statDimensions(skill, level)

        var result = listOf(Variant(null, emptyMap()))
        for (dimension in dimensions) {
            val groups = groups(dimension, usedTokens) ?: continue
            result = result.flatMap { base ->
                groups.map { cases ->
                    val case = cases.first().value
                    Variant(
                        label = listOfNotNull(base.label, dimension.label(cases.map { it.index })).joinToString(", "),
                        overrides = base.overrides + case.tokens,
                        stats = base.stats + case.stats,
                    )
                }
            }
        }
        return result
    }

    /** [dimension]을 스킬 기준으로 합친 경우 묶음. 나눌 게 없으면 null이다. */
    private fun groups(dimension: Dimension, usedTokens: Set<String>): List<List<IndexedValue<Case>>>? {
        if (dimension.tokenFree) {
            return dimension.cases.withIndex().map { listOf(it) }.takeIf { it.isNotEmpty() }
        }
        val used = dimension.tokens intersect usedTokens
        if (used.isEmpty()) {
            return null
        }
        val grouped = dimension.cases.withIndex()
            .groupBy { (_, case) -> used.associateWith { case.tokens.getValue(it) } }
            .filterKeys { assignment -> dimension.keepInactive || assignment.values.any { it > 0.0 } }
            .values.toList()
        // 경우가 모두 한 줄로 합쳐지면 이 묶음은 스킬 점수를 가르지 않는다. 걸리는 경우만 남은
        // 타순 묶음은 예외다 — 그 한 줄이 "이 타순에서만 쓴다"는 정보다.
        return when {
            grouped.isEmpty() -> null
            grouped.size == 1 && dimension.keepInactive -> null
            else -> grouped
        }
    }

    /**
     * 요청에서 나눌 수 있는 묶음. 요청이 이미 정한 값(타순·선발 순번·투타·기본 능력치)은 나누지 않는다.
     */
    private fun dimensions(
        role: String,
        battingOrder: Int?,
        pitcherSlot: Int?,
        batHand: Handedness?,
        throwHand: Handedness?,
        probabilities: Map<String, Double>,
    ): List<Dimension> = buildList {
        if (role == "BATTER" && battingOrder == null) {
            add(Dimension((1..9).map { Case(battingOrderProbabilities(it)) }) { "타순 ${rangeLabel(it, 1)}" })
        }
        if (pitcherSlot == null) {
            when (role) {
                "SP" -> add(slotDimension("선발", STARTER_SLOT_TOKENS, 5))
                "RP" -> add(slotDimension("중계", RELIEVER_SLOT_TOKENS, 6))
            }
        }
        if (role == "BATTER" && batHand == null) {
            add(batHandDimension())
        }
        if (role != "BATTER" && throwHand == null) {
            add(throwHandDimension())
        }
        for (ladder in ScoreCalculator.baseStatThresholdLadders) {
            val undetermined = ladder.all { step -> probabilities[step.token]?.let { it > 0.0 && it < 1.0 } == true }
            if (!undetermined) {
                continue
            }
            // 단계는 누적이다. i번째 단계를 넘으면 그 아래 단계도 모두 넘은 것이다.
            val cases = ladder.indices.map { index ->
                Case(ladder.withIndex().associate { (i, step) -> step.token to if (i <= index) 1.0 else 0.0 })
            }
            add(Dimension(cases) { indices -> thresholdLabel(ladder, indices) })
        }
        for ((tokens, label) in OWN_STAT_COMPARISONS) {
            add(Dimension(listOf(Case(tokens.associateWith { 1.0 }))) { label })
        }
    }

    private fun slotDimension(prefix: String, tokens: List<String>, slots: Int) = Dimension(
        (1..slots).map { slot ->
            Case(
                tokens.associateWith { token ->
                    val members = token.removePrefix(prefix).split("_").map(String::toInt)
                    if (slot in members) 1.0 else 0.0
                },
            )
        },
    // 게임 표기("3, 4 선발 배치 시")처럼 순번을 앞에 둔다: `3~4 선발`, `3~5 중계`.
    ) { slotsInCase -> "${rangeLabel(slotsInCase, 1)} $prefix" }

    /**
     * 타자의 타석 방향. 토큰 값은 [ScoreCalculator]의 투타 게이트와 같다 — 스위치 히터는 좌타·우타
     * 조건을 모두 만족한다.
     */
    private fun batHandDimension(): Dimension {
        val hands = listOf(Handedness.LEFT, Handedness.RIGHT, Handedness.SWITCH)
        val cases = hands.map { hand ->
            Case(
                mapOf(
                    "좌타" to gate(hand == Handedness.LEFT || hand == Handedness.SWITCH),
                    "우타" to gate(hand == Handedness.RIGHT || hand == Handedness.SWITCH),
                    "스위치타" to gate(hand == Handedness.SWITCH),
                ),
            )
        }
        return Dimension(cases, keepInactive = true) { indices ->
            when (indices.map { hands[it] }.toSet()) {
                setOf(Handedness.LEFT) -> "좌타"
                setOf(Handedness.RIGHT) -> "우타"
                setOf(Handedness.SWITCH) -> "스위치 히터"
                setOf(Handedness.LEFT, Handedness.RIGHT) -> "스위치 히터 아님"
                setOf(Handedness.LEFT, Handedness.SWITCH) -> "좌타·스위치"
                setOf(Handedness.RIGHT, Handedness.SWITCH) -> "우타·스위치"
                else -> "모든 타석"
            }
        }
    }

    private fun throwHandDimension(): Dimension {
        val cases = listOf(
            Case(mapOf("좌완" to 1.0, "우완" to 0.0)),
            Case(mapOf("좌완" to 0.0, "우완" to 1.0)),
        )
        return Dimension(cases, keepInactive = true) { indices -> if (indices == listOf(0)) "좌완" else "우완" }
    }

    /**
     * 능력치 비례 효과(`정확 능력치의 2%만큼`)를 능력치 구간으로 나눈 묶음. 기준 능력치마다 하나다.
     *
     * 효과 값은 내림한다([ScoreCalculator.calculate]). 그래서 점수는 `비율 × 능력치`의 내림값이 같은
     * 구간 안에서 같고, 구간이 바뀔 때만 달라진다. 그 구간이 곧 줄이다(2%면 100~149 / 150~199).
     * 워크북이 구간을 나눈 방식과 같다. 줄마다 구간의 가장 낮은 값으로 채점한다.
     */
    private fun statDimensions(skill: ScoreSkill, level: Int): List<Dimension> =
        skill.effects
            .filter { !it.baseStat.isNullOrBlank() }
            .groupBy { it.baseStat!!.trim() }
            .map { (baseStat, effects) ->
                val ratios = effects.map { ratioAt(it.values, level) }
                val range = rangeFor(baseStat)
                val buckets = mutableListOf<IntRange>()
                var previous: List<Double>? = null
                for (value in range) {
                    val key = ratios.map { floor(it * value) }
                    if (key == previous) {
                        buckets[buckets.size - 1] = buckets.last().first..value
                    } else {
                        buckets += value..value
                        previous = key
                    }
                }
                val name = statLabel(baseStat)
                Dimension(buckets.map { Case(emptyMap(), statsAt(baseStat, it.first)) }, tokenFree = true) { indices ->
                    val bucket = buckets[indices.first()]
                    when {
                        range in OPEN_ENDED_RANGES && bucket.last == range.last -> "$name ${bucket.first} 이상"
                        bucket.first == bucket.last -> "$name ${bucket.first}"
                        else -> "$name ${bucket.first}~${bucket.last}"
                    }
                }
            }

    private fun ratioAt(values: String, level: Int): Double {
        val tokens = values.split("/")
        return tokens[level.coerceIn(1, tokens.size) - 1].trim().toDouble()
    }

    private fun rangeFor(baseStat: String): IntRange = when {
        baseStat.contains("덱") -> DECK_SCORE_RANGE
        baseStat.contains("+") -> SUM_STAT_RANGE
        else -> SINGLE_STAT_RANGE
    }

    /**
     * 기준 능력치가 [total]이 되게 하는 능력치 값. 합산형(`주루+수비`)은 구성 능력치에 나눠 넣는다.
     * 채점기가 합산형 기준을 구성 능력치의 합으로 읽기 때문이다.
     */
    private fun statsAt(baseStat: String, total: Int): Map<String, Double> {
        val parts = baseStat.split("+").map(String::trim)
        val share = total / parts.size
        return parts.withIndex().associate { (i, stat) ->
            stat to (if (i == parts.lastIndex) total - share * (parts.size - 1) else share).toDouble()
        }
    }

    private fun statLabel(baseStat: String): String = when {
        baseStat.contains("덱") -> "스페셜 덱 스코어"
        baseStat.contains("+") -> baseStat.split("+").joinToString("+") { STAT_SHORT_NAMES[it.trim()] ?: it.trim() } + "합"
        else -> baseStat
    }

    private fun conditionTokens(condition: String?): List<String> =
        condition.orEmpty().split("+").map(String::trim).filter { it.isNotEmpty() && it != "ALWAYS" }

    private fun gate(condition: Boolean): Double = if (condition) 1.0 else 0.0

    /**
     * 합쳐진 단계의 구간을 겹치지 않게 적는다. 위 단계가 있으면 거기서 끊는다.
     * `주+수합 155 이상 165 미만`, `주+수합 165 이상`.
     */
    private fun thresholdLabel(ladder: List<ScoreCalculator.BaseStatStep>, indices: List<Int>): String {
        val low = ladder[indices.min()]
        val high = ladder.getOrNull(indices.max() + 1)
        val lowText = "${low.label} ${low.threshold.toInt()} 이상"
        return if (high == null) lowText else "$lowText ${high.threshold.toInt()} 미만"
    }

    /** 0부터 센 번호를 [offset]부터 센 번호로 바꿔 `4~5`, `3`, `1~2·6~9`처럼 적는다. */
    private fun rangeLabel(indices: List<Int>, offset: Int): String {
        val runs = mutableListOf<IntRange>()
        for (number in indices.map { it + offset }.sorted()) {
            val last = runs.lastOrNull()
            if (last != null && number == last.last + 1) {
                runs[runs.size - 1] = last.first..number
            } else {
                runs += number..number
            }
        }
        return runs.joinToString("·") { if (it.first == it.last) "${it.first}" else "${it.first}~${it.last}" }
    }
}
