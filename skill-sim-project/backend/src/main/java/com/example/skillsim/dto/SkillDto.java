package com.example.skillsim.dto;

import com.example.skillsim.enums.Tier;
import com.example.skillsim.model.Skill;
import java.util.List;
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
    private String name;
    private Tier tier;
    private String position;
    private String description;
    private String subPositions;

    public static SkillDto from(Skill skill) {
        if (skill == null) {
            return null;
        }

        return SkillDto.builder()
                .id(skill.getId())
                .name(skill.getName())
                .tier(skill.getTier())
                .position(skill.getPosition())
                .description(skill.getDescription())
                .subPositions(skill.getSubPositions())
                .build();
    }
}
