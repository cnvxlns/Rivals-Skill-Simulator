package com.example.skillsim.service

import com.example.skillsim.enums.Level
import com.example.skillsim.enums.TicketType
import java.util.PriorityQueue
import kotlin.math.roundToLong
import org.springframework.stereotype.Component

/** 점수가 더 높은 조합과 그 조합이 변경권 한 장에서 나올 확률을 계산한다. */
@Component
class TicketExpectationSolver {
    data class SkillCandidate(
        val skillKey: String,
        val pool: String,
        val tier: SkillTier,
        /** 레벨 보너스를 반영한 반올림 전 점수. */
        val scoresByLevel: Map<Level, Double>,
        /** 첫 슬롯에만 초월 보너스가 더 붙는 경우의 점수. */
        val firstSlotScoresByLevel: Map<Level, Double>? = null,
    ) {
        fun scoreAt(level: Level, firstSlot: Boolean = false): Double =
            (if (firstSlot) firstSlotScoresByLevel ?: scoresByLevel else scoresByLevel)[level] ?: 0.0
    }

    data class ProblemInput(
        val family: RollFamily,
        val grade: String,
        val variant: String,
        val ticket: TicketType,
        val slotCount: Int,
        val currentTotal: Double,
        val currentSkillKeys: List<String?>,
        val currentLevels: List<Level?>,
        val lockedSlotOne: Pair<SkillCandidate, Level>?,
        val protectLevels: List<Boolean>,
        val candidates: List<SkillCandidate>,
        /** 0부터 세는 고정 슬롯. 후보가 null이면 빈 칸을 유지한다. */
        val fixedSlots: Map<Int, Pair<SkillCandidate?, Level>> = emptyMap(),
        /** 일반 `MOMENT` 카드의 첫 슬롯에 등장할 수 있는 고유 모먼트 스킬 키. */
        val momentSkillKey: String? = null,
    )

    data class CombinationSlot(val skillKey: String?, val level: Level)

    data class BetterCombination(
        val slots: List<CombinationSlot>,
        val total: Double,
        val chance: Double,
    )

    data class Solution(
        val improveChance: Double,
        val averageGain: Double?,
        val averageTotal: Double,
        /** 순서가 무관한 슬롯 순열을 하나로 합친 상위 조합. 개선 통계에는 여기에 표시되지 않는 조합도 모두 포함한다. */
        val betterCombinations: List<BetterCombination>,
        /** 0부터 세는 슬롯 중 위치가 고정되거나 첫 슬롯 보너스로 구분되는 인덱스. */
        val pinnedSlots: Set<Int> = emptySet(),
    )

    fun solve(input: ProblemInput): Solution = Search(input).solve()

    private class Bucket(val index: Int, val tier: SkillTier, val skills: List<SkillCandidate>)
    private data class Draw(val pool: String, val tier: SkillTier, val chance: Double, val levels: Map<Level, Double>)
    private data class SlotGroup(val bucket: Bucket?, val levels: Map<Level, Double>, val firstSlot: Boolean)
    private data class Choice(val skillKey: String?, val level: Level, val score: Double, val weight: Double)

    private data class Half(val first: Choice?, val second: Choice?) {
        val score = (first?.score ?: 0.0) + (second?.score ?: 0.0)
        val weight = (first?.weight ?: 1.0) * (second?.weight ?: 1.0)
        val firstKey = first?.skillKey
        val secondKey = second?.skillKey

        fun has(key: String?): Boolean = key != null && (key == firstKey || key == secondKey)
    }

    private data class SkillPair(val low: String, val high: String) {
        companion object {
            fun of(a: String, b: String): SkillPair = if (a <= b) SkillPair(a, b) else SkillPair(b, a)
        }
    }

    /** 점수가 높은 쪽부터 누적해 희귀한 개선 확률도 전체 확률과 빼지 않고 구한다. */
    private class ScoreSeries(halves: List<Half>) {
        private val scores = DoubleArray(halves.size)
        private val weights = DoubleArray(halves.size + 1)
        private val moments = DoubleArray(halves.size + 1)

        init {
            for (i in halves.indices.reversed()) {
                val half = halves[i]
                scores[i] = half.score
                weights[i] = weights[i + 1] + half.weight
                moments[i] = moments[i + 1] + half.weight * half.score
            }
        }

        fun weightAt(index: Int) = weights[index]
        fun momentAt(index: Int) = moments[index]
        fun countAt(index: Int) = scores.size - index

        fun firstBetter(prefix: Double, currentCents: Long): Int {
            var low = 0
            var high = scores.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (cents(prefix + scores[middle]) > currentCents) high = middle else low = middle + 1
            }
            return low
        }
    }

    /** 뒤쪽 두 슬롯의 점수 합을 정렬하고, 앞쪽 스킬과 겹치는 항을 조회할 수 있게 한다. */
    private class Suffix(val halves: List<Half>) {
        val all = ScoreSeries(halves)
        val bySkill: Map<String, ScoreSeries>
        val byPair: Map<SkillPair, ScoreSeries>

        init {
            val skills = mutableMapOf<String, MutableList<Half>>()
            val pairs = mutableMapOf<SkillPair, MutableList<Half>>()
            for (half in halves) {
                val a = half.firstKey
                val b = half.secondKey
                if (a != null) skills.getOrPut(a) { mutableListOf() }.add(half)
                if (b != null) skills.getOrPut(b) { mutableListOf() }.add(half)
                if (a != null && b != null) pairs.getOrPut(SkillPair.of(a, b)) { mutableListOf() }.add(half)
            }
            bySkill = skills.mapValues { ScoreSeries(it.value) }
            byPair = pairs.mapValues { ScoreSeries(it.value) }
        }
    }

    private data class RankedCombination(val slots: List<CombinationSlot>, val score: Double)

    private class Search(private val input: ProblemInput) {
        private val fixed = buildMap<Int, Pair<SkillCandidate?, Level>> {
            input.lockedSlotOne?.let { put(0, it) }
            putAll(input.fixedSlots)
        }
        private val fixedKeys = fixed.values.mapNotNull { it.first?.skillKey }.toSet()
        private val fixedScore = fixed.entries.sumOf { (index, selection) ->
            val (skill, level) = selection
            skill?.scoreAt(level, firstSlot = index == 0) ?: 0.0
        }
        private val hasFirstSlotScores = input.candidates.any { it.firstSlotScoresByLevel != null }
        private val candidateByKey = input.candidates.associateBy { it.skillKey }
        private val open = (0 until input.slotCount).filterNot { it in fixed }
        /**
         * 순서가 바뀌어도 점수와 적용 등급이 같은 슬롯들.
         *
         * 첫 슬롯 보너스가 있으면 0번 슬롯은 보너스를 받는 자리라 구분하고 1번 이후만 묶는다.
         * 첫 슬롯 보너스가 없으면 열린 슬롯 전체를 하나의 조합으로 묶는다.
         */
        private val permutableSlots = open.filter { it != 0 || !hasFirstSlotScores }
        private val pinnedSlots: Set<Int> =
            if (permutableSlots.size <= 1) (0 until input.slotCount).toSet()
            else (0 until input.slotCount).filterNot { it in permutableSlots }.toSet()
        private val slotOrder: Comparator<CombinationSlot> = Comparator { a, b ->
            when {
                a.skillKey == b.skillKey -> b.level.ordinal.compareTo(a.level.ordinal)
                a.skillKey == null -> 1
                b.skillKey == null -> -1
                else -> {
                    val skillA = candidateByKey.getValue(a.skillKey)
                    val skillB = candidateByKey.getValue(b.skillKey)
                    val momentCmp = (skillB.pool == "MOMENT").compareTo(skillA.pool == "MOMENT")
                    if (momentCmp != 0) return@Comparator momentCmp
                    val scoreCmp = skillB.scoreAt(b.level).compareTo(skillA.scoreAt(a.level))
                    if (scoreCmp != 0) return@Comparator scoreCmp
                    val tierCmp = skillB.tier.ordinal.compareTo(skillA.tier.ordinal)
                    if (tierCmp != 0) return@Comparator tierCmp
                    a.skillKey.compareTo(b.skillKey)
                }
            }
        }
        private val split = open.size / 2
        private val currentCents = cents(input.currentTotal)
        private val pools: Map<String, List<Bucket>>
        private val skillBucket: Map<String, Bucket>
        private val used: IntArray
        private val scenarios: List<Pair<Double, List<List<Draw>>>>
        private val options = mutableMapOf<SlotGroup, List<Choice>>()
        private val prefixCache = mutableMapOf<List<SlotGroup>, List<Half>>()
        private val suffixCache = mutableMapOf<List<SlotGroup>, Suffix>()
        private val best = PriorityQueue(compareBy<RankedCombination> { it.score })
        private val bestSlots = mutableSetOf<List<CombinationSlot>>()
        private var improveChance = 0.0
        private var improvedMoment = 0.0
        private var averageTotal = 0.0

        init {
            val eligibleCandidates = if (input.grade == "MOMENT") {
                val momentCandidates = input.candidates.filter { it.pool == "MOMENT" }
                if (input.momentSkillKey != null && momentCandidates.none { it.skillKey == input.momentSkillKey }) {
                    throw IllegalArgumentException("Unknown moment candidate: ${input.momentSkillKey}")
                }
                val targetMomentKey = input.momentSkillKey ?: momentCandidates.singleOrNull()?.skillKey
                if (
                    targetMomentKey == null &&
                    0 in open &&
                    input.ticket == TicketType.SUPREME_SKILL_CHANGE &&
                    momentCandidates.isNotEmpty()
                ) {
                    throw IllegalArgumentException(
                        "momentSkillKey is required for MOMENT cards when multiple moment candidates exist.",
                    )
                }
                input.candidates.filter { it.pool != "MOMENT" || it.skillKey == targetMomentKey }
            } else {
                input.candidates
            }
            var bucketIndex = 0
            pools = eligibleCandidates.filterNot { it.skillKey in fixedKeys }.groupBy { it.pool }.mapValues { (_, pool) ->
                pool.groupBy { it.tier }.map { (tier, skills) -> Bucket(bucketIndex++, tier, skills) }
            }
            used = IntArray(bucketIndex)
            skillBucket = pools.values.flatten().flatMap { bucket -> bucket.skills.map { it.skillKey to bucket } }.toMap()
            val blackChance = if (
                open.isNotEmpty() && (input.family == RollFamily.BLACK || input.family == RollFamily.WBC_BLACK) &&
                fixed.values.none { it.first?.tier == SkillTier.BLACK }
            ) RollTables.blackPreselectChance(input.ticket) else 0.0

            scenarios = buildList {
                if (blackChance < 1.0) add((1.0 - blackChance) to open.map { draws(it, false) })
                if (blackChance > 0.0) {
                    for (slot in open) {
                        add((blackChance / open.size) to open.map { draws(it, it == slot) })
                    }
                }
            }
        }

        fun solve(): Solution {
            for ((chance, draws) in scenarios) {
                visitRoutes(draws, mutableListOf(), chance)
            }
            val combinations = best.map {
                BetterCombination(it.slots, cents(it.score) / 100.0, chanceOf(it.slots))
            }.sortedWith(compareByDescending<BetterCombination> { it.total }.thenByDescending { it.chance })
            return Solution(
                improveChance = improveChance.coerceIn(0.0, 1.0),
                averageGain = if (improveChance > 0.0) improvedMoment / improveChance else null,
                averageTotal = averageTotal,
                betterCombinations = combinations,
                pinnedSlots = pinnedSlots,
            )
        }

        private fun draws(slot: Int, forcedBlack: Boolean): List<Draw> {
            val family = input.family
            val ticket = input.ticket
            val supremeFirst = ticket == TicketType.SUPREME_SKILL_CHANGE && slot == 0
            val weights = when {
                forcedBlack -> mapOf(
                    SkillTier.BLACK to if (family == RollFamily.WBC_BLACK) mapOf(Level.S to 1.0)
                    else RollTables.BLACK_LEVEL_WEIGHTS,
                )
                family == RollFamily.WBC || family == RollFamily.WBC_BLACK -> {
                    val wbcChance = RollEngine.wbcChanceFor(family, ticket)
                    mapOf(SkillTier.WBC to mapOf(Level.S to wbcChance), SkillTier.GOLD to mapOf(Level.S to 1.0 - wbcChance))
                }
                family == RollFamily.MOMENT && supremeFirst -> RollTables.momentSlotOneTable(input.grade)
                else -> {
                    val table = RollTables.tierTable(family, ticket, slot)
                    if (supremeFirst && family != RollFamily.HOF) table.filterKeys { it == SkillTier.GOLD } else table
                }
            }
            val total = weights.values.sumOf { it.values.sum() }
            return weights.mapNotNull { (tier, row) ->
                val tierWeight = row.values.sum()
                if (tierWeight <= 0.0) return@mapNotNull null
                val pool = when (tier) {
                    SkillTier.WBC -> "WBC"
                    SkillTier.MOMENT -> "MOMENT"
                    SkillTier.HOF -> "HOF"
                    SkillTier.BLACK -> "BLACK"
                    else -> "NORMAL"
                }
                val protect = input.protectLevels.getOrElse(slot) { false } ||
                    (family == RollFamily.MOMENT && !supremeFirst)
                val levels = mutableMapOf<Level, Double>()
                for ((level, weight) in row) {
                    val protected = RollEngine.applyProtection(level, input.currentLevels.getOrNull(slot), protect, pool)
                    levels.merge(protected, weight / tierWeight, Double::plus)
                }
                Draw(pool, tier, tierWeight / total, levels)
            }
        }

        /**
         * 실제 티어가 정해지면 중복을 뺀 추첨 분모는 이미 그 티어에서 고른 개수로 결정된다.
         * 요청한 티어가 소진되면 풀 전체의 남은 스킬에 같은 확률을 부여한다.
         */
        private fun steps(draws: List<Draw>, slotIndex: Int): Map<SlotGroup, Double> = buildMap {
            val firstSlot = slotIndex == 0 && hasFirstSlotScores
            for (draw in draws) {
                val pool = pools[draw.pool].orEmpty()
                val matching = pool.firstOrNull { it.tier == draw.tier && remaining(it) > 0 }
                if (matching != null) {
                    addStep(SlotGroup(matching, draw.levels, firstSlot), draw.chance / remaining(matching))
                    continue
                }
                val available = pool.sumOf(::remaining)
                if (available == 0) {
                    addStep(SlotGroup(null, draw.levels, firstSlot), draw.chance)
                    continue
                }
                for (bucket in pool) {
                    if (remaining(bucket) > 0) addStep(SlotGroup(bucket, draw.levels, firstSlot), draw.chance / available)
                }
            }
        }

        private fun MutableMap<SlotGroup, Double>.addStep(group: SlotGroup, chance: Double) {
            this[group] = getOrDefault(group, 0.0) + chance
        }

        private fun remaining(bucket: Bucket) = bucket.skills.size - used[bucket.index]

        private fun visitRoutes(draws: List<List<Draw>>, route: MutableList<SlotGroup>, chance: Double) {
            if (route.size == draws.size) {
                evaluateRoute(route, chance)
                return
            }
            for ((group, weight) in steps(draws[route.size], open[route.size])) {
                val index = group.bucket?.index
                if (index != null) used[index]++
                route.add(group)
                visitRoutes(draws, route, chance * weight)
                route.removeAt(route.lastIndex)
                if (index != null) used[index]--
            }
        }

        private fun choices(group: SlotGroup): List<Choice> = options.getOrPut(group) {
            val skills = group.bucket?.skills ?: listOf(null)
            skills.flatMap { skill ->
                group.levels.map { (level, weight) ->
                    Choice(skill?.skillKey, level, skill?.scoreAt(level, group.firstSlot) ?: 0.0, weight)
                }
            }
        }

        private fun halves(groups: List<SlotGroup>): List<Half> {
            if (groups.isEmpty()) return listOf(Half(null, null))
            val first = choices(groups[0])
            if (groups.size == 1) return first.map { Half(it, null) }
            val second = choices(groups[1])
            return buildList {
                for (a in first) for (b in second) {
                    if (a.skillKey == null || a.skillKey != b.skillKey) add(Half(a, b))
                }
            }
        }

        /** 경계 이상의 조합만 확률을 더하며, 네 칸 조합의 데카르트 곱을 만들지 않는다. */
        private fun evaluateRoute(route: List<SlotGroup>, chance: Double) {
            val prefixGroups = route.take(split)
            val suffixGroups = route.drop(split)
            val prefix = prefixCache.getOrPut(prefixGroups) { halves(prefixGroups).sortedByDescending { it.score } }
            val suffix = suffixCache.getOrPut(suffixGroups) { Suffix(halves(suffixGroups).sortedBy { it.score }) }
            for (left in prefix) {
                val score = fixedScore + left.score
                val weight = chance * left.weight
                val a = left.firstKey?.let { suffix.bySkill[it] }
                val b = left.secondKey?.let { suffix.bySkill[it] }
                val both = if (a != null && b != null) {
                    suffix.byPair[SkillPair.of(requireNotNull(left.firstKey), requireNotNull(left.secondKey))]
                } else null

                val all = suffix.all
                val compatible = all.countAt(0) - (a?.countAt(0) ?: 0) - (b?.countAt(0) ?: 0) + (both?.countAt(0) ?: 0)
                if (compatible == 0) continue
                val allWeight = all.weightAt(0) - (a?.weightAt(0) ?: 0.0) - (b?.weightAt(0) ?: 0.0) +
                    (both?.weightAt(0) ?: 0.0)
                val allMoment = all.momentAt(0) - (a?.momentAt(0) ?: 0.0) - (b?.momentAt(0) ?: 0.0) +
                    (both?.momentAt(0) ?: 0.0)
                averageTotal += weight * (score * allWeight + allMoment)

                val start = all.firstBetter(score, currentCents)
                if (all.countAt(start) == 0) continue
                val startA = a?.firstBetter(score, currentCents) ?: 0
                val startB = b?.firstBetter(score, currentCents) ?: 0
                val startBoth = both?.firstBetter(score, currentCents) ?: 0
                val winners = all.countAt(start) - (a?.countAt(startA) ?: 0) - (b?.countAt(startB) ?: 0) +
                    (both?.countAt(startBoth) ?: 0)
                if (winners == 0) continue
                val winWeight = all.weightAt(start) - (a?.weightAt(startA) ?: 0.0) - (b?.weightAt(startB) ?: 0.0) +
                    (both?.weightAt(startBoth) ?: 0.0)
                val winMoment = all.momentAt(start) - (a?.momentAt(startA) ?: 0.0) - (b?.momentAt(startB) ?: 0.0) +
                    (both?.momentAt(startBoth) ?: 0.0)
                improveChance += weight * winWeight
                improvedMoment += weight * ((score - input.currentTotal) * winWeight + winMoment)
            }
            keepTop(prefix, suffix.halves)
        }

        private fun keepTop(prefix: List<Half>, suffix: List<Half>) {
            val highest = suffix.lastOrNull() ?: return
            for (left in prefix) {
                val score = fixedScore + left.score
                if (!canAdd(score + highest.score)) break
                for (rightIndex in suffix.indices.reversed()) {
                    val right = suffix[rightIndex]
                    val total = score + right.score
                    if (!canAdd(total)) break
                    if (right.has(left.firstKey) || right.has(left.secondKey)) continue
                    val choices = listOfNotNull(left.first, left.second, right.first, right.second).iterator()
                    val rawSlots = List(input.slotCount) { slot ->
                        val fixedSlot = fixed[slot]
                        if (fixedSlot != null) CombinationSlot(fixedSlot.first?.skillKey, fixedSlot.second)
                        else choices.next().let { CombinationSlot(it.skillKey, it.level) }
                    }
                    val slots = canonicalize(rawSlots)
                    if (slots in bestSlots) continue
                    val canonicalTotal = scoreOf(slots)
                    if (!canAdd(canonicalTotal)) continue
                    bestSlots.add(slots)
                    if (best.size == DISPLAY_LIMIT) bestSlots.remove(best.remove().slots)
                    best.add(RankedCombination(slots, canonicalTotal))
                }
            }
        }

        private fun canonicalize(rawSlots: List<CombinationSlot>): List<CombinationSlot> {
            if (permutableSlots.size <= 1) return rawSlots
            val sorted = permutableSlots.map { rawSlots[it] }.sortedWith(slotOrder)
            val result = rawSlots.toMutableList()
            for ((idx, slot) in permutableSlots.withIndex()) {
                result[slot] = sorted[idx]
            }
            return result
        }

        private fun scoreOf(slots: List<CombinationSlot>): Double =
            slots.withIndex().sumOf { (index, slot) ->
                val candidate = slot.skillKey?.let(candidateByKey::get) ?: return@sumOf 0.0
                candidate.scoreAt(slot.level, firstSlot = index == 0 && hasFirstSlotScores)
            }

        private fun canAdd(score: Double): Boolean =
            cents(score) > currentCents && (best.size < DISPLAY_LIMIT || score > best.element().score)

        /** 순서가 무관한 슬롯들의 모든 고유 순열과 티어 폴백 경로의 확률을 합산한다. */
        private fun chanceOf(canonicalSlots: List<CombinationSlot>): Double =
            permutationsOf(canonicalSlots).sumOf(::orderedChanceOf)

        private fun permutationsOf(canonicalSlots: List<CombinationSlot>): List<List<CombinationSlot>> {
            if (permutableSlots.size <= 1) return listOf(canonicalSlots)
            val current = permutableSlots.map { canonicalSlots[it] }.toMutableList()
            val distinct = LinkedHashSet<List<CombinationSlot>>()
            fun permute(start: Int) {
                if (start == current.lastIndex) {
                    distinct.add(current.toList())
                    return
                }
                val seenAtStart = HashSet<CombinationSlot>()
                for (i in start..current.lastIndex) {
                    val candidate = current[i]
                    if (!seenAtStart.add(candidate)) continue
                    if (i != start) {
                        current[i] = current[start]
                        current[start] = candidate
                    }
                    permute(start + 1)
                    if (i != start) {
                        current[start] = current[i]
                        current[i] = candidate
                    }
                }
            }
            permute(0)
            return distinct.map { perm ->
                val copy = canonicalSlots.toMutableList()
                for ((idx, slot) in permutableSlots.withIndex()) {
                    copy[slot] = perm[idx]
                }
                copy
            }
        }

        private fun orderedChanceOf(slots: List<CombinationSlot>): Double = scenarios.sumOf { (chance, draws) ->
            used.fill(0)
            var product = chance
            for ((index, slot) in open.withIndex()) {
                val chosen = slots[slot]
                val bucket = chosen.skillKey?.let(skillBucket::getValue)
                val weight = steps(draws[index], slot).entries.sumOf { (group, weight) ->
                    if (group.bucket === bucket) weight * group.levels.getOrDefault(chosen.level, 0.0) else 0.0
                }
                product *= weight
                if (weight == 0.0) break
                if (bucket != null) used[bucket.index]++
            }
            product
        }
    }

    private companion object {
        const val DISPLAY_LIMIT = 10
        fun cents(score: Double): Long = (score * 100.0).roundToLong()
    }
}
