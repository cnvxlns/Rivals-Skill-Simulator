package com.example.skillsim.service

import com.example.skillsim.config.DeckDataLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 덱 편집기가 화면을 그리는 데 쓰는 규칙.
 *
 * 앱이 사다리와 상한을 복제하지 않게 서버가 내준다. 그래서 이 응답이 비거나 모양이 어긋나면
 * 화면에서 고를 수 없는 칸이 생긴다 — 그 조용한 실패를 여기서 잡는다.
 */
class DeckRulesServiceTest {

    private val rules = DeckRulesService(DeckDataLoader().apply { run() }).rules()

    @Test
    fun `사다리 둘을 임계값 전부와 함께 내준다`() {
        // 보상이 없는 칸(팀 330·345)도 화면에는 나와야 한다. 사용자가 고를 수 있는 자리다.
        val team = rules.ladders.first { it.ladder == "TEAM" }
        val special = rules.ladders.first { it.ladder == "SPECIAL" }

        assertThat(team.tiers.map { it.threshold }).isEqualTo(DeckScoreRules.TEAM_THRESHOLDS)
        assertThat(special.tiers.map { it.threshold }).isEqualTo(DeckScoreRules.SPECIAL_THRESHOLDS)
    }

    @Test
    fun `연대 칸에만 연대 표시가 붙는다`() {
        val special = rules.ladders.first { it.ladder == "SPECIAL" }
        val decadeTiers = special.tiers.filter { it.decade }.map { it.threshold }.toSet()

        assertThat(decadeTiers).isEqualTo(DeckScoreRules.DECADE_TIERS)
        assertThat(rules.ladders.first { it.ladder == "TEAM" }.tiers).noneSatisfy {
            assertThat(it.decade).isTrue()
        }
        assertThat(rules.decadeYears).contains(1880, 2020)
    }

    @Test
    fun `좌우 효과 요약이 실려 온다`() {
        val team200 = rules.ladders
            .first { it.ladder == "TEAM" }
            .tiers
            .first { it.threshold == 200 }

        // 팀 200 좌는 타자·투수 전체에 전 스탯 +3이다.
        assertThat(team200.left).isNotEmpty
        assertThat(team200.left).allSatisfy { assertThat(it.amount).isEqualTo(3) }
        assertThat(team200.left.map { it.target })
            .contains("BATTER_ALL", "PITCHER_ALL")
    }

    @Test
    fun `카드별 성장 상한을 내준다`() {
        val byKey = rules.growth.associateBy { Triple(it.track, it.cardGrade, it.cardVariant) }

        // 강화는 블랙 계열만 10에서, 초월은 시그니처·프라임 계열이 9에서 끝난다.
        assertThat(byKey.getValue(Triple("ENHANCEMENT", "SIGNATURE_BLACK", "NONE")).maxLevel).isEqualTo(10)
        assertThat(byKey.getValue(Triple("ENHANCEMENT", "SIGNATURE", "NONE")).maxLevel).isEqualTo(20)
        assertThat(byKey.getValue(Triple("TRANSCENDENCE", "SIGNATURE", "NONE")).maxLevel).isEqualTo(9)
        assertThat(byKey.getValue(Triple("TRANSCENDENCE", "HOF", "NONE")).maxLevel).isEqualTo(15)

        // 표가 아예 없는 등급은 내보내지 않는다. 화면이 드롭다운을 열지 않아야 한다.
        assertThat(rules.growth.map { it.cardGrade }).doesNotContain("LIVE", "SEASON", "IMPACT")
    }

    @Test
    fun `파트 가중치와 배수를 내준다`() {
        assertThat(rules.partScale).isEqualTo(10.0)
        assertThat(rules.partWeights).containsEntry("ROTATION", 0.4)
        assertThat(rules.partWeights).containsEntry("BULLPEN", 0.1)
        assertThat(rules.partWeights).containsEntry("LINEUP", 0.5)
        assertThat(rules.partWeights).containsEntry("BENCH", 0.0)
    }
}
