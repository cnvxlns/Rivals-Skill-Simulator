// 슬롯 하나에 해당하는 스킬과 등급 정보를 담는 DTO
package com.example.skillsim.dto;

import com.example.skillsim.enums.Level;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillSlot {
    private SkillDto skill;

    @JsonProperty("grade")
    private Level level;

    private double score;
}
