package com.example.skillsim.service

/**
 * 능력치 점수 — 최종 능력치를 스탯 가중치로 더한 값.
 *
 * 워크북의 `능력치점수`(J열)와 같다.
 *
 *     타자: (파워 + 팀버프) × 1.10 + (정확 + 팀버프) × 0.90 + (선구 + 팀버프) × 0.40
 *     투수: (변화 + 팀버프) × 1.15 + (구위 + 팀버프) × 1.20
 *
 * 가중치는 `stat_weights.csv`에서 읽는다. 워크북과 우리 CSV의 값이 이미 같아서(1.10 / 0.90 /
 * 0.40 / 1.15 / 1.20) 여기에 숫자를 박아 두면 두 벌 관리가 된다.
 *
 * 워크북은 팀 설정을 드롭다운으로 받아 이 자리에서 더한다. 우리는 덱의 스킬에서 유도한
 * 값([TeamBuffResolver])을 같은 자리에 더한다.
 */
internal class StatScoreCalculator(private val statWeights: () -> Map<String, Double>) {

    data class Entry(
        val stat: String,
        /** 팀 버프를 뺀 최종 능력치. */
        val value: Double,
        val teamBuff: Double,
        val weight: Double,
        val contribution: Double,
    )

    data class Result(val total: Double, val entries: List<Entry>)

    fun score(finalStats: Map<String, Double>, teamBuff: Map<String, Double>, isPitcher: Boolean): Result {
        val weights = statWeights()
        val names = if (isPitcher) StatResolver.PITCHER_STATS else StatResolver.BATTER_STATS
        val entries = names.map { stat ->
            val value = finalStats[stat] ?: 0.0
            val buff = teamBuff[stat] ?: 0.0
            val weight = weights[stat] ?: 0.0
            Entry(stat, value, buff, weight, (value + buff) * weight)
        }
        return Result(entries.sumOf { it.contribution }, entries)
    }
}
