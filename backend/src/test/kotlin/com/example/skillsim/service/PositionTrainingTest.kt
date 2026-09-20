package com.example.skillsim.service

import com.example.skillsim.dto.ScoreRequest
import com.example.skillsim.dto.ScoreSelection
import com.example.skillsim.dto.SkillLevelBonus
import com.example.skillsim.enums.Level
import com.example.skillsim.model.ScoreEffect
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.InMemoryScoreSkillRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

/**
 * 포지션 훈련(포훈)이 슬롯에 붙여 준 스킬 레벨 보너스.
 *
 * 요청의 레벨은 보너스가 붙기 전 기본 레벨이고, 채점은 거기에 보너스를 얹은 레벨로 한다.
 * 규칙은 [PositionTrainingRules]에 적어 두었다.
 */
class PositionTrainingTest {

    private val gold = scoreSkill("G_001", "NORMAL", "BATTER", effect("파워", "1/2/3/4/5/6/7/8/9"))
    private val silver = scoreSkill("S_001", "NORMAL", "C", effect("파워", "1/2/3/4/5/6/7/8/9"))
    private val hof = scoreSkill("HOF_001", "HOF", "BATTER", effect("파워", "1/2/3/4/5/6"))
    private val pitcher = scoreSkill("G_900", "NORMAL", "SP", effect("구위", "1/2/3/4/5/6/7/8/9"))

    private val repository = InMemoryScoreSkillRepository().apply {
        saveAll(listOf(gold, silver, hof, pitcher))
    }

    private val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0, "구위" to 1.0))

    private fun request(
        level: Int,
        bonuses: List<SkillLevelBonus>?,
        skillId: String = "G_001",
        grade: String = "SIGNATURE",
    ) = ScoreRequest(
        cardGrade = grade,
        position = "C",
        battingOrder = 1,
        selections = listOf(ScoreSelection(skillId, level)),
        trainingBonuses = bonuses,
    )

    @Test
    fun `보너스만큼 위 단계의 수치로 채점한다`() {
        val plain = service.calculate(request(level = 1, bonuses = null))
        val boosted = service.calculate(request(level = 1, bonuses = listOf(SkillLevelBonus("G_001", 2))))

        // 사다리가 1/2/3/…이라 D는 1, B는 3이다.
        assertThat(plain.total).isEqualTo(1.0)
        assertThat(boosted.total).isEqualTo(3.0)
        assertThat(boosted.perSkill[0].levelBonus).isEqualTo(2)
        assertThat(boosted.perSkill[0].appliedGrade).isEqualTo(Level.B.name)
        // 설명의 수치도 채점에 쓴 레벨을 따라간다.
        assertThat(plain.perSkill[0].appliedGrade).isEqualTo(Level.D.name)
    }

    /** 공지에 S4 위가 없다. 사다리 끝을 넘는 만큼은 버린다. */
    @Test
    fun `사다리 끝을 넘으면 끝에서 멈춘다`() {
        val top = service.calculate(request(level = 9, bonuses = null))
        val boosted = service.calculate(request(level = 9, bonuses = listOf(SkillLevelBonus("G_001", 2))))

        assertThat(top.total).isEqualTo(9.0)
        assertThat(boosted.total).isEqualTo(9.0)
        assertThat(boosted.perSkill[0].appliedGrade).isEqualTo(Level.S4.name)
    }

    /** 끼우지 않은 스킬에 걸린 보너스는 지금 점수를 건드리지 않는다. 변경권으로 나올 때를 위한 값이다. */
    @Test
    fun `고르지 않은 스킬의 보너스는 점수를 바꾸지 않는다`() {
        val result = service.calculate(request(level = 1, bonuses = listOf(SkillLevelBonus("S_001", 2))))

        assertThat(result.total).isEqualTo(1.0)
        assertThat(result.perSkill[0].levelBonus).isZero()
    }

    @Test
    fun `보너스가 나오지 않는 티어는 거절한다`() {
        assertThatThrownBy {
            service.calculate(
                request(level = 1, bonuses = listOf(SkillLevelBonus("HOF_001", 1)), skillId = "HOF_001", grade = "HOF"),
            )
        }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("does not grant a level bonus")
    }

    @Test
    fun `포지션이 맞지 않는 보너스는 거절한다`() {
        assertThatThrownBy { service.calculate(request(level = 1, bonuses = listOf(SkillLevelBonus("G_900", 1)))) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("does not match requested position")
    }

    @Test
    fun `슬롯당 셋을 넘거나 같은 스킬이 두 번 오면 거절한다`() {
        assertThatThrownBy {
            service.calculate(
                request(
                    level = 1,
                    bonuses = listOf(
                        SkillLevelBonus("G_001", 1),
                        SkillLevelBonus("S_001", 1),
                        SkillLevelBonus("G_001", 2),
                    ),
                ),
            )
        }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("Duplicate position training bonus")

        assertThatThrownBy {
            service.calculate(
                request(
                    level = 1,
                    bonuses = List(PositionTrainingRules.MAX_BONUSES + 1) { SkillLevelBonus("G_00$it", 1) },
                ),
            )
        }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("at most ${PositionTrainingRules.MAX_BONUSES}")
    }

    @Test
    fun `오르는 폭은 1이나 2뿐이다`() {
        assertThatThrownBy { service.calculate(request(level = 1, bonuses = listOf(SkillLevelBonus("G_001", 3)))) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("between 1 and 2")
    }

    /**
     * 보너스는 선수가 아니라 슬롯에 붙는다. 그래서 변경권으로 새로 뽑은 스킬도 목록에 있으면 오른다.
     *
     * 스킬 풀을 둘로 좁혀 두었으므로 뽑으면 반드시 보너스가 걸린 스킬이 나온다. 보너스를 걸었을 때
     * 한 장의 평균 총점이 올라가면, 뽑기 결과에도 보너스가 실린 것이다.
     */
    @Test
    fun `새로 뽑은 스킬에도 보너스가 걸린다`() {
        val tickets = TicketExpectationService(repository, RollEngine(repository), service)
        fun evaluate(bonuses: Map<String, Int>) = tickets.evaluate(
            grade = "SIGNATURE",
            variant = "NONE",
            position = "C",
            currentSkillKeys = listOf("G_001"),
            currentLevels = listOf(Level.D),
            battingOrder = 1,
            trainingBonuses = bonuses,
            seed = 20260920L,
        )

        val plain = evaluate(emptyMap()).tickets.associateBy { it.ticket }
        val boosted = evaluate(mapOf("G_001" to 2, "S_001" to 2)).tickets.associateBy { it.ticket }

        for (ticket in plain.keys) {
            assertThat(boosted.getValue(ticket).averageTotal)
                .`as`(ticket)
                .isGreaterThan(plain.getValue(ticket).averageTotal)
        }
    }

    private fun scoreSkill(
        skillKey: String,
        cardType: String,
        position: String,
        vararg effects: ScoreEffect,
    ): ScoreSkill {
        val skill = ScoreSkill(
            skillKey = skillKey,
            cardType = cardType,
            position = position,
            name = skillKey,
            description = skillKey,
            effects = effects.toMutableList(),
        )
        skill.effects.forEach { it.skill = skill }
        return skill
    }

    private fun effect(stat: String, values: String) =
        ScoreEffect(stat = stat, condition = "ALWAYS", values = values)
}
