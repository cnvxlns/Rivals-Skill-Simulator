package com.example.skillsim.service

import com.example.skillsim.enums.Level
import com.example.skillsim.enums.TicketType
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.ScoreSkillRepository
import kotlin.random.Random
import org.springframework.stereotype.Component

/**
 * 스킬 변경권을 한 장 썼을 때 나오는 결과를 뽑는다.
 *
 * 커밋 `6ef8a77`에서 걷어냈던 `SkillService`의 롤 부분을 되살린 것이다. 그때는 시뮬레이터
 * 화면이 쓰던 코드였고, 지금은 "몇 장 쓰면 나아지나"를 세는 데 쓴다. 일반 `MOMENT`는 카드마다
 * 정해진 고유 모먼트 스킬 하나만 첫 슬롯에 나오고, `SUPREME_MOMENT`는 포지션에 맞는 모든
 * 모먼트 스킬이 첫 슬롯 후보가 된다.
 *
 * 옛 코드의 `CardType`은 등급·변형·스킬풀이 뒤섞인 축이었다. 여기서는 [RollFamily]로 다시
 * 그려 지금의 등급 + 변형 축을 쓴다.
 *
 * 난수를 주입받는다. 분포 테스트가 씨드를 고정할 수 있어야 하기 때문이다.
 */
@Component
class RollEngine(
    private val scoreSkillRepository: ScoreSkillRepository,
) {

    /**
     * 한 장을 써서 나온 슬롯들.
     *
     * @param skill 뽑힌 스킬. 후보가 하나도 없으면 null이다.
     */
    data class RolledSlot(val skill: ScoreSkill?, val level: Level)

    /**
     * @param currentSkillKeys 지금 끼워 둔 스킬. 잠근 슬롯을 그대로 두는 데 쓴다.
     * @param currentLevels 지금 레벨. 레벨 보호가 이 값 아래로 떨어지지 않게 막는다.
     * @param lockSlotOne 첫 슬롯을 그대로 두는가. [canLockSlotOne]이 아니면 무시한다.
     * @param protectLevels 슬롯별 레벨 보호 사용 여부.
     * @param momentSkillKey 일반 `MOMENT` 카드에서 첫 슬롯에 뜰 수 있는 고유 모먼트 스킬 키. 첫 슬롯이 이미 모먼트 스킬이면 생략 가능.
     */
    data class RollInput(
        val grade: String,
        val variant: String,
        val position: String,
        val ticket: TicketType,
        val currentSkillKeys: List<String?>,
        val currentLevels: List<Level?>,
        val lockSlotOne: Boolean = false,
        val protectLevels: List<Boolean> = emptyList(),
        val momentSkillKey: String? = null,
    )

    fun roll(input: RollInput, random: Random): List<RolledSlot> {
        val family = RollFamily.of(input.grade, input.variant)
        val grade = CardRules.resolveGrade(input.grade)
        val slotCount = CardRules.slotCount(grade)
        val levels = List(slotCount) { input.currentLevels.getOrNull(it) }
        val protect = List(slotCount) { input.protectLevels.getOrNull(it) ?: false }

        val slots = arrayOfNulls<RolledSlot>(slotCount)
        val used = mutableSetOf<String>()

        // 잠근 칸은 뽑기 전에 먼저 박아 둔다. 그 스킬이 다른 칸에 또 나오면 안 되므로 used에도 넣는다.
        if (input.lockSlotOne && canLockSlotOne(grade, input.currentSkillKeys.firstOrNull())) {
            val locked = input.currentSkillKeys.firstOrNull()?.let { scoreSkillRepository.findBySkillKey(it) }
            slots[0] = RolledSlot(locked, levels[0] ?: Level.D)
            locked?.let { used += it.skillKey }
        }

        // 블랙은 한 장까지다. 고급은 20%, 최고급은 반드시 한 칸을 미리 잡는다.
        val forcedBlackSlot = pickForcedBlackSlot(family, input.ticket, slots, random)

        for (i in 0 until slotCount) {
            if (slots[i] != null) continue
            val slot = rollSlot(family, input, i, levels[i], protect[i], used, forcedBlackSlot == i, random)
            slots[i] = slot
            slot.skill?.let { used += it.skillKey }
        }
        return slots.map { it ?: RolledSlot(null, Level.D) }
    }

    private fun pickForcedBlackSlot(
        family: RollFamily,
        ticket: TicketType,
        slots: Array<RolledSlot?>,
        random: Random,
    ): Int? {
        if (family != RollFamily.BLACK && family != RollFamily.WBC_BLACK) return null
        val alreadyBlack = slots.any { it?.skill != null && SkillTier.of(it.skill!!) == SkillTier.BLACK }
        if (alreadyBlack) return null
        if (random.nextDouble() >= RollTables.blackPreselectChance(ticket)) return null
        val open = slots.indices.filter { slots[it] == null }
        return if (open.isEmpty()) null else open[random.nextInt(open.size)]
    }

    private fun rollSlot(
        family: RollFamily,
        input: RollInput,
        slotIndex: Int,
        currentLevel: Level?,
        protect: Boolean,
        used: Set<String>,
        forcedBlack: Boolean,
        random: Random,
    ): RolledSlot {
        val position = input.position
        val ticket = input.ticket

        // 블랙이 걸린 칸. 티어가 정해져 있고 레벨만 뽑는다.
        if (forcedBlack) {
            val skill = pickSkill("BLACK", SkillTier.BLACK, used, position, random)
            val level = if (family == RollFamily.WBC_BLACK) Level.S
            else pick(RollTables.BLACK_LEVEL_WEIGHTS, Level.D, random)
            return RolledSlot(skill, applyProtection(level, currentLevel, protect, "BLACK"))
        }

        return when (family) {
            // 모먼트 전용은 최고급을 썼을 때 첫 칸에서만 나온다. 둘째·셋째 칸에는 안 나온다.
            // 일반 모먼트는 카드에 정해진 고유 모먼트 스킬 하나만, 슈프림 모먼트는 포지션 내 전체 모먼트 풀이 후보가 된다.
            RollFamily.MOMENT ->
                if (ticket == TicketType.SUPREME_SKILL_CHANGE && slotIndex == 0) {
                    val grade = gradeOf(input)
                    val table = RollTables.momentSlotOneTable(grade)
                    val tier = pick(table.mapValues { it.value.values.sum() }, SkillTier.GOLD, random)
                    val pool = if (tier == SkillTier.MOMENT) "MOMENT" else "NORMAL"
                    val level = pick(table.getValue(tier), Level.S, random)
                    val skill = if (grade == "MOMENT") {
                        val momentSkill = resolveMomentSkill(input, used)
                        if (tier == SkillTier.MOMENT) momentSkill else pickSkill(pool, tier, used, position, random)
                    } else {
                        pickSkill(pool, tier, used, position, random)
                    }
                    RolledSlot(
                        skill,
                        applyProtection(level, currentLevel, protect, pool),
                    )
                } else {
                    // 모먼트 카드는 레벨 보호가 항상 켜져 있다.
                    rollBasicTier(family, ticket, slotIndex, currentLevel, true, used, position, random)
                }

            RollFamily.HOF -> {
                val table = RollTables.tierTable(family, ticket, slotIndex)
                val tier = pick(table.mapValues { it.value.values.sum() }, SkillTier.GOLD, random)
                val pool = if (tier == SkillTier.HOF) "HOF" else "NORMAL"
                val level = pick(table.getValue(tier), Level.D, random)
                RolledSlot(
                    pickSkill(pool, tier, used, position, random),
                    applyProtection(level, currentLevel, protect, pool),
                )
            }

            // WBC 변형은 항상 S로 나온다. 확률만큼 WBC 전용, 나머지는 골드다.
            RollFamily.WBC, RollFamily.WBC_BLACK -> {
                val wbc = random.nextDouble() < wbcChanceFor(family, ticket)
                val pool = if (wbc) "WBC" else "NORMAL"
                val tier = if (wbc) SkillTier.WBC else SkillTier.GOLD
                RolledSlot(
                    pickSkill(pool, tier, used, position, random),
                    applyProtection(Level.S, currentLevel, protect, pool),
                )
            }

            RollFamily.BLACK, RollFamily.NORMAL ->
                rollBasicTier(family, ticket, slotIndex, currentLevel, protect, used, position, random)
        }
    }

    /** 아이언~골드에서 티어와 레벨을 함께 뽑는다. 최고급의 첫 칸은 골드가 보장된다. */
    private fun rollBasicTier(
        family: RollFamily,
        ticket: TicketType,
        slotIndex: Int,
        currentLevel: Level?,
        protect: Boolean,
        used: Set<String>,
        position: String,
        random: Random,
    ): RolledSlot {
        val table = RollTables.tierTable(family, ticket, slotIndex)
        val tier = if (ticket == TicketType.SUPREME_SKILL_CHANGE && slotIndex == 0) {
            SkillTier.GOLD
        } else {
            pick(table.mapValues { it.value.values.sum() }, SkillTier.BRONZE, random)
        }
        val levelWeights = table[tier] ?: RollTables.tierTable(family, ticket, 1)[tier].orEmpty()
        val level = pick(levelWeights, Level.D, random)
        return RolledSlot(
            pickSkill("NORMAL", tier, used, position, random),
            applyProtection(level, currentLevel, protect, "NORMAL"),
        )
    }

    /**
     * 같은 티어 안에서는 균등하게 고른다.
     *
     * 지운 코드의 `SkillRules.rollWeight`도 모든 스킬에 1을 돌려줬다. 스킬별 등장 가중치는
     * 공식 자료에 없어 만들지 않는다.
     */
    private fun pickSkill(
        pool: String,
        tier: SkillTier,
        used: Set<String>,
        position: String,
        random: Random,
    ): ScoreSkill? {
        val available = scoreSkillRepository.findByCardTypeIgnoreCase(pool)
            .filter { SkillRules.matchesPosition(it.position, position) }
            .filter { it.skillKey !in used }
        if (available.isEmpty()) return null
        // 그 티어에 후보가 없으면 풀 전체에서 고른다. 옛 코드와 같은 폴백이다.
        val matches = available.filter { SkillTier.of(it) == tier }
        val pool2 = matches.ifEmpty { available }
        return pool2[random.nextInt(pool2.size)]
    }

    private fun resolveMomentSkill(input: RollInput, used: Set<String>): ScoreSkill? {
        val requestedKey = input.momentSkillKey?.trim()?.takeIf(String::isNotEmpty)
            ?.let { SkillRules.normalizeRequired(it, "Moment skill selection is required.") }
        val slotOneMomentKey = input.currentSkillKeys.firstOrNull()?.takeIf(String::isNotBlank)
            ?.let { SkillRules.normalizeRequired(it, "Skill selection is required.") }
            ?.let(scoreSkillRepository::findBySkillKey)
            ?.takeIf { SkillTier.of(it) == SkillTier.MOMENT }
            ?.skillKey
        val momentKey = when {
            requestedKey != null -> {
                if (slotOneMomentKey != null && slotOneMomentKey != requestedKey) {
                    throw IllegalArgumentException("Slot 1 moment skill must match the card's fixed moment skill.")
                }
                requestedKey
            }
            slotOneMomentKey != null -> slotOneMomentKey
            else -> throw IllegalArgumentException("Moment skill key is required for MOMENT cards.")
        }
        val skill = scoreSkillRepository.findBySkillKey(momentKey)
        if (
            skill == null ||
            SkillTier.of(skill) != SkillTier.MOMENT ||
            !SkillRules.matchesPosition(skill.position, input.position)
        ) {
            throw IllegalArgumentException("Selected moment skill is unavailable for this card: $momentKey")
        }
        return skill.takeIf { it.skillKey !in used }
    }

    private fun gradeOf(input: RollInput): String = CardRules.resolveGrade(input.grade)

    private fun <T> pick(weights: Map<T, Double>, fallback: T, random: Random): T {
        val total = weights.values.sum()
        if (total <= 0.0) return fallback
        var roll = random.nextDouble() * total
        for ((key, weight) in weights) {
            roll -= weight
            if (roll <= 0.0) return key
        }
        return weights.keys.lastOrNull() ?: fallback
    }

    companion object {
        internal fun wbcChanceFor(family: RollFamily, ticket: TicketType): Double =
            if (family == RollFamily.WBC_BLACK && ticket == TicketType.PREMIUM_SKILL_CHANGE) {
                0.005 / 0.95
            } else {
                RollTables.wbcSlotChance(ticket)
            }

        /** 뽑은 레벨이 지금보다 낮으면 보호가 켜져 있을 때 지금 레벨을 지킨다. */
        internal fun applyProtection(rolled: Level, current: Level?, protect: Boolean, pool: String): Level {
            val ladder = SkillRules.gradeLadder(pool)
            val rolledIndex = SkillRules.levelIndex(rolled, pool)
            val currentIndex = if (protect) SkillRules.levelIndex(current, pool) else rolledIndex
            return ladder[maxOf(rolledIndex, currentIndex) - 1]
        }

        /**
         * 이 등급이 첫 슬롯을 잠글 수 있는가.
         *
         * 시그니처·프라임·임팩트는 그냥 되고, 모먼트 계열은 **첫 칸이 이미 모먼트 전용일 때만**
         * 된다("모먼트 스킬을 한 번이라도 띄운 적 있는" 카드가 이 상태다).
         * 시그니처 블랙과 HOF는 잠글 수 없다. 지운 코드의 `validateLockRules`와 같은 규칙이다.
         */
        val LOCKABLE_GRADES = setOf("SIGNATURE", "PRIME", "IMPACT", "MOMENT", "SUPREME_MOMENT")
    }

    fun canLockSlotOne(grade: String, slotOneSkillKey: String?): Boolean {
        val normalized = CardRules.resolveGrade(grade)
        if (normalized !in LOCKABLE_GRADES) return false
        if (normalized != "MOMENT" && normalized != "SUPREME_MOMENT") return true
        val skill = slotOneSkillKey?.let { scoreSkillRepository.findBySkillKey(it) } ?: return false
        return SkillTier.of(skill) == SkillTier.MOMENT
    }
}
