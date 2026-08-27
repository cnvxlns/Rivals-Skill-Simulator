// 스킬 뽑기 결과로 반환되는 슬롯 정보 목록을 담는 DTO
package com.example.skillsim.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollResponse {
    private List<SkillSlot> slots;
    private double totalScore;
}
