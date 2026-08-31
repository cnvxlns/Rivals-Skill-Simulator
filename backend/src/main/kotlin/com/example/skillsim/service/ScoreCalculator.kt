package com.example.skillsim.service

import com.example.skillsim.enums.Handedness
import com.example.skillsim.model.ScoreSkill
import kotlin.math.floor
import kotlin.math.roundToLong
import org.springframework.stereotype.Component

private const val DEFAULT_USER_STAT = 120.0
private const val DEFAULT_DECK_SCORE = 500.0
private const val BATTER_OFFENSE_OVER_DEFENSE_CONDITION = "파워정확합>주루수비합"

private val INNING_WEIGHTS_BY_ROLE = mapOf(
    "SP" to doubleArrayOf(0.190, 0.180, 0.170, 0.130, 0.110, 0.100, 0.060, 0.040, 0.020),
    "BATTER" to doubleArrayOf(0.150, 0.140, 0.130, 0.120, 0.110, 0.100, 0.090, 0.080, 0.080),
    "RP" to doubleArrayOf(0.000, 0.000, 0.000, 0.000, 0.030, 0.180, 0.340, 0.350, 0.100),
    "CP" to doubleArrayOf(0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.100, 0.900),
)

private val STATIC_CONDITION_PROBABILITIES = mapOf(
    "ALWAYS" to 1.0,
    "홈" to 0.3,
    "원정" to 0.7,
    "주자있음" to 0.4,
    "주자없음" to 0.6,
    "주자1명" to 0.25,
    "주자2명이상" to 0.25,
    "주자2루이상" to 0.25,
    "주자3루" to 0.03,
    "OVR열세" to 0.5,
    "OVR우세" to 0.5,
    "덱스코어열세" to 0.5,
    "홈런3이상" to 0.005,
    "좌투상대" to 0.3,
    "우투상대" to 0.7,
    "좌타상대" to 0.4,
    "우타상대" to 0.6,
    "직구상대" to 0.45,
    "속구선택" to 0.45,
    "변화구상대" to 0.55,
    "변화구선택" to 0.55,
    "스윗스팟" to 0.3,
    "당겨치기" to 0.35,
    "밀어치기" to 0.35,
    "높은공" to 0.333,
    "낮은공" to 0.333,
    "대타첫타석" to 0.0, // 주전 라인업 기준 대타 출전 없음
    "교체후첫타자" to 0.25, // 계투 상대 타자 중 첫 타자 근사
    "풀카운트" to 0.048,
    "2아웃" to 0.333,
)

private val PLATE_SITUATION_PROBABILITIES = mapOf(
    "초구" to 0.30,
    "스트라이크타격" to 0.55,
    "1스트라이크" to 0.30,
    "2스트라이크" to 0.35,
)

/**
 * 타순이 지정되지 않았을 때 가정하는 타순.
 *
 * 예전에는 9개 타순의 평균(타순1 = 1/9 = 0.111)으로 채점했는데, 그러면 어떤 타순
 * 조건도 완전히 발동하지 않아 타순 조건이 붙은 스킬이 일률적으로 눌렸다.
 * 하나의 타순을 전제로 두는 편이 읽기도 쉽고 조건 스킬의 값도 제대로 드러난다.
 */
internal const val DEFAULT_BATTING_ORDER = 1

private val GAME_STATE_PROBABILITIES = mapOf(
    "리드" to 0.37,
    "리드아님" to 0.63,
    "비김또는리드" to 0.63,
    "비김또는열세" to 0.63,
)

private val MODE_PROBABILITIES = mapOf(
    "모드_랭킹대전" to 1.0,
    "모드_랭킹토너먼트" to 0.0,
    "모드_라이브매치" to 0.0,
    "모드_리그" to 0.0,
    "모드_클럽" to 0.0,
    "모드_타점배틀" to 0.0,
    "모드_랭킹슬러거" to 0.0,
)

private val LAUNCH_ANGLE_PROBABILITIES = mapOf(
    "발사각조건" to 0.20,
    "발사각10이상" to 0.50,
    "발사각14이하" to 0.50,
)

private val NINE_BATTER_DURATION_PROBABILITIES_BY_ROLE = mapOf(
    "BATTER" to 0.00,
    "SP" to 0.45,
    "RP" to 0.95,
    "CP" to 1.00,
)

// 보직별 평균 상대 타자 수 대비 해당 타자 수 비중 근사(등판후9타자와 동일 방법론).
private val THREE_BATTER_DURATION_PROBABILITIES_BY_ROLE = mapOf(
    "BATTER" to 0.00,
    "SP" to 0.15,
    "RP" to 0.65,
    "CP" to 0.90,
)

private val FOUR_BATTER_DURATION_PROBABILITIES_BY_ROLE = mapOf(
    "BATTER" to 0.00,
    "SP" to 0.20,
    "RP" to 0.80,
    "CP" to 1.00,
)

private val GUTS_PROBABILITIES_BY_ROLE = mapOf(
    "BATTER" to 0.20,
    "SP" to 0.80,
    "RP" to 0.95,
    "CP" to 0.90,
)

// "인내<구속"(투수 구속 > 상대 타자 인내)은 GUTS_PROBABILITIES_BY_ROLE(패기: 상대가 우세할 확률)의
// 반대 사건이므로 1 - 패기확률로 산출.
private val PATIENCE_BELOW_VELOCITY_PROBABILITIES_BY_ROLE = mapOf(
    "BATTER" to 0.80,
    "SP" to 0.20,
    "RP" to 0.05,
    "CP" to 0.10,
)

private val MAESTRO_CUMULATIVE_PROBABILITIES_BY_ROLE = mapOf(
    "BATTER" to 0.0,
    "SP" to maestroAverageActiveStack(17) / 12.0,
    "RP" to maestroAverageActiveStack(4) / 12.0,
    "CP" to maestroAverageActiveStack(3) / 12.0,
)

// 도전정신(상대등급우세): 자기 카드 등급 기준 P(상대 선수 등급 > 내 등급).
// 상대 라인업이 프라임/시그니처/모먼트/HOF 위주라는 메타 가정에서 산출한 값 (agy×3+codex×3 2라운드 토론 합의).
// WBC 계열은 일반 계열의 리스킨(동일 등급)이므로 WBC=NORMAL(프라임/시그니처), WBC_BLACK=BLACK과 같은 값을 사용한다.
// SUPREME_MOMENT(슈프림 모먼트)는 모먼트<슈프림모먼트<시그니처 순서를 반영해 MOMENT와 NORMAL의 중간값을 사용한다.
private const val DEFAULT_CARD_TYPE = "BLACK"
private val OPPONENT_GRADE_ADVANTAGE_PROBABILITIES_BY_CARD_TYPE = mapOf(
    "MOMENT" to 0.40,
    "SUPREME_MOMENT" to 0.30,
    "NORMAL" to 0.20,
    "WBC" to 0.20,
    "BLACK" to 0.05,
    "WBC_BLACK" to 0.05,
    "HOF" to 0.00,
)

private val TOP_ORDER_PLATE_APPEARANCE_REACH = doubleArrayOf(1.0, 1.0, 0.95, 0.70, 0.25, 0.05, 0.01)
private val MIDDLE_ORDER_PLATE_APPEARANCE_REACH = doubleArrayOf(1.0, 1.0, 0.90, 0.55, 0.12, 0.02, 0.005)
private val LOWER_ORDER_PLATE_APPEARANCE_REACH = doubleArrayOf(1.0, 0.95, 0.80, 0.40, 0.06, 0.01, 0.00)

/**
 * 상대 팀 누적 3홈런 이후 경기 종료까지 유지되는 버프의 노출 비율.
 * P(상대팀 경기당 홈런 3개 이상) x 발동 후 잔여 타석 비율로 근사한다.
 * 게임 내 실제 경기당 팀 홈런(lambda)이 확정되지 않아 잠정값이며 민감도가 크다(±2배).
 * lambda=1.1 -> 0.04, lambda=1.6 -> 0.06, lambda=2.0 -> 0.12
 */
private const val OPPONENT_THREE_HOMERUN_EXPOSURE = 0.06

/**
 * 한 이닝에 출루 2명을 허용한 뒤 이닝 종료까지 유지되는 버프의 노출 비율.
 * 이닝을 음이항(3아웃 도달까지)으로 두고 버프 활성 타자 수 / 이닝당 총 상대 타자 수로 산정.
 */
private const val INNING_TWO_BASERUNNERS_EXPOSURE = 0.25

/**
 * 타순 조건 확률. 타순은 경기 중 고정이므로 값은 0 아니면 1이다.
 *
 * 채점과 산정 방식 화면이 같은 함수를 쓰게 해서 두 값이 어긋나지 않게 한다.
 */
internal fun battingOrderProbabilities(battingOrder: Int): Map<String, Double> = mapOf(
    "타순1" to gate(battingOrder == 1),
    "타순1_2" to gate(battingOrder in 1..2),
    "타순2_3" to gate(battingOrder in 2..3),
    "타순3_4_5" to gate(battingOrder in 3..5),
    "타순4_5" to gate(battingOrder in 4..5),
    "타순6_9" to gate(battingOrder in 6..9),
    "타순8_9" to gate(battingOrder in 8..9),
)

/** 조건이 확률이 아니라 참/거짓일 때 쓰는 게이트 값. */
private fun gate(condition: Boolean): Double = if (condition) 1.0 else 0.0

private fun maestroAverageActiveStack(expectedOuts: Int): Double = when {
    expectedOuts <= 0 -> 0.0
    expectedOuts <= 12 -> (expectedOuts - 1) / 2.0
    else -> (66.0 + 12.0 * (expectedOuts - 12)) / expectedOuts
}

private fun roundToCents(value: Double): Double = (value * 100.0).roundToLong() / 100.0

private data class ConditionContext(
    val normalizedPosition: String,
    val role: String,
    val battingOrder: Int?,
    val pitcherSlot: Int?,
    val cardType: String?,
    val throwHand: Handedness,
    val batHand: Handedness,
)

private fun interface ConditionResolver {
    fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext)
}

private class StaticProbabilityResolver(
    private val source: Map<String, Double>,
) : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        probabilities.putAll(source)
    }
}

/** 선수 본인의 투/타 방향 게이트. 확률이 아니라 참/거짓이다. */
private object HandednessGateResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val thrown = context.throwHand
        val bats = context.batHand
        probabilities["좌완"] = gate(thrown == Handedness.LEFT)
        probabilities["우완"] = gate(thrown == Handedness.RIGHT)
        // 스위치 타자는 좌/우 양쪽 상황을 모두 만족한다.
        probabilities["좌타"] = gate(bats == Handedness.LEFT || bats == Handedness.SWITCH)
        probabilities["우타"] = gate(bats == Handedness.RIGHT || bats == Handedness.SWITCH)
        probabilities["스위치타"] = gate(bats == Handedness.SWITCH)
    }
}

private object PositionGateResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val position = context.normalizedPosition
        probabilities["포지션_SP"] = gate(position == "SP")
        probabilities["포지션_RP_CP"] = gate(position in setOf("RP", "CP"))
        probabilities["포지션_DH"] = gate(position == "DH")
        probabilities["포지션_SS"] = gate(position == "SS")
        probabilities["포지션_OF"] = gate(position in setOf("OF", "LF", "CF", "RF"))
        probabilities["포지션_C"] = gate(position == "C")
    }
}

private object SlotGateResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val slot = context.pitcherSlot
        val isStarter = context.role == "SP"
        val isReliever = context.role == "RP"

        probabilities["선발1"] = gate(isStarter && slot == 1)
        probabilities["선발1_2"] = gate(isStarter && slot in setOf(1, 2))
        probabilities["선발3_4"] = gate(isStarter && slot in setOf(3, 4))
        probabilities["선발3_4_5"] = gate(isStarter && slot in setOf(3, 4, 5))
        probabilities["선발4_5"] = gate(isStarter && slot in setOf(4, 5))
        probabilities["중계3_4_5"] = gate(isReliever && slot in setOf(3, 4, 5))
    }
}

private object BattingOrderResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        probabilities.putAll(battingOrderProbabilities(context.battingOrder ?: DEFAULT_BATTING_ORDER))
    }
}

private object DurationResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        probabilities["등판후9타자"] = NINE_BATTER_DURATION_PROBABILITIES_BY_ROLE[context.role] ?: 0.0
        probabilities["등판후3타자"] = THREE_BATTER_DURATION_PROBABILITIES_BY_ROLE[context.role] ?: 0.0
        probabilities["등판후4타자"] = FOUR_BATTER_DURATION_PROBABILITIES_BY_ROLE[context.role] ?: 0.0
        probabilities["두번째타석까지"] = secondPlateAppearanceProbability(context.battingOrder)
        // 아래 둘은 "발동 확률"이 아니라 "버프가 켜져 있는 기회의 비율"이다.
        // 등판후N타자·마에스트로누적과 같은 시간평균 노출 비율 척도를 따른다.
        probabilities["상대팀홈런3"] = OPPONENT_THREE_HOMERUN_EXPOSURE
        probabilities["이닝출루2인이상"] = INNING_TWO_BASERUNNERS_EXPOSURE
    }

    private fun secondPlateAppearanceProbability(order: Int?): Double =
        when (order ?: DEFAULT_BATTING_ORDER) {
            in 1..2 -> 0.50
            in 3..5 -> 0.55
            in 6..9 -> 0.58
            else -> 0.55
        }
}

private object StatComparisonResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val guts = GUTS_PROBABILITIES_BY_ROLE[context.role]
            ?: GUTS_PROBABILITIES_BY_ROLE.getValue("BATTER")
        val patienceBelowVelocity = PATIENCE_BELOW_VELOCITY_PROBABILITIES_BY_ROLE[context.role]
            ?: PATIENCE_BELOW_VELOCITY_PROBABILITIES_BY_ROLE.getValue("BATTER")
        probabilities["OVR열세"] = guts
        probabilities["패기"] = guts
        probabilities["인내<구속"] = patienceBelowVelocity
        probabilities["구속>인내"] = patienceBelowVelocity
        probabilities["구위>파워"] = 0.35
        probabilities["선구>제구"] = 0.65
        probabilities[BATTER_OFFENSE_OVER_DEFENSE_CONDITION] = 1.00
        probabilities["제구>선구"] = when (context.role) {
            "RP" -> 0.10
            "CP" -> 0.25
            "SP" -> 0.45
            else -> 0.45
        }
    }
}

private object CardGradeResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val cardType = if (context.cardType.isNullOrBlank()) {
            DEFAULT_CARD_TYPE
        } else {
            SkillRules.normalizeCardType(context.cardType)
        }
        probabilities["상대등급우세"] =
            OPPONENT_GRADE_ADVANTAGE_PROBABILITIES_BY_CARD_TYPE.getValue(cardType)
    }
}

private object MaestroCumulativeResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        probabilities["마에스트로누적"] = MAESTRO_CUMULATIVE_PROBABILITIES_BY_ROLE[context.role] ?: 0.0
    }
}

private object InningResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val inningWeights = INNING_WEIGHTS_BY_ROLE[context.role]
            ?: INNING_WEIGHTS_BY_ROLE.getValue("BATTER")
        for (inning in 1..inningWeights.size) {
            var probability = 0.0
            for (idx in inning - 1 until inningWeights.size) {
                probability += inningWeights[idx]
            }
            val rounded = roundToCents(probability)
            probabilities["${inning}회이후"] = rounded
            probabilities["${inning}회"] = rounded
        }
    }
}

private object InningRangeResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val inningWeights = INNING_WEIGHTS_BY_ROLE[context.role]
            ?: INNING_WEIGHTS_BY_ROLE.getValue("BATTER")
        for (start in 1..inningWeights.size) {
            var probability = 0.0
            for (end in start..inningWeights.size) {
                probability += inningWeights[end - 1]
                val rounded = roundToCents(probability)
                probabilities["${start}_${end}회"] = rounded
                if (start == 1) {
                    probabilities["${end}회까지"] = rounded
                }
            }
        }
    }
}

private object PlateAppearanceResolver : ConditionResolver {
    override fun apply(probabilities: MutableMap<String, Double>, context: ConditionContext) {
        val reachProbabilities = plateAppearanceReachProbabilities(context.battingOrder)
        val totalReachProbability = reachProbabilities.sum()

        for (start in 1..reachProbabilities.size) {
            probabilities["타석$start"] = reachProbabilities[start - 1] / totalReachProbability

            var rangeProbability = 0.0
            for (end in start..reachProbabilities.size) {
                rangeProbability += reachProbabilities[end - 1] / totalReachProbability
                probabilities["타석${start}_$end"] = rangeProbability
            }
        }
    }

    private fun plateAppearanceReachProbabilities(order: Int?): DoubleArray =
        when (order ?: DEFAULT_BATTING_ORDER) {
            in 1..2 -> TOP_ORDER_PLATE_APPEARANCE_REACH
            in 3..5 -> MIDDLE_ORDER_PLATE_APPEARANCE_REACH
            in 6..9 -> LOWER_ORDER_PLATE_APPEARANCE_REACH
            else -> MIDDLE_ORDER_PLATE_APPEARANCE_REACH
        }
}

private val CONDITION_RESOLVERS = listOf(
    StaticProbabilityResolver(STATIC_CONDITION_PROBABILITIES),
    StaticProbabilityResolver(PLATE_SITUATION_PROBABILITIES),
    BattingOrderResolver,
    StaticProbabilityResolver(GAME_STATE_PROBABILITIES),
    StaticProbabilityResolver(MODE_PROBABILITIES),
    StaticProbabilityResolver(LAUNCH_ANGLE_PROBABILITIES),
    DurationResolver,
    MaestroCumulativeResolver,
    StatComparisonResolver,
    CardGradeResolver,
    PositionGateResolver,
    HandednessGateResolver,
    SlotGateResolver,
    InningResolver,
    InningRangeResolver,
    PlateAppearanceResolver,
)

private fun buildConditionProbabilities(
    position: String?,
    battingOrder: Int?,
    pitcherSlot: Int?,
    cardType: String?,
    throwHand: Handedness?,
    batHand: Handedness?,
): MutableMap<String, Double> {
    val context = ConditionContext(
        normalizedPosition = SkillRules.normalizePosition(position),
        role = SkillRules.roleForPosition(position),
        battingOrder = battingOrder,
        pitcherSlot = pitcherSlot,
        cardType = cardType,
        throwHand = throwHand ?: Handedness.RIGHT,
        batHand = batHand ?: Handedness.RIGHT,
    )
    val probabilities = HashMap<String, Double>()
    CONDITION_RESOLVERS.forEach { it.apply(probabilities, context) }
    return probabilities
}

private val DEFAULT_CONDITION_PROBABILITIES: Map<String, Double> =
    buildConditionProbabilities("BATTER", null, null, null, null, null)

@Component
class ScoreCalculator {

    fun calculate(
        selections: List<Selection>,
        statWeights: Map<String, Double>,
        conditionProbabilities: Map<String, Double> = DEFAULT_CONDITION_PROBABILITIES,
        userStats: Map<String, Double>? = emptyMap(),
    ): Result {
        val totalPerStat = LinkedHashMap<String, Double>()
        val perSkill = mutableListOf<SkillScore>()
        val safeUserStats = userStats ?: emptyMap()
        var weightedTotal = 0.0

        for (selection in selections) {
            val skill = selection.skill
            val skillPerStat = LinkedHashMap<String, Double>()
            val breakdown = mutableListOf<EffectBreakdown>()
            var skillScore = 0.0

            for (effect in skill.effects) {
                val weight = statWeights[effect.stat] ?: 0.0
                val rawValue = valueAt(effect.values, selection.level)
                var baseStat: String? = null
                var baseValue: Double? = null
                var value = rawValue
                if (!effect.baseStat.isNullOrBlank()) {
                    baseStat = effect.baseStat
                    baseValue = userStatValue(safeUserStats, effect.baseStat)
                    value = baseValue * rawValue
                }
                value = floor(value)
                val conditionProbability = conditionProbability(effect.condition, conditionProbabilities)

                val contribution = weight * value * conditionProbability

                // perStat 은 "스킬로 증가한 스탯 절대치"를 표시하므로 weight·조건확률을 제외한
                // 순수 증가량(value)만 누적한다.
                skillPerStat.mergeRounded(effect.stat, value)
                totalPerStat.mergeRounded(effect.stat, value)
                skillScore += contribution
                weightedTotal += contribution
                breakdown += EffectBreakdown(
                    stat = effect.stat,
                    condition = effect.condition,
                    weight = round(weight),
                    value = round(value),
                    conditionProbability = round(conditionProbability),
                    subtotal = round(contribution),
                    baseStat = baseStat,
                    baseValue = baseValue?.let { round(it) },
                    rawValue = round(rawValue),
                )
            }

            perSkill += SkillScore(
                skillKey = skill.skillKey,
                name = skill.name,
                score = round(skillScore),
                perStat = skillPerStat.roundedCopy(),
                breakdown = breakdown,
            )
        }

        return Result(round(weightedTotal), perSkill, totalPerStat.roundedCopy())
    }

    internal fun valueAt(values: String?, level: Int): Double {
        val tokens = if (values.isNullOrBlank()) listOf("0") else values.split("/")
        val clampedLevel = level.coerceIn(1, tokens.size)
        return tokens[clampedLevel - 1].trim().toDouble()
    }

    internal fun conditionProbability(condition: String?, overrides: Map<String, Double>?): Double {
        if (condition.isNullOrBlank() || condition.equals("ALWAYS", ignoreCase = true)) {
            return 1.0
        }
        val safeOverrides = overrides ?: emptyMap()

        var nonModeProbability = 1.0
        var modeProbability: Double? = null
        var positionProbability: Double? = null

        for (rawPart in condition.split("+")) {
            val part = rawPart.trim()
            val probability = resolveConditionPart(part, safeOverrides)
            when {
                part.startsWith("모드_") ->
                    modeProbability = maxOf(modeProbability ?: 0.0, probability)

                part.startsWith("포지션_") || part.startsWith("선발") || part.startsWith("중계") ->
                    positionProbability = maxOf(positionProbability ?: 0.0, probability)

                else -> nonModeProbability *= probability
            }
        }

        return nonModeProbability * (modeProbability ?: 1.0) * (positionProbability ?: 1.0)
    }

    private fun resolveConditionPart(part: String, overrides: Map<String, Double>): Double =
        overrides[part]
            ?: DEFAULT_CONDITION_PROBABILITIES[part]
            ?: throw IllegalArgumentException("Unknown condition token: $part")

    private fun userStatValue(userStats: Map<String, Double>, stat: String?): Double {
        // 합산형 기준 스탯(예: "변화+제구")은 각 구성 스탯 값을 더해 기준값으로 사용한다.
        if (stat != null && stat.contains("+")) {
            return stat.split("+").sumOf { userStatValue(userStats, it.trim()) }
        }
        userStats[stat]?.let { return it }
        return if (isDeckScoreStat(stat)) DEFAULT_DECK_SCORE else DEFAULT_USER_STAT
    }

    private fun isDeckScoreStat(stat: String?): Boolean = stat != null && stat.contains("덱")

    private fun MutableMap<String, Double>.mergeRounded(stat: String, contribution: Double) {
        this[stat] = round((this[stat] ?: 0.0) + contribution)
    }

    private fun Map<String, Double>.roundedCopy(): Map<String, Double> =
        mapValuesTo(LinkedHashMap()) { (_, value) -> round(value) }

    private fun round(value: Double): Double = roundToCents(value)

    data class Selection(val skill: ScoreSkill, val level: Int)

    data class Result(
        val total: Double,
        val perSkill: List<SkillScore>,
        val perStat: Map<String, Double>,
    )

    data class SkillScore(
        val skillKey: String,
        val name: String,
        val score: Double,
        val perStat: Map<String, Double>,
        val breakdown: List<EffectBreakdown>,
    )

    data class EffectBreakdown(
        val stat: String,
        val condition: String,
        val weight: Double,
        val value: Double,
        val conditionProbability: Double,
        val subtotal: Double,
        val baseStat: String?,
        val baseValue: Double?,
        val rawValue: Double,
    )

    companion object {
        val defaultUserStat: Double get() = DEFAULT_USER_STAT

        val defaultDeckScore: Double get() = DEFAULT_DECK_SCORE

        /** 호출자가 배열을 건드려도 원본이 상하지 않도록 복사해서 준다. */
        val inningWeightsByRole: Map<String, DoubleArray>
            get() = INNING_WEIGHTS_BY_ROLE.mapValues { (_, weights) -> weights.copyOf() }

        val staticConditionProbabilities: Map<String, Double> get() = STATIC_CONDITION_PROBABILITIES

        val plateSituationProbabilities: Map<String, Double> get() = PLATE_SITUATION_PROBABILITIES

        val battingOrderDefaultProbabilities: Map<String, Double>
            get() = battingOrderProbabilities(DEFAULT_BATTING_ORDER)

        val gameStateProbabilities: Map<String, Double> get() = GAME_STATE_PROBABILITIES

        val modeProbabilities: Map<String, Double> get() = MODE_PROBABILITIES

        val launchAngleProbabilities: Map<String, Double> get() = LAUNCH_ANGLE_PROBABILITIES

        val nineBatterDurationProbabilitiesByRole: Map<String, Double>
            get() = NINE_BATTER_DURATION_PROBABILITIES_BY_ROLE

        val gutsProbabilitiesByRole: Map<String, Double> get() = GUTS_PROBABILITIES_BY_ROLE

        val patienceBelowVelocityProbabilitiesByRole: Map<String, Double>
            get() = PATIENCE_BELOW_VELOCITY_PROBABILITIES_BY_ROLE

        val maestroCumulativeProbabilitiesByRole: Map<String, Double>
            get() = MAESTRO_CUMULATIVE_PROBABILITIES_BY_ROLE

        val opponentGradeAdvantageProbabilitiesByCardType: Map<String, Double>
            get() = OPPONENT_GRADE_ADVANTAGE_PROBABILITIES_BY_CARD_TYPE

        val topOrderPlateAppearanceReach: DoubleArray
            get() = TOP_ORDER_PLATE_APPEARANCE_REACH.copyOf()

        val middleOrderPlateAppearanceReach: DoubleArray
            get() = MIDDLE_ORDER_PLATE_APPEARANCE_REACH.copyOf()

        val lowerOrderPlateAppearanceReach: DoubleArray
            get() = LOWER_ORDER_PLATE_APPEARANCE_REACH.copyOf()

        /**
         * 투/타 방향까지 반영한 조건 확률표.
         *
         * 방향이 주어지지 않으면 우완/우타로 간주한다. 좌완 전용 절(예: 빅 유닛의 "좌완 선발로 등판 시")이
         * 방향 미상일 때 발동하지 않도록 하기 위한 보수적 기본값이다.
         */
        fun conditionProbabilitiesForPosition(
            position: String?,
            battingOrder: Int? = null,
            pitcherSlot: Int? = null,
            cardType: String? = null,
            throwHand: Handedness? = null,
            batHand: Handedness? = null,
        ): MutableMap<String, Double> =
            buildConditionProbabilities(position, battingOrder, pitcherSlot, cardType, throwHand, batHand)
    }
}
