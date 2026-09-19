package com.example.skillsim.service

import com.example.skillsim.dto.ScoreRequest
import com.example.skillsim.dto.ScoreSelection
import com.example.skillsim.model.ScoreEffect
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.ScoreSkillRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class ScoreServiceTest {

    @Test
    fun `listSkills filters by card type and position`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val batter = scoreSkill("S_001", "NORMAL", "BATTER", "좌투선호", effect("파워", "ALWAYS", "1/2/3"))
        val pitcher = scoreSkill(
            "S_006", "NORMAL", "PITCHER", "좌타 스페셜리스트", effect("파워", "ALWAYS", "1/2/3"),
        )
        `when`(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(listOf(batter, pitcher))
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val options = service.listSkills("normal", null, "batter")

        assertThat(options).hasSize(1)
        assertThat(options[0].skillId).isEqualTo("S_001")
        assertThat(options[0].maxLevel).isEqualTo(3)
        assertThat(options[0].description).isEqualTo("좌투선호")
    }

    @Test
    fun `모먼트 전용 스킬은 첫 칸에서만 채점을 받는다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val gold = scoreSkill("G_001", "NORMAL", "BATTER", "골드", effect("파워", "ALWAYS", "1/2/3"))
        val moment = scoreSkill("M_001", "MOMENT", "BATTER", "모먼트", effect("파워", "ALWAYS", "1"))
        `when`(repository.findBySkillKey("G_001")).thenReturn(gold)
        `when`(repository.findBySkillKey("M_001")).thenReturn(moment)
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        fun request(vararg ids: String) = ScoreRequest(
            cardGrade = "MOMENT",
            position = "C",
            battingOrder = 1,
            selections = ids.map { ScoreSelection(it, 1) },
        )

        assertThat(service.calculate(request("M_001", "G_001")).total).isGreaterThan(0.0)
        assertThatThrownBy { service.calculate(request("G_001", "M_001")) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("first slot")
    }

    @Test
    fun `listSkills exposes card type grade labels and clamped max level`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val black = scoreSkill(
            "BLACK_001", "BLACK", "BATTER", "퓨어 히터", effect("파워", "ALWAYS", "5/8/11/14/17"),
        )
        `when`(repository.findByCardTypeIgnoreCase("BLACK")).thenReturn(listOf(black))
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val options = service.listSkills("SIGNATURE_BLACK", null, "BATTER")

        assertThat(options).hasSize(1)
        assertThat(options[0].maxLevel).isEqualTo(5)
        assertThat(options[0].levelLabels).containsExactly("D", "C", "B", "A", "S")
    }

    @Test
    fun `listSkills for special card types includes normal pool skills`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val gold = scoreSkill(
            "G_001", "NORMAL", "BATTER", "배팅머신", effect("파워", "ALWAYS", "1/2/3/4/5/6/7/8/9"),
        )
        val wbc = scoreSkill("WBC_001", "WBC", "BATTER", "WBC 플레이어", effect("파워", "ALWAYS", "9/11/13"))
        `when`(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(listOf(gold))
        `when`(repository.findByCardTypeIgnoreCase("WBC")).thenReturn(listOf(wbc))
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val options = service.listSkills("WBC", null, "BATTER")

        assertThat(options).extracting("skillId").containsExactly("G_001", "WBC_001")
    }

    @Test
    fun `listSkills for WBC signature black includes normal WBC and black pools`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val gold = scoreSkill(
            "G_001", "NORMAL", "BATTER", "배팅머신", effect("파워", "ALWAYS", "1/2/3/4/5/6/7/8/9"),
        )
        val wbc = scoreSkill("WBC_001", "WBC", "BATTER", "WBC 플레이어", effect("파워", "ALWAYS", "9/11/13"))
        val black = scoreSkill(
            "BLACK_001", "BLACK", "BATTER", "시그니처 블랙", effect("파워", "ALWAYS", "9/11/13"),
        )
        `when`(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(listOf(gold))
        `when`(repository.findByCardTypeIgnoreCase("WBC")).thenReturn(listOf(wbc))
        `when`(repository.findByCardTypeIgnoreCase("BLACK")).thenReturn(listOf(black))
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val options = service.listSkills("WBC_SIGNATURE_BLACK", null, "BATTER")

        assertThat(options).extracting("skillId").containsExactly("G_001", "WBC_001", "BLACK_001")
    }

    @Test
    fun `listSkills for supreme moment includes normal and moment pools`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val gold = scoreSkill(
            "G_001", "NORMAL", "BATTER", "배팅머신", effect("파워", "ALWAYS", "1/2/3/4/5/6/7/8/9"),
        )
        val moment = scoreSkill("M_003", "MOMENT", "BATTER", "슬러거", effect("파워", "ALWAYS", "5"))
        `when`(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(listOf(gold))
        `when`(repository.findByCardTypeIgnoreCase("MOMENT")).thenReturn(listOf(moment))
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val options = service.listSkills("SUPREME_MOMENT", null, "BATTER")

        assertThat(options).extracting("skillId").containsExactly("G_001", "M_003")
    }

    @Test
    fun `calculate returns total per skill and per stat`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val skill = scoreSkill(
            "S_001", "NORMAL", "BATTER", "좌투선호",
            effect("파워", "ALWAYS", "1/2/3"),
            effect("정확", "ALWAYS", "2/4/6"),
        )
        `when`(repository.findBySkillKey("S_001")).thenReturn(skill)
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.10, "정확" to 0.90))

        val response = service.calculate(
            ScoreRequest(
                cardType = "NORMAL",
                position = "BATTER",
                battingOrder = 1,
                selections = listOf(ScoreSelection("S_001", 2)),
            ),
        )

        assertThat(response.total).isEqualTo(5.80)
        assertThat(response.perSkill).hasSize(1)
        assertThat(response.perSkill[0].skillId).isEqualTo("S_001")
        assertThat(response.perSkill[0].breakdown).hasSize(2)
        assertThat(response.perSkill[0].breakdown[0].stat).isEqualTo("파워")
        assertThat(response.perSkill[0].breakdown[0].subtotal).isEqualTo(2.20)
        // perStat 은 스킬로 증가한 스탯 절대치(순수 증가량)이므로 weight·조건확률을 제외한 value 만 표시한다.
        assertThat(response.perStat).extracting("stat", "value")
            .containsExactly(tuple("파워", 2.00), tuple("정확", 4.00))
    }

    @Test
    fun `calculate uses position inning probabilities and user stats`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val skill = scoreSkill(
            "G_057", "NORMAL", "PITCHER", "하드 트레이닝",
            effect("구위", "7회이후", "10"),
            proportionalEffect("변화", "ALWAYS", "0.05", "지구력"),
        )
        `when`(repository.findBySkillKey("G_057")).thenReturn(skill)
        val service = ScoreService(repository, ScoreCalculator(), mapOf("구위" to 1.0, "변화" to 1.0))

        val response = service.calculate(
            ScoreRequest(
                cardType = "NORMAL",
                position = "RP",
                pitcherSlot = 1,
                userStats = mapOf("지구력" to 200.0),
                selections = listOf(ScoreSelection("G_057", 1)),
            ),
        )

        assertThat(response.total).isEqualTo(17.90)
        assertThat(response.perStat).extracting("stat", "value")
            .containsExactly(tuple("구위", 10.00), tuple("변화", 10.00))
    }

    @Test
    fun `calculate allows normal pool skill for special card type`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val gold = scoreSkill(
            "G_001", "NORMAL", "BATTER", "배팅머신",
            effect("파워", "ALWAYS", "10/10/10/10/10/10/10/10/10"),
        )
        `when`(repository.findBySkillKey("G_001")).thenReturn(gold)
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val response = service.calculate(
            ScoreRequest(
                cardType = "WBC",
                position = "BATTER",
                battingOrder = 1,
                selections = listOf(ScoreSelection("G_001", 5)),
            ),
        )

        assertThat(response.total).isEqualTo(10.00)
    }

    @Test
    fun `calculate uses requested batting order as condition gate`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val skill = scoreSkill(
            "G_025", "NORMAL", "BATTER", "테이블 세터",
            effect("POWER", "타순4_5", "10/10/10/10/10/10/10/10/10"),
        )
        `when`(repository.findBySkillKey("G_025")).thenReturn(skill)
        val service = ScoreService(repository, ScoreCalculator(), mapOf("POWER" to 1.0))

        val cleanupOrder = service.calculate(
            ScoreRequest(
                cardType = "NORMAL",
                position = "BATTER",
                battingOrder = 4,
                selections = listOf(ScoreSelection("G_025", 1)),
            ),
        )
        val leadoffOrder = service.calculate(
            ScoreRequest(
                cardType = "NORMAL",
                position = "BATTER",
                battingOrder = 1,
                selections = listOf(ScoreSelection("G_025", 1)),
            ),
        )

        assertThat(cleanupOrder.total).isEqualTo(10.00)
        assertThat(leadoffOrder.total).isEqualTo(0.00)
    }

    @Test
    fun `calculate derives opponent grade advantage from own card type`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val skill = scoreSkill(
            "G_015", "NORMAL", "BATTER", "Challenge",
            effect("POWER", "상대등급우세", "10/10/10/10/10/10/10/10/10"),
        )
        `when`(repository.findBySkillKey("G_015")).thenReturn(skill)
        val service = ScoreService(repository, ScoreCalculator(), mapOf("POWER" to 1.0))

        fun totalFor(cardType: String) = service.calculate(
            ScoreRequest(
                cardType = cardType,
                position = "BATTER",
                battingOrder = 1,
                selections = listOf(ScoreSelection("G_015", 1)),
            ),
        )

        val signature = totalFor("SIGNATURE")
        val moment = totalFor("MOMENT")
        val hof = totalFor("HOF")

        assertThat(signature.total).isEqualTo(2.00)
        assertThat(signature.perSkill[0].breakdown[0].conditionProbability).isEqualTo(0.20)
        assertThat(moment.total).isEqualTo(4.00)
        assertThat(moment.perSkill[0].breakdown[0].conditionProbability).isEqualTo(0.40)
        assertThat(hof.total).isEqualTo(0.00)
        assertThat(hof.perSkill[0].breakdown[0].conditionProbability).isEqualTo(0.00)
    }

    @Test
    fun `calculate rejects invalid batting order`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val skill = scoreSkill(
            "G_025", "NORMAL", "BATTER", "테이블 세터",
            effect("POWER", "타순4_5", "10/10/10/10/10/10/10/10/10"),
        )
        `when`(repository.findBySkillKey("G_025")).thenReturn(skill)
        val service = ScoreService(repository, ScoreCalculator(), mapOf("POWER" to 1.0))

        val request = ScoreRequest(
            cardType = "NORMAL",
            position = "BATTER",
            battingOrder = 10,
            selections = listOf(ScoreSelection("G_025", 1)),
        )

        assertThatThrownBy { service.calculate(request) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }
    }

    @Test
    fun `calculate returns warning and zero contribution for unknown condition`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val skill = scoreSkill(
            "X_001", "NORMAL", "BATTER", "Unknown Condition Skill",
            effect("POWER", "미정의조건", "10/10/10/10/10/10/10/10/10"),
        )
        `when`(repository.findBySkillKey("X_001")).thenReturn(skill)
        val service = ScoreService(repository, ScoreCalculator(), mapOf("POWER" to 1.0))

        val response = service.calculate(
            ScoreRequest(
                cardType = "NORMAL",
                position = "BATTER",
                battingOrder = 1,
                selections = listOf(ScoreSelection("X_001", 1)),
            ),
        )

        assertThat(response.total).isEqualTo(0.00)
        assertThat(response.warnings).containsExactly("X_001 contains undefined condition: 미정의조건")
        assertThat(response.perSkill[0].warnings).containsExactly("Undefined condition: 미정의조건")
        assertThat(response.perSkill[0].breakdown[0].conditionProbability).isEqualTo(0.0)
        assertThat(response.perSkill[0].breakdown[0].subtotal).isEqualTo(0.0)
    }

    @Test
    fun `calculate rejects duplicate skill selection`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val service = ScoreService(repository, ScoreCalculator(), emptyMap())

        val request = ScoreRequest(
            cardType = "NORMAL",
            position = "BATTER",
            battingOrder = 1,
            selections = listOf(ScoreSelection("S_001", 1), ScoreSelection("S_001", 2)),
        )

        assertThatThrownBy { service.calculate(request) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }
    }

    @Test
    fun `calculate rejects missing batting order for batter`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val service = ScoreService(repository, ScoreCalculator(), emptyMap())

        val request = ScoreRequest(
            cardType = "NORMAL",
            position = "BATTER",
            selections = listOf(ScoreSelection("S_001", 1)),
        )

        assertThatThrownBy { service.calculate(request) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }
    }

    @Test
    fun `calculate rejects missing or invalid pitcher slot for starting pitcher`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val service = ScoreService(repository, ScoreCalculator(), emptyMap())

        val requestMissing = ScoreRequest(
            cardType = "NORMAL",
            position = "SP",
            selections = listOf(ScoreSelection("S_001", 1)),
        )

        assertThatThrownBy { service.calculate(requestMissing) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }

        val requestInvalid = ScoreRequest(
            cardType = "NORMAL",
            position = "SP",
            pitcherSlot = 6,
            selections = listOf(ScoreSelection("S_001", 1)),
        )

        assertThatThrownBy { service.calculate(requestInvalid) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }
    }

    @Test
    fun `calculate rejects missing or invalid pitcher slot for relief pitcher`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val service = ScoreService(repository, ScoreCalculator(), emptyMap())

        val requestInvalid = ScoreRequest(
            cardType = "NORMAL",
            position = "RP",
            pitcherSlot = 7,
            selections = listOf(ScoreSelection("S_001", 1)),
        )

        assertThatThrownBy { service.calculate(requestInvalid) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }
    }

    @Test
    fun `calculate allows missing slot for closer pitcher`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val skill = scoreSkill("S_001", "NORMAL", "PITCHER", "마무리", effect("파워", "ALWAYS", "1"))
        `when`(repository.findBySkillKey("S_001")).thenReturn(skill)
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        val response = service.calculate(
            ScoreRequest(
                cardType = "NORMAL",
                position = "CP",
                selections = listOf(ScoreSelection("S_001", 1)),
            ),
        )

        assertThat(response.total).isEqualTo(1.00)
    }

    @Test
    fun `라이브와 시즌은 노멀 풀에서만 스킬을 고른다`() {
        // 전용 스킬이 없고 아이언·브론즈·실버·골드만 가진다. 그 티어 집합이 곧 card_type=NORMAL이다.
        val service = ScoreService(
            mock(ScoreSkillRepository::class.java), ScoreCalculator(), mapOf("파워" to 1.0),
        )
        for (cardType in listOf("LIVE", "SEASON")) {
            assertThat(service.skillPoolsFor(cardType, null))
                .`as`("%s의 스킬 풀", cardType)
                .containsExactly("NORMAL")
        }
    }

    @Test
    fun `라이브 카드는 노멀 스킬은 받고 상위 티어 스킬은 거부한다`() {
        val repository = mock(ScoreSkillRepository::class.java)
        val basic = scoreSkill("G_001", "NORMAL", "BATTER", "골드 스킬", effect("파워", "ALWAYS", "1/2/3"))
        val hof = scoreSkill("HOF_001", "HOF", "BATTER", "HOF 스킬", effect("파워", "ALWAYS", "1/2/3"))
        `when`(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(listOf(basic))
        `when`(repository.findBySkillKey("G_001")).thenReturn(basic)
        `when`(repository.findBySkillKey("HOF_001")).thenReturn(hof)
        val service = ScoreService(repository, ScoreCalculator(), mapOf("파워" to 1.0))

        // 목록 조회는 NORMAL 풀만 돌려준다.
        assertThat(service.listSkills("LIVE", null, "BATTER")).hasSize(1)

        // 채점도 통과해야 한다. 상대등급우세 표에 LIVE가 없으면 여기서 500이 났다.
        val ok = service.calculate(
            ScoreRequest(
                cardType = "LIVE", position = "BATTER", battingOrder = 3,
                selections = listOf(ScoreSelection(skillId = "G_001", level = 1)),
            ),
        )
        assertThat(ok.total).isGreaterThan(0.0)

        // HOF 스킬은 라이브 카드가 가질 수 없다.
        assertThatThrownBy {
            service.calculate(
                ScoreRequest(
                    cardType = "LIVE", position = "BATTER", battingOrder = 3,
                    selections = listOf(ScoreSelection(skillId = "HOF_001", level = 1)),
                ),
            )
        }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("does not match requested card grade")
    }

    private fun scoreSkill(
        skillKey: String,
        cardType: String,
        position: String,
        name: String,
        vararg effects: ScoreEffect,
    ): ScoreSkill {
        val skill = ScoreSkill(
            skillKey = skillKey,
            cardType = cardType,
            position = position,
            name = name,
            description = name,
            effects = effects.toMutableList(),
        )
        skill.effects.forEach { it.skill = skill }
        return skill
    }

    private fun effect(stat: String, condition: String, values: String) =
        ScoreEffect(stat = stat, condition = condition, values = values)

    private fun proportionalEffect(stat: String, condition: String, values: String, baseStat: String) =
        ScoreEffect(stat = stat, condition = condition, values = values, baseStat = baseStat)
}
