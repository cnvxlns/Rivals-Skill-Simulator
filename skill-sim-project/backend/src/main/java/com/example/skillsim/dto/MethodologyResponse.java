package com.example.skillsim.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodologyResponse {
    private FormulaInfo formula;
    private Map<String, Double> statWeights;
    private ConditionProbabilitiesInfo conditionProbabilities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormulaInfo {
        private FormulaItem perSkillFormula;
        private FormulaItem totalFormula;
        private FormulaItem percentEffectRule;
        private FormulaItem roundingRule;
        private FormulaItem conditionCombinationRule;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormulaItem {
        private String displayText;
        private String descriptionKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionProbabilitiesInfo {
        private List<ConditionProbabilityEntry> staticProbabilities;
        private List<RoleProbabilityEntry> roleProbabilities;
        private List<ConditionProbabilityEntry> battingOrderProbabilities;
        private List<ReachProbabilityEntry> reachProbabilities;
        private List<GateEntry> gates;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionProbabilityEntry {
        private String token;
        private double value;
        private String descriptionKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleProbabilityEntry {
        private String role;
        private List<Double> inningWeights;
        private double gutsProbability;
        private double nineBatterDuration;
        private double maestroCumulative;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReachProbabilityEntry {
        private String orderGroup;
        private String descriptionKey;
        private List<Double> reachProbabilities;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GateEntry {
        private String token;
        private String descriptionKey;
    }
}
