package com.example.skillsim.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreResponse {
    private double total;
    private List<SkillScore> perSkill;
    private List<StatScore> perStat;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillScore {
        private String skillId;
        private String name;
        private double score;
        private List<StatScore> perStat;
        private List<EffectBreakdown> breakdown;
        private List<String> warnings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatScore {
        private String stat;
        private double value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EffectBreakdown {
        private String stat;
        private String condition;
        private double weight;
        private double value;
        private double conditionProbability;
        private double subtotal;
        private String baseStat;
        private Double baseValue;
        private double rawValue;
    }
}
