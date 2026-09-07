package com.example.skillsim.dto

import com.example.skillsim.enums.Handedness
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
 * 등급 보호는 받지 않는다. 쓰지 않고 돌리는 사람이 없다시피 해서 늘 켠 것으로 본다
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
)
