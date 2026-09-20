package com.example.skillsim.dto

import com.example.skillsim.enums.RelieverRole

/**
 * 덱 채점 결과.
 *
 * @param total 파트별 평균에 가중치를 매긴 종합 점수. 선발 0.4 + 계투 0.1 + 타자 0.5이며
 *   후보는 0이다. 26명 점수의 단순 합이 아니다 — 워크북의 총점 공식으로 바꿨다.
 * @param statTotal 능력치 점수만 더한 값. @param skillTotal 스킬 점수만 더한 값.
 *   둘은 참고용이고 [total]과 더하는 관계가 아니다.
 * @param parts 파트별 소계. 각 파트의 `weighted`를 더하면 [total]과 같다.
 * @param players 선수별 점수. 기여도 내림차순이다.
 * @param collectionBuff 카드 계열을 모아서 얻은 능력치 보정. 채점에 이미 반영돼 있다.
 * @param teamBuffs 라인업 전체를 올려 주는 스킬. 누가 무엇을 주는지 그대로 돌려준다.
 */
data class DeckScoreResponse(
    val total: Double,
    val statTotal: Double,
    val skillTotal: Double,
    val parts: List<PartScore>,
    val players: List<PlayerScore>,
    val warnings: List<String>,
    val collectionBuff: CollectionBuffInfo,
    val teamBuffs: List<TeamBuffInfo> = emptyList(),
) {
    /**
     * 덱에서 유도한 팀 버프 한 건.
     *
     * @param slot 이 버프를 주는 선수의 자리. @param scope `BATTER` / `PITCHER`.
     */
    data class TeamBuffInfo(
        val slot: String,
        val skillId: String,
        val skillName: String,
        val level: Int,
        val scope: String,
        val stats: List<StatAmount>,
    ) {
        data class StatAmount(val stat: String, val amount: Double)
    }

    /** 능력치 한 칸이 어디서 왔는지. 화면이 내역을 펼칠 때 쓴다. */
    data class StatSourceInfo(
        val stat: String,
        /** `BASE` / `TRAINING` / `SPECIAL_TRAINING` / `TRANSCENDENCE` / `ENHANCEMENT` /
         *  `POSITION_TRAINING` / `DECK_SCORE` / `COLLECTION` / `DIRECT`. */
        val kind: String,
        val amount: Double,
    )

    /** 능력치 점수의 스탯별 내역. */
    data class StatScoreEntry(
        val stat: String,
        val value: Double,
        val teamBuff: Double,
        val weight: Double,
        val contribution: Double,
    )
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

    /**
     * 파트 소계.
     *
     * @param total 파트에 속한 선수들의 최종 점수 합.
     * @param average 그 평균. @param weight 종합 점수에서의 가중치(후보는 0).
     * @param weighted `average × 10 × weight`. 이 값들을 더하면 종합 점수가 된다.
     */
    data class PartScore(
        /** `LINEUP` / `BENCH` / `ROTATION` / `BULLPEN`. */
        val part: String,
        val total: Double,
        val playerCount: Int,
        val average: Double,
        val weight: Double,
        val weighted: Double,
    )

    /**
     * @param score 최종 점수. 능력치 점수와 스킬 점수의 합이다(워크북의 `J + O = P`).
     * @param finalStats 팀 버프를 뺀 최종 능력치. @param teamBuff 이 선수가 받은 팀 버프.
     * @param statSources 능력치가 어디서 왔는지. @param statEntries 능력치 점수의 스탯별 내역.
     */
    data class PlayerScore(
        val slot: String,
        val position: String,
        val playerName: String?,
        val cardType: String,
        val score: Double,
        val statScore: Double,
        val skillScore: Double,
        val battingOrder: Int?,
        val pitcherSlot: Int?,
        /** 저장·표시용이며 점수에는 반영되지 않는다. */
        val relieverRole: RelieverRole?,
        val perSkill: List<ScoreResponse.SkillScore>,
        val perStat: List<ScoreResponse.StatScore>,
        val warnings: List<String>,
        val finalStats: List<TeamBuffInfo.StatAmount> = emptyList(),
        val teamBuff: List<TeamBuffInfo.StatAmount> = emptyList(),
        val statSources: List<StatSourceInfo> = emptyList(),
        val statEntries: List<StatScoreEntry> = emptyList(),
    )
}
