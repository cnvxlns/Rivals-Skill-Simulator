package com.example.skillsim.service

import com.example.skillsim.dto.DeckPlayerRequest
import com.example.skillsim.dto.DeckSaveRequest
import com.example.skillsim.enums.Handedness
import com.example.skillsim.model.DeckPlayer
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.model.DeckSkillSelection
import com.example.skillsim.repository.ScoreSkillRepository

/**
 * 덱 요청을 검증해 [DeckRoster]로 바꾼다.
 *
 * 실패는 [IllegalArgumentException]으로 던진다. HTTP 상태로 옮기는 것은 호출자 몫이며,
 * 덕분에 이 클래스는 Spring도 HTTP도 모르고 순수 JUnit으로 규칙 전부를 검증할 수 있다.
 * [SkillRules] → `ScoreService.normalizeRequiredOrThrow` → `badRequest()` 사슬과 같은 구조다.
 *
 * @param allowedStatNames 허용 능력치 이름. stat_weights.csv가 원천이며 하드코딩하지 않는다.
 */
internal class DeckValidator(
    private val scoreSkillRepository: ScoreSkillRepository,
    private val allowedSkillCardTypes: (String) -> List<String>,
    private val allowedStatNames: () -> Set<String>,
) {

    fun validate(request: DeckSaveRequest): DeckRoster {
        val starterCount = request.starterCount
            ?: throw IllegalArgumentException("Starter count is required.")
        val closerCount = request.closerCount
            ?: throw IllegalArgumentException("Closer count is required.")
        require(starterCount in DeckRules.STARTER_COUNT_RANGE) {
            "Starter count must be between ${DeckRules.STARTER_COUNT_RANGE.first} and " +
                "${DeckRules.STARTER_COUNT_RANGE.last}."
        }
        require(closerCount in DeckRules.CLOSER_COUNT_RANGE) {
            "Closer count must be between ${DeckRules.CLOSER_COUNT_RANGE.first} and " +
                "${DeckRules.CLOSER_COUNT_RANGE.last}."
        }

        val requested = request.players.orEmpty()
        require(requested.size == DeckRoster.ROSTER_SIZE) {
            "Deck must have exactly ${DeckRoster.ROSTER_SIZE} players, but was ${requested.size}."
        }

        // 자리 집합을 통째로 비교하면 누락·중복·초과를 한 번에 잡는다.
        val expected = DeckRules.expectedSlots(starterCount, closerCount)
        val slots = requested.map { it.slot.orEmpty().trim().uppercase() }
        require(slots.toSet().size == slots.size) {
            val duplicates = slots.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            "Duplicate deck slots: ${duplicates.sorted().joinToString(", ")}."
        }
        require(slots.toSet() == expected.toSet()) {
            val missing = expected.toSet() - slots.toSet()
            val unexpected = slots.toSet() - expected.toSet()
            buildString {
                append("Deck slots do not match the roster for ")
                append("$starterCount starters and $closerCount closers.")
                if (missing.isNotEmpty()) append(" Missing: ${missing.sorted().joinToString(", ")}.")
                if (unexpected.isNotEmpty()) append(" Unexpected: ${unexpected.sorted().joinToString(", ")}.")
            }
        }

        val players = requested.mapIndexed { index, player ->
            toPlayer(player, slots[index])
        }
        validateBattingOrders(players)

        return DeckRoster(starterCount = starterCount, closerCount = closerCount, players = players)
    }

    private fun validateBattingOrders(players: List<DeckPlayer>) {
        val orders = players.filter { DeckRules.isLineup(it.slot) }.mapNotNull { it.battingOrder }
        require(orders.toSortedSet() == DeckRules.BATTING_ORDERS.toSortedSet()) {
            "Starting batters must use each batting order from " +
                "${DeckRules.BATTING_ORDERS.first} to ${DeckRules.BATTING_ORDERS.last} exactly once."
        }
    }

    private fun toPlayer(request: DeckPlayerRequest, slot: String): DeckPlayer {
        val cardType = try {
            SkillRules.normalizeCardType(request.cardType)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("$slot: ${ex.message}")
        }

        val position = resolvePosition(request, slot)
        val battingOrder = resolveBattingOrder(request, slot)
        val relieverRole = resolveRelieverRole(request, slot)
        val skills = resolveSkills(request, slot, cardType, position)
        val stats = resolveStats(request, slot)
        validateHands(request, slot)

        return DeckPlayer(
            slot = slot,
            position = position,
            cardType = cardType,
            skills = skills,
            battingOrder = battingOrder,
            pitcherSlot = DeckRules.pitcherSlotNumber(slot),
            relieverRole = relieverRole,
            stats = stats,
            throwHand = request.throwHand,
            batHand = request.batHand,
        )
    }

    /** 주전·투수는 자리에서 유도하고 후보만 받는다. 불일치를 만들 여지를 없앤다. */
    private fun resolvePosition(request: DeckPlayerRequest, slot: String): String {
        DeckRules.positionForSlot(slot)?.let { return it }
        val position = SkillRules.normalizePosition(request.position)
        require(position.isNotEmpty()) { "$slot: Bench position is required." }
        require(SkillRules.matchesPosition(position, "BATTER")) {
            "$slot: Bench position must be a batter position, but was ${request.position}."
        }
        return position
    }

    private fun resolveBattingOrder(request: DeckPlayerRequest, slot: String): Int? {
        if (!DeckRules.isLineup(slot)) {
            // 조용히 무시하지 않고 거부한다. 후보나 투수에 타순이 붙어 있으면 클라이언트 버그다.
            require(request.battingOrder == null) { "$slot: Batting order is only for starting batters." }
            return null
        }
        val battingOrder = request.battingOrder
            ?: throw IllegalArgumentException("$slot: Batting order is required.")
        require(battingOrder in DeckRules.BATTING_ORDERS) {
            "$slot: Batting order must be between ${DeckRules.BATTING_ORDERS.first} and " +
                "${DeckRules.BATTING_ORDERS.last}."
        }
        return battingOrder
    }

    private fun resolveRelieverRole(request: DeckPlayerRequest, slot: String) =
        if (DeckRules.isReliever(slot)) {
            request.relieverRole
                ?: throw IllegalArgumentException("$slot: Reliever role is required.")
        } else {
            require(request.relieverRole == null) { "$slot: Reliever role is only for relievers." }
            null
        }

    private fun resolveSkills(
        request: DeckPlayerRequest,
        slot: String,
        cardType: String,
        position: String,
    ): List<DeckSkillSelection> {
        val requested = request.skills.orEmpty()
        val slotCount = SkillRules.slotCount(cardType)
        // ScoreService.calculate는 미만도 허용하지만, 저장되는 덱은 완성품이므로 정확히 요구한다.
        require(requested.size == slotCount) {
            "$slot: $cardType card must have exactly $slotCount skills, but had ${requested.size}."
        }

        val allowedCardTypes = allowedSkillCardTypes(cardType)
        val seen = mutableSetOf<String>()
        return requested.map { selection ->
            val skillId = selection.skillId.orEmpty().trim()
            require(skillId.isNotEmpty()) { "$slot: Skill id is required." }
            require(seen.add(skillId)) { "$slot: Duplicate skill $skillId." }

            // 없는 스킬은 400으로 돌려준다. 덱 저장 요청의 404는 "덱이 없다"로 읽힌다.
            val skill = scoreSkillRepository.findBySkillKey(skillId)
                ?: throw IllegalArgumentException("$slot: Unknown skill $skillId.")
            require(SkillRules.normalizeCardType(skill.cardType) in allowedCardTypes) {
                "$slot: Skill $skillId does not match card type $cardType."
            }
            require(SkillRules.matchesPosition(skill.position, position)) {
                "$slot: Skill $skillId does not match position $position."
            }
            val maxLevel = SkillRules.maxLevel(skill)
            val level = selection.level
            require(level != null && level in 1..maxLevel) {
                "$slot: Skill $skillId level must be between 1 and $maxLevel."
            }
            DeckSkillSelection(skillId = skillId, level = level)
        }
    }

    private fun resolveStats(request: DeckPlayerRequest, slot: String): Map<String, Double> {
        val stats = request.stats ?: return emptyMap()
        val allowed = allowedStatNames()
        return stats.mapKeys { (stat, _) -> stat.trim() }
            .onEach { (stat, value) ->
                require(stat in allowed) { "$slot: Unknown stat $stat." }
                // 1e999는 파싱 단계에서 조용히 Infinity가 되어 점수를 오염시킨다.
                require(value.isFinite()) { "$slot: Stat $stat must be a finite number." }
                require(value >= 0.0) { "$slot: Stat $stat must not be negative." }
            }
    }

    private fun validateHands(request: DeckPlayerRequest, slot: String) {
        require(request.throwHand != Handedness.SWITCH) {
            "$slot: Throwing hand cannot be SWITCH."
        }
    }
}
