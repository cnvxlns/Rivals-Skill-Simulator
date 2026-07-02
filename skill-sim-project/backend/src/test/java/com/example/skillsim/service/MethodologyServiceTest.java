package com.example.skillsim.service;

import com.example.skillsim.config.ScoreDataLoader;
import com.example.skillsim.dto.MethodologyResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MethodologyServiceTest {

    @Test
    void methodologyResponseExposesExpectedConstants() {
        ScoreDataLoader loader = mock(ScoreDataLoader.class);
        Map<String, Double> weights = Map.of("파워", 1.2, "정확", 1.0);
        when(loader.getStatWeights()).thenReturn(weights);

        MethodologyService service = new MethodologyService(loader);
        MethodologyResponse response = service.getMethodology();

        // 1. Verify formulas are populated
        assertThat(response.getFormula()).isNotNull();
        assertThat(response.getFormula().getPerSkillFormula().getDescriptionKey()).isEqualTo("formula_per_skill");
        assertThat(response.getFormula().getTotalFormula().getDescriptionKey()).isEqualTo("formula_total");

        // 2. Verify stat weights are correct
        assertThat(response.getStatWeights()).isEqualTo(weights);

        // 3. Verify static probabilities (e.g. 홈=0.3, 원정=0.7)
        assertThat(response.getConditionProbabilities()).isNotNull();
        var staticProb = response.getConditionProbabilities().getStaticProbabilities();
        assertThat(staticProb).isNotEmpty();

        double homeProb = staticProb.stream()
                .filter(e -> "홈".equals(e.getToken()))
                .mapToDouble(e -> e.getValue())
                .findFirst()
                .orElse(0.0);
        double awayProb = staticProb.stream()
                .filter(e -> "원정".equals(e.getToken()))
                .mapToDouble(e -> e.getValue())
                .findFirst()
                .orElse(0.0);
        assertThat(homeProb).isEqualTo(0.3);
        assertThat(awayProb).isEqualTo(0.7);

        // 4. Verify role probabilities contain SP, RP, CP, BATTER
        var roleProb = response.getConditionProbabilities().getRoleProbabilities();
        assertThat(roleProb).hasSize(4);
        var spEntry = roleProb.stream().filter(e -> "SP".equals(e.getRole())).findFirst().orElse(null);
        assertThat(spEntry).isNotNull();
        assertThat(spEntry.getGutsProbability()).isEqualTo(0.80);

        // 5. Verify batting order probabilities
        var orderProb = response.getConditionProbabilities().getBattingOrderProbabilities();
        assertThat(orderProb).isNotEmpty();
        double order1 = orderProb.stream()
                .filter(e -> "타순1".equals(e.getToken()))
                .mapToDouble(e -> e.getValue())
                .findFirst()
                .orElse(0.0);
        assertThat(order1).isEqualTo(0.111);

        // 6. Verify reach probabilities
        var reaches = response.getConditionProbabilities().getReachProbabilities();
        assertThat(reaches).hasSize(3);

        // 7. Verify gates
        var gates = response.getConditionProbabilities().getGates();
        assertThat(gates).isNotEmpty();
        assertThat(gates.stream().anyMatch(g -> "포지션_SP".equals(g.getToken()))).isTrue();
    }
}
