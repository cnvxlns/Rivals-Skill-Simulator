package com.example.skillsim.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/**
 * 포지션 훈련이 슬롯에 붙여 준 스킬 레벨 보너스 한 건.
 *
 * 애노테이션으로는 모양만 본다. "그 스킬이 보너스로 나올 수 있는가", "포지션이 맞는가" 같은
 * 규칙은 [com.example.skillsim.service.PositionTrainingRules]가 본다.
 *
 * @param bonus 오르는 폭. 게임이 1 아니면 2를 준다.
 */
data class SkillLevelBonus(
    @field:NotBlank
    val skillId: String? = null,
    @field:Min(1)
    @field:Max(2)
    val bonus: Int? = null,
)
