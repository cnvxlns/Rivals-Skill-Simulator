package com.example.skillsim.dto

import com.example.skillsim.enums.Handedness
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class ScoreRequest(
    @field:NotBlank
    val cardType: String? = null,
    @field:NotBlank
    val position: String? = null,
    @field:Valid
    @field:NotEmpty
    val selections: List<ScoreSelection>? = null,
    val battingOrder: Int? = null,
    val pitcherSlot: Int? = null,
    val userStats: Map<String, Double>? = null,
    /** 선수 본인의 투구 방향. 미지정 시 우완으로 간주한다. */
    val throwHand: Handedness? = null,
    /** 선수 본인의 타격 방향. 미지정 시 우타로 간주한다. */
    val batHand: Handedness? = null,
)
