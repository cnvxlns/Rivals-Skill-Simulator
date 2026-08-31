package com.example.skillsim.service

import com.example.skillsim.enums.Level
import com.example.skillsim.model.ScoreEffect
import com.example.skillsim.model.ScoreSkill
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SkillRulesTest {

    @Test
    fun `slot counts match card type rules`() {
        assertThat(SkillRules.slotCount("SIGNATURE")).isEqualTo(3)
        assertThat(SkillRules.slotCount("HOF")).isEqualTo(3)
        assertThat(SkillRules.slotCount("WBC")).isEqualTo(3)
        assertThat(SkillRules.slotCount("MOMENT")).isEqualTo(3)
        assertThat(SkillRules.slotCount("SUPREME_MOMENT")).isEqualTo(3)
        assertThat(SkillRules.slotCount("SIGNATURE_BLACK")).isEqualTo(4)
        assertThat(SkillRules.slotCount("WBC_SIGNATURE_BLACK")).isEqualTo(4)
        assertThat(SkillRules.slotCount("BLACK")).isEqualTo(4)
    }

    @Test
    fun `grade ladders match card type rules`() {
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
    fun `normalizeCardType rejects PRIME and keeps WBC signature black distinct`() {
        assertThatThrownBy { SkillRules.normalizeCardType("PRIME") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(SkillRules.normalizeCardType("WBC_SIGNATURE_BLACK")).isEqualTo("WBC_BLACK")
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
