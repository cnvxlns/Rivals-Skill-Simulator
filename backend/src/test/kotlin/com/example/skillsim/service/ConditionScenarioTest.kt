package com.example.skillsim.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class ConditionScenarioTest {

    private fun forced(token: String, active: Boolean, position: String = "BATTER", order: Int? = 3) =
        ScoreCalculator.conditionProbabilitiesForPosition(position, order).also {
            ConditionScenario.apply(it, token, active, SkillRules.roleForPosition(position), order)
        }

    @Test
    fun `주자있음을 못박으면 주자없음과 세부 주자 조건이 따라온다`() {
        val on = forced("주자있음", true)
        assertThat(on["주자있음"]).isEqualTo(1.0)
        assertThat(on["주자없음"]).isEqualTo(0.0)
        // 0.25 / 0.4
        assertThat(on["주자2루이상"]).isCloseTo(0.625, within(1e-9))

        val off = forced("주자있음", false)
        assertThat(off["주자없음"]).isEqualTo(1.0)
        assertThat(off["주자2루이상"]).isEqualTo(0.0)
    }

    @Test
    fun `경기 상황은 리드 동점 열세 세 칸으로 조건부확률을 다시 낸다`() {
        val lead = forced("리드", true)
        assertThat(lead["리드아님"]).isEqualTo(0.0)
        assertThat(lead["비김또는리드"]).isEqualTo(1.0)
        assertThat(lead["비김또는열세"]).isEqualTo(0.0)

        val notLead = forced("리드", false)
        assertThat(notLead["리드아님"]).isEqualTo(1.0)
        // 동점 0.26 / (동점 0.26 + 열세 0.37)
        assertThat(notLead["비김또는리드"]).isCloseTo(0.26 / 0.63, within(1e-9))
    }

    @Test
    fun `이닝 조건을 못박으면 겹치는 이닝 토큰도 맞춰진다`() {
        val late = forced("7회이후", true)
        assertThat(late["7회이후"]).isCloseTo(1.0, within(1e-9))
        assertThat(late["6회까지"]).isEqualTo(0.0)
        // 7~9회(0.09 + 0.08 + 0.08) 안에서 8회 이후(0.08 + 0.08)의 몫이다.
        assertThat(late["8회이후"]).isCloseTo(0.16 / 0.25, within(1e-9))

        val early = forced("7회이후", false)
        assertThat(early["6회까지"]).isCloseTo(1.0, within(1e-9))
        assertThat(early["7_9회"]).isEqualTo(0.0)
    }

    @Test
    fun `반대 사건 쌍은 함께 뒤집힌다`() {
        val lefty = forced("좌투상대", true)
        assertThat(lefty["우투상대"]).isEqualTo(0.0)

        val inferior = forced("OVR열세", false)
        assertThat(inferior["OVR우세"]).isEqualTo(1.0)
        assertThat(inferior["패기"]).isEqualTo(0.0)

    }

    @Test
    fun `직전 타자의 결과는 홈런이 출루의, 삼진이 아웃의 일부다`() {
        val reached = forced("직전타자출루", true)
        assertThat(reached["직전타자아웃"]).isEqualTo(0.0)
        assertThat(reached["직전타자삼진"]).isEqualTo(0.0)
        assertThat(reached["직전타자홈런"]).isCloseTo(0.030 / 0.315, within(1e-9))

        // 타자에게는 직전 타자가 늘 있으므로 출루가 아니면 아웃이다.
        val retired = forced("직전타자출루", false)
        assertThat(retired["직전타자아웃"]).isCloseTo(1.0, within(1e-9))
        assertThat(retired["직전타자삼진"]).isCloseTo(0.226 / 0.685, within(1e-9))

        val plate = forced("직전타석출루실패", true)
        assertThat(plate["직전타석홈런실패"]).isCloseTo(1.0, within(1e-9))
    }

    @Test
    fun `주자 수 기댓값은 주자가 있다고 정해져도 1에서 자르지 않는다`() {
        val on = forced("주자있음", true)
        // 0.61 / 0.4
        assertThat(on["주자당"]).isCloseTo(1.525, within(1e-9))

        val off = forced("주자있음", false)
        assertThat(off["주자당"]).isEqualTo(0.0)
    }

    @Test
    fun `묶이지 않은 조건은 자기 값만 바뀐다`() {
        val before = ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 3)
        val after = forced("풀카운트", true)

        assertThat(after["풀카운트"]).isEqualTo(1.0)
        assertThat(after.filterKeys { it != "풀카운트" }).isEqualTo(before.filterKeys { it != "풀카운트" })
    }
}
