package com.example.skillsim.service

import com.example.skillsim.enums.Level
import com.example.skillsim.enums.TicketType
import com.example.skillsim.service.TicketExpectationSolver.CombinationSlot
import com.example.skillsim.service.TicketExpectationSolver.ProblemInput
import com.example.skillsim.service.TicketExpectationSolver.SkillCandidate
import kotlin.math.roundToLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TicketExpectationSolverTest {
    private val solver = TicketExpectationSolver()

    @Test
    fun `threshold sums and displayed probabilities agree with exhaustive draws`() {
        val candidates = listOf(
            candidate("G_001", SkillTier.GOLD, mapOf(Level.S to 5.304)),
            candidate("G_002", SkillTier.GOLD, mapOf(Level.S to 4.104)),
            candidate("G_003", SkillTier.GOLD, mapOf(Level.S to 3.712)),
            candidate("S_001", SkillTier.SILVER, mapOf(Level.S to 3.105)),
            candidate("S_002", SkillTier.SILVER, mapOf(Level.S to 2.299)),
            candidate("B_001", SkillTier.BRONZE, mapOf(Level.S to 1.654)),
        )
        val current = 7.50
        val input = input(candidates, current, slotCount = 3)
        val outcomes = mutableMapOf<Set<CombinationSlot>, Pair<Double, Double>>()
        val tierChances = mapOf(SkillTier.IRON to 0.35, SkillTier.BRONZE to 0.30, SkillTier.SILVER to 0.20, SkillTier.GOLD to 0.15)

        // All levels are protected at S. Enumerate concrete rolls from a tiny pool as a reference,
        // grouping permutations into unordered combinations and summing their probabilities.
        fun draw(remaining: List<SkillCandidate>, slots: List<CombinationSlot>, total: Double, chance: Double) {
            if (slots.size == input.slotCount) {
                val combination = slots.toSet()
                outcomes[combination] = total to ((outcomes[combination]?.second ?: 0.0) + chance)
                return
            }
            for ((tier, tierChance) in tierChances) {
                val choices = remaining.filter { it.tier == tier }.ifEmpty { remaining }
                for (skill in choices) {
                    draw(
                        remaining - skill,
                        slots + CombinationSlot(skill.skillKey, Level.S),
                        total + skill.scoreAt(Level.S),
                        chance * tierChance / choices.size,
                    )
                }
            }
        }
        draw(candidates, emptyList(), 0.0, 1.0)
        val better = outcomes.filterValues { it.first.rounded() > current }
        val chance = better.values.sumOf { it.second }
        val result = solver.solve(input)

        assertTrue(better.size > 10)
        assertTrue(result.pinnedSlots.isEmpty())
        assertEquals(chance, result.improveChance, 1e-12)
        assertEquals(outcomes.values.sumOf { it.first * it.second }, result.averageTotal, 1e-12)
        assertEquals(better.values.sumOf { (score, p) -> (score - current) * p } / chance, result.averageGain!!, 1e-12)
        assertEquals(better.values.map { it.first.rounded() }.sortedDescending().take(10), result.betterCombinations.map { it.total })
        assertEquals(10, result.betterCombinations.distinctBy { it.slots.toSet() }.size)
        for (combo in result.betterCombinations) {
            assertEquals(better.getValue(combo.slots.toSet()).second, combo.chance, 1e-12)
        }
    }

    @Test
    fun `first-only WBC reroll respects later fixed skills blanks and protected levels`() {
        val a = candidate("G_001", SkillTier.GOLD, mapOf(Level.S4 to 8.0))
        val b = candidate("G_002", SkillTier.GOLD, mapOf(Level.S to 30.0, Level.S4 to 1000.0))
        val c = candidate("G_003", SkillTier.GOLD, mapOf(Level.S4 to 20.0))
        val wbc = candidate("WBC_001", SkillTier.WBC, mapOf(Level.S to 11.0, Level.S2 to 44.0))
        val result = solver.solve(
            input(listOf(a, b, c, wbc), 38.0, RollFamily.WBC, TicketType.SUPREME_SKILL_CHANGE).copy(
                currentLevels = listOf(Level.S4, Level.S, null),
                fixedSlots = mapOf(1 to (b to Level.S), 2 to (null to Level.D)),
            ),
        )
        assertEquals(0.55, result.improveChance, 1e-12)
        assertEquals(47.0, result.averageTotal, 1e-12)
        assertEquals(listOf(74.0, 50.0), result.betterCombinations.map { it.total })
        val best = result.betterCombinations.first()
        assertEquals(0.10, best.chance, 1e-12)
        assertEquals(
            listOf(CombinationSlot(wbc.skillKey, Level.S2), CombinationSlot(b.skillKey, Level.S), CombinationSlot(null, Level.D)),
            best.slots,
        )
    }

    @Test
    fun `locked first skill is excluded when every later draw falls back`() {
        val locked = candidate("G_001", SkillTier.GOLD, mapOf(Level.S to 10.0))
        val a = candidate("G_002", SkillTier.GOLD, mapOf(Level.S to 2.0))
        val b = candidate("G_003", SkillTier.GOLD, mapOf(Level.S to 4.0))
        val result = solver.solve(input(listOf(locked, a, b), 15.0).copy(lockedSlotOne = locked to Level.S))
        assertEquals(1.0, result.improveChance, 1e-12)
        assertEquals(1.0, result.averageGain!!, 1e-12)
        assertEquals(setOf(0), result.pinnedSlots)
        assertEquals(1, result.betterCombinations.size)
        val combo = result.betterCombinations.single()
        assertEquals(locked.skillKey, combo.slots.first().skillKey)
        assertEquals(listOf(b.skillKey, a.skillKey), combo.slots.drop(1).map { it.skillKey })
        assertEquals(1.0, combo.chance, 1e-12)
    }

    @Test
    fun `tiny improvement odds survive four independent high level rolls`() {
        val black = candidate("BLACK_001", SkillTier.BLACK, mapOf(Level.S to 10.0))
        val candidates = listOf(black) + List(3) { candidate("G_00${it + 1}", SkillTier.GOLD, mapOf(Level.S to 10.0)) }
        val result = solver.solve(
            input(candidates, 39.0, RollFamily.BLACK, TicketType.SUPREME_SKILL_CHANGE, slotCount = 4).copy(
                protectLevels = List(4) { false },
            ),
        )
        assertEquals(0.00000081, result.improveChance, 1e-16)
        assertEquals(1.2, result.averageTotal, 1e-12)
        assertEquals(1.0, result.averageGain!!, 1e-12)
        assertEquals(1, result.betterCombinations.size)
        val combo = result.betterCombinations.single()
        assertEquals(0.00000081, combo.chance, 1e-16)
        assertEquals(1, combo.slots.count { it.skillKey == black.skillKey })
        assertTrue(combo.slots.all { it.level == Level.S })
    }

    @Test
    fun `the first slot uses the correct supreme rates for Moment and HOF`() {
        val gold = List(3) { candidate("G_00${it + 1}", SkillTier.GOLD, mapOf(Level.S to 1.0)) }
        val moment = candidate("M_001", SkillTier.MOMENT, mapOf(Level.S to 5.0))
        val momentInput = input(gold + moment, 3.0, RollFamily.MOMENT, TicketType.SUPREME_SKILL_CHANGE)
        for ((grade, chance) in mapOf("MOMENT" to 0.06, "SUPREME_MOMENT" to 0.30)) {
            val result = solver.solve(momentInput.copy(grade = grade))
            assertEquals(chance, result.improveChance, 1e-12)
            assertEquals(3, result.betterCombinations.size)
            for (combo in result.betterCombinations) {
                assertEquals(moment.skillKey, combo.slots.first().skillKey)
                assertEquals(chance / 3, combo.chance, 1e-12)
            }
        }

        val hof = candidate("HOF_001", SkillTier.HOF, mapOf(Level.S to 5.0))
        val hofInput = input(gold + hof, 3.0, RollFamily.HOF, TicketType.SUPREME_SKILL_CHANGE)
        val result = solver.solve(hofInput)
        assertEquals(1.0 - 0.9 * 0.9 * 0.9, result.improveChance, 1e-12)
        // HOF_001 + 골드 2개(3종), 골드 1개+빈칸 1개(3종), 빈칸 2개(1종)로 총 7개 고유 조합이다.
        assertEquals(7, result.betterCombinations.size)
        assertEquals(result.improveChance, result.betterCombinations.sumOf { it.chance }, 1e-12)
        assertTrue(result.betterCombinations.all { combo -> combo.slots.count { it.skillKey == hof.skillKey } == 1 })
    }

    @Test
    fun `first slot bonus keeps boosted first skill distinct while merging remaining slot permutations`() {
        val candidates = listOf(
            SkillCandidate("G_001", "NORMAL", SkillTier.GOLD, mapOf(Level.S to 5.0), mapOf(Level.S to 9.0)),
            SkillCandidate("G_002", "NORMAL", SkillTier.GOLD, mapOf(Level.S to 4.0), mapOf(Level.S to 6.0)),
            SkillCandidate("G_003", "NORMAL", SkillTier.GOLD, mapOf(Level.S to 3.0), mapOf(Level.S to 3.0)),
        )
        val result = solver.solve(input(candidates, 12.0))
        assertEquals(setOf(0), result.pinnedSlots)
        // 첫 슬롯이 G_001(9+4+3=16)인 조합 1개와 첫 슬롯이 G_002(6+5+3=14)인 조합 1개로 나뉘고,
        // 각 조합 안에서 2·3번 슬롯의 2! 순열은 하나로 합쳐진다.
        assertEquals(2, result.betterCombinations.size)
        assertEquals(listOf(16.0, 14.0), result.betterCombinations.map { it.total })
        assertEquals(listOf("G_001", "G_002", "G_003"), result.betterCombinations[0].slots.map { it.skillKey })
        assertEquals(listOf("G_002", "G_001", "G_003"), result.betterCombinations[1].slots.map { it.skillKey })
        assertEquals(1.0 / 3.0, result.betterCombinations[0].chance, 1e-12)
        assertEquals(1.0 / 3.0, result.betterCombinations[1].chance, 1e-12)
    }

    @Test
    fun `ties at displayed precision are not improvements`() {
        val skill = candidate("G_001", SkillTier.GOLD, mapOf(Level.S to 0.0049))
        val input = input(listOf(skill), 0.0, RollFamily.WBC)
        val tie = solver.solve(input)
        assertEquals(0.0, tie.improveChance)
        assertNull(tie.averageGain)
        assertTrue(tie.betterCombinations.isEmpty())

        val better = solver.solve(input.copy(candidates = listOf(skill.copy(scoresByLevel = mapOf(Level.S to 0.005)))))
        assertEquals(1.0, better.improveChance, 1e-12)
        assertTrue(better.betterCombinations.all { it.total == 0.01 })
    }

    private fun candidate(key: String, tier: SkillTier, scores: Map<Level, Double>): SkillCandidate {
        val pool = when (tier) {
            SkillTier.WBC -> "WBC"
            SkillTier.BLACK -> "BLACK"
            SkillTier.MOMENT -> "MOMENT"
            SkillTier.HOF -> "HOF"
            else -> "NORMAL"
        }
        return SkillCandidate(key, pool, tier, scores)
    }

    private fun input(
        candidates: List<SkillCandidate>,
        current: Double,
        family: RollFamily = RollFamily.NORMAL,
        ticket: TicketType = TicketType.SKILL_CHANGE,
        slotCount: Int = 3,
    ) = ProblemInput(
        family = family,
        grade = "SIGNATURE",
        variant = "NONE",
        ticket = ticket,
        slotCount = slotCount,
        currentTotal = current,
        currentSkillKeys = List(slotCount) { null },
        currentLevels = List(slotCount) { Level.S },
        lockedSlotOne = null,
        protectLevels = List(slotCount) { true },
        candidates = candidates,
    )

    private fun Double.rounded(): Double = (this * 100.0).roundToLong() / 100.0
}
