package com.example.skillsim.dto

import com.example.skillsim.enums.Handedness
import jakarta.validation.constraints.NotBlank

/**
 * 티어별 스킬 점수표 요청.
 *
 * [ScoreRequest]에서 선택 슬롯과 카드 타입을 뺀 형태다. 카드 타입은 응답이 티어별로
 * 나뉘므로 받지 않는다.
 */
data class ScoreTableRequest(
    @field:NotBlank
    val position: String? = null,
    val battingOrder: Int? = null,
    val pitcherSlot: Int? = null,
    val throwHand: Handedness? = null,
    val batHand: Handedness? = null,
    val userStats: Map<String, Double>? = null,
    /** 카드 고유 능력치. [ScoreRequest.baseStats]와 같은 뜻이다. */
    val baseStats: Map<String, Double>? = null,
)
