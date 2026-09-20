package com.example.skillsim.service

import com.example.skillsim.config.DeckDataLoader
import com.example.skillsim.enums.RelieverRole
import com.example.skillsim.model.DeckRoster
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class DeckScoreServiceTest {

    private val repository = DeckFixtures.repository()
    private val scoreService = ScoreService(
        repository, ScoreCalculator(), mapOf(DeckFixtures.STAT to 1.0),
    )
    private val deckScoreService = DeckScoreService(repository, scoreService, DeckDataLoader(), mapOf(DeckFixtures.STAT to 1.0))
    private val validator = DeckFixtures.validator(repository)

    private fun roster(): DeckRoster = validator.validate(DeckFixtures.deckRequest())

    @Test
    fun `파트별 가중 소계를 더하면 종합 점수가 된다`() {
        // 예전에는 26명 점수를 그냥 더했다. 이제는 파트 평균에 가중치를 매긴다.
        val result = deckScoreService.score(roster())

        assertThat(result.players).hasSize(DeckRoster.ROSTER_SIZE)
        assertThat(result.parts.sumOf { it.weighted })
            .isCloseTo(result.total, within(0.01))
        assertThat(result.parts.sumOf { it.playerCount }).isEqualTo(DeckRoster.ROSTER_SIZE)
        // 단순 합은 더 이상 종합 점수가 아니다.
        assertThat(result.parts.sumOf { it.total }).isNotEqualTo(result.total)
    }

    @Test
    fun `파트 가중치는 선발 04 계투 01 타자 05이고 후보는 0이다`() {
        val result = deckScoreService.score(roster())
        val byPart = result.parts.associateBy { it.part }

        assertThat(byPart.getValue("ROTATION").weight).isEqualTo(0.4)
        assertThat(byPart.getValue("BULLPEN").weight).isEqualTo(0.1)
        assertThat(byPart.getValue("LINEUP").weight).isEqualTo(0.5)
        assertThat(byPart.getValue("BENCH").weight).isEqualTo(0.0)
        assertThat(byPart.getValue("BENCH").weighted).isEqualTo(0.0)
    }

    @Test
    fun `파트 점수는 평균에 10을 곱한 값이다`() {
        val result = deckScoreService.score(roster())
        val rotation = result.parts.first { it.part == "ROTATION" }

        assertThat(rotation.average)
            .isCloseTo(rotation.total / rotation.playerCount, within(0.01))
        assertThat(rotation.weighted)
            .isCloseTo(rotation.average * 10.0 * 0.4, within(0.01))
    }

    @Test
    fun `후보를 아무리 채워도 종합 점수가 오르지 않는다`() {
        // 가중치 0의 실행 가능한 증명이다. 워크북에는 후보 자리가 아예 없다.
        val base = roster()
        val boosted = base.copy(
            players = base.players.map { player ->
                if (DeckRules.isBench(player.slot)) {
                    player.copy(skills = player.skills.map { it.copy(level = 3) })
                } else {
                    player
                }
            },
        )

        assertThat(deckScoreService.score(boosted).total)
            .isEqualTo(deckScoreService.score(base).total)
    }

    @Test
    fun `선수 점수는 능력치 점수와 스킬 점수의 합이다`() {
        // 워크북의 J + O = P다.
        val players = deckScoreService.score(roster()).players

        assertThat(players).allSatisfy {
            assertThat(it.score).isCloseTo(it.statScore + it.skillScore, within(0.01))
        }
    }

    @Test
    fun `능력치를 적지 않으면 능력치 점수가 0이다`() {
        // 워크북의 빈 칸과 같다. 엔진의 기본값 120은 비례 효과 채점에만 남는다.
        val players = deckScoreService.score(roster()).players

        assertThat(players).allSatisfy { assertThat(it.statScore).isEqualTo(0.0) }
    }

    @Test
    fun `파트는 타선 후보 선발진 불펜진으로 나뉜다`() {
        val result = deckScoreService.score(roster())
        val byPart = result.parts.associateBy { it.part }

        // 선발 5 · 마무리 2 → 중계 5. 불펜진은 중계와 마무리를 합친 7명이다.
        assertThat(byPart.getValue("LINEUP").playerCount).isEqualTo(9)
        assertThat(byPart.getValue("BENCH").playerCount).isEqualTo(5)
        assertThat(byPart.getValue("ROTATION").playerCount).isEqualTo(5)
        assertThat(byPart.getValue("BULLPEN").playerCount).isEqualTo(7)
    }

    @Test
    fun `컬렉션 버프가 로스터의 카드 등급에서 계산돼 응답에 담긴다`() {
        // 기본 로스터는 시그니처 26장이라 2·5·8·11·14·17을 모두 넘겨 +6이다.
        val buff = deckScoreService.score(roster()).collectionBuff

        assertThat(buff.batterBonus).isEqualTo(6)
        assertThat(buff.pitcherBonus).isEqualTo(6)
        assertThat(buff.families.first { it.family == "SIGNATURE" }.count)
            .isEqualTo(DeckRoster.ROSTER_SIZE)
    }

    @Test
    fun `계열이 없는 등급만 모으면 버프가 걸리지 않는다`() {
        val impact = validator.validate(
            DeckFixtures.deckRequest { it.copy(cardGrade = "IMPACT") },
        )

        val buff = deckScoreService.score(impact).collectionBuff

        assertThat(buff.batterBonus).isEqualTo(0)
        assertThat(buff.pitcherBonus).isEqualTo(0)
    }

    @Test
    fun `시즌 덱은 타자와 투수의 버프가 다르다`() {
        // 시즌 26장 → 타자는 6·16을, 투수는 10·20을 넘겨 둘 다 +2다.
        val season = validator.validate(
            DeckFixtures.deckRequest { it.copy(cardGrade = "SEASON") },
        )

        val buff = deckScoreService.score(season).collectionBuff

        assertThat(buff.batterBonus).isEqualTo(2)
        assertThat(buff.pitcherBonus).isEqualTo(2)

        // 장수는 덱 전체로 세고 역할은 버프를 받을 대상만 가른다. 타자 14명만 시즌 카드여도
        // 컬렉션은 14장이라 투수 임계값 10도 함께 넘어간다. 시즌 카드를 누가 들고 있는지는
        // 집계에 영향을 주지 않는다.
        val batterOnly = validator.validate(
            DeckFixtures.deckRequest { player ->
                if (DeckRules.isPitcher(player.slot.orEmpty())) player.copy(cardGrade = "IMPACT")
                else player.copy(cardGrade = "SEASON")
            },
        )
        val partial = deckScoreService.score(batterOnly).collectionBuff

        assertThat(partial.families.first { it.family == "SEASON" }.count).isEqualTo(14)
        // 타자 6은 넘고 16은 못 넘어 +1, 투수 10은 넘고 20은 못 넘어 +1.
        assertThat(partial.batterBonus).isEqualTo(1)
        assertThat(partial.pitcherBonus).isEqualTo(1)
    }

    @Test
    fun `중계 하위 역할은 점수를 바꾸지 않는다`() {
        // 요구사항의 실행 가능한 증명이다. 조건 게이트가 슬롯 번호만 보므로
        // 승리조/추격조/롱릴리프를 어떻게 배분해도 점수가 같아야 한다.
        val base = roster()
        val flipped = base.copy(
            players = base.players.map { player ->
                if (player.relieverRole == null) {
                    player
                } else {
                    player.copy(relieverRole = RelieverRole.WIN)
                }
            },
        )

        assertThat(flipped.players.count { it.relieverRole == RelieverRole.WIN }).isEqualTo(5)
        assertThat(deckScoreService.score(flipped).total)
            .isEqualTo(deckScoreService.score(base).total)
    }

    @Test
    fun `선수 목록은 기여도 내림차순이다`() {
        val scores = deckScoreService.score(roster()).players.map { it.score }

        assertThat(scores).isSortedAccordingTo(compareByDescending { it })
    }

    @Test
    fun `자리 정보가 결과에 그대로 실린다`() {
        val result = deckScoreService.score(roster())
        val bySlot = result.players.associateBy { it.slot }

        assertThat(bySlot.getValue("SS").battingOrder).isEqualTo(5)
        assertThat(bySlot.getValue("SS").pitcherSlot).isNull()
        assertThat(bySlot.getValue("RP2").pitcherSlot).isEqualTo(2)
        assertThat(bySlot.getValue("RP2").relieverRole).isEqualTo(RelieverRole.LONG)
        assertThat(bySlot.getValue("BENCH1").battingOrder).isNull()
    }

    @Test
    fun `기본 응답에는 효과 단위 근거를 담지 않는다`() {
        // 26명 x 3~4스킬 x 여러 효과면 응답이 수백 KB로 불어난다.
        val lean = deckScoreService.score(roster())
        assertThat(lean.players.flatMap { it.perSkill }).isNotEmpty
        assertThat(lean.players.flatMap { it.perSkill }.flatMap { it.breakdown }).isEmpty()

        val detailed = deckScoreService.score(roster(), includeBreakdown = true)
        assertThat(detailed.players.flatMap { it.perSkill }.flatMap { it.breakdown }).isNotEmpty
        assertThat(detailed.total).isEqualTo(lean.total)
    }

    @Test
    fun `타순이 없는 후보도 채점된다`() {
        // ScoreService.calculate는 타자에게 타순을 요구하지만, 후보는 타순이 없다.
        // scoreSelections를 직접 쓰면 기본 타순으로 떨어져 점수가 나온다.
        val bench = deckScoreService.score(roster()).players.filter { it.slot.startsWith("BENCH") }

        assertThat(bench).hasSize(5)
        assertThat(bench).allSatisfy { assertThat(it.score).isGreaterThan(0.0) }
    }
}
