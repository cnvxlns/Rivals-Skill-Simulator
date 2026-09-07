package com.example.skillsim.service

import com.example.skillsim.config.ScoreDataLoader
import com.example.skillsim.dto.ScoreRequest
import com.example.skillsim.dto.ScoreResponse
import com.example.skillsim.dto.ScoreSelection
import com.example.skillsim.dto.ScoreSkillOption
import com.example.skillsim.dto.ScoreTableRequest
import com.example.skillsim.dto.ScoreTableResponse
import com.example.skillsim.enums.Handedness
import com.example.skillsim.enums.Level
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.ScoreSkillRepository
import java.util.EnumMap
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ScoreService private constructor(
    private val scoreSkillRepository: ScoreSkillRepository,
    private val scoreCalculator: ScoreCalculator,
    private val statWeightsSupplier: () -> Map<String, Double>,
) {

    @Autowired
    constructor(
        scoreSkillRepository: ScoreSkillRepository,
        scoreCalculator: ScoreCalculator,
        scoreDataLoader: ScoreDataLoader,
    ) : this(scoreSkillRepository, scoreCalculator, scoreDataLoader::statWeights)

    internal constructor(
        scoreSkillRepository: ScoreSkillRepository,
        scoreCalculator: ScoreCalculator,
        statWeights: Map<String, Double>,
    ) : this(scoreSkillRepository, scoreCalculator, { statWeights })

    fun listSkills(cardGrade: String?, cardVariant: String?, position: String?): List<ScoreSkillOption> {
        val card = resolveCard(cardGrade, cardVariant)
        val normalizedPosition = normalizeRequiredOrThrow(position, "Position selection is required.")

        return skillsForCard(card)
            .filter { SkillRules.matchesPosition(it.position, normalizedPosition) }
            .map { it.toOption() }
    }

    fun calculate(request: ScoreRequest?): ScoreResponse {
        if (request == null) {
            throw badRequest("Score request is required.")
        }

        val card = resolveCard(request.cardGrade ?: request.cardType, request.cardVariant)
        val normalizedPosition = normalizeRequiredOrThrow(request.position, "Position selection is required.")
        val selections = request.selections
        validateSelections(selections, card)

        val role = SkillRules.roleForPosition(normalizedPosition)
        val pitcherSlot = request.pitcherSlot
        var battingOrder: Int? = null
        when (role) {
            "BATTER" -> battingOrder = validateBattingOrder(request.battingOrder)
            "SP" -> if (pitcherSlot == null || pitcherSlot !in 1..5) {
                throw badRequest("Pitcher slot must be between 1 and 5 for starting pitchers.")
            }

            "RP" -> if (pitcherSlot == null || pitcherSlot !in 1..6) {
                throw badRequest("Pitcher slot must be between 1 and 6 for relief pitchers.")
            }
        }

        val seenSkillIds = mutableSetOf<String>()
        val poolCounts = mutableMapOf<String, Int>()
        val calculatorSelections = mutableListOf<ScoreCalculator.Selection>()

        selections.orEmpty().forEachIndexed { slotIndex, selection ->
            val skillId = normalizeRequiredOrThrow(selection.skillId, "Skill selection is required.")
            if (!seenSkillIds.add(skillId)) {
                throw badRequest("Duplicate skill selection is not allowed.")
            }

            val skill = scoreSkillRepository.findBySkillKey(skillId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Score skill not found: $skillId")
            val pool = SkillRules.normalizeSkillPool(skill.cardType)
            if (pool !in CardRules.skillPools(card.grade, card.variant)) {
                throw badRequest("Selected skill does not match requested card grade.")
            }
            // 등장 확률이 0인 자리는 막는다. 모먼트 전용은 첫 칸에만 나오고 블랙은 카드당 한 장이다.
            if (!CardRules.allowsPoolInSlot(pool, slotIndex)) {
                throw badRequest("Moment skills can only be in the first slot.")
            }
            poolCounts[pool] = (poolCounts[pool] ?: 0) + 1
            if (poolCounts.getValue(pool) > CardRules.maxSkillsFromPool(pool)) {
                throw badRequest("A card can have at most ${CardRules.maxSkillsFromPool(pool)} $pool skill.")
            }
            if (!SkillRules.matchesPosition(skill.position, normalizedPosition)) {
                throw badRequest("Selected skill does not match requested position.")
            }
            val maxLevel = SkillRules.maxLevel(skill)
            val level = selection.level
            if (level == null || level !in 1..maxLevel) {
                throw badRequest("Skill level must be between 1 and $maxLevel.")
            }

            calculatorSelections += ScoreCalculator.Selection(skill, level)
        }

        return scoreSelections(
            selections = calculatorSelections,
            position = normalizedPosition,
            cardType = card.grade,
            battingOrder = battingOrder,
            pitcherSlot = pitcherSlot,
            throwHand = request.throwHand,
            batHand = request.batHand,
            userStats = request.userStats,
        )
    }

    /**
     * 검증이 끝난 스킬 선택을 채점한다. [calculate]의 뒷부분이며 덱 채점이 같은 경로를 타도록 뽑아냈다.
     *
     * 검증을 하지 않으므로 호출자가 카드 등급·포지션·레벨을 이미 확인했어야 한다.
     * [cardType]에는 카드 **등급**을 넘긴다(상대등급우세 조건이 이 값을 본다).
     * [battingOrder]가 null이면 [ScoreCalculator]의 기본 타순이 쓰인다 — 타순이 없는
     * 후보 선수를 채점할 때 필요하다.
     */
    internal fun scoreSelections(
        selections: List<ScoreCalculator.Selection>,
        position: String,
        cardType: String,
        battingOrder: Int? = null,
        pitcherSlot: Int? = null,
        throwHand: Handedness? = null,
        batHand: Handedness? = null,
        userStats: Map<String, Double>? = null,
        statBonus: Double = 0.0,
    ): ScoreResponse {
        val conditionProbabilities = ScoreCalculator.conditionProbabilitiesForPosition(
            position = position,
            battingOrder = battingOrder,
            pitcherSlot = pitcherSlot,
            cardType = cardType,
            throwHand = throwHand,
            batHand = batHand,
        )
        val undefinedConditionWarnings =
            applyUndefinedConditionWarnings(selections, conditionProbabilities)

        val result = scoreCalculator.calculate(
            selections,
            statWeightsSupplier(),
            conditionProbabilities,
            userStats,
            statBonus,
        )
        return toResponse(result, undefinedConditionWarnings, selections)
    }

    private fun validateBattingOrder(battingOrder: Int?): Int {
        if (battingOrder == null) {
            throw badRequest("Batting order is required for batters.")
        }
        if (battingOrder !in 1..9) {
            throw badRequest("Batting order must be between 1 and 9.")
        }
        return battingOrder
    }

    private fun validateSelections(selections: List<ScoreSelection>?, card: Card) {
        if (selections.isNullOrEmpty()) {
            throw badRequest("At least one skill selection is required.")
        }
        if (selections.size > CardRules.slotCount(card.grade)) {
            throw badRequest("Too many skill selections for card grade ${card.grade}.")
        }
        val seenSkillIds = mutableSetOf<String>()
        for (selection in selections) {
            val skillId = normalizeRequiredOrThrow(selection.skillId, "Skill selection is required.")
            if (!seenSkillIds.add(skillId)) {
                throw badRequest("Duplicate skill selection is not allowed.")
            }
            val level = selection.level
            if (level == null || level < 1) {
                throw badRequest("Skill level must be at least 1.")
            }
        }
    }

    private fun ScoreSkill.toOption(): ScoreSkillOption {
        val maxLevel = SkillRules.maxLevel(this)
        return ScoreSkillOption(
            skillId = skillKey,
            cardType = cardType,
            position = position,
            name = name,
            description = description,
            maxLevel = maxLevel,
            levelLabels = SkillRules.gradeLabels(cardType, maxLevel),
        )
    }

    private fun skillsForCard(card: Card): List<ScoreSkill> =
        CardRules.skillPools(card.grade, card.variant)
            .flatMap { scoreSkillRepository.findByCardTypeIgnoreCase(it) }

    /** 이 카드가 고를 수 있는 스킬 풀. 등급과 변형 두 축이 정한다([CardRules.skillPools]). */
    internal fun skillPoolsFor(cardGrade: String?, cardVariant: String?): List<String> {
        val card = resolveCard(cardGrade, cardVariant)
        return CardRules.skillPools(card.grade, card.variant)
    }

    private fun applyUndefinedConditionWarnings(
        selections: List<ScoreCalculator.Selection>,
        conditionProbabilities: MutableMap<String, Double>,
    ): UndefinedConditionWarnings {
        val globalWarnings = mutableListOf<String>()
        val perSkillWarnings = mutableMapOf<String, MutableList<String>>()
        val seenWarnings = mutableSetOf<String>()

        for (selection in selections) {
            val skill = selection.skill
            for (effect in skill.effects) {
                for (token in conditionTokens(effect.condition)) {
                    if (conditionProbabilities.containsKey(token)) {
                        continue
                    }
                    conditionProbabilities[token] = 0.0
                    val globalWarning = "${skill.skillKey} contains undefined condition: $token"
                    if (seenWarnings.add(globalWarning)) {
                        globalWarnings += globalWarning
                        perSkillWarnings.getOrPut(skill.skillKey) { mutableListOf() } +=
                            "Undefined condition: $token"
                    }
                }
            }
        }

        return UndefinedConditionWarnings(globalWarnings, perSkillWarnings)
    }

    private fun conditionTokens(condition: String?): List<String> {
        if (condition.isNullOrBlank() || condition.equals("ALWAYS", ignoreCase = true)) {
            return emptyList()
        }
        return condition.split("+")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("ALWAYS", ignoreCase = true) }
    }

    private fun toResponse(
        result: ScoreCalculator.Result,
        undefinedConditionWarnings: UndefinedConditionWarnings,
        selections: List<ScoreCalculator.Selection>,
    ): ScoreResponse {
        // 설명의 수치는 사용자가 그 슬롯에서 고른 레벨을 따라야 화면의 점수와 같은 기준이 된다.
        val bySkillKey = mutableMapOf<String, ScoreCalculator.Selection>()
        for (selection in selections) {
            bySkillKey.putIfAbsent(selection.skill.skillKey, selection)
        }
        return ScoreResponse(
            total = result.total,
            perSkill = result.perSkill.map { skill ->
                ScoreResponse.SkillScore(
                    skillId = skill.skillKey,
                    name = skill.name,
                    resolvedDescription = bySkillKey[skill.skillKey]?.let {
                        SkillDescriptions.resolve(it.skill, it.level)
                    },
                    score = skill.score,
                    perStat = skill.perStat.toStatScores(),
                    breakdown = skill.breakdown.map { it.toEffectBreakdown() },
                    warnings = undefinedConditionWarnings.perSkillWarnings[skill.skillKey].orEmpty(),
                )
            },
            perStat = result.perStat.toStatScores(),
            warnings = undefinedConditionWarnings.globalWarnings,
        )
    }

    private fun ScoreCalculator.EffectBreakdown.toEffectBreakdown() = ScoreResponse.EffectBreakdown(
        stat = stat,
        condition = condition,
        weight = weight,
        value = value,
        conditionProbability = conditionProbability,
        subtotal = subtotal,
        baseStat = baseStat,
        baseValue = baseValue,
        rawValue = rawValue,
    )

    private fun Map<String, Double>.toStatScores(): List<ScoreResponse.StatScore> =
        map { (stat, value) -> ScoreResponse.StatScore(stat, value) }

    /** 카드 한 장의 두 축. 예전 단일 cardType으로 들어와도 여기서 풀린다. */
    internal data class Card(val grade: String, val variant: String)

    private fun resolveCard(cardGrade: String?, cardVariant: String?): Card = try {
        val grade = CardRules.normalizeGrade(cardGrade)
        // 변형을 따로 주지 않았는데 등급 자리에 예전 이름(WBC_BLACK 등)이 왔다면 거기서 꺼낸다.
        val variant = if (cardVariant.isNullOrBlank()) {
            CardRules.fromLegacyCardType(cardGrade)?.second ?: CardRules.VARIANT_NONE
        } else {
            CardRules.normalizeVariant(cardVariant)
        }
        CardRules.validateCombination(grade, variant)
        Card(grade, variant)
    } catch (ex: IllegalArgumentException) {
        throw badRequest(ex.message ?: "Card grade is required.")
    }

    private fun normalizeRequiredOrThrow(value: String?, message: String): String =
        try {
            SkillRules.normalizeRequired(value, message)
        } catch (ex: IllegalArgumentException) {
            throw badRequest(message)
        }

    private fun badRequest(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private data class UndefinedConditionWarnings(
        val globalWarnings: List<String>,
        val perSkillWarnings: Map<String, List<String>>,
    )

    /**
     * 전체 스킬을 S레벨 기준으로 채점해 티어별 내림차순으로 돌려준다.
     *
     * S가 없는 스킬(수치 단계가 5단계 미만)은 자기 최대 등급으로 내려서 채점하고,
     * 실제 적용된 등급을 함께 반환한다. 효과 행이 없는 스킬은 0점으로 포함한다 —
     * 능력치가 아니라 확률을 바꾸는 효과라 현재 모델로 측정할 수 없을 뿐이다.
     *
     * @param topN 티어별 상위 몇 개까지 담을지. 0 이하면 전부 담는다.
     */
    fun buildScoreTable(request: ScoreTableRequest, topN: Int): ScoreTableResponse {
        val normalizedPosition = normalizeRequiredOrThrow(request.position, "Position selection is required.")
        val conditionProbabilities = ScoreCalculator.conditionProbabilitiesForPosition(
            position = normalizedPosition,
            battingOrder = request.battingOrder,
            pitcherSlot = request.pitcherSlot,
            cardType = null,
            throwHand = request.throwHand,
            batHand = request.batHand,
        )
        val statWeights = statWeightsSupplier()

        val grouped = EnumMap<SkillTier, MutableList<ScoreTableResponse.Entry>>(SkillTier::class.java)
        for (skill in scoreSkillRepository.findAll()) {
            val tier = SkillTier.of(skill)
            if (tier == null || !SkillRules.matchesPosition(skill.position, normalizedPosition)) {
                continue
            }
            val level = appliedLevel(skill)
            val result = scoreCalculator.calculate(
                listOf(ScoreCalculator.Selection(skill, level)),
                statWeights,
                conditionProbabilities,
                request.userStats,
            )
            grouped.getOrPut(tier) { mutableListOf() } += ScoreTableResponse.Entry(
                skillId = skill.skillKey,
                name = skill.name,
                description = skill.description,
                resolvedDescription = SkillDescriptions.resolve(skill, level),
                score = result.total,
                appliedGrade = SkillRules.gradeLabels(skill.cardType, SkillRules.maxLevel(skill))[level - 1],
            )
        }

        val tiers = SkillTier.DISPLAY_ORDER.mapNotNull { tier ->
            val entries = grouped[tier].orEmpty()
            if (entries.isEmpty()) {
                return@mapNotNull null
            }
            val sorted = entries.sortedWith(
                compareByDescending<ScoreTableResponse.Entry> { it.score }.thenBy { it.skillId },
            )
            ScoreTableResponse.TierGroup(
                tier = tier.key,
                totalCount = sorted.size,
                entries = if (topN > 0 && sorted.size > topN) sorted.take(topN) else sorted,
            )
        }
        return ScoreTableResponse(tiers)
    }

    /** S레벨. 사다리에서 S 위치가 스킬의 최대 단계를 넘으면 최대 단계로 내린다. */
    private fun appliedLevel(skill: ScoreSkill): Int =
        minOf(SkillRules.levelIndex(Level.S, skill.cardType), SkillRules.maxLevel(skill))
}
