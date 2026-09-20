package com.example.skillsim.service

import com.example.skillsim.model.DeckPlayer
import com.example.skillsim.model.DeckScoreChoice
import com.example.skillsim.model.DeckScoreCondition
import com.example.skillsim.model.DeckScoreLadder
import com.example.skillsim.model.DeckScoreReward
import com.example.skillsim.model.DeckScoreTarget

/**
 * 덱 스코어 보상 — 고른 칸이 선수 하나에게 얼마를 주는가.
 *
 * 게임 화면의 `팀 덱 스코어` / `스페셜 덱 스코어`다. 임계값마다 좌·우 중 하나를 고르면 그
 * 보상이 덱 전체에 걸리는데, 누가 얼마를 받는지는 자리·타순·카드 종류·강화 레벨·연도에 따라
 * 갈린다. 규칙은 `deck_score_rewards.csv`에 있고 원천은 워크북 수식이다.
 *
 * 여기 쓰는 조건은 0/1로 확정된다. [ScoreCalculator]의 조건 확률 기계를 쓰지 않는 이유가
 * 그것이다 — 저쪽은 "얼마나 자주 일어나는가"를 다루고 이쪽은 로스터를 보면 답이 나온다.
 */
internal class DeckScoreRewardCalculator(rewards: List<DeckScoreReward>) {

    private val byChoice: Map<Triple<DeckScoreLadder, Int, String>, List<DeckScoreReward>> =
        rewards.groupBy { Triple(it.ladder, it.threshold, it.side.name) }

    /** 고른 칸 전체가 이 선수에게 주는 능력치. 없으면 빈 map이다. */
    fun bonusFor(player: DeckPlayer, choices: List<DeckScoreChoice>): Map<String, Double> {
        if (choices.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Double>()
        for (choice in choices) {
            val rewards = byChoice[Triple(choice.ladder, choice.threshold, choice.side.name)]
                ?: continue
            for (reward in rewards) {
                if (!matchesTarget(reward.target, player)) continue
                if (!matchesCondition(reward.condition, player, choice)) continue
                out.merge(reward.stat, reward.amount.toDouble(), Double::plus)
            }
        }
        return out
    }

    private fun matchesTarget(target: DeckScoreTarget, player: DeckPlayer): Boolean {
        val group = target.group
        if (group != null) {
            val pitcher = DeckRules.isPitcher(player.slot)
            return when (group) {
                DeckScoreTarget.Group.BATTER_ALL -> !pitcher
                DeckScoreTarget.Group.PITCHER_ALL -> pitcher
                // 워크북은 SP1~5·RP1~3·CP1까지만 알지만, 역할로 옮겨 두면 SP6·RP4~7·CP2도
                // 같은 보상을 받는다. 워크북에서 같은 역할의 자리들은 규칙이 완전히 같았다.
                DeckScoreTarget.Group.SP -> player.slot.startsWith("SP")
                DeckScoreTarget.Group.RP -> player.slot.startsWith("RP")
                DeckScoreTarget.Group.RP_CP -> player.slot.startsWith("RP") || player.slot.startsWith("CP")
                DeckScoreTarget.Group.CP -> player.slot.startsWith("CP")
            }
        }
        // 포지션을 콕 집은 규칙. 후보는 자리 이름이 포지션이 아니라 선언한 포지션을 본다.
        val position = DeckRules.positionForSlot(player.slot) ?: player.position
        return position in target.slots
    }

    private fun matchesCondition(
        condition: DeckScoreCondition,
        player: DeckPlayer,
        choice: DeckScoreChoice,
    ): Boolean = when (condition) {
        DeckScoreCondition.ALWAYS -> true
        DeckScoreCondition.CARD_LIVE_SEASON -> isLiveOrSeason(player)
        DeckScoreCondition.CARD_NOT_LIVE_SEASON -> !isLiveOrSeason(player)
        DeckScoreCondition.ORDER_1_2 -> player.battingOrder in 1..2
        DeckScoreCondition.ORDER_3_5 -> player.battingOrder in 3..5
        DeckScoreCondition.ORDER_6_9 -> player.battingOrder in 6..9
        // 워크북 수식은 `$AF11>9`다. 강화 10부터 걸린다.
        DeckScoreCondition.ENHANCE_GTE_10 -> (player.enhancementLevel ?: 0) >= ENHANCE_THRESHOLD
        DeckScoreCondition.DECADE -> matchesDecade(player.year, choice.decadeYear)
    }

    /**
     * 워크북 조건의 `라이브/시즌`. 드롭다운 목록에는 없는 값이라 엑셀 안에서는 죽은 분기였다.
     * 수식 문자 그대로 두 등급으로 읽는다.
     */
    private fun isLiveOrSeason(player: DeckPlayer): Boolean =
        CardRules.normalizeGrade(player.cardGrade) in LIVE_OR_SEASON

    /** 워크북 수식은 `연도 - 기준 < 10 && > -1`이다. 곧 `[기준, 기준+9]`다. */
    private fun matchesDecade(year: Int?, decadeYear: Int?): Boolean {
        if (year == null || decadeYear == null) return false
        return year - decadeYear in 0..DECADE_SPAN
    }

    private operator fun IntRange.contains(value: Int?): Boolean = value != null && value in this

    private companion object {
        const val ENHANCE_THRESHOLD = 10
        const val DECADE_SPAN = 9
        val LIVE_OR_SEASON = setOf("LIVE", "SEASON")
    }
}
