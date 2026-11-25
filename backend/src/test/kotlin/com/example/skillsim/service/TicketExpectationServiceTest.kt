package com.example.skillsim.service

import com.example.skillsim.config.ScoreDataLoader
import com.example.skillsim.dto.ScoreSelection
import com.example.skillsim.dto.SkillLevelBonus
import com.example.skillsim.dto.TicketExpectationRequest
import com.example.skillsim.enums.Level
import com.example.skillsim.model.ScoreEffect
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.InMemoryScoreSkillRepository
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.web.server.ResponseStatusException

class TicketExpectationServiceTest {

    private val repository = InMemoryScoreSkillRepository().apply {
        val loader = ScoreDataLoader(this)
        saveAll(
            reader("score_skills.csv").use { s ->
                reader("score_effects.csv").use { e -> loader.readScoreSkills(s, e) }
            },
        )
    }

    private val statWeights = reader("stat_weights.csv").use { ScoreDataLoader(repository).readStatWeights(it) }

    private val service = TicketExpectationService(
        repository,
        RollEngine(repository),
        ScoreService(repository, ScoreCalculator(), statWeights),
    )

    private fun reader(path: String) =
        InputStreamReader(ClassPathResource(path).inputStream, StandardCharsets.UTF_8)

    private fun evaluate(keys: List<String>, level: Level = Level.S, grade: String = "SIGNATURE") =
        service.evaluate(
            grade = grade,
            variant = "NONE",
            position = "C",
            currentSkillKeys = keys,
            currentLevels = List(keys.size) { level },
            battingOrder = 1,
            seed = 20260907L,
        )

    /** 약한 스킬을 끼고 있으면 거의 매번 나아진다. */
    @Test
    fun `아이언만 끼고 있으면 개선 확률이 높다`() {
        val iron = repository.findAll().filter { it.skillKey.startsWith("I_") }
            .filter { SkillRules.matchesPosition(it.position, "C") }
            .take(3).map { it.skillKey }

        val result = evaluate(iron, Level.D)

        assertThat(result.tickets).allSatisfy { outcome ->
            assertThat(outcome.improveChance).isGreaterThan(0.5)
            assertThat(outcome.expectedTickets).isNotNull()
        }
    }

    /**
     * 이미 최상급이면 기대 장수가 발산한다.
     *
     * 무한대를 그대로 내보내면 화면이 그리기 어렵다. null로 두어 "사실상 불가"로 읽게 한다.
     */
    @Test
    fun `더 나아질 수 없으면 기대 장수가 null이다`() {
        val best = service.let {
            repository.findAll()
                .filter { skill -> skill.cardType == "NORMAL" && skill.skillKey.startsWith("G_") }
                .filter { skill -> SkillRules.matchesPosition(skill.position, "C") }
        }
        // 점수가 가장 높은 셋을 고르기 위해 표를 쓰지 않고 직접 채점한다.
        val scorer = ScoreService(repository, ScoreCalculator(), statWeights)
        val topThree = best.sortedByDescending { skill ->
            scorer.scoreSelections(
                listOf(ScoreCalculator.Selection(skill, SkillRules.maxLevel(skill))),
                position = "C", cardType = "SIGNATURE", battingOrder = 1,
            ).total
        }.take(3).map { it.skillKey }

        val result = evaluate(topThree, Level.S4)

        assertThat(result.tickets).allSatisfy { outcome ->
            assertThat(outcome.improveChance).isZero()
            assertThat(outcome.expectedTickets).isNull()
            assertThat(outcome.averageGain).isNull()
        }
    }

    /**
     * 최고급은 아이언이 안 나오고 첫 칸이 골드로 고정돼 평균 총점이 확실히 높다.
     *
     * 반대로 일반과 고급은 평범한 카드에서 티어 확률이 같아 기대 장수가 거의 붙는다.
     * 그 차이는 숫자가 아니라 `revocable`로 드러난다.
     */
    @Test
    fun `최고급이 나머지 둘보다 낫고 일반과 고급은 비슷하다`() {
        val iron = repository.findAll().filter { it.skillKey.startsWith("I_") }
            .filter { SkillRules.matchesPosition(it.position, "C") }
            .take(3).map { it.skillKey }

        val byTicket = evaluate(iron, Level.D).tickets.associateBy { it.ticket }
        val basic = byTicket.getValue("SKILL_CHANGE")
        val premium = byTicket.getValue("PREMIUM_SKILL_CHANGE")
        val supreme = byTicket.getValue("SUPREME_SKILL_CHANGE")

        assertThat(supreme.averageTotal).isGreaterThan(basic.averageTotal)
        assertThat(supreme.improveChance).isGreaterThan(basic.improveChance)
        assertThat(premium.improveChance).isCloseTo(basic.improveChance, org.assertj.core.api.Assertions.within(0.05))

        assertThat(basic.revocable).isFalse()
        assertThat(premium.revocable).isTrue()
        assertThat(supreme.revocable).isTrue()
    }

    /**
     * 스킬레벨보호권은 화면에서 고르지 않고 늘 쓴 것으로 본다.
     *
     * 보호권이 없으면 뽑은 레벨이 그대로라 지금 레벨이 무엇이든 평균 총점이 같다. 보호가
     * 쓰면 지금 레벨 아래로 내려가지 않으므로, 높은 레벨에서 시작할수록 평균이 높다.
     * 이 차이가 보이면 보호권이 실제로 걸린 것이다.
     */
    @Test
    fun `스킬레벨보호권을 항상 쓴 것으로 센다`() {
        val gold = repository.findAll().filter { it.skillKey.startsWith("G_") }
            .filter { SkillRules.matchesPosition(it.position, "C") }
            .take(3).map { it.skillKey }

        val fromLow = evaluate(gold, Level.D).tickets.associateBy { it.ticket }
        val fromHigh = evaluate(gold, Level.S4).tickets.associateBy { it.ticket }

        for (ticket in fromLow.keys) {
            assertThat(fromHigh.getValue(ticket).averageTotal)
                .`as`(ticket)
                .isGreaterThan(fromLow.getValue(ticket).averageTotal)
        }
    }

    /** 누적 성공 확률은 기하분포다. 장수가 늘수록 단조 증가해야 한다. */
    @Test
    fun `누적 성공 확률은 장수가 늘수록 올라간다`() {
        val iron = repository.findAll().filter { it.skillKey.startsWith("I_") }
            .filter { SkillRules.matchesPosition(it.position, "C") }
            .take(3).map { it.skillKey }

        val outcome = evaluate(iron, Level.D).tickets.first()
        val curve = outcome.chanceWithin.toSortedMap().values.toList()

        assertThat(curve).isSorted
        assertThat(curve.first()).isEqualTo(outcome.improveChance)
        assertThat(curve.last()).isLessThanOrEqualTo(1.0)
    }

    /** 잠글 수 없는 등급이면 응답이 그렇게 알려 준다. 화면은 이 값으로 토글을 끈다. */
    @Test
    fun `잠금 가능 여부를 응답이 알려 준다`() {
        val gold = repository.findAll().first { it.skillKey.startsWith("G_") && SkillRules.matchesPosition(it.position, "C") }

        assertThat(evaluate(listOf(gold.skillKey), grade = "SIGNATURE").slotOneLockable).isTrue()
        assertThat(evaluate(listOf(gold.skillKey), grade = "HOF").slotOneLockable).isFalse()
    }

    @Test
    fun `빈 칸을 건너뛰지 않고 레벨 보호를 적용한 뒤 합계만 반올림한다`() {
        val tickets = fixtureService(
            listOf(skill("G_001", 1), skill("G_002", 1), skill("G_003", 1)),
            powerWeight = 0.004,
        ).evaluate(
            TicketExpectationRequest(
                cardGrade = "SIGNATURE",
                position = "C",
                selections = listOf(null, ScoreSelection("G_001", 9), null),
                lockSlotOne = true,
            ),
        )

        assertThat(tickets.slotOneLockable).isFalse()
        assertThat(tickets.currentTotal).isZero()
        assertThat(tickets.tickets).allSatisfy { ticket ->
            assertThat(ticket.improveChance).isCloseTo(1.0, within(1e-12))
            assertThat(ticket.betterCombinations.map { it.slots.toSet() }).doesNotHaveDuplicates()
            assertThat(ticket.betterCombinations).isNotEmpty().allSatisfy { combination ->
                assertThat(combination.total).isEqualTo(0.01)
                assertThat(combination.gain).isEqualTo(0.01)
                assertThat(combination.slots.count { it.level == "S4" }).isEqualTo(1)
                assertThat(combination.slots.map { it.slot }).containsOnlyNulls()
            }
        }
    }

    @Test
    fun `첫 슬롯만 변경하면 나머지 스킬과 빈 슬롯을 그대로 둔다`() {
        val request = TicketExpectationRequest(
            cardGrade = "SIGNATURE",
            position = "C",
            selections = listOf(ScoreSelection("G_001", 1), null, ScoreSelection("G_003", 7)),
            rerollFirstSlotOnly = true,
        )
        val tickets = fixtureService(listOf(skill("G_001", 1), skill("G_002", 3), skill("G_003", 2)))
            .evaluate(request)

        assertThat(tickets.currentTotal).isEqualTo(3.0)
        assertThat(tickets.tickets).allSatisfy { ticket ->
            assertThat(ticket.improveChance).isCloseTo(0.5, within(1e-12))
            assertThat(ticket.betterCombinations).isNotEmpty().allSatisfy { combination ->
                assertThat(combination.slots.map { it.skillId }).containsExactly("G_002", null, "G_003")
                assertThat(combination.slots[0].name).isEqualTo("G_002")
                assertThat(combination.slots[2].level).isEqualTo("S2")
                assertThat(combination.total).isEqualTo(5.0)
                assertThat(combination.gain).isEqualTo(2.0)
            }
            assertThat(ticket.betterCombinations.sumOf { it.chance }).isCloseTo(0.5, within(1e-12))
        }
    }

    @Test
    fun `지원하지 않는 첫 슬롯 모드 요청은 거절한다`() {
        val fixture = fixtureService(listOf(skill("G_001", 1)))
        val request = TicketExpectationRequest(
            cardGrade = "HOF",
            position = "C",
            selections = listOf(ScoreSelection("G_001", 1)),
            rerollFirstSlotOnly = true,
        )

        assertThatThrownBy { fixture.evaluate(request) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("400 BAD_REQUEST")
        assertThatThrownBy { fixture.evaluate(request.copy(cardGrade = "SIGNATURE", lockSlotOne = true)) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("400 BAD_REQUEST")
    }

    @Test
    fun `기본 능력치 조건을 현재 점수와 후보 점수에 함께 적용한다`() {
        val request = TicketExpectationRequest(
            cardGrade = "SIGNATURE",
            position = "C",
            selections = listOf(ScoreSelection("G_001", 1), ScoreSelection("G_002", 1)),
            rerollFirstSlotOnly = true,
        )
        val fixture = fixtureService(
            listOf(skill("G_001", 10, "기본주루수비합155이상"), skill("G_002", 4), skill("G_003", 2)),
        )
        val below = fixture.evaluate(request.copy(baseStats = mapOf("주루" to 70.0, "수비" to 80.0)))
        val above = fixture.evaluate(request.copy(baseStats = mapOf("주루" to 75.0, "수비" to 80.0)))

        assertThat(below.currentTotal).isEqualTo(4.0)
        assertThat(below.tickets.first().betterCombinations.first().total).isEqualTo(6.0)
        assertThat(above.currentTotal).isEqualTo(14.0)
        assertThat(above.tickets.first().averageTotal).isEqualTo(10.0)
        assertThat(above.tickets.first().betterCombinations).isEmpty()
    }

    @Test
    fun `모든 슬롯을 바꿀 때 첫 슬롯 보너스를 받는 조합만 개선 확률에 넣는다`() {
        val fixture = fixtureService(transcendenceSkills())
        val request = TicketExpectationRequest(
            cardGrade = "SIGNATURE",
            position = "C",
            selections = listOf(ScoreSelection("G_001", 5), ScoreSelection("G_002", 5), ScoreSelection("G_003", 5)),
            transcendenceLevel = 9,
        )
        val result = fixture.evaluate(request)
        assertThat(result.currentTotal).isEqualTo(15.0)
        assertThat(result.tickets).allSatisfy { ticket ->
            assertThat(ticket.improveChance).isCloseTo(1.0 / 3.0, within(1e-12))
            val combination = ticket.betterCombinations.single()
            assertThat(combination.total).isEqualTo(18.0)
            assertThat(combination.chance).isCloseTo(1.0 / 3.0, within(1e-12))
            assertThat(combination.slots.first().skillId).isEqualTo("G_002")
            assertThat(combination.slots.map { it.level }).containsExactly("S", "S", "S")
            assertThat(combination.slots.map { it.appliedGrade }).containsExactly("S1", "S", "S")
            assertThat(combination.slots.map { it.slot }).containsExactly(1, null, null)
        }

        val locked = fixture.evaluate(request.copy(lockSlotOne = true))
        assertThat(locked.tickets).allSatisfy { ticket ->
            assertThat(ticket.improveChance).isZero()
            assertThat(ticket.averageTotal).isEqualTo(15.0)
        }
        val emptyFirst = fixture.evaluate(request.copy(selections = listOf(null, ScoreSelection("G_002", 5))))
        assertThat(emptyFirst.currentTotal).isEqualTo(4.0)
    }

    @Test
    fun `첫 슬롯만 바꿀 때 초월은 첫 슬롯에 적용하고 뒤에 놓인 포지션특훈은 유지한다`() {
        val result = fixtureService(transcendenceSkills()).evaluate(
            TicketExpectationRequest(
                cardGrade = "SIGNATURE",
                position = "C",
                selections = listOf(ScoreSelection("G_001", 5), null, ScoreSelection("G_003", 5)),
                rerollFirstSlotOnly = true,
                trainingBonuses = listOf(SkillLevelBonus("G_003", 2)),
                transcendenceLevel = 9,
            ),
        )

        assertThat(result.currentTotal).isEqualTo(13.0)
        assertThat(result.tickets).allSatisfy { ticket ->
            assertThat(ticket.improveChance).isCloseTo(0.5, within(1e-12))
            val improved = ticket.betterCombinations.single()
            assertThat(improved.total).isEqualTo(15.0)
            assertThat(improved.slots.map { it.skillId }).containsExactly("G_002", null, "G_003")
            assertThat(improved.slots.map { it.appliedGrade }).containsExactly("S1", null, "S2")
        }
    }

    @Test
    fun `시그니처 블랙은 기본 첫 슬롯 +1과 9초월 +2를 각각 기댓값에 반영한다`() {
        val skills = listOf(
            skillWithValues("BLACK_001", "1/2/3/4/5/8/12", pool = "BLACK"),
            skillWithValues("G_001", "1/2/3/4/5/6/7/8/9"),
            skillWithValues("G_002", "1/2/3/4/5/6/7/8/9"),
            skillWithValues("G_003", "1/2/3/4/5/6/7/8/9"),
        )
        val fixture = fixtureService(skills)
        val baseRequest = TicketExpectationRequest(
            cardGrade = "SIGNATURE_BLACK",
            position = "C",
            selections = listOf(
                ScoreSelection("G_001", 5),
                ScoreSelection("BLACK_001", 5),
                ScoreSelection("G_002", 5),
                ScoreSelection("G_003", 5),
            ),
        )

        // 기본(9초월 미달성): 첫 슬롯 G_001이 S->S1(6), 나머지 S(5+5+5)로 합계 21.
        // 최고급 변경권에서 BLACK_001이 첫 슬롯(확률 1/4)에 오면 S->S1(8) + 5 + 5 + 5 = 23이다.
        // 나머지 2~4번 슬롯의 3! = 6개 순열은 하나의 조합으로 합쳐져 확률이 그대로 0.25가 된다.
        val baseResult = fixture.evaluate(baseRequest)
        assertThat(baseResult.currentTotal).isEqualTo(21.0)
        val baseSupreme = baseResult.tickets.first { it.ticket == "SUPREME_SKILL_CHANGE" }
        assertThat(baseSupreme.improveChance).isCloseTo(0.25, within(1e-12))
        val baseCombo = baseSupreme.betterCombinations.single()
        assertThat(baseCombo.total).isEqualTo(23.0)
        assertThat(baseCombo.chance).isCloseTo(0.25, within(1e-12))
        assertThat(baseCombo.slots.first().skillId).isEqualTo("BLACK_001")
        assertThat(baseCombo.slots.map { it.appliedGrade }).containsExactly("S1", "S", "S", "S")
        assertThat(baseCombo.slots.map { it.slot }).containsExactly(1, null, null, null)

        // 9초월 달성: 첫 슬롯 G_001이 S->S2(7)로 합계 22, BLACK_001이 첫 슬롯이면 S->S2(12)로 합계 27이다.
        val nineResult = fixture.evaluate(baseRequest.copy(transcendenceLevel = 9))
        assertThat(nineResult.currentTotal).isEqualTo(22.0)
        val nineSupreme = nineResult.tickets.first { it.ticket == "SUPREME_SKILL_CHANGE" }
        assertThat(nineSupreme.improveChance).isCloseTo(0.25, within(1e-12))
        val nineCombo = nineSupreme.betterCombinations.single()
        assertThat(nineCombo.total).isEqualTo(27.0)
        assertThat(nineCombo.chance).isCloseTo(0.25, within(1e-12))
        assertThat(nineCombo.slots.first().skillId).isEqualTo("BLACK_001")
        assertThat(nineCombo.slots.map { it.appliedGrade }).containsExactly("S2", "S", "S", "S")
        assertThat(nineCombo.slots.map { it.slot }).containsExactly(1, null, null, null)
    }

    private fun transcendenceSkills() = listOf(
        skillWithValues("G_001", "1/2/3/4/5/6/7/8/9"),
        skillWithValues("G_002", "1/1/1/1/4/8/8/8/8"),
        skillWithValues("G_003", "1/2/3/4/5/6/7/8/9"),
    )

    private fun fixtureService(skills: List<ScoreSkill>, powerWeight: Double = 1.0): TicketExpectationService {
        val fixture = InMemoryScoreSkillRepository().apply { saveAll(skills) }
        return TicketExpectationService(
            fixture,
            RollEngine(fixture),
            ScoreService(fixture, ScoreCalculator(), mapOf("파워" to powerWeight)),
        )
    }

    private fun skill(skillKey: String, effectValue: Int, condition: String = "ALWAYS"): ScoreSkill =
        skillWithValues(skillKey, List(9) { effectValue }.joinToString("/"), condition)

    private fun skillWithValues(
        skillKey: String,
        values: String,
        condition: String = "ALWAYS",
        pool: String = "NORMAL",
    ) = ScoreSkill(
        skillKey = skillKey,
        cardType = pool,
        position = "BATTER",
        name = skillKey,
        description = "",
        effects = mutableListOf(
            ScoreEffect(stat = "파워", condition = condition, values = values),
        ),
    )
}
