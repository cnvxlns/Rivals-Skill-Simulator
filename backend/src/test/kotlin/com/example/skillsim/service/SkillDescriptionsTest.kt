package com.example.skillsim.service

import com.example.skillsim.model.ScoreEffect
import com.example.skillsim.model.ScoreSkill
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SkillDescriptionsTest {

    @Test
    fun `맨 x는 해당 등급의 효과 값으로 바뀐다`() {
        // S_001 좌투선호. 사다리 1..9의 5번째가 S다.
        val skill = skill(
            "좌투수 상대할 때 파워, 정확 능력치가 x 증가합니다",
            effect("파워", "1/2/3/4/5/6/7/8/9"),
            effect("정확", "1/2/3/4/5/6/7/8/9"),
        )

        assertThat(SkillDescriptions.resolve(skill, 5))
            .isEqualTo("좌투수 상대할 때 파워, 정확 능력치가 5 증가합니다")
    }

    @Test
    fun `x 더하기 1은 표현 전체가 효과 값으로 바뀐다`() {
        // S_003 타격집중. 사다리가 2..10이라 5번째는 6이다.
        // "x"만 6으로 바꾸면 "6+1"이 되어 실제 채점값(6)과 어긋난다. 산술은 사다리에 이미 반영돼 있다.
        val skill = skill(
            "주자가 2명 이상일 때 파워 능력치가 x+1 증가합니다",
            effect("파워", "2/3/4/5/6/7/8/9/10"),
        )

        assertThat(SkillDescriptions.resolve(skill, 5))
            .isEqualTo("주자가 2명 이상일 때 파워 능력치가 6 증가합니다")
    }

    @Test
    fun `x와 y는 각각 첫째 둘째 효과에 대응한다`() {
        // B_006 평정심. 두 효과의 사다리가 서로 달라서 글자마다 값이 달라야 한다.
        val skill = skill(
            "구위 능력치가 x, 제구 능력치가 y 증가합니다",
            effect("구위", "1/2/3/4/5/6/7/8/9"),
            effect("제구", "1/1/2/2/3/3/4/5/6"),
        )

        assertThat(SkillDescriptions.resolve(skill, 5))
            .isEqualTo("구위 능력치가 5, 제구 능력치가 3 증가합니다")
    }

    @Test
    fun `z는 셋째 효과에 대응한다`() {
        val skill = skill(
            "파워 x, 정확 y, 선구 z 증가합니다",
            effect("파워", "1/2/3"),
            effect("정확", "4/5/6"),
            effect("선구", "7/8/9"),
        )

        assertThat(SkillDescriptions.resolve(skill, 2)).isEqualTo("파워 2, 정확 5, 선구 8 증가합니다")
    }

    @Test
    fun `곱셈 표현도 표현 전체가 바뀐다`() {
        val skill = skill("체크스윙 확률이 3*x% 증가합니다", effect("파워", "3/6/9/12/15"))

        assertThat(SkillDescriptions.resolve(skill, 5)).isEqualTo("체크스윙 확률이 15% 증가합니다")
    }

    @Test
    fun `대응하는 효과가 없으면 원문을 그대로 둔다`() {
        // S_005 방망이처럼 능력치 효과 행이 아예 없는 스킬이 있다.
        val skill = skill("체크스윙 확률이 3*x% 증가합니다")

        assertThat(SkillDescriptions.resolve(skill, 5)).isEqualTo("체크스윙 확률이 3*x% 증가합니다")
    }

    @Test
    fun `글자가 효과 수보다 많으면 남는 글자는 그대로 둔다`() {
        val skill = skill("파워 x, 정확 y 증가합니다", effect("파워", "1/2/3"))

        assertThat(SkillDescriptions.resolve(skill, 2)).isEqualTo("파워 2, 정확 y 증가합니다")
    }

    @Test
    fun `레벨이 사다리보다 높으면 마지막 값을 쓴다`() {
        val skill = skill("파워 능력치가 x 증가합니다", effect("파워", "1/2/3"))

        assertThat(SkillDescriptions.resolve(skill, 9)).isEqualTo("파워 능력치가 3 증가합니다")
    }

    @Test
    fun `소수 값은 사다리에 적힌 그대로 쓴다`() {
        val skill = skill("스페셜 덱 스코어의 x만큼 증가합니다", effect("파워", "0.01"))

        assertThat(SkillDescriptions.resolve(skill, 5)).isEqualTo("스페셜 덱 스코어의 0.01만큼 증가합니다")
    }

    @Test
    fun `한글 조사가 붙어도 치환한다`() {
        // G_050 등 원문에 띄어쓰기가 빠진 표기가 4건 있다. 한글을 경계로 막으면 이것만 조용히 빠진다.
        val skill = skill("상대 투수의 구위 능력치가 x증가합니다", effect("구위", "1/2/3/4/5"))

        assertThat(SkillDescriptions.resolve(skill, 5)).isEqualTo("상대 투수의 구위 능력치가 5증가합니다")
    }

    @Test
    fun `단어 속의 x는 건드리지 않는다`() {
        val skill = skill("MAX 수치와 xy 표기는 그대로 둔다", effect("파워", "1/2/3"))

        assertThat(SkillDescriptions.resolve(skill, 2)).isEqualTo("MAX 수치와 xy 표기는 그대로 둔다")
    }

    @Test
    fun `플레이스홀더가 없으면 원문 그대로다`() {
        val skill = skill("타격 시 삼진을 당하지 않습니다", effect("파워", "1/2/3"))

        assertThat(SkillDescriptions.resolve(skill, 2)).isEqualTo("타격 시 삼진을 당하지 않습니다")
    }

    @Test
    fun `설명이 비어도 안전하다`() {
        // description은 코틀린에서 non-null이라 빈 문자열만 남는다.
        assertThat(SkillDescriptions.resolve(skill("", effect("파워", "1/2/3")), 2)).isEmpty()
        assertThat(SkillDescriptions.resolve(null, 2)).isNull()
    }

    private fun skill(description: String, vararg effects: ScoreEffect): ScoreSkill {
        val skill = ScoreSkill(
            skillKey = "T_001",
            cardType = "NORMAL",
            position = "BATTER",
            name = "테스트",
            description = description,
            effects = effects.toMutableList(),
        )
        skill.effects.forEach { it.skill = skill }
        return skill
    }

    private fun effect(stat: String, values: String) =
        ScoreEffect(stat = stat, condition = "ALWAYS", values = values)
}
