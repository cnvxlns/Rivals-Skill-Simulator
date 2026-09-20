package com.example.skillsim.service

import com.example.skillsim.config.DeckDataLoader
import com.example.skillsim.model.DeckScoreLadder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/** 덱 스코어 사다리와 총점 공식의 상수. 워크북이 캐시해 둔 계산 결과로 못 박는다. */
class DeckScoreRulesTest {

    @Test
    fun `사다리는 팀 25칸 스페셜 29칸이고 오름차순이다`() {
        assertThat(DeckScoreRules.TEAM_THRESHOLDS).hasSize(25).isSorted
        assertThat(DeckScoreRules.SPECIAL_THRESHOLDS).hasSize(29).isSorted
        assertThat(DeckScoreRules.TEAM_THRESHOLDS.toSet()).hasSize(25)
        assertThat(DeckScoreRules.SPECIAL_THRESHOLDS.toSet()).hasSize(29)
    }

    @Test
    fun `보상표의 모든 임계값이 사다리 안에 있다`() {
        // 사다리는 규칙(여기), 보상은 데이터(CSV)라 두 벌이다. 서로를 검사한다.
        val rewards = DeckDataLoader().apply { run() }.deckScoreRewards

        assertThat(rewards.map { it.ladder to it.threshold }.distinct()).allSatisfy { (ladder, threshold) ->
            assertThat(threshold).isIn(DeckScoreRules.thresholdsOf(ladder))
        }
    }

    @Test
    fun `연대 칸은 스페셜 615 645 680이다`() {
        assertThat(DeckScoreRules.DECADE_TIERS).containsExactlyInAnyOrder(615, 645, 680)
        assertThat(DeckScoreRules.isDecadeTier(DeckScoreLadder.SPECIAL, 615)).isTrue()
        assertThat(DeckScoreRules.isDecadeTier(DeckScoreLadder.SPECIAL, 600)).isFalse()
        assertThat(DeckScoreRules.isDecadeTier(DeckScoreLadder.TEAM, 615)).isFalse()
        DeckScoreRules.DECADE_TIERS.forEach {
            assertThat(it).isIn(DeckScoreRules.SPECIAL_THRESHOLDS)
        }
    }

    @Test
    fun `연대 목록은 1880부터 2020까지 열 해 간격이다`() {
        assertThat(DeckScoreRules.DECADE_YEARS.first()).isEqualTo(1880)
        assertThat(DeckScoreRules.DECADE_YEARS.last()).isEqualTo(2020)
        assertThat(DeckScoreRules.DECADE_YEARS).hasSize(15)
    }

    @Test
    fun `총점 공식이 워크북 값을 재현한다`() {
        // 워크북 D4=875.5 D5=875.5 D6=607 D7=741.25.
        // 선발·계투 최종점수 87.55, 타자 60.7일 때의 값이다.
        val rotation = 87.55
        val bullpen = 87.55
        val lineup = 60.7
        val scale = DeckRules.PART_SCALE

        assertThat(rotation * scale).isCloseTo(875.5, within(0.01))
        assertThat(bullpen * scale).isCloseTo(875.5, within(0.01))
        assertThat(lineup * scale).isCloseTo(607.0, within(0.01))

        val total = rotation * scale * DeckRules.PART_WEIGHTS.getValue(DeckPart.ROTATION) +
            bullpen * scale * DeckRules.PART_WEIGHTS.getValue(DeckPart.BULLPEN) +
            lineup * scale * DeckRules.PART_WEIGHTS.getValue(DeckPart.LINEUP)
        assertThat(total).isCloseTo(741.25, within(0.01))
    }

    @Test
    fun `후보는 종합 점수에 들어가지 않는다`() {
        assertThat(DeckRules.PART_WEIGHTS.getValue(DeckPart.BENCH)).isEqualTo(0.0)
        // 나머지 셋의 합이 1이다. 그래야 가중 평균이 된다.
        assertThat(DeckRules.PART_WEIGHTS.values.sum()).isCloseTo(1.0, within(1e-9))
    }
}
