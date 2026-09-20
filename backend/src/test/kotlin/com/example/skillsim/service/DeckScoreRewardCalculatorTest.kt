package com.example.skillsim.service

import com.example.skillsim.config.DeckDataLoader
import com.example.skillsim.model.DeckPlayer
import com.example.skillsim.model.DeckScoreChoice
import com.example.skillsim.model.DeckScoreCondition
import com.example.skillsim.model.DeckScoreLadder
import com.example.skillsim.model.DeckScoreReward
import com.example.skillsim.model.DeckScoreSide
import com.example.skillsim.model.DeckScoreTarget
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 덱 스코어 보상이 누구에게 얼마를 주는가. 조건 여덟 가지와 자리 그룹을 전부 짚는다. */
class DeckScoreRewardCalculatorTest {

    private fun player(
        slot: String,
        grade: String = "SIGNATURE",
        battingOrder: Int? = null,
        enhancement: Int? = null,
        year: Int? = null,
        position: String = DeckRules.positionForSlot(slot) ?: "C",
    ) = DeckPlayer(
        slot = slot,
        position = position,
        cardGrade = grade,
        skills = emptyList(),
        battingOrder = battingOrder,
        pitcherSlot = DeckRules.pitcherSlotNumber(slot),
        enhancementLevel = enhancement,
        year = year,
    )

    private fun reward(
        target: String,
        condition: DeckScoreCondition = DeckScoreCondition.ALWAYS,
        stat: String = "파워",
        amount: Int = 1,
    ) = DeckScoreReward(
        ladder = DeckScoreLadder.TEAM,
        threshold = 200,
        side = DeckScoreSide.LEFT,
        target = DeckScoreTarget.parse(target),
        stat = stat,
        amount = amount,
        condition = condition,
    )

    private val choice = DeckScoreChoice(DeckScoreLadder.TEAM, 200, DeckScoreSide.LEFT)

    private fun bonus(reward: DeckScoreReward, player: DeckPlayer, choice: DeckScoreChoice = this.choice) =
        DeckScoreRewardCalculator(listOf(reward)).bonusFor(player, listOf(choice))

    @Test
    fun `고르지 않은 칸은 아무것도 주지 않는다`() {
        val calculator = DeckScoreRewardCalculator(listOf(reward("BATTER_ALL")))

        assertThat(calculator.bonusFor(player("C", battingOrder = 1), emptyList())).isEmpty()
    }

    @Test
    fun `타자 전체와 투수 전체를 가른다`() {
        val batterReward = reward("BATTER_ALL")

        assertThat(bonus(batterReward, player("C", battingOrder = 1))).containsEntry("파워", 1.0)
        assertThat(bonus(batterReward, player("BENCH1", position = "C"))).containsEntry("파워", 1.0)
        assertThat(bonus(batterReward, player("SP1"))).isEmpty()
    }

    @Test
    fun `역할 그룹은 워크북에 없는 자리까지 덮는다`() {
        // 워크북은 SP1~5·RP1~3·CP1까지만 안다. 우리 덱은 선발 6·중계 7·마무리 2까지 간다.
        val starters = reward("SP")
        assertThat(bonus(starters, player("SP6"))).containsEntry("파워", 1.0)

        val bullpen = reward("RP_CP")
        assertThat(bonus(bullpen, player("RP7"))).containsEntry("파워", 1.0)
        assertThat(bonus(bullpen, player("CP2"))).containsEntry("파워", 1.0)
        assertThat(bonus(bullpen, player("SP1"))).isEmpty()
    }

    @Test
    fun `포지션을 콕 집은 규칙은 그 자리만 받는다`() {
        val corners = reward("1B|3B")

        assertThat(bonus(corners, player("1B", battingOrder = 1))).containsEntry("파워", 1.0)
        assertThat(bonus(corners, player("3B", battingOrder = 2))).containsEntry("파워", 1.0)
        assertThat(bonus(corners, player("2B", battingOrder = 3))).isEmpty()
    }

    @Test
    fun `후보는 선언한 포지션으로 판정한다`() {
        val corners = reward("1B|3B")

        assertThat(bonus(corners, player("BENCH1", position = "1B"))).containsEntry("파워", 1.0)
        assertThat(bonus(corners, player("BENCH2", position = "SS"))).isEmpty()
    }

    @Test
    fun `라이브와 시즌 카드를 가르는 조건`() {
        val live = reward("BATTER_ALL", DeckScoreCondition.CARD_LIVE_SEASON)
        val other = reward("BATTER_ALL", DeckScoreCondition.CARD_NOT_LIVE_SEASON)

        assertThat(bonus(live, player("C", grade = "LIVE", battingOrder = 1))).isNotEmpty
        assertThat(bonus(live, player("C", grade = "SEASON", battingOrder = 1))).isNotEmpty
        assertThat(bonus(live, player("C", grade = "HOF", battingOrder = 1))).isEmpty()
        assertThat(bonus(other, player("C", grade = "HOF", battingOrder = 1))).isNotEmpty
        assertThat(bonus(other, player("C", grade = "LIVE", battingOrder = 1))).isEmpty()
    }

    @Test
    fun `타순 구간의 경계`() {
        val top = reward("BATTER_ALL", DeckScoreCondition.ORDER_1_2)
        val middle = reward("BATTER_ALL", DeckScoreCondition.ORDER_3_5)
        val bottom = reward("BATTER_ALL", DeckScoreCondition.ORDER_6_9)

        assertThat(bonus(top, player("C", battingOrder = 2))).isNotEmpty
        assertThat(bonus(top, player("C", battingOrder = 3))).isEmpty()
        assertThat(bonus(middle, player("C", battingOrder = 3))).isNotEmpty
        assertThat(bonus(middle, player("C", battingOrder = 5))).isNotEmpty
        assertThat(bonus(middle, player("C", battingOrder = 6))).isEmpty()
        assertThat(bonus(bottom, player("C", battingOrder = 6))).isNotEmpty
        // 타순이 없는 후보는 타순 조건을 만족하지 않는다.
        assertThat(bonus(bottom, player("BENCH1", position = "C"))).isEmpty()
    }

    @Test
    fun `강화 조건은 10부터 걸린다`() {
        // 워크북 수식은 `$AF11>9`다.
        val rule = reward("BATTER_ALL", DeckScoreCondition.ENHANCE_GTE_10)

        assertThat(bonus(rule, player("C", battingOrder = 1, enhancement = 9))).isEmpty()
        assertThat(bonus(rule, player("C", battingOrder = 1, enhancement = 10))).isNotEmpty
        assertThat(bonus(rule, player("C", battingOrder = 1, enhancement = null))).isEmpty()
    }

    @Test
    fun `연대 조건은 고른 해부터 아홉 해까지다`() {
        // 워크북 수식은 `연도 - 기준 < 10 && > -1`이다.
        val rule = reward("BATTER_ALL", DeckScoreCondition.DECADE)
        val decade = DeckScoreChoice(DeckScoreLadder.TEAM, 200, DeckScoreSide.LEFT, decadeYear = 2010)

        assertThat(bonus(rule, player("C", battingOrder = 1, year = 2009), decade)).isEmpty()
        assertThat(bonus(rule, player("C", battingOrder = 1, year = 2010), decade)).isNotEmpty
        assertThat(bonus(rule, player("C", battingOrder = 1, year = 2019), decade)).isNotEmpty
        assertThat(bonus(rule, player("C", battingOrder = 1, year = 2020), decade)).isEmpty()
        // 연대를 고르지 않았거나 연도를 적지 않았으면 걸리지 않는다.
        assertThat(bonus(rule, player("C", battingOrder = 1, year = 2010))).isEmpty()
        assertThat(bonus(rule, player("C", battingOrder = 1), decade)).isEmpty()
    }

    @Test
    fun `여러 칸을 고르면 같은 스탯이 쌓인다`() {
        val calculator = DeckScoreRewardCalculator(
            listOf(
                reward("BATTER_ALL", amount = 3),
                reward("BATTER_ALL", stat = "정확", amount = 2),
            ),
        )

        assertThat(calculator.bonusFor(player("C", battingOrder = 1), listOf(choice)))
            .containsExactlyInAnyOrderEntriesOf(mapOf("파워" to 3.0, "정확" to 2.0))
    }

    @Test
    fun `커밋된 보상표의 조건과 대상이 전부 알려진 값이다`() {
        val rewards = DeckDataLoader().apply { run() }.deckScoreRewards

        assertThat(rewards).isNotEmpty
        assertThat(rewards).allSatisfy { reward ->
            assertThat(reward.threshold)
                .isIn(DeckScoreRules.thresholdsOf(reward.ladder))
            if (reward.condition == DeckScoreCondition.DECADE) {
                assertThat(DeckScoreRules.isDecadeTier(reward.ladder, reward.threshold)).isTrue()
            }
        }
        // 연대 칸 셋이 모두 규칙을 갖고 있어야 한다. 하나라도 빠지면 워크북 판단이 뒤집힌 것이다.
        val decadeTiers = rewards
            .filter { it.condition == DeckScoreCondition.DECADE }
            .map { it.threshold }
            .toSet()
        assertThat(decadeTiers).isEqualTo(DeckScoreRules.DECADE_TIERS)
    }
}
