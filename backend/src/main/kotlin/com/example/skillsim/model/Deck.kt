package com.example.skillsim.model

import java.time.Instant

/**
 * 저장된 덱 한 벌.
 *
 * @param totalScore 목록 화면용 캐시다. 채점 기준(스탯 가중치·조건 확률)이 바뀌면 낡을 수
 *   있으므로 상세 조회는 항상 다시 계산한다. 순위나 비교의 근거로 쓰지 않는다.
 */
data class Deck(
    val id: Long,
    val userId: Long,
    val name: String,
    val roster: DeckRoster,
    val totalScore: Double?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        /**
         * jsonb에 담기는 [DeckRoster]의 스키마 버전.
         *
         * 로스터 모양을 바꿀 때 올린다. 필드를 더하는 것은 기본값을 주면 하위 호환이지만,
         * 이름을 바꾸거나 없애면 기존 행을 읽을 수 없어 이 값으로 분기해야 한다.
         */
        const val BODY_VERSION = 2

        /** 한 사용자가 만들 수 있는 덱 수. 공개 서버의 무한 생성을 막는다. */
        const val MAX_PER_USER = 20
    }
}

/** 목록 화면에 쓰는 요약. 로스터 본문을 읽지 않는다. */
data class DeckSummary(
    val id: Long,
    val name: String,
    val totalScore: Double?,
    val updatedAt: Instant,
)
