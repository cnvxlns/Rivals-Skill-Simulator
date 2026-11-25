package com.example.skillsim.service

import com.example.skillsim.dto.TicketExpectationRequest
import com.example.skillsim.dto.TicketExpectationResponse
import com.example.skillsim.enums.Handedness
import com.example.skillsim.enums.Level
import com.example.skillsim.enums.TicketType
import com.example.skillsim.repository.ScoreSkillRepository
import kotlin.math.expm1
import kotlin.math.ln1p
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * 스킬 변경권을 몇 장쯤 쓰면 지금보다 나아지는가.
 *
 * 전처리(현재 점수 산출, 후보 스킬별/레벨별 점수 테이블 구성)와 응답 조립(기하분포 기대 장수 `1/p`,
 * 누적 성공 확률 `chanceWithin`, 반올림)을 담당하고, 핵심 확률/기댓값 계산은 [TicketExpectationSolver]에 위임한다.
 *
 * **일반권과 고급권의 기대 장수는 같게 나올 수 있다.** 평범한 카드에서 두 티켓의 티어 확률이
 * 같기 때문이다(HOF·블랙·WBC 카드에서만 갈린다). 둘의 진짜 차이는 기댓값이 아니라 위험이다 —
 * 일반권은 결과를 무를 수 없어 그 사이 더 나쁜 상태를 거치고, 나아진 판에서 멈추지 않으면 잃는다.
 * 숫자를 억지로 다르게 만들지 않고 [TicketType.revocable]로 그 차이를 함께 내려보낸다.
 *
 * **스킬레벨보호권은 늘 쓴다고 본다.** 화면에서 켜고 끄게 두었더니 끄고 보는 경우가 없어
 * 선택지만 늘리는 꼴이었다. 보호가 없으면 뽑을 때마다 레벨이 내려갈 수 있어 같은 카드의
 * 기대 장수가 크게 달라진다.
 *
 * **포지션 훈련 보너스는 슬롯에 붙는다.** 그래서 지금 끼운 스킬뿐 아니라 새로 뽑힌 스킬도
 * 보너스 목록에 있으면 레벨이 오른다([PositionTrainingRules]). 레벨 보호가 지키는 것은
 * 보너스가 붙기 전 기본 레벨이다.
 */
@Service
class TicketExpectationService(
    private val scoreSkillRepository: ScoreSkillRepository,
    private val rollEngine: RollEngine,
    private val scoreService: ScoreService,
    private val solver: TicketExpectationSolver = TicketExpectationSolver(),
) {

    /** 화면에 보여 줄 "이만큼 쓰면" 지점. */
    private val milestones = listOf(1, 3, 5, 10, 20)

    /** HTTP 요청을 풀어 [evaluate]에 넘긴다. 레벨 번호를 그 스킬 풀의 사다리로 되돌린다. */
    fun evaluate(request: TicketExpectationRequest): TicketExpectationResponse {
        val grade = SkillRules.normalizeRequired(request.cardGrade, "Card grade is required.")
        val position = SkillRules.normalizeRequired(request.position, "Position is required.")
        val selections = request.selections.orEmpty()
        val keys = selections.map { selection ->
            selection?.skillId?.takeIf(String::isNotBlank)?.let {
                SkillRules.normalizeRequired(it, "Skill selection is required.")
            }
        }
        val levels = selections.mapIndexed { index, selection ->
            val skill = keys[index]?.let(scoreSkillRepository::findBySkillKey) ?: return@mapIndexed null
            val ladder = SkillRules.gradeLadder(skill.cardType)
            ladder[(selection?.level ?: 1).coerceIn(1, ladder.size) - 1]
        }
        return evaluate(
            grade = grade,
            variant = request.cardVariant.orEmpty(),
            position = position,
            currentSkillKeys = keys,
            currentLevels = levels,
            battingOrder = request.battingOrder,
            pitcherSlot = request.pitcherSlot,
            throwHand = request.throwHand,
            batHand = request.batHand,
            userStats = request.userStats,
            lockSlotOne = request.lockSlotOne,
            trainingBonuses = scoreService.resolveTrainingBonuses(request.trainingBonuses, position),
            baseStats = request.baseStats,
            rerollFirstSlotOnly = request.rerollFirstSlotOnly,
            transcendenceLevel = request.transcendenceLevel,
        )
    }

    /**
     * @param currentSkillKeys 지금 끼워 둔 스킬. 빈 칸이 있으면 그 칸은 0점으로 본다.
     * @param trainingBonuses 포지션 훈련 보너스. `스킬 -> 오르는 폭`이며 비어 있으면 보너스가 없다.
     * @param seed 기존 테스트 호환용 파라미터.
     */
    fun evaluate(
        grade: String,
        variant: String,
        position: String,
        currentSkillKeys: List<String?>,
        currentLevels: List<Level?>,
        battingOrder: Int? = null,
        pitcherSlot: Int? = null,
        throwHand: Handedness? = null,
        batHand: Handedness? = null,
        userStats: Map<String, Double>? = null,
        lockSlotOne: Boolean = false,
        trainingBonuses: Map<String, Int> = emptyMap(),
        seed: Long? = null,
        baseStats: Map<String, Double>? = null,
        rerollFirstSlotOnly: Boolean = false,
        transcendenceLevel: Int? = null,
    ): TicketExpectationResponse {
        if (lockSlotOne && rerollFirstSlotOnly) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose only one first-slot mode.")
        }
        scoreService.validateTranscendenceLevel(transcendenceLevel)

        val resolvedGrade = CardRules.resolveGrade(grade)
        val resolvedVariant = CardRules.fromLegacyCardType(grade)?.second
            ?.takeUnless { it == CardRules.VARIANT_NONE } ?: CardRules.normalizeVariant(variant)
        val family = RollFamily.of(resolvedGrade, resolvedVariant)
        val slotCount = CardRules.slotCount(resolvedGrade)
        val firstSlotBonus = CardRules.firstSlotTranscendenceBonus(resolvedGrade, transcendenceLevel)
        fun levelBonus(skillKey: String, slotIndex: Int): Int =
            (trainingBonuses[skillKey] ?: 0) + if (slotIndex == 0) firstSlotBonus else 0

        val paddedKeys = List(slotCount) { currentSkillKeys.getOrNull(it) }
        val paddedLevels = List(slotCount) { currentLevels.getOrNull(it) }
        val score = scoreService.ticketScorer(
            position, resolvedGrade, battingOrder, pitcherSlot, throwHand, batHand, userStats, baseStats,
        )

        val currentSelections = paddedKeys.mapIndexedNotNull { index, key ->
            val skill = key?.let(scoreSkillRepository::findBySkillKey) ?: return@mapIndexedNotNull null
            ScoreCalculator.Selection(
                skill,
                SkillRules.levelIndex(paddedLevels[index], skill.cardType),
                levelBonus(skill.skillKey, index),
            )
        }
        val currentTotal = score(currentSelections).total
        val candidates = buildCandidates(
            CardRules.skillPools(resolvedGrade, resolvedVariant), position, trainingBonuses, firstSlotBonus, score,
        )
        val candidateByKey = candidates.associateBy { it.skillKey }
        val firstKey = paddedKeys[0]
        val firstCandidate = firstKey?.let(candidateByKey::get)
        val lockable = firstCandidate != null && rollEngine.canLockSlotOne(resolvedGrade, firstKey)
        if (rerollFirstSlotOnly && !lockable) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "First-slot-only changes are unavailable for this card.")
        }
        val protectLevels = List(slotCount) { true }

        val lockedSlotOne = firstCandidate?.takeIf { lockSlotOne && lockable }?.let {
            it to (paddedLevels[0] ?: SkillRules.gradeLadder(it.pool).first())
        }
        val fixedSlots = if (rerollFirstSlotOnly) {
            (1 until slotCount).associateWith { index ->
                val key = paddedKeys[index] ?: return@associateWith null to Level.D
                val skill = candidateByKey[key] ?: throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Selected skill is unavailable for this card: $key",
                )
                skill to (paddedLevels[index] ?: SkillRules.gradeLadder(skill.pool).first())
            }
        } else {
            emptyMap()
        }

        val tickets = TicketType.entries.map { ticket ->
            val problemInput = TicketExpectationSolver.ProblemInput(
                family = family,
                grade = resolvedGrade,
                variant = resolvedVariant,
                ticket = ticket,
                slotCount = slotCount,
                currentTotal = currentTotal,
                currentSkillKeys = paddedKeys,
                currentLevels = paddedLevels,
                lockedSlotOne = lockedSlotOne,
                protectLevels = protectLevels,
                candidates = candidates,
                fixedSlots = fixedSlots,
            )
            val solution = solver.solve(problemInput)
            toOutcome(ticket, solution, currentTotal, ::levelBonus)
        }

        return TicketExpectationResponse(
            currentTotal = currentTotal,
            slotOneLockable = lockable,
            tickets = tickets,
        )
    }

    private fun buildCandidates(
        pools: List<String>,
        position: String,
        trainingBonuses: Map<String, Int>,
        firstSlotBonus: Int,
        score: (List<ScoreCalculator.Selection>) -> ScoreCalculator.Result,
    ): List<TicketExpectationSolver.SkillCandidate> =
        pools.flatMap { pool ->
            scoreSkillRepository.findByCardTypeIgnoreCase(pool)
                .filter { SkillRules.matchesPosition(it.position, position) }
                .mapNotNull { skill ->
                    val tier = SkillTier.of(skill) ?: return@mapNotNull null
                    val ladder = SkillRules.gradeLadder(pool)
                    val bonus = trainingBonuses[skill.skillKey] ?: 0
                    // 조합의 합계에서만 반올림해야 실제 계산기와 같은 점수로 비교한다.
                    val scoresByLevel = ladder.withIndex().associate { (index, level) ->
                        val selection = ScoreCalculator.Selection(skill, index + 1, bonus)
                        level to score(listOf(selection)).unroundedTotal
                    }
                    // 포지션특훈까지 반영한 표에서 초월만큼 위 레벨의 점수를 읽는다.
                    val firstSlotScoresByLevel = if (firstSlotBonus == 0) null else {
                        ladder.withIndex().associate { (index, level) ->
                            val boosted = ladder[(index + firstSlotBonus).coerceAtMost(ladder.lastIndex)]
                            level to scoresByLevel.getValue(boosted)
                        }
                    }
                    TicketExpectationSolver.SkillCandidate(
                        skillKey = skill.skillKey,
                        pool = pool,
                        tier = tier,
                        scoresByLevel = scoresByLevel,
                        firstSlotScoresByLevel = firstSlotScoresByLevel,
                    )
                }
        }

    private fun toOutcome(
        ticket: TicketType,
        solution: TicketExpectationSolver.Solution,
        currentTotal: Double,
        levelBonus: (String, Int) -> Int,
    ): TicketExpectationResponse.TicketOutcome {
        val p = solution.improveChance.coerceIn(0.0, 1.0)
        return TicketExpectationResponse.TicketOutcome(
            ticket = ticket.name,
            revocable = ticket.revocable,
            improveChance = p,
            // p가 0이면 기대 장수가 발산한다. 무한대를 내려보내지 않고 null로 두어
            // 화면이 "사실상 불가"로 읽게 한다.
            expectedTickets = if (p == 0.0) null else 1.0 / p,
            averageGain = solution.averageGain?.let { round(it) },
            averageTotal = round(solution.averageTotal),
            chanceWithin = milestones.associateWith { if (it == 1) p else -expm1(it * ln1p(-p)) },
            betterCombinations = solution.betterCombinations.map { combination ->
                val total = round(combination.total, scale = 100.0)
                TicketExpectationResponse.BetterCombination(
                    slots = combination.slots.mapIndexed { index, slot ->
                        val skill = slot.skillKey?.let(scoreSkillRepository::findBySkillKey)
                        TicketExpectationResponse.CombinationSlot(
                            skillId = slot.skillKey,
                            name = skill?.name,
                            level = slot.level.name,
                            appliedGrade = skill?.let {
                                val level = SkillRules.levelIndex(slot.level, it.cardType)
                                scoreService.appliedGradeLabel(
                                    ScoreCalculator.Selection(it, level, levelBonus(it.skillKey, index)),
                                )
                            },
                            slot = if (index in solution.pinnedSlots) index + 1 else null,
                        )
                    },
                    total = total,
                    gain = round(total - currentTotal, scale = 100.0),
                    chance = combination.chance,
                )
            },
        )
    }

    private fun round(value: Double, scale: Double = 10_000.0): Double = Math.round(value * scale) / scale
}
