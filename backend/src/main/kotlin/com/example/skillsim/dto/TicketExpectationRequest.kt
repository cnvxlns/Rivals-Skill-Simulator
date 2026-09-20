package com.example.skillsim.dto

import com.example.skillsim.enums.Handedness
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

/**
 * 스킬 변경권 기댓값 요청.
 *
 * [ScoreRequest]와 같은 조건을 받고 잠금·레벨 보호만 더한다. 계산기가 이미 들고 있는 값
 * 그대로라 화면에서 새로 물어볼 것이 거의 없다.
 *
 * @param selections 지금 끼워 둔 스킬. 비어 있는 칸은 빼고 보내면 그만큼 0점으로 본다.
 * @param lockSlotOne 첫 슬롯 잠금. 등급이 허용하지 않으면 무시하고 응답의
 *   `slotOneLockable`이 false로 내려간다.
 *
 * 스킬레벨보호권은 받지 않는다. 쓰지 않고 돌리는 사람이 없다시피 해서 늘 켠 것으로 본다
 * ([TicketExpectationService]).
 */
data class TicketExpectationRequest(
    @field:NotBlank
    val cardGrade: String? = null,
    val cardVariant: String? = null,
    @field:NotBlank
    val position: String? = null,
    @field:NotEmpty
    val selections: List<ScoreSelection>? = null,
    val battingOrder: Int? = null,
    val pitcherSlot: Int? = null,
    val userStats: Map<String, Double>? = null,
    val throwHand: Handedness? = null,
    val batHand: Handedness? = null,
    val lockSlotOne: Boolean = false,
    /**
     * 포지션 훈련이 이 슬롯에 붙여 준 스킬 레벨 보너스.
     *
     * 보너스는 선수가 아니라 슬롯에 붙으므로 **새로 뽑힌 스킬에도** 적용된다. 그래서
     * 지금 점수와 뽑은 점수 양쪽에 같은 목록을 건다.
     */
    @field:Valid
    val trainingBonuses: List<SkillLevelBonus>? = null,
)
