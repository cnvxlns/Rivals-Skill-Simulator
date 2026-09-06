package com.example.skillsim.service

import com.example.skillsim.config.ScoreDataLoader
import com.example.skillsim.enums.Level
import com.example.skillsim.repository.InMemoryScoreSkillRepository
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

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
}
