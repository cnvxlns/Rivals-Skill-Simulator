package com.example.skillsim.dto

import com.example.skillsim.model.DeckRoster

/** 목록 화면용. 로스터 본문을 담지 않는다. */
data class DeckSummaryResponse(
    val id: Long,
    val name: String,
    /**
     * 저장 시점에 계산해 둔 값이다. 채점 기준이 바뀌면 낡을 수 있으므로 참고용이며,
     * 정확한 값은 상세 조회나 채점 응답을 쓴다.
     */
    val totalScore: Double?,
    val updatedAt: String,
)

/** 상세 조회. 점수는 저장값이 아니라 지금 기준으로 다시 계산한 것이다. */
data class DeckDetailResponse(
    val id: Long,
    val name: String,
    val roster: DeckRoster,
    val score: DeckScoreResponse,
    val createdAt: String,
    val updatedAt: String,
)
