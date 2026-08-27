package com.example.skillsim.dto;

import com.example.skillsim.enums.Tier;
import com.example.skillsim.model.ScoreSkill;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDto {
    private Long id;
    private String skillId;
    private String name;
    private Tier tier;
    private String position;
    private String description;
    private String subPositions;

    public static SkillDto from(ScoreSkill skill, Tier tier) {
        if (skill == null) {
            return null;
        }

        return SkillDto.builder()
                .id(skill.getId())
                .skillId(skill.getSkillKey())
                .name(skill.getName())
                .tier(tier)
                .position(skill.getPosition())
                .description(skill.getDescription())
                .subPositions(skill.getPosition())
                .build();
    }
}
