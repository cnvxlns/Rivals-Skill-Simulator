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
    /**
     * 티어별로 채점할 등급(`gold` -> `S2`). S~S4만 받는다. 빠진 티어와 모르는 값은 S로 채점한다.
     * 스킬 사다리가 그 등급까지 없으면 자기 최대 등급으로 내려간다(HOF는 S1이 끝이다).
     */
    val grades: Map<String, String>? = null,
    /** 카드 고유 능력치. [ScoreRequest.baseStats]와 같은 뜻이다. */
    val baseStats: Map<String, Double>? = null,
    /**
     * 발동 여부를 못박을 조건 토큰(`주자있음` 등). 비우면 모든 조건을 발동 확률로 곱한
     * 기댓값으로 채점한다. 고를 수 있는 토큰은 응답의 [ScoreTableResponse.conditions]에 있다.
     */
    val condition: String? = null,
    /** [condition]을 발동으로 볼지(true) 미발동으로 볼지(false). */
    val conditionActive: Boolean = true,
)
