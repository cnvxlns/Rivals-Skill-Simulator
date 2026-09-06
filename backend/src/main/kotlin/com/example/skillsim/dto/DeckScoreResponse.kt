package com.example.skillsim.dto

import com.example.skillsim.enums.RelieverRole

/**
 * 덱 채점 결과.
 *
 * @param total 26명 점수의 단순 합.
 * @param parts 파트별 소계. 넷을 더하면 [total]과 같다.
 * @param players 선수별 점수. 기여도 내림차순이다.
 */
data class DeckScoreResponse(
    val total: Double,
    val parts: List<PartScore>,
    val players: List<PlayerScore>,
    val warnings: List<String>,
) {
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
