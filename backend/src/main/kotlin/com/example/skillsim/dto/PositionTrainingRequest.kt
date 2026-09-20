package com.example.skillsim.dto

import jakarta.validation.Valid

/**
 * 포지션 훈련 저장 요청.
 *
 * 응답([com.example.skillsim.model.PositionTraining])과 같은 모양이라 받은 것을 그대로
 * 돌려보낼 수 있다. 모양만 애노테이션으로 보고, 자리 이름·스탯 이름·보너스 규칙은
 * [com.example.skillsim.service.PositionTrainingValidator]가 본다.
 *
 * @param slots 자리 이름에서 그 자리의 훈련 결과로. 보내지 않은 자리는 훈련이 없는 것으로 본다.
 */
data class PositionTrainingRequest(
    @field:Valid
    val slots: Map<String, SlotTrainingRequest>? = null,
)

/**
 * @param stats 포훈으로 오른 능력치. 레벨이 아니라 게임 화면에서 읽은 증가치 그대로다.
 * @param skills 이 자리에 붙은 스킬 레벨 보너스. 최대 세 개다.
 */
data class SlotTrainingRequest(
    val stats: Map<String, Double>? = null,
    @field:Valid
    val skills: List<SkillLevelBonus>? = null,
)
