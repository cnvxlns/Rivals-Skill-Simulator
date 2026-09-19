package com.example.skillsim.dto

import com.example.skillsim.enums.RelieverRole

/**
 * 덱 채점 결과.
 *
 * @param total 26명 점수의 단순 합.
 * @param parts 파트별 소계. 넷을 더하면 [total]과 같다.
 * @param players 선수별 점수. 기여도 내림차순이다.
 * @param collectionBuff 카드 계열을 모아서 얻은 능력치 보정. 채점에 이미 반영돼 있다.
 */
data class DeckScoreResponse(
    val total: Double,
    val parts: List<PartScore>,
    val players: List<PlayerScore>,
    val warnings: List<String>,
    val collectionBuff: CollectionBuffInfo,
) {
    /**
     * 컬렉션 버프 요약.
     *
     * @param batterBonus 타자 14명에게 걸린 능력치 증가치. 계열별 값의 합이다.
     * @param pitcherBonus 투수 12명에게 걸린 능력치 증가치.
     */
    data class CollectionBuffInfo(
        val families: List<FamilyCount>,
        val batterBonus: Int,
        val pitcherBonus: Int,
    ) {
        data class FamilyCount(
            /** `HOF` / `SIGNATURE` / `MOMENT` / `LIVE` / `SEASON`. */
            val family: String,
            /** 가중치 합. 슈프림 모먼트를 두 장으로 세므로 실제 장수보다 클 수 있다. */
            val count: Int,
            val batterBonus: Int,
            val pitcherBonus: Int,
        )
    }

    data class PartScore(
        /** `LINEUP` / `BENCH` / `ROTATION` / `BULLPEN`. */
        val part: String,
        val total: Double,
        val playerCount: Int,
    )

    data class PlayerScore(
        val slot: String,
        val position: String,
        val playerName: String?,
        val cardType: String,
        val score: Double,
        val battingOrder: Int?,
        val pitcherSlot: Int?,
        /** 저장·표시용이며 점수에는 반영되지 않는다. */
        val relieverRole: RelieverRole?,
        val perSkill: List<ScoreResponse.SkillScore>,
        val perStat: List<ScoreResponse.StatScore>,
        val warnings: List<String>,
    )
}
