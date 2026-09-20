package com.example.skillsim.dto

import com.example.skillsim.enums.Handedness
import com.example.skillsim.enums.RelieverRole
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 덱 저장·채점 요청.
 *
 * 애노테이션으로는 모양만 본다. 26명 구성이나 타순 중복 같은 관계 규칙은
 * [com.example.skillsim.service.DeckValidator]가 검사한다.
 */
data class DeckSaveRequest(
    /** 덱 이름. 저장할 때만 쓰이고 채점 미리보기에서는 비어 있어도 된다. */
    @field:Size(max = 50)
    val name: String? = null,
    @field:Min(4)
    @field:Max(6)
    val starterCount: Int? = null,
    @field:Min(1)
    @field:Max(2)
    val closerCount: Int? = null,
    /**
     * 중계 인원. 셋 중 둘만 보내면 나머지는 서버가 계산한다.
     * 셋을 다 보내면 합이 12인지 확인한다.
     */
    @field:Min(4)
    @field:Max(7)
    val relieverCount: Int? = null,
    @field:Valid
    @field:NotEmpty
    val players: List<DeckPlayerRequest>? = null,
    /**
     * 구단의 포지션 훈련 현황. **저장 없이 채점만 할 때만 쓴다.**
     *
     * 훈련은 계정당 한 벌이라 덱에 저장하지 않는다. 저장된 덱을 채점할 때는 서버가
     * 계정에 저장된 설정을 읽고, 여기 온 값은 무시한다
     * ([com.example.skillsim.controller.PositionTrainingController]).
     */
    @field:Valid
    val positionTraining: PositionTrainingRequest? = null,
)

data class DeckPlayerRequest(
    @field:NotBlank
    val slot: String? = null,
    /** 선수 이름. 표시용이며 점수에는 영향이 없다. */
    @field:Size(max = 30)
    val playerName: String? = null,
    /** 카드 등급. 예전 단일 cardType으로 보내도 등급과 변형으로 풀린다. */
    val cardGrade: String? = null,
    /** 카드 변형. NONE(기본) / FA / WBC. */
    val cardVariant: String? = null,
    /** 예전 단일 카드 타입. [cardGrade]가 없을 때만 쓰인다. */
    val cardType: String? = null,
    /** 후보만 필요하다. 주전과 투수는 슬롯에서 유도하므로 보내도 무시한다. */
    val position: String? = null,
    @field:Valid
    @field:NotEmpty
    val skills: List<DeckSkillRequest>? = null,
    val battingOrder: Int? = null,
    /** 중계에만 허용한다. 나머지 자리에 오면 거부한다. */
    val relieverRole: RelieverRole? = null,
    val stats: Map<String, Double>? = null,
    /** [stats]를 적을 당시 서 있던 자리. 비우면 지금 자리에서 적은 것으로 본다. */
    @field:Size(max = 10)
    val statsSlot: String? = null,
    val throwHand: Handedness? = null,
    val batHand: Handedness? = null,
)

data class DeckSkillRequest(
    @field:NotBlank
    val skillId: String? = null,
    @field:NotNull
    @field:Min(1)
    @field:Max(9)
    val level: Int? = null,
)
