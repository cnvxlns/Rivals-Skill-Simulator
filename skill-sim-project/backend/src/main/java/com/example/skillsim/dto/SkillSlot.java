package com.example.skillsim.dto;

import com.example.skillsim.enums.Grade;
import com.example.skillsim.model.Skill;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillSlot {
    private Skill skill;
    private Grade grade;
}
