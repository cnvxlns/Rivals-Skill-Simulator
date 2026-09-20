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
    /**
     * 카드 고유 능력치. 육성·구단 관리를 뺀 값이라 [userStats]와 다르다.
     *
     * "기본 주루+수비 합이 155 이상인 경우"처럼 카드가 타고난 값에 임계를 거는 스킬에만
     * 쓴다. 넘기면 그 조건이 확률이 아니라 켜짐/꺼짐으로 확정되고, 비우면 표본 확률로
     * 채점한다.
     */
    val baseStats: Map<String, Double>? = null,
    /** 선수 본인의 투구 방향. 미지정 시 우완으로 간주한다. */
    val throwHand: Handedness? = null,
    /** 선수 본인의 타격 방향. 미지정 시 우타로 간주한다. */
    val batHand: Handedness? = null,
    /**
     * 포지션 훈련이 이 슬롯에 붙여 준 스킬 레벨 보너스. 최대 3개다.
     *
     * [selections]의 `level`은 **보너스가 붙기 전 기본 레벨**이다. 여기 적힌 스킬을 골랐다면
     * 그만큼 올려서 채점한다([com.example.skillsim.service.PositionTrainingRules]).
     */
    @field:Valid
    val trainingBonuses: List<SkillLevelBonus>? = null,
)
