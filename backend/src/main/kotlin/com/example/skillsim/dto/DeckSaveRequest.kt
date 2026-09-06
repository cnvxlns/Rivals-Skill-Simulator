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
    @field:NotNull
    @field:Min(4)
    @field:Max(6)
    val starterCount: Int? = null,
    @field:NotNull
    @field:Min(1)
    @field:Max(2)
    val closerCount: Int? = null,
    @field:Valid
    @field:NotEmpty
    val players: List<DeckPlayerRequest>? = null,
)

data class DeckPlayerRequest(
    @field:NotBlank
    val slot: String? = null,
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
