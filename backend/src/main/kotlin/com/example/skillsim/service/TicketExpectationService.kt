package com.example.skillsim.service

import com.example.skillsim.dto.TicketExpectationRequest
import com.example.skillsim.dto.TicketExpectationResponse
import com.example.skillsim.enums.Handedness
import com.example.skillsim.enums.Level
import com.example.skillsim.enums.TicketType
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.ScoreSkillRepository
import kotlin.random.Random
import org.springframework.stereotype.Service

/**
 * 스킬 변경권을 몇 장쯤 쓰면 지금보다 나아지는가.
 *
 * 티켓마다 [RollEngine]으로 많이 뽑아 보고, 새로 나온 세 칸의 합계가 지금보다 높은 비율을 센다.
 * 그 비율이 `p`이고 기대 장수는 기하분포의 `1/p`다.
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
 *
 * 해석적으로 풀지 않고 몬테카를로로 센다. 한 칸의 결과가 티어 → 레벨 → 스킬로 이어지고 블랙
 * 선추첨과 중복 배제까지 얽혀 있어, 정확한 분포를 접어 올리는 것보다 돌려 보는 편이 단순하다.
 */
@Service
class TicketExpectationService(
    private val scoreSkillRepository: ScoreSkillRepository,
    private val rollEngine: RollEngine,
    private val scoreService: ScoreService,
) {

    /** 뽑는 횟수. 지운 분포 테스트가 쓰던 규모와 같은 자릿수다. */
    private val trials = 20_000

    /** 화면에 보여 줄 "이만큼 쓰면" 지점. */
    private val milestones = listOf(1, 3, 5, 10, 20)

    /** HTTP 요청을 풀어 [evaluate]에 넘긴다. 레벨 번호를 그 스킬 풀의 사다리로 되돌린다. */
    fun evaluate(request: TicketExpectationRequest): TicketExpectationResponse {
        val grade = SkillRules.normalizeRequired(request.cardGrade, "Card grade is required.")
        val position = SkillRules.normalizeRequired(request.position, "Position is required.")
        val selections = request.selections.orEmpty()
        val keys = selections.mapNotNull { it.skillId }
        val levels = selections.map { selection ->
            val skill = selection.skillId?.let { scoreSkillRepository.findBySkillKey(it) }
            val ladder = SkillRules.gradeLadder(skill?.cardType)
            ladder[((selection.level ?: 1) - 1).coerceIn(0, ladder.size - 1)]
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
        )
    }

    /**
     * @param currentSkillKeys 지금 끼워 둔 스킬. 빈 칸이 있으면 그 칸은 0점으로 본다.
     * @param trainingBonuses 포지션 훈련 보너스. `스킬 -> 오르는 폭`이며 비어 있으면 보너스가 없다.
     * @param seed 테스트에서 결과를 고정하려고 받는다. 운영에서는 null이다.
     */
    fun evaluate(
        grade: String,
        variant: String,
        position: String,
        currentSkillKeys: List<String>,
        currentLevels: List<Level>,
        battingOrder: Int? = null,
        pitcherSlot: Int? = null,
        throwHand: Handedness? = null,
        batHand: Handedness? = null,
        userStats: Map<String, Double>? = null,
        lockSlotOne: Boolean = false,
        trainingBonuses: Map<String, Int> = emptyMap(),
        seed: Long? = null,
    ): TicketExpectationResponse {
        val score = { selections: List<ScoreCalculator.Selection> ->
            scoreService.scoreSelections(
                selections = selections,
                position = position,
                cardType = grade,
                battingOrder = battingOrder,
                pitcherSlot = pitcherSlot,
                throwHand = throwHand,
                batHand = batHand,
                userStats = userStats,
            ).total
        }

        val currentSelections = currentSkillKeys.mapIndexedNotNull { index, key ->
            val skill = scoreSkillRepository.findBySkillKey(key) ?: return@mapIndexedNotNull null
            ScoreCalculator.Selection(
                skill,
                SkillRules.levelIndex(currentLevels.getOrNull(index), skill.cardType),
                trainingBonuses[skill.skillKey] ?: 0,
            )
        }
        val currentTotal = if (currentSelections.isEmpty()) 0.0 else score(currentSelections)

        val lockable = rollEngine.canLockSlotOne(grade, currentSkillKeys.firstOrNull())
        val input = RollEngine.RollInput(
            grade = grade,
            variant = variant,
            position = position,
            ticket = TicketType.SKILL_CHANGE,
            currentSkillKeys = currentSkillKeys,
            currentLevels = currentLevels,
            lockSlotOne = lockSlotOne && lockable,
            // 스킬레벨보호권은 늘 쓴다고 본다. 안 쓰고 돌리는 사람이 없다시피 한데, 그 가정이
            // 없으면 뽑을 때마다 레벨이 내려갈 수 있어 기대 장수가 실제보다 훨씬 나쁘게 나온다.
            protectLevels = List(CardRules.slotCount(CardRules.resolveGrade(grade))) { true },
        )

        val tickets = TicketType.entries.map { ticket ->
            simulate(input.copy(ticket = ticket), currentTotal, score, trainingBonuses, seed)
        }

        return TicketExpectationResponse(
            currentTotal = round(currentTotal),
            slotOneLockable = lockable,
            tickets = tickets,
        )
    }

    private fun simulate(
        input: RollEngine.RollInput,
        currentTotal: Double,
        score: (List<ScoreCalculator.Selection>) -> Double,
        trainingBonuses: Map<String, Int>,
        seed: Long?,
    ): TicketExpectationResponse.TicketOutcome {
        // 티켓마다 씨드를 달리해야 세 티켓이 같은 난수열을 쓰지 않는다.
        val random = seed?.let { Random(it + input.ticket.ordinal) } ?: Random.Default
        var wins = 0
        var gainSum = 0.0
        var totalSum = 0.0

        repeat(trials) {
            val rolled = rollEngine.roll(input, random)
            val selections = rolled.mapNotNull { slot ->
                val skill: ScoreSkill = slot.skill ?: return@mapNotNull null
                ScoreCalculator.Selection(
                    skill,
                    SkillRules.levelIndex(slot.level, skill.cardType),
                    // 보너스는 슬롯에 붙으므로 새로 뽑힌 스킬도 목록에 있으면 오른다.
                    trainingBonuses[skill.skillKey] ?: 0,
                )
            }
            val total = if (selections.isEmpty()) 0.0 else score(selections)
            totalSum += total
            if (total > currentTotal) {
                wins++
                gainSum += total - currentTotal
            }
        }

        val p = wins.toDouble() / trials
        return TicketExpectationResponse.TicketOutcome(
            ticket = input.ticket.name,
            revocable = input.ticket.revocable,
            improveChance = round(p),
            // p가 0이면 기대 장수가 발산한다. 무한대를 내려보내지 않고 null로 두어
            // 화면이 "사실상 불가"로 읽게 한다.
            expectedTickets = if (wins == 0) null else round(1.0 / p),
            averageGain = if (wins == 0) null else round(gainSum / wins),
            averageTotal = round(totalSum / trials),
            chanceWithin = milestones.associateWith { round(1.0 - Math.pow(1.0 - p, it.toDouble())) },
        )
    }

    private fun round(value: Double): Double = Math.round(value * 10_000.0) / 10_000.0
}
