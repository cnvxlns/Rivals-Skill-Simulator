package com.example.skillsim.dto

import com.example.skillsim.enums.Handedness
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class ScoreRequest(
    /** 카드 등급. season/live/impact/prime/moment/signature/signature_black/hof. */
    val cardGrade: String? = null,
    /** 카드 변형. NONE(기본) / FA / WBC. 서열에는 영향을 주지 않는다. */
    val cardVariant: String? = null,
    /**
     * 예전 단일 카드 타입. [cardGrade]가 없을 때만 쓰이며 등급과 변형으로 풀린다.
     * 저장된 덱과 기존 클라이언트를 위해 남겨 둔다.
     */
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
