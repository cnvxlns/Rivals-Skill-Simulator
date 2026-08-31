package com.example.skillsim.service

import com.example.skillsim.config.ScoreDataLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MethodologyServiceTest {

    @Test
    fun `methodology response exposes expected constants`() {
        val loader = mock(ScoreDataLoader::class.java)
        val weights = mapOf("파워" to 1.2, "정확" to 1.0)
        `when`(loader.statWeights).thenReturn(weights)

        val response = MethodologyService(loader).getMethodology()

        // 1. 공식이 채워져 있다
        assertThat(response.formula.perSkillFormula.descriptionKey).isEqualTo("formula_per_skill")
        assertThat(response.formula.totalFormula.descriptionKey).isEqualTo("formula_total")

        // 2. 스탯 가중치가 로더 값을 그대로 싣는다
        assertThat(response.statWeights).isEqualTo(weights)

        // 3. 정적 확률(홈=0.3, 원정=0.7)
        val staticProbabilities = response.conditionProbabilities.staticProbabilities
        assertThat(staticProbabilities).isNotEmpty()
        assertThat(staticProbabilities.first { it.token == "홈" }.value).isEqualTo(0.3)
        assertThat(staticProbabilities.first { it.token == "원정" }.value).isEqualTo(0.7)

        // 4. 역할별 확률에 SP/RP/CP/BATTER가 모두 있다
        val roleProbabilities = response.conditionProbabilities.roleProbabilities
        assertThat(roleProbabilities).hasSize(4)
        assertThat(roleProbabilities.first { it.role == "SP" }.gutsProbability).isEqualTo(0.80)

        // 5. 평균 타순 개념을 없애고 1번타자를 전제로 채점한다. 타순1은 항상 발동한다.
        val battingOrderProbabilities = response.conditionProbabilities.battingOrderProbabilities
        assertThat(battingOrderProbabilities.first { it.token == "타순1" }.value).isEqualTo(1.0)

        // 6. 타석 도달 확률 세 묶음
        assertThat(response.conditionProbabilities.reachProbabilities).hasSize(3)

        // 7. 게이트 토큰
        assertThat(response.conditionProbabilities.gates).anyMatch { it.token == "포지션_SP" }
    }
}
