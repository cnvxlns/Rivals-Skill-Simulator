package com.example.skillsim.service

import com.example.skillsim.enums.Level
import com.example.skillsim.model.ScoreEffect
import com.example.skillsim.model.ScoreSkill
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SkillRulesTest {

    // 슬롯 수는 카드 등급의 축이라 CardRulesTest가 검사한다. 여기는 스킬 풀 축만 본다.

    @Test
    fun `grade ladders match skill pool rules`() {
        assertThat(SkillRules.gradeLadder("NORMAL")).containsExactly(
            Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1, Level.S2, Level.S3, Level.S4,
        )
        assertThat(SkillRules.gradeLadder("SIGNATURE")).containsExactly(
            Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1, Level.S2, Level.S3, Level.S4,
        )
        assertThat(SkillRules.gradeLadder("HOF"))
            .containsExactly(Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1)
        assertThat(SkillRules.gradeLadder("WBC")).containsExactly(Level.S, Level.S1, Level.S2)
        assertThat(SkillRules.gradeLadder("SIGNATURE_BLACK"))
            .containsExactly(Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1, Level.S2)
        assertThat(SkillRules.gradeLadder("WBC_SIGNATURE_BLACK"))
            .containsExactly(Level.S, Level.S1, Level.S2)
        assertThat(SkillRules.gradeLadder("MOMENT")).containsExactly(Level.S)
        assertThat(SkillRules.gradeLadder("SUPREME_MOMENT")).containsExactly(Level.S)
    }

    @Test
    fun `black skill max level clamps to value columns within expanded ladder`() {
        val skill = skill("BLACK_001", "BLACK")
        skill.effects += effect("5/8/11")

        assertThat(SkillRules.maxLevel(skill)).isEqualTo(3)
        assertThat(SkillRules.gradeLabels("BLACK", SkillRules.maxLevel(skill)))
            .containsExactly("D", "C", "B")
    }

    @Test
    fun `스킬 풀 이름만 받고 카드 등급 이름은 거부한다`() {
        // 축이 다르다. PRIME은 카드 등급이지 스킬 풀이 아니다.
        for (notAPool in listOf("PRIME", "IMPACT", "LIVE", "SEASON")) {
            assertThatThrownBy { SkillRules.normalizeSkillPool(notAPool) }
                .`as`(notAPool)
                .isInstanceOf(IllegalArgumentException::class.java)
        }
        // WBC 계열 별칭은 모두 WBC 풀로 모인다.
        assertThat(SkillRules.normalizeSkillPool("WBC_SIGNATURE_BLACK")).isEqualTo("WBC")
        assertThat(SkillRules.normalizeSkillPool("SIGNATURE")).isEqualTo("NORMAL")
        assertThat(SkillRules.normalizeSkillPool("SUPREME_MOMENT")).isEqualTo("MOMENT")
    }

    @Test
    fun `CSV에 있는 스킬 풀은 전부 사다리를 가진다`() {
        for (pool in SkillRules.SKILL_POOLS) {
            assertThat(SkillRules.normalizeSkillPool(pool)).`as`(pool).isEqualTo(pool)
            assertThat(SkillRules.gradeLadder(pool)).`as`(pool).isNotEmpty()
        }
    }

    @Test
    fun `matches detailed moment position exclusives including infield alias`() {
        val upTheMiddle = "C, 2B, SS, CF"
        assertThat(SkillRules.matchesPosition(upTheMiddle, "BATTER")).isTrue()
        assertThat(SkillRules.matchesPosition(upTheMiddle, "C")).isTrue()
        assertThat(SkillRules.matchesPosition(upTheMiddle, "2B")).isTrue()
        assertThat(SkillRules.matchesPosition(upTheMiddle, "SS")).isTrue()
        assertThat(SkillRules.matchesPosition(upTheMiddle, "CF")).isTrue()
        assertThat(SkillRules.matchesPosition(upTheMiddle, "1B")).isFalse()
        assertThat(SkillRules.matchesPosition(upTheMiddle, "LF")).isFalse()

        val allAround = "C, IF, OF"
        assertThat(SkillRules.matchesPosition(allAround, "BATTER")).isTrue()
        assertThat(SkillRules.matchesPosition(allAround, "IF")).isTrue()
        assertThat(SkillRules.matchesPosition(allAround, "1B")).isTrue()
        assertThat(SkillRules.matchesPosition(allAround, "2B")).isTrue()
        assertThat(SkillRules.matchesPosition(allAround, "3B")).isTrue()
        assertThat(SkillRules.matchesPosition(allAround, "SS")).isTrue()
        assertThat(SkillRules.matchesPosition(allAround, "LF")).isTrue()
        assertThat(SkillRules.matchesPosition(allAround, "CF")).isTrue()
        assertThat(SkillRules.matchesPosition(allAround, "RF")).isTrue()
        assertThat(SkillRules.matchesPosition(allAround, "DH")).isFalse()

        // WBC_006(아웃필더)의 position이 'LF, CR, RF'로 잘못 적혀 있었다. CR은 포지션이
        // 아니라서 CF 선수만 이 스킬을 못 받았는데, 목록에서 빠질 뿐 오류가 나지 않아
        // 드러나지 않았다. 세 자리가 모두 걸리는지 못 박는다.
        val outfield = "LF, CF, RF"
        assertThat(SkillRules.matchesPosition(outfield, "LF")).isTrue()
        assertThat(SkillRules.matchesPosition(outfield, "CF")).isTrue()
        assertThat(SkillRules.matchesPosition(outfield, "RF")).isTrue()
        assertThat(SkillRules.matchesPosition(outfield, "1B")).isFalse()
    }

    @Test
    fun `detailed pitcher exclusives still match their broad position`() {
        assertThat(SkillRules.matchesPosition("SP", "PITCHER")).isTrue()
        assertThat(SkillRules.matchesPosition("RP", "PITCHER")).isTrue()
        assertThat(SkillRules.matchesPosition("CP", "PITCHER")).isTrue()
        assertThat(SkillRules.matchesPosition("CP", "RP")).isFalse()
    }

    @Test
    fun `블랙 스킬은 D부터 S2까지 7단계 라벨을 가진다`() {
        // 영상 복구 전에는 수치가 3개뿐이라 라벨이 D/C/B로 잘렸고,
        // levelIndex(S)=5가 3으로 클램프되어 S를 고르면 S2 값이 적용됐다.
        val black = ScoreSkill(
            skillKey = "BLACK_001",
            cardType = "BLACK",
            position = "BATTER",
            name = "퓨어 히터",
            description = "퓨어 히터",
            effects = mutableListOf(effect("1/2/3/4/5/8/11")),
        )

        assertThat(SkillRules.maxLevel(black)).isEqualTo(7)
        assertThat(SkillRules.gradeLabels("BLACK", SkillRules.maxLevel(black)))
            .containsExactly("D", "C", "B", "A", "S", "S1", "S2")
        assertThat(SkillRules.levelIndex(Level.S, "BLACK")).isEqualTo(5)
    }

    // 카드 등급의 슬롯 수·스킬 풀·상대등급우세는 CardRulesTest가 검사한다.
    // 여기에 있던 "모든 카드 타입" 가드는 두 축을 하나로 보던 시절의 것이라 옮겼다.

    private fun skill(skillKey: String, cardType: String) = ScoreSkill(
        skillKey = skillKey,
        cardType = cardType,
        position = "BATTER",
        name = skillKey,
        description = skillKey,
    )

    private fun effect(values: String) = ScoreEffect(
        stat = "POWER",
        condition = "ALWAYS",
        values = values,
    )
}
