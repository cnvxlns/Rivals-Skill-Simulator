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
public class ScoreSkillOption {
    private String skillId;
    private String cardType;
    private String position;
    private String name;
    private String description;
    private int maxLevel;
    private List<String> levelLabels;
}
